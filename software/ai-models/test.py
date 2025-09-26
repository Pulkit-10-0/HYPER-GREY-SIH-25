# AyuSure AI Model Testing Module
# Functions for testing and evaluating trained models

from ayu_pipeline import AyuSureAIModelPipeline
from predict import predict_sample, predict_batch_samples
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from sklearn.metrics import mean_absolute_error, accuracy_score, classification_report, confusion_matrix
import seaborn as sns

def test_model_performance(pipeline, test_data_path):
    """Test model performance on test dataset"""
    print("=" * 60)
    print("AYUSURE AI MODEL PERFORMANCE TEST")
    print("=" * 60)
    
    # Load test data
    try:
        df_test = pd.read_csv(test_data_path)
        print(f"Loaded {len(df_test)} test samples")
    except Exception as e:
        print(f"Error loading test data: {e}")
        return None
    
    # Preprocess test features
    X_test, _ = pipeline.preprocess_features(df_test)
    
    if X_test.empty:
        print("ERROR: No valid features extracted from test data")
        return None
    
    print(f"Test feature matrix shape: {X_test.shape}")
    
    results = {}
    
    # Test taste model
    if pipeline.taste_model and any(col.startswith('taste_') for col in df_test.columns):
        results['taste'] = test_taste_model(pipeline, X_test, df_test)
    
    # Test adulteration model
    if pipeline.adulteration_model and 'authenticity_score' in df_test.columns:
        results['adulteration'] = test_adulteration_model(pipeline, X_test, df_test)
    
    # Test phytochemical model
    if pipeline.phytochemical_model and any(col.startswith('phyto_') for col in df_test.columns):
        results['phytochemical'] = test_phytochemical_model(pipeline, X_test, df_test)
    
    return results

def test_taste_model(pipeline, X_test, df_test):
    """Test taste profile prediction model"""
    print("\nTesting Taste Profile Model...")
    print("-" * 40)
    
    # Extract taste labels
    taste_columns = [col for col in df_test.columns if col.startswith('taste_')]
    Y_true = df_test[taste_columns]
    
    # Scale features and predict
    X_test_scaled = pipeline.scaler.transform(X_test)
    Y_pred = pipeline.taste_model.predict(X_test_scaled)
    
    # Calculate metrics
    mae = mean_absolute_error(Y_true, Y_pred)
    print(f"Overall MAE: {mae:.3f}")
    
    # Per-taste metrics
    taste_results = {}
    for i, taste in enumerate(taste_columns):
        taste_mae = mean_absolute_error(Y_true.iloc[:, i], Y_pred[:, i])
        taste_results[taste] = {
            'mae': taste_mae,
            'true_mean': Y_true.iloc[:, i].mean(),
            'pred_mean': Y_pred[:, i].mean()
        }
        print(f"  {taste}: MAE = {taste_mae:.3f}")
    
    return {
        'overall_mae': mae,
        'taste_metrics': taste_results,
        'predictions': Y_pred,
        'ground_truth': Y_true.values
    }

def test_adulteration_model(pipeline, X_test, df_test):
    """Test adulteration detection model"""
    print("\nTesting Adulteration Detection Model...")
    print("-" * 40)
    
    # Scale features and predict
    X_test_scaled = pipeline.scaler.transform(X_test)
    predictions = pipeline.adulteration_model.predict(X_test_scaled)
    anomaly_scores = pipeline.adulteration_model.decision_function(X_test_scaled)
    
    # Convert predictions to binary
    is_authentic_pred = predictions == 1
    
    # Ground truth (threshold authenticity score)
    authenticity_threshold = 75
    is_authentic_true = df_test['authenticity_score'] >= authenticity_threshold
    
    # Calculate metrics
    accuracy = accuracy_score(is_authentic_true, is_authentic_pred)
    
    # Classification report
    class_report = classification_report(is_authentic_true, is_authentic_pred, 
                                       target_names=['Adulterated', 'Authentic'],
                                       output_dict=True)
    
    print(f"Accuracy: {accuracy:.3f}")
    print(f"Precision (Authentic): {class_report['Authentic']['precision']:.3f}")
    print(f"Recall (Authentic): {class_report['Authentic']['recall']:.3f}")
    print(f"F1-Score (Authentic): {class_report['Authentic']['f1-score']:.3f}")
    
    # Anomaly detection rate
    anomaly_rate = (~is_authentic_pred).mean()
    print(f"Anomaly Detection Rate: {anomaly_rate:.3f}")
    
    return {
        'accuracy': accuracy,
        'classification_report': class_report,
        'anomaly_rate': anomaly_rate,
        'predictions': is_authentic_pred,
        'anomaly_scores': anomaly_scores,
        'ground_truth': is_authentic_true.values
    }

