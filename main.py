#!/usr/bin/env python3
"""
DeepGuard v3.1 (Final Clean Build)
AI-Based Cyber Harassment & Deepfake Detection
------------------------------------------------
✅ Suppresses TensorFlow, Hugging Face & Deprecation Warnings
✅ Stable on Windows + CPU
✅ Firebase warning only
✅ Safe Logging + Error Handling
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
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"              # Hide TensorFlow INFO/WARN
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"             # Disable oneDNN float warnings
os.environ["TOKENIZERS_PARALLELISM"] = "false"        # Silence Hugging Face warnings
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
from src.services.detection import detect_harassment, detect_deepfake
from src.utils.logging import logger

# ==========================================================
# 🚀 FastAPI Initialization
# ==========================================================
app = FastAPI(
    title=getattr(settings, "APP_NAME", "DeepGuard"),
    version=getattr(settings, "APP_VERSION", "3.1.0"),
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
        "hashed_password": get_password_hash("test123"),  # Properly hashed password
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
        "message": getattr(settings, "APP_NAME", "DeepGuard"),
        "status": "healthy",
        "version": getattr(settings, "APP_VERSION", "3.1.0"),
    }


@app.get(f"{API_PREFIX}/health", tags=["Health"])
async def health():
    return {"status": "healthy", "message": "AI system operational"}

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
            "method": harassment.get("method", "hybrid"),
            "categories": harassment.get("categories", []),
        },
    }

# ==========================================================
# 🎭 Deepfake Detection
# ==========================================================
@app.post(f"{API_PREFIX}/scan_image", tags=["Analysis"])
async def scan_image(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        result = await detect_deepfake(contents)
    except Exception as e:
        logger.error(f"Deepfake detection error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Deepfake service error")

    deepfake = result.get("deepfake", {})
    prediction = deepfake.get("prediction", "unknown")
    score = deepfake.get("score", 0.0)

    analytics_db["total_scans"] += 1
    if prediction.lower() == "deepfake":
        analytics_db["threats_detected"] += 1

    logger.info(f"Image scanned — Prediction={prediction} (score={score:.2f})")
    return {"success": True, "data": {"prediction": prediction, "confidence": score}}

# ==========================================================
# 📱 Mobile Notification Analysis
# ==========================================================
@app.post(f"{API_PREFIX}/mobile/analyze-notification", tags=["Mobile"])
async def analyze_notification(p: NotificationPayload):
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
    print("🤖 DeepGuard v3.1 - AI-Based Cyber Harassment & Deepfake Detection")
    print("=" * 60)
    print(f"Running on http://{host}:{port}")
    print(f"Docs → http://localhost:{port}/docs")
    print("=" * 60 + "\n")

    uvicorn.run("main:app", host=host, port=port, reload=False)
