from pydantic_settings import BaseSettings
from typing import List

class Settings(BaseSettings):
    """
    Centralized application settings. Pydantic will automatically
    load values from a .env file to override these defaults.
    """
    # App Info
    app_name: str = "DeepGuard v3.1"
    app_version: str = "3.1.0"
    app_description: str = "AI-Based Cyber Harassment and Deepfake Detection App"

    # API Settings
    API_PREFIX: str = "/api/v1"
    HOST: str = "0.0.0.0"
    PORT: int = 8000
    DEBUG: bool = False
    WORKERS: int = 4

    # Security
    SECRET_KEY: str
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    API_USERNAME: str
    API_PASSWORD: str

    # CORS
    ALLOWED_ORIGINS: List[str] = ["http://localhost:3000"]

    # Database
    mongodb_url: str = "mongodb://localhost:27017"
    database_name: str = "deepguard"

    # Firebase
    firebase_project_id: str = "deepguard-dev"
    firebase_storage_bucket: str = "deepguard-dev.appspot.com"
    firebase_credentials_path: str = "firebase-service-account.json"

    # File Upload Settings
    max_file_size_mb: int = 50
    allowed_image_types: List[str] = ["jpg", "jpeg", "png", "webp"]
    allowed_video_types: List[str] = ["mp4", "avi", "mov", "mkv"]
    temp_folder: str = "temp"

    # Model Settings
    DEEPFAKE_MODEL_PATH: str = "models/deepfake.pt"
    HARASSMENT_MODEL_PATH: str = "models/harassment.pt"

    # Redis Settings
    redis_url: str = "redis://localhost:6379/0"
    rate_limit_requests: int = 100
    rate_limit_window: int = 3600  # 1 hour in seconds

    # Logging
    log_level: str = "INFO"
    log_file: str = "logs/deepguard.log"
    log_max_size: int = 10485760  # 10MB
    log_backup_count: int = 5

    class Config:
        env_file = ".env"
        case_sensitive = False

# Create a single, importable instance of the Settings class
settings = Settings()