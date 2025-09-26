#!/bin/bash

# AyuSure Backend Deployment Script
# This script handles deployment for development, staging, and production environments

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
ENVIRONMENT="development"
SKIP_TESTS=false
SKIP_BACKUP=false
FORCE_REBUILD=false

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -e, --environment ENV    Set environment (development|staging|production) [default: development]"
    echo "  -t, --skip-tests        Skip running tests"
    echo "  -b, --skip-backup       Skip database backup (production only)"
    echo "  -f, --force-rebuild     Force rebuild of Docker images"
    echo "  -h, --help              Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 -e production                    # Deploy to production"
    echo "  $0 -e staging -t                    # Deploy to staging, skip tests"
    echo "  $0 -e development -f                # Development deploy with force rebuild"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        -t|--skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        -b|--skip-backup)
            SKIP_BACKUP=true
            shift
            ;;
        -f|--force-rebuild)
            FORCE_REBUILD=true
            shift
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Validate environment
if [[ ! "$ENVIRONMENT" =~ ^(development|staging|production)$ ]]; then
    print_error "Invalid environment: $ENVIRONMENT"
    print_error "Must be one of: development, staging, production"
    exit 1
fi

print_status "Starting deployment for environment: $ENVIRONMENT"

# Check if Docker and Docker Compose are installed
if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed. Please install Docker first."
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    print_error "Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

# Check if we're in the right directory
if [[ ! -f "docker-compose.yml" ]]; then
    print_error "docker-compose.yml not found. Please run this script from the project root."
    exit 1
fi

# Set environment-specific variables
case $ENVIRONMENT in
    "development")
        COMPOSE_FILE="docker-compose.yml"
        ENV_FILE=".env"
        ;;
    "staging")
        COMPOSE_FILE="docker-compose.yml"
        ENV_FILE=".env.staging"
        ;;
    "production")
        COMPOSE_FILE="docker-compose.yml"
        ENV_FILE=".env.production"
        ;;
esac

# Check if environment file exists
if [[ ! -f "$ENV_FILE" ]]; then
    print_warning "Environment file $ENV_FILE not found. Using .env.docker as template."
    if [[ -f ".env.docker" ]]; then
        cp .env.docker "$ENV_FILE"
        print_warning "Please edit $ENV_FILE with your environment-specific settings."
    else
        print_error "No environment template found. Please create $ENV_FILE"
        exit 1
    fi
fi

# Load environment variables
export $(grep -v '^#' "$ENV_FILE" | xargs)

# Pre-deployment checks
print_status "Running pre-deployment checks..."

# Check if required directories exist
mkdir -p logs
mkdir -p backend/instance/reports

# Validate environment variables for production
if [[ "$ENVIRONMENT" == "production" ]]; then
    if [[ "$SECRET_KEY" == "your-secret-key-change-in-production" ]] || [[ "$JWT_SECRET_KEY" == "your-jwt-secret-key-change-in-production" ]]; then
        print_error "Production deployment requires secure SECRET_KEY and JWT_SECRET_KEY"
        print_error "Please update your $ENV_FILE with secure random keys"
        exit 1
    fi
fi

# Run tests if not skipped
if [[ "$SKIP_TESTS" == false ]]; then
    print_status "Running tests..."
    
    # Build test image
    docker build -t ayusure-backend-test --target development .
    
    # Run tests in container
    if docker run --rm \
        -v "$(pwd)/backend:/app/backend" \
        -e FLASK_ENV=testing \
        ayusure-backend-test \
        python -m pytest backend/tests/ -v; then
        print_success "All tests passed"
    else
        print_error "Tests failed. Deployment aborted."
        exit 1
    fi
fi

# Backup database for production
if [[ "$ENVIRONMENT" == "production" ]] && [[ "$SKIP_BACKUP" == false ]]; then
    print_status "Creating database backup..."
    
    # Create backup directory
    BACKUP_DIR="backups/$(date +%Y%m%d_%H%M%S)"
    mkdir -p "$BACKUP_DIR"
    
    # Backup MongoDB
    if docker-compose exec -T mongodb mongodump --out /tmp/backup; then
        docker cp $(docker-compose ps -q mongodb):/tmp/backup "$BACKUP_DIR/mongodb"
        print_success "Database backup created: $BACKUP_DIR"
    else
        print_warning "Database backup failed, but continuing deployment"
    fi
