"""Resume les zones d'un SF2 : racines, plages de velocite, pan, durees.

Sert a decider la structure d'un pack AVANT d'extraire 400 Mo de WAV.  Un SF2
multi-couches se decrit par ses zones, pas par ses noms de fichiers.

Usage : python3 zones_sf2.py <fichier.sf2>
"""
import struct
import sys
from collections import defaultdict


def walk(data, off, end):
    """Parcourt un niveau RIFF.  Pour un LIST, l'identifiant rendu est son type
    ('pdta'), pas 'LIST' : c'est ce que portent les chunks de haut niveau."""
    out = []
    while off + 8 <= end:
        cid = data[off:off + 4]
        size = struct.unpack('<I', data[off + 4:off + 8])[0]
        start = off + 8
        if cid == b'LIST' and size >= 4:
            out.append((data[start:start + 4], start + 4, size - 4))
        else:
            out.append((cid, start, size))
        off += 8 + size + (size & 1)
    return out


def main(path):
    data = open(path, 'rb').read()
    top = walk(data, 12, len(data))
    # Le chunk pdta est le DERNIER : un 'pdta' peut apparaitre dans les donnees
    # audio par coincidence (constate sur ce fichier : 1,6 Go annonces).
    pdta = [c for c in top if c[0] == b'pdta'][-1]
    sub = {cid: (o, s) for cid, o, s in walk(data, pdta[1], pdta[1] + pdta[2])}

    def recs(tag, size):
        o, s = sub[tag]
        return [data[o + i * size:o + (i + 1) * size] for i in range(s // size)]

    def nm(b):
        return b[:20].split(b'\x00')[0].decode('latin1')

    phdr, inst = recs(b'phdr', 38), recs(b'inst', 22)
    shdr, ibag, igen = recs(b'shdr', 46), recs(b'ibag', 4), recs(b'igen', 4)
    pbag, pgen = recs(b'pbag', 4), recs(b'pgen', 4)

    for i in range(len(phdr) - 1):
        print(f"--- preset [{i}] {nm(phdr[i])!r} ---")
        pi = struct.unpack('<H', phdr[i][20:22])[0]
        # Les zones du PRESET vivent dans pbag/pgen (pas ibag/igen, qui sont les
        # zones globales d'instrument).  Lire les mauvaises tables ne remonte rien.
        ib = struct.unpack('<H', phdr[i][24:26])[0]
        ib2 = struct.unpack('<H', phdr[i + 1][24:26])[0]
        used = []
        for z in range(ib, ib2):
            gs = struct.unpack('<H', pbag[z][:2])[0]
            ge = struct.unpack('<H', pbag[z + 1][:2])[0]
            for k in range(gs, ge):
                op = pgen[k][0] | (pgen[k][1] << 8)
                if op == 41:  # instrument
                    used.append(pgen[k][2] | (pgen[k][3] << 8))
        if not used:
            print("    (aucun instrument reference)")
            continue
        for ii in sorted(set(used)):
            a = struct.unpack('<H', inst[ii][20:22])[0]
            b = struct.unpack('<H', inst[ii + 1][20:22])[0]
            roots = defaultdict(list)
            pans = set()
            dur = 0
            for z in range(a, b):
                gs = struct.unpack('<H', ibag[z][:2])[0]
                ge = struct.unpack('<H', ibag[z + 1][:2])[0]
                kr = vr = root = sid = None
                pan = 0
                for k in range(gs, ge):
                    op = igen[k][0] | (igen[k][1] << 8)
                    am = igen[k][2] | (igen[k][3] << 8)
                    if op == 43: kr = (am & 0xFF, am >> 8)
                    elif op == 44: vr = (am & 0xFF, am >> 8)
                    elif op == 58: root = am if am < 128 else am - 256
                    elif op == 53: sid = am
                    elif op == 17: pan = am if am < 32768 else am - 65536
                if sid is None: continue
                st, en = struct.unpack('<II', shdr[sid][20:28])
                dur += en - st
                pans.add(pan)
                if root is not None:
                    roots[root].append((kr, vr, sid, en - st))
            print(f"    inst [{ii}] {nm(inst[ii])!r} : {b - a} zones, "
                  f"{len(roots)} racines, pan {sorted(pans)[:3]}..., {dur / 44100:.0f} s")
            ks = sorted(roots)
            print(f"      racines {ks[0]}..{ks[-1]} : {ks}")
            # plages de velocite reellement utilisees
            vs = sorted({v[1] for z in roots.values() for v in z if v[1]})
            print(f"      plages de velocite : {vs}")


if __name__ == '__main__':
    main(sys.argv[1])
