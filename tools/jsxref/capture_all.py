#!/usr/bin/env python3
"""One bounded pass: start the Robolectric render loop, capture every page/popup,
stop the loop. Kept in one process so the Gradle daemon is only paid for once
(restarting it repeatedly is what exhausted memory in earlier sessions).

Usage: python3 tools/jsxref/capture_all.py [outdir]
"""
import json
import os
import pathlib
import shutil
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[2]
DIR = pathlib.Path("/tmp/liveui")
CMD, IMG, STATUS = DIR / "cmd.json", DIR / "screen.png", DIR / "status.txt"

# (name, spec) — each spec is one frame of the real Compose UI.
SHOTS = [
    ("01_mixer",        {"tracks": 4, "page": "MIXER", "keys": []}),
    ("02_mixer_chord",  {"page": "MIXER", "keys": ["C4", "E4", "G4"]}),
    ("03_mixer_long_chord", {"page": "MIXER", "keys": ["C4", "E4", "G4", "B4"]}),
    ("04_mixer_sustain", {"page": "MIXER", "keys": ["C4"], "sustain": True}),
    ("05_drumpad",      {"page": "DRUMPAD", "keys": []}),
    ("06_settings",     {"page": "SETTINGS", "keys": []}),
    ("07_setlists",     {"page": "SETLISTS", "keys": []}),
    ("08_sheets",       {"page": "SHEETS", "keys": []}),
    ("09_projects",     {"page": "PROJECTS", "keys": []}),
    ("10_popup_soundfont", {"page": "MIXER", "popup": "SOUNDFONT"}),
    ("11_popup_scene",  {"popup": "SCENE"}),
    ("12_popup_loops",  {"popup": "LOOPS"}),
    ("13_popup_track_settings", {"popup": "TRACK_SETTINGS"}),
    ("14_popup_effects", {"popup": "EFFECTS"}),
    ("15_popup_midi",   {"popup": "MIDI"}),
    ("16_popup_velocity", {"popup": "VELOCITY_SETTINGS"}),
]


def drive(seq, spec, out, timeout=45.0):
    spec = {"seq": seq, **spec}
    CMD.write_text(json.dumps(spec))
    time.sleep(1.6)
    t0 = time.time()
    while time.time() - t0 < timeout:
        if IMG.exists() and STATUS.exists() and STATUS.read_text().startswith("ok "):
            shutil.copy(IMG, out)
            return True
        time.sleep(0.1)
    return False


def main():
    outdir = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ROOT / "tools/jsxref/shots")
    outdir.mkdir(parents=True, exist_ok=True)
    DIR.mkdir(parents=True, exist_ok=True)
    STATUS.unlink(missing_ok=True)

    env = dict(os.environ)
    env.setdefault("JAVA_HOME", "/workspace/jdk/jdk21")
    env.setdefault("ANDROID_HOME", "/workspace/android-sdk")
    log = open("/tmp/capture_all.log", "w")
    loop = subprocess.Popen(["bash", "tools/liveui/loop.sh"], cwd=ROOT,
                            env=env, stdout=log, stderr=subprocess.STDOUT)
    print(f"loop pid {loop.pid}; waiting for first frame…")
    t0 = time.time()
    while time.time() - t0 < 300:
        if IMG.exists() and time.time() - IMG.stat().st_mtime < 30:
            break
        if loop.poll() is not None:
            print("loop exited early; see /tmp/capture_all.log")
            return 1
        time.sleep(2)
    else:
        print("no frame within 300s")
        loop.terminate()
        return 1

    ok = 0
    for i, (name, spec) in enumerate(SHOTS):
        out = outdir / f"{name}.png"
        got = drive(1000 + i, spec, out)
        print(("ok  " if got else "FAIL") + f" {name}")
        ok += got

    # Stop the loop immediately: leaving it running is what caused the crashes.
    loop.terminate()
    try:
        loop.wait(timeout=20)
    except subprocess.TimeoutExpired:
        loop.kill()
    log.close()
    print(f"captured {ok}/{len(SHOTS)}; loop stopped")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
