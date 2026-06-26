from pathlib import Path

import cv2
import numpy as np
from PIL import Image


SOURCE = Path("tmp/mascot-source.png")
OUTPUT = Path("output/mascot-chroma-key-green.png")


def fill_holes(mask: np.ndarray) -> np.ndarray:
    flood = mask.copy()
    h, w = mask.shape
    canvas = np.zeros((h + 2, w + 2), np.uint8)
    cv2.floodFill(flood, canvas, (0, 0), 255)
    return mask | cv2.bitwise_not(flood)


image = cv2.imread(str(SOURCE), cv2.IMREAD_COLOR)
if image is None:
    raise FileNotFoundError(SOURCE)

h, w = image.shape[:2]
hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)
saturation = hsv[:, :, 1]
value = hsv[:, :, 2]

# Colored, dark, and outlined pixels are reliable foreground evidence.
strong_fg = ((saturation > 42) | (value < 205)).astype(np.uint8) * 255

# Preserve the mascot's bright white interior by closing and filling the
# outlined central subject. Disconnected cyan effects remain in strong_fg.
subject_seed = np.zeros((h, w), np.uint8)
subject_seed[5 : h - 8, int(w * 0.18) : int(w * 0.72)] = strong_fg[
    5 : h - 8, int(w * 0.18) : int(w * 0.72)
]
subject_seed = cv2.morphologyEx(
    subject_seed,
    cv2.MORPH_CLOSE,
    cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (17, 17)),
    iterations=2,
)
subject_seed = fill_holes(subject_seed)

all_fg = cv2.bitwise_or(strong_fg, subject_seed)
near_fg = cv2.dilate(
    all_fg,
    cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (13, 13)),
    iterations=1,
)

# Initialize GrabCut from deterministic color and distance evidence.
mask = np.full((h, w), cv2.GC_PR_BGD, np.uint8)
neutral_bright = (saturation < 22) & (value > 238)
mask[neutral_bright & (near_fg == 0)] = cv2.GC_BGD
mask[near_fg > 0] = cv2.GC_PR_FGD
mask[all_fg > 0] = cv2.GC_FGD

# The outer corners are known background.
border = 4
mask[:border, :] = cv2.GC_BGD
mask[-border:, :] = cv2.GC_BGD
mask[:, :border] = cv2.GC_BGD
mask[:, -border:] = cv2.GC_BGD

background_model = np.zeros((1, 65), np.float64)
foreground_model = np.zeros((1, 65), np.float64)
cv2.grabCut(
    image,
    mask,
    None,
    background_model,
    foreground_model,
    6,
    cv2.GC_INIT_WITH_MASK,
)

binary = np.where(
    (mask == cv2.GC_FGD) | (mask == cv2.GC_PR_FGD), 255, 0
).astype(np.uint8)

# Keep all original colored/dark effect pixels and lightly feather only the
# cut edge. Foreground RGB pixels themselves are never repainted.
binary = cv2.bitwise_or(binary, strong_fg)
binary = cv2.morphologyEx(
    binary,
    cv2.MORPH_CLOSE,
    cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3)),
)
alpha = cv2.GaussianBlur(binary, (0, 0), 0.45).astype(np.float32) / 255.0
alpha[strong_fg > 0] = 1.0

foreground = image.astype(np.float32)
green = np.zeros_like(foreground)
green[:, :, 1] = 255
result = foreground * alpha[:, :, None] + green * (1.0 - alpha[:, :, None])

# Remove the original UI card border while keeping the central mascot/effects.
result[286:, :105] = green[286:, :105]
result[286:, 345:] = green[286:, 345:]
result[300:, :] = green[300:, :]

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
Image.fromarray(cv2.cvtColor(result.astype(np.uint8), cv2.COLOR_BGR2RGB)).save(
    OUTPUT
)
print(OUTPUT.resolve())
