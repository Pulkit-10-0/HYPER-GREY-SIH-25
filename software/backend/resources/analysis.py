"""
Analysis Results API Resources
Handles AI analysis results retrieval and management
"""
import logging
from datetime import datetime
from flask import request, current_app
from flask_restful import Resource
from flask_jwt_extended import jwt_required, get_jwt_identity, get_jwt
from bson import ObjectId
from marshmallow import Schema, fields, ValidationError as MarshmallowValidationError, validate

from backend.utils.exceptions import ValidationError, AuthorizationError, NotFoundError
from backend.services.ai_service import AIService
from backend.middleware.auth_middleware import require_permission


class AnalysisResultsResource(Resource):
    """Handle analysis results listing and filtering"""
    
    def __init__(self):
        self.ai_service = AIService()
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    @require_permission('data.read')
    def get(self):
        """
        Get analysis results with pagination and filtering
        ---
        tags:
          - Analysis
        security:
          - Bearer: []
        parameters:
          - in: query
            name: page
            type: integer
            default: 1
          - in: query
            name: per_page
            type: integer
            default: 20
          - in: query
            name: device_id
            type: string
          - in: query
            name: herb_name
            type: string
          - in: query
            name: grade
            type: string
            enum: [A, B, C, D, F]
          - in: query
            name: start_date
            type: string
            format: date-time
          - in: query
            name: end_date
            type: string
            format: date-time
          - in: query
            name: authenticity
            type: boolean
        responses:
          200:
            description: List of analysis results
            schema:
              type: object
              properties:
                results:
                  type: array
                pagination:
                  type: object
          401:
            description: Unauthorized
          403:
            description: Insufficient permissions
        """
        try:
            # Get query parameters
            page = int(request.args.get('page', 1))
            per_page = min(int(request.args.get('per_page', 20)), 100)
            device_id = request.args.get('device_id')
            herb_name = request.args.get('herb_name')
            grade = request.args.get('grade')
            start_date = request.args.get('start_date')
            end_date = request.args.get('end_date')
            authenticity = request.args.get('authenticity')
            
            # Get current user info
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Build filters
            filters = {}
            
            if device_id:
                filters['device_id'] = device_id
            
            if herb_name:
                filters['herb_name'] = {'$regex': herb_name, '$options': 'i'}
            
            if grade:
                filters['quality_metrics.grade'] = grade
            
            if authenticity is not None:
                auth_bool = authenticity.lower() == 'true'
                filters['predictions.authenticity.is_authentic'] = auth_bool
            
            if start_date or end_date:
                date_filter = {}
                if start_date:
                    date_filter['$gte'] = datetime.fromisoformat(start_date.replace('Z', '+00:00'))
                if end_date:
                    date_filter['$lte'] = datetime.fromisoformat(end_date.replace('Z', '+00:00'))
                filters['analysis_timestamp'] = date_filter
            
            # Get analysis results
            result = self.ai_service.get_analysis_results(
                filters=filters,
                page=page,
                per_page=per_page,
                user_role=user_role,
                organization_id=organization_id
            )
            
            return {
                'results': result['results'],
                'pagination': result['pagination']
            }, 200
            
        except Exception as e:
            self.logger.error(f"Error fetching analysis results: {str(e)}")
            raise ValidationError("Failed to fetch analysis results")


