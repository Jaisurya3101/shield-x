from PIL import Image
import numpy as np

def preprocess_image(pil_image: Image.Image, size=(224, 224)):
    """Resize, normalize, and convert image to numpy array."""
    img = pil_image.resize(size)
    img_array = np.array(img) / 255.0
    return img_array

def preprocess_video_frame(frame: np.ndarray, size=(224, 224)):
    """Resize and normalize a video frame."""
    frame_resized = cv2.resize(frame, size)
    return frame_resized / 255.0