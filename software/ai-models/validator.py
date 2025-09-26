# AyuSure AI Model Validation Module
# Model validation and cross-validation functions

from ayu_pipeline import AyuSureAIModelPipeline
from sklearn.model_selection import cross_val_score, cross_validate
import pandas as pd
import numpy as np

class ModelValidator:
    def __init__(self, model_pipeline):
        self.pipeline = model_pipeline

    def cross_validate_models(self, X, df, cv_folds=5):
        """Perform cross-validation on all models"""
        print("Performing cross-validation...")

        results = {}

        # Taste model validation
        if 'taste_sweet' in df.columns:
            taste_columns = [col for col in df.columns if col.startswith('taste_')]
            Y_taste = df[taste_columns]

            taste_scores = cross_val_score(
                self.pipeline.taste_model, 
                self.pipeline.scaler.transform(X), 
                Y_taste, 
                cv=cv_folds, 
                scoring='neg_mean_absolute_error'
            )
            results['taste_cv_mae'] = -taste_scores.mean()
            results['taste_cv_std'] = taste_scores.std()

        # Phytochemical model validation
        if 'phyto_alkaloids' in df.columns:
            phyto_columns = [col for col in df.columns if col.startswith('phyto_')]
            Y_phyto = df[phyto_columns]

            phyto_scores = cross_val_score(
                self.pipeline.phytochemical_model,
                self.pipeline.scaler.transform(X),
                Y_phyto,
                cv=cv_folds,
                scoring='neg_mean_absolute_error'
            )
            results['phyto_cv_mae'] = -phyto_scores.mean()
            results['phyto_cv_std'] = phyto_scores.std()

        print("Cross-validation results:")
        for key, value in results.items():
            print(f"  {key}: {value:.4f}")

        return results

    def validate_taste_model(self, X, df, cv_folds=5):
        """Detailed validation for taste profile model"""
        print("\nValidating Taste Profile Model...")
        print("-" * 40)
        
        taste_columns = [col for col in df.columns if col.startswith('taste_')]
        if not taste_columns:
            print("No taste columns found for validation")
            return None
            
        Y_taste = df[taste_columns]
        X_scaled = self.pipeline.scaler.transform(X)
        
        # Cross-validation with multiple metrics
        scoring = ['neg_mean_absolute_error', 'neg_mean_squared_error', 'r2']
        cv_results = cross_validate(
            self.pipeline.taste_model, X_scaled, Y_taste, 
            cv=cv_folds, scoring=scoring, return_train_score=True
        )
        
        results = {}
        for metric in scoring:
            test_scores = cv_results[f'test_{metric}']
            train_scores = cv_results[f'train_{metric}']
            
            if metric.startswith('neg_'):
                test_scores = -test_scores
                train_scores = -train_scores
                metric_name = metric[4:]  # Remove 'neg_' prefix
            else:
                metric_name = metric
                
            results[f'test_{metric_name}'] = {
                'mean': test_scores.mean(),
                'std': test_scores.std(),
                'scores': test_scores
            }
            results[f'train_{metric_name}'] = {
                'mean': train_scores.mean(),
                'std': train_scores.std(),
                'scores': train_scores
            }
            
            print(f"{metric_name.upper()}:")
            print(f"  Test:  {test_scores.mean():.4f} ± {test_scores.std():.4f}")
            print(f"  Train: {train_scores.mean():.4f} ± {train_scores.std():.4f}")
        
        # Per-taste validation
        taste_results = {}
        for i, taste in enumerate(taste_columns):
            taste_target = Y_taste.iloc[:, i]
            taste_cv_scores = cross_val_score(
                self.pipeline.taste_model, X_scaled, taste_target,
                cv=cv_folds, scoring='neg_mean_absolute_error'
            )
            
            taste_results[taste] = {
                'cv_mae': -taste_cv_scores.mean(),
                'cv_std': taste_cv_scores.std()
            }
            print(f"{taste}: MAE = {-taste_cv_scores.mean():.4f} ± {taste_cv_scores.std():.4f}")
        
        results['per_taste'] = taste_results
        return results

    def validate_adulteration_model(self, X, df, cv_folds=5):
        """Detailed validation for adulteration detection model"""
        print("\nValidating Adulteration Detection Model...")
        print("-" * 40)
        
        if 'authenticity_score' not in df.columns:
            print("No authenticity score found for validation")
            return None
        
        # Create binary labels for validation
        authenticity_threshold = 75
        y_binary = (df['authenticity_score'] >= authenticity_threshold).astype(int)
        
        X_scaled = self.pipeline.scaler.transform(X)
        
        # For isolation forest, we need to use decision_function for scoring
        from sklearn.model_selection import StratifiedKFold
        
        skf = StratifiedKFold(n_splits=cv_folds, shuffle=True, random_state=42)
        
        scores = {
            'accuracy': [],
            'precision': [],
            'recall': [],
            'f1': []
        }
        
        for train_idx, val_idx in skf.split(X_scaled, y_binary):
            X_train_fold, X_val_fold = X_scaled[train_idx], X_scaled[val_idx]
            y_train_fold, y_val_fold = y_binary.iloc[train_idx], y_binary.iloc[val_idx]
            
            # Use only authentic samples for training (as in original approach)
            authentic_mask = y_train_fold == 1
            if authentic_mask.sum() > 0:
                X_authentic = X_train_fold[authentic_mask]
                
                # Create temporary isolation forest for this fold
                from sklearn.ensemble import IsolationForest
                temp_model = IsolationForest(
                    n_estimators=200, contamination=0.15, random_state=42
                )
                temp_model.fit(X_authentic)
                
                # Predict on validation set
                val_predictions = temp_model.predict(X_val_fold)
                val_binary = (val_predictions == 1).astype(int)
                
                # Calculate metrics
                from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score
                scores['accuracy'].append(accuracy_score(y_val_fold, val_binary))
                scores['precision'].append(precision_score(y_val_fold, val_binary, zero_division=0))
                scores['recall'].append(recall_score(y_val_fold, val_binary, zero_division=0))
                scores['f1'].append(f1_score(y_val_fold, val_binary, zero_division=0))
        
        # Calculate mean and std for each metric
        results = {}
        for metric, score_list in scores.items():
            if score_list:  # Only if we have scores
                results[metric] = {
                    'mean': np.mean(score_list),
                    'std': np.std(score_list),
                    'scores': score_list
                }
                print(f"{metric.upper()}: {np.mean(score_list):.4f} ± {np.std(score_list):.4f}")
        
        return results

    def validate_phytochemical_model(self, X, df, cv_folds=5):
        """Detailed validation for phytochemical quantification model"""
        print("\nValidating Phytochemical Model...")
        print("-" * 40)
        
        phyto_columns = [col for col in df.columns if col.startswith('phyto_')]
        if not phyto_columns:
            print("No phytochemical columns found for validation")
            return None
            
        Y_phyto = df[phyto_columns]
        X_scaled = self.pipeline.scaler.transform(X)
        
        # Cross-validation with multiple metrics
        scoring = ['neg_mean_absolute_error', 'neg_mean_squared_error', 'r2']
        cv_results = cross_validate(
            self.pipeline.phytochemical_model, X_scaled, Y_phyto, 
            cv=cv_folds, scoring=scoring, return_train_score=True
        )
        
        results = {}
        for metric in scoring:
            test_scores = cv_results[f'test_{metric}']
            train_scores = cv_results[f'train_{metric}']
            
            if metric.startswith('neg_'):
                test_scores = -test_scores
                train_scores = -train_scores
                metric_name = metric[4:]  # Remove 'neg_' prefix
            else:
                metric_name = metric
                
            results[f'test_{metric_name}'] = {
                'mean': test_scores.mean(),
                'std': test_scores.std(),
                'scores': test_scores
            }
            results[f'train_{metric_name}'] = {
                'mean': train_scores.mean(),
                'std': train_scores.std(),
                'scores': train_scores
            }
            
            print(f"{metric_name.upper()}:")
            print(f"  Test:  {test_scores.mean():.4f} ± {test_scores.std():.4f}")
            print(f"  Train: {train_scores.mean():.4f} ± {train_scores.std():.4f}")
        
        # Per-compound validation
        compound_results = {}
        for i, compound in enumerate(phyto_columns):
            compound_target = Y_phyto.iloc[:, i]
            compound_cv_scores = cross_val_score(
                self.pipeline.phytochemical_model, X_scaled, compound_target,
                cv=cv_folds, scoring='neg_mean_absolute_error'
            )
            
            compound_results[compound] = {
                'cv_mae': -compound_cv_scores.mean(),
                'cv_std': compound_cv_scores.std()
            }
            print(f"{compound}: MAE = {-compound_cv_scores.mean():.4f} ± {compound_cv_scores.std():.4f}")
        
        results['per_compound'] = compound_results
        return results

    def comprehensive_validation(self, data_path, cv_folds=5):
        """Run comprehensive validation on all models"""
        print("=" * 60)
        print("AYUSURE AI COMPREHENSIVE MODEL VALIDATION")
        print("=" * 60)
        
        # Load validation data
        try:
            df = pd.read_csv(data_path)
            print(f"Loaded {len(df)} validation samples")
        except Exception as e:
            print(f"Error loading validation data: {e}")
            return None
        
        # Preprocess features
        X, _ = self.pipeline.preprocess_features(df)
        
        if X.empty:
            print("ERROR: No valid features extracted from validation data")
            return None
        
        validation_results = {}
        
        # Validate each model
        if self.pipeline.taste_model:
            validation_results['taste'] = self.validate_taste_model(X, df, cv_folds)
        
        if self.pipeline.adulteration_model:
            validation_results['adulteration'] = self.validate_adulteration_model(X, df, cv_folds)
        
        if self.pipeline.phytochemical_model:
            validation_results['phytochemical'] = self.validate_phytochemical_model(X, df, cv_folds)
        
        # Overall cross-validation
        overall_results = self.cross_validate_models(X, df, cv_folds)
        validation_results['overall'] = overall_results
        
        return validation_results

