"""
Report Generation API Resources
Handles report generation, listing, and download operations
"""
import logging
import os
from datetime import datetime
from flask import request, current_app, send_file
from flask_restful import Resource
from flask_jwt_extended import jwt_required, get_jwt_identity, get_jwt
from bson import ObjectId
from marshmallow import Schema, fields, ValidationError as MarshmallowValidationError, validate

from backend.utils.exceptions import ValidationError, AuthorizationError, NotFoundError
from backend.services.report_service import ReportService
from backend.middleware.auth_middleware import require_permission
from backend.tasks.ai_analysis import generate_quality_report


class ReportGenerationSchema(Schema):
    """Schema for report generation request validation"""
    report_type = fields.Str(
        required=True,
        validate=validate.OneOf(['quality_summary', 'device_status', 'analysis_results', 'calibration_report'])
    )
    format = fields.Str(
        required=False,
        missing='pdf',
        validate=validate.OneOf(['pdf', 'csv', 'json'])
    )
    date_range = fields.Nested({
        'start_date': fields.DateTime(required=True),
        'end_date': fields.DateTime(required=True)
    }, required=True)
    filters = fields.Dict(required=False, missing={})


class ReportGenerationResource(Resource):
    """Handle report generation requests"""
    
    def __init__(self):
        self.report_service = ReportService()
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    @require_permission('report.generate')
    def post(self):
        """
        Generate a new report
        ---
        tags:
          - Reports
        security:
          - Bearer: []
        parameters:
          - in: body
            name: report_request
            schema:
              type: object
              required:
                - report_type
                - date_range
              properties:
                report_type:
                  type: string
                  enum: [quality_summary, device_status, analysis_results, calibration_report]
                  description: Type of report to generate
                format:
                  type: string
                  enum: [pdf, csv, json]
                  default: pdf
                  description: Output format
                date_range:
                  type: object
                  required:
                    - start_date
                    - end_date
                  properties:
                    start_date:
                      type: string
                      format: date-time
                    end_date:
                      type: string
                      format: date-time
                filters:
                  type: object
                  description: Additional filters for report data
        responses:
          202:
            description: Report generation queued
            schema:
              type: object
              properties:
                task_id:
                  type: string
                message:
                  type: string
                status:
                  type: string
          400:
            description: Invalid request data
          401:
            description: Unauthorized
          403:
            description: Insufficient permissions
        """
        try:
            # Validate request data
            schema = ReportGenerationSchema()
            try:
                data = schema.load(request.get_json() or {})
            except MarshmallowValidationError as e:
                raise ValidationError("Invalid report request", details=e.messages)
            
            # Get current user info
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Validate date range
            start_date = data['date_range']['start_date']
            end_date = data['date_range']['end_date']
            
            if start_date >= end_date:
                raise ValidationError("Start date must be before end date")
            
            # Check if date range is reasonable (not more than 1 year)
            if (end_date - start_date).days > 365:
                raise ValidationError("Date range cannot exceed 365 days")
            
            # For large reports, use background task
            if data['report_type'] in ['analysis_results'] and (end_date - start_date).days > 30:
                # Queue background task
                task = generate_quality_report.delay(
                    organization_id=organization_id,
                    date_range={
                        'start_date': start_date.isoformat(),
                        'end_date': end_date.isoformat()
                    },
                    report_type=data['report_type']
                )
                
                return {
                    'task_id': task.id,
                    'message': 'Report generation queued for background processing',
                    'status': 'queued',
                    'estimated_completion': 'within 5 minutes'
                }, 202
            else:
                # Generate report immediately
                report_result = self.report_service.generate_report(
                    organization_id=organization_id,
                    report_type=data['report_type'],
                    date_range={
                        'start_date': start_date.isoformat(),
                        'end_date': end_date.isoformat()
                    },
                    format=data['format'],
                    filters=data['filters']
                )
                
                self.logger.info(f"Report generated: {data['report_type']} for organization {organization_id}")
                
                return {
                    'report_id': report_result['report_id'],
                    'report_type': report_result['report_type'],
                    'format': report_result['format'],
                    'status': report_result['status'],
                    'created_at': report_result['created_at'],
                    'expires_at': report_result['expires_at'],
                    'message': 'Report generated successfully'
                }, 201
            
        except (ValidationError, AuthorizationError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Error generating report: {str(e)}")
            raise ValidationError("Failed to generate report")


class ReportListResource(Resource):
    """Handle report listing"""
    
    def __init__(self):
        self.report_service = ReportService()
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    @require_permission('report.read')
    def get(self):
        """
        Get list of reports
        ---
        tags:
          - Reports
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
            name: report_type
            type: string
          - in: query
            name: format
            type: string
        responses:
          200:
            description: List of reports
            schema:
              type: object
              properties:
                reports:
                  type: array
                pagination:
                  type: object
        """
        try:
            # Get query parameters
            page = int(request.args.get('page', 1))
            per_page = min(int(request.args.get('per_page', 20)), 100)
            report_type = request.args.get('report_type')
            format_filter = request.args.get('format')
            
            # Get current user info
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Build filters
            filters = {}
            
            # Add organization filter for non-admin users
            if user_role != 'admin' and organization_id:
                filters['organization_id'] = ObjectId(organization_id)
            
            if report_type:
                filters['report_type'] = report_type
            
            if format_filter:
                filters['format'] = format_filter
            
            # Get reports from database
            db = current_app.db
            
            # Calculate skip value
            skip = (page - 1) * per_page
            
            # Get total count
            total_count = db.reports.count_documents(filters)
            
            # Get reports with pagination
            cursor = db.reports.find(filters).sort('created_at', -1).skip(skip).limit(per_page)
            
            reports = []
            for report in cursor:
                # Check if file exists
                file_exists = os.path.exists(report['file_path']) if report.get('file_path') else False
                
                reports.append({
                    'id': str(report['_id']),
                    'report_type': report['report_type'],
                    'format': report['format'],
                    'status': report['status'],
                    'file_size': report.get('file_size', 0),
                    'created_at': report['created_at'].isoformat(),
                    'expires_at': report['expires_at'].isoformat(),
                    'date_range': {
                        'start': report['date_range']['start_date'].isoformat(),
                        'end': report['date_range']['end_date'].isoformat()
                    },
                    'file_exists': file_exists
                })
            
            # Calculate pagination info
            total_pages = (total_count + per_page - 1) // per_page
            has_next = page < total_pages
            has_prev = page > 1
            
            return {
                'reports': reports,
                'pagination': {
                    'page': page,
                    'per_page': per_page,
                    'total_count': total_count,
                    'total_pages': total_pages,
                    'has_next': has_next,
                    'has_prev': has_prev
                }
            }, 200
            
        except Exception as e:
            self.logger.error(f"Error fetching reports: {str(e)}")
            raise ValidationError("Failed to fetch reports")


class ReportDetailResource(Resource):
    """Handle individual report operations"""
    
    def __init__(self):
        self.report_service = ReportService()
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    @require_permission('report.read')
    def get(self, report_id):
        """
        Get report details
        ---
        tags:
          - Reports
        security:
          - Bearer: []
        parameters:
          - in: path
            name: report_id
            type: string
            required: true
        responses:
          200:
            description: Report details
          404:
            description: Report not found
        """
        try:
            # Get current user info
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Get report
            report = self.report_service.get_report(report_id, user_role, organization_id)
            
            return {'report': report}, 200
            
        except (NotFoundError, AuthorizationError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Error fetching report {report_id}: {str(e)}")
            raise ValidationError("Failed to fetch report")
    
    @jwt_required()
    @require_permission('report.delete')
    def delete(self, report_id):
        """
        Delete report
        ---
        tags:
          - Reports
        security:
          - Bearer: []
        parameters:
          - in: path
            name: report_id
            type: string
            required: true
        responses:
          200:
            description: Report deleted successfully
          404:
            description: Report not found
        """
        try:
            # Get current user info
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Delete report
            self.report_service.delete_report(report_id, user_role, organization_id)
            
            return {'message': 'Report deleted successfully'}, 200
            
        except (NotFoundError, AuthorizationError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Error deleting report {report_id}: {str(e)}")
            raise ValidationError("Failed to delete report")


class ReportDownloadResource(Resource):
    """Handle report file downloads"""
    
    def __init__(self):
        self.report_service = ReportService()
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    @require_permission('report.read')
    def get(self, report_id):
        """
        Download report file
        ---
        tags:
          - Reports
        security:
          - Bearer: []
        parameters:
          - in: path
            name: report_id
            type: string
            required: true
        responses:
          200:
            description: Report file
            content:
              application/pdf:
                schema:
                  type: string
                  format: binary
              text/csv:
                schema:
                  type: string
                  format: binary
              application/json:
                schema:
                  type: string
                  format: binary
          404:
            description: Report not found
        """
        try:
            # Get current user info
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Get report
            report = self.report_service.get_report(report_id, user_role, organization_id)
            
            if not report['file_exists']:
                raise NotFoundError("Report file not found")
            
            file_path = report['file_path']
            
            # Determine MIME type based on format
            if report['format'] == 'pdf':
                mimetype = 'application/pdf'
            elif report['format'] == 'csv':
                mimetype = 'text/csv'
            elif report['format'] == 'json':
                mimetype = 'application/json'
            else:
                mimetype = 'application/octet-stream'
            
            # Generate download filename
            timestamp = datetime.utcnow().strftime('%Y%m%d_%H%M%S')
            download_name = f"{report['report_type']}_{timestamp}.{report['format']}"
            
            self.logger.info(f"Report downloaded: {report_id} by user {get_jwt_identity()}")
            
            return send_file(
                file_path,
                mimetype=mimetype,
                as_attachment=True,
                download_name=download_name
            )
            
        except (NotFoundError, AuthorizationError) as e:
            raise e
        except Exception as e:
            self.logger.error(f"Error downloading report {report_id}: {str(e)}")
            raise ValidationError("Failed to download report")


class ReportStatsResource(Resource):
    """Handle report statistics"""
    
    def __init__(self):
        self.logger = logging.getLogger(__name__)
    
    @jwt_required()
    @require_permission('report.read')
    def get(self):
        """
        Get report generation statistics
        ---
        tags:
          - Reports
        security:
          - Bearer: []
        responses:
          200:
            description: Report statistics
        """
        try:
            # Get current user info
            jwt_claims = get_jwt()
            user_role = jwt_claims.get('role')
            organization_id = jwt_claims.get('organization_id')
            
            # Build filters
            filters = {}
            if user_role != 'admin' and organization_id:
                filters['organization_id'] = ObjectId(organization_id)
            
            # Get statistics from database
            db = current_app.db
            
            # Total reports
            total_reports = db.reports.count_documents(filters)
            
            # Reports by type
            type_pipeline = [
                {'$match': filters},
                {'$group': {
                    '_id': '$report_type',
                    'count': {'$sum': 1}
                }}
            ]
            type_stats = {item['_id']: item['count'] for item in db.reports.aggregate(type_pipeline)}
            
            # Reports by format
            format_pipeline = [
                {'$match': filters},
                {'$group': {
                    '_id': '$format',
                    'count': {'$sum': 1}
                }}
            ]
            format_stats = {item['_id']: item['count'] for item in db.reports.aggregate(format_pipeline)}
            
            # Recent reports (last 30 days)
            thirty_days_ago = datetime.utcnow() - timedelta(days=30)
            recent_filters = dict(filters)
            recent_filters['created_at'] = {'$gte': thirty_days_ago}
            recent_reports = db.reports.count_documents(recent_filters)
            
            return {
                'stats': {
                    'total_reports': total_reports,
                    'recent_reports': recent_reports,
                    'by_type': type_stats,
                    'by_format': format_stats,
                    'timestamp': datetime.utcnow().isoformat()
                }
            }, 200
            
        except Exception as e:
            self.logger.error(f"Error fetching report statistics: {str(e)}")
            raise ValidationError("Failed to fetch report statistics")