"""
Device Service
Handles device registration, management, and operations
"""
import logging
from datetime import datetime, timedelta
from bson import ObjectId
from pymongo.errors import PyMongoError, DuplicateKeyError
from flask import current_app

from backend.utils.exceptions import DatabaseError, ConflictError, NotFoundError


class DeviceService:
    """Service class for device management operations"""
    
    def __init__(self):
        self.logger = logging.getLogger(__name__)
    
    def register_device(self, device_data):
        """
        Register a new device
        
        Args:
            device_data (dict): Device registration data
            
        Returns:
            dict: Created device document
        """
        try:
            db = current_app.db
            
            # Check if device already exists
            existing_device = db.devices.find_one({'device_id': device_data['device_id']})
            if existing_device:
                raise ConflictError(f"Device with ID {device_data['device_id']} already exists")
            
            # Prepare device document
            device_doc = {
                'device_id': device_data['device_id'],
                'device_model': device_data['device_model'],
                'firmware_version': device_data['firmware_version'],
                'organization_id': ObjectId(device_data['organization_id']),
                'owner_user_id': ObjectId(device_data['owner_user_id']),
                'status': 'active',
                'location': device_data.get('location', {}),
                'description': device_data.get('description', ''),
                'calibration_data': {
                    'last_calibration': None,
                    'calibration_coefficients': {},
                    'next_calibration_due': None,
                    'calibration_history': []
                },
                'statistics': {
                    'total_readings': 0,
                    'last_reading': None,
                    'uptime_hours': 0
                },
                'created_at': datetime.utcnow(),
                'updated_at': datetime.utcnow()
            }
            
            # Insert device
            result = db.devices.insert_one(device_doc)
            device_doc['_id'] = result.inserted_id
            
            # Convert ObjectIds to strings for JSON serialization
            return self._format_device_response(device_doc)
            
        except ConflictError as e:
            raise e
        except PyMongoError as e:
            self.logger.error(f"Database error registering device: {str(e)}")
            raise DatabaseError("Failed to register device")
        except Exception as e:
            self.logger.error(f"Unexpected error registering device: {str(e)}")
            raise DatabaseError("Failed to register device")
    
    def get_devices(self, filters=None, page=1, per_page=20):
        """
        Get devices with pagination and filtering
        
        Args:
            filters (dict): Query filters
            page (int): Page number
            per_page (int): Items per page
            
        Returns:
            dict: Devices and pagination info
        """
        try:
            db = current_app.db
            
            if filters is None:
                filters = {}
            
            # Convert organization_id string to ObjectId if present
            if 'organization_id' in filters and isinstance(filters['organization_id'], str):
                filters['organization_id'] = ObjectId(filters['organization_id'])
            
            # Calculate skip value
            skip = (page - 1) * per_page
            
            # Get total count
            total_count = db.devices.count_documents(filters)
            
            # Get devices with pagination
            cursor = db.devices.find(filters).sort('created_at', -1).skip(skip).limit(per_page)
            devices = [self._format_device_response(device) for device in cursor]
            
            # Calculate pagination info
            total_pages = (total_count + per_page - 1) // per_page
            has_next = page < total_pages
            has_prev = page > 1
            
            return {
                'devices': devices,
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
            self.logger.error(f"Database error fetching devices: {str(e)}")
            raise DatabaseError("Failed to fetch devices")
    
    def get_device_by_id(self, device_id):
        """
        Get device by device ID
        
        Args:
            device_id (str): Device ID
            
        Returns:
            dict: Device document or None if not found
        """
        try:
            db = current_app.db
            device = db.devices.find_one({'device_id': device_id})
            
            if device:
                return self._format_device_response(device)
            return None
            
        except PyMongoError as e:
            self.logger.error(f"Database error fetching device: {str(e)}")
            raise DatabaseError("Failed to fetch device")
    
    def update_device(self, device_id, update_data):
        """
        Update device information
        
        Args:
            device_id (str): Device ID
            update_data (dict): Update data
            
        Returns:
            dict: Updated device document
        """
        try:
            db = current_app.db
            
            # Prepare update document
            update_doc = {
                '$set': {
                    'updated_at': datetime.utcnow()
                }
            }
            
            # Add fields to update
            for field in ['device_model', 'firmware_version', 'status', 'location', 'description']:
                if field in update_data:
                    update_doc['$set'][field] = update_data[field]
            
            # Update device
            result = db.devices.find_one_and_update(
                {'device_id': device_id},
                update_doc,
                return_document=True
            )
            
            if not result:
                raise NotFoundError("Device not found")
            
            return self._format_device_response(result)
            
        except NotFoundError as e:
            raise e
        except PyMongoError as e:
            self.logger.error(f"Database error updating device: {str(e)}")
            raise DatabaseError("Failed to update device")
    
    def delete_device(self, device_id):
        """
        Soft delete device (mark as inactive)
        
        Args:
            device_id (str): Device ID
        """
        try:
            db = current_app.db
            
            result = db.devices.update_one(
                {'device_id': device_id},
                {
                    '$set': {
                        'status': 'inactive',
                        'updated_at': datetime.utcnow(),
                        'deleted_at': datetime.utcnow()
                    }
                }
            )
            
            if result.matched_count == 0:
                raise NotFoundError("Device not found")
            
        except NotFoundError as e:
            raise e
        except PyMongoError as e:
            self.logger.error(f"Database error deleting device: {str(e)}")
            raise DatabaseError("Failed to delete device")
    
    def update_calibration(self, device_id, calibration_data, user_id):
        """
        Update device calibration
        
        Args:
            device_id (str): Device ID
            calibration_data (dict): Calibration data
            user_id (str): User performing calibration
            
        Returns:
            dict: Updated device document
        """
        try:
            db = current_app.db
            
            # Prepare calibration record
            calibration_record = {
                'calibration_date': datetime.utcnow(),
                'calibrated_by': ObjectId(user_id),
                'calibration_coefficients': calibration_data['calibration_coefficients'],
                'calibration_notes': calibration_data.get('calibration_notes', ''),
                'next_calibration_due': calibration_data.get('next_calibration_due')
            }
            
            # Update device calibration
            update_doc = {
                '$set': {
                    'calibration_data.last_calibration': calibration_record['calibration_date'],
                    'calibration_data.calibration_coefficients': calibration_record['calibration_coefficients'],
                    'calibration_data.next_calibration_due': calibration_record.get('next_calibration_due'),
                    'updated_at': datetime.utcnow()
                },
                '$push': {
                    'calibration_data.calibration_history': calibration_record
                }
            }
            
            result = db.devices.find_one_and_update(
                {'device_id': device_id},
                update_doc,
                return_document=True
            )
            
            if not result:
                raise NotFoundError("Device not found")
            
            return self._format_device_response(result)
            
        except NotFoundError as e:
            raise e
        except PyMongoError as e:
            self.logger.error(f"Database error updating calibration: {str(e)}")
            raise DatabaseError("Failed to update calibration")
    
    def get_calibration_history(self, device_id):
        """
        Get device calibration history
        
        Args:
            device_id (str): Device ID
            
        Returns:
            list: Calibration history records
        """
        try:
            db = current_app.db
            
            device = db.devices.find_one(
                {'device_id': device_id},
                {'calibration_data.calibration_history': 1}
            )
            
            if not device:
                raise NotFoundError("Device not found")
            
            calibration_history = device.get('calibration_data', {}).get('calibration_history', [])
            
            # Format calibration history for response
            formatted_history = []
            for record in calibration_history:
                formatted_record = {
                    'calibration_date': record['calibration_date'].isoformat() if record.get('calibration_date') else None,
                    'calibrated_by': str(record['calibrated_by']) if record.get('calibrated_by') else None,
                    'calibration_coefficients': record.get('calibration_coefficients', {}),
                    'calibration_notes': record.get('calibration_notes', ''),
                    'next_calibration_due': record['next_calibration_due'].isoformat() if record.get('next_calibration_due') else None
                }
                formatted_history.append(formatted_record)
            
            return formatted_history
            
        except NotFoundError as e:
            raise e
        except PyMongoError as e:
            self.logger.error(f"Database error fetching calibration history: {str(e)}")
            raise DatabaseError("Failed to fetch calibration history")
    
    def update_device_status(self, device_id, status, additional_data=None):
        """
        Update device status and emit real-time notifications
        
        Args:
            device_id (str): Device ID
            status (str): New status
            additional_data (dict): Additional status data
        """
        try:
            db = current_app.db
            
            update_doc = {
                '$set': {
                    'status': status,
                    'updated_at': datetime.utcnow()
                }
            }
            
            if additional_data:
                for key, value in additional_data.items():
                    update_doc['$set'][key] = value
            
            result = db.devices.update_one(
                {'device_id': device_id},
                update_doc
            )
            
            if result.matched_count == 0:
                raise NotFoundError("Device not found")
            
            # TODO: Emit real-time notification (will be implemented in WebSocket task)
            # self._emit_device_status_update(device_id, status, additional_data)
            
        except NotFoundError as e:
            raise e
        except PyMongoError as e:
            self.logger.error(f"Database error updating device status: {str(e)}")
            raise DatabaseError("Failed to update device status")
    
    def update_device_statistics(self, device_id, stats_update):
        """
        Update device statistics
        
        Args:
            device_id (str): Device ID
            stats_update (dict): Statistics update data
        """
        try:
            db = current_app.db
            
            update_doc = {
                '$set': {
                    'updated_at': datetime.utcnow()
                }
            }
            
            # Update statistics fields
            for field in ['total_readings', 'last_reading', 'uptime_hours']:
                if field in stats_update:
                    update_doc['$set'][f'statistics.{field}'] = stats_update[field]
            
            db.devices.update_one(
                {'device_id': device_id},
                update_doc
            )
            
        except PyMongoError as e:
            self.logger.error(f"Database error updating device statistics: {str(e)}")
            # Don't raise exception as this is not critical
    
    def _format_device_response(self, device):
        """
        Format device document for API response
        
        Args:
            device (dict): Device document
            
        Returns:
            dict: Formatted device data
        """
        formatted_device = {
            'id': str(device['_id']),
            'device_id': device['device_id'],
            'device_model': device['device_model'],
            'firmware_version': device['firmware_version'],
            'organization_id': str(device['organization_id']),
            'owner_user_id': str(device['owner_user_id']),
            'status': device['status'],
            'location': device.get('location', {}),
            'description': device.get('description', ''),
            'calibration_data': {
                'last_calibration': device['calibration_data']['last_calibration'].isoformat() if device['calibration_data'].get('last_calibration') else None,
                'calibration_coefficients': device['calibration_data'].get('calibration_coefficients', {}),
                'next_calibration_due': device['calibration_data']['next_calibration_due'].isoformat() if device['calibration_data'].get('next_calibration_due') else None
            },
            'statistics': device.get('statistics', {}),
            'created_at': device['created_at'].isoformat() if device.get('created_at') else None,
            'updated_at': device['updated_at'].isoformat() if device.get('updated_at') else None
        }
        
        return formatted_device