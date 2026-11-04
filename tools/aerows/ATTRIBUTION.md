# Row artwork — sources and licences

`app/src/main/res/drawable-nodpi/ae_row_*.webp` are the photographs the Æther
Instruments window shows on the right of each row. They are **not** hand-drawn:
each is a real photograph, cropped to the row's 3:1 cell and given a left-side
alpha dissolve. Regenerate them with:

    python3 tools/aerows/build_rows.py

The build script downloads the originals into `tools/aerows/src/` (gitignored)
and writes the five `.webp` into the app resources.

| Asset | Source | Licence |
|---|---|---|
| `ae_row_organ.webp` | Wikimedia Commons — *Hammond B3 & Drawbars.jpg* | see file page |
| `ae_row_synth.webp` | Wikimedia Commons — *Sequential Prophet 10 Rev 4.png* | see file page |
| `ae_row_leads.webp` | Wikimedia Commons — *Minimoog Model D Synthesizer at SMEM Playroom.jpg* | see file page |
| `ae_row_sax.webp` | Wikimedia Commons — *A Saxophone.jpg* (Sally V) | CC BY-SA 4.0 |
| `ae_row_piano.webp` | repo asset `ae_piano_mellow.webp` (Salamander / CC-BY), mirrored | CC-BY |

Commons file pages carry the exact licence and author for each original; check
them before redistributing. The piano row inherits the licence already
documented in `ATTRIBUTION.md`.

*Note:* the sax row used to be built from the repo's `ae_sax_alto_gold.webp`
cut-out. A cut-out is a thin silhouette on empty glass, which read as a
different style next to the four photographs, so it was replaced by the Commons
photograph above (same treatment as organ / synth / leads).
