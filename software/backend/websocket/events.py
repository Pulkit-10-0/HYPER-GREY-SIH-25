"""
WebSocket Event Handlers
Real-time communication for device status and analysis updates
"""
import logging
from flask import current_app
from flask_socketio import emit, join_room, leave_room, disconnect
from flask_jwt_extended import decode_token, get_jwt_identity
from bson import ObjectId

from backend.utils.exceptions import AuthenticationError


logger = logging.getLogger(__name__)


def init_websocket_events(socketio):
    """Initialize WebSocket event handlers"""
    
    @socketio.on('connect')
    def handle_connect(auth):
        """Handle client connection"""
        try:
            # Authenticate client using JWT token
            if not auth or 'token' not in auth:
                logger.warning("WebSocket connection rejected: No token provided")
                disconnect()
                return False
            
            token = auth['token']
            
            try:
                # Decode JWT token
                decoded_token = decode_token(token)
                user_id = decoded_token['sub']
                user_role = decoded_token.get('role', 'viewer')
                organization_id = decoded_token.get('organization_id')
                
                # Store user info in session
                from flask import session
                session['user_id'] = user_id
                session['user_role'] = user_role
                session['organization_id'] = organization_id
                
                logger.info(f"WebSocket client connected: user {user_id}")
                
                # Join organization room for targeted broadcasts
                if organization_id:
                    join_room(f"org_{organization_id}")
                
                # Join role-based room
                join_room(f"role_{user_role}")
                
                # Send connection confirmation
                emit('connected', {
                    'status': 'connected',
                    'user_id': user_id,
                    'role': user_role,
                    'timestamp': current_app.utcnow().isoformat()
                })
                
                return True
                
            except Exception as e:
                logger.error(f"WebSocket authentication failed: {str(e)}")
                disconnect()
                return False
                
        except Exception as e:
            logger.error(f"WebSocket connection error: {str(e)}")
            disconnect()
            return False
    
    @socketio.on('disconnect')
    def handle_disconnect():
        """Handle client disconnection"""
        try:
            from flask import session
            user_id = session.get('user_id')
            organization_id = session.get('organization_id')
            user_role = session.get('user_role')
            
            if user_id:
                logger.info(f"WebSocket client disconnected: user {user_id}")
                
                # Leave rooms
                if organization_id:
                    leave_room(f"org_{organization_id}")
                if user_role:
                    leave_room(f"role_{user_role}")
            
        except Exception as e:
            logger.error(f"WebSocket disconnection error: {str(e)}")
    
    @socketio.on('subscribe_device')
    def handle_device_subscription(data):
        """Handle device-specific subscriptions"""
        try:
            from flask import session
            user_id = session.get('user_id')
            user_role = session.get('user_role')
            organization_id = session.get('organization_id')
            
            if not user_id:
                emit('error', {'message': 'Not authenticated'})
                return
            
            device_id = data.get('device_id')
            if not device_id:
                emit('error', {'message': 'Device ID required'})
                return
            
            # Verify user has access to device
            db = current_app.db
            device = db.devices.find_one({'device_id': device_id})
            
            if not device:
                emit('error', {'message': 'Device not found'})
                return
            
            # Check authorization
            if user_role != 'admin' and str(device['organization_id']) != organization_id:
                emit('error', {'message': 'Access denied to device'})
                return
            
            # Join device-specific room
            join_room(f"device_{device_id}")
            
            logger.info(f"User {user_id} subscribed to device {device_id}")
            
            emit('subscribed', {
                'device_id': device_id,
                'status': 'subscribed',
                'timestamp': current_app.utcnow().isoformat()
            })
            
        except Exception as e:
            logger.error(f"Device subscription error: {str(e)}")
            emit('error', {'message': 'Subscription failed'})
    
    @socketio.on('unsubscribe_device')
    def handle_device_unsubscription(data):
        """Handle device unsubscription"""
        try:
            from flask import session
            user_id = session.get('user_id')
            
            if not user_id:
                emit('error', {'message': 'Not authenticated'})
                return
            
            device_id = data.get('device_id')
            if not device_id:
                emit('error', {'message': 'Device ID required'})
                return
            
            # Leave device-specific room
            leave_room(f"device_{device_id}")
            
            logger.info(f"User {user_id} unsubscribed from device {device_id}")
            
            emit('unsubscribed', {
                'device_id': device_id,
                'status': 'unsubscribed',
                'timestamp': current_app.utcnow().isoformat()
            })
            
        except Exception as e:
            logger.error(f"Device unsubscription error: {str(e)}")
            emit('error', {'message': 'Unsubscription failed'})
    
    @socketio.on('get_device_status')
    def handle_device_status_request(data):
        """Handle real-time device status requests"""
        try:
            from flask import session
            user_id = session.get('user_id')
            user_role = session.get('user_role')
            organization_id = session.get('organization_id')
            
            if not user_id:
                emit('error', {'message': 'Not authenticated'})
                return
            
            device_id = data.get('device_id')
            if not device_id:
                emit('error', {'message': 'Device ID required'})
                return
            
            # Get device status
            db = current_app.db
            device = db.devices.find_one({'device_id': device_id})
            
            if not device:
                emit('error', {'message': 'Device not found'})
                return
            
            # Check authorization
            if user_role != 'admin' and str(device['organization_id']) != organization_id:
                emit('error', {'message': 'Access denied to device'})
                return
            
            # Get latest sensor reading
            latest_reading = db.sensor_readings.find_one(
                {'device_id': device_id},
                sort=[('timestamp', -1)]
            )
            
            # Prepare status response
            status_data = {
                'device_id': device_id,
                'status': device.get('status', 'unknown'),
                'last_reading': latest_reading['timestamp'].isoformat() if latest_reading else None,
                'total_readings': device.get('statistics', {}).get('total_readings', 0),
                'firmware_version': device.get('firmware_version'),
                'timestamp': current_app.utcnow().isoformat()
            }
            
            emit('device_status', status_data)
            
        except Exception as e:
            logger.error(f"Device status request error: {str(e)}")
            emit('error', {'message': 'Status request failed'})


