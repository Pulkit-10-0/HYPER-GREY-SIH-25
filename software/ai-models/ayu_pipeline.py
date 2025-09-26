# AyuSure AI Models for Herb Authentication
# Advanced machine learning pipeline for electronic tongue analysis

import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split, cross_val_score, GridSearchCV
from sklearn.preprocessing import StandardScaler, LabelEncoder
from sklearn.neural_network import MLPRegressor, MLPClassifier
from sklearn.ensemble import RandomForestRegressor, IsolationForest
from sklearn.metrics import mean_absolute_error, accuracy_score, classification_report
from sklearn.pipeline import Pipeline
import pickle
import joblib
from datetime import datetime
import warnings
warnings.filterwarnings('ignore')

class AyuSureAIModelPipeline:
    def __init__(self):
        self.taste_model = None
        self.adulteration_model = None
        self.phytochemical_model = None
        self.scaler = StandardScaler()
        self.label_encoder = LabelEncoder()
        self.model_version = "2.0.1"
        self.training_date = None

    def load_training_data(self, processed_data_path, synthetic_data_path=None):
        """Load and combine training datasets"""
        print("Loading training data...")

        # Load processed sensor data
        df_processed = pd.read_csv(processed_data_path)
        print(f"Loaded {len(df_processed)} processed samples")

        # Load synthetic data if available
        if synthetic_data_path:
            df_synthetic = pd.read_csv(synthetic_data_path)
            print(f"Loaded {len(df_synthetic)} synthetic samples")

            # Combine datasets
            df_combined = pd.concat([df_processed, df_synthetic], ignore_index=True)
        else:
            df_combined = df_processed

        print(f"Total training samples: {len(df_combined)}")
        return df_combined

    def preprocess_features(self, df):
        """Extract and engineer features from raw sensor data"""
        features = []

        # Extract electrode readings
        electrode_columns = [col for col in df.columns if '_voltage' in col and any(
            electrode in col for electrode in ['SS', 'Cu', 'Zn', 'Ag', 'Pt'])]

        if not electrode_columns:
            # Alternative column naming
            electrode_columns = ['SS_voltage', 'Cu_voltage', 'Zn_voltage', 'Ag_voltage', 'Pt_voltage']
            electrode_columns = [col for col in electrode_columns if col in df.columns]

        # Basic electrode features
        for col in electrode_columns:
            features.append(col)

        # Environmental features
        env_features = ['temperature_c', 'humidity_percent', 'ph_value', 'tds_ppm', 
                       'uv_intensity', 'moisture_percent']
        for feat in env_features:
            if feat in df.columns:
                features.append(feat)

        # Color features
        color_features = ['color_r', 'color_g', 'color_b']
        for feat in color_features:
            if feat in df.columns:
                features.append(feat)

        # Create derived features
        if len(electrode_columns) >= 5:
            # Electrode ratios (important for discrimination)
            df['electrode_sum'] = df[electrode_columns].sum(axis=1)
            df['electrode_mean'] = df[electrode_columns].mean(axis=1)
            df['electrode_std'] = df[electrode_columns].std(axis=1)
            df['pt_ss_ratio'] = df[electrode_columns[4]] / (df[electrode_columns[0]] + 1e-6)
            df['ag_cu_ratio'] = df[electrode_columns[3]] / (df[electrode_columns[1]] + 1e-6)

            features.extend(['electrode_sum', 'electrode_mean', 'electrode_std', 
                           'pt_ss_ratio', 'ag_cu_ratio'])

        # Color intensity and features
        if all(feat in df.columns for feat in color_features):
            df['color_intensity'] = (df['color_r'] + df['color_g'] + df['color_b']) / 3
            df['color_dominance'] = df[color_features].max(axis=1) - df[color_features].min(axis=1)
            features.extend(['color_intensity', 'color_dominance'])

        # Environmental combinations
        if 'temperature_c' in df.columns and 'humidity_percent' in df.columns:
            df['heat_index'] = df['temperature_c'] + (df['humidity_percent'] / 100) * 10
            features.append('heat_index')

        # Filter existing features
        final_features = [feat for feat in features if feat in df.columns]
        print(f"Extracted {len(final_features)} features: {final_features}")

        return df[final_features], final_features

    def extract_features_from_readings(self, sensor_readings):
        """Extract features from raw sensor readings dictionary"""
        try:
            # Expected electrode readings
            electrodes = ['SS_voltage', 'Cu_voltage', 'Zn_voltage', 'Ag_voltage', 'Pt_voltage']
            features = []

            # Basic electrode features
            electrode_values = []
            for electrode in electrodes:
                if electrode in sensor_readings:
                    value = float(sensor_readings[electrode])
                    features.append(value)
                    electrode_values.append(value)
                else:
                    print(f"Warning: {electrode} not found in sensor readings")
                    return None

            # Environmental features
            env_features = ['temperature_c', 'humidity_percent', 'ph_value', 
                           'tds_ppm', 'uv_intensity', 'moisture_percent']
            for feat in env_features:
                if feat in sensor_readings:
                    features.append(float(sensor_readings[feat]))
                else:
                    features.append(0.0)  # Default value

            # Color features
            color_features = ['color_r', 'color_g', 'color_b']
            color_values = []
            for feat in color_features:
                if feat in sensor_readings:
                    value = float(sensor_readings[feat])
                    features.append(value)
                    color_values.append(value)
                else:
                    features.append(0.0)
                    color_values.append(0.0)

            # Derived features
            if len(electrode_values) == 5:
                features.append(sum(electrode_values))  # electrode_sum
                features.append(np.mean(electrode_values))  # electrode_mean
                features.append(np.std(electrode_values))   # electrode_std
                features.append(electrode_values[4] / max(electrode_values[0], 1e-6))  # pt_ss_ratio
                features.append(electrode_values[3] / max(electrode_values[1], 1e-6))  # ag_cu_ratio

            # Color derived features
            if len(color_values) == 3:
                features.append(np.mean(color_values))  # color_intensity
                features.append(max(color_values) - min(color_values))  # color_dominance

            # Environmental combinations
            if 'temperature_c' in sensor_readings and 'humidity_percent' in sensor_readings:
                temp = float(sensor_readings['temperature_c'])
                humidity = float(sensor_readings['humidity_percent'])
                features.append(temp + (humidity / 100) * 10)  # heat_index

            return np.array(features)

        except Exception as e:
            print(f"Error extracting features: {e}")
            return None

    def save_models(self, model_dir="models"):
        """Save trained models to disk"""
        import os
        os.makedirs(model_dir, exist_ok=True)

        # Save individual models
        if self.taste_model:
            joblib.dump(self.taste_model, f"{model_dir}/taste_model.pkl")

        if self.adulteration_model:
            joblib.dump(self.adulteration_model, f"{model_dir}/adulteration_model.pkl")

        if self.phytochemical_model:
            joblib.dump(self.phytochemical_model, f"{model_dir}/phytochemical_model.pkl")

        # Save scaler and metadata
        joblib.dump(self.scaler, f"{model_dir}/scaler.pkl")

        metadata = {
            'model_version': self.model_version,
            'training_date': str(self.training_date),
            'feature_names': self.feature_names,
            'feature_count': len(self.feature_names) if hasattr(self, 'feature_names') else 0
        }

        with open(f"{model_dir}/model_metadata.json", 'w') as f:
            import json
            json.dump(metadata, f, indent=2)

        print(f"Models saved to {model_dir}/")

    def load_models(self, model_dir="models"):
        """Load trained models from disk"""
        import os
        import json

        try:
            # Load models
            self.taste_model = joblib.load(f"{model_dir}/taste_model.pkl")
            self.adulteration_model = joblib.load(f"{model_dir}/adulteration_model.pkl")
            self.phytochemical_model = joblib.load(f"{model_dir}/phytochemical_model.pkl")
            self.scaler = joblib.load(f"{model_dir}/scaler.pkl")

            # Load metadata
            with open(f"{model_dir}/model_metadata.json", 'r') as f:
                metadata = json.load(f)
                self.model_version = metadata['model_version']
                self.training_date = metadata['training_date']
                self.feature_names = metadata['feature_names']

            print(f"Models loaded successfully from {model_dir}/")
            print(f"Model version: {self.model_version}")
            print(f"Training date: {self.training_date}")
            return True

        except Exception as e:
            print(f"Error loading models: {e}")
            return False