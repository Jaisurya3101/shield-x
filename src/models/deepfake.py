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
    Multi-model AI detection system with fallback strategy.
    Tries multiple detection models in order of capability.
    """
    
    # Ordered list of models to try (best first)
    DETECTOR_MODELS = [
        {
            "key": "dima806",
            "path": "dima806/deepfake_vs_real_image_detection",
            "purpose": "Face deepfake & manipulation detection",
            "priority": 1,
            "labels": {"0": "Real", "1": "Fake", "real": "Real", "fake": "Fake"}
        },
        {
            "key": "organika_sdxl",
            "path": "Organika/sdxl-detector",
            "purpose": "Stable Diffusion XL detection",
            "priority": 2,
            "labels": {"artificial": "Fake", "human": "Real"}
        },
        {
            "key": "umm_maybe",
            "path": "umm-maybe/AI-image-detector", 
            "purpose": "General AI art detection (Oct 2022)",
            "priority": 3,
            "labels": {"0": "Fake", "1": "Real", "artificial": "Fake", "real": "Real"}
        }
    ]
    
    def __init__(
        self, 
        model_name: Optional[str] = None,
        use_auth_token: Optional[str] = None,
        use_ensemble: bool = False
    ):
        """Initialize deepfake detection model(s)."""
        logger.info("🧠 Loading AI Detection System...")
        
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        logger.info(f"Using device: {self.device}")
        
        self.models = {}
        self.processors = {}
        self.model_configs = {}
        
        if model_name:
            # Single custom model
            self._load_single_model("custom", model_name, use_auth_token)
        else:
            # Try loading models in order, use first successful
            loaded = False
            for config in self.DETECTOR_MODELS:
                try:
                    self._load_single_model(config["key"], config["path"], use_auth_token, config)
                    logger.info(f"✅ Primary model loaded: {config['purpose']}")
                    loaded = True
                    break  # Use first working model
                except Exception as e:
                    logger.warning(f"⚠️ Skipping {config['key']}: {e}")
            
            if not loaded:
                raise Exception("Failed to load any detection models")
        
        self.model_type = "deepfake_detection"
        self.is_deepfake_model = True
        logger.info(f"✅ Detection system ready ({len(self.models)} model(s))")

    def _load_single_model(self, key: str, model_path: str, use_auth_token: Optional[str], config: dict = None):
        """Load a single detection model"""
        try:
            logger.info(f"→ Loading: {model_path}")
            
            processor = AutoImageProcessor.from_pretrained(model_path, token=use_auth_token)
            model = AutoModelForImageClassification.from_pretrained(model_path, token=use_auth_token)
            model.to(self.device).eval()
            
            self.processors[key] = processor
            self.models[key] = model
            
            if config:
                self.model_configs[key] = config
            
            if hasattr(model.config, 'id2label'):
                logger.info(f"  Model labels: {model.config.id2label}")
            
        except Exception as e:
            logger.error(f"❌ Failed to load {model_path}: {e}")
            raise

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
                raise ValueError(f"Unsupported array shape: {image_data.shape}")
            raise ValueError(f"Unsupported type: {type(image_data)}")
        except Exception as e:
            logger.error(f"Error converting image: {e}")
            raise

    def _interpret_prediction(self, probs: torch.Tensor, id2label: dict, config: dict, model_key: str) -> tuple[str, float, dict]:
        """
        Interpret model output using label mapping from config.
        """
        top_prob, top_idx = torch.max(probs, dim=0)
        top_idx_val = top_idx.item()
        
        # Get raw label from model
        raw_label = id2label.get(top_idx_val, str(top_idx_val))
        
        # Get all probabilities
        all_probs = {
            id2label.get(i, f"class_{i}"): round(probs[i].item(), 4)
            for i in range(len(probs))
        }
        
        logger.info(f"{model_key}: top_idx={top_idx_val}, label='{raw_label}', probs={all_probs}")
        
        # Get label mapping from config
        label_map = config.get("labels", {}) if config else {}
        
        # Try to map the label
        prediction = "Uncertain"
        
        # Direct label lookup
        if raw_label in label_map:
            prediction = label_map[raw_label]
        elif raw_label.lower() in label_map:
            prediction = label_map[raw_label.lower()]
        elif str(top_idx_val) in label_map:
            prediction = label_map[str(top_idx_val)]
        else:
            # Fallback: keyword matching
            raw_label_lower = raw_label.lower()
            if any(word in raw_label_lower for word in ["artificial", "ai", "synthetic", "fake", "generated"]):
                prediction = "Fake"
            elif any(word in raw_label_lower for word in ["real", "authentic", "natural", "genuine", "human"]):
                prediction = "Real"
            else:
                logger.warning(f"⚠️ Could not interpret label '{raw_label}' from {model_key}")
        
        confidence = top_prob.item()
        
        details = {
            "raw_label": raw_label,
            "label_index": top_idx_val,
            "all_probabilities": all_probs
        }
        
        logger.info(f"{model_key} result: {raw_label} -> {prediction} ({confidence:.4f})")
        return prediction, confidence, details

    def analyze_image(
        self, 
        image_data: Union[bytes, Image.Image, np.ndarray],
        return_all_scores: bool = True
    ) -> Dict[str, Any]:
        """
        Analyze an image for AI-generated/deepfake content.
        """
        if not self.is_deepfake_model:
            return self._error_response("No model loaded")
        
        try:
            image = self._to_pil_image(image_data)
            
            # Run inference on loaded model(s)
            results = {}
            
            for model_key, model in self.models.items():
                try:
                    processor = self.processors[model_key]
                    config = self.model_configs.get(model_key, {})
                    
                    # Preprocess
                    inputs = processor(images=image, return_tensors="pt").to(self.device)
                    
                    # Get prediction
                    with torch.no_grad():
                        outputs = model(**inputs)
                    
                    # Get probabilities
                    probs = torch.nn.functional.softmax(outputs.logits, dim=1)[0]
                    id2label = model.config.id2label if hasattr(model.config, 'id2label') else {}
                    
                    # Interpret prediction
                    prediction, confidence, details = self._interpret_prediction(
                        probs, id2label, config, model_key
                    )
                    
                    results[model_key] = {
                        "prediction": prediction,
                        "confidence": confidence,
                        "purpose": config.get("purpose", "Unknown"),
                        **details
                    }
                    
                except Exception as e:
                    logger.error(f"Model {model_key} failed: {e}", exc_info=True)
            
            if not results:
                return self._error_response("All models failed")
            
            # Use primary model result
            primary_key = list(results.keys())[0]
            final = results[primary_key]
            
            prediction = final["prediction"]
            confidence = final["confidence"]
            method = final.get("purpose", "Unknown detector")
            
            # Confidence level
            if confidence >= 0.85:
                confidence_level = "High"
            elif confidence >= 0.7:
                confidence_level = "Medium"
            else:
                confidence_level = "Low"
            
            # Build response
            result = {
                "prediction": prediction,
                "confidence": round(confidence, 4),
                "score": round(confidence, 4),
                "confidence_level": confidence_level,
                "detection_method": method,
                "models_used": list(results.keys()),
                "limitations": self._get_limitations(primary_key)
            }
            
            if return_all_scores:
                result["model_details"] = results
            
            logger.info(f"Final: {prediction} ({confidence:.2%}) via {method}")
            
            return result
            
        except Exception as e:
            logger.error(f"Error analyzing image: {e}", exc_info=True)
            return self._error_response(str(e))

    def _get_limitations(self, model_key: str) -> str:
        """Get limitations text for the model being used"""
        limitations = {
            "dima806": "Detects face deepfakes and manipulated images. May miss fully AI-generated art (Stable Diffusion, DALL-E).",
            "organika_sdxl": "Optimized for Stable Diffusion XL. May not detect face deepfakes or other AI generators (Gemini, DALL-E 3, Midjourney 5+).",
            "umm_maybe": "Trained on pre-Oct 2022 AI art. May miss face deepfakes and newer generators (Gemini, DALL-E 3, Midjourney 5+).",
        }
        return limitations.get(model_key, "Detection accuracy varies by image type and generator.")

    def analyze_video_frames(self, frames: list, sample_rate: int = 1) -> Dict[str, Any]:
        """Analyze multiple video frames."""
        frame_results = []
        fake_count = 0
        real_count = 0
        total_confidence = 0.0
        
        for i, frame in enumerate(frames[::sample_rate]):
            result = self.analyze_image(frame, return_all_scores=False)
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
        
        if fake_ratio > 0.3:
            overall = "Fake"
        elif fake_ratio < 0.1 and real_count > 0:
            overall = "Real"
        else:
            overall = "Uncertain"
        
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


if __name__ == "__main__":
    try:
        detector = DeepfakeModel()
        print("✅ Model initialized")
        print(f"Models: {list(detector.models.keys())}")
        print(f"Device: {detector.device}")
    except Exception as e:
        print(f"❌ Failed: {e}")