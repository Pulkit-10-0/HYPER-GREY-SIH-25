# AyuSure Backend API Documentation

## Overview

The AyuSure Backend API provides comprehensive authentication, device management, and data analysis capabilities for the herbal quality analysis system. This RESTful API supports JWT-based authentication, role-based access control, and real-time communication.

## Base URL

```
Development: http://localhost:5000
Production: https://api.ayusure.com
```

## Authentication

All API endpoints (except login) require JWT authentication. Include the access token in the Authorization header:

```
Authorization: Bearer <access_token>
```

### Authentication Endpoints

#### POST /api/auth/login

Authenticate user and receive JWT tokens.

**Request Body:**
```json
{
  "username": "admin",
  "password": "Admin123!",
  "organization_id": "optional_org_id"
}
```

**Response (200):**
```json
{
  "access_token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...",
  "refresh_token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": "user_id",
    "username": "admin",
    "email": "admin@example.com",
    "role": "admin",
    "organization_id": "org_id",
    "full_name": "Administrator"
  },
  "expires_in": 3600,
  "token_type": "Bearer"
}
```

#### POST /api/auth/refresh

Refresh access token using refresh token.

**Headers:**
```
Authorization: Bearer <refresh_token>
```

**Response (200):**
```json
{
  "access_token": "new_access_token",
  "expires_in": 3600,
  "token_type": "Bearer"
}
```

#### POST /api/auth/logout

Logout user and invalidate tokens.

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response (200):**
```json
{
  "message": "Successfully logged out"
}
```

#### GET /api/auth/profile

Get current user profile information.

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response (200):**
```json
{
  "user": {
    "id": "user_id",
    "username": "admin",
    "email": "admin@example.com",
    "role": "admin",
    "organization_id": "org_id",
    "full_name": "Administrator",
    "created_at": "2025-01-01T00:00:00Z",
    "last_login": "2025-01-01T12:00:00Z",
    "is_active": true
  }
}
```

## Device Management

### Device Endpoints

#### GET /api/devices

Get list of devices with pagination and filtering.

**Query Parameters:**
- `page` (int): Page number (default: 1)
- `per_page` (int): Items per page (default: 20, max: 100)
- `status` (string): Filter by status (active, inactive, maintenance)
- `device_model` (string): Filter by device model
- `search` (string): Search in device ID or description

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response (200):**
```json
{
  "devices": [
    {
      "id": "device_doc_id",
      "device_id": "ESP32_LAB_001",
      "device_model": "AYUSURE-ET-2000",
      "firmware_version": "2.0.1",
      "organization_id": "org_id",
      "owner_user_id": "user_id",
      "status": "active",
      "location": {
        "latitude": 28.6139,
        "longitude": 77.2090,
        "address": "Main Lab, AyuSure Labs"
      },
      "description": "Primary testing device",
      "calibration_data": {
        "last_calibration": "2025-01-01T00:00:00Z",
        "calibration_coefficients": {
          "SS": 1.0,
          "Cu": 1.0,
          "Zn": 1.0,
          "Ag": 1.0,
          "Pt": 1.0
        },
        "next_calibration_due": "2025-03-01T00:00:00Z"
      },
      "statistics": {
        "total_readings": 1250,
        "last_reading": "2025-01-01T12:00:00Z",
        "uptime_hours": 720
      },
      "created_at": "2024-12-01T00:00:00Z",
      "updated_at": "2025-01-01T12:00:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "per_page": 20,
    "total_count": 3,
    "total_pages": 1,
    "has_next": false,
    "has_prev": false
  }
}
```

#### POST /api/devices

Register a new device.

**Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "device_id": "ESP32_NEW_001",
  "device_model": "AYUSURE-ET-2000",
  "firmware_version": "2.0.1",
  "location": {
    "latitude": 28.6139,
    "longitude": 77.2090,
    "address": "New Lab Location"
  },
  "description": "New testing device"
}
```

**Response (201):**
```json
{
  "device": {
    "id": "new_device_doc_id",
    "device_id": "ESP32_NEW_001",
    "device_model": "AYUSURE-ET-2000",
    "firmware_version": "2.0.1",
    "status": "active",
    "location": {
      "latitude": 28.6139,
      "longitude": 77.2090,
      "address": "New Lab Location"
    },
    "description": "New testing device",
    "created_at": "2025-01-01T12:00:00Z"
  },
  "message": "Device registered successfully"
}
```

#### GET /api/devices/{device_id}

Get specific device details.

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response (200):**
```json
{
  "device": {
    "id": "device_doc_id",
    "device_id": "ESP32_LAB_001",
    "device_model": "AYUSURE-ET-2000",
    "firmware_version": "2.0.1",
    "status": "active",
    "location": {
      "latitude": 28.6139,
      "longitude": 77.2090,
      "address": "Main Lab"
    },
    "description": "Primary testing device",
    "calibration_data": {
      "last_calibration": "2025-01-01T00:00:00Z",
      "calibration_coefficients": {
        "SS": 1.0,
        "Cu": 1.0,
        "Zn": 1.0,
        "Ag": 1.0,
        "Pt": 1.0
      },
      "next_calibration_due": "2025-03-01T00:00:00Z"
    },
    "statistics": {
      "total_readings": 1250,
      "last_reading": "2025-01-01T12:00:00Z",
      "uptime_hours": 720
    },
    "created_at": "2024-12-01T00:00:00Z",
    "updated_at": "2025-01-01T12:00:00Z"
  }
}
```

#### PUT /api/devices/{device_id}

Update device information.

**Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "firmware_version": "2.0.2",
  "status": "maintenance",
  "location": {
    "latitude": 28.6150,
    "longitude": 77.2100,
    "address": "Updated Lab Location"
  },
  "description": "Updated description"
}
```

**Response (200):**
```json
{
  "device": {
    "id": "device_doc_id",
    "device_id": "ESP32_LAB_001",
    "firmware_version": "2.0.2",
    "status": "maintenance",
    "location": {
      "latitude": 28.6150,
      "longitude": 77.2100,
      "address": "Updated Lab Location"
    },
    "description": "Updated description",
    "updated_at": "2025-01-01T12:30:00Z"
  },
  "message": "Device updated successfully"
}
```

#### DELETE /api/devices/{device_id}

Delete device (soft delete - marks as inactive).

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response (200):**
```json
{
  "message": "Device deleted successfully"
}
```

### Device Calibration

#### POST /api/devices/{device_id}/calibration

Update device calibration.

**Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "calibration_coefficients": {
    "SS": 1.05,
    "Cu": 0.98,
    "Zn": 1.02,
    "Ag": 0.99,
    "Pt": 1.01
  },
  "calibration_notes": "Monthly calibration performed",
  "next_calibration_due": "2025-02-01T00:00:00Z"
}
```

**Response (200):**
```json
{
  "device": {
    "id": "device_doc_id",
    "device_id": "ESP32_LAB_001",
    "calibration_data": {
      "last_calibration": "2025-01-01T12:00:00Z",
      "calibration_coefficients": {
        "SS": 1.05,
        "Cu": 0.98,
        "Zn": 1.02,
        "Ag": 0.99,
        "Pt": 1.01
      },
      "next_calibration_due": "2025-02-01T00:00:00Z"
    },
    "updated_at": "2025-01-01T12:00:00Z"
  },
  "message": "Calibration updated successfully"
}
```

#### GET /api/devices/{device_id}/calibration

Get device calibration history.

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response (200):**
```json
{
  "calibration_history": [
    {
      "calibration_date": "2025-01-01T12:00:00Z",
      "calibrated_by": "user_id",
      "calibration_coefficients": {
        "SS": 1.05,
        "Cu": 0.98,
        "Zn": 1.02,
        "Ag": 0.99,
        "Pt": 1.01
      },
      "calibration_notes": "Monthly calibration performed",
      "next_calibration_due": "2025-02-01T00:00:00Z"
    }
  ]
}
```

## Role-Based Access Control

### Roles and Permissions

#### Admin
- Full system access
- User management (create, read, update, delete)
- Device management (create, read, update, delete, calibrate)
- Data operations (read, analyze, export)
- Report management (generate, read, delete)
- Organization management (read, update, settings)
- System administration (admin, monitor, backup)

#### Manager
- User management (read, update)
- Device management (create, read, update, calibrate)
- Data operations (read, analyze, export)
- Report management (generate, read)
- Organization access (read)
- System monitoring

#### Operator
- Device management (read, update, calibrate)
- Data operations (read, analyze)
- Report access (read)
- Organization access (read)

#### Viewer
- Device access (read only)
- Data access (read only)
- Report access (read only)
- Organization access (read only)

### Organization-Level Access

Non-admin users can only access resources within their organization:
- Devices belonging to their organization
- Users from their organization
- Data from their organization's devices
- Reports for their organization

## Error Handling

### Error Response Format

All API errors return a consistent format:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "details": {
      "field": "specific_field",
      "expected": "expected_value",
      "received": "received_value"
    },
    "timestamp": "2025-01-01T12:00:00Z",
    "request_id": "req_12345"
  }
}
```

### Common Error Codes

- `400` - Bad Request (ValidationError)
- `401` - Unauthorized (AuthenticationError)
- `403` - Forbidden (AuthorizationError)
- `404` - Not Found (NotFoundError)
- `409` - Conflict (ConflictError)
- `500` - Internal Server Error (DatabaseError)

## Rate Limiting

API endpoints are rate limited:
- Default: 200 requests per day, 50 requests per hour
- Rate limits are applied per IP address
- Rate limit headers are included in responses

## Security Features

### Request Security
- HTTPS enforcement in production
- Content-Type validation
- Request size limits (16MB max)
- Input sanitization
- SQL injection prevention

### Response Security Headers
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 1; mode=block`
- `Strict-Transport-Security` (production)
- `Content-Security-Policy`
- `Referrer-Policy: strict-origin-when-cross-origin`

### Token Security
- JWT tokens with expiration
- Token blacklisting on logout
- Refresh token rotation
- Secure token storage recommendations

## Health Check

#### GET /health

Check system health status.

**Response (200):**
```json
{
  "status": "healthy",
  "timestamp": "2025-01-01T12:00:00Z",
  "services": {
    "mongodb": "healthy",
    "redis": "healthy"
  }
}
```

## Sample Data

For development and testing, the system includes sample data:

### Sample Users
- **admin** / Admin123! (Administrator)
- **manager** / Manager123! (Lab Manager)
- **operator** / Operator123! (Lab Operator)
- **viewer** / Viewer123! (Data Viewer)

### Sample Devices
- ESP32_LAB_001 (Main Lab Device)
- ESP32_LAB_002 (Quality Control Device)
- ESP32_FIELD_001 (Field Testing Device)

## CLI Commands

The backend includes CLI commands for management:

```bash
# Initialize database with sample data
flask init-db

# Clean up sample data
flask cleanup-db

# Show database statistics
flask db-stats

# Perform health check
flask health-check
```

## Development Setup

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Set environment variables:
```bash
export FLASK_ENV=development
export MONGODB_URI=mongodb://localhost:27017/ayusure
export REDIS_URL=redis://localhost:6379/0
export SECRET_KEY=your-secret-key
```

3. Initialize database:
```bash
flask init-db
```

4. Run development server:
```bash
python run.py
```

## Testing

Run tests with pytest:
```bash
pytest backend/tests/ -v
```

Or use the simple test runner:
```bash
python backend/test_runner.py
```