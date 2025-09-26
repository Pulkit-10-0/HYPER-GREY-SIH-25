"""
Security utilities for input validation, sanitization, and protection
"""
import re
import hashlib
import secrets
from datetime import datetime, timedelta
from flask import request, current_app
from functools import wraps

from backend.utils.exceptions import ValidationError, AuthorizationError


class SecurityValidator:
    """Comprehensive security validation utilities"""
    
    @staticmethod
    def validate_device_id(device_id):
        """Validate device ID format and security"""
        if not device_id or len(device_id) < 3 or len(device_id) > 50:
            raise ValidationError("Device ID must be between 3 and 50 characters")
        
        # Allow only alphanumeric, underscore, and hyphen
        if not re.match(r'^[A-Za-z0-9_-]+$', device_id):
            raise ValidationError("Device ID contains invalid characters")
        
        return True
    
    @staticmethod
    def validate_password_strength(password):
        """Validate password meets security requirements"""
        if len(password) < 8:
            raise ValidationError("Password must be at least 8 characters long")
        
        if len(password) > 128:
            raise ValidationError("Password must be less than 128 characters")
        
        if not re.search(r'[A-Z]', password):
            raise ValidationError("Password must contain at least one uppercase letter")
        
        if not re.search(r'[a-z]', password):
            raise ValidationError("Password must contain at least one lowercase letter")
        
        if not re.search(r'\d', password):
            raise ValidationError("Password must contain at least one digit")
        
        if not re.search(r'[!@#$%^&*(),.?":{}|<>]', password):
            raise ValidationError("Password must contain at least one special character")
        
        return True
    
    @staticmethod
    def sanitize_input(data):
        """Sanitize input to prevent injection attacks"""
        if isinstance(data, dict):
            return {key: SecurityValidator.sanitize_input(value) for key, value in data.items()}
        elif isinstance(data, list):
            return [SecurityValidator.sanitize_input(item) for item in data]
        elif isinstance(data, str):
            # Remove potentially dangerous characters
            sanitized = re.sub(r'[<>"\']', '', data)
            return sanitized.strip()
        else:
            return data
    
    @staticmethod
    def validate_sensor_data_ranges(sensor_data):
        """Validate sensor readings are within expected ranges"""
        # Electrode voltage ranges (0-5V)
        electrodes = sensor_data.get('electrodes', {})
        for electrode, value in electrodes.items():
            if not 0 <= value <= 5:
                raise ValidationError(f"Electrode {electrode} value {value} out of range (0-5V)")
        
        # Environmental sensor ranges
        env = sensor_data.get('environmental', {})
        if 'temperature' in env and not -40 <= env['temperature'] <= 85:
            raise ValidationError("Temperature out of valid range (-40 to 85°C)")
        
        if 'humidity' in env and not 0 <= env['humidity'] <= 100:
            raise ValidationError("Humidity out of valid range (0-100%)")
        
        if 'ph_voltage' in env and not 0 <= env['ph_voltage'] <= 5:
            raise ValidationError("pH voltage out of valid range (0-5V)")
        
        # Color ranges (0-255)
        color = sensor_data.get('color', {})
        for channel, value in color.items():
            if not 0 <= value <= 255:
                raise ValidationError(f"Color {channel} value {value} out of range (0-255)")
        
        return True
    
    @staticmethod
    def generate_secure_token():
        """Generate cryptographically secure token"""
        return secrets.token_urlsafe(32)
    
    @staticmethod
    def hash_sensitive_data(data):
        """Hash sensitive data for storage"""
        return hashlib.sha256(data.encode()).hexdigest()


def rate_limit(max_requests=100, window_minutes=60):
    """Rate limiting decorator"""
    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):
            # Get client identifier
            client_ip = request.environ.get('HTTP_X_FORWARDED_FOR', request.remote_addr)
            
            # Create rate limit key
            key = f"rate_limit:{f.__name__}:{client_ip}"
            
            try:
                redis_client = current_app.redis
                
                # Get current count
                current_count = redis_client.get(key)
                
                if current_count is None:
                    # First request in window
                    redis_client.setex(key, window_minutes * 60, 1)
                else:
                    current_count = int(current_count)
                    if current_count >= max_requests:
                        raise AuthorizationError(f"Rate limit exceeded. Max {max_requests} requests per {window_minutes} minutes")
                    
                    # Increment counter
                    redis_client.incr(key)
                
            except Exception as e:
                # If Redis is down, allow request but log error
                current_app.logger.warning(f"Rate limiting error: {str(e)}")
            
            return f(*args, **kwargs)
        
        return decorated_function
    return decorator


class InputSanitizer:
    """Input sanitization and validation"""
    
    @staticmethod
    def clean_string(value, max_length=None):
        """Clean and validate string input"""
        if not isinstance(value, str):
            raise ValidationError("Value must be a string")
        
        # Remove null bytes and control characters
        cleaned = re.sub(r'[\x00-\x1f\x7f-\x9f]', '', value)
        
        # Trim whitespace
        cleaned = cleaned.strip()
        
        if max_length and len(cleaned) > max_length:
            raise ValidationError(f"String too long (max {max_length} characters)")
        
        return cleaned
    
    @staticmethod
    def validate_email(email):
        """Validate email format"""
        pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
        if not re.match(pattern, email):
            raise ValidationError("Invalid email format")
        return email.lower()
    
    @staticmethod
    def validate_numeric_range(value, min_val=None, max_val=None, field_name="value"):
        """Validate numeric value is within range"""
        if not isinstance(value, (int, float)):
            raise ValidationError(f"{field_name} must be a number")
        
        if min_val is not None and value < min_val:
            raise ValidationError(f"{field_name} must be at least {min_val}")
        
        if max_val is not None and value > max_val:
            raise ValidationError(f"{field_name} must be at most {max_val}")
        
        return value


def require_https():
    """Decorator to require HTTPS in production"""
    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):
            if current_app.config.get('ENV') == 'production':
                if not request.is_secure:
                    raise AuthorizationError("HTTPS required")
            return f(*args, **kwargs)
        return decorated_function
    return decorator


def validate_content_type(allowed_types=['application/json']):
    """Decorator to validate request content type"""
    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):
            if request.method in ['POST', 'PUT', 'PATCH']:
                content_type = request.content_type
                if content_type not in allowed_types:
                    raise ValidationError(f"Content-Type must be one of: {allowed_types}")
            return f(*args, **kwargs)
        return decorated_function
    return decorator