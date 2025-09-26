# AyuSure Backend API

A comprehensive Flask-based REST API for the AyuSure herbal quality analysis system with AI-powered analysis, real-time communication, and comprehensive reporting capabilities.

##  Features

### Core Functionality
- **Sensor Data Collection**: ESP32 device integration for herbal sample analysis
- **AI-Powered Analysis**: Machine learning models for authenticity, quality, and contamination detection
- **Real-time Communication**: WebSocket support for live updates and notifications
- **Comprehensive Reporting**: PDF, CSV, and JSON report generation with customizable filters

### Authentication & Authorization
- JWT-based authentication with access and refresh tokens
- Role-based access control (Admin, Manager, Operator, Viewer)
- Organization-level data isolation and multi-tenancy
- Token blacklisting and secure logout

### Device Management
- ESP32 device registration and management
- Device status monitoring and real-time updates
- Calibration management with history tracking
- Location-based device organization and filtering

### AI & Analytics
- Asynchronous AI analysis using Celery background tasks
- Quality score calculation and grading (A-F scale)
- Authenticity verification and confidence scoring
- Contamination detection with risk level assessment
- Intelligent recommendations based on analysis results

### Data Management
- Time-series sensor data storage with automatic cleanup
- Analysis results with detailed predictions and metrics
- Comprehensive audit trails and data lineage
- Flexible filtering and pagination for large datasets

### Security & Validation
- Comprehensive input validation and sanitization
- Rate limiting and request size controls
- Security headers and CORS configuration
- Protection against common web vulnerabilities

### Infrastructure
- MongoDB with optimized indexing strategy
- Redis for caching, session management, and task queuing
- Docker containerization with multi-stage builds
- Health monitoring and automatic failover capabilities

## Project Structure

```
backend/
├── __init__.py                 # Package initialization
├── app.py                      # Flask application factory
├── config.py                   # Configuration management
├── run.py                      # Development server runner
├── cli.py                      # CLI commands for management
├── requirements.txt            # Python dependencies
├── .env.example               # Environment variables template
├── README.md                  # This documentation
├── API_DOCUMENTATION.md       # Detailed API documentation
├── models/                    # Database models and managers
│   ├── __init__.py
│   └── database.py           # MongoDB manager and indexing
├── resources/                 # API endpoints (Flask-RESTful)
│   ├── __init__.py
│   ├── auth.py               # Authentication endpoints
│   ├── devices.py            # Device management endpoints
│   ├── sensor_data.py        # Sensor data collection endpoints
│   ├── analysis.py           # Analysis results endpoints
│   └── reports.py            # Report generation endpoints
├── services/                  # Business logic layer
│   ├── __init__.py
│   ├── auth_service.py       # Authentication business logic
│   ├── device_service.py     # Device management logic
│   ├── sensor_service.py     # Sensor data processing
│   ├── ai_service.py         # AI model integration
│   └── report_service.py     # Report generation logic
├── tasks/                     # Celery background tasks
│   ├── __init__.py
│   └── ai_analysis.py        # AI analysis tasks
├── middleware/                # Custom middleware
│   ├── __init__.py
│   ├── auth_middleware.py    # Authentication middleware
│   └── security_middleware.py # Security middleware
├── utils/                     # Utility functions and helpers
│   ├── __init__.py
│   ├── exceptions.py         # Custom exception classes
│   ├── validation.py         # Input validation schemas
│   ├── security.py           # Security utilities
│   ├── permissions.py        # RBAC permissions system
│   ├── logging_config.py     # Logging configuration
│   └── sample_data.py        # Sample data generator
├── websocket/                 # WebSocket event handlers
│   ├── __init__.py
│   └── events.py             # Real-time event handlers
└── tests/                     # Test suite
    ├── __init__.py
    ├── test_app.py           # Application tests
    ├── test_auth.py          # Authentication tests
    └── test_devices.py       # Device management tests
```

## Quick Start

