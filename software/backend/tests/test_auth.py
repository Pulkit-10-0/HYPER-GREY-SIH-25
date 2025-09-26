"""
Tests for Authentication API endpoints
"""
import pytest
import json
from datetime import datetime
from flask_jwt_extended import create_access_token

from backend.app import create_app
from backend.utils.sample_data import SampleDataGenerator


class TestAuthAPI:
    """Test authentication API endpoints"""
    
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
    
    def test_login_success(self, client, sample_data):
        """Test successful login"""
        response = client.post('/api/auth/login', 
            json={
                'username': 'admin',
                'password': 'Admin123!'
            },
            content_type='application/json'
        )
        
        assert response.status_code == 200
        data = json.loads(response.data)
        
        assert 'access_token' in data
        assert 'refresh_token' in data
        assert 'user' in data
        assert data['user']['username'] == 'admin'
        assert data['user']['role'] == 'admin'
    
    def test_login_invalid_credentials(self, client, sample_data):
        """Test login with invalid credentials"""
        response = client.post('/api/auth/login',
            json={
                'username': 'admin',
                'password': 'wrongpassword'
            },
            content_type='application/json'
        )
        
        assert response.status_code == 401
        data = json.loads(response.data)
        assert 'error' in data
    
    def test_login_missing_fields(self, client):
        """Test login with missing fields"""
        response = client.post('/api/auth/login',
            json={
                'username': 'admin'
                # Missing password
            },
            content_type='application/json'
        )
        
        assert response.status_code == 400
        data = json.loads(response.data)
        assert 'error' in data
    
    def test_refresh_token(self, client, sample_data, app):
        """Test token refresh"""
        # First login to get tokens
        login_response = client.post('/api/auth/login',
            json={
                'username': 'admin',
                'password': 'Admin123!'
            },
            content_type='application/json'
        )
        
        login_data = json.loads(login_response.data)
        refresh_token = login_data['refresh_token']
        
        # Use refresh token to get new access token
        response = client.post('/api/auth/refresh',
            headers={'Authorization': f'Bearer {refresh_token}'},
            content_type='application/json'
        )
        
        assert response.status_code == 200
        data = json.loads(response.data)
        assert 'access_token' in data
    
    def test_logout(self, client, sample_data, app):
        """Test user logout"""
        # First login to get token
        login_response = client.post('/api/auth/login',
            json={
                'username': 'admin',
                'password': 'Admin123!'
            },
            content_type='application/json'
        )
        
        login_data = json.loads(login_response.data)
        access_token = login_data['access_token']
        
        # Logout
        response = client.post('/api/auth/logout',
            headers={'Authorization': f'Bearer {access_token}'},
            content_type='application/json'
        )
        
        assert response.status_code == 200
        data = json.loads(response.data)
        assert 'message' in data
    
    def test_get_profile(self, client, sample_data, app):
        """Test get user profile"""
        # First login to get token
        login_response = client.post('/api/auth/login',
            json={
                'username': 'admin',
                'password': 'Admin123!'
            },
            content_type='application/json'
        )
        
        login_data = json.loads(login_response.data)
        access_token = login_data['access_token']
        
        # Get profile
        response = client.get('/api/auth/profile',
            headers={'Authorization': f'Bearer {access_token}'}
        )
        
        assert response.status_code == 200
        data = json.loads(response.data)
        assert 'user' in data
        assert data['user']['username'] == 'admin'
    
    def test_unauthorized_access(self, client):
        """Test accessing protected endpoint without token"""
        response = client.get('/api/auth/profile')
        
        assert response.status_code == 401
        data = json.loads(response.data)
        assert 'error' in data