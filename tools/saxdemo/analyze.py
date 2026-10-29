#!/usr/bin/env python3
"""Pitch and brightness of the release, on the real render.

Pitch is tracked with a phase-vocoder-ish approach: parabolic interpolation of the
spectral peak around the strongest partial *near the expected f0*, not the global max,
because a reed tone's strongest partial is usually h2 and the subharmonic trap makes
autocorrelation useless here.
"""
import sys, pathlib, struct, numpy as np

def read_wav(p):
    b = pathlib.Path(p).read_bytes()
    pos, fmt, data = 12, None, None
    while pos + 8 <= len(b):
        cid, size = b[pos:pos+4], struct.unpack("<I", b[pos+4:pos+8])[0]
        body = b[pos+8:pos+8+size]
        if cid == b"fmt ": fmt = struct.unpack("<HHIIHH", body[:16])
        elif cid == b"data": data = body
        pos += 8 + size + (size & 1)
    sr = fmt[2]
    a = np.frombuffer(data, dtype="<f4").reshape(-1, 2)
    return a[:, 0], sr

def frame_pitch(x, sr, n=4096):
    w = np.hanning(n)
    X = np.abs(np.fft.rfft(x * w))
    f = np.fft.rfftfreq(n, 1/sr)
    # restrict to the 150..1500 Hz band, find the peak, then refine parabolically
    m = (f > 150) & (f < 1500)
    i = np.argmax(X[m])
    idx = np.nonzero(m)[0][i]
    if 0 < idx < len(X)-1:
        a, b_, c = X[idx-1], X[idx], X[idx+1]
        d = 0.5*(a-c)/(a-2*b_+c+1e-12)
    else:
        d = 0
    return f[idx] + d*(f[1]-f[0]), X[idx]

def centroid(x, sr, n=4096):
    w = np.hanning(n)
    X = np.abs(np.fft.rfft(x*w))
    f = np.fft.rfftfreq(n, 1/sr)
    m = (f > 100) & (f < 8000)
    return float(np.sum(f[m]*X[m])/np.sum(X[m]))

def main():
    for tag in sys.argv[1:]:
        x, sr = read_wav(tag)
        # release starts at 0.8 s (the render holds 0.8 s then releases)
        rel = int(0.8*sr)
        print(f"\n{pathlib.Path(tag).name}")
        n = 4096
        hop = 1024
        ref = None
        for a in range(rel, min(rel+int(0.9*sr), len(x)-n), hop*6):
            seg = x[a:a+n]
            rms = float(np.sqrt(np.mean(seg**2)))
            f0, _ = frame_pitch(seg, sr)
            c = centroid(seg, sr)
            if ref is None: ref = f0
            st = 12*np.log2(f0/ref)
            print(f"   t={(a-rel)/sr*1000:5.0f} ms  rms {rms:.4f}  pic {f0:6.1f} Hz  {st:+5.2f} demi-tons  centroide {c:5.0f} Hz")

if __name__ == "__main__":
    main()