def validate_model_stability(pipeline, data_path, n_runs=10):
    """Test model stability across multiple runs"""
    print("\nTesting Model Stability...")
    print("-" * 40)
    
    df = pd.read_csv(data_path)
    X, _ = pipeline.preprocess_features(df)
    
    stability_results = {
        'taste': {'mae_runs': []},
        'phytochemical': {'mae_runs': []},
        'adulteration': {'accuracy_runs': []}
    }
    
    for run in range(n_runs):
        print(f"Stability run {run + 1}/{n_runs}")
        
        # Taste model stability
        if pipeline.taste_model and any(col.startswith('taste_') for col in df.columns):
            taste_columns = [col for col in df.columns if col.startswith('taste_')]
            Y_taste = df[taste_columns]
            X_scaled = pipeline.scaler.transform(X)
            
            taste_cv_scores = cross_val_score(
                pipeline.taste_model, X_scaled, Y_taste,
                cv=5, scoring='neg_mean_absolute_error'
            )
            stability_results['taste']['mae_runs'].append(-taste_cv_scores.mean())
        
        # Phytochemical model stability  
        if pipeline.phytochemical_model and any(col.startswith('phyto_') for col in df.columns):
            phyto_columns = [col for col in df.columns if col.startswith('phyto_')]
            Y_phyto = df[phyto_columns]
            X_scaled = pipeline.scaler.transform(X)
            
            phyto_cv_scores = cross_val_score(
                pipeline.phytochemical_model, X_scaled, Y_phyto,
                cv=5, scoring='neg_mean_absolute_error'
            )
            stability_results['phytochemical']['mae_runs'].append(-phyto_cv_scores.mean())
    
    # Calculate stability metrics
    for model_type in stability_results:
        for metric, runs in stability_results[model_type].items():
            if runs:
                mean_score = np.mean(runs)
                std_score = np.std(runs)
                cv_score = std_score / mean_score if mean_score != 0 else 0
                
                print(f"{model_type.capitalize()} {metric}: {mean_score:.4f} ± {std_score:.4f} (CV: {cv_score:.4f})")
                
                stability_results[model_type][f'{metric}_mean'] = mean_score
                stability_results[model_type][f'{metric}_std'] = std_score
                stability_results[model_type][f'{metric}_cv'] = cv_score
    
    return stability_results