def test_phytochemical_model(pipeline, X_test, df_test):
    """Test phytochemical quantification model"""
    print("\nTesting Phytochemical Model...")
    print("-" * 40)
    
    # Extract phytochemical labels
    phyto_columns = [col for col in df_test.columns if col.startswith('phyto_')]
    Y_true = df_test[phyto_columns]
    
    # Scale features and predict
    X_test_scaled = pipeline.scaler.transform(X_test)
    Y_pred = pipeline.phytochemical_model.predict(X_test_scaled)
    
    # Calculate metrics
    mae = mean_absolute_error(Y_true, Y_pred)
    print(f"Overall MAE: {mae:.3f} mg/g")
    
    # Per-compound metrics
    compound_results = {}
    for i, compound in enumerate(phyto_columns):
        compound_mae = mean_absolute_error(Y_true.iloc[:, i], Y_pred[:, i])
        compound_results[compound] = {
            'mae': compound_mae,
            'true_mean': Y_true.iloc[:, i].mean(),
            'pred_mean': Y_pred[:, i].mean()
        }
        print(f"  {compound}: MAE = {compound_mae:.3f} mg/g")
    
    return {
        'overall_mae': mae,
        'compound_metrics': compound_results,
        'predictions': Y_pred,
        'ground_truth': Y_true.values
    }

def plot_test_results(test_results, save_plots=True):
    """Generate visualization plots for test results"""
    print("\nGenerating test result visualizations...")
    
    # Taste model plots
    if 'taste' in test_results:
        plot_taste_results(test_results['taste'], save_plots)
    
    # Adulteration model plots
    if 'adulteration' in test_results:
        plot_adulteration_results(test_results['adulteration'], save_plots)
    
    # Phytochemical model plots
    if 'phytochemical' in test_results:
        plot_phytochemical_results(test_results['phytochemical'], save_plots)

def plot_taste_results(taste_results, save_plots=True):
    """Plot taste model test results"""
    fig, axes = plt.subplots(1, 2, figsize=(15, 6))
    
    # MAE by taste category
    taste_names = list(taste_results['taste_metrics'].keys())
    mae_values = [taste_results['taste_metrics'][taste]['mae'] for taste in taste_names]
    
    axes[0].bar(range(len(taste_names)), mae_values, color='skyblue')
    axes[0].set_xlabel('Taste Categories')
    axes[0].set_ylabel('Mean Absolute Error')
    axes[0].set_title('Taste Prediction MAE by Category')
    axes[0].set_xticks(range(len(taste_names)))
    axes[0].set_xticklabels([name.replace('taste_', '') for name in taste_names], rotation=45)
    
    # Predicted vs True scatter plot
    Y_true = taste_results['ground_truth'].flatten()
    Y_pred = taste_results['predictions'].flatten()
    
    axes[1].scatter(Y_true, Y_pred, alpha=0.6, color='orange')
    axes[1].plot([Y_true.min(), Y_true.max()], [Y_true.min(), Y_true.max()], 'r--', lw=2)
    axes[1].set_xlabel('True Values')
    axes[1].set_ylabel('Predicted Values')
    axes[1].set_title('Taste Prediction: True vs Predicted')
    
    plt.tight_layout()
    if save_plots:
        plt.savefig('taste_model_test_results.png', dpi=300, bbox_inches='tight')
    plt.show()

def plot_adulteration_results(adulteration_results, save_plots=True):
    """Plot adulteration detection test results"""
    fig, axes = plt.subplots(1, 2, figsize=(15, 6))
    
    # Confusion matrix
    cm = confusion_matrix(adulteration_results['ground_truth'], 
                         adulteration_results['predictions'])
    
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues', 
                xticklabels=['Adulterated', 'Authentic'],
                yticklabels=['Adulterated', 'Authentic'], ax=axes[0])
    axes[0].set_title('Adulteration Detection Confusion Matrix')
    axes[0].set_ylabel('True Label')
    axes[0].set_xlabel('Predicted Label')
    
    # Anomaly scores distribution
    authentic_scores = adulteration_results['anomaly_scores'][adulteration_results['ground_truth']]
    adulterated_scores = adulteration_results['anomaly_scores'][~adulteration_results['ground_truth']]
    
    axes[1].hist(authentic_scores, bins=30, alpha=0.7, label='Authentic', color='green')
    axes[1].hist(adulterated_scores, bins=30, alpha=0.7, label='Adulterated', color='red')
    axes[1].set_xlabel('Anomaly Score')
    axes[1].set_ylabel('Frequency')
    axes[1].set_title('Anomaly Score Distribution')
    axes[1].legend()
    
    plt.tight_layout()
    if save_plots:
        plt.savefig('adulteration_model_test_results.png', dpi=300, bbox_inches='tight')
    plt.show()

