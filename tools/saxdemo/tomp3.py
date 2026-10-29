#!/usr/bin/env python3
"""WAV float32 stereo -> MP3 stereo. lameenc writes VBR; the corpus stays small.

Stereo is not decoration here: SpaceReverb builds a true mid/side image, so a mono
export discards part of the voice.
"""
import sys, pathlib, struct, numpy as np, lameenc

def read_wav(p):
    """Minimal RIFF reader: Python's `wave` refuses IEEE-float WAV (format tag 3)."""
    b = pathlib.Path(p).read_bytes()
    if b[:4] != b"RIFF" or b[8:12] != b"WAVE":
        raise ValueError("not a WAV")
    pos, fmt, data = 12, None, None
    while pos + 8 <= len(b):
        cid, size = b[pos:pos + 4], struct.unpack("<I", b[pos + 4:pos + 8])[0]
        body = b[pos + 8:pos + 8 + size]
        if cid == b"fmt ":
            tag, ch, sr, _, _, bits = struct.unpack("<HHIIHH", body[:16])
            fmt = (tag, ch, sr, bits)
        elif cid == b"data":
            data = body
        pos += 8 + size + (size & 1)
    if fmt is None or data is None:
        raise ValueError("missing fmt/data chunk")
    tag, ch, sr, bits = fmt
    if ch != 2 or bits != 32 or tag != 3:
        raise ValueError(f"expected stereo float32, got tag={tag} ch={ch} bits={bits}")
    return np.frombuffer(data, dtype="<f4").reshape(-1, 2)

def write_mp3(a, out, kbps=128):
    # lameenc wants interleaved int16
    x = np.clip(a, -1.0, 1.0)
    i16 = (x * 32767.0).astype("<i2")
    enc = lameenc.Encoder()
    enc.set_bit_rate(kbps)
    enc.set_in_sample_rate(48000)
    enc.set_channels(2)
    enc.set_quality(2)
    data = enc.encode(i16.tobytes())
    data += enc.flush()
    pathlib.Path(out).write_bytes(bytes(data))

def main():
    src, dst = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    kbps = int(sys.argv[3]) if len(sys.argv) > 3 else 128
    a = read_wav(src)
    l, r = a[:, 0], a[:, 1]
    diff = float(np.sqrt(np.mean((l - r) ** 2)))
    corr = float(np.corrcoef(l, r)[0, 1])
    write_mp3(a, dst, kbps)
    size = dst.stat().st_size
    print(f"{dst.name:44s} {size/1024:6.1f} Ko  stereo  L-R rms {diff:.4f}  corr {corr:.4f}")

if __name__ == "__main__":
    main()
