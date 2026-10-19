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
    """Push a command and return the frame that reflects it.

    status.txt advances on every render loop iteration, not only when a new
    command is applied, so waiting for one advance can capture a stale frame.
    Sleep past a few iterations after writing, then take the newest image.
    """
    CMD.write_text(json.dumps(cmd))
    time.sleep(1.6)
    t0 = time.time()
    while time.time() - t0 < timeout:
        if IMG.exists() and status().startswith("ok "):
            shutil.copy(IMG, out)
            return True
        time.sleep(0.1)
    return False


if __name__ == "__main__":
    spec = json.loads(sys.argv[1])
    out = sys.argv[2]
    ok = drive(spec, out)
    print("ok" if ok else "TIMEOUT", out)
