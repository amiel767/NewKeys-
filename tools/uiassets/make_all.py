"""Build the full UI kit: 9 pianos + 6 strings, at the sizes the UI shows.

Sources: the 6 piano photographs, the 3 Qwen piano renders, the 5 Qwen
string renders and the Gemini string PAD. Two rungs only -- 750 px (phone
canvas 500 CSS x DPR 1.5) and 1500 px (desktop 2x). See uikit.py.
"""
import os
import sys

sys.path.insert(0, '/workspace/dl/tools')
from uikit import prep, reference, pick

OUT = '/workspace/dl/uikit'
os.makedirs(OUT, exist_ok=True)
P = '/workspace/dl/piano'

JOBS = {
    # 9 pianos: 6 originals + 3 Qwen renders
    'piano_felt': f'{P}/piano_02_felt.png',
    'piano_studio': f'{P}/piano_03_studio_bright.png',
    'piano_bright': f'{P}/piano_00_reference.png',
    'piano_upright': f'{P}/piano_01_upright.png',
    'piano_honky': f'{P}/piano_04_honky.png',
    'piano_dark': f'{P}/piano_05_dark.png',
    'piano_mellow': '/tmp/qwen/raw_01.png',
    'piano_lady': '/tmp/qwen/raw_02.png',
    'piano_electric': '/tmp/qwen/raw_03.png',
    # strings: 4 Qwen renders + the Gemini PAD. Order from the Qwen recap:
    # raw_07 Solo Violin+Viola, raw_08 Solo Cello+Bass, raw_09 Section,
    # raw_10 Orchestra, raw_11 Pizzicato, Gemini Pad.
    'strings_solo': '/tmp/qwen/raw_07.png',
    'strings_cello': '/tmp/qwen/raw_08.png',
    'strings_sec': '/tmp/qwen/raw_09.png',
    'strings_orch': '/tmp/qwen/raw_10.png',
    'strings_pizz': '/tmp/qwen/raw_11.png',
    'strings_pad': '/tmp/gemini_img_0.png',
}

if __name__ == '__main__':
    print(f"{'slot':18} {'W':>5} {'q':>3} {'Ko':>7} {'ssim':>7}")
    tot = 0
    for slot, src in JOBS.items():
        if not os.path.exists(src):
            print(f"{slot:18} SOURCE MANQUANTE {src}")
            continue
        for W in (750, 1500):
            im = prep(src, W)
            q, raw, s = pick(im, 'avif', reference(src, W), target=0.985)
            open(f'{OUT}/{slot}_{W}.avif', 'wb').write(raw)
            tot += len(raw)
            print(f"{slot:18} {W:5} {q:3} {len(raw)/1024:7.1f} {s:7.4f}")
    print(f"\ntotal {tot / 1048576:.2f} Mo")
