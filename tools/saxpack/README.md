# saxpack — Æther Sax sample pack builder

Turns the **MTG Solo Saxophones** library (CC-BY 4.0) into the compact packs the
native engine reads: attack + sustain loop, 22.05 kHz mono, 4-bit IMA ADPCM.

## Build

```bash
python3 tools/saxpack/extract.py              # the four types, both velocity layers
python3 tools/saxpack/verify.py               # seam / SNR / key coverage report
```

Output lands in `tools/saxpack/out/` (gitignored — regenerable, and the source
library is 105 MB). Measured on the 2026-10-27 run:

| Type | Samples | MIDI | Size | Seam min / median | SNR |
|---|---|---|---|---|---|
| sop | 66 | 56..88 | 1823 Ko | 0.992 / 0.999 | 31.3 dB |
| alt | 66 | 49..81 | 2138 Ko | 0.986 / 0.998 | 30.8 dB |
| ten | 66 | 44..76 | 1988 Ko | 0.990 / 0.998 | 32.1 dB |
| bar | 68 | 36..69 | 1234 Ko | 0.976 / 0.994 | 30.9 dB |

Total 7.01 MB of ADPCM for the four saxophones, two velocity layers each.

## Two traps worth knowing

**Loop points are not in the SFZ.** `Data/*.txt` carries no `loop_*` opcode; the
points live in the FLAC `riff`/`smpl` application block. Read `smpl` from
`b.index(b'smpl') + 8` (skip the id *and* the size), or the fields shift and
every sample looks unlooped.

**Resampling breaks the join.** At 48 kHz the library loops are a whole number
of periods; at 22.05 kHz the points land between frames and the seam correlation
drops from 0.95 to 0.82 on high notes. `snap_loop_end` searches loopEnd over
±384 frames for the best correlation, which brings it back to 0.99. Do **not**
try to fix this by estimating the period from the audio: on high notes the
autocorrelation locks onto the second harmonic and the loop lands an octave out
(measured: seam fell to 0.32). Optimise the seam itself.

## Licence

CC-BY 4.0 — attribution is required. See `ATTRIBUTION.md`.
