#!/usr/bin/env python3
"""
DeepGuard v3.2 (Updated with Real Deepfake Detection)
AI-Based Cyber Harassment & Deepfake Detection
------------------------------------------------
✅ Real deepfake detection with specialized models
✅ Improved video analysis support
✅ Better error handling and logging
✅ Enhanced health monitoring
"""

import sys
import os
import uuid
import warnings
from datetime import datetime
from typing import Optional
from collections import Counter

# ==========================================================
# 🧩 Environment & Warning Suppression
# ==========================================================
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"
os.environ["TOKENIZERS_PARALLELISM"] = "false"
warnings.filterwarnings("ignore", category=UserWarning)
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=DeprecationWarning)

# Ensure UTF-8 console output (Windows-safe)
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

# ==========================================================
# ⚙️ Core Imports
# ==========================================================
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from fastapi import FastAPI, HTTPException, Header, UploadFile, File, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel
import uvicorn

from src.core.config import settings
from src.core.security import create_access_token, verify_password, get_password_hash
from src.services.detection import (
    detect_harassment, 
    detect_deepfake,
    detect_video_deepfake,
    get_service_health
)
from src.utils.logging import logger

# ==========================================================
# 🚀 FastAPI Initialization
# ==========================================================
app = FastAPI(
    title=getattr(settings, "APP_NAME", "DeepGuard"),
    version="3.2.0",
    description=getattr(settings, "APP_DESCRIPTION", "AI-based harassment & deepfake detection"),
    docs_url="/docs" if getattr(settings, "DEBUG", True) else None,
    redoc_url="/redoc" if getattr(settings, "DEBUG", True) else None,
)

# ==========================================================
# 🌐 CORS Setup
# ==========================================================
allowed_origins = getattr(settings, "ALLOWED_ORIGINS", ["http://localhost:3000"])
app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

API_PREFIX = getattr(settings, "API_PREFIX", "/api/v1")

# ==========================================================
# 🧠 Mock Databases
# ==========================================================
users_db = {
    "testuser": {
        "user_id": "1",
        "username": "testuser",
        "email": "test@deepguard.com",
        "full_name": "Test User",
        "hashed_password": get_password_hash("test123"),
    }
}
analytics_db = {"total_scans": 0, "threats_detected": 0, "recent_scans": []}

# ==========================================================
# 🧾 Schemas
# ==========================================================
class SignupRequest(BaseModel):
    username: str
    email: str
    password: str
    full_name: Optional[str] = None


class LoginRequest(BaseModel):
    username: str
    password: str


class ScanRequest(BaseModel):
    text: str


class NotificationPayload(BaseModel):
    content: str
    sender: Optional[str] = "unknown"
    timestamp: Optional[int] = None


# ==========================================================
# ⚠️ Global Exception Handler
# ==========================================================
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error(f"Unhandled exception on {request.url}: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500, content={"success": False, "message": "Internal server error"}
    )

# ==========================================================
# 🩺 Health Endpoints
# ==========================================================
@app.get("/", tags=["Health"])
async def root():
    return {
        "message": "DeepGuard - AI-Based Detection System",
        "status": "healthy",
        "version": "3.2.0",
    }


@app.get(f"{API_PREFIX}/health", tags=["Health"])
async def health():
    """Get detailed service health status"""
    try:
        health_status = await get_service_health()
        return {
            "status": "healthy",
            "message": "AI system operational",
            "details": health_status
        }
    except Exception as e:
        logger.error(f"Health check failed: {e}", exc_info=True)
        return {
            "status": "degraded",
            "message": "Service partially operational",
            "error": str(e)
        }