### Option 1: Docker Deployment (Recommended)

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd ayusure-backend
   ```

2. **Configure environment**:
   ```bash
   cp .env.docker .env
   # Edit .env with your secure keys and settings
   ```

3. **Deploy with Docker**:
   ```bash
   # Make deployment script executable (Linux/Mac)
   chmod +x scripts/deploy.sh
   
   # Deploy to development environment
   ./scripts/deploy.sh -e development
   
   # Or use Docker Compose directly
   docker-compose up -d
   ```

4. **Access the application**:
   - API: http://localhost:5000
   - Health Check: http://localhost:5000/health
   - Celery Monitor: http://localhost:5555 (if monitoring enabled)

### Option 2: Local Development Setup

#### Prerequisites
- Python 3.11+
- MongoDB 6.0+
- Redis 7.0+
- Node.js (for AI model dependencies)

#### Installation

1. **Set up Python environment**:
   ```bash
   cd backend
   python -m venv venv
   source venv/bin/activate  # Windows: venv\Scripts\activate
   pip install -r requirements.txt
   ```

2. **Configure environment**:
   ```bash
   cp .env.example .env
   # Edit .env with your database connections
   ```

3. **Start infrastructure services**:
   ```bash
   # MongoDB (adjust path as needed)
   mongod --dbpath /usr/local/var/mongodb
   
   # Redis
   redis-server
   ```

4. **Initialize database**:
   ```bash
   flask init-db
   ```

5. **Start the application**:
   ```bash
   # Main application
   python run.py
   
   # In separate terminals:
   # Celery worker
   celery -A backend.tasks.ai_analysis.celery worker --loglevel=info
   
   # Celery beat (scheduled tasks)
   celery -A backend.tasks.ai_analysis.celery beat --loglevel=info
   ```

### Default Credentials
- **Username**: admin
- **Password**: admin123
- **Organization**: Default Organization

⚠️ **Important**: Change the default password after first login!

### Environment Variables

Key environment variables (see `.env.example` for complete list):

- `FLASK_ENV`: Application environment (development/testing/production)
- `SECRET_KEY`: Flask secret key for sessions
- `JWT_SECRET_KEY`: JWT token signing key
- `MONGODB_URI`: MongoDB connection string
- `REDIS_URL`: Redis connection string
- `CORS_ORIGINS`: Allowed CORS origins (comma-separated)

## API Endpoints

### System
- `GET /health` - System health status and service availability

### Authentication
- `POST /api/auth/login` - User login with JWT token generation
- `POST /api/auth/refresh` - Refresh access token using refresh token
- `POST /api/auth/logout` - Logout and blacklist tokens
- `GET /api/auth/profile` - Get current user profile

### Device Management
- `GET /api/devices` - List devices with pagination and filtering
- `POST /api/devices` - Register new ESP32 device
- `GET /api/devices/{device_id}` - Get device details
- `PUT /api/devices/{device_id}` - Update device information
- `DELETE /api/devices/{device_id}` - Deactivate device
- `POST /api/devices/{device_id}/calibration` - Update device calibration
- `GET /api/devices/{device_id}/calibration` - Get calibration history

### Sensor Data Collection
- `POST /api/sensor-data` - Submit sensor readings from ESP32
- `GET /api/sensor-data/list` - List sensor readings with filtering
- `GET /api/sensor-data/{reading_id}` - Get detailed sensor reading

### AI Analysis
- `GET /api/analysis/results` - List analysis results with filtering
- `GET /api/analysis/results/{result_id}` - Get detailed analysis result
- `GET /api/analysis/stats` - Get analysis statistics and summaries
- `POST /api/analysis/reanalyze/{reading_id}` - Request reanalysis of reading

### Report Generation
- `POST /api/reports/generate` - Generate new report (PDF/CSV/JSON)
- `GET /api/reports` - List generated reports
- `GET /api/reports/{report_id}` - Get report metadata
- `GET /api/reports/{report_id}/download` - Download report file
- `DELETE /api/reports/{report_id}` - Delete report
- `GET /api/reports/stats` - Get report generation statistics

### WebSocket Events
- `connect` - Authenticate and join organization/device rooms
- `subscribe_device` - Subscribe to device-specific updates
- `get_device_status` - Request real-time device status
- Real-time events: `analysis_complete`, `device_status_update`, `new_reading`

## Database Schema

The system uses MongoDB with the following collections:
- `devices` - ESP32 device registration and management
- `sensor_readings` - Time-series sensor data from devices
- `analysis_results` - AI analysis results and quality metrics
- `users` - User accounts and authentication
- `organizations` - Multi-tenant organization management
- `reports` - Generated quality reports

## Development

### CLI Commands
```bash
# Initialize database with sample data
flask init-db

# Generate additional sample data
flask generate-sample-data --devices 10 --readings 100

# Perform health check
flask health-check

# Clean up old data
flask cleanup-old-data

# Create admin user
flask create-admin-user

# List all users
flask list-users
```

### Running Tests
```bash
# Run all tests
pytest

# Run with coverage
pytest --cov=backend

# Run specific test file
pytest backend/tests/test_auth.py -v
```

### Code Quality
```bash
# Format code
black backend/

# Lint code
flake8 backend/

# Type checking (if using mypy)
mypy backend/
```

### Background Tasks
```bash
# Start Celery worker
celery -A backend.tasks.ai_analysis.celery worker --loglevel=info

