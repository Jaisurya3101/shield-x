"""
Setup script to download required models during deployment
"""
import os
from transformers import AutoModel, AutoFeatureExtractor

def download_models():
    print("📥 Downloading required models...")
    
    # Create models directory if it doesn't exist
    os.makedirs("models_cache", exist_ok=True)
    
    # Download BEIT model for deepfake detection
    print("Downloading BEIT model...")
    model = AutoModel.from_pretrained("microsoft/beit-base-patch16-224")
    feature_extractor = AutoFeatureExtractor.from_pretrained("microsoft/beit-base-patch16-224")
    
    # Save models
    print("Saving models...")
    model.save_pretrained("models_cache/beit")
    feature_extractor.save_pretrained("models_cache/beit")
    
    print("✅ Models downloaded successfully!")

if __name__ == "__main__":
    download_models()