# Attribution — saxophone samples

The sample packs produced by `extract.py` contain audio derived from
**MTG Solo Saxophones**, and their use is bound by that library's licence.

## MTG Solo Saxophones

- **Author**: MTG / Music Technology Group, Universitat Pompeu Fabra —
  programmed by kinwie
- **Source**: https://github.com/sfzinstruments/MTG.SoloSax
- **Licence**: Creative Commons Attribution 4.0 International (CC-BY 4.0)
  https://creativecommons.org/licenses/by/4.0/
- **Contents used**: Soprano, Alto, Tenor and Baritone solo saxophones,
  24-bit 48 kHz mono FLAC, sustain loops taken from the FLAC `smpl` metadata.

**CC-BY 4.0 requires attribution.** Any build that ships these packs must credit
the author. Suggested wording, for an "About" screen or a credits list:

> Saxophone samples: MTG Solo Saxophones by the Music Technology Group
> (Universitat Pompeu Fabra), programmed by kinwie. Licensed under
> CC-BY 4.0. Loops trimmed and re-encoded for this application.

## What `extract.py` changes

The derivative work is limited to:

- trimming each region to its attack plus one sustain loop,
- resampling 48 kHz to 22.05 kHz,
- re-aligning each loop point to the best seam (`snap_loop_end`),
- encoding to 4-bit IMA ADPCM.

No pitch shifting, time stretching or spectral editing is applied.

## Rejected sources, kept for the record

| Source | Why it is not used |
|---|---|
| FluidR3 GM | Its `ICMT` chunk states "DO NOT REDISTRIBUTE ANY OF THESE SAMPLES." |
| VSCO-2 CE | Contains no saxophone at all (0 of 3313 files). |
| Philharmonia Woodwind | Saxophone type not identified; range D3..G6 too high for a baritone; licence not stated on the site. |
| GeneralUser GS | Download blocked (HTTP 403); licence unverifiable. |

## Alternative without attribution

**FreePats Tenor Saxophone** (https://freepats.zenvoid.org/) is **CC0 1.0**:
no attribution required. It covers the tenor only, in 18 samples (small) or 46
(full), with infinite sustain loops and no built-in vibrato. Keep it as the
fallback if attribution is not wanted.
