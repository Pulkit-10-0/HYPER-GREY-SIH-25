# AyuSure AI Prediction Module
# Functions for making predictions on herb samples

from ayu_pipeline import AyuSureAIModelPipeline
import numpy as np
import json

def predict_sample(pipeline, sensor_readings):
    """Make predictions on a single sample"""
    if not all([pipeline.taste_model, pipeline.adulteration_model, pipeline.phytochemical_model]):
        print("Error: Models not trained yet")
        return None

    # Convert sensor readings to feature vector
    feature_vector = pipeline.extract_features_from_readings(sensor_readings)

    if feature_vector is None:
        return None

    # Scale features
    feature_scaled = pipeline.scaler.transform([feature_vector])

    # Make predictions
    results = {}

    # Taste prediction
    taste_pred = pipeline.taste_model.predict(feature_scaled)[0]
    taste_names = ['sweet', 'sour', 'salty', 'pungent', 'bitter', 'astringent']
    results['taste_profile'] = dict(zip(taste_names, taste_pred))

    # Adulteration detection
    authenticity_pred = pipeline.adulteration_model.predict(feature_scaled)[0]
    authenticity_score = pipeline.adulteration_model.decision_function(feature_scaled)[0]
    results['authenticity'] = {
        'is_authentic': authenticity_pred == 1,
        'confidence_score': float(authenticity_score),
        'authenticity_percentage': max(0, min(100, (authenticity_score + 0.5) * 100))
    }

    # Phytochemical prediction
    phyto_pred = pipeline.phytochemical_model.predict(feature_scaled)[0]
    phyto_names = ['alkaloids', 'flavonoids', 'saponins', 'tannins', 'glycosides']
    results['phytochemicals'] = dict(zip(phyto_names, phyto_pred))

    return results

def predict_batch_samples(pipeline, sensor_readings_list):
    """Make predictions on multiple samples"""
    if not all([pipeline.taste_model, pipeline.adulteration_model, pipeline.phytochemical_model]):
        print("Error: Models not trained yet")
        return None

    results_list = []
    
    for i, sensor_readings in enumerate(sensor_readings_list):
        print(f"Processing sample {i+1}/{len(sensor_readings_list)}")
        
        # Make prediction for this sample
        result = predict_sample(pipeline, sensor_readings)
        
        if result:
            result['sample_id'] = i
            results_list.append(result)
        else:
            print(f"Failed to process sample {i+1}")
    
    return results_list

def load_and_predict(model_dir, sensor_readings):
    """Load models and make prediction in one function"""
    # Initialize pipeline
    pipeline = AyuSureAIModelPipeline()
    
    # Load trained models
    if not pipeline.load_models(model_dir):
        print("Failed to load models")
        return None
    
    # Make prediction
    return predict_sample(pipeline, sensor_readings)

def predict_from_csv(pipeline, csv_file_path):
    """Make predictions from CSV file containing sensor readings"""
    import pandas as pd
    
    try:
        # Load sensor data from CSV
        df = pd.read_csv(csv_file_path)
        print(f"Loaded {len(df)} samples from {csv_file_path}")
        
        results = []
        
        for idx, row in df.iterrows():
            # Convert row to sensor readings dictionary
            sensor_readings = row.to_dict()
            
            # Make prediction
            result = predict_sample(pipeline, sensor_readings)
            
            if result:
                result['sample_index'] = idx
                results.append(result)
            else:
                print(f"Failed to process sample at index {idx}")
        
        return results
        
    except Exception as e:
        print(f"Error processing CSV file: {e}")
        return None

def save_predictions_to_json(predictions, output_file):
    """Save prediction results to JSON file"""
    try:
        with open(output_file, 'w') as f:
            json.dump(predictions, f, indent=2, default=str)
        print(f"Predictions saved to {output_file}")
        return True
    except Exception as e:
        print(f"Error saving predictions: {e}")
        return False

def format_prediction_output(results):
    """Format prediction results for display"""
    if not results:
        return "No prediction results available"
    
    output = []
    output.append("=" * 60)
    output.append("AYUSURE HERB AUTHENTICATION RESULTS")
    output.append("=" * 60)
    
    # Taste Profile
    output.append("\n🌿 TASTE PROFILE ANALYSIS:")
    output.append("-" * 30)
    for taste, value in results['taste_profile'].items():
        intensity = "●" * int(value * 5)  # Scale to 0-5
        output.append(f"{taste.capitalize():>12}: {value:.3f} {intensity}")
    
    # Authenticity
    output.append("\n🔍 AUTHENTICITY ANALYSIS:")
    output.append("-" * 30)
    auth = results['authenticity']
    status = "AUTHENTIC" if auth['is_authentic'] else "POTENTIALLY ADULTERATED"
    output.append(f"Status: {status}")
    output.append(f"Authenticity Score: {auth['authenticity_percentage']:.1f}%")
    output.append(f"Confidence: {auth['confidence_score']:.3f}")
    
    # Phytochemicals
    output.append("\n🧪 PHYTOCHEMICAL ANALYSIS:")
    output.append("-" * 30)
    for compound, concentration in results['phytochemicals'].items():
        output.append(f"{compound.capitalize():>12}: {concentration:.2f} mg/g")
    
    output.append("\n" + "=" * 60)
    
    return "\n".join(output)

# Example usage and testing functions
def test_prediction():
    """Test prediction with sample data"""
    # Example sensor readings for prediction
    sample_readings = {
        'SS_voltage': 1.420,
        'Cu_voltage': 1.680,
        'Zn_voltage': 1.890,
        'Ag_voltage': 2.210,
        'Pt_voltage': 2.030,
        'temperature_c': 25.0,
        'humidity_percent': 60.0,
        'ph_value': 6.8,
        'tds_ppm': 450,
        'uv_intensity': 2.5,
        'moisture_percent': 15.0,
        'color_r': 120,
        'color_g': 180,
        'color_b': 90
    }
    
    # Load models and make prediction
    result = load_and_predict("models", sample_readings)
    
    if result:
        print(format_prediction_output(result))
    else:
        print("Failed to make prediction")

# Example usage
if __name__ == "__main__":
    print("AyuSure AI Prediction Module")
    print("=" * 40)

    # Example: Test prediction (uncomment when models are available)
    # test_prediction()

    print("Prediction module ready. Import and use prediction functions.")