"""Decodeur NCW (Native Instruments Compressed Wave), variante non chiffree.

Le format n'est pas documente par Native Instruments ; la disposition ci-dessous
est mesuree sur des fichiers reels et croisee avec deux implementations
independantes :

  * ben-juodvalkis/Ableton-Device-Creator, `tools/ncw.py` (MIT)
  * git-moss/ConvertWithMoss, `file/ncw/NcwFile.java` (MIT)

La version precedente de ce fichier vivait dans `/tmp` et a ete perdue a chaque
wipe : elle est desormais versionnee ici, avec le reste des outils.

En-tete (120 octets, petit-boutiste) :

    0x00 u32 magic 0xD69EA801
    0x04 u32 version 0x0130 ou 0x0131
    0x08 u16 canaux
    0x0A u16 bits par echantillon (16, 24 ou 32)
    0x0C u32 frequence d'echantillonnage
    0x10 u32 nombre d'echantillons par canal
    0x14 u32 offset de la table de blocs
    0x18 u32 offset des donnees de blocs
    0x1C u32 taille des donnees de blocs
    0x20 88 octets de remplissage (ignores)

Table de blocs : un u32 par bloc, offset dans la zone de donnees, plus un
sentinelle finale. Un bloc porte `canaux` sous-blocs consecutifs, un par canal :

    0x00 u32 magic 0x3E9A0C16
    0x04 i32 valeur de base (premier echantillon du sous-bloc)
    0x08 i16 bits signes (voir ci-dessous)
    0x0A u16 drapeaux : 1 = mid/side, 2 = IEEE float
    0x0C 4 octets de remplissage
    0x10 512 * |bits| / 8 octets de donnees

`bits` choisit le mode : > 0 delta (chaque valeur est l'ecart au precedent),
< 0 troncature (valeurs absolues), 0 non compresse.

Un sous-bloc fait toujours 512 echantillons ; le dernier est complete de zeros.
Le decodage delta accumule 512 deltas, le premier echantillon valant `base`.

Pure stdlib.
"""

import struct
import wave
from pathlib import Path

MAGIC = 0xD69EA801
BLOCK_MAGIC = 0x3E9A0C16
VERSIONS = (0x0130, 0x0131)
HEADER_SIZE = 120
BLOCK_SAMPLES = 512
FLAG_MID_SIDE = 1
FLAG_IEEE_FLOAT = 2


class NcwError(Exception):
    pass


def _read_packed(data, bits):
    """Entiers signes a `bits` largeur, empaquetes LSB d'abord."""
    out = []
    acc = 0
    nacc = 0
    mask = (1 << bits) - 1
    sign = 1 << (bits - 1)
    for byte in data:
        acc |= byte << nacc
        nacc += 8
        while nacc >= bits:
            v = acc & mask
            if v & sign:
                v -= 1 << bits
            out.append(v)
            acc >>= bits
            nacc -= bits
    return out


def _decode_delta(base, data, bits):
    deltas = _read_packed(data, bits)
    out = []
    cur = base
    for i in range(BLOCK_SAMPLES):
        out.append(cur)
        cur += deltas[i] if i < len(deltas) else 0
    return out


def _decode_truncated(data, bits):
    out = _read_packed(data, bits)
    out.extend([0] * (BLOCK_SAMPLES - len(out)))
    return out[:BLOCK_SAMPLES]


def read_ncw(path):
    """Renvoie (canaux, info) pour un fichier NCW."""
    return parse_ncw(Path(path).read_bytes(), str(path))


def decode_ncw_bytes(data):
    """Meme chose depuis un tampon, au format qu'attendait l'ancien outil piano :
    dict `data` (canaux), `bps`, `rate`, `channels`, `n`."""
    chans, info = parse_ncw(data)
    return dict(data=chans, bps=info["bit_depth"], rate=info["sample_rate"],
                channels=info["channels"], n=info["num_samples"])


