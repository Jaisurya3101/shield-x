from fastapi import APIRouter, Depends, HTTPException, File, UploadFile
from fastapi.security import OAuth2PasswordRequestForm
from typing import Dict, List

# --- Schema Imports ---
from .schemas import (
    DeepfakeRequest,
    HarassmentRequest,
    AnalysisResponse,
    BatchAnalysisRequest,
    HealthCheckResponse
)

# --- Service and Security Imports ---
from src.services.detection import detect_deepfake, detect_harassment
from src.core.security import (
    get_current_user,
    verify_password,
    create_access_token,
    get_password_hash
)
from src.utils.logging import logger

# --- Router Setup ---
router = APIRouter()


# --- Fake User Database for Development ---
fake_users_db = {
    "johndoe": {
        "username": "johndoe",
        "full_name": "John Doe",
        "email": "johndoe@example.com",
        "hashed_password": get_password_hash("secret"),
        "disabled": False,
    }
}


# --- Authentication Endpoint ---
@router.post("/token", response_model=Dict[str, str], tags=["Authentication"])
async def login_for_access_token(form_data: OAuth2PasswordRequestForm = Depends()):
    """Handles user login and returns an access token."""
    user = fake_users_db.get(form_data.username)
    if not user or not verify_password(form_data.password, user["hashed_password"]):
        raise HTTPException(
            status_code=401,
            detail="Incorrect username or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    access_token = create_access_token(data={"sub": user["username"]})
    return {"access_token": access_token, "token_type": "bearer"}


# --- Application Endpoints ---
@router.get("/health", response_model=HealthCheckResponse, tags=["Health"])
async def health_check():
    """Checks if the API is operational."""
    return HealthCheckResponse(status="healthy")


@router.post("/analyze/deepfake", response_model=AnalysisResponse, tags=["Analysis"])
async def analyze_deepfake(
    request: DeepfakeRequest,
    current_user: str = Depends(get_current_user)
):
    """Analyzes a media file for potential deepfake content."""
    try:
        logger.info("Processing deepfake analysis request")
        result = await detect_deepfake(request.file)
        return AnalysisResponse(
            success=True,
            result=result,
            message="Analysis completed successfully"
        )
    except Exception as e:
        logger.error(f"Deepfake analysis failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/analyze/harassment", response_model=AnalysisResponse, tags=["Analysis"])
async def analyze_harassment(
    request: HarassmentRequest,
    current_user: str = Depends(get_current_user)
):
    """Analyzes text for harassment content."""
    try:
        logger.info("Processing harassment analysis request")
        result = await detect_harassment(request.text)
        return AnalysisResponse(
            success=True,
            result=result,
            message="Analysis completed successfully"
        )
    except Exception as e:
        logger.error(f"Harassment analysis failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/analyze/batch", response_model=List[AnalysisResponse], tags=["Analysis"])
async def analyze_batch(
    request: BatchAnalysisRequest,
    current_user: str = Depends(get_current_user)
):
    """Processes multiple items for analysis in a single batch."""
    try:
        results = []
        for item in request.items:
            if item.type == "deepfake":
                result = await detect_deepfake(item.content)
            elif item.type == "harassment":
                result = await detect_harassment(item.content)
            else:
                raise HTTPException(
                    status_code=400,
                    detail=f"Unsupported analysis type: {item.type}"
                )
            results.append(AnalysisResponse(
                success=True,
                result=result,
                message=f"{item.type} analysis completed"
            ))
        return results
    except Exception as e:
        logger.error(f"Batch analysis failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/upload", response_model=AnalysisResponse, tags=["Analysis"])
async def upload_file(
    file: UploadFile = File(...),
    analysis_type: str | None = "deepfake",
    current_user: str = Depends(get_current_user)
):
    """Uploads and analyzes a file directly."""
    try:
        contents = await file.read()
        if analysis_type == "deepfake":
            result = await detect_deepfake(contents)
        else:
            raise HTTPException(
                status_code=400,
                detail=f"Unsupported analysis type: {analysis_type}"
            )

        return AnalysisResponse(
            success=True,
            result=result,
            message="File analysis completed successfully"
        )
    except Exception as e:
        logger.error(f"File upload and analysis failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))