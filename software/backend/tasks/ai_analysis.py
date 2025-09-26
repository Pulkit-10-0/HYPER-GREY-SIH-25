"""
Celery Tasks for AI Analysis
Background processing of sensor data analysis
"""
import logging
import time
from datetime import datetime
from celery import Celery
from flask import current_app

from backend.services.ai_service import AIService
from backend.services.sensor_service import SensorService
from backend.websocket.events import emit_analysis_complete


# Initialize Celery
celery = Celery('ayusure_tasks')


@celery.task(bind=True, max_retries=3, default_retry_delay=60)
def analyze_sensor_data(self, reading_id):
    """
    Analyze sensor data using AI models
    
    Args:
        reading_id (str): Sensor reading ID
        
    Returns:
        dict: Analysis result summary
    """
    logger = logging.getLogger(__name__)
    start_time = time.time()
    
    try:
        logger.info(f"Starting AI analysis for reading {reading_id}")
        
        # Initialize services
        ai_service = AIService()
        sensor_service = SensorService()
        
        # Perform AI analysis
        analysis_result = ai_service.analyze_sensor_data(reading_id)
        
        # Calculate processing time
        processing_time = int((time.time() - start_time) * 1000)
        analysis_result['processing_time_ms'] = processing_time
        
        # Update sensor reading status
        sensor_service.update_processing_status(
            reading_id, 'completed', analysis_result
        )
        
        # Emit real-time notification
        emit_analysis_complete(reading_id, analysis_result)
        
        logger.info(f"AI analysis completed for reading {reading_id} in {processing_time}ms")
        
        return {
            'reading_id': reading_id,
            'analysis_id': str(analysis_result['_id']),
            'status': 'completed',
            'processing_time_ms': processing_time,
            'quality_grade': analysis_result['quality_metrics'].get('grade', 'Unknown')
        }
        
    except Exception as e:
        logger.error(f"AI analysis failed for reading {reading_id}: {str(e)}")
        
        # Update sensor reading status to failed
        try:
            sensor_service = SensorService()
            sensor_service.update_processing_status(reading_id, 'failed')
        except:
            pass
        
        # Retry the task
        if self.request.retries < self.max_retries:
            logger.info(f"Retrying AI analysis for reading {reading_id} (attempt {self.request.retries + 1})")
            raise self.retry(exc=e, countdown=60 * (2 ** self.request.retries))
        
        # Final failure
        logger.error(f"AI analysis permanently failed for reading {reading_id} after {self.max_retries} retries")
        
        return {
            'reading_id': reading_id,
            'status': 'failed',
            'error': str(e),
            'retries': self.request.retries
        }


@celery.task(bind=True, max_retries=2)
def batch_analyze_readings(self, reading_ids):
    """
    Batch analyze multiple sensor readings
    
    Args:
        reading_ids (list): List of reading IDs
        
    Returns:
        dict: Batch analysis summary
    """
    logger = logging.getLogger(__name__)
    
    try:
        logger.info(f"Starting batch analysis for {len(reading_ids)} readings")
        
        results = []
        failed_count = 0
        
        for reading_id in reading_ids:
            try:
                result = analyze_sensor_data.delay(reading_id)
                results.append({
                    'reading_id': reading_id,
                    'task_id': result.id,
                    'status': 'queued'
                })
            except Exception as e:
                logger.error(f"Failed to queue analysis for reading {reading_id}: {str(e)}")
                failed_count += 1
                results.append({
                    'reading_id': reading_id,
                    'status': 'failed',
                    'error': str(e)
                })
        
        logger.info(f"Batch analysis queued: {len(results) - failed_count} successful, {failed_count} failed")
        
        return {
            'total_readings': len(reading_ids),
            'queued_successfully': len(results) - failed_count,
            'failed_to_queue': failed_count,
            'results': results
        }
        
    except Exception as e:
        logger.error(f"Batch analysis failed: {str(e)}")
        raise self.retry(exc=e, countdown=30)


