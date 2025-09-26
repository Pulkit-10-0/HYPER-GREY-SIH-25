"""
Report Generation Service
Handles PDF, CSV, and JSON report generation
"""
import logging
import os
import json
import csv
from datetime import datetime, timedelta
from io import BytesIO, StringIO
from bson import ObjectId
from pymongo.errors import PyMongoError
from flask import current_app
from reportlab.lib import colors
from reportlab.lib.pagesizes import letter, A4
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer, Image
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
import pandas as pd

from backend.utils.exceptions import DatabaseError, ValidationError, NotFoundError


class ReportService:
    """Service class for report generation operations"""
    
    def __init__(self):
        self.logger = logging.getLogger(__name__)
    
    def generate_report(self, organization_id, report_type, date_range, format='pdf', filters=None):
        """
        Generate report based on type and parameters
        
        Args:
            organization_id (str): Organization ID
            report_type (str): Type of report
            date_range (dict): Date range for report
            format (str): Output format (pdf, csv, json)
            filters (dict): Additional filters
            
        Returns:
            dict: Report generation result
        """
        try:
            # Validate date range
            start_date = datetime.fromisoformat(date_range['start_date'].replace('Z', '+00:00'))
            end_date = datetime.fromisoformat(date_range['end_date'].replace('Z', '+00:00'))
            
            # Generate report based on type
            if report_type == 'quality_summary':
                report_data = self._generate_quality_summary(organization_id, start_date, end_date, filters)
            elif report_type == 'device_status':
                report_data = self._generate_device_status_report(organization_id, start_date, end_date, filters)
            elif report_type == 'analysis_results':
                report_data = self._generate_analysis_results_report(organization_id, start_date, end_date, filters)
            elif report_type == 'calibration_report':
                report_data = self._generate_calibration_report(organization_id, start_date, end_date, filters)
            else:
                raise ValidationError(f"Unknown report type: {report_type}")
            
            # Generate output in requested format
            if format == 'pdf':
                file_path = self._generate_pdf_report(report_data, report_type)
            elif format == 'csv':
                file_path = self._generate_csv_report(report_data, report_type)
            elif format == 'json':
                file_path = self._generate_json_report(report_data, report_type)
            else:
                raise ValidationError(f"Unsupported format: {format}")
            
            # Store report metadata
            report_metadata = {
                'report_type': report_type,
                'organization_id': ObjectId(organization_id),
                'date_range': {
                    'start_date': start_date,
                    'end_date': end_date
                },
                'format': format,
                'filters': filters or {},
                'file_path': file_path,
                'file_size': os.path.getsize(file_path) if os.path.exists(file_path) else 0,
                'status': 'completed',
                'created_at': datetime.utcnow(),
                'expires_at': datetime.utcnow() + timedelta(days=30)  # Reports expire after 30 days
            }
            
            db = current_app.db
            result = db.reports.insert_one(report_metadata)
            
            self.logger.info(f"Report generated: {report_type} for organization {organization_id}")
            
            return {
                'report_id': str(result.inserted_id),
                'report_type': report_type,
                'format': format,
                'file_path': file_path,
                'status': 'completed',
                'created_at': report_metadata['created_at'].isoformat(),
                'expires_at': report_metadata['expires_at'].isoformat()
            }
            
        except PyMongoError as e:
            self.logger.error(f"Database error generating report: {str(e)}")
            raise DatabaseError("Failed to generate report")
        except Exception as e:
            self.logger.error(f"Error generating report: {str(e)}")
            raise ValidationError(f"Report generation failed: {str(e)}")
    
    def _generate_quality_summary(self, organization_id, start_date, end_date, filters):
        """Generate quality summary report data"""
        try:
            db = current_app.db
            
            # Build aggregation pipeline
            match_stage = {
                'organization_id': ObjectId(organization_id),
                'analysis_timestamp': {'$gte': start_date, '$lte': end_date}
            }
            
            if filters:
                if 'device_id' in filters:
                    match_stage['device_id'] = filters['device_id']
                if 'herb_name' in filters:
                    match_stage['herb_name'] = {'$regex': filters['herb_name'], '$options': 'i'}
            
            pipeline = [
                {'$match': match_stage},
                {'$group': {
                    '_id': {
                        'herb_name': '$herb_name',
                        'device_id': '$device_id'
                    },
                    'total_analyses': {'$sum': 1},
                    'avg_quality_score': {'$avg': '$quality_metrics.overall_score'},
                    'grade_distribution': {'$push': '$quality_metrics.grade'},
                    'authenticity_rate': {
                        '$avg': {
                            '$cond': [
                                {'$eq': ['$predictions.authenticity.is_authentic', True]},
                                1, 0
                            ]
                        }
                    },
                    'contamination_rate': {
                        '$avg': {
                            '$cond': [
                                {'$eq': ['$predictions.contamination.contamination_detected', True]},
                                1, 0
                            ]
                        }
                    },
                    'latest_analysis': {'$max': '$analysis_timestamp'}
                }},
                {'$sort': {'_id.herb_name': 1, '_id.device_id': 1}}
            ]
            
            results = list(db.analysis_results.aggregate(pipeline))
            
            # Process results
            summary_data = []
            for result in results:
                herb_name = result['_id']['herb_name']
                device_id = result['_id']['device_id']
                
                # Calculate grade distribution
                grades = result['grade_distribution']
                grade_counts = {}
                for grade in ['A', 'B', 'C', 'D', 'F']:
                    grade_counts[grade] = grades.count(grade)
                
                summary_data.append({
                    'herb_name': herb_name,
                    'device_id': device_id,
                    'total_analyses': result['total_analyses'],
                    'average_quality_score': round(result['avg_quality_score'], 3),
                    'authenticity_rate': round(result['authenticity_rate'] * 100, 2),
                    'contamination_rate': round(result['contamination_rate'] * 100, 2),
                    'grade_distribution': grade_counts,
                    'latest_analysis': result['latest_analysis'].isoformat()
                })
            
            return {
                'report_title': 'Quality Summary Report',
                'date_range': {
                    'start': start_date.isoformat(),
                    'end': end_date.isoformat()
                },
                'organization_id': organization_id,
                'summary_data': summary_data,
                'total_records': len(summary_data)
            }
            
        except PyMongoError as e:
            self.logger.error(f"Database error in quality summary: {str(e)}")
            raise DatabaseError("Failed to generate quality summary")
    
    def _generate_device_status_report(self, organization_id, start_date, end_date, filters):
        """Generate device status report data"""
        try:
            db = current_app.db
            
            # Get devices for organization
            device_query = {'organization_id': ObjectId(organization_id)}
            if filters and 'device_id' in filters:
                device_query['device_id'] = filters['device_id']
            
            devices = list(db.devices.find(device_query))
            
            device_data = []
            for device in devices:
                device_id = device['device_id']
                
                # Get reading count in date range
                reading_count = db.sensor_readings.count_documents({
                    'device_id': device_id,
                    'timestamp': {'$gte': start_date, '$lte': end_date}
                })
                
                # Get latest reading
                latest_reading = db.sensor_readings.find_one(
                    {'device_id': device_id},
                    sort=[('timestamp', -1)]
                )
                
                # Calculate uptime (simplified)
                uptime_hours = device.get('statistics', {}).get('uptime_hours', 0)
                
                device_data.append({
                    'device_id': device_id,
                    'device_model': device['device_model'],
                    'firmware_version': device['firmware_version'],
                    'status': device['status'],
                    'location': device.get('location', {}),
                    'readings_in_period': reading_count,
                    'total_readings': device.get('statistics', {}).get('total_readings', 0),
                    'last_reading': latest_reading['timestamp'].isoformat() if latest_reading else None,
                    'uptime_hours': uptime_hours,
                    'last_calibration': device.get('calibration_data', {}).get('last_calibration'),
                    'next_calibration_due': device.get('calibration_data', {}).get('next_calibration_due')
                })
            
            return {
                'report_title': 'Device Status Report',
                'date_range': {
                    'start': start_date.isoformat(),
                    'end': end_date.isoformat()
                },
                'organization_id': organization_id,
                'device_data': device_data,
                'total_devices': len(device_data)
            }
            
        except PyMongoError as e:
            self.logger.error(f"Database error in device status report: {str(e)}")
            raise DatabaseError("Failed to generate device status report")
    
    def _generate_analysis_results_report(self, organization_id, start_date, end_date, filters):
        """Generate detailed analysis results report data"""
        try:
            db = current_app.db
            
            # Build query
            query = {
                'organization_id': ObjectId(organization_id),
                'analysis_timestamp': {'$gte': start_date, '$lte': end_date}
            }
            
            if filters:
                if 'device_id' in filters:
                    query['device_id'] = filters['device_id']
                if 'herb_name' in filters:
                    query['herb_name'] = {'$regex': filters['herb_name'], '$options': 'i'}
                if 'grade' in filters:
                    query['quality_metrics.grade'] = filters['grade']
            
            # Get analysis results
            results = list(db.analysis_results.find(query).sort('analysis_timestamp', -1))
            
            analysis_data = []
            for result in results:
                analysis_data.append({
                    'analysis_id': str(result['_id']),
                    'reading_id': str(result['reading_id']),
                    'device_id': result['device_id'],
                    'herb_name': result['herb_name'],
                    'analysis_timestamp': result['analysis_timestamp'].isoformat(),
                    'quality_score': result['quality_metrics'].get('overall_score', 0),
                    'quality_grade': result['quality_metrics'].get('grade', 'Unknown'),
                    'is_authentic': result['predictions'].get('authenticity', {}).get('is_authentic'),
                    'authenticity_confidence': result['predictions'].get('authenticity', {}).get('confidence', 0),
                    'contamination_detected': result['predictions'].get('contamination', {}).get('contamination_detected'),
                    'contamination_level': result['predictions'].get('contamination', {}).get('contamination_level', 0),
                    'recommendations_count': len(result.get('recommendations', [])),
                    'processing_time_ms': result.get('processing_time_ms', 0)
                })
            
            return {
                'report_title': 'Analysis Results Report',
                'date_range': {
                    'start': start_date.isoformat(),
                    'end': end_date.isoformat()
                },
                'organization_id': organization_id,
                'analysis_data': analysis_data,
                'total_analyses': len(analysis_data)
            }
            
        except PyMongoError as e:
            self.logger.error(f"Database error in analysis results report: {str(e)}")
            raise DatabaseError("Failed to generate analysis results report")
    
    def _generate_calibration_report(self, organization_id, start_date, end_date, filters):
        """Generate calibration report data"""
        try:
            db = current_app.db
            
            # Get devices for organization
            device_query = {'organization_id': ObjectId(organization_id)}
            if filters and 'device_id' in filters:
                device_query['device_id'] = filters['device_id']
            
            devices = list(db.devices.find(device_query))
            
            calibration_data = []
            for device in devices:
                calibration_history = device.get('calibration_data', {}).get('calibration_history', [])
                
                # Filter calibration history by date range
                filtered_history = [
                    cal for cal in calibration_history
                    if cal.get('calibration_date') and start_date <= cal['calibration_date'] <= end_date
                ]
                
                calibration_data.append({
                    'device_id': device['device_id'],
                    'device_model': device['device_model'],
                    'last_calibration': device.get('calibration_data', {}).get('last_calibration'),
                    'next_calibration_due': device.get('calibration_data', {}).get('next_calibration_due'),
                    'calibrations_in_period': len(filtered_history),
                    'calibration_history': [
                        {
                            'date': cal['calibration_date'].isoformat(),
                            'calibrated_by': str(cal.get('calibrated_by', '')),
                            'notes': cal.get('calibration_notes', ''),
                            'coefficients_count': len(cal.get('calibration_coefficients', {}))
                        }
                        for cal in filtered_history
                    ]
                })
            
            return {
                'report_title': 'Calibration Report',
                'date_range': {
                    'start': start_date.isoformat(),
                    'end': end_date.isoformat()
                },
                'organization_id': organization_id,
                'calibration_data': calibration_data,
                'total_devices': len(calibration_data)
            }
            
        except PyMongoError as e:
            self.logger.error(f"Database error in calibration report: {str(e)}")
            raise DatabaseError("Failed to generate calibration report")
    
    def _generate_pdf_report(self, report_data, report_type):
        """Generate PDF report"""
        try:
            # Create reports directory if it doesn't exist
            reports_dir = os.path.join(current_app.instance_path, 'reports')
            os.makedirs(reports_dir, exist_ok=True)
            
            # Generate filename
            timestamp = datetime.utcnow().strftime('%Y%m%d_%H%M%S')
            filename = f"{report_type}_{timestamp}.pdf"
            file_path = os.path.join(reports_dir, filename)
            
            # Create PDF document
            doc = SimpleDocTemplate(file_path, pagesize=A4)
            styles = getSampleStyleSheet()
            story = []
            
            # Title
            title_style = ParagraphStyle(
                'CustomTitle',
                parent=styles['Heading1'],
                fontSize=18,
                spaceAfter=30,
                alignment=1  # Center alignment
            )
            story.append(Paragraph(report_data['report_title'], title_style))
            
            # Date range
            date_info = f"Period: {report_data['date_range']['start']} to {report_data['date_range']['end']}"
            story.append(Paragraph(date_info, styles['Normal']))
            story.append(Spacer(1, 20))
            
            # Generate content based on report type
            if report_type == 'quality_summary':
                self._add_quality_summary_content(story, report_data, styles)
            elif report_type == 'device_status':
                self._add_device_status_content(story, report_data, styles)
            elif report_type == 'analysis_results':
                self._add_analysis_results_content(story, report_data, styles)
            elif report_type == 'calibration_report':
                self._add_calibration_content(story, report_data, styles)
            
            # Build PDF
            doc.build(story)
            
            return file_path
            
        except Exception as e:
            self.logger.error(f"Error generating PDF report: {str(e)}")
            raise ValidationError(f"PDF generation failed: {str(e)}")
    
    def _generate_csv_report(self, report_data, report_type):
        """Generate CSV report"""
        try:
            # Create reports directory if it doesn't exist
            reports_dir = os.path.join(current_app.instance_path, 'reports')
            os.makedirs(reports_dir, exist_ok=True)
            
            # Generate filename
            timestamp = datetime.utcnow().strftime('%Y%m%d_%H%M%S')
            filename = f"{report_type}_{timestamp}.csv"
            file_path = os.path.join(reports_dir, filename)
            
            # Convert data to DataFrame and save as CSV
            if report_type == 'quality_summary':
                df = pd.DataFrame(report_data['summary_data'])
            elif report_type == 'device_status':
                df = pd.DataFrame(report_data['device_data'])
            elif report_type == 'analysis_results':
                df = pd.DataFrame(report_data['analysis_data'])
            elif report_type == 'calibration_report':
                # Flatten calibration data for CSV
                flattened_data = []
                for device in report_data['calibration_data']:
                    for cal in device['calibration_history']:
                        flattened_data.append({
                            'device_id': device['device_id'],
                            'device_model': device['device_model'],
                            'calibration_date': cal['date'],
                            'calibrated_by': cal['calibrated_by'],
                            'notes': cal['notes'],
                            'coefficients_count': cal['coefficients_count']
                        })
                df = pd.DataFrame(flattened_data)
            
            df.to_csv(file_path, index=False)
            
            return file_path
            
        except Exception as e:
            self.logger.error(f"Error generating CSV report: {str(e)}")
            raise ValidationError(f"CSV generation failed: {str(e)}")
    
    def _generate_json_report(self, report_data, report_type):
        """Generate JSON report"""
        try:
            # Create reports directory if it doesn't exist
            reports_dir = os.path.join(current_app.instance_path, 'reports')
            os.makedirs(reports_dir, exist_ok=True)
            
            # Generate filename
            timestamp = datetime.utcnow().strftime('%Y%m%d_%H%M%S')
            filename = f"{report_type}_{timestamp}.json"
            file_path = os.path.join(reports_dir, filename)
            
            # Save as JSON
            with open(file_path, 'w') as f:
                json.dump(report_data, f, indent=2, default=str)
            
            return file_path
            
        except Exception as e:
            self.logger.error(f"Error generating JSON report: {str(e)}")
            raise ValidationError(f"JSON generation failed: {str(e)}")
    
    def _add_quality_summary_content(self, story, report_data, styles):
        """Add quality summary content to PDF"""
        # Summary statistics
        total_records = report_data['total_records']
        story.append(Paragraph(f"Total Records: {total_records}", styles['Normal']))
        story.append(Spacer(1, 10))
        
        if report_data['summary_data']:
            # Create table data
            table_data = [['Herb Name', 'Device ID', 'Analyses', 'Avg Quality', 'Auth Rate %', 'Contam Rate %', 'Grade A', 'Grade B', 'Grade C']]
            
            for item in report_data['summary_data']:
                table_data.append([
                    item['herb_name'],
                    item['device_id'],
                    str(item['total_analyses']),
                    f"{item['average_quality_score']:.3f}",
                    f"{item['authenticity_rate']:.1f}%",
                    f"{item['contamination_rate']:.1f}%",
                    str(item['grade_distribution'].get('A', 0)),
                    str(item['grade_distribution'].get('B', 0)),
                    str(item['grade_distribution'].get('C', 0))
                ])
            
            # Create table
            table = Table(table_data)
            table.setStyle(TableStyle([
                ('BACKGROUND', (0, 0), (-1, 0), colors.grey),
                ('TEXTCOLOR', (0, 0), (-1, 0), colors.whitesmoke),
                ('ALIGN', (0, 0), (-1, -1), 'CENTER'),
                ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
                ('FONTSIZE', (0, 0), (-1, 0), 10),
                ('BOTTOMPADDING', (0, 0), (-1, 0), 12),
                ('BACKGROUND', (0, 1), (-1, -1), colors.beige),
                ('GRID', (0, 0), (-1, -1), 1, colors.black)
            ]))
            
            story.append(table)
    
    def _add_device_status_content(self, story, report_data, styles):
        """Add device status content to PDF"""
        total_devices = report_data['total_devices']
        story.append(Paragraph(f"Total Devices: {total_devices}", styles['Normal']))
        story.append(Spacer(1, 10))
        
        if report_data['device_data']:
            # Create table data
            table_data = [['Device ID', 'Model', 'Status', 'Readings', 'Last Reading', 'Uptime (hrs)']]
            
            for device in report_data['device_data']:
                table_data.append([
                    device['device_id'],
                    device['device_model'],
                    device['status'],
                    str(device['readings_in_period']),
                    device['last_reading'][:19] if device['last_reading'] else 'N/A',
                    str(device['uptime_hours'])
                ])
            
            # Create table
            table = Table(table_data)
            table.setStyle(TableStyle([
                ('BACKGROUND', (0, 0), (-1, 0), colors.grey),
                ('TEXTCOLOR', (0, 0), (-1, 0), colors.whitesmoke),
                ('ALIGN', (0, 0), (-1, -1), 'CENTER'),
                ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
                ('FONTSIZE', (0, 0), (-1, 0), 10),
                ('BOTTOMPADDING', (0, 0), (-1, 0), 12),
                ('BACKGROUND', (0, 1), (-1, -1), colors.beige),
                ('GRID', (0, 0), (-1, -1), 1, colors.black)
            ]))
            
            story.append(table)
    
    def _add_analysis_results_content(self, story, report_data, styles):
        """Add analysis results content to PDF"""
        total_analyses = report_data['total_analyses']
        story.append(Paragraph(f"Total Analyses: {total_analyses}", styles['Normal']))
        story.append(Spacer(1, 10))
        
        if report_data['analysis_data']:
            # Create table data (first 50 records to avoid huge PDFs)
            table_data = [['Device ID', 'Herb Name', 'Grade', 'Quality Score', 'Authentic', 'Contaminated', 'Date']]
            
            for item in report_data['analysis_data'][:50]:  # Limit to first 50
                table_data.append([
                    item['device_id'],
                    item['herb_name'][:15],  # Truncate long names
                    item['quality_grade'],
                    f"{item['quality_score']:.3f}",
                    'Yes' if item['is_authentic'] else 'No',
                    'Yes' if item['contamination_detected'] else 'No',
                    item['analysis_timestamp'][:10]  # Date only
                ])
            
            # Create table
            table = Table(table_data)
            table.setStyle(TableStyle([
                ('BACKGROUND', (0, 0), (-1, 0), colors.grey),
                ('TEXTCOLOR', (0, 0), (-1, 0), colors.whitesmoke),
                ('ALIGN', (0, 0), (-1, -1), 'CENTER'),
                ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
                ('FONTSIZE', (0, 0), (-1, 0), 9),
                ('BOTTOMPADDING', (0, 0), (-1, 0), 12),
                ('BACKGROUND', (0, 1), (-1, -1), colors.beige),
                ('GRID', (0, 0), (-1, -1), 1, colors.black)
            ]))
            
            story.append(table)
            
            if len(report_data['analysis_data']) > 50:
                story.append(Spacer(1, 10))
                story.append(Paragraph(f"Note: Showing first 50 of {total_analyses} records. Download CSV for complete data.", styles['Italic']))
    
    def _add_calibration_content(self, story, report_data, styles):
        """Add calibration content to PDF"""
        total_devices = report_data['total_devices']
        story.append(Paragraph(f"Total Devices: {total_devices}", styles['Normal']))
        story.append(Spacer(1, 10))
        
        for device in report_data['calibration_data']:
            # Device header
            story.append(Paragraph(f"Device: {device['device_id']} ({device['device_model']})", styles['Heading3']))
            
            if device['calibration_history']:
                # Create table for calibration history
                table_data = [['Date', 'Calibrated By', 'Notes']]
                
                for cal in device['calibration_history']:
                    table_data.append([
                        cal['date'][:10],  # Date only
                        cal['calibrated_by'][:20],  # Truncate long IDs
                        cal['notes'][:50] if cal['notes'] else 'N/A'  # Truncate long notes
                    ])
                
                table = Table(table_data)
                table.setStyle(TableStyle([
                    ('BACKGROUND', (0, 0), (-1, 0), colors.lightgrey),
                    ('ALIGN', (0, 0), (-1, -1), 'LEFT'),
                    ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
                    ('FONTSIZE', (0, 0), (-1, 0), 9),
                    ('GRID', (0, 0), (-1, -1), 1, colors.black)
                ]))
                
                story.append(table)
            else:
                story.append(Paragraph("No calibrations in selected period", styles['Italic']))
            
            story.append(Spacer(1, 15))
    
    def get_report(self, report_id, user_role, organization_id):
        """
        Get report by ID
        
        Args:
            report_id (str): Report ID
            user_role (str): User role
            organization_id (str): User organization ID
            
        Returns:
            dict: Report metadata
        """
        try:
            db = current_app.db
            
            # Build query
            query = {'_id': ObjectId(report_id)}
            
            # Add organization filter for non-admin users
            if user_role != 'admin' and organization_id:
                query['organization_id'] = ObjectId(organization_id)
            
            report = db.reports.find_one(query)
            
            if not report:
                raise NotFoundError("Report not found")
            
            # Check if report file exists
            file_exists = os.path.exists(report['file_path']) if report.get('file_path') else False
            
            return {
                'id': str(report['_id']),
                'report_type': report['report_type'],
                'format': report['format'],
                'status': report['status'],
                'file_path': report['file_path'] if file_exists else None,
                'file_size': report.get('file_size', 0),
                'created_at': report['created_at'].isoformat(),
                'expires_at': report['expires_at'].isoformat(),
                'date_range': {
                    'start': report['date_range']['start_date'].isoformat(),
                    'end': report['date_range']['end_date'].isoformat()
                },
                'file_exists': file_exists
            }
            
        except (NotFoundError,) as e:
            raise e
        except PyMongoError as e:
            self.logger.error(f"Database error fetching report: {str(e)}")
            raise DatabaseError("Failed to fetch report")
    
    def delete_report(self, report_id, user_role, organization_id):
        """
        Delete report
        
        Args:
            report_id (str): Report ID
            user_role (str): User role
            organization_id (str): User organization ID
        """
        try:
            db = current_app.db
            
            # Build query
            query = {'_id': ObjectId(report_id)}
            
            # Add organization filter for non-admin users
            if user_role != 'admin' and organization_id:
                query['organization_id'] = ObjectId(organization_id)
            
            report = db.reports.find_one(query)
            
            if not report:
                raise NotFoundError("Report not found")
            
            # Delete file if it exists
            if report.get('file_path') and os.path.exists(report['file_path']):
                os.remove(report['file_path'])
            
            # Delete report metadata
            db.reports.delete_one({'_id': ObjectId(report_id)})
            
            self.logger.info(f"Report deleted: {report_id}")
            
        except (NotFoundError,) as e:
            raise e
        except PyMongoError as e:
            self.logger.error(f"Database error deleting report: {str(e)}")
            raise DatabaseError("Failed to delete report")