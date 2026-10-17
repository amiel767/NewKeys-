package com.soundstage.mixer.model

/**
 * Kit de batterie : suite de 8 à 16 noms de samples assignés aux pads.
 * null dans la liste = pad vide.
 */
data class DrumKitData(
    val id: String,
    val name: String,
    val padSamples: List<String?>,
) {
    companion object {
        fun defaults(): List<DrumKitData> = listOf(
            DrumKitData(
                id = "gospel_01",
                name = "Gospel NeoSoul 01",
                padSamples = listOf("Tambourin N", "Chimes 2", null, null, "Kick Club", "Clap Tight", "Ambient Texture", "Hat Closed",
                    null, null, null, null, null, null, null, null),
            ),
            DrumKitData(
                id = "lofi_kit",
                name = "Lo-Fi Kit",
                padSamples = listOf("Kick Soft", "Snare Rim", "Hat Open", "Shaker Light", "Tom Low", "Clap Tight", null, "Conga High",
                    null, null, null, null, null, null, null, null),
            ),
            DrumKitData(
                id = "perc_set",
                name = "Percussion Set",
                padSamples = listOf("Conga High", "Shaker Light", null, "Tambourin N", "Chimes 2", "Tom Low", "Hat Closed", null,
                    null, null, null, null, null, null, null, null),
            ),
        )
    }
}