@celery.task
def cleanup_old_analysis_results():
    """
    Clean up old analysis results beyond retention period
    """
    logger = logging.getLogger(__name__)
    
    try:
        from backend.models.database import DatabaseManager
        from flask import Flask
        
        # Create minimal Flask app context for database access
        app = Flask(__name__)
        app.config.from_object('backend.config.DevelopmentConfig')
        
        with app.app_context():
            # Initialize database manager
            from pymongo import MongoClient
            mongo_client = MongoClient(app.config['MONGODB_URI'])
            db_manager = DatabaseManager(mongo_client)
            
            # Clean up old data
            cleanup_result = db_manager.cleanup_old_data()
            
            logger.info(f"Cleanup completed: {cleanup_result}")
            
            return cleanup_result
            
    except Exception as e:
        logger.error(f"Cleanup task failed: {str(e)}")
        raise


@celery.task
def generate_quality_report(organization_id, date_range, report_type='quality_summary'):
    """
    Generate quality analysis report
    
    Args:
        organization_id (str): Organization ID
        date_range (dict): Date range for report
        report_type (str): Type of report to generate
        
    Returns:
        dict: Report generation result
    """
    logger = logging.getLogger(__name__)
    
    try:
        from backend.services.report_service import ReportService
        
        logger.info(f"Generating {report_type} report for organization {organization_id}")
        
        report_service = ReportService()
        report_result = report_service.generate_report(
            organization_id=organization_id,
            report_type=report_type,
            date_range=date_range
        )
        
        logger.info(f"Report generated successfully: {report_result['report_id']}")
        
        return report_result
        
    except Exception as e:
        logger.error(f"Report generation failed: {str(e)}")
        raise


@celery.task
def monitor_device_health():
    """
    Monitor device health and send alerts for inactive devices
    """
    logger = logging.getLogger(__name__)
    
    try:
        from backend.services.device_service import DeviceService
        from datetime import timedelta
        
        logger.info("Starting device health monitoring")
        
        device_service = DeviceService()
        
        # Find devices that haven't sent data in the last 24 hours
        cutoff_time = datetime.utcnow() - timedelta(hours=24)
        
        # This would be implemented with proper database queries
        # For now, just log the monitoring activity
        logger.info("Device health monitoring completed")
        
        return {
            'status': 'completed',
            'timestamp': datetime.utcnow().isoformat(),
            'devices_checked': 0,  # Would be actual count
            'alerts_sent': 0       # Would be actual count
        }
        
    except Exception as e:
        logger.error(f"Device health monitoring failed: {str(e)}")
        raise


# Celery beat schedule for periodic tasks
celery.conf.beat_schedule = {
    'cleanup-old-data': {
        'task': 'backend.tasks.ai_analysis.cleanup_old_analysis_results',
        'schedule': 86400.0,  # Daily
    },
    'monitor-device-health': {
        'task': 'backend.tasks.ai_analysis.monitor_device_health',
        'schedule': 3600.0,   # Hourly
    },
}

celery.conf.timezone = 'UTC'


def init_celery(app):
    """Initialize Celery with Flask app"""
    celery.conf.update(
        broker_url=app.config['CELERY_BROKER_URL'],
        result_backend=app.config['CELERY_RESULT_BACKEND'],
        task_serializer='json',
        accept_content=['json'],
        result_serializer='json',
        timezone='UTC',
        enable_utc=True,
        task_routes={
            'backend.tasks.ai_analysis.analyze_sensor_data': {'queue': 'analysis'},
            'backend.tasks.ai_analysis.batch_analyze_readings': {'queue': 'batch'},
            'backend.tasks.ai_analysis.generate_quality_report': {'queue': 'reports'},
        }
    )
    
    class ContextTask(celery.Task):
        """Make celery tasks work with Flask app context"""
        def __call__(self, *args, **kwargs):
            with app.app_context():
                return self.run(*args, **kwargs)
    
    celery.Task = ContextTask
    return celery