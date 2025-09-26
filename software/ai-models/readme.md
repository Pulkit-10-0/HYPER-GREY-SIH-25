# AyuSure AI Model Pipeline

An advanced machine learning pipeline for herb authentication using electronic tongue analysis. This system provides comprehensive taste profile analysis, adulteration detection, and phytochemical quantification for Ayurvedic herbs and botanical samples.

## Overview

The AyuSure AI pipeline consists of three specialized models:
- **Taste Profile Model**: Neural network-based taste prediction (sweet, sour, salty, pungent, bitter, astringent)
- **Adulteration Detection Model**: Isolation Forest-based anomaly detection for authenticity verification
- **Phytochemical Analysis Model**: Random Forest-based quantification of bioactive compounds

## File Structure

```
├── ayu_pipeline.py       # Core pipeline class and feature processing
├── train_models.py       # Model training functions
├── predict.py           # Prediction and inference functions
├── test_models.py       # Model testing and evaluation
├── validate_models.py   # Cross-validation and model validation
└── README.md           # This documentation
```

## Installation

### Prerequisites
```bash
pip install pandas numpy scikit-learn joblib matplotlib seaborn
```

### Required Python Packages
- `pandas` - Data manipulation and analysis
- `numpy` - Numerical computing
- `scikit-learn` - Machine learning algorithms
- `joblib` - Model serialization
- `matplotlib` - Plotting and visualization
- `seaborn` - Statistical data visualization

## File Descriptions

### `ayu_pipeline.py`
**Core pipeline class containing:**
- `AyuSureAIModelPipeline`: Main pipeline class
- Data loading and preprocessing functions
- Feature extraction and engineering
- Model saving/loading capabilities
- Feature extraction from raw sensor readings

**Key Methods:**
- `load_training_data()` - Load and combine training datasets
- `preprocess_features()` - Extract and engineer features from sensor data
- `extract_features_from_readings()` - Convert raw sensor readings to feature vectors
- `save_models()` / `load_models()` - Model persistence

### `train_models.py`
**Model training module containing:**
- Individual model training functions for each component
- Training pipeline orchestration
- Model hyperparameter configuration

**Key Functions:**
- `train_taste_profile_model()` - Train neural network for taste prediction
- `train_adulteration_detection_model()` - Train isolation forest for authenticity
- `train_phytochemical_model()` - Train random forest for compound quantification
- `train_all_models()` - Complete training pipeline

### `predict.py`
**Inference and prediction module containing:**
- Single sample prediction functions
- Batch prediction capabilities
- Result formatting and visualization

**Key Functions:**
- `predict_sample()` - Make predictions on single sensor reading
- `predict_batch_samples()` - Process multiple samples
- `load_and_predict()` - Load models and predict in one step
- `predict_from_csv()` - Process CSV files containing sensor data
- `format_prediction_output()` - Format results for display

### `test_models.py`
**Model testing and evaluation module containing:**
- Performance testing on test datasets
- Model accuracy assessment
- Visualization of test results

**Key Functions:**
- `test_model_performance()` - Comprehensive model testing
- `test_taste_model()` - Taste model specific testing
- `test_adulteration_model()` - Adulteration detection testing
- `test_phytochemical_model()` - Phytochemical model testing
- `plot_test_results()` - Generate test visualization plots
- `run_comprehensive_test()` - Complete testing pipeline

### `validate_models.py`
**Model validation and cross-validation module containing:**
- Cross-validation procedures
- Model stability testing
- Validation result analysis

**Key Functions:**
- `cross_validate_models()` - Perform k-fold cross-validation
- `validate_taste_model()` - Detailed taste model validation
- `validate_adulteration_model()` - Adulteration model validation
- `validate_phytochemical_model()` - Phytochemical model validation
- `validate_model_stability()` - Test model consistency across runs
- `run_full_validation()` - Complete validation suite

## Usage Examples

### Basic Training
```python
from train_models import train_all_models

# Train all models
pipeline = train_all_models(
    processed_data_path="processed_sensor_data.csv",
    synthetic_data_path="synthetic_herb_data.csv",
    model_save_dir="models"
)
```