# ==========================================================
# 🔐 Authentication
# ==========================================================
@app.post(f"{API_PREFIX}/auth/signup", tags=["Authentication"])
async def signup(r: SignupRequest):
    if r.username in users_db:
        raise HTTPException(status_code=400, detail="Username already exists")

    users_db[r.username] = {
        "user_id": str(len(users_db) + 1),
        "username": r.username,
        "email": r.email,
        "full_name": r.full_name or r.username,
        "hashed_password": get_password_hash(r.password),
    }
    access_token = create_access_token({"sub": r.username})
    user = {k: v for k, v in users_db[r.username].items() if k != "hashed_password"}
    logger.info(f"User registered successfully: {r.username}")
    return {
        "success": True,
        "message": "Registration successful",
        "data": {"access_token": access_token, "user": user},
    }


@app.post(f"{API_PREFIX}/auth/login", tags=["Authentication"])
async def login(r: LoginRequest):
    u = users_db.get(r.username)
    if not u or not verify_password(r.password, u["hashed_password"]):
        logger.warning(f"Failed login attempt: {r.username}")
        raise HTTPException(status_code=401, detail="Invalid credentials")

    access_token = create_access_token({"sub": r.username})
    user = {k: v for k, v in u.items() if k != "hashed_password"}
    logger.info(f"User logged in: {r.username}")
    return {
        "success": True,
        "message": "Login successful",
        "data": {"access_token": access_token, "user": user},
    }


