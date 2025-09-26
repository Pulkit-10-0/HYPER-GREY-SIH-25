# AyuSure AI Model Training Module
# Training functions for taste profile, adulteration detection, and phytochemical analysis

from ayu_pipeline import AyuSureAIModelPipeline
from sklearn.model_selection import train_test_split
from sklearn.neural_network import MLPRegressor
from sklearn.ensemble import RandomForestRegressor, IsolationForest
from sklearn.metrics import mean_absolute_error, accuracy_score
import numpy as np
from datetime import datetime

def train_taste_profile_model(pipeline, X, df):
    """Train neural network for taste profile prediction"""
    print("\nTraining taste profile prediction model...")

    # Extract taste labels
    taste_columns = [col for col in df.columns if col.startswith('taste_')]
    if not taste_columns:
        print("No taste profile columns found in data")
        return None

    Y_taste = df[taste_columns]
    print(f"Taste categories: {taste_columns}")

    # Split data
    X_train, X_test, Y_train, Y_test = train_test_split(
        X, Y_taste, test_size=0.2, random_state=42)

    # Scale features
    X_train_scaled = pipeline.scaler.fit_transform(X_train)
    X_test_scaled = pipeline.scaler.transform(X_test)

    # Neural network architecture optimized for taste prediction
    taste_model = MLPRegressor(
        hidden_layer_sizes=(128, 64, 32),
        activation='relu',
        solver='adam',
        alpha=0.001,
        batch_size=32,
        learning_rate='adaptive',
        learning_rate_init=0.001,
        max_iter=500,
        early_stopping=True,
        validation_fraction=0.1,
        n_iter_no_change=20,
        random_state=42
    )

    # Train model
    taste_model.fit(X_train_scaled, Y_train)

    # Evaluate model
    Y_pred = taste_model.predict(X_test_scaled)
    mae = mean_absolute_error(Y_test, Y_pred)

    print(f"Taste prediction MAE: {mae:.3f}")

    # Calculate per-taste accuracy
    for i, taste in enumerate(taste_columns):
        taste_mae = mean_absolute_error(Y_test.iloc[:, i], Y_pred[:, i])
        print(f"  {taste}: MAE = {taste_mae:.3f}")

    pipeline.taste_model = taste_model
    return taste_model

def train_adulteration_detection_model(pipeline, X, df):
    """Train isolation forest for adulteration detection"""
    print("\nTraining adulteration detection model...")

    # For unsupervised learning, use only authentic samples if labeled
    if 'authenticity_score' in df.columns:
        # Use samples with high authenticity scores as training data
        authentic_mask = df['authenticity_score'] >= 85
        X_authentic = X[authentic_mask]
        print(f"Training on {len(X_authentic)} authentic samples")
    else:
        X_authentic = X
        print(f"Training on all {len(X_authentic)} samples (unsupervised)")

    # Scale features
    X_scaled = pipeline.scaler.fit_transform(X_authentic)

    # Isolation Forest for anomaly detection
    adulteration_model = IsolationForest(
        n_estimators=200,
        contamination=0.15,  # Expected contamination rate
        max_samples='auto',
        max_features=1.0,
        bootstrap=False,
        random_state=42,
        verbose=0
    )

    # Train model
    adulteration_model.fit(X_scaled)

    # Evaluate on full dataset if labels available
    if 'authenticity_score' in df.columns:
        X_full_scaled = pipeline.scaler.transform(X)
        predictions = adulteration_model.predict(X_full_scaled)
        anomaly_scores = adulteration_model.decision_function(X_full_scaled)

        # Convert predictions (-1 for anomaly, 1 for normal) to binary
        is_authentic = predictions == 1

        # Compare with ground truth
        ground_truth = df['authenticity_score'] >= 75  # Threshold for authenticity
        accuracy = accuracy_score(ground_truth, is_authentic)

        print(f"Adulteration detection accuracy: {accuracy:.3f}")
        print(f"Anomaly detection rate: {(~is_authentic).mean():.3f}")

    pipeline.adulteration_model = adulteration_model
    return adulteration_model

