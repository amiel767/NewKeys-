"""Publish the piano captures and demo to the served directory.

Images are halved (the captures render at 440 dpi, so half is still 1:1 on a 2x
display); the audio is already MP3.
"""
import os
import shutil
import subprocess
import sys

SHOTS = "app/src/test/screenshots"
PUB = "/workspace/dl/pub/piano"
os.makedirs(PUB, exist_ok=True)

PAIRS = [
    ("ae_piano_window.png", "fenetre_studio.jpg"),
    ("ae_piano_felt.png", "fenetre_felt.jpg"),
    ("ae_piano_bright.png", "fenetre_bright.jpg"),
    ("ae_piano_electric.png", "fenetre_electric.jpg"),
    ("ae_piano_cards.png", "carte_instruments.jpg"),
]

for src, dst in PAIRS:
    s = os.path.join(SHOTS, src)
    d = os.path.join(PUB, dst)
    subprocess.run([sys.executable, "tools/aetherfx/reduce.py", s, d], check=True)

shutil.copy("/tmp/demo/piano.mp3", os.path.join(PUB, "can_you_feel_the_love_tonight_9_pianos.mp3"))
print("MP3 %.0f Ko" % (os.path.getsize(os.path.join(PUB, "can_you_feel_the_love_tonight_9_pianos.mp3")) / 1024))
print("total dossier %.0f Ko" % (sum(
    os.path.getsize(os.path.join(PUB, f)) for f in os.listdir(PUB)) / 1024))
