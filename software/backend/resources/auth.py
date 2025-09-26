"""
Authentication API Resources
Handles user login, token refresh, and logout functionality
"""
import logging
from datetime import datetime, timedelta
from flask import request, current_app
from flask_restful import Resource
from flask_jwt_extended import (
    create_access_token, create_refresh_token, jwt_required,
    get_jwt_identity, get_jwt, verify_jwt_in_request
)
from werkzeug.security import check_password_hash
from marshmallow import Schema, fields, ValidationError as MarshmallowValidationError

from backend.utils.exceptions import ValidationError, AuthenticationError, DatabaseError
from backend.services.auth_service import AuthService


class LoginSchema(Schema):
    """Schema for login request validation"""
    username = fields.Str(required=True, validate=lambda x: len(x.strip()) > 0)
    password = fields.Str(required=True, validate=lambda x: len(x) >= 6)
    organization_id = fields.Str(required=False, allow_none=True)


class RefreshSchema(Schema):
    """Schema for token refresh validation"""
    pass  # JWT refresh token is validated by decorator


class AuthResource(Resource):
    """Handle user authentication and token generation"""
    
    def __init__(self):
        self.auth_service = AuthService()
        self.logger = logging.getLogger(__name__)
    
    def post(self):
        """
        User login endpoint
        ---
        tags:
          - Authentication
        parameters:
          - in: body
            name: credentials
            schema:
              type: object
              required:
                - username
                - password
              properties:
                username:
                  type: string
                  description: Username or email
                password:
                  type: string
                  description: User password
                organization_id:
                  type: string
                  description: Optional organization ID
        responses:
          200:
            description: Login successful
            schema:
              type: object
              properties:
                access_token:
                  type: string
                refresh_token:
                  type: string
                user:
                  type: object
                expires_in:
                  type: integer
          400:
            description: Invalid request data
          401:
            description: Invalid credentials
        """
        try:
            # Validate request data
            schema = LoginSchema()
            try:
                data = schema.load(request.get_json() or {})
            except MarshmallowValidationError as e:
                raise ValidationError("Invalid login data", details=e.messages)
            
            # Authenticate user
            user = self.auth_service.authenticate_user(
                username=data['username'],
                password=data['password'],
                organization_id=data.get('organization_id')
            )
            
            if not user:
                self.logger.warning(f"Failed login attempt for username: {data['username']}")
                raise AuthenticationError("Invalid username or password")
            
            # Check if user is active
            if not user.get('is_active', True):
                raise AuthenticationError("Account is deactivated")
            
            # Create JWT tokens
            additional_claims = {
                'user_id': str(user['_id']),
                'username': user['username'],
                'role': user['role'],
                'organization_id': str(user['organization_id'])
            }
            
            access_token = create_access_token(
                identity=str(user['_id']),
                additional_claims=additional_claims
            )
            
            refresh_token = create_refresh_token(
                identity=str(user['_id']),
                additional_claims=additional_claims
            )
            
            # Update last login timestamp
            self.auth_service.update_last_login(user['_id'])
            
            # Prepare user data for response (exclude sensitive fields)
            user_data = {
                'id': str(user['_id']),
                'username': user['username'],
                'email': user['email'],
                'role': user['role'],
                'organization_id': str(user['organization_id']),
                'full_name': user.get('full_name'),
                'last_login': user.get('last_login')
            }
            
            self.logger.info(f"Successful login for user: {user['username']}")
            
            return {
                'access_token': access_token,
                'refresh_token': refresh_token,
                'user': user_data,
                'expires_in': int(current_app.config['JWT_ACCESS_TOKEN_EXPIRES'].total_seconds()),
                'token_type': 'Bearer'
            }, 200
            
        except (ValidationError, AuthenticationError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Login error: {str(e)}")
            raise DatabaseError("Login failed due to server error")


class RefreshResource(Resource):
    """Handle JWT token refresh"""
    
    def __init__(self):
        self.auth_service = AuthService()
        self.logger = logging.getLogger(__name__)
    
    @jwt_required(refresh=True)
    def post(self):
        """
        Refresh access token
        ---
        tags:
          - Authentication
        security:
          - Bearer: []
        responses:
          200:
            description: Token refreshed successfully
            schema:
              type: object
              properties:
                access_token:
                  type: string
                expires_in:
                  type: integer
          401:
            description: Invalid or expired refresh token
        """
        try:
            current_user_id = get_jwt_identity()
            
            # Verify user still exists and is active
            user = self.auth_service.get_user_by_id(current_user_id)
            if not user or not user.get('is_active', True):
                raise AuthenticationError("User account not found or deactivated")
            
            # Create new access token
            additional_claims = {
                'user_id': str(user['_id']),
                'username': user['username'],
                'role': user['role'],
                'organization_id': str(user['organization_id'])
            }
            
            new_access_token = create_access_token(
                identity=current_user_id,
                additional_claims=additional_claims
            )
            
            self.logger.info(f"Token refreshed for user: {user['username']}")
            
            return {
                'access_token': new_access_token,
                'expires_in': int(current_app.config['JWT_ACCESS_TOKEN_EXPIRES'].total_seconds()),
                'token_type': 'Bearer'
            }, 200
            
        except AuthenticationError as e:
            raise e
        except Exception as e:
            self.logger.error(f"Token refresh error: {str(e)}")
            raise DatabaseError("Token refresh failed due to server error")


class LogoutResource(Resource):
    """Handle user logout and token invalidation"""
    
    def __init__(self):
        self.auth_service = AuthService()
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    def post(self):
        """
        User logout endpoint
        ---
        tags:
          - Authentication
        security:
          - Bearer: []
        responses:
          200:
            description: Logout successful
          401:
            description: Invalid or missing token
        """
        try:
            # Get JWT token info
            jwt_data = get_jwt()
            jti = jwt_data['jti']  # JWT ID for blacklisting
            user_id = get_jwt_identity()
            
            # Add token to blacklist
            self.auth_service.blacklist_token(jti, jwt_data.get('exp', 0))
            
            # Update user logout timestamp
            self.auth_service.update_last_logout(user_id)
            
            self.logger.info(f"User logged out: {user_id}")
            
            return {
                'message': 'Successfully logged out'
            }, 200
            
        except Exception as e:
            self.logger.error(f"Logout error: {str(e)}")
            raise DatabaseError("Logout failed due to server error")


class UserProfileResource(Resource):
    """Handle user profile operations"""
    
    def __init__(self):
        self.auth_service = AuthService()
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    def get(self):
        """
        Get current user profile
        ---
        tags:
          - Authentication
        security:
          - Bearer: []
        responses:
          200:
            description: User profile data
            schema:
              type: object
              properties:
                user:
                  type: object
          401:
            description: Invalid or missing token
        """
        try:
            user_id = get_jwt_identity()
            user = self.auth_service.get_user_by_id(user_id)
            
            if not user:
                raise AuthenticationError("User not found")
            
            # Prepare user data (exclude sensitive fields)
            user_data = {
                'id': str(user['_id']),
                'username': user['username'],
                'email': user['email'],
                'role': user['role'],
                'organization_id': str(user['organization_id']),
                'full_name': user.get('full_name'),
                'created_at': user.get('created_at'),
                'last_login': user.get('last_login'),
                'is_active': user.get('is_active', True)
            }
            
            return {'user': user_data}, 200
            
        except AuthenticationError as e:
            raise e
        except Exception as e:
            self.logger.error(f"Profile fetch error: {str(e)}")
            raise DatabaseError("Failed to fetch user profile")