def emit_analysis_complete(reading_id, analysis_result):
    """
    Emit analysis completion event to relevant clients
    
    Args:
        reading_id (str): Sensor reading ID
        analysis_result (dict): Analysis result data
    """
    try:
        from flask_socketio import SocketIO
        
        socketio = current_app.extensions.get('socketio')
        if not socketio:
            return
        
        # Get device and organization info
        db = current_app.db
        reading = db.sensor_readings.find_one({'_id': ObjectId(reading_id)})
        
        if not reading:
            return
        
        device_id = reading['device_id']
        organization_id = str(reading['organization_id'])
        
        # Prepare event data
        event_data = {
            'type': 'analysis_complete',
            'reading_id': reading_id,
            'device_id': device_id,
            'measurement_id': reading.get('measurement_id'),
            'herb_name': reading.get('sample_metadata', {}).get('herb_name'),
            'quality_grade': analysis_result.get('quality_metrics', {}).get('grade'),
            'overall_score': analysis_result.get('quality_metrics', {}).get('overall_score'),
            'is_authentic': analysis_result.get('predictions', {}).get('authenticity', {}).get('is_authentic'),
            'contamination_detected': analysis_result.get('predictions', {}).get('contamination', {}).get('contamination_detected'),
            'recommendations': analysis_result.get('recommendations', []),
            'timestamp': analysis_result.get('analysis_timestamp', current_app.utcnow()).isoformat()
        }
        
        # Emit to device subscribers
        socketio.emit('analysis_complete', event_data, room=f"device_{device_id}")
        
        # Emit to organization
        socketio.emit('analysis_complete', event_data, room=f"org_{organization_id}")
        
        logger.info(f"Analysis complete event emitted for reading {reading_id}")
        
    except Exception as e:
        logger.error(f"Error emitting analysis complete event: {str(e)}")


def emit_device_status_update(device_id, status, additional_data=None):
    """
    Emit device status update to relevant clients
    
    Args:
        device_id (str): Device ID
        status (str): New device status
        additional_data (dict): Additional status data
    """
    try:
        from flask_socketio import SocketIO
        
        socketio = current_app.extensions.get('socketio')
        if not socketio:
            return
        
        # Get device info
        db = current_app.db
        device = db.devices.find_one({'device_id': device_id})
        
        if not device:
            return
        
        organization_id = str(device['organization_id'])
        
        # Prepare event data
        event_data = {
            'type': 'device_status_update',
            'device_id': device_id,
            'status': status,
            'timestamp': current_app.utcnow().isoformat()
        }
        
        if additional_data:
            event_data.update(additional_data)
        
        # Emit to device subscribers
        socketio.emit('device_status_update', event_data, room=f"device_{device_id}")
        
        # Emit to organization
        socketio.emit('device_status_update', event_data, room=f"org_{organization_id}")
        
        logger.info(f"Device status update emitted for device {device_id}: {status}")
        
    except Exception as e:
        logger.error(f"Error emitting device status update: {str(e)}")


def emit_new_reading(reading_data):
    """
    Emit new sensor reading event to relevant clients
    
    Args:
        reading_data (dict): Sensor reading data
    """
    try:
        from flask_socketio import SocketIO
        
        socketio = current_app.extensions.get('socketio')
        if not socketio:
            return
        
        device_id = reading_data['device_id']
        organization_id = str(reading_data['organization_id'])
        
        # Prepare event data
        event_data = {
            'type': 'new_reading',
            'reading_id': str(reading_data['_id']),
            'device_id': device_id,
            'measurement_id': reading_data.get('measurement_id'),
            'herb_name': reading_data.get('sample_metadata', {}).get('herb_name'),
            'timestamp': reading_data.get('timestamp', current_app.utcnow()).isoformat()
        }
        
        # Emit to device subscribers
        socketio.emit('new_reading', event_data, room=f"device_{device_id}")
        
        # Emit to organization
        socketio.emit('new_reading', event_data, room=f"org_{organization_id}")
        
        logger.info(f"New reading event emitted for device {device_id}")
        
    except Exception as e:
        logger.error(f"Error emitting new reading event: {str(e)}")


def emit_system_alert(alert_type, message, severity='info', target_roles=None):
    """
    Emit system-wide alerts to relevant users
    
    Args:
        alert_type (str): Type of alert
        message (str): Alert message
        severity (str): Alert severity (info, warning, error)
        target_roles (list): Target user roles (None for all)
    """
    try:
        from flask_socketio import SocketIO
        
        socketio = current_app.extensions.get('socketio')
        if not socketio:
            return
        
        # Prepare alert data
        alert_data = {
            'type': 'system_alert',
            'alert_type': alert_type,
            'message': message,
            'severity': severity,
            'timestamp': current_app.utcnow().isoformat()
        }
        
        # Emit to target roles or all users
        if target_roles:
            for role in target_roles:
                socketio.emit('system_alert', alert_data, room=f"role_{role}")
        else:
            socketio.emit('system_alert', alert_data, broadcast=True)
        
        logger.info(f"System alert emitted: {alert_type} - {message}")
        
    except Exception as e:
        logger.error(f"Error emitting system alert: {str(e)}")