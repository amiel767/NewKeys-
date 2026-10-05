package com.soundstage.mixer.model

/**
 * Representation of a file or directory in internal storage for the AOSP audio browser.
 */
data class AudioStorageItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val durationFormatted: String = ""
)

/**
 * Step modes for DAW loop quantization.
 */
enum class LoopStepMode(val label: String) {
    DEFAULT("DEFAULT"),
    ONE_BAR("1 BAR"),
    TWO_BARS("2 BARS"),
    FOUR_BARS("4 BARS"),
    EIGHT_BARS("8 BARS"),
    SIXTEEN_BARS("16 BARS")
}
