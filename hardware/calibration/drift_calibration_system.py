# AyuSure Advanced Drift Calibration System
# Real-time sensor drift correction and predictive maintenance

import numpy as np
import pandas as pd
from datetime import datetime, timedelta
import json
import math

class AdvancedDriftCalibrationEngine:
    def __init__(self):
        self.reference_standards = self._load_reference_standards()
        self.calibration_history = []
        self.drift_models = {}
        self.last_calibration = None

    def _load_reference_standards(self):
        """Load comprehensive reference standards for all sensor types"""
        return {
            'electrode_standards': {
                'SS': {
                    'reference_responses': {'pH4': 1.85, 'pH7': 1.42, 'pH10': 0.95},
                    'temp_coefficient': -1.2e-3,
                    'aging_rate': 0.005,  # mV/month
                    'noise_threshold': 0.002  # V
                },
                'Cu': {
                    'reference_responses': {'pH4': 2.15, 'pH7': 1.68, 'pH10': 1.12},
                    'temp_coefficient': -1.8e-3,
                    'aging_rate': 0.008,
                    'noise_threshold': 0.003
                },
                'Zn': {
                    'reference_responses': {'pH4': 2.45, 'pH7': 1.89, 'pH10': 1.35},
                    'temp_coefficient': -1.5e-3,
                    'aging_rate': 0.006,
                    'noise_threshold': 0.0025
                },
                'Ag': {
                    'reference_responses': {'pH4': 2.78, 'pH7': 2.21, 'pH10': 1.58},
                    'temp_coefficient': -0.9e-3,
                    'aging_rate': 0.004,
                    'noise_threshold': 0.002
                },
                'Pt': {
                    'reference_responses': {'pH4': 2.52, 'pH7': 2.03, 'pH10': 1.41},
                    'temp_coefficient': -0.7e-3,
                    'aging_rate': 0.003,
                    'noise_threshold': 0.0015
                }
            },
            'environmental_standards': {
                'pH': {
                    'slope_nominal': -59.16,  # mV/pH at 25°C
                    'temp_coefficient': -0.198,  # mV/pH/°C
                    'drift_threshold': 1.0  # mV/day
                },
                'conductivity': {
                    'temp_coefficient': 0.021,  # /°C
                    'cell_constant': 1.0,
                    'drift_threshold': 0.05  # %/day
                }
            }
        }

    def detect_sensor_drift(self, sensor_readings, sensor_type, electrode_type=None):
        """Advanced drift detection using multiple statistical methods"""
        readings = np.array(sensor_readings)
        n_samples = len(readings)

        if n_samples < 10:
            return {'drift_detected': False, 'reason': 'Insufficient data'}

        # Method 1: Trend Analysis using Linear Regression
        time_points = np.arange(n_samples)
        slope = self._calculate_linear_trend(time_points, readings)

        # Method 2: CUSUM (Cumulative Sum) Control Chart
        cusum_stats = self._calculate_cusum(readings)

        # Method 3: Moving Range Analysis
        moving_ranges = np.abs(np.diff(readings))
        mr_stats = self._calculate_moving_range_stats(moving_ranges)

        # Method 4: Statistical Process Control
        spc_stats = self._statistical_process_control(readings)

        # Determine drift thresholds based on sensor type
        if sensor_type == 'electrode' and electrode_type:
            drift_threshold = self.reference_standards['electrode_standards'][electrode_type]['aging_rate'] / 30
        elif sensor_type == 'pH':
            drift_threshold = self.reference_standards['environmental_standards']['pH']['drift_threshold']
        else:
            drift_threshold = 0.01

        # Convert slope to drift rate (units per hour)
        drift_rate = slope * 3600 / n_samples if n_samples > 1 else 0

        # Comprehensive drift detection logic
        drift_indicators = {
            'linear_trend': abs(drift_rate) > drift_threshold,
            'cusum_alarm': cusum_stats['alarm_triggered'],
            'range_alarm': mr_stats['out_of_control'],
            'spc_alarm': spc_stats['out_of_control']
        }

        drift_confidence = sum(drift_indicators.values()) / len(drift_indicators)
        drift_detected = drift_confidence >= 0.4

        return {
            'drift_detected': drift_detected,
            'drift_confidence': drift_confidence,
            'drift_rate_per_hour': drift_rate,
            'linear_slope': slope,
            'cusum_statistics': cusum_stats,
            'moving_range_stats': mr_stats,
            'spc_statistics': spc_stats,
            'individual_indicators': drift_indicators,
            'recommendation': self._generate_drift_recommendation(drift_detected, drift_confidence, drift_rate)
        }

    def _calculate_linear_trend(self, x, y):
        """Calculate linear trend slope using least squares"""
        n = len(x)
        if n < 2:
            return 0

        sum_x = np.sum(x)
        sum_y = np.sum(y)
        sum_xy = np.sum(x * y)
        sum_x2 = np.sum(x * x)

        slope = (n * sum_xy - sum_x * sum_y) / (n * sum_x2 - sum_x * sum_x)
        return slope

    def _calculate_cusum(self, data, target=None, std_dev=None):
        """Calculate CUSUM statistics for drift detection"""
        if target is None:
            target = np.mean(data[:min(10, len(data))])

        if std_dev is None:
            std_dev = np.std(data[:min(10, len(data))])

        deviations = (data - target) / max(std_dev, 1e-6)

        k = 0.5  # Reference value
        h = 4.0  # Decision interval

        cusum_pos = np.zeros(len(deviations))
        cusum_neg = np.zeros(len(deviations))

        for i in range(1, len(deviations)):
            cusum_pos[i] = max(0, cusum_pos[i-1] + deviations[i] - k)
            cusum_neg[i] = min(0, cusum_neg[i-1] + deviations[i] + k)

        pos_alarm = np.any(cusum_pos > h)
        neg_alarm = np.any(cusum_neg < -h)

        return {
            'cusum_positive': cusum_pos.tolist(),
            'cusum_negative': cusum_neg.tolist(),
            'positive_alarm': pos_alarm,
            'negative_alarm': neg_alarm,
            'alarm_triggered': pos_alarm or neg_alarm,
            'max_cusum_pos': float(np.max(cusum_pos)),
            'min_cusum_neg': float(np.min(cusum_neg))
        }

    def _calculate_moving_range_stats(self, moving_ranges):
        """Calculate moving range control chart statistics"""
        mean_mr = np.mean(moving_ranges)
        d2 = 1.128  # Control chart constant for n=2
        d3 = 0.853
        d4 = 1.693

        ucl_mr = d4 * mean_mr
        lcl_mr = max(0, d3 * mean_mr)

        out_of_control_points = np.where((moving_ranges > ucl_mr) | (moving_ranges < lcl_mr))[0]

        return {
            'mean_moving_range': float(mean_mr),
            'upper_control_limit': float(ucl_mr),
            'lower_control_limit': float(lcl_mr),
            'out_of_control_points': out_of_control_points.tolist(),
            'out_of_control': len(out_of_control_points) > 0,
            'percent_out_of_control': len(out_of_control_points) / len(moving_ranges) * 100
        }

    def _statistical_process_control(self, data):
        """Statistical process control analysis"""
        mean_val = np.mean(data)
        std_val = np.std(data)

        # Control limits (3-sigma)
        ucl = mean_val + 3 * std_val
        lcl = mean_val - 3 * std_val

        # Check for out-of-control points
        out_of_control = np.where((data > ucl) | (data < lcl))[0]

        # Additional SPC rules
        # Rule 2: 9 points in a row on same side of centerline
        above_center = data > mean_val
        below_center = data < mean_val

        rule2_violation = False
        consecutive_above = 0
        consecutive_below = 0

        for i in range(len(data)):
            if above_center[i]:
                consecutive_above += 1
                consecutive_below = 0
            else:
                consecutive_below += 1
                consecutive_above = 0

            if consecutive_above >= 9 or consecutive_below >= 9:
                rule2_violation = True
                break

        return {
            'mean': float(mean_val),
            'std_deviation': float(std_val),
            'upper_control_limit': float(ucl),
            'lower_control_limit': float(lcl),
            'out_of_control_points': out_of_control.tolist(),
            'rule2_violation': rule2_violation,
            'out_of_control': len(out_of_control) > 0 or rule2_violation
        }

    def _generate_drift_recommendation(self, drift_detected, confidence, drift_rate):
        """Generate actionable recommendations based on drift analysis"""
        if not drift_detected:
            return {
                'action': 'monitor',
                'urgency': 'low',
                'message': 'No significant drift detected. Continue normal monitoring.',
                'next_check_hours': 24
            }

        if confidence < 0.6:
            return {
                'action': 'increase_monitoring',
                'urgency': 'medium',
                'message': 'Possible drift detected. Increase monitoring frequency.',
                'next_check_hours': 4
            }

        if abs(drift_rate) > 0.01:
            return {
                'action': 'immediate_calibration',
                'urgency': 'high',
                'message': 'Significant drift detected. Immediate calibration recommended.',
                'next_check_hours': 1
            }

        return {
            'action': 'schedule_calibration',
            'urgency': 'medium',
            'message': 'Moderate drift detected. Schedule calibration within 24 hours.',
            'next_check_hours': 8
        }

    def apply_temperature_compensation(self, reading, temperature, sensor_type, electrode_type=None):
        """Apply temperature compensation to sensor readings"""
        reference_temp = 25.0  # °C
        temp_diff = temperature - reference_temp

        if sensor_type == 'electrode' and electrode_type:
            temp_coeff = self.reference_standards['electrode_standards'][electrode_type]['temp_coefficient']
            compensation = temp_coeff * temp_diff

        elif sensor_type == 'pH':
            # Nernst equation based pH compensation
            nernst_slope_25 = -59.16  # mV/pH at 25°C
            nernst_slope_t = nernst_slope_25 * (temperature + 273.15) / 298.15

            ph_at_25 = (reading - 2.0) / (nernst_slope_25 / 1000)
            compensated_voltage = 2.0 + ph_at_25 * (nernst_slope_t / 1000)
            return compensated_voltage

        elif sensor_type == 'conductivity':
            temp_coeff = self.reference_standards['environmental_standards']['conductivity']['temp_coefficient']
            compensation_factor = 1 + temp_coeff * temp_diff
            return reading / compensation_factor
        else:
            compensation = -1.0e-3 * temp_diff

        return reading - compensation

    def kalman_filter_enhancement(self, measurements, sensor_type='electrode'):
        """Kalman filtering for sensor signal enhancement"""
        measurements = np.array(measurements)
        n = len(measurements)

        if n < 3:
            return measurements

        # Kalman filter parameters
        if sensor_type == 'electrode':
            process_noise = 1e-6
            measurement_noise = 1e-4
        elif sensor_type == 'pH':
            process_noise = 1e-5
            measurement_noise = 1e-3
        else:
            process_noise = 1e-5
            measurement_noise = 1e-3

        filtered = np.zeros(n)
        x_est = measurements[0]
        p_est = 1.0

        for i in range(n):
            # Prediction step
            x_pred = x_est
            p_pred = p_est + process_noise

            # Update step
            innovation = measurements[i] - x_pred
            kalman_gain = p_pred / (p_pred + measurement_noise)

            x_est = x_pred + kalman_gain * innovation
            p_est = (1 - kalman_gain) * p_pred

            filtered[i] = x_est

        return filtered

    def cross_validation_check(self, current_readings, reference_profile, herb_name):
        """Cross-validate current readings against known herb profiles"""
        if not reference_profile or not current_readings:
            return {'validation_score': 0, 'status': 'insufficient_data'}

        electrodes = ['SS', 'Cu', 'Zn', 'Ag', 'Pt']
        expected_values = []
        actual_values = []

        for electrode in electrodes:
            if electrode in reference_profile.get('electrode_response', {}):
                expected_values.append(reference_profile['electrode_response'][electrode])
                actual_values.append(current_readings.get(f'{electrode}_voltage', 0))

        if len(expected_values) < 3:
            return {'validation_score': 0, 'status': 'insufficient_reference_data'}

        expected_values = np.array(expected_values)
        actual_values = np.array(actual_values)

        # Calculate validation metrics
        correlation = np.corrcoef(expected_values, actual_values)[0, 1]
        rmse = np.sqrt(np.mean((expected_values - actual_values)**2))
        normalized_rmse = rmse / (np.max(expected_values) - np.min(expected_values))
        relative_errors = np.abs((actual_values - expected_values) / expected_values) * 100
        mean_relative_error = np.mean(relative_errors)

        # Calculate composite validation score
        correlation_score = max(0, correlation) * 30
        rmse_score = max(0, (1 - normalized_rmse)) * 30
        error_score = max(0, (1 - mean_relative_error/100)) * 40

        composite_score = correlation_score + rmse_score + error_score

        # Determine validation status
        if composite_score >= 80:
            status = 'excellent_match'
        elif composite_score >= 60:
            status = 'good_match'
        elif composite_score >= 40:
            status = 'acceptable_match'
        elif composite_score >= 20:
            status = 'poor_match'
        else:
            status = 'no_match'

        return {
            'validation_score': composite_score,
            'status': status,
            'herb_name': herb_name,
            'metrics': {
                'correlation': float(correlation),
                'normalized_rmse': float(normalized_rmse),
                'mean_relative_error': float(mean_relative_error)
            },
            'electrode_comparison': {
                'expected': expected_values.tolist(),
                'actual': actual_values.tolist(),
                'relative_errors': relative_errors.tolist()
            }
        }

    def predictive_maintenance_analysis(self, sensor_history, usage_data):
        """Predictive maintenance analysis"""
        if not sensor_history or len(sensor_history) < 20:
            return {'status': 'insufficient_data'}

        timestamps = [entry['timestamp'] for entry in sensor_history]
        drift_rates = [entry.get('drift_rate', 0) for entry in sensor_history]
        noise_levels = [entry.get('noise_level', 0) for entry in sensor_history]

        # Calculate trends
        n = len(drift_rates)
        time_indices = list(range(n))

        drift_trend = self._calculate_linear_trend(np.array(time_indices), np.array(drift_rates))
        noise_trend = self._calculate_linear_trend(np.array(time_indices), np.array(noise_levels))

        current_drift = drift_rates[-1] if drift_rates else 0
        current_noise = noise_levels[-1] if noise_levels else 0

        # Predict maintenance needs
        drift_threshold = 0.05
        noise_threshold = 0.01

        maintenance_predictions = {}

        if drift_trend > 0 and current_drift < drift_threshold:
            days_to_drift_limit = (drift_threshold - current_drift) / max(drift_trend, 1e-10)
            maintenance_predictions['drift_maintenance'] = max(1, days_to_drift_limit)

        if noise_trend > 0 and current_noise < noise_threshold:
            days_to_noise_limit = (noise_threshold - current_noise) / max(noise_trend, 1e-10)
            maintenance_predictions['noise_maintenance'] = max(1, days_to_noise_limit)

        # Overall recommendation
        if maintenance_predictions:
            next_maintenance_days = min(maintenance_predictions.values())
        else:
            next_maintenance_days = 90

        if next_maintenance_days < 7:
            urgency = 'critical'
            message = 'Immediate maintenance required within 1 week'
        elif next_maintenance_days < 30:
            urgency = 'high'
            message = f'Maintenance recommended within {int(next_maintenance_days)} days'
        else:
            urgency = 'medium'
            message = f'Next maintenance due in {int(next_maintenance_days)} days'

        return {
            'status': 'analysis_complete',
            'next_maintenance_days': int(next_maintenance_days),
            'urgency': urgency,
            'message': message,
            'current_performance': {
                'drift_rate': float(current_drift),
                'noise_level': float(current_noise)
            },
            'performance_trends': {
                'drift_trend': float(drift_trend),
                'noise_trend': float(noise_trend)
            }
        }

# Example usage and testing
if __name__ == "__main__":
    print("Advanced Drift Calibration System v2.0")
    print("=" * 50)

    # Initialize system
    engine = AdvancedDriftCalibrationEngine()

    # Generate test data
    np.random.seed(42)
    test_readings = 2.0 + 0.001 * np.arange(50) + np.random.normal(0, 0.002, 50)

    # Test drift detection
    result = engine.detect_sensor_drift(test_readings, 'electrode', 'Pt')

    print(f"Drift detected: {result['drift_detected']}")
    print(f"Confidence: {result['drift_confidence']:.2f}")
    print(f"Drift rate: {result['drift_rate_per_hour']:.6f} V/hour")
    print(f"Recommendation: {result['recommendation']['action']}")

    print("\nSystem initialized successfully")
