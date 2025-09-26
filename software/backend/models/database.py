"""
Database Manager for MongoDB operations and indexing
"""
import logging
from pymongo import MongoClient, ASCENDING, DESCENDING
from pymongo.errors import PyMongoError
from datetime import datetime, timedelta


class DatabaseManager:
    """Manages MongoDB database operations and indexing strategy"""
    
    def __init__(self, mongo_client):
        """Initialize database manager with MongoDB client"""
        self.client = mongo_client
        self.db = mongo_client.ayusure
        
        # Collection references
        self.devices = self.db.devices
        self.sensor_readings = self.db.sensor_readings
        self.analysis_results = self.db.analysis_results
        self.users = self.db.users
        self.organizations = self.db.organizations
        self.reports = self.db.reports
        
        self.logger = logging.getLogger(__name__)
    
    def setup_indexes(self):
        """Create database indexes for optimal performance"""
        try:
            # Devices collection indexes
            self.devices.create_index([("device_id", ASCENDING)], unique=True)
            self.devices.create_index([("organization_id", ASCENDING), ("status", ASCENDING)])
            self.devices.create_index([("owner_user_id", ASCENDING)])
            
            # Sensor readings collection indexes (time series optimized)
            self.sensor_readings.create_index([
                ("device_id", ASCENDING), 
                ("timestamp", DESCENDING)
            ])
            self.sensor_readings.create_index([
                ("timestamp", DESCENDING), 
                ("processing_status", ASCENDING)
            ])
            self.sensor_readings.create_index([("measurement_id", ASCENDING)])
            self.sensor_readings.create_index([("sample_metadata.batch_id", ASCENDING)])
            
            # TTL index for automatic cleanup of old sensor readings (1 year retention)
            self.sensor_readings.create_index(
                [("created_at", ASCENDING)], 
                expireAfterSeconds=31536000  # 365 days
            )
            
            # Analysis results collection indexes
            self.analysis_results.create_index([
                ("device_id", ASCENDING), 
                ("analysis_timestamp", DESCENDING)
            ])
            self.analysis_results.create_index([("reading_id", ASCENDING)], unique=True)
            self.analysis_results.create_index([
                ("quality_metrics.grade", ASCENDING),
                ("analysis_timestamp", DESCENDING)
            ])
            self.analysis_results.create_index([("model_version", ASCENDING)])
            
            # Users collection indexes
            self.users.create_index([("username", ASCENDING)], unique=True)
            self.users.create_index([("email", ASCENDING)], unique=True)
            self.users.create_index([("organization_id", ASCENDING), ("role", ASCENDING)])
            
            # Organizations collection indexes
            self.organizations.create_index([("name", ASCENDING)])
            self.organizations.create_index([("created_at", DESCENDING)])
            
            # Reports collection indexes
            self.reports.create_index([
                ("organization_id", ASCENDING),
                ("created_at", DESCENDING)
            ])
            self.reports.create_index([("report_type", ASCENDING), ("status", ASCENDING)])
            
            # Compound indexes for common queries
            self.sensor_readings.create_index([
                ("device_id", ASCENDING),
                ("sample_metadata.herb_name", ASCENDING),
                ("timestamp", DESCENDING)
            ])
            
            self.analysis_results.create_index([
                ("device_id", ASCENDING),
                ("predictions.authenticity.is_authentic", ASCENDING),
                ("analysis_timestamp", DESCENDING)
            ])
            
            self.logger.info("Database indexes created successfully")
            
        except PyMongoError as e:
            self.logger.error(f"Failed to create database indexes: {str(e)}")
            raise
    
    def get_collection_stats(self):
        """Get statistics for all collections"""
        try:
            stats = {}
            collections = [
                'devices', 'sensor_readings', 'analysis_results', 
                'users', 'organizations', 'reports'
            ]
            
            for collection_name in collections:
                collection = getattr(self, collection_name)
                stats[collection_name] = {
                    'count': collection.count_documents({}),
                    'size': self.db.command('collStats', collection_name).get('size', 0)
                }
            
            return stats
            
        except PyMongoError as e:
            self.logger.error(f"Failed to get collection stats: {str(e)}")
            return {}
    
    def cleanup_old_data(self, days_to_keep=365):
        """Clean up old data beyond retention period"""
        try:
            cutoff_date = datetime.utcnow() - timedelta(days=days_to_keep)
            
            # Clean up old sensor readings (if TTL index fails)
            old_readings = self.sensor_readings.delete_many({
                'created_at': {'$lt': cutoff_date}
            })
            
            # Clean up corresponding analysis results
            old_analyses = self.analysis_results.delete_many({
                'analysis_timestamp': {'$lt': cutoff_date}
            })
            
            self.logger.info(
                f"Cleaned up {old_readings.deleted_count} old sensor readings "
                f"and {old_analyses.deleted_count} old analysis results"
            )
            
            return {
                'sensor_readings_deleted': old_readings.deleted_count,
                'analysis_results_deleted': old_analyses.deleted_count
            }
            
        except PyMongoError as e:
            self.logger.error(f"Failed to cleanup old data: {str(e)}")
            return {'error': str(e)}
    
    def health_check(self):
        """Perform database health check"""
        try:
            # Test basic connectivity
            self.client.admin.command('ping')
            
            # Test read/write operations
            test_doc = {
                'test': True,
                'timestamp': datetime.utcnow()
            }
            
            # Insert test document
            result = self.db.health_check.insert_one(test_doc)
            
            # Read test document
            found_doc = self.db.health_check.find_one({'_id': result.inserted_id})
            
            # Delete test document
            self.db.health_check.delete_one({'_id': result.inserted_id})
            
            if found_doc:
                return {'status': 'healthy', 'timestamp': datetime.utcnow().isoformat()}
            else:
                return {'status': 'unhealthy', 'error': 'Read operation failed'}
                
        except PyMongoError as e:
            return {'status': 'unhealthy', 'error': str(e)}