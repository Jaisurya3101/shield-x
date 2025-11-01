from pydantic import BaseModel
from typing import List, Optional, Dict, Any


class DeepfakeRequest(BaseModel):
    file: str  # base64 string or path to file
    confidence_threshold: Optional[float] = 0.5


class HarassmentRequest(BaseModel):
    text: str
    sensitivity: Optional[float] = 0.7


class AnalysisResponse(BaseModel):
    success: bool
    result: Dict[str, Any]
    message: str


class BatchAnalysisItem(BaseModel):
    type: str  # "deepfake" or "harassment"
    content: str


class BatchAnalysisRequest(BaseModel):
    items: List[BatchAnalysisItem]


class HealthCheckResponse(BaseModel):
    status: str
