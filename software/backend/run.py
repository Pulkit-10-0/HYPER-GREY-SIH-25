"""
Development server runner for AyuSure Backend API
"""
import os
from dotenv import load_dotenv
from backend.app import create_app

# Load environment variables
load_dotenv()

# Create Flask app and SocketIO instance
app, socketio = create_app()

if __name__ == '__main__':
    # Development server configuration
    debug = os.environ.get('FLASK_ENV') == 'development'
    port = int(os.environ.get('PORT', 5000))
    host = os.environ.get('HOST', '127.0.0.1')
    
    print(f"Starting AyuSure Backend API on {host}:{port}")
    print(f"Debug mode: {debug}")
    print(f"Environment: {os.environ.get('FLASK_ENV', 'development')}")
    
    # Run with SocketIO support
    socketio.run(
        app,
        host=host,
        port=port,
        debug=debug,
        use_reloader=debug
    )