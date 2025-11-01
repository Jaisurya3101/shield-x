# src/services/detection.py

import io
import logging
import torch
import cv2
import numpy as np
from fastapi import HTTPException
from typing import Dict, Any, Union, List, Optional
from PIL import Image
from src.models.deepfake import DeepfakeModel
from src.models.harassment import HarassmentDetector
from datetime import datetime
import tempfile
import os

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class DetectionService:
    """Service for handling all content detection operations."""
    
    def __init__(self, 
                 deepfake_model: Optional[str] = None,
                 use_auth_token: Optional[str] = None):
        """
        Initialize the detection service with configurable models.
        
        Args:
            deepfake_model: Optional custom model identifier (uses default if None)
            use_auth_token: HuggingFace token for private models
        """
        logger.info("🚀 Initializing DeepGuard Detection Service...")
        start_time = datetime.now()
        
        try:
            # Initialize deepfake detection
            logger.info("Loading deepfake detection model...")
            self.deepfake_model = DeepfakeModel(
                model_name=deepfake_model,
                use_auth_token=use_auth_token
            )
            
            # Initialize harassment detection
            logger.info("Loading harassment detection model...")
            self.harassment_model = HarassmentDetector()
            
            # Log initialization success
            init_time = (datetime.now() - start_time).total_seconds()
            logger.info(f"✅ Detection Service initialized in {init_time:.2f}s")
            
            # Track service health
            self.healthy = True
            self.last_error = None
            
        except Exception as e:
            logger.error(f"❌ Failed to initialize Detection Service: {str(e)}", exc_info=True)
            self.healthy = False
            self.last_error = str(e)
            raise

    def analyze_image(self, image: Union[Image.Image, bytes, str]) -> Dict[str, Any]:
        """
        Analyze an image for deepfake content.
        
        Args:
            image: Can be a PIL Image, bytes, or file path
            
        Returns:
            Dictionary containing analysis results
        """
        if not self.healthy:
            raise HTTPException(
                status_code=503, 
                detail=f"Service unhealthy. Last error: {self.last_error}"
            )
            
        try:
            # Convert input to PIL Image if needed
            if isinstance(image, bytes):
                image = Image.open(io.BytesIO(image))
            elif isinstance(image, str):
                image = Image.open(image)
            elif not isinstance(image, Image.Image):
                raise ValueError(f"Unsupported image type: {type(image)}")
            
            # Ensure image is in RGB mode
            if image.mode != "RGB":
                image = image.convert("RGB")
            
            # Run deepfake detection
            start_time = datetime.now()
            deepfake_result = self.deepfake_model.analyze_image(image)
            
            # Add analysis metadata
            deepfake_result.update({
                "analysis_time": round((datetime.now() - start_time).total_seconds(), 4),
                "image_size": list(image.size),  # [width, height]
                "timestamp": datetime.now().isoformat()
            })
            
            logger.info(
                f"Image analyzed: {deepfake_result['prediction']} "
                f"(confidence: {deepfake_result['confidence']:.2%})"
            )
            
            return {
                "deepfake": deepfake_result,
                "harassment": None,
                "status": "success"
            }
            
        except Exception as e:
            logger.error(f"Image analysis error: {str(e)}", exc_info=True)
            raise HTTPException(
                status_code=500, 
                detail=f"Error analyzing image: {str(e)}"
            )

    def analyze_video(self, video_bytes: bytes, max_frames: int = 30) -> Dict[str, Any]:
        """
        Analyze video for deepfake content by sampling frames.
        
        Args:
            video_bytes: Raw video data
            max_frames: Maximum number of frames to analyze (default: 30)
            
        Returns:
            Dictionary with aggregated video analysis results
        """
        if not self.healthy:
            raise HTTPException(
                status_code=503,
                detail=f"Service unhealthy. Last error: {self.last_error}"
            )
        
        temp_path = None
        
        try:
            # Create temporary file for video processing
            with tempfile.NamedTemporaryFile(mode='wb', suffix='.mp4', delete=False) as tmp:
                tmp.write(video_bytes)
                temp_path = tmp.name
            
            # Open video with OpenCV
            cap = cv2.VideoCapture(temp_path)
            if not cap.isOpened():
                raise ValueError("Failed to open video file. Invalid format or corrupted file.")
            
            # Get video metadata
            total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
            fps = cap.get(cv2.CAP_PROP_FPS)
            
            if total_frames == 0:
                raise ValueError("Video has no frames")
            if fps == 0:
                fps = 30  # Default fallback
                
            duration = total_frames / fps
            
            # Calculate frame sampling interval
            sample_interval = max(1, total_frames // max_frames)
            
            logger.info(
                f"Analyzing video: {total_frames} frames, "
                f"{duration:.2f}s duration, sampling every {sample_interval} frames"
            )
            
            # Collect frames for analysis
            frames = []
            frame_count = 0
            
            while cap.isOpened() and len(frames) < max_frames:
                ret, frame = cap.read()
                if not ret:
                    break
                
                # Only collect sampled frames
                if frame_count % sample_interval == 0:
                    # Convert BGR to RGB
                    frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                    frames.append(frame_rgb)
                
                frame_count += 1
            
            # Cleanup video capture
            cap.release()
            
            if not frames:
                raise ValueError("No frames could be extracted from video")
            
            # Analyze collected frames using the model's video analysis method
            logger.info(f"Analyzing {len(frames)} sampled frames...")
            video_result = self.deepfake_model.analyze_video_frames(
                frames=frames,
                sample_rate=1  # Already sampled, so analyze all collected frames
            )
            
            # Add video metadata
            video_result.update({
                "total_frames": total_frames,
                "duration": round(duration, 2),
                "fps": round(fps, 2),
            })
            
            # Calculate fake frame ratio from frame results
            frame_results = video_result.get("frame_results", [])
            if frame_results:
                for i, result in enumerate(frame_results):
                    # Add temporal information
                    frame_number = i * sample_interval
                    result["frame_number"] = frame_number
                    result["timestamp"] = round(frame_number / fps, 2)
            
            logger.info(
                f"Video analyzed: {video_result['prediction']} "
                f"({video_result['fake_frames']}/{video_result['frames_analyzed']} frames fake, "
                f"confidence: {video_result['confidence']:.2%})"
            )
            
            return {
                "deepfake": video_result,
                "harassment": None,
                "status": "success"
            }
            
        except Exception as e:
            logger.error(f"Video analysis error: {str(e)}", exc_info=True)
            raise HTTPException(
                status_code=500, 
                detail=f"Error analyzing video: {str(e)}"
            )
        finally:
            # Ensure temporary file is cleaned up
            if temp_path and os.path.exists(temp_path):
                try:
                    os.remove(temp_path)
                    logger.debug(f"Cleaned up temporary file: {temp_path}")
                except Exception as e:
                    logger.warning(f"Failed to remove temporary file {temp_path}: {e}")
        
    def analyze_text(self, text: str) -> Dict[str, Any]:
        """
        Analyze text for harassment content.
        
        Args:
            text: Text content to analyze
            
        Returns:
            Dictionary with harassment analysis results
        """
        if not self.healthy:
            raise HTTPException(
                status_code=503,
                detail=f"Service unhealthy. Last error: {self.last_error}"
            )
        
        if not text or not text.strip():
            return {
                "deepfake": None,
                "harassment": {
                    'is_harassment': False,
                    'confidence': 0.0,
                    'raw_scores': {},
                    'categories': ['empty'],
                    'text_length': 0,
                    'error': 'Empty text provided'
                },
                "status": "success"
            }
            
        try:
            start_time = datetime.now()
            
            # Run harassment detection
            harassment_results = self.harassment_model.detect_harassment([text])
            harassment_result = harassment_results[0] if harassment_results else {}
            
            # Process results
            if isinstance(harassment_result, dict):
                # Get scores for different categories
                toxic_score = harassment_result.get('TOXIC', 0.0)
                severe_toxic_score = harassment_result.get('SEVERE_TOXIC', 0.0)
                threat_score = harassment_result.get('THREAT', 0.0)
                obscene_score = harassment_result.get('OBSCENE', 0.0)
                insult_score = harassment_result.get('INSULT', 0.0)
                
                # Check for intent-based threats (stalking, coercion, etc.)
                has_intent_threat = (
                    any(trigger in text.lower() for trigger in [
                        "know where you live", "i'm watching", "watching you", 
                        "find you", "track you", "follow you",
                        "or else", "do what i say", "you must", "you have to"
                    ])
                )
                
                # Determine overall harassment status
                is_harassment = (
                    toxic_score > 0.5 or 
                    severe_toxic_score > 0.3 or 
                    threat_score > 0.3 or
                    has_intent_threat
                )
                
                # Get highest confidence score
                if is_harassment:
                    # For harassment, use the highest toxic score OR boost for intent threats
                    if has_intent_threat:
                        confidence = max(0.75, max(toxic_score, severe_toxic_score, threat_score))
                    else:
                        confidence = max(toxic_score, severe_toxic_score, threat_score)
                else:
                    # For safe content, use low confidence (inverted and capped)
                    confidence = min(1.0 - toxic_score, 0.15) if toxic_score < 0.3 else 0.0
                
                # Determine categories with intent-based detection
                categories = []
                if toxic_score > 0.5:
                    categories.append('toxic')
                if severe_toxic_score > 0.3:
                    categories.append('severe_toxic')
                if threat_score > 0.3:
                    categories.append('threat')
                if obscene_score > 0.5:
                    categories.append('obscene')
                if insult_score > 0.5:
                    categories.append('insult')
                
                # Add intent-based categories
                text_lower = text.lower()
                if any(w in text_lower for w in ["know where you live", "find you", "watching", "follow"]):
                    categories.append('stalking')
                if any(w in text_lower for w in ["kill", "hurt", "destroy", "going to"]):
                    if 'threat' not in categories:
                        categories.append('threat')
                if any(w in text_lower for w in ["or else", "do what i say", "you must", "force"]):
                    categories.append('coercion')
                
                if not categories:
                    categories.append('safe')
                
                formatted_result = {
                    'is_harassment': is_harassment,
                    'confidence': round(confidence, 4),
                    'raw_scores': harassment_result,
                    'categories': categories,
                    'text_length': len(text),
                    'analysis_time': round((datetime.now() - start_time).total_seconds(), 4),
                    'timestamp': datetime.now().isoformat()
                }
                
                logger.info(
                    f"Text analyzed: {'harassment' if is_harassment else 'safe'} "
                    f"(confidence: {confidence:.2%}, categories: {categories})"
                )
            else:
                formatted_result = {
                    'is_harassment': False,
                    'confidence': 0.0,
                    'raw_scores': {},
                    'categories': ['unknown'],
                    'error': 'Invalid model output format'
                }
                logger.warning("Harassment model returned invalid format")
            
            return {
                "deepfake": None,
                "harassment": formatted_result,
                "status": "success"
            }
            
        except Exception as e:
            logger.error(f"Text analysis error: {str(e)}", exc_info=True)
            raise HTTPException(
                status_code=500, 
                detail=f"Error analyzing text: {str(e)}"
            )

    def health_check(self) -> Dict[str, Any]:
        """
        Check the health status of the detection service.
        
        Returns:
            Dictionary with detailed health information
        """
        try:
            deepfake_device = None
            if hasattr(self, 'deepfake_model') and hasattr(self.deepfake_model, 'device'):
                deepfake_device = str(self.deepfake_model.device)
            
            return {
                "status": "healthy" if self.healthy else "unhealthy",
                "last_error": self.last_error,
                "models": {
                    "deepfake": {
                        "loaded": hasattr(self, 'deepfake_model'),
                        "type": getattr(self.deepfake_model, 'model_type', 'unknown') if hasattr(self, 'deepfake_model') else None,
                        "device": deepfake_device,
                        "is_deepfake_model": getattr(self.deepfake_model, 'is_deepfake_model', False) if hasattr(self, 'deepfake_model') else False
                    },
                    "harassment": {
                        "loaded": hasattr(self, 'harassment_model'),
                        "type": type(self.harassment_model).__name__ if hasattr(self, 'harassment_model') else None
                    }
                },
                "system": {
                    "gpu_available": torch.cuda.is_available(),
                    "gpu_count": torch.cuda.device_count() if torch.cuda.is_available() else 0,
                    "gpu_name": torch.cuda.get_device_name(0) if torch.cuda.is_available() else None
                },
                "timestamp": datetime.now().isoformat()
            }
        except Exception as e:
            logger.error(f"Health check error: {e}", exc_info=True)
            return {
                "status": "error",
                "error": str(e),
                "timestamp": datetime.now().isoformat()
            }


# =============================================================================
# Factory and Service Initialization
# =============================================================================

def create_detection_service(
    model_name: Optional[str] = None,
    use_auth_token: Optional[str] = None
) -> DetectionService:
    """
    Factory function to create a detection service instance.
    
    Args:
        model_name: Optional custom deepfake model identifier
        use_auth_token: Optional HuggingFace API token
        
    Returns:
        Initialized DetectionService instance
    """
    try:
        service = DetectionService(
            deepfake_model=model_name,
            use_auth_token=use_auth_token
        )
        logger.info("✅ Detection service created successfully")
        return service
    except Exception as e:
        logger.error(f"❌ Failed to create detection service: {str(e)}", exc_info=True)
        raise


# Initialize default service instance
try:
    logger.info("Creating default detection service instance...")
    _service = create_detection_service()
    logger.info("✅ Default service instance created")
except Exception as e:
    logger.error(f"❌ Failed to create default detection service: {str(e)}")
    _service = None


# =============================================================================
# Async Wrappers for FastAPI Routes
# =============================================================================

async def detect_deepfake(file: Union[str, bytes, Image.Image]) -> Dict[str, Any]:
    """
    Async wrapper for deepfake detection.
    
    Args:
        file: Image as file path, bytes, or PIL Image
        
    Returns:
        Detection results dictionary
    """
    if not _service or not _service.healthy:
        raise HTTPException(
            status_code=503,
            detail="Detection service is not available. Please check service health."
        )
        
    try:
        return _service.analyze_image(file)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Deepfake detection error: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Detection failed: {str(e)}")


async def detect_harassment(text: str) -> Dict[str, Any]:
    """
    Async wrapper for harassment detection.
    
    Args:
        text: Text content to analyze
        
    Returns:
        Detection results dictionary
    """
    if not _service or not _service.healthy:
        raise HTTPException(
            status_code=503,
            detail="Detection service is not available. Please check service health."
        )
        
    try:
        return _service.analyze_text(text)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Harassment detection error: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Detection failed: {str(e)}")


async def detect_video_deepfake(video_bytes: bytes, max_frames: int = 30) -> Dict[str, Any]:
    """
    Async wrapper for video deepfake detection.
    
    Args:
        video_bytes: Raw video file bytes
        max_frames: Maximum frames to analyze
        
    Returns:
        Detection results dictionary
    """
    if not _service or not _service.healthy:
        raise HTTPException(
            status_code=503,
            detail="Detection service is not available. Please check service health."
        )
        
    try:
        return _service.analyze_video(video_bytes, max_frames)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Video detection error: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Detection failed: {str(e)}")


async def get_service_health() -> Dict[str, Any]:
    """
    Get the current health status of the detection service.
    
    Returns:
        Health status dictionary
    """
    if not _service:
        return {
            "status": "unavailable",
            "error": "Service not initialized. Check logs for initialization errors.",
            "timestamp": datetime.now().isoformat()
        }
    return _service.health_check()