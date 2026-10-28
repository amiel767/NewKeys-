"""Prepare UI image assets: right-size, encode, verify.

A UI background never needs more pixels than it displays. Oversizing wastes
bytes and -- because the preview's WebGL uses LINEAR filtering with no
mipmaps -- adds aliasing instead of sharpness. So: resample once, at high
quality, to the largest size the UI can actually show, then encode with a
modern codec at the lowest quality that keeps SSIM >= target.
"""
import io
import numpy as np
from PIL import Image, ImageFilter
from skimage.metrics import structural_similarity as ssim


def prep(src, width, sharpen=0.0):
    im = Image.open(src).convert('RGB')
    h = max(1, round(width * im.size[1] / im.size[0]))
    im = im.resize((width, h), Image.LANCZOS)
    if sharpen > 0:
        im = im.filter(ImageFilter.UnsharpMask(radius=0.8, percent=int(sharpen * 100), threshold=2))
    return im


def encode(im, fmt, q):
    b = io.BytesIO()
    if fmt == 'webp':
        im.save(b, 'WEBP', quality=q, method=6)
    else:
        im.save(b, 'AVIF', quality=q, speed=6)
    return b.getvalue()


def _gray(raw, size):
    return np.asarray(Image.open(io.BytesIO(raw)).convert('L').resize(size, Image.LANCZOS), float)


def reference(src, display_w):
    """The ideal: full-resolution source shown at the display size."""
    im = Image.open(src).convert('RGB')
    h = max(1, round(display_w * im.size[1] / im.size[0]))
    return np.asarray(im.convert('L').resize((display_w, h), Image.LANCZOS), float)


def pick(im, fmt, ref_gray, target=0.985, lo=40, hi=96):
    """Lowest quality whose SSIM at display size still meets the target."""
    size = ref_gray.shape[::-1]
    s = 0.0
    for q in range(lo, hi + 1, 2):
        raw = encode(im, fmt, q)
        s = ssim(ref_gray, _gray(raw, size), data_range=255)
        if s >= target:
            return q, raw, s
    raw = encode(im, fmt, hi)
    return hi, raw, ssim(ref_gray, _gray(raw, size), data_range=255)
