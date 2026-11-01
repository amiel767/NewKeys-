"""Extrait un SF2 en WAV par note racine, en lisant le vrai mapping de zones.

Un SF2 ne dit pas « cet echantillon est un C4 » : il le dit par une zone
(`igen`) qui porte `overridingRootKey` (58) et `keyRange` (43).  Deviner la note
par le nom du fichier est faux des que la banque est multi-couches ou compressee.

Sortie : un WAV mono par (echantillon, couche de velocite), nomme
`<sample>_L<vel>.wav`, plus un `mapping.json` que le build relit.

Usage : python3 extract_sf2.py <fichier.sf2> <dossier_sortie>
"""
import json
import os
import struct
import sys

import numpy as np
import soundfile as sf

# Generateurs SF2 utilises ici (spec 2.04, section 8.1.2)
GEN_KEYRANGE = 43
GEN_VELRANGE = 44
GEN_ROOTKEY = 58
GEN_SAMPLEID = 53
GEN_FINETUNE = 52
GEN_COARSE = 51
GEN_SAMPLEMODES = 54
GEN_PAN = 17


def chunks(data, off, end, want, out):
    """Parcourt l'arbre RIFF et collecte les chunks `want`."""
    while off + 8 <= end:
        cid = data[off:off + 4]
        size = struct.unpack('<I', data[off + 4:off + 8])[0]
        start = off + 8
        if cid == b'LIST' and size > 4:
            chunks(data, start + 4, start + size, want, out)
        elif cid == want:
            out.append((start, size))
        off = start + size + (size & 1)
    return out


