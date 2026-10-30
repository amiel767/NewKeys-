"""Export the Sunday Keys reference screenshots as light JPEGs for the report page.

Maps each downloaded capture to the short name used by the HTML report so the
gallery can load them lazily, and keeps the total payload small (JPEG q78).
"""
import os

from PIL import Image

SRC = "/workspace/dl/sk/app"
OUT = "/workspace/dl/pub"

MAP = {
    "effects_8": "sk_chrous",
    "effects_9": "sk_delay",
    "effects_17": "sk_spaceverb",
    "effects_12": "sk_overdrive",
    "effects_7": "sk_bitcrusher",
    "effects_18": "sk_tremolo",
    "effects_14": "sk_pureverb",
    "effects_3": "sk_arp",
    "effects_4": "sk_easychord",
    "effects_6": "sk_sequencer",
    "effects_5": "sk_octave",
    "effects_10": "sk_flanger",
    "effects_13": "sk_pitch",
    "effects_15": "sk_rotor",
    "effects_11": "sk_gain",
}

total = 0
for src, dst in MAP.items():
    p = os.path.join(SRC, src + ".png")
    im = Image.open(p)
    im.load()
    if im.width > 1400:
        r = 1400 / im.width
        im = im.resize((1400, int(im.height * r)), Image.LANCZOS)
    out = os.path.join(OUT, dst + ".jpg")
    im.convert("RGB").save(out, quality=78, optimize=True)
    total += os.path.getsize(out)

print(f"{len(MAP)} files, {total // 1024} KB total")
