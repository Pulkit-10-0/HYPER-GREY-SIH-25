"""
Authentication and Authorization Middleware
Handles JWT token validation, role-based access control, and security checks
"""
import logging
from functools import wraps
from flask import current_app
from flask_jwt_extended import verify_jwt_in_request, get_jwt, get_jwt_identity

from backend.utils.exceptions import AuthenticationError, AuthorizationError
from backend.services.auth_service import AuthService
from backend.utils.permissions import rbac_manager, check_permission


def require_permission(permission):
    """
    Decorator to require specific permission for accessing an endpoint
    
    Args:
        permission (str): Required permission (e.g., 'device.read', 'user.create')
    
    Returns:
        function: Decorated function
    """
    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):
            try:
                # Verify JWT token
                verify_jwt_in_request()
                
                # Get JWT claims
                jwt_claims = get_jwt()
                user_id = get_jwt_identity()
                
                # Check if token is blacklisted
                jti = jwt_claims.get('jti')
                if jti:
                    auth_service = AuthService()
                    if auth_service.is_token_blacklisted(jti):
                        raise AuthenticationError("Token has been revoked")
                
                # Get user role from JWT claims
                user_role = jwt_claims.get('role', 'viewer')
                
                # Check if user has required permission
                if not check_permission(user_role, permission):
                    raise AuthorizationError(f"Permission '{permission}' required")
                
                return f(*args, **kwargs)
                
            except AuthenticationError as e:
                raise e
            except AuthorizationError as e:
                raise e
            except Exception as e:
                logging.getLogger(__name__).error(f"Authorization error: {str(e)}")
                raise AuthenticationError("Authentication failed")
        
        return decorated_function
    return decorator


def require_role(required_role):
    """
    Decorator to require specific role for accessing an endpoint
    
    Args:
        required_role (str): Required role (e.g., 'admin', 'manager')
    
    Returns:
        function: Decorated function
    """
    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):
            try:
                # Verify JWT token
                verify_jwt_in_request()
                
                # Get JWT claims
                jwt_claims = get_jwt()
                user_role = jwt_claims.get('role')
                
                # Check if token is blacklisted
                jti = jwt_claims.get('jti')
                if jti:
                    auth_service = AuthService()
                    if auth_service.is_token_blacklisted(jti):
                        raise AuthenticationError("Token has been revoked")
                
                # Define role hierarchy
                role_hierarchy = {
                    'viewer': 1,
                    'operator': 2,
                    'manager': 3,
                    'admin': 4
                }
                
                user_level = role_hierarchy.get(user_role, 0)
                required_level = role_hierarchy.get(required_role, 5)
                
                if user_level < required_level:
                    raise AuthorizationError(f"Role '{required_role}' or higher required")
                
                return f(*args, **kwargs)
                
            except AuthenticationError as e:
                raise e
            except AuthorizationError as e:
                raise e
            except Exception as e:
                logging.getLogger(__name__).error(f"Role authorization error: {str(e)}")
                raise AuthenticationError("Authentication failed")
        
        return decorated_function
    return decorator


def require_organization_access(organization_field='organization_id'):
    """
    Decorator to ensure user can only access resources from their organization
    (unless they are admin)
    
    Args:
        organization_field (str): Field name containing organization ID in request
    
    Returns:
        function: Decorated function
    """
    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):
            try:
                # Verify JWT token
                verify_jwt_in_request()
                
                # Get JWT claims
                jwt_claims = get_jwt()
                user_role = jwt_claims.get('role')
                user_org_id = jwt_claims.get('organization_id')
                
                # Admin users can access all organizations
                if user_role == 'admin':
                    return f(*args, **kwargs)
                
                # For non-admin users, check organization access
                # This decorator can be extended to check specific resource organization
                # For now, it just ensures the JWT contains organization info
                if not user_org_id:
                    raise AuthorizationError("Organization access required")
                
                return f(*args, **kwargs)
                
            except AuthenticationError as e:
                raise e
            except AuthorizationError as e:
                raise e
            except Exception as e:
                logging.getLogger(__name__).error(f"Organization access error: {str(e)}")
                raise AuthenticationError("Authentication failed")
        
        return decorated_function
    return decorator


def rate_limit_by_user():
    """
    Decorator to apply rate limiting per user
    
    Returns:
        function: Decorated function
    """
    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):
            try:
                # Verify JWT token
                verify_jwt_in_request()
                
                # Get user ID for rate limiting key
                user_id = get_jwt_identity()
                
                # TODO: Implement user-specific rate limiting logic
                # This can be extended to use Redis for per-user rate limiting
                
                return f(*args, **kwargs)
                
            except Exception as e:
                logging.getLogger(__name__).error(f"Rate limiting error: {str(e)}")
                raise AuthenticationError("Authentication failed")
        
        return decorated_function
    return decorator


class SecurityMiddleware:
    """Security middleware for additional security checks"""
    
    def __init__(self, app=None):
        self.app = app
        if app is not None:
            self.init_app(app)
    
    def init_app(self, app):
        """Initialize security middleware with Flask app"""
        app.before_request(self.before_request)
        app.after_request(self.after_request)
    
    def before_request(self):
        """Security checks before processing request"""
        # TODO: Add security checks like:
        # - Request size validation
        # - IP whitelisting/blacklisting
        # - Suspicious activity detection
        pass
    
    def after_request(self, response):
        """Security headers and cleanup after request"""
        # Add security headers
        response.headers['X-Content-Type-Options'] = 'nosniff'
        response.headers['X-Frame-Options'] = 'DENY'
        response.headers['X-XSS-Protection'] = '1; mode=block'
        response.headers['Strict-Transport-Security'] = 'max-age=31536000; includeSubDomains'
        
        return response


def validate_device_access(device_id_param='device_id'):
    """
    Decorator to validate user access to specific device
    
    Args:
        device_id_param (str): Parameter name containing device ID
    
    Returns:
        function: Decorated function
    """
    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):
            try:
                # Verify JWT token
                verify_jwt_in_request()
                
                # Get JWT claims
                jwt_claims = get_jwt()
                user_role = jwt_claims.get('role')
                user_org_id = jwt_claims.get('organization_id')
                
                # Admin users can access all devices
                if user_role == 'admin':
                    return f(*args, **kwargs)
                
                # Get device ID from request parameters
                device_id = kwargs.get(device_id_param)
                if not device_id:
                    raise AuthorizationError("Device ID required")
                
                # Check device access (this would typically query the database)
                # For now, we'll let the resource handler do the detailed check
                # This decorator ensures the user has organization context
                if not user_org_id:
                    raise AuthorizationError("Organization access required")
                
                return f(*args, **kwargs)
                
            except AuthenticationError as e:
                raise e
            except AuthorizationError as e:
                raise e
            except Exception as e:
                logging.getLogger(__name__).error(f"Device access validation error: {str(e)}")
                raise AuthenticationError("Authentication failed")
        
        return decorated_function
    return decorator