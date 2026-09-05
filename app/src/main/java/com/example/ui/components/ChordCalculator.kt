package com.example.ui.components

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
    val alternateNames: String,
    val alternateName2: String = "",
    val formula: String,
    val notesList: List<String>
)

object ChordCalculator {

    private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    // Map of sorted interval sets (modulo 12 relative to root = 0) -> (Suffix, Pair(Alternate Notation, Interval Formula))
    private val CHORD_DEFINITIONS: Map<List<Int>, Pair<String, Pair<String, String>>> = mapOf(
        // --- 1. Basic Triads ---
        listOf(0, 4, 7) to ("" to Pair("maj / Major / Δ", "1 - 3 - 5")),
        listOf(0, 3, 7) to ("m" to Pair("min / Minor / -", "1 - b3 - 5")),
        listOf(0, 3, 6) to ("dim" to Pair("° / Diminué", "1 - b3 - b5")),
        listOf(0, 4, 8) to ("aug" to Pair("+ / +5 / Augmenté", "1 - 3 - #5")),
        listOf(0, 5, 7) to ("sus4" to Pair("sus / Quartes", "1 - 4 - 5")),
        listOf(0, 2, 7) to ("sus2" to Pair("sus2", "1 - 2 - 5")),
        listOf(0, 2, 5, 7) to ("sus2sus4" to Pair("sus24 / Quartes et secondes", "1 - 2 - 4 - 5")),
        listOf(0, 2, 4, 7) to ("add9" to Pair("add2 / maj(add9)", "1 - 2 - 3 - 5")),
        listOf(0, 2, 3, 7) to ("m(add9)" to Pair("min(add9) / -add9", "1 - 2 - b3 - 5")),
        listOf(0, 4, 5, 7) to ("add11" to Pair("add4", "1 - 3 - 4 - 5")),
        listOf(0, 3, 5, 7) to ("m(add11)" to Pair("min(add11)", "1 - b3 - 4 - 5")),

        // --- 2. 7th Chords ---
        listOf(0, 4, 7, 10) to ("7" to Pair("Dominant 7 / dom7", "1 - 3 - 5 - b7")),
        listOf(0, 4, 7, 11) to ("maj7" to Pair("M7 / Δ / Maj7 / Δ7", "1 - 3 - 5 - 7")),
        listOf(0, 3, 7, 10) to ("m7" to Pair("min7 / -7 / m7", "1 - b3 - 5 - b7")),
        listOf(0, 3, 7, 11) to ("m(maj7)" to Pair("min(M7) / -Δ / mM7", "1 - b3 - 5 - 7")),
        listOf(0, 3, 6, 10) to ("m7b5" to Pair("ø7 / Demi-diminué / Half-Dim", "1 - b3 - b5 - b7")),
        listOf(0, 3, 6, 9) to ("dim7" to Pair("°7 / Diminué 7 / Full Dim", "1 - b3 - b5 - bb7")),
        listOf(0, 4, 8, 10) to ("7#5" to Pair("7+5 / aug7 / 7(+5)", "1 - 3 - #5 - b7")),
        listOf(0, 4, 6, 10) to ("7b5" to Pair("7-5 / 7(b5)", "1 - 3 - b5 - b7")),
        listOf(0, 4, 8, 11) to ("maj7#5" to Pair("M7#5 / augM7 / Δ#5", "1 - 3 - #5 - 7")),
        listOf(0, 4, 6, 11) to ("maj7b5" to Pair("M7b5 / Δb5", "1 - 3 - b5 - 7")),
        listOf(0, 5, 7, 10) to ("7sus4" to Pair("sus4 7 / 7sus", "1 - 4 - 5 - b7")),
        listOf(0, 2, 7, 10) to ("7sus2" to Pair("sus2 7", "1 - 2 - 5 - b7")),

        // --- 3. 6th & 6/9 Chords ---
        listOf(0, 4, 7, 9) to ("6" to Pair("maj6 / Major 6 / M6", "1 - 3 - 5 - 6")),
        listOf(0, 3, 7, 9) to ("m6" to Pair("min6 / Minor 6 / -6", "1 - b3 - 5 - 6")),
        listOf(0, 2, 4, 7, 9) to ("6/9" to Pair("maj6(add9) / 69", "1 - 3 - 5 - 6 - 9")),
        listOf(0, 2, 3, 7, 9) to ("m6/9" to Pair("min6(add9) / -69", "1 - b3 - 5 - 6 - 9")),

        // --- 4. 9th Chords ---
        listOf(0, 2, 4, 7, 10) to ("9" to Pair("dom9 / Dominant 9", "1 - 3 - 5 - b7 - 9")),
        listOf(0, 2, 4, 7, 11) to ("maj9" to Pair("M9 / Maj9 / Δ9", "1 - 3 - 5 - 7 - 9")),
        listOf(0, 2, 3, 7, 10) to ("m9" to Pair("min9 / -9 / m9", "1 - b3 - 5 - b7 - 9")),
        listOf(0, 2, 3, 7, 11) to ("m(maj9)" to Pair("min(M9) / -Δ9", "1 - b3 - 5 - 7 - 9")),
        listOf(0, 1, 4, 7, 10) to ("7b9" to Pair("dom7(b9) / 7(-9)", "1 - 3 - 5 - b7 - b9")),
        listOf(0, 3, 4, 7, 10) to ("7#9" to Pair("Hendrix Chord / 7(+9)", "1 - 3 - 5 - b7 - #9")),
        listOf(0, 2, 3, 6, 10) to ("m9b5" to Pair("ø9 / Half-Dim 9", "1 - b3 - b5 - b7 - 9")),
        listOf(0, 2, 4, 8, 10) to ("9#5" to Pair("9+5 / aug9", "1 - 3 - #5 - b7 - 9")),
        listOf(0, 2, 4, 6, 10) to ("9b5" to Pair("9-5", "1 - 3 - b5 - b7 - 9")),
        listOf(0, 2, 5, 7, 10) to ("9sus4" to Pair("9sus / 7sus4(9)", "1 - 4 - 5 - b7 - 9")),

        // --- 5. 11th Extended Chords ---
        listOf(0, 2, 4, 5, 7, 10) to ("11" to Pair("dom11 / Dominant 11", "1 - 3 - 5 - b7 - 9 - 11")),
        listOf(0, 2, 3, 5, 7, 10) to ("m11" to Pair("min11 / -11", "1 - b3 - 5 - b7 - 9 - 11")),
        listOf(0, 2, 4, 5, 7, 11) to ("maj11" to Pair("M11 / Maj11 / Δ11", "1 - 3 - 5 - 7 - 9 - 11")),
        listOf(0, 2, 4, 6, 7, 10) to ("7#11" to Pair("7(+11) / Lydian Dom 7", "1 - 3 - 5 - b7 - 9 - #11")),
        listOf(0, 2, 4, 6, 7, 11) to ("maj7#11" to Pair("M7#11 / Lydian / Δ#11", "1 - 3 - 5 - 7 - 9 - #11")),
        listOf(0, 2, 3, 5, 6, 10) to ("m11b5" to Pair("ø11 / Half-Dim 11", "1 - b3 - b5 - b7 - 9 - 11")),
        listOf(0, 1, 4, 5, 7, 10) to ("11b9" to Pair("dom11(b9)", "1 - 3 - 5 - b7 - b9 - 11")),

        // --- 6. 13th Extended Chords ---
        listOf(0, 2, 4, 7, 9, 10) to ("13" to Pair("dom13 / Dominant 13", "1 - 3 - 5 - b7 - 9 - 13")),
        listOf(0, 2, 4, 7, 9, 11) to ("maj13" to Pair("M13 / Maj13 / Δ13", "1 - 3 - 5 - 7 - 9 - 13")),
        listOf(0, 2, 3, 7, 9, 10) to ("m13" to Pair("min13 / -13", "1 - b3 - 5 - b7 - 9 - 13")),
        listOf(0, 1, 4, 7, 9, 10) to ("13b9" to Pair("dom13(b9)", "1 - 3 - 5 - b7 - b9 - 13")),
        listOf(0, 3, 4, 7, 9, 10) to ("13#9" to Pair("dom13(#9)", "1 - 3 - 5 - b7 - #9 - 13")),
        listOf(0, 2, 4, 6, 9, 10) to ("13#11" to Pair("dom13(#11) / Lydian 13", "1 - 3 - 5 - b7 - 9 - #11 - 13")),
        listOf(0, 2, 4, 8, 9, 10) to ("7b13" to Pair("7(b13) / 7(+5)", "1 - 3 - 5 - b7 - b13")),
        listOf(0, 2, 5, 7, 9, 10) to ("13sus4" to Pair("13sus", "1 - 4 - 5 - b7 - 9 - 13")),

        // --- 7. Altered Chords (Jazz Super Locrian) ---
        listOf(0, 1, 4, 8, 10) to ("7alt" to Pair("7(b9,b13) / Super Locrian", "1 - 3 - #5 - b7 - b9")),
        listOf(0, 3, 4, 8, 10) to ("7alt(#9)" to Pair("7(#9,b13) / Altered Dom", "1 - 3 - #5 - b7 - #9")),
        listOf(0, 1, 4, 6, 10) to ("7b9b5" to Pair("7(b9,b5)", "1 - 3 - b5 - b7 - b9")),
        listOf(0, 3, 4, 6, 10) to ("7#9b5" to Pair("7(#9,b5)", "1 - 3 - b5 - b7 - #9")),

        // --- 8. Jazz Voicings with Omitted 5th (no5) ---
        listOf(0, 4, 10) to ("7(no5)" to Pair("Septième Shell Voicing", "1 - 3 - b7")),
        listOf(0, 4, 11) to ("maj7(no5)" to Pair("M7 Shell Voicing / Δ(no5)", "1 - 3 - 7")),
        listOf(0, 3, 10) to ("m7(no5)" to Pair("min7 Shell / -7(no5)", "1 - b3 - b7")),
        listOf(0, 2, 4, 10) to ("9(no5)" to Pair("dom9 Jazz Voicing", "1 - 3 - b7 - 9")),
        listOf(0, 2, 4, 11) to ("maj9(no5)" to Pair("M9 Jazz Voicing / Δ9(no5)", "1 - 3 - 7 - 9")),
        listOf(0, 2, 3, 10) to ("m9(no5)" to Pair("min9 Jazz Voicing / -9(no5)", "1 - b3 - b7 - 9")),
        listOf(0, 1, 4, 10) to ("7b9(no5)" to Pair("7(b9) Jazz Voicing", "1 - 3 - b7 - b9")),
        listOf(0, 3, 4, 10) to ("7#9(no5)" to Pair("7(#9) Jazz Voicing", "1 - 3 - b7 - #9")),
        listOf(0, 4, 9, 10) to ("13(no5)" to Pair("dom13 Shell Voicing", "1 - 3 - b7 - 13")),
        listOf(0, 2, 4, 9, 10) to ("13(no5,9)" to Pair("13 Jazz Rootless/Voicing", "1 - 3 - b7 - 9 - 13")),
        listOf(0, 4, 9, 11) to ("maj13(no5)" to Pair("M13 Shell Voicing / Δ13", "1 - 3 - 7 - 13")),
        listOf(0, 3, 9, 10) to ("m13(no5)" to Pair("min13 Shell Voicing / -13", "1 - b3 - b7 - 13")),
        listOf(0, 2, 3, 5, 10) to ("m11(no5)" to Pair("min11 Jazz Voicing", "1 - b3 - b7 - 9 - 11")),

        // --- 9. Dyads / Open Power Chords ---
        listOf(0, 7) to ("5" to Pair("Power Chord / Quinte pure", "1 - 5")),
        listOf(0, 4) to ("(no5)" to Pair("Tierce Majeure", "1 - 3")),
        listOf(0, 3) to ("m(no5)" to Pair("Tierce Mineure", "1 - b3"))
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
                alternateNames = "Note fondamentale",
                formula = "1",
                notesList = listOf(rootName)
            )
        }

        // Test each unique pitch as potential chord root
        for (rootPc in uniquePitchClasses) {
            val rootName = NOTE_NAMES[rootPc]
            val intervals = uniquePitchClasses.map { (it - rootPc + 12) % 12 }.sorted()

            CHORD_DEFINITIONS[intervals]?.let { (suffix, extra) ->
                val (alt, formula) = extra
                val isSlash = rootPc != lowestPitchClass
                val baseChord = "$rootName$suffix"
                val finalPrimary = if (isSlash) "$baseChord/$lowestNoteName" else baseChord
                
                val altParts = alt.split(" / ")
                val finalAlt1 = if (isSlash) "${rootName} ${altParts[0]} / $lowestNoteName" else "${rootName} ${altParts[0]}"
                val finalAlt2 = if (altParts.size > 1) {
                    if (isSlash) "${rootName} ${altParts[1]} / $lowestNoteName" else "${rootName} ${altParts[1]}"
                } else ""

                val notesFormatted = uniquePitchClasses.map { NOTE_NAMES[it] }.joinToString(" · ")

                return DetectedChord(
                    primaryName = finalPrimary,
                    alternateNames = "$finalAlt1 — [$formula]",
                    alternateName2 = finalAlt2,
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
            alternateNames = "Cluster harmonique",
            formula = clusterStr,
            notesList = uniquePitchClasses.map { NOTE_NAMES[it] }
        )
    }
}
