"""
AI Service
Handles AI model integration and analysis operations
"""
import logging
import os
import json
import numpy as np
from datetime import datetime
from bson import ObjectId
from pymongo.errors import PyMongoError
from flask import current_app
import joblib
from sklearn.preprocessing import StandardScaler

from backend.utils.exceptions import DatabaseError, ValidationError


class AIService:
    """Service class for AI model operations"""
    
    def __init__(self):
        self.logger = logging.getLogger(__name__)
        self.models = {}
        self.scalers = {}
        self._load_models()
    
    def _load_models(self):
        """Load AI models from ai-models directory"""
        try:
            # Define model paths (assuming ai-models folder is at project root)
            models_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), 'ai-models')
            
            if not os.path.exists(models_dir):
                self.logger.warning(f"AI models directory not found: {models_dir}")
                return
            
            # Load authenticity model
            authenticity_model_path = os.path.join(models_dir, 'authenticity_model.joblib')
            if os.path.exists(authenticity_model_path):
                self.models['authenticity'] = joblib.load(authenticity_model_path)
                self.logger.info("Loaded authenticity model")
            
            # Load quality model
            quality_model_path = os.path.join(models_dir, 'quality_model.joblib')
            if os.path.exists(quality_model_path):
                self.models['quality'] = joblib.load(quality_model_path)
                self.logger.info("Loaded quality model")
            
            # Load contamination model
            contamination_model_path = os.path.join(models_dir, 'contamination_model.joblib')
            if os.path.exists(contamination_model_path):
                self.models['contamination'] = joblib.load(contamination_model_path)
                self.logger.info("Loaded contamination model")
            
            # Load scalers
            scaler_path = os.path.join(models_dir, 'feature_scaler.joblib')
            if os.path.exists(scaler_path):
                self.scalers['features'] = joblib.load(scaler_path)
                self.logger.info("Loaded feature scaler")
            
            if not self.models:
                self.logger.warning("No AI models loaded")
            
        except Exception as e:
            self.logger.error(f"Error loading AI models: {str(e)}")
    
    def analyze_sensor_data(self, reading_id):
        """
        Perform AI analysis on sensor reading
        
        Args:
            reading_id (str): Sensor reading ID
            
        Returns:
            dict: Analysis results
        """
        try:
            db = current_app.db
            
            # Get sensor reading
            reading = db.sensor_readings.find_one({'_id': ObjectId(reading_id)})
            if not reading:
                raise ValidationError("Sensor reading not found")
            
            # Extract features from raw readings
            features = self._extract_features(reading['raw_readings'])
            
            # Perform predictions
            predictions = {}
            
            # Authenticity analysis
            if 'authenticity' in self.models:
                auth_result = self._predict_authenticity(features)
                predictions['authenticity'] = auth_result
            
            # Quality analysis
            if 'quality' in self.models:
                quality_result = self._predict_quality(features)
                predictions['quality'] = quality_result
            
            # Contamination analysis
            if 'contamination' in self.models:
                contamination_result = self._predict_contamination(features)
                predictions['contamination'] = contamination_result
            
            # Calculate overall quality metrics
            quality_metrics = self._calculate_quality_metrics(predictions)
            
            # Generate recommendations
            recommendations = self._generate_recommendations(predictions, reading['sample_metadata'])
            
            # Store analysis results
            analysis_result = {
                'reading_id': ObjectId(reading_id),
                'device_id': reading['device_id'],
                'herb_name': reading['sample_metadata']['herb_name'],
                'predictions': predictions,
                'quality_metrics': quality_metrics,
                'recommendations': recommendations,
                'model_versions': self._get_model_versions(),
                'analysis_timestamp': datetime.utcnow(),
                'processing_time_ms': 0,  # Will be calculated by caller
                'organization_id': reading['organization_id']
            }
            
            # Insert analysis result
            result = db.analysis_results.insert_one(analysis_result)
            analysis_result['_id'] = result.inserted_id
            
            self.logger.info(f"AI analysis completed for reading {reading_id}")
            
            return analysis_result
            
        except PyMongoError as e:
            self.logger.error(f"Database error during AI analysis: {str(e)}")
            raise DatabaseError("Failed to perform AI analysis")
        except Exception as e:
            self.logger.error(f"Error during AI analysis: {str(e)}")
            raise ValidationError("AI analysis failed")
    
    def _extract_features(self, raw_readings):
        """
        Extract features from raw sensor readings
        
        Args:
            raw_readings (dict): Raw sensor data
            
        Returns:
            np.array: Feature vector
        """
        features = []
        
        # Extract electrode readings
        electrodes = raw_readings.get('electrodes', {})
        for i in range(1, 9):  # Assuming 8 electrodes
            electrode_key = f'electrode_{i}'
            features.append(electrodes.get(electrode_key, 0.0))
        
        # Extract color sensor readings
        color = raw_readings.get('color', {})
        features.extend([
            color.get('red', 0),
            color.get('green', 0),
            color.get('blue', 0),
            color.get('clear', 0)
        ])
        
        # Extract environmental readings
        env = raw_readings.get('environmental', {})
        features.extend([
            env.get('temperature', 25.0),
            env.get('humidity', 50.0),
            env.get('pressure', 1013.25)
        ])
        
        # Calculate derived features
        electrode_values = [electrodes.get(f'electrode_{i}', 0.0) for i in range(1, 9)]
        features.extend([
            np.mean(electrode_values),  # Mean electrode voltage
            np.std(electrode_values),   # Electrode voltage std
            np.max(electrode_values),   # Max electrode voltage
            np.min(electrode_values),   # Min electrode voltage
            color.get('red', 0) + color.get('green', 0) + color.get('blue', 0)  # Total color intensity
        ])
        
        feature_array = np.array(features).reshape(1, -1)
        
        # Apply scaling if available
        if 'features' in self.scalers:
            feature_array = self.scalers['features'].transform(feature_array)
        
        return feature_array
    
    def _predict_authenticity(self, features):
        """Predict herb authenticity"""
        try:
            model = self.models['authenticity']
            
            # Get prediction and probability
            prediction = model.predict(features)[0]
            probabilities = model.predict_proba(features)[0]
            
            return {
                'is_authentic': bool(prediction),
                'confidence': float(max(probabilities)),
                'authenticity_score': float(probabilities[1] if len(probabilities) > 1 else probabilities[0])
            }
        except Exception as e:
            self.logger.error(f"Authenticity prediction error: {str(e)}")
            return {
                'is_authentic': None,
                'confidence': 0.0,
                'authenticity_score': 0.0,
                'error': str(e)
            }
    
    def _predict_quality(self, features):
        """Predict herb quality"""
        try:
            model = self.models['quality']
            
            # Get quality score (assuming regression model)
            quality_score = model.predict(features)[0]
            
            # Convert to grade
            if quality_score >= 0.9:
                grade = 'A'
            elif quality_score >= 0.8:
                grade = 'B'
            elif quality_score >= 0.7:
                grade = 'C'
            elif quality_score >= 0.6:
                grade = 'D'
            else:
                grade = 'F'
            
            return {
                'quality_score': float(quality_score),
                'grade': grade,
                'quality_level': self._get_quality_level(quality_score)
            }
        except Exception as e:
            self.logger.error(f"Quality prediction error: {str(e)}")
            return {
                'quality_score': 0.0,
                'grade': 'Unknown',
                'quality_level': 'Unknown',
                'error': str(e)
            }
    
    def _predict_contamination(self, features):
        """Predict contamination levels"""
        try:
            model = self.models['contamination']
            
            # Get contamination prediction
            contamination_level = model.predict(features)[0]
            
            return {
                'contamination_detected': contamination_level > 0.5,
                'contamination_level': float(contamination_level),
                'risk_level': self._get_risk_level(contamination_level)
            }
        except Exception as e:
            self.logger.error(f"Contamination prediction error: {str(e)}")
            return {
                'contamination_detected': None,
                'contamination_level': 0.0,
                'risk_level': 'Unknown',
                'error': str(e)
            }
    
    def _calculate_quality_metrics(self, predictions):
        """Calculate overall quality metrics"""
        metrics = {
            'overall_score': 0.0,
            'grade': 'Unknown',
            'confidence': 0.0,
            'risk_factors': []
        }
        
        try:
            scores = []
            confidences = []
            
            # Authenticity contribution
            if 'authenticity' in predictions and predictions['authenticity'].get('is_authentic'):
                auth_score = predictions['authenticity'].get('authenticity_score', 0.0)
                scores.append(auth_score * 0.4)  # 40% weight
                confidences.append(predictions['authenticity'].get('confidence', 0.0))
            else:
                metrics['risk_factors'].append('Authenticity concerns')
            
            # Quality contribution
            if 'quality' in predictions:
                quality_score = predictions['quality'].get('quality_score', 0.0)
                scores.append(quality_score * 0.4)  # 40% weight
                confidences.append(0.8)  # Assume reasonable confidence for quality
            
            # Contamination contribution (inverse)
            if 'contamination' in predictions:
                contamination_level = predictions['contamination'].get('contamination_level', 0.0)
                contamination_score = max(0, 1.0 - contamination_level)
                scores.append(contamination_score * 0.2)  # 20% weight
                confidences.append(0.8)
                
                if predictions['contamination'].get('contamination_detected'):
                    metrics['risk_factors'].append('Contamination detected')
            
            # Calculate overall metrics
            if scores:
                metrics['overall_score'] = sum(scores)
                metrics['confidence'] = np.mean(confidences)
                
                # Determine grade
                if metrics['overall_score'] >= 0.9:
                    metrics['grade'] = 'A'
                elif metrics['overall_score'] >= 0.8:
                    metrics['grade'] = 'B'
                elif metrics['overall_score'] >= 0.7:
                    metrics['grade'] = 'C'
                elif metrics['overall_score'] >= 0.6:
                    metrics['grade'] = 'D'
                else:
                    metrics['grade'] = 'F'
            
        except Exception as e:
            self.logger.error(f"Error calculating quality metrics: {str(e)}")
        
        return metrics
    
    def _generate_recommendations(self, predictions, sample_metadata):
        """Generate recommendations based on analysis results"""
        recommendations = []
        
        try:
            herb_name = sample_metadata.get('herb_name', 'Unknown')
            
            # Authenticity recommendations
            if 'authenticity' in predictions:
                auth = predictions['authenticity']
                if not auth.get('is_authentic'):
                    recommendations.append({
                        'type': 'warning',
                        'category': 'authenticity',
                        'message': f'Authenticity concerns detected for {herb_name}. Consider verifying source and supplier.',
                        'priority': 'high'
                    })
                elif auth.get('confidence', 0) < 0.7:
                    recommendations.append({
                        'type': 'caution',
                        'category': 'authenticity',
                        'message': f'Low confidence in authenticity assessment. Additional testing recommended.',
                        'priority': 'medium'
                    })
            
            # Quality recommendations
            if 'quality' in predictions:
                quality = predictions['quality']
                grade = quality.get('grade', 'Unknown')
                
                if grade in ['D', 'F']:
                    recommendations.append({
                        'type': 'warning',
                        'category': 'quality',
                        'message': f'Low quality grade ({grade}) detected. Review processing and storage conditions.',
                        'priority': 'high'
                    })
                elif grade == 'C':
                    recommendations.append({
                        'type': 'info',
                        'category': 'quality',
                        'message': f'Moderate quality grade ({grade}). Consider quality improvement measures.',
                        'priority': 'medium'
                    })
                else:
                    recommendations.append({
                        'type': 'success',
                        'category': 'quality',
                        'message': f'Good quality grade ({grade}) achieved.',
                        'priority': 'low'
                    })
            
            # Contamination recommendations
            if 'contamination' in predictions:
                contamination = predictions['contamination']
                if contamination.get('contamination_detected'):
                    risk_level = contamination.get('risk_level', 'Unknown')
                    recommendations.append({
                        'type': 'warning',
                        'category': 'contamination',
                        'message': f'Contamination detected with {risk_level} risk level. Immediate review required.',
                        'priority': 'high'
                    })
            
            # General recommendations
            if not recommendations or all(r['type'] == 'success' for r in recommendations):
                recommendations.append({
                    'type': 'info',
                    'category': 'general',
                    'message': 'Sample analysis completed successfully. Continue monitoring quality parameters.',
                    'priority': 'low'
                })
            
        except Exception as e:
            self.logger.error(f"Error generating recommendations: {str(e)}")
            recommendations.append({
                'type': 'error',
                'category': 'system',
                'message': 'Error generating recommendations. Manual review recommended.',
                'priority': 'medium'
            })
        
        return recommendations
    
    def _get_quality_level(self, score):
        """Convert quality score to level"""
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
    
    def _get_risk_level(self, contamination_level):
        """Convert contamination level to risk"""
        if contamination_level >= 0.8:
            return 'High'
        elif contamination_level >= 0.6:
            return 'Medium'
        elif contamination_level >= 0.3:
            return 'Low'
        else:
            return 'Minimal'
    
    def _get_model_versions(self):
        """Get versions of loaded models"""
        versions = {}
        for model_name in self.models:
            # In a real implementation, models would have version metadata
            versions[model_name] = '1.0.0'
        return versions
    
    def get_analysis_results(self, filters=None, page=1, per_page=20, user_role=None, organization_id=None):
        """
        Get analysis results with pagination and filtering
        
        Args:
            filters (dict): Query filters
            page (int): Page number
            per_page (int): Items per page
            user_role (str): User role
            organization_id (str): User organization ID
            
        Returns:
            dict: Analysis results and pagination info
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
            total_count = db.analysis_results.count_documents(filters)
            
            # Get results with pagination
            cursor = db.analysis_results.find(filters).sort('analysis_timestamp', -1).skip(skip).limit(per_page)
            results = [self._format_analysis_response(result) for result in cursor]
            
            # Calculate pagination info
            total_pages = (total_count + per_page - 1) // per_page
            has_next = page < total_pages
            has_prev = page > 1
            
            return {
                'results': results,
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
            self.logger.error(f"Database error fetching analysis results: {str(e)}")
            raise DatabaseError("Failed to fetch analysis results")
    
    def _format_analysis_response(self, result):
        """Format analysis result for API response"""
        return {
            'id': str(result['_id']),
            'reading_id': str(result['reading_id']),
            'device_id': result['device_id'],
            'herb_name': result['herb_name'],
            'predictions': result['predictions'],
            'quality_metrics': result['quality_metrics'],
            'recommendations': result['recommendations'],
            'model_versions': result.get('model_versions', {}),
            'analysis_timestamp': result['analysis_timestamp'].isoformat(),
            'processing_time_ms': result.get('processing_time_ms', 0)
        }