def run_full_validation(model_dir, validation_data_path, cv_folds=5, stability_runs=5):
    """Run complete model validation suite"""
    print("Running full AyuSure AI model validation...")
    
    # Load pipeline and models
    pipeline = AyuSureAIModelPipeline()
    if not pipeline.load_models(model_dir):
        print("Failed to load models")
        return None
    
    # Initialize validator
    validator = ModelValidator(pipeline)
    
    # Run comprehensive validation
    validation_results = validator.comprehensive_validation(validation_data_path, cv_folds)
    
    # Run stability testing
    stability_results = validate_model_stability(pipeline, validation_data_path, stability_runs)
    
    # Combine results
    full_results = {
        'validation': validation_results,
        'stability': stability_results
    }
    
    # Save results
    save_validation_results(full_results, 'model_validation_results.json')
    
    return full_results

def save_validation_results(validation_results, output_file):
    """Save validation results to JSON file"""
    import json
    
    # Convert numpy arrays to lists for JSON serialization
    def convert_numpy(obj):
        if isinstance(obj, np.ndarray):
            return obj.tolist()
        elif isinstance(obj, dict):
            return {key: convert_numpy(value) for key, value in obj.items()}
        elif isinstance(obj, list):
            return [convert_numpy(item) for item in obj]
        else:
            return obj
    
    serializable_results = convert_numpy(validation_results)
    
    try:
        with open(output_file, 'w') as f:
            json.dump(serializable_results, f, indent=2, default=str)
        print(f"Validation results saved to {output_file}")
    except Exception as e:
        print(f"Error saving validation results: {e}")

# Example usage
if __name__ == "__main__":
    print("AyuSure AI Model Validation Module")
    print("=" * 40)

    # Example: Run full validation (uncomment when models and data are available)
    # validation_results = run_full_validation("models", "validation_sensor_data.csv")

    print("Validation module ready. Import and use validation functions.")