@app.get(f"{API_PREFIX}/auth/me", tags=["Authentication"])
async def get_me(authorization: Optional[str] = Header(None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Not authenticated")

    user = {k: v for k, v in users_db["testuser"].items() if k != "hashed_password"}
    return {"success": True, "data": user}

# ==========================================================
# 🧠 Harassment Detection
# ==========================================================
@app.post(f"{API_PREFIX}/scan_text", tags=["Analysis"])
async def scan_text(r: ScanRequest):
    """Scan text content for harassment/toxic content"""
    try:
        result = await detect_harassment(r.text)
    except Exception as e:
        logger.error(f"Harassment detection error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Detection service error")

    harassment = result.get("harassment", {})
    is_harassment = harassment.get("is_harassment", False)
    confidence = harassment.get("confidence", 0.0)

    severity = (
        "critical" if confidence >= 0.9 else
        "high" if confidence >= 0.7 else
        "medium" if confidence >= 0.5 else
        "low"
    )

    analytics_db["total_scans"] += 1
    if is_harassment:
        analytics_db["threats_detected"] += 1

    logger.info(f"Text scanned — Harassment={is_harassment} (conf={confidence:.2f})")
    return {
        "success": True,
        "data": {
            "is_harassment": is_harassment,
            "confidence": confidence,
            "severity": severity,
            "categories": harassment.get("categories", []),
        },
    }

# ==========================================================
# 🎭 Deepfake Detection
# ==========================================================
@app.post(f"{API_PREFIX}/scan_image", tags=["Analysis"])
async def scan_image(file: UploadFile = File(...)):
    """Scan image for deepfake manipulation"""
    try:
        contents = await file.read()
        result = await detect_deepfake(contents)
    except Exception as e:
        logger.error(f"Deepfake detection error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Deepfake service error")

    deepfake = result.get("deepfake", {})
    prediction = deepfake.get("prediction", "unknown")
    confidence = deepfake.get("confidence", 0.0)
    confidence_level = deepfake.get("confidence_level", "unknown")

    analytics_db["total_scans"] += 1
    # Updated: Check for "Fake" instead of "deepfake"
    if prediction.lower() == "fake":
        analytics_db["threats_detected"] += 1

    logger.info(f"Image scanned — Prediction={prediction} (confidence={confidence:.2f})")
    return {
        "success": True,
        "data": {
            "prediction": prediction,
            "confidence": confidence,
            "confidence_level": confidence_level,
            "analysis_time": deepfake.get("analysis_time", 0.0),
            "image_size": deepfake.get("image_size", [0, 0])
        }
    }


@app.post(f"{API_PREFIX}/scan_video", tags=["Analysis"])
async def scan_video(file: UploadFile = File(...), max_frames: int = 30):
    """Scan video for deepfake manipulation"""
    try:
        contents = await file.read()
        result = await detect_video_deepfake(contents, max_frames)
    except Exception as e:
        logger.error(f"Video deepfake detection error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Video analysis service error")

    deepfake = result.get("deepfake", {})
    prediction = deepfake.get("prediction", "unknown")
    confidence = deepfake.get("confidence", 0.0)
    fake_ratio = deepfake.get("fake_frame_ratio", 0.0)

    analytics_db["total_scans"] += 1
    if prediction.lower() == "fake":
        analytics_db["threats_detected"] += 1

    logger.info(
        f"Video scanned — Prediction={prediction} "
        f"(confidence={confidence:.2f}, fake_ratio={fake_ratio:.2f})"
    )
    return {
        "success": True,
        "data": {
            "prediction": prediction,
            "confidence": confidence,
            "fake_frame_ratio": fake_ratio,
            "frames_analyzed": deepfake.get("frames_analyzed", 0),
            "total_frames": deepfake.get("total_frames", 0),
            "duration": deepfake.get("duration", 0.0),
            "fps": deepfake.get("fps", 0.0)
        }
    }

# ==========================================================
# 📱 Mobile Notification Analysis
# ==========================================================
@app.post(f"{API_PREFIX}/mobile/analyze-notification", tags=["Mobile"])
async def analyze_notification(p: NotificationPayload):
    """Analyze mobile notification content for harassment"""
    try:
        result = await detect_harassment(p.content)
    except Exception as e:
        logger.error(f"Notification analysis error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Notification analysis error")

    harassment = result.get("harassment", {})
    is_harassment = harassment.get("is_harassment", False)
    confidence = harassment.get("confidence", 0.0)
    risk = int(confidence * 100)

    return {
        "harassment": {
            "is_harassment": is_harassment,
            "confidence": round(confidence, 3),
            "severity": "critical" if confidence > 0.9 else "high" if confidence > 0.7 else "medium",
            "type": "threat" if is_harassment else "safe",
            "explanation": f"{risk}% risk",
        },
        "analysis_id": str(uuid.uuid4()),
        "timestamp": p.timestamp or int(datetime.now().timestamp() * 1000),
        "risk_score": risk,
    }

# ==========================================================
# 📊 Analytics
# ==========================================================
@app.get(f"{API_PREFIX}/analytics/overview", tags=["Analytics"])
async def overview():
    """Get analytics overview of scans and detections"""
    total = analytics_db["total_scans"]
    threats = analytics_db["threats_detected"]
    detection_rate = round((threats / max(total, 1)) * 100, 2)
    text_pool = " ".join([s.get("text", "") for s in analytics_db["recent_scans"]])
    keywords = [
        {"keyword": k, "count": c}
        for k, c in Counter([w.lower() for w in text_pool.split() if len(w) > 3]).most_common(10)
    ]

    return {
        "success": True,
        "data": {
            "total_scans": total,
            "threats_detected": threats,
            "detection_rate": detection_rate,
            "top_keywords": keywords,
        },
    }

# ==========================================================
# ▶️ Run Server
# ==========================================================
if __name__ == "__main__":
    host = getattr(settings, "HOST", "0.0.0.0")
    port = getattr(settings, "PORT", 8001)

    print("\n" + "=" * 60)
    print("🤖 DeepGuard v3.2 - AI-Based Cyber Harassment & Deepfake Detection")
    print("=" * 60)
    print(f"Running on http://{host}:{port}")
    print(f"Docs → http://localhost:{port}/docs")
    print(f"Health Check → http://localhost:{port}{API_PREFIX}/health")
    print("=" * 60)
    print("\n✨ Features:")
    print("  • Real deepfake detection (dima806 model)")
    print("  • Image & Video analysis")
    print("  • Harassment/toxic content detection")
    print("  • Mobile notification scanning")
    print("=" * 60 + "\n")

    uvicorn.run("main:app", host=host, port=port, reload=False)