def parse(data):
    smpl = chunks(data, 12, len(data), b'smpl', [])[0]
    pcm = np.frombuffer(data[smpl[0]:smpl[0] + smpl[1]], dtype='<i2').astype(np.float32) / 32768.0

    shdr, shdr_len = chunks(data, 12, len(data), b'shdr', [])[0]
    n_sh = shdr_len // 46
    samples = []
    for i in range(n_sh):
        r = data[shdr + i * 46:shdr + i * 46 + 46]
        name = r[:20].split(b'\0')[0].decode('latin1')
        st, en, ls, le, rate = struct.unpack('<IIIII', r[20:40])
        pitch, adj = struct.unpack('<Bi', r[40:45])
        samples.append(dict(name=name, start=st, end=en, loop_start=ls, loop_end=le,
                            rate=rate, pitch=pitch, correction=adj))

    def recs(tag, size):
        off, length = chunks(data, 12, len(data), tag, [])[0]
        return [data[off + i * size:off + (i + 1) * size] for i in range(length // size)]


    phdr = recs(b'phdr', 38)
    pbag = recs(b'pbag', 4)
    pgen = recs(b'pgen', 4)
    inst = recs(b'inst', 22)
    ibag = recs(b'ibag', 4)
    igen = recs(b'igen', 4)

    def gen_pairs(rec):
        return [(struct.unpack('<H', rec[i:i + 2])[0], struct.unpack('<H', rec[i + 2:i + 4])[0])
                for i in range(0, len(rec), 4)]

    def zone_gens(rec):
        # `amount` est un octet signe quand l'operateur est un offset (keyRange),
        # mais un entier non signe pour un index d'echantillon.  On garde les deux.
        out = []
        for i in range(0, len(rec), 4):
            op = struct.unpack('<H', rec[i:i + 2])[0]
            amt = rec[i + 2] | (rec[i + 3] << 8)
            out.append((op, amt))
        return out

    # phdr : nom(20) + preset(2) + bank(2) + pbagIndex(2) + ... -> l'index est a 24,
    # pas a 20 (20/22 sont le numero de preset et la banque).
    presets = []
    for pi in range(len(phdr) - 1):
        name = phdr[pi][:20].split(b'\0')[0].decode('latin1')
        # Debut dans CET enregistrement, fin dans le SUIVANT (le terminal EOP) :
        # lire les deux dans le meme enregistrement donne ze = 0, donc zero zone.
        zs = struct.unpack('<H', phdr[pi][24:26])[0]
        ze = struct.unpack('<H', phdr[pi + 1][24:26])[0]
        pzones = []
        for zi in range(zs, ze):
            gs = struct.unpack('<H', pbag[zi][:2])[0]
            ge = struct.unpack('<H', pbag[zi + 1][:2])[0]
            gens = zone_gens(b''.join(pgen[gs:ge]))
            iinst = [a for o, a in gens if o == 41]
            if iinst:
                pzones.append((iinst[0], gens))
        presets.append((name, pzones))

    out = []
    for pname, pzones in presets:
        for iinst, _pgens in pzones:
            # inst : nom(20) + bagIndex(2).  La fin est l'index du terminal (dernier).
            izs = struct.unpack('<H', inst[iinst][20:22])[0]
            ize = struct.unpack('<H', inst[iinst + 1][20:22])[0]
            for zi in range(izs, ize):
                gs = struct.unpack('<H', ibag[zi][:2])[0]
                ge = struct.unpack('<H', ibag[zi + 1][:2])[0]
                gens = dict(zone_gens(b''.join(igen[gs:ge])))
                sid = gens.get(GEN_SAMPLEID, 0)
                if sid >= len(samples) - 1:
                    continue
                s = samples[sid]
                kr = gens.get(GEN_KEYRANGE, 0x7F00)
                vr = gens.get(GEN_VELRANGE, 0x7F00)
                root = gens.get(GEN_ROOTKEY, s['pitch'])
                fine = struct.unpack('<h', struct.pack('<H', gens.get(GEN_FINETUNE, 0)))[0]
                # coarseTune est un octet SIGNE : 255 vaut -1 (une octave plus bas).
                coarse = struct.unpack('<b', bytes([gens.get(GEN_COARSE, 0) & 0xFF]))[0]
                # Le pan separe les deux prises d'une meme couche : c'est ce qui
                # distingue la gauche de la droite d'une paire stereo.
                pan = struct.unpack('<h', struct.pack('<H', gens.get(GEN_PAN, 0)))[0]
                out.append(dict(preset=pname, sample=s['name'], sample_id=sid,
                                lo=kr & 0xFF, hi=(kr >> 8) & 0xFF,
                                vel_lo=vr & 0xFF, vel_hi=(vr >> 8) & 0xFF,
                                pan=pan,
                                root=root, fine=fine, coarse=coarse,
                                start=s['start'], end=s['end'],
                                loop_start=s['loop_start'], loop_end=s['loop_end'],
                                rate=s['rate']))
    return pcm, samples, out


def main():
    src, dst = sys.argv[1], sys.argv[2]
    os.makedirs(dst, exist_ok=True)
    data = open(src, 'rb').read()
    pcm, samples, zones = parse(data)
    print(f"echantillons {len(samples)} | zones {len(zones)} | PCM {len(pcm)/1048576:.1f} Mo")

    seen = {}
    mapping = []
    for z in zones:
        key = (z['sample_id'], z['vel_lo'], z['vel_hi'])
        if key in seen:
            continue
        seen[key] = True
        seg = pcm[z['start']:z['end']]
        if len(seg) < 2048:
            continue
        name = f"{z['sample']}_L{z['vel_lo']:03d}.wav"
        path = os.path.join(dst, name)
        if not os.path.exists(path):
            sf.write(path, seg, z['rate'], subtype='PCM_16')
        mapping.append(dict(file=name, root=z['root'], fine=z['fine'], coarse=z['coarse'],
                            pan=z['pan'],
                            lo=z['lo'], hi=z['hi'], vel_lo=z['vel_lo'], vel_hi=z['vel_hi'],
                            rate=z['rate'], n=int(len(seg)),
                            loop_start=int(z['loop_start']), loop_end=int(z['loop_end']),
                            preset=z['preset'], sample=z['sample']))

    json.dump(mapping, open(os.path.join(dst, 'mapping.json'), 'w'), indent=1)
    print(f"ecrit {len(mapping)} WAV dans {dst}")
    roots = sorted({m['root'] for m in mapping})
    print("notes racines:", roots)
    print("plages de velocite:", sorted({(m['vel_lo'], m['vel_hi']) for m in mapping}))


if __name__ == '__main__':
    main()
