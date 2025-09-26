"""
Flask Application Factory for AyuSure Backend API
"""
import os
import logging
from flask import Flask, jsonify, request
from flask_restful import Api
from flask_jwt_extended import JWTManager
from flask_socketio import SocketIO
from flask_cors import CORS
from flask_limiter import Limiter
from flask_limiter.util import get_remote_address
from pymongo import MongoClient
import redis
from datetime import datetime

from backend.config import config
from backend.models.database import DatabaseManager
from backend.utils.exceptions import APIException
from backend.utils.logging_config import setup_logging
from backend.cli import register_commands
from backend.middleware.security_middleware import SecurityMiddleware


def create_app(config_name=None):
    """Application factory pattern for Flask app creation"""
    if config_name is None:
        config_name = os.environ.get('FLASK_ENV', 'development')
    
    app = Flask(__name__)
    app.config.from_object(config[config_name])
    
    # Setup logging
    logger = setup_logging(app)
    
    # Initialize extensions
    api = Api(app)
    jwt = JWTManager(app)
    socketio = SocketIO(app, cors_allowed_origins="*", async_mode='threading')
    limiter = Limiter(
        app,
        key_func=get_remote_address,
        default_limits=["200 per day", "50 per hour"]
    )
    CORS(app, origins=app.config.get('CORS_ORIGINS', ['http://localhost:3000']))
    
    # Initialize security middleware
    security_middleware = SecurityMiddleware(app)
    
    # Database connections with error handling
    try:
        mongo_client = MongoClient(
            app.config['MONGODB_URI'],
            serverSelectionTimeoutMS=5000,
            connectTimeoutMS=10000,
            socketTimeoutMS=20000
        )
        # Test connection
        mongo_client.admin.command('ping')
        app.logger.info("MongoDB connection established successfully")
    except Exception as e:
        app.logger.error(f"MongoDB connection failed: {str(e)}")
        raise APIException("Database connection failed", 500)
    
    try:
        redis_client = redis.from_url(
            app.config['REDIS_URL'],
            decode_responses=True,
            socket_connect_timeout=5,
            socket_timeout=5
        )
        # Test connection
        redis_client.ping()
        app.logger.info("Redis connection established successfully")
    except Exception as e:
        app.logger.error(f"Redis connection failed: {str(e)}")
        raise APIException("Cache connection failed", 500)
    
    # Initialize database manager
    db_manager = DatabaseManager(mongo_client)
    db_manager.setup_indexes()
    
    # Store connections in app context
    app.mongo = mongo_client
    app.redis = redis_client
    app.db = db_manager
    
    # Configure JWT
    @jwt.expired_token_loader
    def expired_token_callback(jwt_header, jwt_payload):
        return jsonify({'error': {'message': 'Token has expired'}}), 401
    
    @jwt.invalid_token_loader
    def invalid_token_callback(error):
        return jsonify({'error': {'message': 'Invalid token'}}), 401
    
    @jwt.unauthorized_loader
    def missing_token_callback(error):
        return jsonify({'error': {'message': 'Authorization token required'}}), 401
    
    # Global error handlers
    @app.errorhandler(APIException)
    def handle_api_exception(error):
        response = {
            'error': {
                'code': error.__class__.__name__,
                'message': error.message,
                'details': error.details,
                'timestamp': datetime.utcnow().isoformat(),
                'request_id': request.headers.get('X-Request-ID', 'unknown')
            }
        }
        return jsonify(response), error.code
    
    @app.errorhandler(404)
    def not_found(error):
        return jsonify({
            'error': {
                'code': 'NOT_FOUND',
                'message': 'Resource not found',
                'timestamp': datetime.utcnow().isoformat()
            }
        }), 404
    
    @app.errorhandler(500)
    def internal_error(error):
        return jsonify({
            'error': {
                'code': 'INTERNAL_ERROR',
                'message': 'Internal server error',
                'timestamp': datetime.utcnow().isoformat()
            }
        }), 500
    
    # Health check endpoint
    @app.route('/health')
    def health_check():
        try:
            # Check MongoDB
            app.mongo.admin.command('ping')
            mongo_status = 'healthy'
        except:
            mongo_status = 'unhealthy'
        
        try:
            # Check Redis
            app.redis.ping()
            redis_status = 'healthy'
        except:
            redis_status = 'unhealthy'
        
        overall_status = 'healthy' if mongo_status == 'healthy' and redis_status == 'healthy' else 'unhealthy'
        
        return jsonify({
            'status': overall_status,
            'timestamp': datetime.utcnow().isoformat(),
            'services': {
                'mongodb': mongo_status,
                'redis': redis_status
            }
        }), 200 if overall_status == 'healthy' else 503
    
    # Register API resources
    register_resources(api)
    
    # Register WebSocket events
    register_websocket_events(socketio)
    
    # Register CLI commands
    register_commands(app)
    
    # Initialize Celery
    from backend.tasks.ai_analysis import init_celery
    init_celery(app)
    
    return app, socketio


def register_resources(api):
    """Register API resources"""
    from backend.resources.auth import AuthResource, RefreshResource, LogoutResource, UserProfileResource
    from backend.resources.devices import DeviceListResource, DeviceResource, DeviceCalibrationResource
    from backend.resources.sensor_data import SensorDataResource, SensorDataListResource, SensorDataDetailResource
    from backend.resources.analysis import AnalysisResultsResource, AnalysisResultDetailResource, AnalysisStatsResource, ReanalysisResource
    from backend.resources.reports import ReportGenerationResource, ReportListResource, ReportDetailResource, ReportDownloadResource, ReportStatsResource
    
    # Authentication endpoints
    api.add_resource(AuthResource, '/api/auth/login')
    api.add_resource(RefreshResource, '/api/auth/refresh')
    api.add_resource(LogoutResource, '/api/auth/logout')
    api.add_resource(UserProfileResource, '/api/auth/profile')
    
    # Device management endpoints
    api.add_resource(DeviceListResource, '/api/devices')
    api.add_resource(DeviceResource, '/api/devices/<string:device_id>')
    api.add_resource(DeviceCalibrationResource, '/api/devices/<string:device_id>/calibration')
    
    # Sensor data endpoints
    api.add_resource(SensorDataResource, '/api/sensor-data')
    api.add_resource(SensorDataListResource, '/api/sensor-data/list')
    api.add_resource(SensorDataDetailResource, '/api/sensor-data/<string:reading_id>')
    
    # Analysis endpoints
    api.add_resource(AnalysisResultsResource, '/api/analysis/results')
    api.add_resource(AnalysisResultDetailResource, '/api/analysis/results/<string:result_id>')
    api.add_resource(AnalysisStatsResource, '/api/analysis/stats')
    api.add_resource(ReanalysisResource, '/api/analysis/reanalyze/<string:reading_id>')
    
    # Report endpoints
    api.add_resource(ReportGenerationResource, '/api/reports/generate')
    api.add_resource(ReportListResource, '/api/reports')
    api.add_resource(ReportDetailResource, '/api/reports/<string:report_id>')
    api.add_resource(ReportDownloadResource, '/api/reports/<string:report_id>/download')
    api.add_resource(ReportStatsResource, '/api/reports/stats')


def register_websocket_events(socketio):
    """Register WebSocket events"""
    from backend.websocket.events import init_websocket_events
    init_websocket_events(socketio)