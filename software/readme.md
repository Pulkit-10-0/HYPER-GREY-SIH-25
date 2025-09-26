# AyuSure - Herbal Quality Analysis System

A comprehensive system for analyzing herbal medicine quality using AI-powered analysis of sensor data from ESP32 devices.

## Project Overview

AyuSure is a complete solution for herbal quality control that combines IoT sensors, machine learning, and web technologies to provide real-time analysis of herbal samples. The system detects authenticity, measures quality parameters, and identifies contamination in herbal medicines.

## Architecture

The system consists of three main components:

### 1. Backend API (Flask)
- RESTful API built with Flask and Flask-RESTful
- JWT-based authentication with role-based access control
- MongoDB for data storage with Redis for caching
- Celery for background task processing
- WebSocket support for real-time communication
- AI model integration for herbal analysis

### 2. Frontend Dashboard (Next.js)
- Modern React-based dashboard built with Next.js
- Real-time data visualization and monitoring
- Device management and configuration interface
- Report generation and download capabilities
- Responsive design for desktop and mobile

### 3. ESP32 IoT Devices
- Custom sensor arrays for herbal sample analysis
- Multi-channel electrode measurements
- Color sensor integration
- Environmental condition monitoring
- WiFi connectivity for data transmission

## How the Backend Was Built

The backend development followed a systematic approach to create a robust, scalable API:

### Phase 1: Core Infrastructure
- Set up Flask application factory pattern for modularity
- Configured MongoDB with optimized indexing strategy
- Integrated Redis for caching and session management
- Implemented comprehensive error handling and logging
- Created database models and connection management

### Phase 2: Authentication & Security
- Built JWT-based authentication system with refresh tokens
- Implemented role-based access control (Admin, Manager, Operator, Viewer)
- Created security middleware for input validation
- Added rate limiting and request size controls
- Implemented organization-level data isolation

### Phase 3: Device Management
- Developed ESP32 device registration and management
- Created device status monitoring and real-time updates
- Implemented calibration management with history tracking
- Added location-based device organization
- Built comprehensive device filtering and pagination

### Phase 4: Sensor Data Processing
- Created endpoints for ESP32 sensor data collection
- Implemented data validation and sanitization
- Built time-series data storage with automatic cleanup
- Added sensor reading retrieval with filtering capabilities
- Integrated environmental condition monitoring

### Phase 5: AI Integration
- Integrated machine learning models for herbal analysis
- Built AI service wrapper for model management
- Implemented feature extraction from sensor readings
- Created asynchronous analysis processing with Celery
- Added quality scoring and grading algorithms

### Phase 6: Analysis & Results
- Developed analysis result storage and retrieval
- Implemented authenticity detection algorithms
- Created contamination detection and risk assessment
- Built intelligent recommendation generation
- Added analysis statistics and summary endpoints

### Phase 7: Real-time Communication
- Integrated Flask-SocketIO for WebSocket support
- Implemented client authentication for WebSocket connections
- Created device-specific subscription management
- Built real-time event broadcasting system
- Added system-wide alert and notification capabilities

### Phase 8: Reporting System
- Built comprehensive report generation (PDF, CSV, JSON)
- Implemented quality summaries and device status reports
- Created analysis results and calibration reports
- Added date range filtering and data aggregation
- Built report caching and download management

### Phase 9: Production Deployment
- Created multi-stage Docker builds for optimization
- Built complete Docker Compose orchestration
- Implemented automated deployment scripts
- Added database initialization with sample data
- Created comprehensive monitoring and health checks

## Technology Stack

### Backend Technologies
- **Framework**: Flask 2.3.3 with Flask-RESTful
- **Database**: MongoDB 6.0 with optimized indexing
- **Cache**: Redis 7.0 for session and task management
- **Authentication**: JWT with Flask-JWT-Extended
- **Background Tasks**: Celery with Redis broker
- **WebSockets**: Flask-SocketIO for real-time communication
- **AI/ML**: scikit-learn, NumPy, pandas for analysis
- **Validation**: Marshmallow for input validation
- **Documentation**: Comprehensive API documentation
- **Testing**: pytest with coverage reporting
- **Deployment**: Docker with multi-stage builds

### Development Tools
- **Code Quality**: Black formatter, Flake8 linter
- **Environment**: Python virtual environments
- **Version Control**: Git with structured commits
- **CLI Tools**: Custom Flask CLI commands
- **Logging**: Structured logging with configurable levels
- **Monitoring**: Health checks and performance metrics

## Key Features Implemented

### Authentication & Authorization
- JWT token-based authentication with secure refresh mechanism
- Role-based permissions system with granular access control
- Organization-level data isolation for multi-tenancy
- Token blacklisting for secure logout functionality
- Password hashing with industry-standard algorithms

### Device Management
- ESP32 device registration with validation
- Real-time device status monitoring and updates
- Calibration management with complete history tracking
- Location-based device organization and filtering
- Device statistics and performance monitoring

