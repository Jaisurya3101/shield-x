# src/api/mobile_routes.py

from fastapi import APIRouter, Depends, HTTPException
from typing import Dict, List, Optional
from datetime import datetime
from pydantic import BaseModel
import uuid

from src.services.detection import detect_harassment
from src.core.security import get_current_user
from src.utils.logging import logger

# Mobile-specific router
mobile_router = APIRouter(prefix="/mobile", tags=["Mobile"])

# --- Mobile-Specific Schemas ---
class NotificationMessage(BaseModel):
    """Schema for incoming notification messages from mobile app"""
    sender: str
    message_text: str
    app_name: str  # WhatsApp, Messenger, SMS, etc.
    timestamp: datetime
    package_name: Optional[str] = None
    device_id: str

class HarassmentAlert(BaseModel):
    """Schema for harassment alert response"""
    is_harassment: bool
    confidence_score: float
    severity_level: str  # "low", "medium", "high", "critical"
    threat_categories: List[str]
    alert_id: str
    timestamp: datetime
    recommendation: str

class BatchNotificationRequest(BaseModel):
    """Schema for batch notification analysis"""
    notifications: List[NotificationMessage]
    device_id: str

# --- Mobile Endpoints ---

@mobile_router.post("/analyze-notification", response_model=HarassmentAlert)
async def analyze_notification(
    notification: NotificationMessage,
    current_user: str = Depends(get_current_user)
):
    """
    Analyzes a single notification message for harassment content.
    Optimized for real-time mobile notifications.
    """
    try:
        logger.info(f"Analyzing notification from {notification.app_name}: {notification.sender}")
        
        # Perform harassment detection
        result = await detect_harassment(notification.message_text)
        
        # Extract harassment analysis results
        harassment_data = result.get("harassment", {})
        
        # Determine if it's harassment and confidence
        is_harassment = harassment_data.get("is_harassment", False)
        confidence = harassment_data.get("confidence", 0.0)
        
        # Determine severity level based on confidence
        if confidence >= 0.9:
            severity = "critical"
        elif confidence >= 0.7:
            severity = "high"
        elif confidence >= 0.5:
            severity = "medium"
        else:
            severity = "low"
        
        # Generate alert ID for tracking
        alert_id = str(uuid.uuid4())
        
        # Get threat categories (customize based on your model)
        threat_categories = harassment_data.get("categories", ["general"])
        
        # Generate recommendation
        if is_harassment and confidence > 0.7:
            recommendation = "Block sender and report to authorities if threats escalate"
        elif is_harassment:
            recommendation = "Monitor sender and consider blocking if pattern continues"
        else:
            recommendation = "No action required"
        
        # Log the analysis for monitoring
        logger.info(f"Harassment analysis: {is_harassment}, confidence: {confidence}")
        
        return HarassmentAlert(
            is_harassment=is_harassment,
            confidence_score=confidence,
            severity_level=severity,
            threat_categories=threat_categories,
            alert_id=alert_id,
            timestamp=datetime.now(),
            recommendation=recommendation
        )
        
    except Exception as e:
        logger.error(f"Mobile notification analysis failed: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Analysis failed: {str(e)}")


@mobile_router.post("/analyze-batch-notifications", response_model=List[HarassmentAlert])
async def analyze_batch_notifications(
    request: BatchNotificationRequest,
    current_user: str = Depends(get_current_user)
):
    """
    Analyzes multiple notifications in batch for deep scan functionality.
    """
    try:
        logger.info(f"Processing batch of {len(request.notifications)} notifications")
        
        alerts = []
        for notification in request.notifications:
            # Reuse the single notification analysis logic
            alert = await analyze_notification(notification, current_user)
            alerts.append(alert)
        
        # Log batch results
        harassment_count = sum(1 for alert in alerts if alert.is_harassment)
        logger.info(f"Batch analysis complete: {harassment_count}/{len(alerts)} flagged as harassment")
        
        return alerts
        
    except Exception as e:
        logger.error(f"Batch notification analysis failed: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Batch analysis failed: {str(e)}")


@mobile_router.get("/device-status/{device_id}")
async def get_device_status(
    device_id: str,
    current_user: str = Depends(get_current_user)
):
    """
    Gets monitoring status and statistics for a specific device.
    """
    try:
        # This would typically query a database for device statistics
        # For now, returning mock data structure
        return {
            "device_id": device_id,
            "monitoring_active": True,
            "last_check": datetime.now(),
            "total_messages_analyzed": 0,  # Would come from database
            "harassment_detected": 0,      # Would come from database
            "apps_monitored": ["WhatsApp", "Messenger", "SMS", "Instagram", "Telegram"]
        }
        
    except Exception as e:
        logger.error(f"Device status check failed: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Status check failed: {str(e)}")


@mobile_router.post("/report-incident")
async def report_incident(
    alert_id: str,
    additional_info: Optional[str] = None,
    current_user: str = Depends(get_current_user)
):
    """
    Allows users to report harassment incidents for further action.
    """
    try:
        # This would typically store incident reports in a database
        # and potentially trigger notifications to authorities
        
        incident_id = str(uuid.uuid4())
        
        logger.info(f"Incident reported: alert_id={alert_id}, incident_id={incident_id}")
        
        return {
            "incident_id": incident_id,
            "alert_id": alert_id,
            "status": "reported",
            "timestamp": datetime.now(),
            "message": "Incident reported successfully. Authorities will be notified if required."
        }
        
    except Exception as e:
        logger.error(f"Incident reporting failed: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Incident reporting failed: {str(e)}")