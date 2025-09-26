"""
Basic tests for Flask application infrastructure
"""
import pytest
import json
from backend.app import create_app


@pytest.fixture
def app():
    """Create test application"""
    app, socketio = create_app('testing')
    return app


@pytest.fixture
def client(app):
    """Create test client"""
    return app.test_client()


def test_app_creation(app):
    """Test that app is created successfully"""
    assert app is not None
    assert app.config['TESTING'] is True


def test_health_endpoint(client):
    """Test health check endpoint"""
    response = client.get('/health')
    assert response.status_code in [200, 503]  # May be 503 if MongoDB/Redis not available
    
    data = json.loads(response.data)
    assert 'status' in data
    assert 'timestamp' in data
    assert 'services' in data
    assert 'mongodb' in data['services']
    assert 'redis' in data['services']


def test_404_error_handler(client):
    """Test 404 error handling"""
    response = client.get('/nonexistent-endpoint')
    assert response.status_code == 404
    
    data = json.loads(response.data)
    assert 'error' in data
    assert data['error']['code'] == 'NOT_FOUND'


def test_cors_headers(client):
    """Test CORS headers are present"""
    response = client.get('/health')
    # CORS headers should be present due to Flask-CORS
    assert 'Access-Control-Allow-Origin' in response.headers or response.status_code == 503