### AI-Powered Analysis
- Machine learning model integration for herbal analysis
- Authenticity detection with confidence scoring
- Quality assessment with A-F grading system
- Contamination detection with risk level assessment
- Intelligent recommendation generation based on results

### Data Management
- Time-series sensor data storage with automatic cleanup
- Comprehensive analysis result storage and retrieval
- Flexible filtering and pagination for large datasets
- Data validation and sanitization at all entry points
- Audit trails and data lineage tracking

### Real-time Communication
- WebSocket authentication and room management
- Device-specific subscription and notification system
- Live updates for analysis completion and device status
- System-wide alerts and broadcasting capabilities
- Connection management with automatic reconnection

### Reporting & Analytics
- Multi-format report generation (PDF, CSV, JSON)
- Quality summaries with statistical analysis
- Device status reports with performance metrics
- Analysis results with detailed predictions
- Calibration reports with maintenance scheduling

### Production Features
- Docker containerization with multi-stage builds
- Automated deployment with health verification
- Comprehensive monitoring and alerting
- Database backup and recovery procedures
- Performance optimization and caching strategies

## Project Structure```
ay
usure-backend/
├── backend/                    # Flask backend application
│   ├── app.py                 # Application factory and configuration
│   ├── config.py              # Environment-specific configurations
│   ├── run.py                 # Development server runner
│   ├── cli.py                 # Custom CLI commands
│   ├── requirements.txt       # Python dependencies
│   ├── models/                # Database models and managers
│   │   └── database.py       # MongoDB connection and indexing
│   ├── resources/             # API endpoint definitions
│   │   ├── auth.py           # Authentication endpoints
│   │   ├── devices.py        # Device management endpoints
│   │   ├── sensor_data.py    # Sensor data collection endpoints
│   │   ├── analysis.py       # Analysis result endpoints
│   │   └── reports.py        # Report generation endpoints
│   ├── services/              # Business logic layer
│   │   ├── auth_service.py   # Authentication business logic
│   │   ├── device_service.py # Device management logic
│   │   ├── sensor_service.py # Sensor data processing
│   │   ├── ai_service.py     # AI model integration
│   │   └── report_service.py # Report generation logic
│   ├── tasks/                 # Background task processing
│   │   └── ai_analysis.py    # Celery tasks for AI analysis
│   ├── middleware/            # Custom middleware components
│   │   ├── auth_middleware.py    # Authentication middleware
│   │   └── security_middleware.py # Security middleware
│   ├── utils/                 # Utility functions and helpers
│   │   ├── exceptions.py     # Custom exception classes
│   │   ├── validation.py     # Input validation schemas
│   │   ├── security.py       # Security utilities
│   │   ├── permissions.py    # RBAC permission system
│   │   ├── logging_config.py # Logging configuration
│   │   └── sample_data.py    # Sample data generator
│   ├── websocket/             # WebSocket event handlers
│   │   └── events.py         # Real-time event management
│   └── tests/                 # Test suite
│       ├── test_app.py       # Application tests
│       ├── test_auth.py      # Authentication tests
│       └── test_devices.py   # Device management tests
├── ai-models/                  # Machine learning models
│   ├── authenticity_model.joblib
│   ├── quality_model.joblib
│   ├── contamination_model.joblib
│   └── feature_scaler.joblib
├── scripts/                    # Deployment and utility scripts
│   ├── deploy.sh             # Automated deployment script
│   └── mongo-init.js         # Database initialization script
├── docker-compose.yml          # Docker orchestration configuration
├── Dockerfile                  # Multi-stage Docker build
├── .env.docker                # Docker environment template
└── DEPLOYMENT.md              # Comprehensive deployment guide
```

## API Endpoints

### System Health
- `GET /health` - System health status and service availability

### Authentication
- `POST /api/auth/login` - User authentication with JWT token generation
- `POST /api/auth/refresh` - Access token refresh using refresh token
- `POST /api/auth/logout` - Secure logout with token blacklisting
- `GET /api/auth/profile` - Current user profile information

### Device Management
- `GET /api/devices` - List devices with pagination and filtering
- `POST /api/devices` - Register new ESP32 device
- `GET /api/devices/{device_id}` - Retrieve device details
- `PUT /api/devices/{device_id}` - Update device information
- `DELETE /api/devices/{device_id}` - Deactivate device
- `POST /api/devices/{device_id}/calibration` - Update device calibration
- `GET /api/devices/{device_id}/calibration` - Retrieve calibration history

### Sensor Data Collection
- `POST /api/sensor-data` - Submit sensor readings from ESP32 devices
- `GET /api/sensor-data/list` - List sensor readings with filtering options
- `GET /api/sensor-data/{reading_id}` - Retrieve detailed sensor reading

### AI Analysis
- `GET /api/analysis/results` - List analysis results with filtering
- `GET /api/analysis/results/{result_id}` - Retrieve detailed analysis result
- `GET /api/analysis/stats` - Analysis statistics and summaries
- `POST /api/analysis/reanalyze/{reading_id}` - Request reanalysis of reading

### Report Generation
- `POST /api/reports/generate` - Generate reports in multiple formats
- `GET /api/reports` - List generated reports with metadata
- `GET /api/reports/{report_id}` - Retrieve report information
- `GET /api/reports/{report_id}/download` - Download report file
- `DELETE /api/reports/{report_id}` - Delete report
- `GET /api/reports/stats` - Report generation statistics

## Development Methodology

### 1. Requirements Analysis
- Analyzed herbal quality control requirements
- Identified key stakeholders and user roles
- Defined functional and non-functional requirements
- Created comprehensive system specifications

### 2. System Design
- Designed modular architecture with clear separation of concerns
- Created database schema optimized for time-series data
- Planned API endpoints following RESTful principles
- Designed security model with role-based access control

### 3. Infrastructure Setup
- Set up development environment with virtual environments
- Configured MongoDB with proper indexing strategy
- Integrated Redis for caching and background task management
- Implemented comprehensive logging and error handling

### 4. Iterative Development
- Built core functionality in phases
- Implemented comprehensive testing for each component
- Added features incrementally with proper validation
- Maintained backward compatibility throughout development

### 5. Quality Assurance
- Implemented comprehensive input validation
- Added security measures at all levels
- Created extensive test coverage
- Performed security audits and vulnerability assessments

### 6. Documentation
- Created comprehensive API documentation
- Wrote detailed deployment guides
- Added inline code documentation
- Provided troubleshooting guides and examples

## Quick Start

### Using Docker (Recommended)
```bash
# Clone the repository
git clone <repository-url>
cd ayusure-backend

