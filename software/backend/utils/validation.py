"""
Input Validation Utilities
Provides comprehensive validation schemas and functions for API requests
"""
import re
from datetime import datetime
from marshmallow import Schema, fields, ValidationError, validates, validates_schema, post_load
from marshmallow.validate import Length, Range, OneOf, Email, Regexp


class BaseSchema(Schema):
    """Base schema with common validation methods"""
    
    @validates('email')
    def validate_email(self, value):
        """Validate email format"""
        if not re.match(r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$', value):
            raise ValidationError('Invalid email format')
    
    @validates('password')
    def validate_password(self, value):
        """Validate password strength"""
        if len(value) < 8:
            raise ValidationError('Password must be at least 8 characters long')
        
        if not re.search(r'[A-Z]', value):
            raise ValidationError('Password must contain at least one uppercase letter')
        
        if not re.search(r'[a-z]', value):
            raise ValidationError('Password must contain at least one lowercase letter')
        
        if not re.search(r'\d', value):
            raise ValidationError('Password must contain at least one digit')
    
    @validates('device_id')
    def validate_device_id(self, value):
        """Validate device ID format"""
        if not re.match(r'^[A-Z0-9_-]+$', value):
            raise ValidationError('Device ID must contain only uppercase letters, numbers, underscores, and hyphens')


class UserRegistrationSchema(BaseSchema):
    """Schema for user registration validation"""
    username = fields.Str(
        required=True,
        validate=[
            Length(min=3, max=50),
            Regexp(r'^[a-zA-Z0-9_]+$', error='Username can only contain letters, numbers, and underscores')
        ]
    )
    email = fields.Email(required=True)
    password = fields.Str(required=True, validate=Length(min=8, max=128))
    full_name = fields.Str(required=False, validate=Length(max=100))
    role = fields.Str(
        required=False,
        validate=OneOf(['admin', 'manager', 'operator', 'viewer']),
        missing='operator'
    )
    organization_id = fields.Str(required=True, validate=Length(min=1))


class UserUpdateSchema(BaseSchema):
    """Schema for user update validation"""
    email = fields.Email(required=False)
    full_name = fields.Str(required=False, validate=Length(max=100))
    role = fields.Str(
        required=False,
        validate=OneOf(['admin', 'manager', 'operator', 'viewer'])
    )
    is_active = fields.Bool(required=False)


class PasswordChangeSchema(BaseSchema):
    """Schema for password change validation"""
    current_password = fields.Str(required=True)
    new_password = fields.Str(required=True, validate=Length(min=8, max=128))
    confirm_password = fields.Str(required=True)
    
    @validates_schema
    def validate_passwords_match(self, data, **kwargs):
        """Validate that new password and confirmation match"""
        if data.get('new_password') != data.get('confirm_password'):
            raise ValidationError('New password and confirmation do not match')


class SensorDataSchema(Schema):
    """Schema for sensor data validation"""
    device_id = fields.Str(required=True, validate=Length(min=1, max=50))
    timestamp = fields.DateTime(required=False, missing=datetime.utcnow)
    measurement_id = fields.Int(required=True, validate=Range(min=1))
    
    # Electrode readings
    electrodes = fields.Dict(
        required=True,
        keys=fields.Str(validate=OneOf(['SS', 'Cu', 'Zn', 'Ag', 'Pt'])),
        values=fields.Float(validate=Range(min=0, max=5))
    )
    
    # Environmental readings
    environmental = fields.Dict(
        required=True,
        keys=fields.Str(validate=OneOf([
            'temperature', 'humidity', 'ph_voltage', 'tds_voltage',
            'uv_intensity', 'moisture_percent'
        ])),
        values=fields.Float()
    )
    
    # Color readings
    color = fields.Dict(
        required=True,
        keys=fields.Str(validate=OneOf(['r', 'g', 'b'])),
        values=fields.Int(validate=Range(min=0, max=255))
    )
    
    # System status
    system = fields.Dict(
        required=True,
        keys=fields.Str(validate=OneOf([
            'battery_voltage', 'wifi_rssi', 'free_heap'
        ])),
        values=fields.Float()
    )
    
    # Sample metadata
    sample_metadata = fields.Dict(
        required=False,
        keys=fields.Str(),
        values=fields.Raw()
    )
    
    @validates('electrodes')
    def validate_electrodes(self, value):
        """Validate electrode readings"""
        required_electrodes = ['SS', 'Cu', 'Zn', 'Ag', 'Pt']
        if not all(electrode in value for electrode in required_electrodes):
            raise ValidationError(f'All electrodes required: {required_electrodes}')
    
    @validates('environmental')
    def validate_environmental(self, value):
        """Validate environmental readings"""
        if 'temperature' in value:
            temp = value['temperature']
            if not -40 <= temp <= 85:  # Typical sensor range
                raise ValidationError('Temperature out of valid range (-40 to 85°C)')
        
        if 'humidity' in value:
            humidity = value['humidity']
            if not 0 <= humidity <= 100:
                raise ValidationError('Humidity must be between 0 and 100%')
    
    @validates('color')
    def validate_color(self, value):
        """Validate color readings"""
        required_colors = ['r', 'g', 'b']
        if not all(color in value for color in required_colors):
            raise ValidationError(f'All color channels required: {required_colors}')


class ReportGenerationSchema(Schema):
    """Schema for report generation validation"""
    report_type = fields.Str(
        required=True,
        validate=OneOf(['quality_summary', 'device_status', 'analysis_results', 'calibration_report'])
    )
    format = fields.Str(
        required=False,
        validate=OneOf(['pdf', 'csv', 'json']),
        missing='pdf'
    )
    date_range = fields.Dict(
        required=False,
        keys=fields.Str(validate=OneOf(['start_date', 'end_date'])),
        values=fields.DateTime()
    )
    filters = fields.Dict(
        required=False,
        keys=fields.Str(),
        values=fields.Raw()
    )
    
    @validates_schema
    def validate_date_range(self, data, **kwargs):
        """Validate date range"""
        date_range = data.get('date_range', {})
        if 'start_date' in date_range and 'end_date' in date_range:
            if date_range['start_date'] >= date_range['end_date']:
                raise ValidationError('Start date must be before end date')


class OrganizationSchema(Schema):
    """Schema for organization validation"""
    name = fields.Str(required=True, validate=Length(min=1, max=200))
    description = fields.Str(required=False, validate=Length(max=1000))
    contact_email = fields.Email(required=False)
    contact_phone = fields.Str(
        required=False,
        validate=Regexp(r'^\+?[\d\s\-\(\)]+$', error='Invalid phone number format')
    )
    address = fields.Dict(
        required=False,
        keys=fields.Str(validate=OneOf(['street', 'city', 'state', 'country', 'postal_code'])),
        values=fields.Str()
    )
    settings = fields.Dict(
        required=False,
        keys=fields.Str(),
        values=fields.Raw()
    )


class PaginationSchema(Schema):
    """Schema for pagination parameters"""
    page = fields.Int(required=False, validate=Range(min=1), missing=1)
    per_page = fields.Int(required=False, validate=Range(min=1, max=100), missing=20)
    sort_by = fields.Str(required=False)
    sort_order = fields.Str(required=False, validate=OneOf(['asc', 'desc']), missing='desc')


def validate_object_id(value):
    """Validate MongoDB ObjectId format"""
    if not re.match(r'^[a-f\d]{24}$', value):
        raise ValidationError('Invalid ObjectId format')


def validate_mac_address(value):
    """Validate MAC address format"""
    if not re.match(r'^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$', value):
        raise ValidationError('Invalid MAC address format')


def validate_ip_address(value):
    """Validate IP address format"""
    if not re.match(r'^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$', value):
        raise ValidationError('Invalid IP address format')


def sanitize_input(data):
    """Sanitize input data to prevent injection attacks"""
    if isinstance(data, dict):
        return {key: sanitize_input(value) for key, value in data.items()}
    elif isinstance(data, list):
        return [sanitize_input(item) for item in data]
    elif isinstance(data, str):
        # Remove potentially dangerous characters
        dangerous_chars = ['<', '>', '"', "'", '&', '$', '`']
        sanitized = data
        for char in dangerous_chars:
            sanitized = sanitized.replace(char, '')
        return sanitized.strip()
    else:
        return data


class ValidationUtils:
    """Utility class for common validation operations"""
    
    @staticmethod
    def validate_request_size(request, max_size_mb=16):
        """Validate request content length"""
        if request.content_length and request.content_length > max_size_mb * 1024 * 1024:
            raise ValidationError(f'Request size exceeds {max_size_mb}MB limit')
    
    @staticmethod
    def validate_file_type(filename, allowed_types):
        """Validate file type by extension"""
        if not filename:
            raise ValidationError('Filename is required')
        
        extension = filename.lower().split('.')[-1]
        if extension not in allowed_types:
            raise ValidationError(f'File type not allowed. Allowed types: {allowed_types}')
    
    @staticmethod
    def validate_json_structure(data, required_fields):
        """Validate JSON structure has required fields"""
        if not isinstance(data, dict):
            raise ValidationError('Request must be a JSON object')
        
        missing_fields = [field for field in required_fields if field not in data]
        if missing_fields:
            raise ValidationError(f'Missing required fields: {missing_fields}')
    
    @staticmethod
    def validate_enum_value(value, enum_values, field_name):
        """Validate enum value"""
        if value not in enum_values:
            raise ValidationError(f'{field_name} must be one of: {enum_values}')
    
    @staticmethod
    def validate_date_format(date_string, format_string='%Y-%m-%d'):
        """Validate date string format"""
        try:
            datetime.strptime(date_string, format_string)
        except ValueError:
            raise ValidationError(f'Invalid date format. Expected: {format_string}')
    
    @staticmethod
    def validate_numeric_range(value, min_val=None, max_val=None, field_name='value'):
        """Validate numeric value is within range"""
        if min_val is not None and value < min_val:
            raise ValidationError(f'{field_name} must be at least {min_val}')
        
        if max_val is not None and value > max_val:
            raise ValidationError(f'{field_name} must be at most {max_val}')