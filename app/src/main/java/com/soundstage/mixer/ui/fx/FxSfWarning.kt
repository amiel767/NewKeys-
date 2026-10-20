package com.soundstage.mixer.ui.fx

/**
 * Soundfont / instrument mismatch warning, from the preview's `FAMOK`, `KW` and
 * `SHN` tables. A shaper instrument only sounds right on a soundfont of its own
 * family; the window warns when the loaded one looks unrelated.
 */
object FxSfWarning {
    /** GM program ranges accepted per instrument (inclusive). */
    private val FAMOK: Map<Int, List<IntRange>> = mapOf(
        5 to listOf(0..7, 16..23),
        7 to listOf(64..71),
        8 to listOf(40..51),
        9 to listOf(24..31),
        12 to listOf(56..63),
    )

    private val KW: Map<Int, Regex> = mapOf(
        5 to Regex("piano|keys|clav|harpsichord", RegexOption.IGNORE_CASE),
        7 to Regex("sax|reed", RegexOption.IGNORE_CASE),
        8 to Regex("violin|viola|cello|string|orchestra|fiddle", RegexOption.IGNORE_CASE),
        9 to Regex("guitar|gtr|strat|nylon|banjo", RegexOption.IGNORE_CASE),
        12 to Regex("trumpet|trombone|horn|brass|tuba|cornet", RegexOption.IGNORE_CASE),
    )

    private val SHN: Map<Int, String> = mapOf(
        5 to "a piano",
        7 to "a saxophone",
        8 to "a violin or string section",
        9 to "a guitar",
        12 to "a brass instrument",
    )

    /** True when [effectId] is not a family-checked instrument (no warning applies). */
    fun checked(effectId: Int): Boolean = FAMOK.containsKey(effectId)

    fun matches(effectId: Int, soundfontName: String, program: Int): Boolean {
        val ranges = FAMOK[effectId] ?: return true
        if (KW[effectId]?.containsMatchIn(soundfontName) == true) return true
        return ranges.any { program in it }
    }

    fun head(effectId: Int): String = "This doesn't look like ${SHN[effectId] ?: "a matching instrument"}."

    fun body(effectId: Int, soundfontName: String): String =
        "The loaded soundfont is \u201C$soundfontName\u201D. Choose a matching soundfont for the best result."
}
