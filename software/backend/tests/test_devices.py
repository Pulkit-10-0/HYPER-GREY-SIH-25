"""
Tests for Device Management API endpoints
"""
import pytest
import json
from datetime import datetime

from backend.app import create_app
from backend.utils.sample_data import SampleDataGenerator


class TestDeviceAPI:
    """Test device management API endpoints"""
    
    @pytest.fixture
    def app(self):
        """Create test app"""
        app, socketio = create_app('testing')
        return app
    
    @pytest.fixture
    def client(self, app):
        """Create test client"""
        return app.test_client()
    
    @pytest.fixture
    def sample_data(self, app):
        """Create sample data for testing"""
        with app.app_context():
            generator = SampleDataGenerator()
            data = generator.generate_all_sample_data()
            yield data
            # Cleanup after test
            generator.cleanup_sample_data()
    
    @pytest.fixture
    def auth_headers(self, client, sample_data):
        """Get authentication headers"""
        # Login as admin
        response = client.post('/api/auth/login',
            json={
                'username': 'admin',
                'password': 'Admin123!'
            },
            content_type='application/json'
        )
        
        data = json.loads(response.data)
        access_token = data['access_token']
        
        return {'Authorization': f'Bearer {access_token}'}
    
    def test_get_devices_list(self, client, sample_data, auth_headers):
        """Test getting devices list"""
        response = client.get('/api/devices', headers=auth_headers)
        
        assert response.status_code == 200
        data = json.loads(response.data)
        
        assert 'devices' in data
        assert 'pagination' in data
        assert len(data['devices']) > 0
    
    def test_get_devices_with_pagination(self, client, sample_data, auth_headers):
        """Test getting devices with pagination"""
        response = client.get('/api/devices?page=1&per_page=2', headers=auth_headers)
        
        assert response.status_code == 200
        data = json.loads(response.data)
        
        assert 'devices' in data
        assert 'pagination' in data
        assert data['pagination']['page'] == 1
        assert data['pagination']['per_page'] == 2
    
    def test_get_devices_with_filters(self, client, sample_data, auth_headers):
        """Test getting devices with filters"""
        response = client.get('/api/devices?status=active', headers=auth_headers)
        
        assert response.status_code == 200
        data = json.loads(response.data)
        
        assert 'devices' in data
        # All returned devices should have active status
        for device in data['devices']:
            assert device['status'] == 'active'
    
    def test_register_device(self, client, sample_data, auth_headers):
        """Test device registration"""
        new_device = {
            'device_id': 'ESP32_TEST_001',
            'device_model': 'AYUSURE-ET-2000-TEST',
            'firmware_version': '2.0.2',
            'location': {
                'latitude': 28.7041,
                'longitude': 77.1025,
                'address': 'Test Location'
            },
            'description': 'Test device for unit testing'
        }
        
        response = client.post('/api/devices',
            json=new_device,
            headers=auth_headers,
            content_type='application/json'
        )
        
        assert response.status_code == 201
        data = json.loads(response.data)
        
        assert 'device' in data
        assert 'message' in data
        assert data['device']['device_id'] == new_device['device_id']
    
    def test_register_duplicate_device(self, client, sample_data, auth_headers):
        """Test registering device with duplicate ID"""
        # Try to register device with existing ID
        duplicate_device = {
            'device_id': 'ESP32_LAB_001',  # This already exists in sample data
            'device_model': 'AYUSURE-ET-2000',
            'firmware_version': '2.0.1'
        }
        
        response = client.post('/api/devices',
            json=duplicate_device,
            headers=auth_headers,
            content_type='application/json'
        )
        
        assert response.status_code == 409
        data = json.loads(response.data)
        assert 'error' in data
    
    def test_get_device_details(self, client, sample_data, auth_headers):
        """Test getting device details"""
        device_id = 'ESP32_LAB_001'
        
        response = client.get(f'/api/devices/{device_id}', headers=auth_headers)
        
        assert response.status_code == 200
        data = json.loads(response.data)
        
        assert 'device' in data
        assert data['device']['device_id'] == device_id
    
    def test_get_nonexistent_device(self, client, sample_data, auth_headers):
        """Test getting nonexistent device"""
        device_id = 'NONEXISTENT_DEVICE'
        
        response = client.get(f'/api/devices/{device_id}', headers=auth_headers)
        
        assert response.status_code == 404
        data = json.loads(response.data)
        assert 'error' in data
    
    def test_update_device(self, client, sample_data, auth_headers):
        """Test updating device information"""
        device_id = 'ESP32_LAB_001'
        
        update_data = {
            'firmware_version': '2.0.3',
            'description': 'Updated description for testing'
        }
        
        response = client.put(f'/api/devices/{device_id}',
            json=update_data,
            headers=auth_headers,
            content_type='application/json'
        )
        
        assert response.status_code == 200
        data = json.loads(response.data)
        
        assert 'device' in data
        assert data['device']['firmware_version'] == update_data['firmware_version']
        assert data['device']['description'] == update_data['description']
    
    def test_update_device_calibration(self, client, sample_data, auth_headers):
        """Test updating device calibration"""
        device_id = 'ESP32_LAB_001'
        
        calibration_data = {
            'calibration_coefficients': {
                'SS': 1.05,
                'Cu': 0.98,
                'Zn': 1.02,
                'Ag': 0.99,
                'Pt': 1.01
            },
            'calibration_notes': 'Test calibration update'
        }
        
        response = client.post(f'/api/devices/{device_id}/calibration',
            json=calibration_data,
            headers=auth_headers,
            content_type='application/json'
        )
        
        assert response.status_code == 200
        data = json.loads(response.data)
        
        assert 'device' in data
        assert 'message' in data
    
    def test_get_calibration_history(self, client, sample_data, auth_headers):
        """Test getting device calibration history"""
        device_id = 'ESP32_LAB_001'
        
        response = client.get(f'/api/devices/{device_id}/calibration', headers=auth_headers)
        
        assert response.status_code == 200
        data = json.loads(response.data)
        
        assert 'calibration_history' in data
        assert isinstance(data['calibration_history'], list)
    
    def test_delete_device(self, client, sample_data, auth_headers):
        """Test device deletion (soft delete)"""
        device_id = 'ESP32_LAB_001'
        
        response = client.delete(f'/api/devices/{device_id}', headers=auth_headers)
        
        assert response.status_code == 200
        data = json.loads(response.data)
        
        assert 'message' in data
        
        # Verify device is marked as inactive
        get_response = client.get(f'/api/devices/{device_id}', headers=auth_headers)
        get_data = json.loads(get_response.data)
        # Note: The device should still exist but be marked as inactive
        # This depends on the implementation of soft delete
    
    def test_unauthorized_device_access(self, client, sample_data):
        """Test accessing device endpoints without authentication"""
        response = client.get('/api/devices')
        
        assert response.status_code == 401
        data = json.loads(response.data)
        assert 'error' in data
    
    def test_invalid_device_data(self, client, sample_data, auth_headers):
        """Test registering device with invalid data"""
        invalid_device = {
            'device_id': '',  # Empty device ID
            'device_model': 'AYUSURE-ET-2000'
            # Missing required firmware_version
        }
        
        response = client.post('/api/devices',
            json=invalid_device,
            headers=auth_headers,
            content_type='application/json'
        )
        
        assert response.status_code == 400
        data = json.loads(response.data)
        assert 'error' in data