def train_phytochemical_model(pipeline, X, df):
    """Train random forest for phytochemical quantification"""
    print("\nTraining phytochemical quantification model...")

    # Extract phytochemical labels
    phyto_columns = [col for col in df.columns if col.startswith('phyto_')]
    if not phyto_columns:
        print("No phytochemical columns found in data")
        return None

    Y_phyto = df[phyto_columns]
    print(f"Phytochemical compounds: {phyto_columns}")

    # Split data
    X_train, X_test, Y_train, Y_test = train_test_split(
        X, Y_phyto, test_size=0.2, random_state=42)

    # Scale features
    X_train_scaled = pipeline.scaler.fit_transform(X_train)
    X_test_scaled = pipeline.scaler.transform(X_test)

    # Random Forest model optimized for regression
    phyto_model = RandomForestRegressor(
        n_estimators=200,
        max_depth=15,
        min_samples_split=5,
        min_samples_leaf=2,
        max_features='sqrt',
        bootstrap=True,
        oob_score=True,
        random_state=42,
        n_jobs=-1
    )

    # Train model
    phyto_model.fit(X_train_scaled, Y_train)

    # Evaluate model
    Y_pred = phyto_model.predict(X_test_scaled)
    mae = mean_absolute_error(Y_test, Y_pred)

    print(f"Phytochemical prediction MAE: {mae:.3f} mg/g")
    print(f"OOB Score: {phyto_model.oob_score_:.3f}")

    # Feature importance analysis
    feature_names = pipeline.feature_names
    importances = phyto_model.feature_importances_
    feature_importance = list(zip(feature_names, importances))
    feature_importance.sort(key=lambda x: x[1], reverse=True)

    print("Top 10 important features:")
    for feat, imp in feature_importance[:10]:
        print(f"  {feat}: {imp:.4f}")

    # Calculate per-compound accuracy
    for i, compound in enumerate(phyto_columns):
        compound_mae = mean_absolute_error(Y_test.iloc[:, i], Y_pred[:, i])
        print(f"  {compound}: MAE = {compound_mae:.3f} mg/g")

    pipeline.phytochemical_model = phyto_model
    return phyto_model

def train_all_models(processed_data_path, synthetic_data_path=None, model_save_dir="models"):
    """Train all three models in the pipeline"""
    print("="*60)
    print("AyuSure AI Model Training Pipeline v2.0.1")
    print("="*60)

    # Initialize pipeline
    pipeline = AyuSureAIModelPipeline()

    # Load training data
    df = pipeline.load_training_data(processed_data_path, synthetic_data_path)

    # Preprocess features
    X, feature_names = pipeline.preprocess_features(df)
    pipeline.feature_names = feature_names

    if X.empty:
        print("ERROR: No valid features extracted from data")
        return None

    print(f"Feature matrix shape: {X.shape}")

    # Train models
    taste_model = train_taste_profile_model(pipeline, X, df)
    adulteration_model = train_adulteration_detection_model(pipeline, X, df)
    phytochemical_model = train_phytochemical_model(pipeline, X, df)

    # Set training metadata
    pipeline.training_date = datetime.now()

    print("\n" + "="*60)
    print("Training completed successfully!")

    # Display model summary
    models_trained = []
    if taste_model: models_trained.append("Taste Profile")
    if adulteration_model: models_trained.append("Adulteration Detection")
    if phytochemical_model: models_trained.append("Phytochemical Analysis")

    print(f"Models trained: {', '.join(models_trained)}")
    print(f"Training date: {pipeline.training_date}")
    print(f"Feature count: {len(feature_names)}")
    print("="*60)

    # Save models
    pipeline.save_models(model_save_dir)

    return pipeline

# Example usage
if __name__ == "__main__":
    print("AyuSure AI Model Training")
    print("=" * 40)

    # Train models with your data paths
    # pipeline = train_all_models("processed_sensor_data.csv", "synthetic_herb_data.csv")

    print("Training module ready. Import and use train_all_models() function.")