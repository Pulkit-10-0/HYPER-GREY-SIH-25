"""
Device Management API Resources
Handles ESP32 device registration, listing, and management operations
"""
import logging
from datetime import datetime
from flask import request, current_app
from flask_restful import Resource
from flask_jwt_extended import jwt_required, get_jwt_identity, get_jwt
from bson import ObjectId
from marshmallow import Schema, fields, ValidationError as MarshmallowValidationError, validate

from backend.utils.exceptions import ValidationError, AuthorizationError, NotFoundError, ConflictError
from backend.services.device_service import DeviceService
from backend.middleware.auth_middleware import require_permission


class DeviceRegistrationSchema(Schema):
    """Schema for device registration validation"""
    device_id = fields.Str(required=True, validate=validate.Length(min=1, max=50))
    device_model = fields.Str(required=True, validate=validate.Length(min=1, max=100))
    firmware_version = fields.Str(required=True, validate=validate.Length(min=1, max=20))
    location = fields.Nested({
        'latitude': fields.Float(required=False, validate=validate.Range(-90, 90)),
        'longitude': fields.Float(required=False, validate=validate.Range(-180, 180)),
        'address': fields.Str(required=False, validate=validate.Length(max=500))
    }, required=False)
    description = fields.Str(required=False, validate=validate.Length(max=500))


class DeviceUpdateSchema(Schema):
    """Schema for device update validation"""
    device_model = fields.Str(required=False, validate=validate.Length(min=1, max=100))
    firmware_version = fields.Str(required=False, validate=validate.Length(min=1, max=20))
    status = fields.Str(required=False, validate=validate.OneOf(['active', 'inactive', 'maintenance']))
    location = fields.Nested({
        'latitude': fields.Float(required=False, validate=validate.Range(-90, 90)),
        'longitude': fields.Float(required=False, validate=validate.Range(-180, 180)),
        'address': fields.Str(required=False, validate=validate.Length(max=500))
    }, required=False)
    description = fields.Str(required=False, validate=validate.Length(max=500))


class CalibrationSchema(Schema):
    """Schema for device calibration validation"""
    calibration_coefficients = fields.Dict(required=True)
    calibration_notes = fields.Str(required=False, validate=validate.Length(max=1000))
    next_calibration_due = fields.DateTime(required=False)


