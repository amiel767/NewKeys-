#!/usr/bin/env python3
"""Drives the LiveUI render loop: navigate/click, wait for the new frame, copy it.

The bridge writes /tmp/liveui/screen.png; this helper pushes a cmd.json and waits
for status.txt to advance, so callers get the frame for the state they asked for.
"""
import json
import shutil
import sys
import time
import pathlib

DIR = pathlib.Path("/tmp/liveui")
CMD = DIR / "cmd.json"
IMG = DIR / "screen.png"
STATUS = DIR / "status.txt"


def status():
    try:
        return STATUS.read_text().strip()
    except Exception:
        return ""


def drive(cmd, out, timeout=40.0):
    prev = status()
    base = int(prev.split()[1]) if prev.startswith("ok ") else 0
    CMD.write_text(json.dumps(cmd))
    t0 = time.time()
    while time.time() - t0 < timeout:
        s = status()
        if s.startswith("ok ") and int(s.split()[1]) > base:
            time.sleep(0.35)
            shutil.copy(IMG, out)
            return True
        time.sleep(0.1)
    return False


if __name__ == "__main__":
    spec = json.loads(sys.argv[1])
    out = sys.argv[2]
    ok = drive(spec, out)
    print("ok" if ok else "TIMEOUT", out)
