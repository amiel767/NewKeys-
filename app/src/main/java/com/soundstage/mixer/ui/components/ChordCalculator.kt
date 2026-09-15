package com.soundstage.mixer.ui.components

/**
 * Professional Ultra-Fast Real-Time Chord Calculator & Music Theory Engine.
 * Analyzes active polyphonic notes with zero latency and calculates:
 * - Extended Chords (9th, 11th, 13th, altered dominants, sus, add9, 6/9, slash inversions).
 * - Jazz open voicings with omitted 5th (no5) or roots.
 * - Comprehensive chord dictionary with rich alternate naming (e.g., Δ, -, ø, dim, alt, Hendrix).
 * - Real-time interval breakdown and notes list.
 */
data class DetectedChord(
    val primaryName: String,
    val variantName: String,
    val alternateNames: String = "",
    val alternateName2: String = "",
    val formula: String,
    val notesList: List<String>
)

object ChordCalculator {

    private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    // Map of sorted interval sets (modulo 12 relative to root = 0) -> (Suffix, Triple(Variant Name Suffix/Format, Alternate Notation, Interval Formula))
    private val CHORD_DEFINITIONS: Map<List<Int>, Triple<String, String, String>> = mapOf(
        // --- 1. Basic Triads ---
        listOf(0, 4, 7) to Triple("", "maj / Δ", "1 - 3 - 5"),
        listOf(0, 3, 7) to Triple("m", "min / -", "1 - b3 - 5"),
        listOf(0, 3, 6) to Triple("dim", "° / Dim", "1 - b3 - b5"),
        listOf(0, 4, 8) to Triple("aug", "+ / (+5)", "1 - 3 - #5"),
        listOf(0, 5, 7) to Triple("sus4", "sus", "1 - 4 - 5"),
        listOf(0, 2, 7) to Triple("sus2", "sus2", "1 - 2 - 5"),
        listOf(0, 2, 5, 7) to Triple("sus2sus4", "sus24", "1 - 2 - 4 - 5"),
        listOf(0, 2, 4, 7) to Triple("add9", "maj(add9) / add2", "1 - 2 - 3 - 5"),
        listOf(0, 2, 3, 7) to Triple("m(add9)", "min(add9) / -add9", "1 - 2 - b3 - 5"),
        listOf(0, 4, 5, 7) to Triple("add11", "add4", "1 - 3 - 4 - 5"),
        listOf(0, 3, 5, 7) to Triple("m(add11)", "min(add11)", "1 - b3 - 4 - 5"),

        // --- 2. 7th Chords ---
        listOf(0, 4, 7, 10) to Triple("7", "dom7 / Dominante", "1 - 3 - 5 - b7"),
        listOf(0, 4, 7, 11) to Triple("maj7", "M7 / Δ7 / Maj7", "1 - 3 - 5 - 7"),
        listOf(0, 3, 7, 10) to Triple("m7", "min7 / -7", "1 - b3 - 5 - b7"),
        listOf(0, 3, 7, 11) to Triple("m(maj7)", "min(M7) / -Δ7", "1 - b3 - 5 - 7"),
        listOf(0, 3, 6, 10) to Triple("m7b5", "ø7 / Half-Dim", "1 - b3 - b5 - b7"),
        listOf(0, 3, 6, 9) to Triple("dim7", "°7 / Dim7", "1 - b3 - b5 - bb7"),
        listOf(0, 4, 8, 10) to Triple("7#5", "7+5 / aug7", "1 - 3 - #5 - b7"),
        listOf(0, 4, 6, 10) to Triple("7b5", "7-5 / 7(b5)", "1 - 3 - b5 - b7"),
        listOf(0, 4, 8, 11) to Triple("maj7#5", "M7#5 / Δ#5", "1 - 3 - #5 - 7"),
        listOf(0, 4, 6, 11) to Triple("maj7b5", "M7b5 / Δb5", "1 - 3 - b5 - 7"),
        listOf(0, 5, 7, 10) to Triple("7sus4", "7sus", "1 - 4 - 5 - b7"),
        listOf(0, 2, 7, 10) to Triple("7sus2", "sus2 7", "1 - 2 - 5 - b7"),

        // --- 3. 6th & 6/9 Chords ---
        listOf(0, 4, 7, 9) to Triple("6", "maj6 / M6", "1 - 3 - 5 - 6"),
        listOf(0, 3, 7, 9) to Triple("m6", "min6 / -6", "1 - b3 - 5 - 6"),
        listOf(0, 2, 4, 7, 9) to Triple("6/9", "69 / maj6(add9)", "1 - 3 - 5 - 6 - 9"),
        listOf(0, 2, 3, 7, 9) to Triple("m6/9", "-69 / min6(add9)", "1 - b3 - 5 - 6 - 9"),

        // --- 4. 9th Chords ---
        listOf(0, 2, 4, 7, 10) to Triple("9", "dom9", "1 - 3 - 5 - b7 - 9"),
        listOf(0, 2, 4, 7, 11) to Triple("maj9", "M9 / Δ9", "1 - 3 - 5 - 7 - 9"),
        listOf(0, 2, 3, 7, 10) to Triple("m9", "min9 / -9", "1 - b3 - 5 - b7 - 9"),
        listOf(0, 2, 3, 7, 11) to Triple("m(maj9)", "min(M9) / -Δ9", "1 - b3 - 5 - 7 - 9"),
        listOf(0, 1, 4, 7, 10) to Triple("7b9", "dom7(b9)", "1 - 3 - 5 - b7 - b9"),
        listOf(0, 3, 4, 7, 10) to Triple("7#9", "Hendrix / 7(+9)", "1 - 3 - 5 - b7 - #9"),
        listOf(0, 2, 3, 6, 10) to Triple("m9b5", "ø9 / Half-Dim 9", "1 - b3 - b5 - b7 - 9"),
        listOf(0, 2, 4, 8, 10) to Triple("9#5", "9+5 / aug9", "1 - 3 - #5 - b7 - 9"),
        listOf(0, 2, 4, 6, 10) to Triple("9b5", "9-5", "1 - 3 - b5 - b7 - 9"),
        listOf(0, 2, 5, 7, 10) to Triple("9sus4", "9sus", "1 - 4 - 5 - b7 - 9"),

        // --- 5. 11th Extended Chords ---
        listOf(0, 2, 4, 5, 7, 10) to Triple("11", "dom11", "1 - 3 - 5 - b7 - 9 - 11"),
        listOf(0, 2, 3, 5, 7, 10) to Triple("m11", "min11 / -11", "1 - b3 - 5 - b7 - 9 - 11"),
        listOf(0, 2, 4, 5, 7, 11) to Triple("maj11", "M11 / Δ11", "1 - 3 - 5 - 7 - 9 - 11"),
        listOf(0, 2, 4, 6, 7, 10) to Triple("7#11", "7(+11) / Lydian Dom", "1 - 3 - 5 - b7 - 9 - #11"),
        listOf(0, 2, 4, 6, 7, 11) to Triple("maj7#11", "M7#11 / Δ#11", "1 - 3 - 5 - 7 - 9 - #11"),
        listOf(0, 2, 3, 5, 6, 10) to Triple("m11b5", "ø11", "1 - b3 - b5 - b7 - 9 - 11"),
        listOf(0, 1, 4, 5, 7, 10) to Triple("11b9", "dom11(b9)", "1 - 3 - 5 - b7 - b9 - 11"),

        // --- 6. 13th Extended Chords ---
        listOf(0, 2, 4, 7, 9, 10) to Triple("13", "dom13", "1 - 3 - 5 - b7 - 9 - 13"),
        listOf(0, 2, 4, 7, 9, 11) to Triple("maj13", "M13 / Δ13", "1 - 3 - 5 - 7 - 9 - 13"),
        listOf(0, 2, 3, 7, 9, 10) to Triple("m13", "min13 / -13", "1 - b3 - 5 - b7 - 9 - 13"),
        listOf(0, 1, 4, 7, 9, 10) to Triple("13b9", "dom13(b9)", "1 - 3 - 5 - b7 - b9 - 13"),
        listOf(0, 3, 4, 7, 9, 10) to Triple("13#9", "dom13(#9)", "1 - 3 - 5 - b7 - #9 - 13"),
        listOf(0, 2, 4, 6, 9, 10) to Triple("13#11", "dom13(#11)", "1 - 3 - 5 - b7 - 9 - #11 - 13"),
        listOf(0, 2, 4, 8, 9, 10) to Triple("7b13", "7(b13)", "1 - 3 - 5 - b7 - b13"),
        listOf(0, 2, 5, 7, 9, 10) to Triple("13sus4", "13sus", "1 - 4 - 5 - b7 - 9 - 13"),

        // --- 7. Altered Chords (Jazz Super Locrian) ---
        listOf(0, 1, 4, 8, 10) to Triple("7alt", "7(b9,b13)", "1 - 3 - #5 - b7 - b9"),
        listOf(0, 3, 4, 8, 10) to Triple("7alt(#9)", "7(#9,b13)", "1 - 3 - #5 - b7 - #9"),
        listOf(0, 1, 4, 6, 10) to Triple("7b9b5", "7(b9,b5)", "1 - 3 - b5 - b7 - b9"),
        listOf(0, 3, 4, 6, 10) to Triple("7#9b5", "7(#9,b5)", "1 - 3 - b5 - b7 - #9"),

        // --- 8. Jazz Voicings with Omitted 5th (no5) ---
        listOf(0, 4, 10) to Triple("7(no5)", "7 Shell / dom7", "1 - 3 - b7"),
        listOf(0, 4, 11) to Triple("maj7(no5)", "M7 Shell / Δ7", "1 - 3 - 7"),
        listOf(0, 3, 10) to Triple("m7(no5)", "min7 Shell / -7", "1 - b3 - b7"),
        listOf(0, 2, 4, 10) to Triple("9(no5)", "dom9 Voicing", "1 - 3 - b7 - 9"),
        listOf(0, 2, 4, 11) to Triple("maj9(no5)", "M9 / Δ9 Voicing", "1 - 3 - 7 - 9"),
        listOf(0, 2, 3, 10) to Triple("m9(no5)", "min9 / -9 Voicing", "1 - b3 - b7 - 9"),
        listOf(0, 1, 4, 10) to Triple("7b9(no5)", "7(b9) Voicing", "1 - 3 - b7 - b9"),
        listOf(0, 3, 4, 10) to Triple("7#9(no5)", "7(#9) Voicing", "1 - 3 - b7 - #9"),
        listOf(0, 4, 9, 10) to Triple("13(no5)", "13 Shell Voicing", "1 - 3 - b7 - 13"),
        listOf(0, 2, 4, 9, 10) to Triple("13(no5,9)", "13 Jazz Voicing", "1 - 3 - b7 - 9 - 13"),
        listOf(0, 4, 9, 11) to Triple("maj13(no5)", "M13 / Δ13 Shell", "1 - 3 - 7 - 13"),
        listOf(0, 3, 9, 10) to Triple("m13(no5)", "min13 / -13 Shell", "1 - b3 - b7 - 13"),
        listOf(0, 2, 3, 5, 10) to Triple("m11(no5)", "min11 Voicing", "1 - b3 - b7 - 9 - 11"),

        // --- 9. Dyads / Open Power Chords ---
        listOf(0, 7) to Triple("5", "Power Chord (1-5)", "1 - 5"),
        listOf(0, 4) to Triple("(no5)", "Tierce Maj (1-3)", "1 - 3"),
        listOf(0, 3) to Triple("m(no5)", "Tierce Min (1-b3)", "1 - b3")
    )