class DeviceListResource(Resource):
    """Handle device listing and registration"""
    
    def __init__(self):
        self.device_service = DeviceService()
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    @require_permission('device.read')
    def get(self):
        """
        Get list of devices with pagination and filtering
        ---
        tags:
          - Devices
        security:
          - Bearer: []
        parameters:
          - in: query
            name: page
            type: integer
            default: 1
            description: Page number
          - in: query
            name: per_page
            type: integer
            default: 20
            description: Items per page
          - in: query
            name: status
            type: string
            enum: [active, inactive, maintenance]
            description: Filter by device status
          - in: query
            name: device_model
            type: string
            description: Filter by device model
          - in: query
            name: search
            type: string
            description: Search in device ID or description
        responses:
          200:
            description: List of devices
            schema:
              type: object
              properties:
                devices:
                  type: array
                pagination:
                  type: object
          401:
            description: Unauthorized
          403:
            description: Insufficient permissions
        """
        try:
            # Get query parameters
            page = int(request.args.get('page', 1))
            per_page = min(int(request.args.get('per_page', 20)), 100)  # Max 100 items per page
            status = request.args.get('status')
            device_model = request.args.get('device_model')
            search = request.args.get('search')
            
            # Get current user info
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Build filters
            filters = {}
            
            # Organization filter (non-admin users can only see their org's devices)
            if user_role != 'admin':
                filters['organization_id'] = organization_id
            
            if status:
                filters['status'] = status
            
            if device_model:
                filters['device_model'] = device_model
            
            if search:
                filters['$or'] = [
                    {'device_id': {'$regex': search, '$options': 'i'}},
                    {'description': {'$regex': search, '$options': 'i'}}
                ]
            
            # Get devices with pagination
            result = self.device_service.get_devices(
                filters=filters,
                page=page,
                per_page=per_page
            )
            
            return {
                'devices': result['devices'],
                'pagination': result['pagination']
            }, 200
            
        except Exception as e:
            self.logger.error(f"Error fetching devices: {str(e)}")
            raise ValidationError("Failed to fetch devices")
    
    @jwt_required()
    @require_permission('device.create')
    def post(self):
        """
        Register a new device
        ---
        tags:
          - Devices
        security:
          - Bearer: []
        parameters:
          - in: body
            name: device
            schema:
              type: object
              required:
                - device_id
                - device_model
                - firmware_version
              properties:
                device_id:
                  type: string
                  description: Unique device identifier
                device_model:
                  type: string
                  description: Device model name
                firmware_version:
                  type: string
                  description: Firmware version
                location:
                  type: object
                  properties:
                    latitude:
                      type: number
                    longitude:
                      type: number
                    address:
                      type: string
                description:
                  type: string
                  description: Device description
        responses:
          201:
            description: Device registered successfully
            schema:
              type: object
              properties:
                device:
                  type: object
                message:
                  type: string
          400:
            description: Invalid request data
          401:
            description: Unauthorized
          403:
            description: Insufficient permissions
          409:
            description: Device already exists
        """
        try:
            # Validate request data
            schema = DeviceRegistrationSchema()
            try:
                data = schema.load(request.get_json() or {})
            except MarshmallowValidationError as e:
                raise ValidationError("Invalid device data", details=e.messages)
            
            # Get current user info
            user_id = get_jwt_identity()
            jwt_claims = get_jwt()
            organization_id = jwt_claims.get('organization_id')
            
            # Add user and organization info to device data
            data['owner_user_id'] = user_id
            data['organization_id'] = organization_id
            
            # Register device
            device = self.device_service.register_device(data)
            
            self.logger.info(f"Device registered: {data['device_id']} by user {user_id}")
            
            return {
                'device': device,
                'message': 'Device registered successfully'
            }, 201
            
        except (ValidationError, ConflictError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Error registering device: {str(e)}")
            raise ValidationError("Failed to register device")


class DeviceResource(Resource):
    """Handle individual device operations"""
    
    def __init__(self):
        self.device_service = DeviceService()
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    @require_permission('device.read')
    def get(self, device_id):
        """
        Get device details
        ---
        tags:
          - Devices
        security:
          - Bearer: []
        parameters:
          - in: path
            name: device_id
            type: string
            required: true
            description: Device ID
        responses:
          200:
            description: Device details
            schema:
              type: object
              properties:
                device:
                  type: object
          401:
            description: Unauthorized
          403:
            description: Insufficient permissions
          404:
            description: Device not found
        """
        try:
            # Get current user info for authorization
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Get device
            device = self.device_service.get_device_by_id(device_id)
            
            if not device:
                raise NotFoundError("Device not found")
            
            # Check authorization (non-admin users can only see their org's devices)
            if user_role != 'admin' and str(device['organization_id']) != organization_id:
                raise AuthorizationError("Access denied to this device")
            
            return {'device': device}, 200
            
        except (NotFoundError, AuthorizationError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Error fetching device {device_id}: {str(e)}")
            raise ValidationError("Failed to fetch device")
    
    @jwt_required()
    @require_permission('device.update')
    def put(self, device_id):
        """
        Update device information
        ---
        tags:
          - Devices
        security:
          - Bearer: []
        parameters:
          - in: path
            name: device_id
            type: string
            required: true
            description: Device ID
          - in: body
            name: device_update
            schema:
              type: object
              properties:
                device_model:
                  type: string
                firmware_version:
                  type: string
                status:
                  type: string
                  enum: [active, inactive, maintenance]
                location:
                  type: object
                description:
                  type: string
        responses:
          200:
            description: Device updated successfully
          400:
            description: Invalid request data
          401:
            description: Unauthorized
          403:
            description: Insufficient permissions
          404:
            description: Device not found
        """
        try:
            # Validate request data
            schema = DeviceUpdateSchema()
            try:
                data = schema.load(request.get_json() or {})
            except MarshmallowValidationError as e:
                raise ValidationError("Invalid device update data", details=e.messages)
            
            # Get current user info for authorization
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Check if device exists and user has access
            device = self.device_service.get_device_by_id(device_id)
            if not device:
                raise NotFoundError("Device not found")
            
            if user_role != 'admin' and str(device['organization_id']) != organization_id:
                raise AuthorizationError("Access denied to this device")
            
            # Update device
            updated_device = self.device_service.update_device(device_id, data)
            
            self.logger.info(f"Device updated: {device_id}")
            
            return {
                'device': updated_device,
                'message': 'Device updated successfully'
            }, 200
            
        except (ValidationError, NotFoundError, AuthorizationError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Error updating device {device_id}: {str(e)}")
            raise ValidationError("Failed to update device")
    
    @jwt_required()
    @require_permission('device.delete')
    def delete(self, device_id):
        """
        Delete device (soft delete - mark as inactive)
        ---
        tags:
          - Devices
        security:
          - Bearer: []
        parameters:
          - in: path
            name: device_id
            type: string
            required: true
            description: Device ID
        responses:
          200:
            description: Device deleted successfully
          401:
            description: Unauthorized
          403:
            description: Insufficient permissions
          404:
            description: Device not found
        """
        try:
            # Get current user info for authorization
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Check if device exists and user has access
            device = self.device_service.get_device_by_id(device_id)
            if not device:
                raise NotFoundError("Device not found")
            
            if user_role != 'admin' and str(device['organization_id']) != organization_id:
                raise AuthorizationError("Access denied to this device")
            
            # Soft delete device (mark as inactive)
            self.device_service.delete_device(device_id)
            
            self.logger.info(f"Device deleted: {device_id}")
            
            return {'message': 'Device deleted successfully'}, 200
            
        except (NotFoundError, AuthorizationError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Error deleting device {device_id}: {str(e)}")
            raise ValidationError("Failed to delete device")


class DeviceCalibrationResource(Resource):
    """Handle device calibration operations"""
    
    def __init__(self):
        self.device_service = DeviceService()
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    @require_permission('device.update')
    def post(self, device_id):
        """
        Update device calibration
        ---
        tags:
          - Devices
        security:
          - Bearer: []
        parameters:
          - in: path
            name: device_id
            type: string
            required: true
            description: Device ID
          - in: body
            name: calibration
            schema:
              type: object
              required:
                - calibration_coefficients
              properties:
                calibration_coefficients:
                  type: object
                  description: Calibration coefficients
                calibration_notes:
                  type: string
                  description: Calibration notes
                next_calibration_due:
                  type: string
                  format: date-time
                  description: Next calibration due date
        responses:
          200:
            description: Calibration updated successfully
          400:
            description: Invalid request data
          401:
            description: Unauthorized
          403:
            description: Insufficient permissions
          404:
            description: Device not found
        """
        try:
            # Validate request data
            schema = CalibrationSchema()
            try:
                data = schema.load(request.get_json() or {})
            except MarshmallowValidationError as e:
                raise ValidationError("Invalid calibration data", details=e.messages)
            
            # Get current user info for authorization
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            user_id = get_jwt_identity()
            
            # Check if device exists and user has access
            device = self.device_service.get_device_by_id(device_id)
            if not device:
                raise NotFoundError("Device not found")
            
            if user_role != 'admin' and str(device['organization_id']) != organization_id:
                raise AuthorizationError("Access denied to this device")
            
            # Update calibration
            updated_device = self.device_service.update_calibration(
                device_id, data, user_id
            )
            
            self.logger.info(f"Device calibration updated: {device_id}")
            
            return {
                'device': updated_device,
                'message': 'Calibration updated successfully'
            }, 200
            
        except (ValidationError, NotFoundError, AuthorizationError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Error updating calibration for device {device_id}: {str(e)}")
            raise ValidationError("Failed to update calibration")
    
    @jwt_required()
    @require_permission('device.read')
    def get(self, device_id):
        """
        Get device calibration history
        ---
        tags:
          - Devices
        security:
          - Bearer: []
        parameters:
          - in: path
            name: device_id
            type: string
            required: true
            description: Device ID
        responses:
          200:
            description: Calibration history
            schema:
              type: object
              properties:
                calibration_history:
                  type: array
          401:
            description: Unauthorized
          403:
            description: Insufficient permissions
          404:
            description: Device not found
        """
        try:
            # Get current user info for authorization
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Check if device exists and user has access
            device = self.device_service.get_device_by_id(device_id)
            if not device:
                raise NotFoundError("Device not found")
            
            if user_role != 'admin' and str(device['organization_id']) != organization_id:
                raise AuthorizationError("Access denied to this device")
            
            # Get calibration history
            calibration_history = self.device_service.get_calibration_history(device_id)
            
            return {'calibration_history': calibration_history}, 200
            
        except (NotFoundError, AuthorizationError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Error fetching calibration history for device {device_id}: {str(e)}")
            raise ValidationError("Failed to fetch calibration history")