### Making Predictions
```python
from predict import load_and_predict, format_prediction_output

# Single sample prediction
sensor_readings = {
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

result = load_and_predict("models", sensor_readings)
print(format_prediction_output(result))
```

### Batch Processing
```python
from ayu_pipeline import AyuSureAIModelPipeline
from predict import predict_from_csv

# Load pipeline
pipeline = AyuSureAIModelPipeline()
pipeline.load_models("models")

# Process CSV file
results = predict_from_csv(pipeline, "new_sensor_data.csv")
```

### Model Testing
```python
from test_models import run_comprehensive_test

# Run complete model testing
test_results = run_comprehensive_test(
    model_dir="models",
    test_data_path="test_sensor_data.csv"
)
```

### Model Validation
```python
from validate_models import run_full_validation

# Run complete validation suite
validation_results = run_full_validation(
    model_dir="models",
    validation_data_path="validation_sensor_data.csv",
    cv_folds=5,
    stability_runs=10
)
```

## Data Format Requirements

### Input Sensor Data CSV Format
Your CSV files should contain the following columns:

**Electrode Readings:**
- `SS_voltage`, `Cu_voltage`, `Zn_voltage`, `Ag_voltage`, `Pt_voltage`

**Environmental Data:**
- `temperature_c`, `humidity_percent`, `ph_value`, `tds_ppm`, `uv_intensity`, `moisture_percent`

**Color Analysis:**
- `color_r`, `color_g`, `color_b`

**Ground Truth Labels (for training):**
- Taste: `taste_sweet`, `taste_sour`, `taste_salty`, `taste_pungent`, `taste_bitter`, `taste_astringent`
- Authenticity: `authenticity_score` (0-100)
- Phytochemicals: `phyto_alkaloids`, `phyto_flavonoids`, `phyto_saponins`, `phyto_tannins`, `phyto_glycosides`

## Model Architecture

### Taste Profile Model
- **Type**: Multi-layer Perceptron (MLP) Regressor
- **Architecture**: 128-64-32 hidden layers
- **Activation**: ReLU
- **Output**: 6 taste intensities (0-1 scale)

### Adulteration Detection Model
- **Type**: Isolation Forest
- **Estimators**: 200 trees
- **Contamination**: 15% expected
- **Output**: Binary authenticity + confidence score

### Phytochemical Quantification Model
- **Type**: Random Forest Regressor
- **Estimators**: 200 trees
- **Max Depth**: 15
- **Output**: Compound concentrations (mg/g)

## Performance Metrics

The pipeline tracks multiple performance metrics:
- **Mean Absolute Error (MAE)** for regression tasks
- **Accuracy, Precision, Recall, F1-Score** for classification
- **Cross-validation scores** for model stability
- **Feature importance** analysis

## Configuration

Models use optimized hyperparameters but can be customized by modifying the training functions in `train_models.py`. Key parameters include:

- Learning rates and batch sizes for neural networks
- Tree counts and depths for ensemble methods
- Cross-validation fold counts
- Feature scaling and preprocessing options

## 📝 Output Formats

### Prediction Results
```json
{
  "taste_profile": {
    "sweet": 0.65,
    "sour": 0.23,
    "salty": 0.12,
    "pungent": 0.45,
    "bitter": 0.78,
    "astringent": 0.34
  },
  "authenticity": {
    "is_authentic": true,
    "confidence_score": 0.82,
    "authenticity_percentage": 87.5
  },
  "phytochemicals": {
    "alkaloids": 12.5,
    "flavonoids": 8.3,
    "saponins": 5.7,
    "tannins": 15.2,
    "glycosides": 9.8
  }
}
```

## Error Handling

The pipeline includes comprehensive error handling for:
- Missing sensor readings (with default values)
- Invalid data formats
- Model loading failures
- Feature extraction errors
- File I/O issues

## Visualization

Test and validation modules generate visualization plots:
- Performance comparison charts
- Confusion matrices for classification
- Prediction vs. ground truth scatter plots
- Feature importance rankings

## Model Versioning

The pipeline includes model versioning with metadata tracking:
- Model version numbers
- Training timestamps
- Feature counts and names
- Performance metrics

## Model Persistence

Models are saved using joblib serialization with metadata in JSON format for easy loading and deployment.

