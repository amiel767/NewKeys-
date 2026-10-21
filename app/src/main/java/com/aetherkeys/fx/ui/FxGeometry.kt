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

data class KnobBox(val label: String, val x: Float, val y: Float, val w: Float, val h: Float)

object FxKnobBoxes {
    val byEffect: Map<Int, List<KnobBox>> = mapOf(
        0 to listOf(
            KnobBox("Size", 387.8f, 53.8f, 54.8f, 79.2f),
            KnobBox("Decay", 614.1f, 84.4f, 54.8f, 79.2f),
            KnobBox("Pre-delay", 665.3f, 153.1f, 64.0f, 79.2f),
            KnobBox("Damp", 513.4f, 208.2f, 54.8f, 79.2f),
            KnobBox("Width", 262.3f, 208.2f, 54.8f, 79.2f),
            KnobBox("Mix", 105.7f, 153.1f, 54.8f, 79.2f),
            KnobBox("Diffuse", 161.6f, 84.4f, 54.8f, 79.2f),
        ),
        1 to listOf(
            KnobBox("Feedback", 649.7f, 89.3f, 61.0f, 81.2f),
            KnobBox("Tone", 738.1f, 89.3f, 56.9f, 81.2f),
            KnobBox("Wow", 694.4f, 209.1f, 56.9f, 81.2f),
            KnobBox("Mix", 90.4f, 228.4f, 52.8f, 77.2f),
        ),
        2 to listOf(
            KnobBox("Low", 36.5f, 122.8f, 101.5f, 127.9f),
            KnobBox("Mid", 324.9f, 232.5f, 60.9f, 87.3f),
            KnobBox("High", 654.9f, 130.0f, 79.2f, 105.6f),
            KnobBox("Mid Q", 434.5f, 242.7f, 44.7f, 71.1f),
        ),
        3 to listOf(
            KnobBox("Ratio", 618.3f, 86.3f, 79.2f, 103.6f),
            KnobBox("Knee", 731.0f, 86.3f, 50.8f, 75.1f),
            KnobBox("Attack", 600.0f, 222.3f, 54.8f, 79.2f),
            KnobBox("Release", 673.1f, 222.3f, 54.8f, 79.2f),
            KnobBox("Makeup", 746.2f, 222.3f, 54.8f, 79.2f),
        ),
        4 to listOf(
            KnobBox("Amount", 78.2f, 107.6f, 138.1f, 162.4f),
            KnobBox("Ceiling", 247.7f, 87.3f, 77.2f, 101.5f),
            KnobBox("Release", 260.5f, 220.3f, 51.6f, 75.1f),
            KnobBox("Width", 340.1f, 220.3f, 50.8f, 75.1f),
            KnobBox("Mix", 340.1f, 92.4f, 50.8f, 75.1f),
            KnobBox("Output", 735.1f, 145.2f, 62.9f, 87.3f),
        ),
        5 to listOf(
            KnobBox("Tone", 151.3f, 281.2f, 42.6f, 67.0f),
            KnobBox("Body", 293.4f, 281.2f, 42.6f, 67.0f),
            KnobBox("Space", 435.6f, 281.2f, 42.6f, 67.0f),
        ),
        6 to listOf(
            KnobBox("Click", 753.3f, 133.5f, 40.6f, 66.0f),
            KnobBox("Tone", 151.3f, 280.7f, 42.6f, 68.0f),
            KnobBox("Drive", 293.4f, 280.7f, 42.6f, 68.0f),
            KnobBox("Space", 435.6f, 280.7f, 42.6f, 68.0f),
        ),
        7 to listOf(
            KnobBox("Tone", 151.3f, 281.2f, 42.6f, 67.0f),
            KnobBox("Body", 293.4f, 281.2f, 42.6f, 67.0f),
            KnobBox("Space", 435.6f, 281.2f, 42.6f, 67.0f),
        ),
        8 to listOf(
            KnobBox("Color", 97.5f, 205.1f, 40.6f, 65.0f),
            KnobBox("Time", 215.2f, 205.1f, 40.6f, 65.0f),
            KnobBox("Morph", 383.8f, 185.8f, 62.9f, 87.3f),
            KnobBox("Motion", 571.6f, 205.1f, 46.8f, 65.0f),
            KnobBox("Pressure", 681.7f, 205.1f, 58.0f, 65.0f),
            KnobBox("Speed", 767.5f, 205.1f, 40.6f, 65.0f),
            KnobBox("Tone", 151.3f, 281.2f, 42.6f, 67.0f),
            KnobBox("Body", 293.4f, 281.2f, 42.6f, 67.0f),
            KnobBox("Space", 435.6f, 281.2f, 42.6f, 67.0f),
        ),
        9 to listOf(
            KnobBox("Tone", 151.3f, 280.7f, 42.6f, 68.0f),
            KnobBox("Resonance", 284.2f, 280.7f, 61.0f, 68.0f),
            KnobBox("Space", 435.6f, 280.7f, 42.6f, 68.0f),
        ),
        10 to listOf(
            KnobBox("Gain", 68.0f, 110.7f, 46.7f, 71.1f),
            KnobBox("Bass", 149.2f, 110.7f, 46.7f, 71.1f),
            KnobBox("Mid", 230.5f, 110.7f, 46.7f, 71.1f),
            KnobBox("Treble", 311.7f, 110.7f, 46.7f, 71.1f),
            KnobBox("Presence", 386.8f, 110.7f, 59.0f, 71.1f),
            KnobBox("Master", 474.1f, 110.7f, 46.8f, 71.1f),
            KnobBox("Level", 738.1f, 110.7f, 46.7f, 71.1f),
        ),
        11 to listOf(
            KnobBox("Drive", 333.0f, 97.5f, 44.7f, 69.0f),
            KnobBox("Comp", 404.1f, 97.5f, 44.7f, 69.0f),
            KnobBox("Blend", 475.1f, 97.5f, 44.7f, 69.0f),
            KnobBox("Level", 738.1f, 232.5f, 46.7f, 71.1f),
        ),
        12 to listOf(
            KnobBox("Tone", 151.3f, 281.2f, 42.6f, 67.0f),
            KnobBox("Body", 293.4f, 281.2f, 42.6f, 67.0f),
            KnobBox("Space", 435.6f, 281.2f, 42.6f, 67.0f),
        ),
        13 to listOf(
            KnobBox("Breath", 69.0f, 81.2f, 44.7f, 69.0f),
            KnobBox("Choir", 69.0f, 176.7f, 44.7f, 69.0f),
            KnobBox("Vibrato", 706.8f, 85.3f, 48.4f, 69.0f),
            KnobBox("Tone", 151.3f, 281.2f, 42.6f, 67.0f),
            KnobBox("Body", 293.4f, 281.2f, 42.6f, 67.0f),
            KnobBox("Space", 435.6f, 281.2f, 42.6f, 67.0f),
        ),
        14 to listOf(
            KnobBox("Wave A", 45.6f, 129.4f, 40.7f, 66.0f),
            KnobBox("Wave B", 126.9f, 129.4f, 40.7f, 66.0f),
            KnobBox("Detune", 208.1f, 129.4f, 40.7f, 66.0f),
            KnobBox("Sub", 45.7f, 198.5f, 40.6f, 66.0f),
            KnobBox("Noise", 126.9f, 198.5f, 40.6f, 66.0f),
            KnobBox("Unison", 208.1f, 198.5f, 40.7f, 66.0f),
            KnobBox("Drive", 639.6f, 90.9f, 40.6f, 66.0f),
            KnobBox("Env amt", 707.3f, 90.9f, 47.5f, 66.0f),
            KnobBox("Spread", 775.6f, 90.9f, 40.7f, 66.0f),
            KnobBox("LFO1 rate", -31.2f, -55.1f, 0.0f, 0.0f),
            KnobBox("LFO1 depth", -31.2f, -55.1f, 0.0f, 0.0f),
            KnobBox("LFO2 rate", -31.2f, -55.1f, 0.0f, 0.0f),
            KnobBox("LFO2 depth", -31.2f, -55.1f, 0.0f, 0.0f),
            KnobBox("Macro 4", -31.2f, -55.1f, 0.0f, 0.0f),
            KnobBox("Glide", -31.2f, -55.1f, 0.0f, 0.0f),
            KnobBox("Voices", -31.2f, -55.1f, 0.0f, 0.0f),
            KnobBox("Bend", -31.2f, -55.1f, 0.0f, 0.0f),
            KnobBox("Tune", -31.2f, -55.1f, 0.0f, 0.0f),
            KnobBox("Macro 1", 148.9f, 280.7f, 47.5f, 68.0f),
            KnobBox("Macro 2", 291.0f, 280.7f, 47.5f, 68.0f),
            KnobBox("Macro 3", 433.1f, 280.7f, 47.5f, 68.0f),
        ),
    )
}