# Start Celery beat scheduler
celery -A backend.tasks.ai_analysis.celery beat --loglevel=info

# Monitor tasks with Flower
celery -A backend.tasks.ai_analysis.celery flower
```

## Production Deployment

### Docker Deployment (Recommended)
```bash
# Production deployment
./scripts/deploy.sh -e production

# Staging deployment
./scripts/deploy.sh -e staging

# With custom options
./scripts/deploy.sh -e production --skip-tests --force-rebuild
```

### Manual Deployment
```bash
# Using Gunicorn with eventlet for WebSocket support
gunicorn -w 4 -k eventlet --bind 0.0.0.0:5000 \
  --timeout 120 --access-logfile - --error-logfile - \
  "backend.app:create_app()[0]"

# Environment-specific configuration
export FLASK_ENV=production
export SECRET_KEY="your-secure-secret-key"
export JWT_SECRET_KEY="your-secure-jwt-key"
```

### Scaling Considerations
- **Horizontal Scaling**: Use multiple Gunicorn workers and load balancer
- **Database**: MongoDB replica sets for high availability
- **Caching**: Redis cluster for distributed caching
- **Background Tasks**: Multiple Celery workers across different queues
- **File Storage**: External storage (AWS S3, Google Cloud) for reports

## Monitoring

- Health check endpoint: `/health`
- Database connection monitoring
- Redis connectivity checks
- Performance metrics (to be expanded)

## Security Features

- JWT token-based authentication
- Rate limiting on API endpoints
- CORS protection
- Input validation and sanitization
- Structured error responses
- Request/response logging

## Integration Points

- **Frontend**: CORS-enabled API for Next.js dashboard
- **ESP32 Devices**: REST endpoints for sensor data ingestion
- **AI Models**: Integration with existing ai-models folder
- **Background Tasks**: Celery for asynchronous processing

## AI Model Integration

The system integrates with machine learning models for herbal quality analysis:

### Model Types
- **Authenticity Model**: Detects authentic vs. adulterated herbs
- **Quality Model**: Scores overall quality (0-1 scale, A-F grades)
- **Contamination Model**: Identifies contamination and risk levels

### Model Loading
Models are automatically loaded from the `ai-models/` directory:
```
ai-models/
├── authenticity_model.joblib
├── quality_model.joblib
├── contamination_model.joblib
└── feature_scaler.joblib
```

### Feature Extraction
The system extracts features from sensor readings:
- 8-channel electrode voltages (0-5V)
- RGB color sensor values (0-255)
- Environmental conditions (temperature, humidity, pressure)
- Derived features (means, standard deviations, ratios)

## Monitoring and Observability

### Health Monitoring
- `/health` endpoint with service status checks
- Database connectivity monitoring
- Redis availability checks
- Celery worker health verification

### Logging
- Structured logging with configurable levels
- Request/response logging for API calls
- Error tracking with stack traces
- Performance metrics for AI analysis

### Metrics
- Analysis processing times
- Device activity statistics
- User authentication metrics
- Report generation statistics

## Security Features

### Authentication & Authorization
- JWT tokens with configurable expiration
- Role-based permissions (RBAC)
- Token blacklisting for secure logout
- Organization-level data isolation

### Data Protection
- Input validation and sanitization
- SQL injection prevention (NoSQL)
- XSS protection headers
- Rate limiting per user/IP
- Request size limitations

### Infrastructure Security
- Docker container isolation
- Environment variable management
- Secure default configurations
- HTTPS enforcement (production)

## Troubleshooting

### Common Issues

**Database Connection Failed**
```bash
# Check MongoDB status
docker-compose logs mongodb

# Verify connection string
echo $MONGODB_URI
```

**Redis Connection Failed**
```bash
# Check Redis status
docker-compose logs redis

# Test Redis connectivity
redis-cli ping
```

**Celery Tasks Not Processing**
```bash
# Check worker status
celery -A backend.tasks.ai_analysis.celery inspect active

# View worker logs
docker-compose logs celery_worker
```

**AI Analysis Failing**
```bash
# Check if models are loaded
ls -la ai-models/

# View analysis logs
docker-compose logs backend | grep -i "ai\|analysis"
```

### Performance Optimization
- Database query optimization with proper indexing
- Redis caching for frequently accessed data
- Celery task queues for background processing
- Connection pooling for database connections
- Gzip compression for API responses

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines
- Follow PEP 8 style guidelines
- Write comprehensive tests for new features
- Update documentation for API changes
- Use type hints where appropriate
- Ensure backward compatibility

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support and questions:
- Create an issue in the repository
- Check the troubleshooting section
- Review the API documentation
- Contact the development team

---

