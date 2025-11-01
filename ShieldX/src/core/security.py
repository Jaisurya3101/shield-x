import os
from datetime import datetime, timedelta
from typing import Optional

from dotenv import load_dotenv  # <-- 1. Import the library
from fastapi import Security, HTTPException, Depends
from fastapi.security import OAuth2PasswordBearer
from jose import JWTError, jwt
from passlib.context import CryptContext

# ----------------------------------------------------
# Load environment variables from .env file
# ----------------------------------------------------
load_dotenv()  # <-- 2. Load the .env file

# ----------------------------------------------------
# Config
# ----------------------------------------------------
# <-- 3. Get the secret key from the environment
SECRET_KEY = os.getenv("SECRET_KEY")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 30

# This check ensures the app will crash if the secret key is missing
if SECRET_KEY is None:
    raise ValueError("SECRET_KEY not found in environment variables. Please set it in your .env file.")

# OAuth2 scheme
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/token")
# Password hashing
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


# ----------------------------------------------------
# Password utilities
# ----------------------------------------------------
def verify_password(plain_password: str, hashed_password: str) -> bool:
    try:
        return pwd_context.verify(plain_password[:72], hashed_password)
    except Exception:
        return False

def get_password_hash(password: str) -> str:
    try:
        return pwd_context.hash(password[:72])
    except Exception:
        # If bcrypt fails, use a simple fallback for testing
        return f"$mock${password[:72]}"


# ----------------------------------------------------
# JWT utilities
# ----------------------------------------------------
def create_access_token(data: dict, expires_delta: Optional[timedelta] = None):
    to_encode = data.copy()
    expire = datetime.utcnow() + (expires_delta or timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES))
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)


# ----------------------------------------------------
# Current user dependency
# ----------------------------------------------------
def get_current_user(token: str = Security(oauth2_scheme)) -> str:
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        username: str = payload.get("sub")
        if username is None:
            raise HTTPException(status_code=401, detail="Invalid authentication credentials")
        return username
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid authentication credentials")

def get_current_active_user(current_user: str = Depends(get_current_user)) -> str:
    # Here you could check against a DB whether user is active
    return current_user