def parse_ncw(data, path="<tampon>"):
    """Renvoie (canaux, info). `canaux` est une liste de listes d'entiers."""
    if len(data) < HEADER_SIZE:
        raise NcwError("%s : trop court pour etre un NCW" % path)
    magic, version = struct.unpack_from("<II", data, 0)
    if magic != MAGIC:
        raise NcwError("%s : magic %#010x, pas un NCW non chiffre" % (path, magic))
    if version not in VERSIONS:
        raise NcwError("%s : version %#06x inconnue" % (path, version))
    channels, bps = struct.unpack_from("<HH", data, 8)
    rate, n_samples, table_off, data_off, data_size = struct.unpack_from(
        "<5I", data, 12)
    if channels < 1:
        raise NcwError("%s : nombre de canaux invalide (%d)" % (path, channels))
    if bps not in (16, 24, 32):
        raise NcwError("%s : profondeur %d non geree" % (path, bps))

    n_blocks = (data_off - table_off) // 4
    offsets = struct.unpack_from("<%dI" % n_blocks, data, table_off)
    if offsets[0] != 0:
        raise NcwError("%s : le premier offset de bloc doit valoir 0" % path)

    chans = [[] for _ in range(channels)]
    is_mid_side = False
    for b in range(1, n_blocks):
        base = data_off + offsets[b - 1]
        end = data_off + offsets[b]
        pos = base
        for ch in range(channels):
            mg, base_val, bits, flags = struct.unpack_from("<IihH", data, pos)
            if mg != BLOCK_MAGIC:
                raise NcwError("%s : bloc %d canal %d, magic %#010x"
                               % (path, b - 1, ch, mg))
            if flags & FLAG_MID_SIDE:
                is_mid_side = True
            n_bytes = BLOCK_SAMPLES * abs(bits) // 8
            payload = data[pos + 16:pos + 16 + n_bytes]
            if bits > 0:
                samples = _decode_delta(base_val, payload, bits)
            elif bits < 0:
                samples = _decode_truncated(payload, -bits)
            else:
                step = bps // 8
                samples = [int.from_bytes(payload[i * step:(i + 1) * step],
                                          "little", signed=True)
                           for i in range(BLOCK_SAMPLES)]
            chans[ch].extend(samples)
            pos += 16 + n_bytes
        if pos != end:
            raise NcwError("%s : bloc %d mal dimensionne (%d != %d)"
                           % (path, b - 1, pos, end))

    for c in range(channels):
        del chans[c][n_samples:]
        if len(chans[c]) < n_samples:
            raise NcwError("%s : %d echantillons lus sur %d attendus (canal %d)"
                           % (path, len(chans[c]), n_samples, c))

    if is_mid_side:
        if channels != 2:
            raise NcwError("%s : mid/side sur %d canaux" % (path, channels))
        mid, side = chans[0], chans[1]
        chans[0] = [m + s for m, s in zip(mid, side)]
        chans[1] = [m - s for m, s in zip(mid, side)]

    info = dict(channels=channels, bit_depth=bps, sample_rate=rate,
                num_samples=n_samples, mid_side=is_mid_side,
                duration=n_samples / rate if rate else 0.0)
    return chans, info


def ncw_to_wav(src, dst):
    """Decode un NCW vers un WAV 16 bits. Renvoie l'info."""
    chans, info = read_ncw(src)
    shift = info["bit_depth"] - 16
    frames = bytearray()
    for i in range(len(chans[0])):
        for c in chans:
            v = c[i] >> shift if shift > 0 else c[i]
            v = -32768 if v < -32768 else (32767 if v > 32767 else v)
            frames += struct.pack("<h", v)
    dst = Path(dst)
    dst.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(dst), "wb") as w:
        w.setnchannels(info["channels"])
        w.setsampwidth(2)
        w.setframerate(info["sample_rate"])
        w.writeframes(bytes(frames))
    return info


if __name__ == "__main__":
    import sys

    for arg in sys.argv[1:]:
        src = Path(arg)
        print(src.name, ncw_to_wav(src, src.with_suffix(".wav")))
