"""
Sensor Data Service
Handles sensor data storage, validation, and retrieval
"""
import logging
from datetime import datetime
from bson import ObjectId
from pymongo.errors import PyMongoError
from flask import current_app

from backend.utils.exceptions import DatabaseError, NotFoundError, AuthorizationError
from backend.utils.security import SecurityValidator


class SensorService:
    """Service class for sensor data operations"""
    
    def __init__(self):
        self.logger = logging.getLogger(__name__)
    
    def verify_device_access(self, device_id, user_role, organization_id):
        """
        Verify device exists and user has access
        
        Args:
            device_id (str): Device ID
            user_role (str): User role
            organization_id (str): User organization ID
            
        Returns:
            dict: Device document
        """
        try:
            db = current_app.db
            
            # Find device
            device = db.devices.find_one({'device_id': device_id})
            
            if not device:
                raise NotFoundError("Device not found")
            
            # Check authorization (non-admin users can only access their org's devices)
            if user_role != 'admin' and str(device['organization_id']) != organization_id:
                raise AuthorizationError("Access denied to this device")
            
            # Check if device is active
            if device.get('status') != 'active':
                raise AuthorizationError("Device is not active")
            
            return device
            
        except (NotFoundError, AuthorizationError) as e:
            raise e
        except PyMongoError as e:
            self.logger.error(f"Database error verifying device access: {str(e)}")
            raise DatabaseError("Failed to verify device access")
    
    def store_sensor_reading(self, reading_data, device):
        """
        Store sensor reading in database
        
        Args:
            reading_data (dict): Sensor reading data
            device (dict): Device document
            
        Returns:
            dict: Stored reading document
        """
        try:
            db = current_app.db
            
            # Validate sensor data ranges
            SecurityValidator.validate_sensor_data_ranges(reading_data['raw_readings'])
            
            # Prepare reading document
            reading_doc = {
                'device_id': reading_data['device_id'],
                'measurement_id': reading_data['measurement_id'],
                'raw_readings': reading_data['raw_readings'],
                'sample_metadata': reading_data['sample_metadata'],
                'environmental_conditions': reading_data.get('environmental_conditions', {}),
                'device_info': {
                    'device_model': device['device_model'],
                    'firmware_version': device['firmware_version'],
                    'calibration_coefficients': device.get('calibration_data', {}).get('calibration_coefficients', {})
                },
                'organization_id': device['organization_id'],
                'processing_status': 'pending',
                'timestamp': reading_data['timestamp'],
                'created_at': datetime.utcnow()
            }
            
            # Insert reading
            result = db.sensor_readings.insert_one(reading_doc)
            reading_doc['_id'] = result.inserted_id
            
            # Update device statistics
            self._update_device_statistics(device['device_id'])
            
            return reading_doc
            
        except PyMongoError as e:
            self.logger.error(f"Database error storing sensor reading: {str(e)}")
            raise DatabaseError("Failed to store sensor reading")
    
    def get_sensor_readings(self, filters=None, page=1, per_page=20, user_role=None, organization_id=None):
        """
        Get sensor readings with pagination and filtering
        
        Args:
            filters (dict): Query filters
            page (int): Page number
            per_page (int): Items per page
            user_role (str): User role
            organization_id (str): User organization ID
            
        Returns:
            dict: Readings and pagination info
        """
        try:
            db = current_app.db
            
            if filters is None:
                filters = {}
            
            # Add organization filter for non-admin users
            if user_role != 'admin' and organization_id:
                filters['organization_id'] = ObjectId(organization_id)
            
            # Calculate skip value
            skip = (page - 1) * per_page
            
            # Get total count
            total_count = db.sensor_readings.count_documents(filters)
            
            # Get readings with pagination
            cursor = db.sensor_readings.find(filters).sort('timestamp', -1).skip(skip).limit(per_page)
            readings = [self._format_reading_response(reading) for reading in cursor]
            
            # Calculate pagination info
            total_pages = (total_count + per_page - 1) // per_page
            has_next = page < total_pages
            has_prev = page > 1
            
            return {
                'readings': readings,
                'pagination': {
                    'page': page,
                    'per_page': per_page,
                    'total_count': total_count,
                    'total_pages': total_pages,
                    'has_next': has_next,
                    'has_prev': has_prev
                }
            }
            
        except PyMongoError as e:
            self.logger.error(f"Database error fetching sensor readings: {str(e)}")
            raise DatabaseError("Failed to fetch sensor readings")
    
    def get_sensor_reading_by_id(self, reading_id, user_role, organization_id):
        """
        Get sensor reading by ID
        
        Args:
            reading_id (str): Reading ID
            user_role (str): User role
            organization_id (str): User organization ID
            
        Returns:
            dict: Reading document or None if not found
        """
        try:
            db = current_app.db
            
            # Build query
            query = {'_id': ObjectId(reading_id)}
            
            # Add organization filter for non-admin users
            if user_role != 'admin' and organization_id:
                query['organization_id'] = ObjectId(organization_id)
            
            reading = db.sensor_readings.find_one(query)
            
            if reading:
                return self._format_reading_response(reading)
            return None
            
        except PyMongoError as e:
            self.logger.error(f"Database error fetching sensor reading: {str(e)}")
            raise DatabaseError("Failed to fetch sensor reading")
    
    def update_processing_status(self, reading_id, status, analysis_result=None):
        """
        Update sensor reading processing status
        
        Args:
            reading_id (str): Reading ID
            status (str): Processing status
            analysis_result (dict): Analysis result data
        """
        try:
            db = current_app.db
            
            update_doc = {
                '$set': {
                    'processing_status': status,
                    'updated_at': datetime.utcnow()
                }
            }
            
            if analysis_result:
                update_doc['$set']['analysis_result_id'] = analysis_result['_id']
            
            db.sensor_readings.update_one(
                {'_id': ObjectId(reading_id)},
                update_doc
            )
            
        except PyMongoError as e:
            self.logger.error(f"Database error updating processing status: {str(e)}")
            # Don't raise exception as this is not critical
    
    def _update_device_statistics(self, device_id):
        """
        Update device statistics after new reading
        
        Args:
            device_id (str): Device ID
        """
        try:
            db = current_app.db
            
            # Get reading count for device
            reading_count = db.sensor_readings.count_documents({'device_id': device_id})
            
            # Get latest reading timestamp
            latest_reading = db.sensor_readings.find_one(
                {'device_id': device_id},
                sort=[('timestamp', -1)]
            )
            
            # Update device statistics
            update_doc = {
                '$set': {
                    'statistics.total_readings': reading_count,
                    'statistics.last_reading': latest_reading['timestamp'] if latest_reading else None,
                    'updated_at': datetime.utcnow()
                }
            }
            
            db.devices.update_one(
                {'device_id': device_id},
                update_doc
            )
            
        except PyMongoError as e:
            self.logger.error(f"Database error updating device statistics: {str(e)}")
            # Don't raise exception as this is not critical
    
    def _format_reading_response(self, reading):
        """
        Format reading document for API response
        
        Args:
            reading (dict): Reading document
            
        Returns:
            dict: Formatted reading data
        """
        formatted_reading = {
            'id': str(reading['_id']),
            'device_id': reading['device_id'],
            'measurement_id': reading['measurement_id'],
            'raw_readings': reading['raw_readings'],
            'sample_metadata': reading['sample_metadata'],
            'environmental_conditions': reading.get('environmental_conditions', {}),
            'device_info': reading.get('device_info', {}),
            'processing_status': reading.get('processing_status', 'pending'),
            'analysis_result_id': str(reading['analysis_result_id']) if reading.get('analysis_result_id') else None,
            'timestamp': reading['timestamp'].isoformat() if reading.get('timestamp') else None,
            'created_at': reading['created_at'].isoformat() if reading.get('created_at') else None
        }
        
        return formatted_reading