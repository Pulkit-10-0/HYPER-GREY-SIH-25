"""
Sample Data Generator
Creates realistic sample data for testing and demonstration
"""
import logging
import random
from datetime import datetime, timedelta
from bson import ObjectId
from flask import current_app
from werkzeug.security import generate_password_hash

from backend.services.device_service import DeviceService
from backend.services.sensor_service import SensorService
from backend.services.ai_service import AIService


class SampleDataGenerator:
    """Generate sample data for testing and demonstration"""
    
    def __init__(self):
        self.logger = logging.getLogger(__name__)
        self.device_service = DeviceService()
        self.sensor_service = SensorService()
        self.ai_service = AIService()
    
    def generate_all_sample_data(self, num_devices=5, num_readings_per_device=50):
        """
        Generate complete sample dataset
        
        Args:
            num_devices (int): Number of sample devices to create
            num_readings_per_device (int): Number of readings per device
        """
        try:
            self.logger.info("Starting sample data generation...")
            
            # Create sample organizations
            organizations = self.create_sample_organizations()
            
            # Create sample users
            users = self.create_sample_users(organizations)
            
            # Create sample devices
            devices = self.create_sample_devices(organizations, users, num_devices)
            
            # Create sample sensor readings
            readings = self.create_sample_sensor_readings(devices, num_readings_per_device)
            
            # Generate sample analysis results
            self.create_sample_analysis_results(readings)
            
            # Create sample reports
            self.create_sample_reports(organizations)
            
            self.logger.info("Sample data generation completed successfully")
            
            return {
                'organizations': len(organizations),
                'users': len(users),
                'devices': len(devices),
                'readings': len(readings),
                'status': 'completed'
            }
            
        except Exception as e:
            self.logger.error(f"Error generating sample data: {str(e)}")
            raise
    
    def create_sample_organizations(self):
        """Create sample organizations"""
        db = current_app.db
        
        organizations = [
            {
                'name': 'Ayurvedic Research Institute',
                'description': 'Leading research institute for Ayurvedic medicine quality control',
                'settings': {
                    'timezone': 'Asia/Kolkata',
                    'data_retention_days': 730,
                    'auto_analysis': True,
                    'notification_preferences': {
                        'email_alerts': True,
                        'quality_threshold': 0.7,
                        'contamination_alerts': True
                    }
                },
                'created_at': datetime.utcnow() - timedelta(days=365),
                'updated_at': datetime.utcnow()
            },
            {
                'name': 'Herbal Manufacturing Co.',
                'description': 'Large-scale herbal medicine manufacturer',
                'settings': {
                    'timezone': 'Asia/Kolkata',
                    'data_retention_days': 365,
                    'auto_analysis': True,
                    'notification_preferences': {
                        'email_alerts': True,
                        'quality_threshold': 0.8,
                        'contamination_alerts': True
                    }
                },
                'created_at': datetime.utcnow() - timedelta(days=200),
                'updated_at': datetime.utcnow()
            },
            {
                'name': 'Quality Control Labs',
                'description': 'Independent quality testing laboratory',
                'settings': {
                    'timezone': 'UTC',
                    'data_retention_days': 1095,
                    'auto_analysis': True,
                    'notification_preferences': {
                        'email_alerts': True,
                        'quality_threshold': 0.6,
                        'contamination_alerts': True
                    }
                },
                'created_at': datetime.utcnow() - timedelta(days=150),
                'updated_at': datetime.utcnow()
            }
        ]
        
        created_orgs = []
        for org_data in organizations:
            # Check if organization already exists
            existing = db.organizations.find_one({'name': org_data['name']})
            if not existing:
                result = db.organizations.insert_one(org_data)
                org_data['_id'] = result.inserted_id
                created_orgs.append(org_data)
                self.logger.info(f"Created organization: {org_data['name']}")
            else:
                created_orgs.append(existing)
        
        return created_orgs
    
    def create_sample_users(self, organizations):
        """Create sample users for each organization"""
        db = current_app.db
        
        user_templates = [
            {
                'username': 'dr_sharma',
                'email': 'dr.sharma@ayurveda-research.org',
                'full_name': 'Dr. Rajesh Sharma',
                'role': 'manager'
            },
            {
                'username': 'lab_tech1',
                'email': 'tech1@ayurveda-research.org',
                'full_name': 'Priya Patel',
                'role': 'operator'
            },
            {
                'username': 'quality_manager',
                'email': 'qm@herbal-mfg.com',
                'full_name': 'Amit Kumar',
                'role': 'manager'
            },
            {
                'username': 'analyst1',
                'email': 'analyst1@herbal-mfg.com',
                'full_name': 'Sneha Gupta',
                'role': 'operator'
            },
            {
                'username': 'lab_supervisor',
                'email': 'supervisor@qc-labs.com',
                'full_name': 'Dr. Meera Singh',
                'role': 'manager'
            },
            {
                'username': 'technician',
                'email': 'tech@qc-labs.com',
                'full_name': 'Rahul Verma',
                'role': 'operator'
            }
        ]
        
        created_users = []
        for i, user_template in enumerate(user_templates):
            org = organizations[i // 2]  # 2 users per organization
            
            user_data = {
                **user_template,
                'password_hash': generate_password_hash('password123'),
                'organization_id': org['_id'],
                'is_active': True,
                'created_at': datetime.utcnow() - timedelta(days=random.randint(30, 300)),
                'updated_at': datetime.utcnow(),
                'login_count': random.randint(10, 100),
                'last_login': datetime.utcnow() - timedelta(days=random.randint(1, 30))
            }
            
            # Check if user already exists
            existing = db.users.find_one({'username': user_data['username']})
            if not existing:
                result = db.users.insert_one(user_data)
                user_data['_id'] = result.inserted_id
                created_users.append(user_data)
                self.logger.info(f"Created user: {user_data['username']}")
            else:
                created_users.append(existing)
        
        return created_users
    
    def create_sample_devices(self, organizations, users, num_devices):
        """Create sample ESP32 devices"""
        device_models = [
            'AyuSure ESP32 v1.0',
            'AyuSure ESP32 v1.1',
            'AyuSure ESP32 Pro v2.0'
        ]
        
        firmware_versions = ['1.0.0', '1.0.1', '1.1.0', '2.0.0']
        
        locations = [
            {'latitude': 28.6139, 'longitude': 77.2090, 'address': 'New Delhi, India'},
            {'latitude': 19.0760, 'longitude': 72.8777, 'address': 'Mumbai, India'},
            {'latitude': 13.0827, 'longitude': 80.2707, 'address': 'Chennai, India'},
            {'latitude': 22.5726, 'longitude': 88.3639, 'address': 'Kolkata, India'},
            {'latitude': 12.9716, 'longitude': 77.5946, 'address': 'Bangalore, India'}
        ]
        
        created_devices = []
        for i in range(num_devices):
            org = organizations[i % len(organizations)]
            user = random.choice([u for u in users if u['organization_id'] == org['_id']])
            
            device_data = {
                'device_id': f'ESP32_DEMO_{str(i+1).zfill(3)}',
                'device_model': random.choice(device_models),
                'firmware_version': random.choice(firmware_versions),
                'organization_id': str(org['_id']),
                'owner_user_id': str(user['_id']),
                'location': random.choice(locations),
                'description': f'Sample device {i+1} for testing and demonstration'
            }
            
            try:
                device = self.device_service.register_device(device_data)
                created_devices.append(device)
                self.logger.info(f"Created device: {device_data['device_id']}")
            except Exception as e:
                if "already exists" in str(e):
                    # Device already exists, get it
                    db = current_app.db
                    existing_device = db.devices.find_one({'device_id': device_data['device_id']})
                    if existing_device:
                        created_devices.append(self.device_service._format_device_response(existing_device))
                else:
                    self.logger.error(f"Error creating device {device_data['device_id']}: {str(e)}")
        
        return created_devices
    
    def create_sample_sensor_readings(self, devices, num_readings_per_device):
        """Create sample sensor readings"""
        herb_names = [
            'Turmeric', 'Ashwagandha', 'Brahmi', 'Neem', 'Tulsi',
            'Ginger', 'Amla', 'Triphala', 'Guduchi', 'Shatavari'
        ]
        
        preparation_methods = [
            'Dried and powdered', 'Fresh extract', 'Standardized extract',
            'Raw material', 'Processed powder'
        ]
        
        created_readings = []
        for device in devices:
            for i in range(num_readings_per_device):
                # Generate realistic sensor readings
                reading_data = {
                    'device_id': device['device_id'],
                    'measurement_id': f"MEAS_{device['device_id']}_{str(i+1).zfill(4)}",
                    'raw_readings': self._generate_realistic_sensor_data(),
                    'sample_metadata': {
                        'herb_name': random.choice(herb_names),
                        'batch_id': f"BATCH_{random.randint(1000, 9999)}",
                        'sample_weight': round(random.uniform(0.5, 10.0), 2),
                        'preparation_method': random.choice(preparation_methods),
                        'collection_notes': f'Sample {i+1} collected for quality analysis'
                    },
                    'environmental_conditions': {
                        'temperature': round(random.uniform(20, 35), 1),
                        'humidity': round(random.uniform(40, 80), 1),
                        'pressure': round(random.uniform(980, 1020), 2)
                    },
                    'timestamp': datetime.utcnow() - timedelta(
                        days=random.randint(1, 90),
                        hours=random.randint(0, 23),
                        minutes=random.randint(0, 59)
                    )
                }
                
                try:
                    # Find device document for sensor service
                    db = current_app.db
                    device_doc = db.devices.find_one({'device_id': device['device_id']})
                    
                    if device_doc:
                        reading = self.sensor_service.store_sensor_reading(reading_data, device_doc)
                        created_readings.append(reading)
                        
                        if i % 10 == 0:  # Log progress every 10 readings
                            self.logger.info(f"Created {i+1} readings for device {device['device_id']}")
                    
                except Exception as e:
                    self.logger.error(f"Error creating reading for device {device['device_id']}: {str(e)}")
        
        self.logger.info(f"Created {len(created_readings)} sensor readings")
        return created_readings
    
    def _generate_realistic_sensor_data(self):
        """Generate realistic sensor readings"""
        # Simulate electrode readings (8 electrodes, 0-5V)
        electrodes = {}
        for i in range(1, 9):
            # Add some correlation between electrodes for realism
            base_value = random.uniform(0.5, 4.5)
            noise = random.uniform(-0.2, 0.2)
            electrodes[f'electrode_{i}'] = round(max(0, min(5, base_value + noise)), 3)
        
        # Color sensor readings (0-255)
        color = {
            'red': random.randint(50, 255),
            'green': random.randint(50, 255),
            'blue': random.randint(50, 255),
            'clear': random.randint(100, 1000)
        }
        
        # Environmental readings
        environmental = {
            'temperature': round(random.uniform(22, 32), 1),
            'humidity': round(random.uniform(45, 75), 1),
            'pressure': round(random.uniform(990, 1015), 2)
        }
        
        return {
            'electrodes': electrodes,
            'color': color,
            'environmental': environmental
        }
    
    def create_sample_analysis_results(self, readings):
        """Create sample analysis results for readings"""
        created_results = 0
        
        for reading in readings[:100]:  # Limit to first 100 readings for demo
            try:
                # Simulate AI analysis
                analysis_result = self._generate_mock_analysis_result(reading)
                
                # Store in database
                db = current_app.db
                result = db.analysis_results.insert_one(analysis_result)
                
                # Update reading status
                db.sensor_readings.update_one(
                    {'_id': reading['_id']},
                    {
                        '$set': {
                            'processing_status': 'completed',
                            'analysis_result_id': result.inserted_id
                        }
                    }
                )
                
                created_results += 1
                
            except Exception as e:
                self.logger.error(f"Error creating analysis result: {str(e)}")
        
        self.logger.info(f"Created {created_results} analysis results")
        return created_results
    
    def _generate_mock_analysis_result(self, reading):
        """Generate mock analysis result"""
        herb_name = reading['sample_metadata']['herb_name']
        
        # Generate realistic predictions based on herb type
        authenticity_score = random.uniform(0.6, 0.95)
        quality_score = random.uniform(0.5, 0.9)
        contamination_level = random.uniform(0.0, 0.3)
        
        # Adjust scores based on herb (some herbs are typically higher quality)
        if herb_name in ['Turmeric', 'Ashwagandha', 'Brahmi']:
            quality_score = random.uniform(0.7, 0.95)
            authenticity_score = random.uniform(0.8, 0.98)
        
        predictions = {
            'authenticity': {
                'is_authentic': authenticity_score > 0.7,
                'confidence': round(authenticity_score, 3),
                'authenticity_score': round(authenticity_score, 3)
            },
            'quality': {
                'quality_score': round(quality_score, 3),
                'grade': self._score_to_grade(quality_score),
                'quality_level': self._score_to_level(quality_score)
            },
            'contamination': {
                'contamination_detected': contamination_level > 0.2,
                'contamination_level': round(contamination_level, 3),
                'risk_level': self._contamination_to_risk(contamination_level)
            }
        }
        
        # Calculate overall quality metrics
        overall_score = (authenticity_score * 0.4 + quality_score * 0.4 + (1 - contamination_level) * 0.2)
        quality_metrics = {
            'overall_score': round(overall_score, 3),
            'grade': self._score_to_grade(overall_score),
            'confidence': round((authenticity_score + quality_score) / 2, 3),
            'risk_factors': []
        }
        
        if not predictions['authenticity']['is_authentic']:
            quality_metrics['risk_factors'].append('Authenticity concerns')
        if predictions['contamination']['contamination_detected']:
            quality_metrics['risk_factors'].append('Contamination detected')
        
        # Generate recommendations
        recommendations = self._generate_mock_recommendations(predictions, herb_name)
        
        return {
            'reading_id': reading['_id'],
            'device_id': reading['device_id'],
            'herb_name': herb_name,
            'predictions': predictions,
            'quality_metrics': quality_metrics,
            'recommendations': recommendations,
            'model_versions': {
                'authenticity': '1.0.0',
                'quality': '1.0.0',
                'contamination': '1.0.0'
            },
            'analysis_timestamp': reading['timestamp'] + timedelta(minutes=random.randint(1, 30)),
            'processing_time_ms': random.randint(500, 3000),
            'organization_id': reading['organization_id']
        }
    
    def _score_to_grade(self, score):
        """Convert score to letter grade"""
        if score >= 0.9:
            return 'A'
        elif score >= 0.8:
            return 'B'
        elif score >= 0.7:
            return 'C'
        elif score >= 0.6:
            return 'D'
        else:
            return 'F'
    
    def _score_to_level(self, score):
        """Convert score to quality level"""
        if score >= 0.9:
            return 'Excellent'
        elif score >= 0.8:
            return 'Good'
        elif score >= 0.7:
            return 'Fair'
        elif score >= 0.6:
            return 'Poor'
        else:
            return 'Very Poor'
    
    def _contamination_to_risk(self, level):
        """Convert contamination level to risk"""
        if level >= 0.6:
            return 'High'
        elif level >= 0.3:
            return 'Medium'
        elif level >= 0.1:
            return 'Low'
        else:
            return 'Minimal'
    
    def _generate_mock_recommendations(self, predictions, herb_name):
        """Generate mock recommendations"""
        recommendations = []
        
        if not predictions['authenticity']['is_authentic']:
            recommendations.append({
                'type': 'warning',
                'category': 'authenticity',
                'message': f'Authenticity concerns detected for {herb_name}. Verify supplier and source.',
                'priority': 'high'
            })
        
        grade = predictions['quality']['grade']
        if grade in ['D', 'F']:
            recommendations.append({
                'type': 'warning',
                'category': 'quality',
                'message': f'Low quality grade ({grade}). Review processing conditions.',
                'priority': 'high'
            })
        elif grade == 'C':
            recommendations.append({
                'type': 'info',
                'category': 'quality',
                'message': f'Moderate quality ({grade}). Consider quality improvements.',
                'priority': 'medium'
            })
        
        if predictions['contamination']['contamination_detected']:
            recommendations.append({
                'type': 'warning',
                'category': 'contamination',
                'message': 'Contamination detected. Immediate review required.',
                'priority': 'high'
            })
        
        if not recommendations:
            recommendations.append({
                'type': 'success',
                'category': 'general',
                'message': 'Sample meets quality standards. Continue monitoring.',
                'priority': 'low'
            })
        
        return recommendations
    
    def create_sample_reports(self, organizations):
        """Create sample report metadata"""
        db = current_app.db
        
        report_types = ['quality_summary', 'device_status', 'analysis_results', 'calibration_report']
        formats = ['pdf', 'csv', 'json']
        
        created_reports = 0
        for org in organizations:
            for _ in range(3):  # 3 reports per organization
                report_data = {
                    'report_type': random.choice(report_types),
                    'organization_id': org['_id'],
                    'date_range': {
                        'start_date': datetime.utcnow() - timedelta(days=30),
                        'end_date': datetime.utcnow()
                    },
                    'format': random.choice(formats),
                    'filters': {},
                    'file_path': f'/app/instance/reports/sample_report_{created_reports+1}.pdf',
                    'file_size': random.randint(50000, 500000),
                    'status': 'completed',
                    'created_at': datetime.utcnow() - timedelta(days=random.randint(1, 30)),
                    'expires_at': datetime.utcnow() + timedelta(days=30)
                }
                
                result = db.reports.insert_one(report_data)
                created_reports += 1
        
        self.logger.info(f"Created {created_reports} sample reports")
        return created_reports