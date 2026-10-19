package com.soundstage.mixer.ui.components

/**
 * Display-side chord model, split the way MixerPreview.jsx draws it:
 * a big root, a smaller quality, an optional slash bass, the "ALSO" chips,
 * the note chips and the inversion label.
 *
 * The JSX canvas is 891 x 411 px and matches 891 x 411 dp, so its fontSize
 * values are used as-is (1 canvas px = 1 dp). See AGENTS.md.
 */
data class ChordDisplay(
    val root: String,
    val quality: String,
    val bass: String?,
    val also: List<String>,
    val notes: List<String>,
    val inversion: String
) {
    /** Length of the full chord symbol, used by the JSX to pick the root size. */
    val symbolLength: Int get() = (root + quality + (bass?.let { "/$it" } ?: "")).length

    val rootFontSp: Float get() = if (symbolLength > 6) 24f else 28f

    companion object {
        private val ROOT_RE = Regex("^([A-G][#b♯♭]?)(.*)$")

        /** JSX `note()`: sharp sign rendered as the musical ♯ glyph. */
        private fun pretty(note: String): String = note.replace("#", "♯")

        private val INVERSION_LABELS = listOf("Root position", "1st inversion", "2nd inversion", "3rd inversion")

        fun from(chord: DetectedChord?): ChordDisplay? {
            if (chord == null) return null
            val symbol = chord.primaryName
            if (symbol.isBlank()) return null

            val slash = symbol.indexOf('/')
            val head = if (slash >= 0) symbol.substring(0, slash) else symbol
            val bassRaw = if (slash >= 0) symbol.substring(slash + 1) else null

            val m = ROOT_RE.find(head)
            val root = pretty(m?.groupValues?.get(1).orEmpty().ifBlank { head })
            val quality = m?.groupValues?.get(2).orEmpty()

            val notes = chord.notesList.map { pretty(it) }
            val bass = bassRaw?.let { pretty(it) }

            val also = buildList {
                listOfNotNull(chord.equivalentName, chord.alternateNames, chord.alternateName2)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it != symbol && it != chord.primaryName }
                    .forEach { if (!contains(it)) add(it) }
            }.take(3)

            val inversion = when {
                bass == null -> INVERSION_LABELS[0]
                else -> {
                    val idx = notes.indexOfFirst { it == bass }
                    INVERSION_LABELS.getOrNull(if (idx <= 0) 1 else idx)
                        ?: "${idx}th inversion"
                }
            }

            return ChordDisplay(root, quality, bass, also, notes, inversion)
        }
    }
}