fi

# Stop existing services
print_status "Stopping existing services..."
docker-compose --env-file "$ENV_FILE" down

# Build images
BUILD_ARGS=""
if [[ "$FORCE_REBUILD" == true ]]; then
    BUILD_ARGS="--no-cache"
fi

print_status "Building Docker images..."
docker-compose --env-file "$ENV_FILE" build $BUILD_ARGS

# Start services
print_status "Starting services..."

# Start infrastructure services first
docker-compose --env-file "$ENV_FILE" up -d mongodb redis

# Wait for MongoDB to be ready
print_status "Waiting for MongoDB to be ready..."
timeout=60
while ! docker-compose --env-file "$ENV_FILE" exec -T mongodb mongo --eval "db.adminCommand('ismaster')" &>/dev/null; do
    sleep 2
    timeout=$((timeout - 2))
    if [[ $timeout -le 0 ]]; then
        print_error "MongoDB failed to start within 60 seconds"
        exit 1
    fi
done

# Wait for Redis to be ready
print_status "Waiting for Redis to be ready..."
timeout=30
while ! docker-compose --env-file "$ENV_FILE" exec -T redis redis-cli ping &>/dev/null; do
    sleep 2
    timeout=$((timeout - 2))
    if [[ $timeout -le 0 ]]; then
        print_error "Redis failed to start within 30 seconds"
        exit 1
    fi
done

# Start application services
docker-compose --env-file "$ENV_FILE" up -d backend celery_worker celery_beat

# Wait for backend to be ready
print_status "Waiting for backend to be ready..."
timeout=120
while ! curl -f http://localhost:5000/health &>/dev/null; do
    sleep 5
    timeout=$((timeout - 5))
    if [[ $timeout -le 0 ]]; then
        print_error "Backend failed to start within 120 seconds"
        docker-compose --env-file "$ENV_FILE" logs backend
        exit 1
    fi
done

# Start monitoring services if requested
if [[ "$ENVIRONMENT" != "production" ]]; then
    print_status "Starting monitoring services..."
    docker-compose --env-file "$ENV_FILE" --profile monitoring up -d flower
fi

# Health check
print_status "Performing health checks..."

# Check backend health
if curl -f http://localhost:5000/health | jq -e '.status == "healthy"' &>/dev/null; then
    print_success "Backend health check passed"
else
    print_error "Backend health check failed"
    docker-compose --env-file "$ENV_FILE" logs backend
    exit 1
fi

# Check Celery worker
if docker-compose --env-file "$ENV_FILE" exec -T celery_worker celery -A backend.tasks.ai_analysis.celery inspect ping &>/dev/null; then
    print_success "Celery worker health check passed"
else
    print_warning "Celery worker health check failed"
fi

# Post-deployment tasks
print_status "Running post-deployment tasks..."

# Create initial data if this is a fresh deployment
if [[ "$ENVIRONMENT" == "development" ]]; then
    print_status "Setting up development data..."
    # The mongo-init.js script handles initial data creation
fi

# Show deployment summary
print_success "Deployment completed successfully!"
echo ""
echo "Environment: $ENVIRONMENT"
echo "Services running:"
docker-compose --env-file "$ENV_FILE" ps

echo ""
echo "Useful commands:"
echo "  View logs:           docker-compose --env-file $ENV_FILE logs -f"
echo "  Stop services:       docker-compose --env-file $ENV_FILE down"
echo "  Restart backend:     docker-compose --env-file $ENV_FILE restart backend"
echo "  Access MongoDB:      docker-compose --env-file $ENV_FILE exec mongodb mongo"
echo "  Access Redis:        docker-compose --env-file $ENV_FILE exec redis redis-cli"

if [[ "$ENVIRONMENT" != "production" ]]; then
    echo "  Monitor Celery:      http://localhost:5555 (Flower)"
fi

echo ""
echo "API Endpoints:"
echo "  Health Check:        http://localhost:5000/health"
echo "  API Documentation:   http://localhost:5000/api/docs (if enabled)"
echo "  Authentication:      http://localhost:5000/api/auth/login"

if [[ "$ENVIRONMENT" == "development" ]]; then
    echo ""
    echo "Default credentials:"
    echo "  Username: admin"
    echo "  Password: admin123"
    echo ""
    print_warning "Remember to change the default password!"
fi

print_success "Deployment completed successfully!"