class AnalysisResultDetailResource(Resource):
    """Handle individual analysis result operations"""
    
    def __init__(self):
        self.ai_service = AIService()
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    @require_permission('data.read')
    def get(self, result_id):
        """
        Get analysis result details
        ---
        tags:
          - Analysis
        security:
          - Bearer: []
        parameters:
          - in: path
            name: result_id
            type: string
            required: true
            description: Analysis result ID
        responses:
          200:
            description: Analysis result details
            schema:
              type: object
              properties:
                result:
                  type: object
          401:
            description: Unauthorized
          403:
            description: Insufficient permissions
          404:
            description: Result not found
        """
        try:
            # Get current user info
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Get analysis result
            db = current_app.db
            
            # Build query
            query = {'_id': ObjectId(result_id)}
            
            # Add organization filter for non-admin users
            if user_role != 'admin' and organization_id:
                query['organization_id'] = ObjectId(organization_id)
            
            result = db.analysis_results.find_one(query)
            
            if not result:
                raise NotFoundError("Analysis result not found")
            
            # Format response
            formatted_result = self.ai_service._format_analysis_response(result)
            
            return {'result': formatted_result}, 200
            
        except (NotFoundError, AuthorizationError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Error fetching analysis result {result_id}: {str(e)}")
            raise ValidationError("Failed to fetch analysis result")


class AnalysisStatsResource(Resource):
    """Handle analysis statistics and summaries"""
    
    def __init__(self):
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    @require_permission('data.read')
    def get(self):
        """
        Get analysis statistics
        ---
        tags:
          - Analysis
        security:
          - Bearer: []
        parameters:
          - in: query
            name: period
            type: string
            enum: [day, week, month, year]
            default: month
          - in: query
            name: device_id
            type: string
        responses:
          200:
            description: Analysis statistics
            schema:
              type: object
              properties:
                stats:
                  type: object
        """
        try:
            # Get query parameters
            period = request.args.get('period', 'month')
            device_id = request.args.get('device_id')
            
            # Get current user info
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Calculate date range based on period
            now = datetime.utcnow()
            if period == 'day':
                start_date = now.replace(hour=0, minute=0, second=0, microsecond=0)
            elif period == 'week':
                start_date = now - timedelta(days=7)
            elif period == 'month':
                start_date = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
            else:  # year
                start_date = now.replace(month=1, day=1, hour=0, minute=0, second=0, microsecond=0)
            
            # Build aggregation pipeline
            db = current_app.db
            
            match_stage = {
                'analysis_timestamp': {'$gte': start_date}
            }
            
            # Add organization filter for non-admin users
            if user_role != 'admin' and organization_id:
                match_stage['organization_id'] = ObjectId(organization_id)
            
            if device_id:
                match_stage['device_id'] = device_id
            
            pipeline = [
                {'$match': match_stage},
                {'$group': {
                    '_id': None,
                    'total_analyses': {'$sum': 1},
                    'authentic_count': {
                        '$sum': {
                            '$cond': [
                                {'$eq': ['$predictions.authenticity.is_authentic', True]},
                                1, 0
                            ]
                        }
                    },
                    'grade_distribution': {
                        '$push': '$quality_metrics.grade'
                    },
                    'avg_quality_score': {
                        '$avg': '$quality_metrics.overall_score'
                    },
                    'contamination_detected': {
                        '$sum': {
                            '$cond': [
                                {'$eq': ['$predictions.contamination.contamination_detected', True]},
                                1, 0
                            ]
                        }
                    }
                }}
            ]
            
            result = list(db.analysis_results.aggregate(pipeline))
            
            if result:
                stats = result[0]
                
                # Calculate grade distribution
                grades = stats.get('grade_distribution', [])
                grade_counts = {}
                for grade in ['A', 'B', 'C', 'D', 'F']:
                    grade_counts[grade] = grades.count(grade)
                
                # Calculate percentages
                total = stats.get('total_analyses', 0)
                authenticity_rate = (stats.get('authentic_count', 0) / total * 100) if total > 0 else 0
                contamination_rate = (stats.get('contamination_detected', 0) / total * 100) if total > 0 else 0
                
                formatted_stats = {
                    'period': period,
                    'total_analyses': total,
                    'authenticity_rate': round(authenticity_rate, 2),
                    'contamination_rate': round(contamination_rate, 2),
                    'average_quality_score': round(stats.get('avg_quality_score', 0), 3),
                    'grade_distribution': grade_counts,
                    'date_range': {
                        'start': start_date.isoformat(),
                        'end': now.isoformat()
                    }
                }
            else:
                formatted_stats = {
                    'period': period,
                    'total_analyses': 0,
                    'authenticity_rate': 0,
                    'contamination_rate': 0,
                    'average_quality_score': 0,
                    'grade_distribution': {grade: 0 for grade in ['A', 'B', 'C', 'D', 'F']},
                    'date_range': {
                        'start': start_date.isoformat(),
                        'end': now.isoformat()
                    }
                }
            
            return {'stats': formatted_stats}, 200
            
        except Exception as e:
            self.logger.error(f"Error fetching analysis statistics: {str(e)}")
            raise ValidationError("Failed to fetch analysis statistics")


class ReanalysisResource(Resource):
    """Handle reanalysis requests"""
    
    def __init__(self):
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    @require_permission('data.analyze')
    def post(self, reading_id):
        """
        Request reanalysis of sensor reading
        ---
        tags:
          - Analysis
        security:
          - Bearer: []
        parameters:
          - in: path
            name: reading_id
            type: string
            required: true
            description: Sensor reading ID
        responses:
          202:
            description: Reanalysis queued
          401:
            description: Unauthorized
          403:
            description: Insufficient permissions
          404:
            description: Reading not found
        """
        try:
            from backend.tasks.ai_analysis import analyze_sensor_data
            
            # Get current user info
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Verify reading exists and user has access
            db = current_app.db
            
            query = {'_id': ObjectId(reading_id)}
            if user_role != 'admin' and organization_id:
                query['organization_id'] = ObjectId(organization_id)
            
            reading = db.sensor_readings.find_one(query)
            
            if not reading:
                raise NotFoundError("Sensor reading not found")
            
            # Queue reanalysis
            task = analyze_sensor_data.delay(reading_id)
            
            self.logger.info(f"Reanalysis queued for reading {reading_id}")
            
            return {
                'message': 'Reanalysis queued successfully',
                'reading_id': reading_id,
                'task_id': task.id,
                'status': 'queued'
            }, 202
            
        except (NotFoundError, AuthorizationError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Error queuing reanalysis for reading {reading_id}: {str(e)}")
            raise ValidationError("Failed to queue reanalysis")