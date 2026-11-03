"""Construit un pack PIAP depuis un SF2 a DEUX couches de velocite.

`build_sf2.py` suppose 7 plages de velocite (la banque E.Piano) et fusionne vers
4 couches.  Le SF2 « ED DX7 ROMANCE TINES » n'en a que 2 (0-80 et 81-127) : le
filtre LAYER_OF_VEL y rejette la couche haute et le build sort 0 region.  Ici on
garde les 2 couches telles quelles ; le moteur lit `layerCount_` du pack, donc
2 couches suffisent.

Reutilise les fonctions de build_sf2.py (import par chemin, le main est garde).

Usage : python3 build_dxrom.py --src /tmp/dxrom_out --out /tmp/dxrom_pack
"""
import argparse
import importlib.util
import json
import os
import struct
import sys

import numpy as np
import soundfile as sf

_HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("build_sf2", os.path.join(_HERE, "build_sf2.py"))
b = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(b)

RATE = b.RATE
TAIL_S = b.TAIL_S

# Deux plages -> deux couches, dans l'ordre du SF2.
LAYER_OF_VEL_2 = [(0, 80, 0), (81, 127, 1)]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--src', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--roots', default='')
    ap.add_argument('--max-seconds', type=float, default=2.5)
    ap.add_argument('--fade-ms', type=float, default=250.0)
    ap.add_argument('--tail-seconds', type=float, default=None)
    args = ap.parse_args()

    mapping = json.load(open(os.path.join(args.src, 'mapping.json')))
    pairs = {}
    for z in mapping:
        if z['vel_lo'] == 0 and z['vel_hi'] == 127:
            continue
        layer = next((L for lo, hi, L in LAYER_OF_VEL_2
                      if z['vel_lo'] >= lo and z['vel_hi'] <= hi), None)
        if layer is None:
            continue
        pairs.setdefault((z['root'], layer), []).append(z)

    want = {int(x) for x in args.roots.split(',') if x} if args.roots else None
    blob = bytearray()
    recs = bytearray()
    index = []
    for (root, layer) in sorted(pairs):
        if want and root not in want:
            continue
        zs = pairs[(root, layer)]
        lefts = [z for z in zs if z['pan'] < 0]
        rights = [z for z in zs if z['pan'] >= 0]
        if not lefts or not rights:
            continue
        l, slr = sf.read(os.path.join(args.src, lefts[0]['file']))
        r, srr = sf.read(os.path.join(args.src, rights[0]['file']))
        if l.ndim > 1:
            l = l.mean(axis=1)
        if r.ndim > 1:
            r = r.mean(axis=1)
        st, meta = b.build_region(l.astype(np.float64), r.astype(np.float64), slr, root,
                                  args.max_seconds, args.fade_ms, args.tail_seconds)
        packed = b.adpcm_encode(st)
        meta['offset'] = len(blob)
        meta['layer'] = layer
        meta['frames'] = meta['n']
        meta['tune_cents'] = round(lefts[0]['fine'] + lefts[0]['coarse'] * 100.0, 2)
        blob += packed
        rec = bytearray(24)
        rec[0] = root
        rec[1] = layer
        rec[2] = 127
        struct.pack_into('<I', rec, 4, meta['offset'])
        struct.pack_into('<I', rec, 8, meta['frames'])
        struct.pack_into('<I', rec, 12, meta['loop_start'])
        struct.pack_into('<I', rec, 16, meta['loop_end'])
        struct.pack_into('<f', rec, 20, meta['tune_cents'])
        recs += rec
        index.append(meta)

    out = bytearray(28)
    out[0:4] = b'PIAP'
    struct.pack_into('<I', out, 4, 1)
    struct.pack_into('<I', out, 8, RATE)
    struct.pack_into('<I', out, 12, len(index))
    struct.pack_into('<I', out, 16, len(blob))
    struct.pack_into('<I', out, 20, len(recs))
    out += recs + blob

    os.makedirs(os.path.dirname(args.out) or '.', exist_ok=True)
    with open(args.out + '.bin', 'wb') as fh:
        fh.write(out)
    json.dump({'rate': RATE, 'regions': index}, open(args.out + '.json', 'w'), indent=1)
    dur = sum(m['n'] for m in index) / RATE
    print(f"{len(index)} regions | {len(out)/1048576:.2f} Mo | duree totale {dur:.1f} s "
          f"| {dur/len(index):.2f} s/region | taux {RATE} Hz")


if __name__ == '__main__':
    main()
