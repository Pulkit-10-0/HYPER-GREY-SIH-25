"""
Sensor Data Collection API Resources
Handles ESP32 sensor data ingestion and processing
"""
import logging
from datetime import datetime
from flask import request, current_app
from flask_restful import Resource
from flask_jwt_extended import jwt_required, get_jwt_identity, get_jwt
from bson import ObjectId
from marshmallow import Schema, fields, ValidationError as MarshmallowValidationError, validate

from backend.utils.exceptions import ValidationError, AuthorizationError, NotFoundError
from backend.services.sensor_service import SensorService
from backend.middleware.auth_middleware import require_permission
from backend.tasks.ai_analysis import analyze_sensor_data


class SensorReadingSchema(Schema):
    """Schema for sensor reading validation"""
    device_id = fields.Str(required=True, validate=validate.Length(min=1, max=50))
    measurement_id = fields.Str(required=True, validate=validate.Length(min=1, max=100))
    raw_readings = fields.Dict(required=True)
    sample_metadata = fields.Nested({
        'herb_name': fields.Str(required=True, validate=validate.Length(min=1, max=100)),
        'batch_id': fields.Str(required=False, validate=validate.Length(max=50)),
        'sample_weight': fields.Float(required=False, validate=validate.Range(0.1, 1000)),
        'preparation_method': fields.Str(required=False, validate=validate.Length(max=200)),
        'collection_notes': fields.Str(required=False, validate=validate.Length(max=500))
    }, required=True)
    environmental_conditions = fields.Nested({
        'temperature': fields.Float(required=False, validate=validate.Range(-40, 85)),
        'humidity': fields.Float(required=False, validate=validate.Range(0, 100)),
        'pressure': fields.Float(required=False, validate=validate.Range(300, 1100))
    }, required=False)
    timestamp = fields.DateTime(required=False, missing=datetime.utcnow)


class SensorDataResource(Resource):
    """Handle sensor data collection from ESP32 devices"""
    
    def __init__(self):
        self.sensor_service = SensorService()
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    @require_permission('data.create')
    def post(self):
        """
        Collect sensor data from ESP32 device
        ---
        tags:
          - Sensor Data
        security:
          - Bearer: []
        parameters:
          - in: body
            name: sensor_reading
            schema:
              type: object
              required:
                - device_id
                - measurement_id
                - raw_readings
                - sample_metadata
              properties:
                device_id:
                  type: string
                  description: Device identifier
                measurement_id:
                  type: string
                  description: Unique measurement identifier
                raw_readings:
                  type: object
                  description: Raw sensor readings
                sample_metadata:
                  type: object
                  properties:
                    herb_name:
                      type: string
                    batch_id:
                      type: string
                    sample_weight:
                      type: number
                    preparation_method:
                      type: string
                    collection_notes:
                      type: string
                environmental_conditions:
                  type: object
                  properties:
                    temperature:
                      type: number
                    humidity:
                      type: number
                    pressure:
                      type: number
        responses:
          201:
            description: Sensor data collected successfully
          400:
            description: Invalid sensor data
          401:
            description: Unauthorized
          403:
            description: Insufficient permissions
        """
        try:
            # Validate request data
            schema = SensorReadingSchema()
            try:
                data = schema.load(request.get_json() or {})
            except MarshmallowValidationError as e:
                raise ValidationError("Invalid sensor data", details=e.messages)
            
            # Get current user info
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Verify device exists and user has access
            device = self.sensor_service.verify_device_access(
                data['device_id'], user_role, organization_id
            )
            
            # Store sensor reading
            reading = self.sensor_service.store_sensor_reading(data, device)
            
            # Trigger AI analysis asynchronously
            analyze_sensor_data.delay(str(reading['_id']))
            
            self.logger.info(f"Sensor data collected: {data['measurement_id']} from device {data['device_id']}")
            
            return {
                'reading_id': str(reading['_id']),
                'measurement_id': data['measurement_id'],
                'status': 'collected',
                'analysis_queued': True,
                'message': 'Sensor data collected and queued for analysis'
            }, 201
            
        except (ValidationError, AuthorizationError, NotFoundError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Error collecting sensor data: {str(e)}")
            raise ValidationError("Failed to collect sensor data")


class SensorDataListResource(Resource):
    """Handle sensor data listing and filtering"""
    
    def __init__(self):
        self.sensor_service = SensorService()
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    @require_permission('data.read')
    def get(self):
        """
        Get sensor readings with pagination and filtering
        ---
        tags:
          - Sensor Data
        security:
          - Bearer: []
        parameters:
          - in: query
            name: page
            type: integer
            default: 1
          - in: query
            name: per_page
            type: integer
            default: 20
          - in: query
            name: device_id
            type: string
          - in: query
            name: herb_name
            type: string
          - in: query
            name: start_date
            type: string
            format: date-time
          - in: query
            name: end_date
            type: string
            format: date-time
        responses:
          200:
            description: List of sensor readings
        """
        try:
            # Get query parameters
            page = int(request.args.get('page', 1))
            per_page = min(int(request.args.get('per_page', 20)), 100)
            device_id = request.args.get('device_id')
            herb_name = request.args.get('herb_name')
            start_date = request.args.get('start_date')
            end_date = request.args.get('end_date')
            
            # Get current user info
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Build filters
            filters = {}
            
            if device_id:
                filters['device_id'] = device_id
            
            if herb_name:
                filters['sample_metadata.herb_name'] = {'$regex': herb_name, '$options': 'i'}
            
            if start_date or end_date:
                date_filter = {}
                if start_date:
                    date_filter['$gte'] = datetime.fromisoformat(start_date.replace('Z', '+00:00'))
                if end_date:
                    date_filter['$lte'] = datetime.fromisoformat(end_date.replace('Z', '+00:00'))
                filters['timestamp'] = date_filter
            
            # Get sensor readings
            result = self.sensor_service.get_sensor_readings(
                filters=filters,
                page=page,
                per_page=per_page,
                user_role=user_role,
                organization_id=organization_id
            )
            
            return {
                'readings': result['readings'],
                'pagination': result['pagination']
            }, 200
            
        except Exception as e:
            self.logger.error(f"Error fetching sensor readings: {str(e)}")
            raise ValidationError("Failed to fetch sensor readings")


class SensorDataDetailResource(Resource):
    """Handle individual sensor reading operations"""
    
    def __init__(self):
        self.sensor_service = SensorService()
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    @require_permission('data.read')
    def get(self, reading_id):
        """
        Get sensor reading details
        ---
        tags:
          - Sensor Data
        security:
          - Bearer: []
        parameters:
          - in: path
            name: reading_id
            type: string
            required: true
        responses:
          200:
            description: Sensor reading details
          404:
            description: Reading not found
        """
        try:
            # Get current user info
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Get sensor reading
            reading = self.sensor_service.get_sensor_reading_by_id(
                reading_id, user_role, organization_id
            )
            
            if not reading:
                raise NotFoundError("Sensor reading not found")
            
            return {'reading': reading}, 200
            
        except (NotFoundError, AuthorizationError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Error fetching sensor reading {reading_id}: {str(e)}")
            raise ValidationError("Failed to fetch sensor reading")