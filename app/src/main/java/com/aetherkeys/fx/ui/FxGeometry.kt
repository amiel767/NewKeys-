// GENERATED from fx-window-preview.html (layout extractor). Numbers are measured, not hand-typed.
package com.aetherkeys.fx.ui

object FxGeom {
    const val LOGICAL_W = 825f
    const val LOGICAL_H = 343f
    const val CONTENT_X = 20f
    const val CONTENT_Y = 58f
    const val BOTTOM_BAR_Y = 214f
    const val BOTTOM_BAR_H = 71f
    const val LEVEL_X = 540f
    const val LEVEL_W = 200f
    const val LEVEL_Y = 252f
    const val CARD_W = 161.0f
    const val CARD_H = 288.3f
    const val ADD_TILE_W = 263.0f
    const val ADD_TILE_H = 58.9f
}

/**
 * A knob's box, in window coordinates, taken straight from the preview's
 * `AB(i,j,x,y,z,sk)` table (centre (x,y) in content coordinates, diameter z).
 * [sk] is the cap material `c0..c14`; the preview defaults it to the effect id,
 * and `SKN` overrides it per effect.
 */
data class KnobBox(
    val label: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val sk: Int,
)

object FxKnobBoxes {
    val byEffect: Map<Int, List<KnobBox>> = mapOf(
        0 to listOf(
            KnobBox("Size", 387.8f, 53.8f, 54.8f, 79.2f, 0),
            KnobBox("Decay", 614.1f, 84.4f, 54.8f, 79.2f, 0),
            KnobBox("Pre-delay", 665.3f, 153.1f, 64.0f, 79.2f, 0),
            KnobBox("Damp", 513.4f, 208.2f, 54.8f, 79.2f, 0),
            KnobBox("Width", 262.3f, 208.2f, 54.8f, 79.2f, 0),
            KnobBox("Mix", 105.7f, 153.1f, 54.8f, 79.2f, 0),
            KnobBox("Diffuse", 161.6f, 84.4f, 54.8f, 79.2f, 0),
        ),
                        1 to listOf(
            KnobBox("Feedback", 642.0f, 100.0f, 56.0f, 56.0f, 1),
            KnobBox("Tone", 727.0f, 100.0f, 56.0f, 56.0f, 1),
            KnobBox("Wow", 684.0f, 218.0f, 56.0f, 56.0f, 1),
            KnobBox("Mix", 89.0f, 237.0f, 52.0f, 52.0f, 1),
        ),
                        2 to listOf(
            KnobBox("Low", 36.0f, 134.0f, 100.0f, 100.0f, 3),
            KnobBox("Mid", 320.0f, 242.0f, 60.0f, 60.0f, 4),
            KnobBox("High", 428.0f, 252.0f, 44.0f, 44.0f, 1),
            KnobBox("Mid Q", 645.0f, 141.0f, 78.0f, 78.0f, 2),
        ),
                        3 to listOf(
            KnobBox("Ratio", 609.0f, 97.0f, 78.0f, 78.0f, 3),
            KnobBox("Knee", 591.0f, 231.0f, 54.0f, 54.0f, 3),
            KnobBox("Attack", 663.0f, 231.0f, 54.0f, 54.0f, 3),
            KnobBox("Release", 720.0f, 97.0f, 50.0f, 50.0f, 3),
            KnobBox("Makeup", 735.0f, 231.0f, 54.0f, 54.0f, 3),
        ),
                        4 to listOf(
            KnobBox("Amount", 77.0f, 118.0f, 136.0f, 136.0f, 4),
            KnobBox("Ceiling", 244.0f, 98.0f, 76.0f, 76.0f, 4),
            KnobBox("Release", 257.0f, 229.0f, 50.0f, 50.0f, 4),
            KnobBox("Width", 335.0f, 229.0f, 50.0f, 50.0f, 4),
            KnobBox("Mix", 335.0f, 103.0f, 50.0f, 50.0f, 4),
            KnobBox("Output", 724.0f, 155.0f, 62.0f, 62.0f, 4),
        ),
                        5 to listOf(
            KnobBox("Tone", 149.0f, 289.0f, 42.0f, 42.0f, 4),
            KnobBox("Body", 289.0f, 289.0f, 42.0f, 42.0f, 1),
            KnobBox("Space", 429.0f, 289.0f, 42.0f, 42.0f, 4),
        ),
                        6 to listOf(
            KnobBox("Click", 742.0f, 144.0f, 40.0f, 40.0f, 1),
            KnobBox("Tone", 149.0f, 289.0f, 42.0f, 42.0f, 4),
            KnobBox("Drive", 289.0f, 289.0f, 42.0f, 42.0f, 1),
            KnobBox("Space", 429.0f, 289.0f, 42.0f, 42.0f, 4),
        ),
                        7 to listOf(
            KnobBox("Tone", 149.0f, 289.0f, 42.0f, 42.0f, 4),
            KnobBox("Body", 289.0f, 289.0f, 42.0f, 42.0f, 1),
            KnobBox("Space", 429.0f, 289.0f, 42.0f, 42.0f, 4),
        ),
                        8 to listOf(
            KnobBox("Color", 680.0f, 214.0f, 40.0f, 40.0f, 8),
            KnobBox("Time", 756.0f, 214.0f, 40.0f, 40.0f, 8),
            KnobBox("Morph", 566.0f, 214.0f, 40.0f, 40.0f, 8),
            KnobBox("Motion", 378.0f, 195.0f, 62.0f, 62.0f, 8),
            KnobBox("Pressure", 212.0f, 214.0f, 40.0f, 40.0f, 8),
            KnobBox("Speed", 96.0f, 214.0f, 40.0f, 40.0f, 8),
            KnobBox("Tone", 149.0f, 289.0f, 42.0f, 42.0f, 8),
            KnobBox("Body", 289.0f, 289.0f, 42.0f, 42.0f, 8),
            KnobBox("Space", 429.0f, 289.0f, 42.0f, 42.0f, 8),
        ),
                        9 to listOf(
            KnobBox("Tone", 149.0f, 289.0f, 42.0f, 42.0f, 9),
            KnobBox("Resonance", 289.0f, 289.0f, 42.0f, 42.0f, 9),
            KnobBox("Space", 429.0f, 289.0f, 42.0f, 42.0f, 9),
        ),
                        10 to listOf(
            KnobBox("Gain", 67.0f, 119.0f, 46.0f, 46.0f, 4),
            KnobBox("Bass", 147.0f, 119.0f, 46.0f, 46.0f, 1),
            KnobBox("Mid", 227.0f, 119.0f, 46.0f, 46.0f, 4),
            KnobBox("Treble", 307.0f, 119.0f, 46.0f, 46.0f, 1),
            KnobBox("Presence", 387.0f, 119.0f, 46.0f, 46.0f, 4),
            KnobBox("Master", 467.0f, 119.0f, 46.0f, 46.0f, 1),
            KnobBox("Level", 727.0f, 119.0f, 46.0f, 46.0f, 4),
        ),
                        11 to listOf(
            KnobBox("Drive", 328.0f, 108.0f, 44.0f, 44.0f, 1),
            KnobBox("Comp", 398.0f, 108.0f, 44.0f, 44.0f, 4),
            KnobBox("Blend", 468.0f, 108.0f, 44.0f, 44.0f, 1),
            KnobBox("Level", 727.0f, 241.0f, 46.0f, 46.0f, 4),
        ),
                        12 to listOf(
            KnobBox("Tone", 149.0f, 289.0f, 42.0f, 42.0f, 4),
            KnobBox("Body", 289.0f, 289.0f, 42.0f, 42.0f, 1),
            KnobBox("Space", 429.0f, 289.0f, 42.0f, 42.0f, 4),
        ),
                        13 to listOf(
            KnobBox("Breath", 68.0f, 92.0f, 44.0f, 44.0f, 4),
            KnobBox("Choir", 68.0f, 186.0f, 44.0f, 44.0f, 1),
            KnobBox("Vibrato", 698.0f, 96.0f, 44.0f, 44.0f, 4),
            KnobBox("Tone", 149.0f, 289.0f, 42.0f, 42.0f, 4),
            KnobBox("Body", 289.0f, 289.0f, 42.0f, 42.0f, 1),
            KnobBox("Space", 429.0f, 289.0f, 42.0f, 42.0f, 4),
        ),
                        14 to listOf(
            KnobBox("Wave A", 45.0f, 140.0f, 40.0f, 40.0f, 1),
            KnobBox("Wave B", 125.0f, 140.0f, 40.0f, 40.0f, 1),
            KnobBox("Detune", 205.0f, 140.0f, 40.0f, 40.0f, 4),
            KnobBox("Sub", 45.0f, 208.0f, 40.0f, 40.0f, 4),
            KnobBox("Noise", 125.0f, 208.0f, 40.0f, 40.0f, 4),
            KnobBox("Unison", 205.0f, 208.0f, 40.0f, 40.0f, 1),
            KnobBox("Drive", 764.0f, 102.0f, 40.0f, 40.0f, 1),
            KnobBox("Env amt", 630.0f, 102.0f, 40.0f, 40.0f, 1),
            KnobBox("Spread", 700.0f, 102.0f, 40.0f, 40.0f, 4),
            KnobBox("LFO1 rate", 50.0f, 166.0f, 40.0f, 40.0f, 1),
            KnobBox("LFO1 depth", 130.0f, 166.0f, 40.0f, 40.0f, 4),
            KnobBox("LFO2 rate", 290.0f, 166.0f, 40.0f, 40.0f, 1),
            KnobBox("LFO2 depth", 370.0f, 166.0f, 40.0f, 40.0f, 4),
            KnobBox("Macro 4", 149.0f, 289.0f, 42.0f, 42.0f, 4),
            KnobBox("Glide", 289.0f, 289.0f, 42.0f, 42.0f, 1),
            KnobBox("Voices", 429.0f, 289.0f, 42.0f, 42.0f, 4),
            KnobBox("Bend", 650.0f, 214.0f, 40.0f, 40.0f, 4),
            KnobBox("Tune", 58.0f, 140.0f, 44.0f, 44.0f, 1),
            KnobBox("Macro 1", 138.0f, 140.0f, 44.0f, 44.0f, 4),
            KnobBox("Macro 2", 218.0f, 140.0f, 44.0f, 44.0f, 1),
            KnobBox("Macro 3", 298.0f, 140.0f, 44.0f, 44.0f, 4),
        ),
    )
}

