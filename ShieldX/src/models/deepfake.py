# src/models/deepfake.py

import io
import torch
import numpy as np
from PIL import Image
from transformers import AutoModelForImageClassification, AutoImageProcessor
from typing import Union, Dict, Any, Optional, List
import logging

logger = logging.getLogger(__name__)


class DeepfakeModel:
    """
    Enhanced deepfake detection using multiple specialized models.
    Detects: Traditional deepfakes + AI-generated images (Stable Diffusion, DALL-E, Midjourney, etc.)
    """
    
    DETECTION_MODELS = {
        "dima806": "dima806/deepfake_vs_real_image_detection",
        "umm-maybe": "umm-maybe/AI-image-detector",
    }
    
    def __init__(
        self, 
        model_name: Optional[str] = None,
        use_auth_token: Optional[str] = None,
        use_ensemble: bool = True
    ):
        """Initialize deepfake detection model(s)."""
        logger.info("🧠 Loading Enhanced Deepfake Detection System...")
        
        self.models = {}
        self.processors = {}
        self.use_ensemble = use_ensemble and model_name is None
        
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        logger.info(f"Using device: {self.device}")
        
        if model_name:
            self._load_single_model(model_name, use_auth_token)
        elif use_ensemble:
            self._load_ensemble_models(use_auth_token)
        else:
            self._load_single_model(self.DETECTION_MODELS["umm-maybe"], use_auth_token)
        
        self.model_type = "deepfake_detection"
        self.is_deepfake_model = len(self.models) > 0
        
        if self.is_deepfake_model:
            logger.info(f"✅ Loaded {len(self.models)} detection model(s)")
        else:
            logger.error("❌ No models loaded!")
            raise Exception("Failed to load any detection models")

    def _load_single_model(self, model_name: str, use_auth_token: Optional[str] = None):
        """Load a single detection model"""
        try:
            logger.info(f"→ Loading model: {model_name}")
            processor = AutoImageProcessor.from_pretrained(model_name, token=use_auth_token)
            model = AutoModelForImageClassification.from_pretrained(model_name, token=use_auth_token)
            model.to(self.device).eval()
            
            self.processors[model_name] = processor
            self.models[model_name] = model
            
            if hasattr(model.config, 'id2label'):
                logger.info(f"  Labels: {model.config.id2label}")
            logger.info(f"✅ Loaded: {model_name}")
        except Exception as e:
            logger.error(f"❌ Failed to load {model_name}: {e}")
            raise

    def _load_ensemble_models(self, use_auth_token: Optional[str] = None):
        """Load multiple models for ensemble detection"""
        models_to_load = [
            ("umm-maybe", self.DETECTION_MODELS["umm-maybe"]),
            ("dima806", self.DETECTION_MODELS["dima806"]),
        ]
        
        loaded_count = 0
        for name, model_path in models_to_load:
            try:
                self._load_single_model(model_path, use_auth_token)
                loaded_count += 1
            except Exception as e:
                logger.warning(f"⚠️ Skipping {name}: {e}")
        
        if loaded_count == 0:
            raise Exception("Failed to load any ensemble models")
        logger.info(f"✅ Ensemble ready with {loaded_count} models")

    def _to_pil_image(self, image_data: Union[bytes, Image.Image, np.ndarray]) -> Image.Image:
        """Convert various image formats to PIL Image."""
        try:
            if isinstance(image_data, (bytes, bytearray)):
                return Image.open(io.BytesIO(image_data)).convert("RGB")
            elif isinstance(image_data, Image.Image):
                return image_data.convert("RGB")
            elif isinstance(image_data, np.ndarray):
                if len(image_data.shape) == 3 and image_data.shape[-1] == 3:
                    return Image.fromarray(image_data).convert("RGB")
                else:
                    raise ValueError(f"Unsupported array shape: {image_data.shape}")
            else:
                raise ValueError(f"Unsupported type: {type(image_data)}")
        except Exception as e:
            logger.error(f"Error converting image: {e}")
            raise

    def _normalize_label(self, raw_label: str, model_name: str) -> tuple:
        """Normalize model output labels to standard format."""
        label_lower = raw_label.lower()
        fake_keywords = ["fake", "ai", "artificial", "generated", "synthetic", "forged", "manipulated", "1"]
        real_keywords = ["real", "authentic", "genuine", "original", "human", "0"]
        
        if any(keyword in label_lower for keyword in fake_keywords):
            return "Fake", 1.0
        elif any(keyword in label_lower for keyword in real_keywords):
            return "Real", 1.0
        else:
            logger.warning(f"Unknown label format: {raw_label} from {model_name}")
            return "Uncertain", 0.5

    def analyze_image(
        self, 
        image_data: Union[bytes, Image.Image, np.ndarray],
        return_all_scores: bool = False
    ) -> Dict[str, Any]:
        """Analyze an image for deepfake/AI-generated content."""
        if not self.is_deepfake_model:
            return self._error_response("No models loaded")
        
        try:
            image = self._to_pil_image(image_data)
            model_results = []
            all_model_scores = {}
            
            for model_name, model in self.models.items():
                try:
                    processor = self.processors[model_name]
                    inputs = processor(images=image, return_tensors="pt").to(self.device)
                    
                    with torch.no_grad():
                        outputs = model(**inputs)
                    
                    probs = torch.nn.functional.softmax(outputs.logits, dim=1)[0]
                    top_prob, top_idx = torch.max(probs, dim=0)
                    
                    id2label = model.config.id2label if hasattr(model.config, 'id2label') else {0: "real", 1: "fake"}
                    raw_label = id2label.get(top_idx.item(), "unknown")
                    
                    prediction, adjustment = self._normalize_label(raw_label, model_name)
                    confidence = top_prob.item() * adjustment
                    
                    model_results.append({
                        "model": model_name.split('/')[-1],
                        "prediction": prediction,
                        "confidence": confidence,
                        "raw_label": raw_label
                    })
                    
                    all_model_scores[model_name.split('/')[-1]] = {
                        "prediction": prediction,
                        "confidence": round(confidence, 4),
                        "probabilities": {
                            id2label.get(i, f"label_{i}"): round(probs[i].item(), 4)
                            for i in range(len(probs))
                        }
                    }
                    
                    logger.debug(f"{model_name}: {prediction} ({confidence:.4f})")
                except Exception as e:
                    logger.warning(f"Model {model_name} failed: {e}")
            
            if not model_results:
                return self._error_response("All models failed")
            
            # Aggregate results
            if len(model_results) > 1:
                fake_votes = sum(1 for r in model_results if r["prediction"] == "Fake")
                avg_confidence = sum(r["confidence"] for r in model_results) / len(model_results)
                
                if fake_votes > len(model_results) / 2:
                    final_prediction = "Fake"
                elif fake_votes == 0:
                    final_prediction = "Real"
                else:
                    final_prediction = "Fake" if avg_confidence > 0.6 else "Uncertain"
                
                final_confidence = avg_confidence
                method = f"ensemble ({len(model_results)} models)"
            else:
                final_prediction = model_results[0]["prediction"]
                final_confidence = model_results[0]["confidence"]
                method = "single model"
            
            confidence_level = "High" if final_confidence >= 0.8 else "Medium" if final_confidence >= 0.6 else "Low"
            
            result = {
                "prediction": final_prediction,
                "confidence": round(final_confidence, 4),
                "score": round(final_confidence, 4),
                "confidence_level": confidence_level,
                "detection_method": method,
                "models_used": [r["model"] for r in model_results]
            }
            
            if return_all_scores or len(model_results) > 1:
                result["model_details"] = all_model_scores
            
            logger.info(f"Final: {final_prediction} (confidence: {final_confidence:.2%}, method: {method})")
            return result
            
        except Exception as e:
            logger.error(f"Error analyzing image: {e}", exc_info=True)
            return self._error_response(str(e))

    def analyze_video_frames(self, frames: list, sample_rate: int = 1) -> Dict[str, Any]:
        """Analyze multiple video frames."""
        frame_results = []
        fake_count = 0
        real_count = 0
        total_confidence = 0.0
        
        for i, frame in enumerate(frames[::sample_rate]):
            result = self.analyze_image(frame)
            result["frame_index"] = i * sample_rate
            frame_results.append(result)
            
            if result["prediction"] == "Fake":
                fake_count += 1
            elif result["prediction"] == "Real":
                real_count += 1
            total_confidence += result["confidence"]
        
        num_frames = len(frame_results)
        avg_confidence = total_confidence / num_frames if num_frames > 0 else 0.0
        fake_ratio = fake_count / num_frames if num_frames > 0 else 0.0
        
        overall = "Fake" if fake_ratio > 0.3 else "Real" if fake_ratio < 0.1 and real_count > 0 else "Uncertain"
        
        return {
            "prediction": overall,
            "confidence": round(avg_confidence, 4),
            "score": round(avg_confidence, 4),
            "fake_frame_ratio": round(fake_ratio, 4),
            "frames_analyzed": num_frames,
            "fake_frames": fake_count,
            "real_frames": real_count,
            "frame_results": frame_results,
            "detection_method": "video_analysis"
        }

    def _error_response(self, error_msg: str) -> Dict[str, Any]:
        """Return structured error response"""
        return {
            "prediction": "Error",
            "confidence": 0.0,
            "score": 0.0,
            "error": error_msg,
            "model": "none"
        }