    fun parsePitchClass(noteStr: String): Int? {
        val clean = noteStr.trim().uppercase()
        if (clean.isEmpty()) return null
        val letter = when {
            clean.startsWith("C#") || clean.startsWith("DB") -> "C#"
            clean.startsWith("D#") || clean.startsWith("EB") -> "D#"
            clean.startsWith("F#") || clean.startsWith("GB") -> "F#"
            clean.startsWith("G#") || clean.startsWith("AB") -> "G#"
            clean.startsWith("A#") || clean.startsWith("BB") -> "A#"
            clean.startsWith("C") -> "C"
            clean.startsWith("D") -> "D"
            clean.startsWith("E") -> "E"
            clean.startsWith("F") -> "F"
            clean.startsWith("G") -> "G"
            clean.startsWith("A") -> "A"
            clean.startsWith("B") -> "B"
            else -> return null
        }
        val idx = NOTE_NAMES.indexOf(letter)
        return if (idx >= 0) idx else null
    }

    /**
     * Detects chord from polyphonic collection of notes.
     * Guaranteed sub-millisecond execution.
     */
    fun detect(notes: Collection<String>): DetectedChord? {
        if (notes.isEmpty()) return null

        val pitchClassesWithOctave = notes.mapNotNull { noteStr ->
            val pc = parsePitchClass(noteStr) ?: return@mapNotNull null
            val octave = noteStr.filter { it.isDigit() }.toIntOrNull() ?: 4
            val midi = octave * 12 + pc
            Triple(pc, midi, noteStr)
        }.sortedBy { it.second }

        if (pitchClassesWithOctave.isEmpty()) return null

        val uniquePitchClasses = pitchClassesWithOctave.map { it.first }.distinct()
        val lowestPitchClass = pitchClassesWithOctave.first().first
        val lowestNoteName = NOTE_NAMES[lowestPitchClass]

        // 1 Single Note
        if (uniquePitchClasses.size == 1) {
            val rootName = NOTE_NAMES[uniquePitchClasses.first()]
            return DetectedChord(
                primaryName = rootName,
                variantName = "Note Fondamentale (Root)",
                alternateNames = "Note fondamentale",
                formula = "1",
                notesList = listOf(rootName)
            )
        }

        // Test each unique pitch as potential chord root
        for (rootPc in uniquePitchClasses) {
            val rootName = NOTE_NAMES[rootPc]
            val intervals = uniquePitchClasses.map { (it - rootPc + 12) % 12 }.sorted()

            CHORD_DEFINITIONS[intervals]?.let { (suffix, variantFormat, formula) ->
                val isSlash = rootPc != lowestPitchClass
                val baseChord = "$rootName$suffix"
                val finalPrimary = if (isSlash) "$baseChord/$lowestNoteName" else baseChord
                
                // Formulate clear, distinct variant name (e.g. "Cmaj7" -> "Em/C", "Am7" -> "C6/A", "C" -> "Cmaj (Δ)")
                val variantBase = if (variantFormat.startsWith("maj") || variantFormat.startsWith("min") || variantFormat.startsWith("M") || variantFormat.startsWith("°") || variantFormat.startsWith("+") || variantFormat.startsWith("sus") || variantFormat.startsWith("dom") || variantFormat.startsWith("ø") || variantFormat.startsWith("6") || variantFormat.startsWith("Power")) {
                    "$rootName $variantFormat"
                } else {
                    variantFormat
                }
                val finalVariant = if (isSlash) "$variantBase / $lowestNoteName" else variantBase

                val notesFormatted = uniquePitchClasses.map { NOTE_NAMES[it] }.joinToString(" · ")

                return DetectedChord(
                    primaryName = finalPrimary,
                    variantName = finalVariant,
                    alternateNames = "$finalVariant — [$formula]",
                    alternateName2 = formula,
                    formula = notesFormatted,
                    notesList = uniquePitchClasses.map { NOTE_NAMES[it] }
                )
            }
        }

        // Partial Match / Voicing detection if 3+ notes
        if (uniquePitchClasses.size >= 3) {
            val rootName = NOTE_NAMES[lowestPitchClass]
            val intervals = uniquePitchClasses.map { (it - lowestPitchClass + 12) % 12 }.sorted()

            val hasMaj3 = intervals.contains(4)
            val hasMin3 = intervals.contains(3)
            val hasPerf5 = intervals.contains(7)
            val hasDom7 = intervals.contains(10)
            val hasMaj7 = intervals.contains(11)
            val has9th = intervals.contains(2)
            val has11th = intervals.contains(5)
            val has13th = intervals.contains(9)

            val inferredSuffix = when {
                hasMaj3 && hasDom7 && has13th -> "13(voic)"
                hasMaj3 && hasMaj7 && has13th -> "maj13(voic)"
                hasMin3 && hasDom7 && has11th -> "m11(voic)"
                hasMaj3 && hasDom7 && has9th -> "9(voic)"
                hasMaj3 && hasMaj7 && has9th -> "maj9(voic)"
                hasMin3 && hasDom7 && has9th -> "m9(voic)"
                hasMaj3 && hasPerf5 && hasMaj7 -> "maj7(voic)"
                hasMaj3 && hasPerf5 && hasDom7 -> "7(voic)"
                hasMin3 && hasPerf5 && hasDom7 -> "m7(voic)"
                hasMaj3 && hasPerf5 -> "(voic)"
                hasMin3 && hasPerf5 -> "m(voic)"
                else -> ""
            }

            if (inferredSuffix.isNotEmpty()) {
                val notesFormatted = uniquePitchClasses.map { NOTE_NAMES[it] }.joinToString(" · ")
                return DetectedChord(
                    primaryName = "$rootName$inferredSuffix",
                    variantName = "Voicing Ouvert ($rootName)",
                    alternateNames = "Voicing harmonique ouvert",
                    formula = notesFormatted,
                    notesList = uniquePitchClasses.map { NOTE_NAMES[it] }
                )
            }
        }

        // Fallback for unclassified multi-note clusters
        val rootName = NOTE_NAMES[lowestPitchClass]
        val clusterStr = uniquePitchClasses.map { NOTE_NAMES[it] }.joinToString(" · ")
        return DetectedChord(
            primaryName = rootName,
            variantName = "Harmonie / Cluster",
            alternateNames = "Cluster harmonique",
            formula = clusterStr,
            notesList = uniquePitchClasses.map { NOTE_NAMES[it] }
        )
    }
}