/** One option inside a `.chg` group. [value] null means a toggle on [param]. */
data class ChipItem(val label: String, val param: Int, val value: Float? = null)

/** A `.chg` group: a label plus its options, at an absolute window position. */
data class ChipGroup(val label: String, val x: Float, val y: Float, val items: List<ChipItem>)

private fun pick(p: Int, v: Float, vararg l: String) =
    l.mapIndexed { i, n -> ChipItem(n, p, v * i) }

/** `SPG(p, html)`: a page of the synth screen, shown one at a time. */
data class ChipPage(val page: Int, val groups: List<ChipGroup>)

/**
 * `TG(i,label,x,y,items)` from the preview, in window coordinates. A `TG` label is
 * never empty in the source except on the synth, where `TG(14,'',...)` marks a
 * group that belongs to a page. Effects 5, 7, 8 and 13 have no `TG` call at all:
 * their chips are ADSR handles drawn on the canvas by `HS`, not DOM groups.
 *
 * For 14, `SPG(p, ...)` wraps each page; the reference shows one page at a time and
 * starts on page 0.
 */
object FxChipGroups {
    val byEffect: Map<Int, List<ChipGroup>> = mapOf(
        6 to listOf(
            ChipGroup("PERCUSSION", 520f, 10f, listOf(
                ChipItem("ON", 18), ChipItem("SOFT", 19), ChipItem("FAST", 20), ChipItem("3RD", 21))),
            ChipGroup("", 640f, 84f, listOf(ChipItem("UPPER", 23))),
            ChipGroup("", 640f, 116f, listOf(ChipItem("LOWER", 24))),
            ChipGroup("ROTARY", 520f, 176f, listOf(
                ChipItem("STOP", 25, 0f), ChipItem("SLOW", 25, 0.5f), ChipItem("FAST", 25, 1f))),
        ),
        9 to listOf(ChipGroup("BODY", 16f, 6f,
            pick(0, 0.333f, "ACOUSTIC", "NYLON", "ELEC DC", "ELEC SC"))),
        10 to listOf(
            ChipGroup("CHANNEL", 16f, 1f, pick(6, 0.5f, "CLEAN", "CRUNCH", "LEAD")),
            ChipGroup("PEDALS", 560f, 1f, listOf(ChipItem("BOOST", 9), ChipItem("TREM", 10)))),
        11 to listOf(ChipGroup("CABINET", 16f, 134f, listOf(
            ChipItem("1\u00d715", 8, 0f), ChipItem("4\u00d710", 8, 0.5f), ChipItem("8\u00d710", 8, 1f)))),
        12 to listOf(
            ChipGroup("MUTE", 16f, 6f, listOf(
                ChipItem("OPEN", 4, 0f), ChipItem("STRAIGHT", 4, 0.25f), ChipItem("CUP", 4, 0.5f),
                ChipItem("HARMON", 4, 0.75f), ChipItem("PLUNGER", 4, 1f))),
            ChipGroup("SECTION", 500f, 6f, listOf(
                ChipItem("1", 3, 0f), ChipItem("2", 3, 0.25f), ChipItem("3", 3, 0.5f),
                ChipItem("4", 3, 0.75f), ChipItem("5", 3, 1f))),
        ),
    )

    /** The synth's three pages. Page 0 also carries the filter group at (586,120). */
    val pages: Map<Int, List<ChipPage>> = mapOf(
        14 to listOf(
            ChipPage(0, listOf(
                ChipGroup("", 586f, 120f, pick(11, 0.333f, "LP", "HP", "BP", "NOTCH")))),
            ChipPage(1, listOf(
                ChipGroup("", 14f, 178f, pick(18, 0.333f, "SIN", "TRI", "SAW", "SQR")),
                ChipGroup("", 250f, 178f, pick(19, 0.333f, "CUTOFF", "PITCH", "WAVE", "LEVEL")))),
            ChipPage(2, listOf(
                ChipGroup("", 14f, 14f, pick(26, 0.5f, "POLY", "MONO", "LEGATO")))),
        ),
    )
}
