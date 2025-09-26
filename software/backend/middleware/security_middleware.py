"""
Comprehensive Security Middleware
Handles request validation, security headers, and protection mechanisms
"""
import logging
from flask import request, g, current_app
from datetime import datetime
import json

from backend.utils.exceptions import ValidationError, AuthorizationError
from backend.utils.security import SecurityValidator, InputSanitizer


class SecurityMiddleware:
    """Comprehensive security middleware for request processing"""
    
    def __init__(self, app=None):
        self.app = app
        self.logger = logging.getLogger(__name__)
        if app is not None:
            self.init_app(app)
    
    def init_app(self, app):
        """Initialize security middleware with Flask app"""
        app.before_request(self.before_request)
        app.after_request(self.after_request)
    
    def before_request(self):
        """Security checks before processing request"""
        try:
            # Log request for security monitoring
            self._log_request()
            
            # Validate request size
            self._validate_request_size()
            
            # Validate content type for data endpoints
            self._validate_content_type()
            
            # Sanitize request data
            self._sanitize_request_data()
            
            # Check for suspicious patterns
            self._check_suspicious_patterns()
            
        except (ValidationError, AuthorizationError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Security middleware error: {str(e)}")
            # Don't block request for non-critical security checks
    
    def after_request(self, response):
        """Add security headers and cleanup after request"""
        try:
            # Add security headers
            self._add_security_headers(response)
            
            # Log response for monitoring
            self._log_response(response)
            
        except Exception as e:
            self.logger.error(f"Security middleware after_request error: {str(e)}")
        
        return response
    
    def _log_request(self):
        """Log request details for security monitoring"""
        request_data = {
            'timestamp': datetime.utcnow().isoformat(),
            'method': request.method,
            'path': request.path,
            'remote_addr': request.environ.get('HTTP_X_FORWARDED_FOR', request.remote_addr),
            'user_agent': request.headers.get('User-Agent', ''),
            'content_length': request.content_length or 0
        }
        
        # Store in request context for later use
        g.security_log = request_data
        
        # Log suspicious requests
        if self._is_suspicious_request(request_data):
            self.logger.warning(f"Suspicious request detected: {json.dumps(request_data)}")
    
    def _validate_request_size(self):
        """Validate request content length"""
        max_size = current_app.config.get('MAX_CONTENT_LENGTH', 16 * 1024 * 1024)  # 16MB default
        
        if request.content_length and request.content_length > max_size:
            raise ValidationError(f"Request size {request.content_length} exceeds maximum {max_size} bytes")
    
    def _validate_content_type(self):
        """Validate content type for API endpoints"""
        if request.method in ['POST', 'PUT', 'PATCH'] and request.path.startswith('/api/'):
            content_type = request.content_type
            
            # Allow JSON and form data
            allowed_types = ['application/json', 'application/x-www-form-urlencoded', 'multipart/form-data']
            
            if content_type and not any(allowed in content_type for allowed in allowed_types):
                raise ValidationError(f"Unsupported content type: {content_type}")
    
    def _sanitize_request_data(self):
        """Sanitize request data to prevent injection attacks"""
        if request.is_json:
            try:
                # Get JSON data and sanitize
                json_data = request.get_json()
                if json_data:
                    sanitized_data = SecurityValidator.sanitize_input(json_data)
                    # Store sanitized data in request context
                    g.sanitized_json = sanitized_data
            except Exception as e:
                self.logger.warning(f"JSON sanitization error: {str(e)}")
    
    def _check_suspicious_patterns(self):
        """Check for suspicious request patterns"""
        # Check for SQL injection patterns
        suspicious_patterns = [
            r'union\s+select',
            r'drop\s+table',
            r'insert\s+into',
            r'delete\s+from',
            r'<script',
            r'javascript:',
            r'eval\s*\(',
            r'exec\s*\('
        ]
        
        # Check URL and query parameters
        full_url = request.url.lower()
        for pattern in suspicious_patterns:
            import re
            if re.search(pattern, full_url, re.IGNORECASE):
                self.logger.warning(f"Suspicious pattern detected in URL: {pattern}")
                raise AuthorizationError("Suspicious request pattern detected")
    
    def _add_security_headers(self, response):
        """Add comprehensive security headers"""
        # Prevent MIME type sniffing
        response.headers['X-Content-Type-Options'] = 'nosniff'
        
        # Prevent clickjacking
        response.headers['X-Frame-Options'] = 'DENY'
        
        # XSS protection
        response.headers['X-XSS-Protection'] = '1; mode=block'
        
        # HTTPS enforcement (in production)
        if current_app.config.get('ENV') == 'production':
            response.headers['Strict-Transport-Security'] = 'max-age=31536000; includeSubDomains; preload'
        
        # Content Security Policy
        csp_policy = (
            "default-src 'self'; "
            "script-src 'self' 'unsafe-inline'; "
            "style-src 'self' 'unsafe-inline'; "
            "img-src 'self' data: https:; "
            "connect-src 'self'; "
            "font-src 'self'; "
            "object-src 'none'; "
            "base-uri 'self'; "
            "form-action 'self'"
        )
        response.headers['Content-Security-Policy'] = csp_policy
        
        # Referrer policy
        response.headers['Referrer-Policy'] = 'strict-origin-when-cross-origin'
        
        # Permissions policy
        response.headers['Permissions-Policy'] = 'geolocation=(), microphone=(), camera=()'
        
        # Cache control for sensitive endpoints
        if request.path.startswith('/api/auth/') or 'token' in request.path:
            response.headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, private'
            response.headers['Pragma'] = 'no-cache'
            response.headers['Expires'] = '0'
    
    def _log_response(self, response):
        """Log response for security monitoring"""
        if hasattr(g, 'security_log'):
            g.security_log.update({
                'response_status': response.status_code,
                'response_size': len(response.get_data()),
                'processing_time': datetime.utcnow().isoformat()
            })
            
            # Log failed authentication attempts
            if response.status_code == 401 and request.path.startswith('/api/auth/'):
                self.logger.warning(f"Authentication failure: {json.dumps(g.security_log)}")
    
    def _is_suspicious_request(self, request_data):
        """Determine if request is suspicious"""
        # Check for rapid requests from same IP
        # Check for unusual user agents
        # Check for requests to non-existent endpoints
        
        suspicious_indicators = [
            request_data['content_length'] > 10 * 1024 * 1024,  # Very large requests
            'bot' in request_data['user_agent'].lower() and not any(
                allowed in request_data['user_agent'].lower() 
                for allowed in ['googlebot', 'bingbot']
            ),
            request_data['path'].count('/') > 10,  # Very deep paths
        ]
        
        return any(suspicious_indicators)


class RequestValidator:
    """Request validation utilities"""
    
    @staticmethod
    def validate_json_request():
        """Validate JSON request format"""
        if not request.is_json:
            raise ValidationError("Request must be JSON")
        
        try:
            data = request.get_json()
            if data is None:
                raise ValidationError("Invalid JSON data")
            return data
        except Exception as e:
            raise ValidationError(f"JSON parsing error: {str(e)}")
    
    @staticmethod
    def validate_required_fields(data, required_fields):
        """Validate required fields are present"""
        missing_fields = [field for field in required_fields if field not in data or data[field] is None]
        if missing_fields:
            raise ValidationError(f"Missing required fields: {missing_fields}")
    
    @staticmethod
    def validate_field_types(data, field_types):
        """Validate field data types"""
        for field, expected_type in field_types.items():
            if field in data and not isinstance(data[field], expected_type):
                raise ValidationError(f"Field '{field}' must be of type {expected_type.__name__}")
    
    @staticmethod
    def validate_string_length(data, field, min_length=None, max_length=None):
        """Validate string field length"""
        if field in data:
            value = data[field]
            if not isinstance(value, str):
                raise ValidationError(f"Field '{field}' must be a string")
            
            if min_length and len(value) < min_length:
                raise ValidationError(f"Field '{field}' must be at least {min_length} characters")
            
            if max_length and len(value) > max_length:
                raise ValidationError(f"Field '{field}' must be at most {max_length} characters")
    
    @staticmethod
    def validate_enum_field(data, field, allowed_values):
        """Validate field value is in allowed enum"""
        if field in data:
            value = data[field]
            if value not in allowed_values:
                raise ValidationError(f"Field '{field}' must be one of: {allowed_values}")


class APISecurityDecorator:
    """Security decorators for API endpoints"""
    
    @staticmethod
    def validate_device_data(f):
        """Decorator to validate device-related data"""
        from functools import wraps
        
        @wraps(f)
        def decorated_function(*args, **kwargs):
            if request.method in ['POST', 'PUT']:
                data = RequestValidator.validate_json_request()
                
                # Validate device ID if present
                if 'device_id' in data:
                    SecurityValidator.validate_device_id(data['device_id'])
                
                # Validate sensor data if present
                if 'raw_readings' in data:
                    SecurityValidator.validate_sensor_data_ranges(data['raw_readings'])
            
            return f(*args, **kwargs)
        
        return decorated_function
    
    @staticmethod
    def validate_user_data(f):
        """Decorator to validate user-related data"""
        from functools import wraps
        
        @wraps(f)
        def decorated_function(*args, **kwargs):
            if request.method in ['POST', 'PUT']:
                data = RequestValidator.validate_json_request()
                
                # Validate password if present
                if 'password' in data:
                    SecurityValidator.validate_password_strength(data['password'])
                
                # Validate email if present
                if 'email' in data:
                    InputSanitizer.validate_email(data['email'])
            
            return f(*args, **kwargs)
        
        return decorated_function