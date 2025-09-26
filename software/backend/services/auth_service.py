"""
Authentication Service
Handles user authentication, token management, and user operations
"""
import logging
from datetime import datetime, timedelta
from bson import ObjectId
from werkzeug.security import check_password_hash, generate_password_hash
from flask import current_app
from pymongo.errors import PyMongoError

from backend.utils.exceptions import DatabaseError, AuthenticationError, NotFoundError


class AuthService:
    """Service class for authentication operations"""
    
    def __init__(self):
        self.logger = logging.getLogger(__name__)
    
    def authenticate_user(self, username, password, organization_id=None):
        """
        Authenticate user with username/email and password
        
        Args:
            username (str): Username or email
            password (str): Plain text password
            organization_id (str, optional): Organization ID for multi-tenant auth
            
        Returns:
            dict: User document if authentication successful, None otherwise
        """
        try:
            db = current_app.db
            
            # Build query - search by username or email
            query = {
                '$or': [
                    {'username': username.lower()},
                    {'email': username.lower()}
                ]
            }
            
            # Add organization filter if provided
            if organization_id:
                query['organization_id'] = ObjectId(organization_id)
            
            # Find user
            user = db.users.find_one(query)
            
            if not user:
                return None
            
            # Verify password
            if not check_password_hash(user['password_hash'], password):
                return None
            
            return user
            
        except PyMongoError as e:
            self.logger.error(f"Database error during authentication: {str(e)}")
            raise DatabaseError("Authentication failed due to database error")
        except Exception as e:
            self.logger.error(f"Unexpected error during authentication: {str(e)}")
            raise DatabaseError("Authentication failed")
    
    def get_user_by_id(self, user_id):
        """
        Get user by ID
        
        Args:
            user_id (str): User ID
            
        Returns:
            dict: User document or None if not found
        """
        try:
            db = current_app.db
            return db.users.find_one({'_id': ObjectId(user_id)})
            
        except PyMongoError as e:
            self.logger.error(f"Database error fetching user: {str(e)}")
            raise DatabaseError("Failed to fetch user")
        except Exception as e:
            self.logger.error(f"Error fetching user: {str(e)}")
            return None
    
    def update_last_login(self, user_id):
        """
        Update user's last login timestamp
        
        Args:
            user_id (ObjectId): User ID
        """
        try:
            db = current_app.db
            db.users.update_one(
                {'_id': user_id},
                {
                    '$set': {'last_login': datetime.utcnow()},
                    '$inc': {'login_count': 1}
                }
            )
            
        except PyMongoError as e:
            self.logger.error(f"Failed to update last login: {str(e)}")
            # Don't raise exception as this is not critical
    
    def update_last_logout(self, user_id):
        """
        Update user's last logout timestamp
        
        Args:
            user_id (str): User ID
        """
        try:
            db = current_app.db
            db.users.update_one(
                {'_id': ObjectId(user_id)},
                {'$set': {'last_logout': datetime.utcnow()}}
            )
            
        except PyMongoError as e:
            self.logger.error(f"Failed to update last logout: {str(e)}")
            # Don't raise exception as this is not critical
    
    def blacklist_token(self, jti, exp_timestamp):
        """
        Add JWT token to blacklist
        
        Args:
            jti (str): JWT ID
            exp_timestamp (int): Token expiration timestamp
        """
        try:
            redis_client = current_app.redis
            
            # Calculate TTL based on token expiration
            current_time = datetime.utcnow().timestamp()
            ttl = max(int(exp_timestamp - current_time), 1)
            
            # Store in Redis with TTL
            redis_client.setex(f"blacklist:{jti}", ttl, "true")
            
        except Exception as e:
            self.logger.error(f"Failed to blacklist token: {str(e)}")
            # Don't raise exception as this is not critical for logout
    
    def is_token_blacklisted(self, jti):
        """
        Check if JWT token is blacklisted
        
        Args:
            jti (str): JWT ID
            
        Returns:
            bool: True if token is blacklisted
        """
        try:
            redis_client = current_app.redis
            return redis_client.exists(f"blacklist:{jti}")
            
        except Exception as e:
            self.logger.error(f"Failed to check token blacklist: {str(e)}")
            return False  # Fail open for availability
    
    def create_user(self, user_data):
        """
        Create a new user account
        
        Args:
            user_data (dict): User information
            
        Returns:
            dict: Created user document
        """
        try:
            db = current_app.db
            
            # Check if username or email already exists
            existing_user = db.users.find_one({
                '$or': [
                    {'username': user_data['username'].lower()},
                    {'email': user_data['email'].lower()}
                ]
            })
            
            if existing_user:
                raise AuthenticationError("Username or email already exists")
            
            # Hash password
            password_hash = generate_password_hash(user_data['password'])
            
            # Prepare user document
            user_doc = {
                'username': user_data['username'].lower(),
                'email': user_data['email'].lower(),
                'password_hash': password_hash,
                'full_name': user_data.get('full_name', ''),
                'role': user_data.get('role', 'operator'),
                'organization_id': ObjectId(user_data['organization_id']),
                'is_active': True,
                'created_at': datetime.utcnow(),
                'updated_at': datetime.utcnow(),
                'login_count': 0
            }
            
            # Insert user
            result = db.users.insert_one(user_doc)
            user_doc['_id'] = result.inserted_id
            
            self.logger.info(f"Created new user: {user_data['username']}")
            return user_doc
            
        except PyMongoError as e:
            self.logger.error(f"Database error creating user: {str(e)}")
            raise DatabaseError("Failed to create user account")
    
    def update_user_password(self, user_id, old_password, new_password):
        """
        Update user password
        
        Args:
            user_id (str): User ID
            old_password (str): Current password
            new_password (str): New password
        """
        try:
            db = current_app.db
            
            # Get current user
            user = db.users.find_one({'_id': ObjectId(user_id)})
            if not user:
                raise NotFoundError("User not found")
            
            # Verify old password
            if not check_password_hash(user['password_hash'], old_password):
                raise AuthenticationError("Current password is incorrect")
            
            # Hash new password
            new_password_hash = generate_password_hash(new_password)
            
            # Update password
            db.users.update_one(
                {'_id': ObjectId(user_id)},
                {
                    '$set': {
                        'password_hash': new_password_hash,
                        'updated_at': datetime.utcnow()
                    }
                }
            )
            
            self.logger.info(f"Password updated for user: {user['username']}")
            
        except (NotFoundError, AuthenticationError) as e:
            raise e
        except PyMongoError as e:
            self.logger.error(f"Database error updating password: {str(e)}")
            raise DatabaseError("Failed to update password")
    
    def get_user_permissions(self, user_id):
        """
        Get user permissions based on role
        
        Args:
            user_id (str): User ID
            
        Returns:
            list: List of permissions
        """
        try:
            from backend.utils.permissions import get_user_permissions
            
            user = self.get_user_by_id(user_id)
            if not user:
                return []
            
            role = user.get('role', 'viewer')
            return get_user_permissions(role)
            
        except Exception as e:
            self.logger.error(f"Error getting user permissions: {str(e)}")
            return []