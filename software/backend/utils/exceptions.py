"""
Custom exception classes for AyuSure Backend API
"""


class APIException(Exception):
    """Base API exception class"""
    
    def __init__(self, message, code=500, details=None):
        self.message = message
        self.code = code
        self.details = details
        super().__init__(self.message)


class ValidationError(APIException):
    """Raised when request validation fails"""
    
    def __init__(self, message, details=None):
        super().__init__(message, 400, details)


class AuthenticationError(APIException):
    """Raised when authentication fails"""
    
    def __init__(self, message="Authentication failed", details=None):
        super().__init__(message, 401, details)


class AuthorizationError(APIException):
    """Raised when authorization fails"""
    
    def __init__(self, message="Access denied", details=None):
        super().__init__(message, 403, details)


class NotFoundError(APIException):
    """Raised when resource is not found"""
    
    def __init__(self, message="Resource not found", details=None):
        super().__init__(message, 404, details)


class ConflictError(APIException):
    """Raised when resource conflict occurs"""
    
    def __init__(self, message="Resource conflict", details=None):
        super().__init__(message, 409, details)


class DatabaseError(APIException):
    """Raised when database operation fails"""
    
    def __init__(self, message="Database operation failed", details=None):
        super().__init__(message, 500, details)


class ExternalServiceError(APIException):
    """Raised when external service call fails"""
    
    def __init__(self, message="External service error", details=None):
        super().__init__(message, 502, details)