def plot_phytochemical_results(phyto_results, save_plots=True):
    """Plot phytochemical model test results"""
    fig, axes = plt.subplots(1, 2, figsize=(15, 6))
    
    # MAE by compound
    compound_names = list(phyto_results['compound_metrics'].keys())
    mae_values = [phyto_results['compound_metrics'][compound]['mae'] for compound in compound_names]
    
    axes[0].bar(range(len(compound_names)), mae_values, color='lightcoral')
    axes[0].set_xlabel('Phytochemical Compounds')
    axes[0].set_ylabel('Mean Absolute Error (mg/g)')
    axes[0].set_title('Phytochemical Prediction MAE by Compound')
    axes[0].set_xticks(range(len(compound_names)))
    axes[0].set_xticklabels([name.replace('phyto_', '') for name in compound_names], rotation=45)
    
    # Predicted vs True scatter plot
    Y_true = phyto_results['ground_truth'].flatten()
    Y_pred = phyto_results['predictions'].flatten()
    
    axes[1].scatter(Y_true, Y_pred, alpha=0.6, color='purple')
    axes[1].plot([Y_true.min(), Y_true.max()], [Y_true.min(), Y_true.max()], 'r--', lw=2)
    axes[1].set_xlabel('True Concentration (mg/g)')
    axes[1].set_ylabel('Predicted Concentration (mg/g)')
    axes[1].set_title('Phytochemical Prediction: True vs Predicted')
    
    plt.tight_layout()
    if save_plots:
        plt.savefig('phytochemical_model_test_results.png', dpi=300, bbox_inches='tight')
    plt.show()

def run_comprehensive_test(model_dir, test_data_path, save_results=True):
    """Run comprehensive model testing"""
    print("Running comprehensive AyuSure AI model test...")
    
    # Load pipeline and models
    pipeline = AyuSureAIModelPipeline()
    if not pipeline.load_models(model_dir):
        print("Failed to load models")
        return None
    
    # Run performance tests
    test_results = test_model_performance(pipeline, test_data_path)
    
    if test_results:
        # Generate plots
        plot_test_results(test_results, save_plots=True)
        
        # Save results to file
        if save_results:
            save_test_results(test_results, 'model_test_results.json')
        
        # Print summary
        print_test_summary(test_results)
    
    return test_results

def save_test_results(test_results, output_file):
    """Save test results to JSON file"""
    import json
    
    # Convert numpy arrays to lists for JSON serialization
    serializable_results = {}
    for model_type, results in test_results.items():
        serializable_results[model_type] = {}
        for key, value in results.items():
            if isinstance(value, np.ndarray):
                serializable_results[model_type][key] = value.tolist()
            else:
                serializable_results[model_type][key] = value
    
    try:
        with open(output_file, 'w') as f:
            json.dump(serializable_results, f, indent=2, default=str)
        print(f"Test results saved to {output_file}")
    except Exception as e:
        print(f"Error saving test results: {e}")

def print_test_summary(test_results):
    """Print comprehensive test summary"""
    print("\n" + "=" * 60)
    print("AYUSURE AI MODEL TEST SUMMARY")
    print("=" * 60)
    
    if 'taste' in test_results:
        print(f"\n🌿 TASTE MODEL:")
        print(f"   Overall MAE: {test_results['taste']['overall_mae']:.3f}")
        
    if 'adulteration' in test_results:
        print(f"\n🔍 ADULTERATION MODEL:")
        print(f"   Accuracy: {test_results['adulteration']['accuracy']:.3f}")
        print(f"   Anomaly Rate: {test_results['adulteration']['anomaly_rate']:.3f}")
        
    if 'phytochemical' in test_results:
        print(f"\n🧪 PHYTOCHEMICAL MODEL:")
        print(f"   Overall MAE: {test_results['phytochemical']['overall_mae']:.3f} mg/g")
    
    print("\n" + "=" * 60)

# Example usage
if __name__ == "__main__":
    print("AyuSure AI Model Testing Module")
    print("=" * 40)

    # Example: Run comprehensive test (uncomment when models and data are available)
    # test_results = run_comprehensive_test("models", "test_sensor_data.csv")

    print("Testing module ready. Import and use testing functions.")