#!/usr/bin/env python3
"""Bounded capture pass: renders the real Compose UI off-screen and writes one PNG
per page/popup, plus short GIFs of the animated controls.

Frames are half-resolution (891x411) so a full pass is quick and light. The driver
waits for the loop to acknowledge the exact sequence it sent, so a frame can never
be a leftover of the previous command.
"""
import json
import os
import pathlib
import shutil
import subprocess
import sys
import time

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
DIR = pathlib.Path("/tmp/liveui")
CMD, IMG, STATUS = DIR / "cmd.json", DIR / "screen.png", DIR / "status.txt"
FRAME_W = 891  # half of the 1782 native width

# (name, spec) — one frame of the real Compose UI. Setlists is deliberately absent.
SHOTS = [
    ("01_mixer",              {"tracks": 4, "page": "MIXER", "keys": []}),
    ("02_mixer_chord",        {"page": "MIXER", "keys": ["C4", "E4", "G4"]}),
    ("03_mixer_sustain",      {"page": "MIXER", "keys": ["C4"], "sustain": True}),
    ("04_mixer_midi_pedal",   {"page": "MIXER", "keys": ["C4"], "midiPedal": True}),
    ("05_drumpad",            {"page": "DRUMPAD", "keys": []}),
    ("06_settings",           {"page": "SETTINGS", "keys": []}),
    ("07_sheets",             {"page": "SHEETS", "keys": []}),
    ("08_projects",           {"page": "PROJECTS", "keys": []}),
    ("09_vkeyboard_layer",    {"page": "MIXER", "keyboard": True, "layer": True, "keys": []}),
    ("10_popup_soundfont",    {"page": "MIXER", "popup": "SOUNDFONT"}),
    ("11_popup_scene",        {"page": "MIXER", "popup": "SCENE"}),
    ("12_popup_track_settings",        {"page": "MIXER", "popup": "TRACK_SETTINGS"}),
    ("13_popup_effects",        {"page": "MIXER", "popup": "EFFECTS"}),
    ("14_popup_midi",        {"page": "MIXER", "popup": "MIDI"}),
    ("15_popup_velocity",        {"page": "MIXER", "popup": "VELOCITY_SETTINGS"}),
]

# (name, base spec, animated spec, frames) — the GIFs touch the control being shown.
GIFS = [
    ("pedal_sustain", {"page": "MIXER"}, {"sustain": True}, 40),
    ("pedal_midi",    {"page": "MIXER"}, {"midiPedal": True}, 40),
]


def _status_seq():
    try:
        parts = STATUS.read_text().split()
    except OSError:
        return None, None
    if len(parts) >= 2 and parts[0] == "ok":
        try:
            return "ok", int(parts[1])
        except ValueError:
            return None, None
    return None, None


def drive(seq, spec, out, timeout=60.0):
    """Send one command, wait for its acknowledgement, then copy the frame."""
    IMG.unlink(missing_ok=True)
    STATUS.unlink(missing_ok=True)
    CMD.write_text(json.dumps({"seq": seq, **spec}))
    t0 = time.time()
    acked = False
    while time.time() - t0 < timeout:
        state, got = _status_seq()
        if state == "ok" and got == seq and IMG.exists():
            if acked:
                # Half-resolution on purpose: a full pass stays light and quick.
                im = Image.open(IMG).convert("RGB")
                im = im.resize((FRAME_W, int(im.height * FRAME_W / im.width)), Image.LANCZOS)
                im.save(out, optimize=True)
                return True
            acked = True
            # Entry animations (chord chip, pedal) need a few render passes to settle.
            time.sleep(0.5)
        time.sleep(0.1)
    return False


def capture_gif(name, base, animated, frames, outdir, seq0):
    """Toggle the animated field on, sample frames, then release it."""
    d = pathlib.Path("/tmp/liveui_gif")
    shutil.rmtree(d, ignore_errors=True)
    d.mkdir(parents=True, exist_ok=True)
    shots = []
    for i in range(frames):
        spec = dict(base)
        if i >= 4:
            spec.update(animated)
        p = d / f"f{i:03d}.png"
        if not drive(seq0 + i, spec, p):
            print(f"  frame {i} timed out")
            return False
        shots.append(p)
    off = dict(base)
    off[list(animated)[0]] = False
    drive(seq0 + frames, off, d / "release.png")
    ims = [Image.open(p).convert("RGB") for p in shots]
    ims = [im.resize((FRAME_W, int(im.height * FRAME_W / im.width)), Image.LANCZOS) for im in ims]
    ims = [im.quantize(colors=128, method=Image.MEDIANCUT, dither=Image.FLOYDSTEINBERG) for im in ims]
    out = outdir / f"{name}.gif"
    ims[0].save(out, save_all=True, append_images=ims[1:], duration=int(1000 / 15),
                loop=0, optimize=True, disposal=2)
    kb = out.stat().st_size // 1024
    print(f"ok   {name}.gif  ({kb} KB, {frames} frames)")
    return True


def main():
    outdir = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ROOT / "tools/jsxref/shots")
    outdir.mkdir(parents=True, exist_ok=True)
    DIR.mkdir(parents=True, exist_ok=True)
    STATUS.unlink(missing_ok=True)
    IMG.unlink(missing_ok=True)

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
    seq = 1000
    for name, spec in SHOTS:
        got = drive(seq, spec, outdir / f"{name}.png")
        print(("ok  " if got else "FAIL") + f" {name}")
        ok += got
        seq += 1

    for name, base, animated, frames in GIFS:
        if capture_gif(name, base, animated, frames, outdir, seq):
            ok += 1
        seq += frames + 2

    # Stop the loop immediately: leaving it running is what caused the crashes.
    loop.terminate()
    try:
        loop.wait(timeout=20)
    except subprocess.TimeoutExpired:
        loop.kill()
    log.close()
    total = len(SHOTS) + len(GIFS)
    print(f"captured {ok}/{total}; loop stopped")
    return 0 if ok == total else 1


if __name__ == "__main__":
    sys.exit(main())
