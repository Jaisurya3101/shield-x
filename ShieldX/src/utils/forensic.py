# filepath: c:\deepguard\src\utils\forensic.py
import cv2
import numpy as np

def laplacian_variance(img_array: np.ndarray):
    return cv2.Laplacian(img_array, cv2.CV_64F).var()

def noise_std(img_array: np.ndarray):
    return np.std(img_array)

def high_freq_ratio(img_array: np.ndarray):
    fft = np.fft.fft2(img_array)
    fft_shift = np.fft.fftshift(fft)
    magnitude = np.abs(fft_shift)
    hf = magnitude[int(magnitude.shape[0]*0.25):, int(magnitude.shape[1]*0.25):]
    return hf.mean() / (magnitude.mean() + 1e-8)