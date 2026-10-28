# pianopack — Æther Piano sample pack builder (analysis + plan)

Goal: a **light** piano pack built the same way as `tools/saxpack` — attack + sustain
loop, mono 22.05 kHz, 4-bit IMA ADPCM — so the existing native reader
(`fx_sax_samples.h`) and the hybrid engine pattern (`fx_sax.h`) can be reused.

All numbers below are **measured**, not estimated. Sources are queried over the
GitHub raw endpoint; loop metadata is read from the FLAC `riff`/`smpl` block.

## Candidate sources (licence is the first filter)

| Library | Licence | Content | Loops | Verdict |
|---|---|---|---|---|
| **SplendidGrandPiano** (AKAI / Steinway) | **Public domain** | 62 notes, 5 velocity layers, 73.3 MB FLAC 44.1 k stereo | `smpl` present but **ends at EOF**, seam −0.85..+0.29 | **primary** |
| SalamanderGrandPiano (A. Holm, Yamaha C5) | CC-BY **3.0** | 16 layers, 714 MB | **no `smpl` at all** (decay to silence) | rejected (too heavy) |
| GregSullivan.E-Pianos (Wurlitzer / CP80 / Pianet) | CC-BY **3.0** | Wurlitzer 2.3 MB, CP80 10.5 MB | **real `smpl` loops**, but short (0.09–0.20 s), seam +0.007..+0.48 | **secondary** (electric models) |
| jlearman.jRhodes3c (Rhodes Mk I) | CC BY-**NC**-SA 4.0 | 17.8 MB | looped | **rejected — non-commercial** |
| MTG Solo Sax (used for the sax) | CC-BY 4.0 | — | — | reference only |

Attribution is therefore required for the shipped pack, exactly like the sax:
Splendid Grand Piano is public domain (credit is courtesy), E-Pianos is CC-BY 3.0.

## Size, computed from real bytes

Pipeline: FLAC stereo 44.1 k → decode → mono → resample 22.05 k → trim to
attack + one loop → ADPCM 4-bit (0.5 byte/sample).

Selection measured: **21 notes × 3 layers = 46 regions**.

| Quantity | Value |
|---|---|
| FLAC of the selection | 16.2 MB |
| Packed size | **1.37 MB** |
| ratio pack / FLAC | 0.0844 |
| per region | **~30 KB** |

The sax packs are ~7 MB for four instruments; a piano is ~1.4 MB because a piano
sample decays and the loop is short. Extrapolating: 3 layers × every 3rd semitone
≈ 1.4 MB, 5 layers × every 2nd semitone ≈ 4 MB. **1.4 MB is the target.**

## Two problems the sax pipeline does not have

**1. There is no usable loop point.** Splendid's `smpl` chunk is present but its
loop end equals the last frame of the file and the seam is bad (measured C3 seam
−0.85, A0 +0.10). Salamander has no `smpl` at all. Unlike the sax, the loop has
to be **found**, not read.

Measured fix: search for a loopable window of **0.35–1.6 s** in the sustain,
scoring the correlation between the window head and the segment one period later.
Over four notes and layers the best candidate scores **+0.91 .. +0.98**.

**2. A good correlation is not enough.** Looping the window raw still jumps 5–10×
the signal's own largest step (measured ratio 5.4 / 10.6 / 10.3 against the 99.9th
percentile of the internal sample-to-sample difference) — an audible click.

Measured fix: a **12 ms crossfade** of the tail onto the head. The weight must be
**full at index 0** (`w = linspace(1, 0, X)`), i.e. the head starts as a copy of
the tail and fades into itself. With the weight the other way round the ratio does
not move (5.41 → 5.60 across 12–100 ms, the bug the first attempt had).

| Note | raw | 12 ms crossfade |
|---|---|---|
| FF C3 | 5.38 | **0.01** |
| PP C4 | 10.64 | **0.11** |
| MF A2 | 10.31 | **0.07** |

A ratio near 1 means the wrap is indistinguishable from the signal; 0.01–0.11 is
well inside that.

## Plan

1. `extract.py` — mirror `tools/saxpack/extract.py`, with the loop **search** and
   the crossfade above instead of reading `smpl`. Output `pianopack_<layer>.bin`
   + `pianopack.json`.
2. `PianoSampleReader` — the sax reader with the transposition and the velocity
   layer split kept; no changes needed to the format.
3. `AePianoEngine` (C++, `fx_chain.h`) — hybrid like `SaxEngine`: a percussive
   synthesis bed under the sampled attack, `sampleMix` per model.
4. Parameter map for the 12 params, per model (felt / studio / bright / mellow /
   dark / upright / lady / honest / electric), tuned against the samples.
5. Only then the UI cards.

Steps 1–2 are independent of the engine and can land first.
