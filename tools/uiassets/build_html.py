"""Rebuild the preview HTML with the optimised assets inlined.

Replaces every embedded image with its AVIF counterpart from the UI kit.
Piano slots map by name; the file stays a single self-contained HTML.
"""
import base64
import os
import re
import sys

SRC = sys.argv[1] if len(sys.argv) > 1 else '/tmp/preview.html'
OUT = sys.argv[2] if len(sys.argv) > 2 else '/workspace/dl/uikit.html'
KIT = '/workspace/dl/uikit'

# HTML slot -> UI kit asset
MAP = {
    'felt': 'piano_felt',
    'studio': 'piano_studio',
    'bright': 'piano_bright',
    'upright': 'piano_upright',
    'honky': 'piano_honky',
    'dark': 'piano_dark',
    'mellow': 'piano_mellow',
    'lady': 'piano_lady',
    'electric': 'piano_electric',
}

h = open(SRC, encoding='utf-8').read()
total = 0
for slot, asset in MAP.items():
    p = f'{KIT}/{asset}_750.avif'
    if not os.path.exists(p):
        print(f'  {slot:9} MANQUANT {p}')
        continue
    b64 = base64.b64encode(open(p, 'rb').read()).decode()
    pat = re.compile(r'("' + slot + r'":\s*")data:image/[a-z+]+;base64,[A-Za-z0-9+/=]+(")')
    n = len(pat.findall(h))
    h = pat.sub(lambda m: m.group(1) + 'data:image/avif;base64,' + b64 + m.group(2), h, count=1)
    total += os.path.getsize(p)
    print(f'  {slot:9} <- {asset}_750.avif  {os.path.getsize(p) / 1024:5.0f} Ko  ({n} slot)')

open(OUT, 'w', encoding='utf-8').write(h)
print(f'\n{OUT} : {os.path.getsize(OUT) / 1048576:.2f} Mo  (images {total / 1048576:.2f} Mo)')