# Configure environment
cp .env.docker .env
# Edit .env with your secure keys

# Deploy the system
./scripts/deploy.sh -e development

# Access the API
curl http://localhost:5000/health
```

### Manual Setup
```bash
# Set up Python environment
cd backend
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt

# Configure environment
cp .env.example .env
# Edit .env with your database connections

# Start infrastructure services
# MongoDB and Redis must be running

# Initialize database
flask init-db

# Start the application
python run.py
```

## Default Credentials
- Username: admin
- Password: admin123
- Organization: Default Organization

**Important**: Change the default password after first login.

## Environment Variables

### Required Configuration
```bash
# Application
FLASK_ENV=development
SECRET_KEY=your-secret-key
JWT_SECRET_KEY=your-jwt-secret-key

# Database
MONGODB_URI=mongodb://localhost:27017/ayusure
REDIS_URL=redis://localhost:6379/0

# Celery
CELERY_BROKER_URL=redis://localhost:6379/1
CELERY_RESULT_BACKEND=redis://localhost:6379/2

# Security
CORS_ORIGINS=http://localhost:3000
```

## Testing

### Running Tests
```bash
# Run all tests
pytest

# Run with coverage
pytest --cov=backend

# Run specific test categories
pytest backend/tests/test_auth.py -v
pytest backend/tests/test_devices.py -v
```

### Test Coverage
The test suite covers:
- Authentication and authorization
- Device management operations
- API endpoint functionality
- Database operations
- Security validations
- Error handling scenarios

## Deployment

### Development Deployment
```bash
./scripts/deploy.sh -e development
```

### Production Deployment
```bash
./scripts/deploy.sh -e production
```

### Manual Deployment
Refer to `DEPLOYMENT.md` for comprehensive deployment instructions including:
- System requirements and setup
- Database configuration
- Security hardening
- Performance optimization
- Monitoring and maintenance

## Monitoring and Maintenance

### Health Monitoring
- System health checks at `/health` endpoint
- Database connectivity monitoring
- Redis availability verification
- Celery worker health checks

### Performance Monitoring
- API response time tracking
- Database query performance analysis
- Memory and CPU usage monitoring
- Background task processing metrics

### Logging
- Structured logging with configurable levels
- Request and response logging
- Error tracking with stack traces
- Performance metrics logging

## Security Features

### Authentication Security
- JWT tokens with configurable expiration
- Secure password hashing with salt
- Token blacklisting for secure logout
- Session management with Redis

### Data Protection
- Input validation and sanitization
- SQL injection prevention
- XSS protection headers
- Rate limiting per user and IP
- Request size limitations

### Infrastructure Security
- Docker container isolation
- Environment variable management
- Secure default configurations
- HTTPS enforcement in production


## Troubleshooting

### Common Issues
1. **Database Connection Failed**: Check MongoDB status and connection string
2. **Redis Connection Failed**: Verify Redis service and configuration
3. **Celery Tasks Not Processing**: Check worker status and broker connection
4. **AI Analysis Failing**: Verify model files and dependencies

