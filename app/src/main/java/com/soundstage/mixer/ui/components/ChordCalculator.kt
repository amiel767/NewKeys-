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
    val equivalentName: String,
    val variantName: String = "",
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
        listOf(0, 4, 7) to Triple("", "Maj", "1 - 3 - 5"),
        listOf(0, 3, 7) to Triple("m", "Min", "1 - b3 - 5"),
        listOf(0, 3, 6) to Triple("dim", "°", "1 - b3 - b5"),
        listOf(0, 4, 8) to Triple("aug", "+", "1 - 3 - #5"),
        listOf(0, 5, 7) to Triple("sus4", "sus", "1 - 4 - 5"),
        listOf(0, 2, 7) to Triple("sus2", "sus2", "1 - 2 - 5"),
        listOf(0, 2, 5, 7) to Triple("sus2sus4", "sus24", "1 - 2 - 4 - 5"),
        listOf(0, 2, 4, 7) to Triple("add9", "add2", "1 - 2 - 3 - 5"),
        listOf(0, 2, 3, 7) to Triple("m(add9)", "m(add2)", "1 - 2 - b3 - 5"),
        listOf(0, 4, 5, 7) to Triple("add11", "add4", "1 - 3 - 4 - 5"),
        listOf(0, 3, 5, 7) to Triple("m(add11)", "m(add4)", "1 - b3 - 4 - 5"),

        // --- 2. 7th Chords ---
        listOf(0, 4, 7, 10) to Triple("7", "dom7", "1 - 3 - 5 - b7"),
        listOf(0, 4, 7, 11) to Triple("maj7", "Δ7", "1 - 3 - 5 - 7"),
        listOf(0, 3, 7, 10) to Triple("m7", "-7", "1 - b3 - 5 - b7"),
        listOf(0, 3, 7, 11) to Triple("m(maj7)", "-Δ7", "1 - b3 - 5 - 7"),
        listOf(0, 3, 6, 10) to Triple("m7b5", "ø7", "1 - b3 - b5 - b7"),
        listOf(0, 3, 6, 9) to Triple("dim7", "°7", "1 - b3 - b5 - bb7"),
        listOf(0, 4, 8, 10) to Triple("7#5", "7+", "1 - 3 - #5 - b7"),
        listOf(0, 4, 6, 10) to Triple("7b5", "7(b5)", "1 - 3 - b5 - b7"),
        listOf(0, 4, 8, 11) to Triple("maj7#5", "Δ#5", "1 - 3 - #5 - 7"),
        listOf(0, 4, 6, 11) to Triple("maj7b5", "Δb5", "1 - 3 - b5 - 7"),
        listOf(0, 5, 7, 10) to Triple("7sus4", "7sus", "1 - 4 - 5 - b7"),
        listOf(0, 2, 7, 10) to Triple("7sus2", "sus2(7)", "1 - 2 - 5 - b7"),

        // --- 3. 6th & 6/9 Chords ---
        listOf(0, 4, 7, 9) to Triple("6", "maj6", "1 - 3 - 5 - 6"),
        listOf(0, 3, 7, 9) to Triple("m6", "-6", "1 - b3 - 5 - 6"),
        listOf(0, 2, 4, 7, 9) to Triple("6/9", "6(add9)", "1 - 3 - 5 - 6 - 9"),
        listOf(0, 2, 3, 7, 9) to Triple("m6/9", "-6(add9)", "1 - b3 - 5 - 6 - 9"),

        // --- 4. 9th Chords ---
        listOf(0, 2, 4, 7, 10) to Triple("9", "dom9", "1 - 3 - 5 - b7 - 9"),
        listOf(0, 2, 4, 7, 11) to Triple("maj9", "Δ9", "1 - 3 - 5 - 7 - 9"),
        listOf(0, 2, 3, 7, 10) to Triple("m9", "-9", "1 - b3 - 5 - b7 - 9"),
        listOf(0, 2, 3, 7, 11) to Triple("m(maj9)", "-Δ9", "1 - b3 - 5 - 7 - 9"),
        listOf(0, 1, 4, 7, 10) to Triple("7b9", "7(b9)", "1 - 3 - 5 - b7 - b9"),
        listOf(0, 3, 4, 7, 10) to Triple("7#9", "7(#9)", "1 - 3 - 5 - b7 - #9"),
        listOf(0, 2, 3, 6, 10) to Triple("m9b5", "ø9", "1 - b3 - b5 - b7 - 9"),
        listOf(0, 2, 4, 8, 10) to Triple("9#5", "9(+5)", "1 - 3 - #5 - b7 - 9"),
        listOf(0, 2, 4, 6, 10) to Triple("9b5", "9(b5)", "1 - 3 - b5 - b7 - 9"),
        listOf(0, 2, 5, 7, 10) to Triple("9sus4", "9sus", "1 - 4 - 5 - b7 - 9"),

        // --- 5. 11th Extended Chords ---
        listOf(0, 2, 4, 5, 7, 10) to Triple("11", "dom11", "1 - 3 - 5 - b7 - 9 - 11"),
        listOf(0, 2, 3, 5, 7, 10) to Triple("m11", "-11", "1 - b3 - 5 - b7 - 9 - 11"),
        listOf(0, 2, 4, 5, 7, 11) to Triple("maj11", "Δ11", "1 - 3 - 5 - 7 - 9 - 11"),
        listOf(0, 2, 4, 6, 7, 10) to Triple("7#11", "7(+11)", "1 - 3 - 5 - b7 - 9 - #11"),
        listOf(0, 2, 4, 6, 7, 11) to Triple("maj7#11", "Δ#11", "1 - 3 - 5 - 7 - 9 - #11"),
        listOf(0, 2, 3, 5, 6, 10) to Triple("m11b5", "ø11", "1 - b3 - b5 - b7 - 9 - 11"),
        listOf(0, 1, 4, 5, 7, 10) to Triple("11b9", "11(b9)", "1 - 3 - 5 - b7 - b9 - 11"),

        // --- 6. 13th Extended Chords ---
        listOf(0, 2, 4, 7, 9, 10) to Triple("13", "dom13", "1 - 3 - 5 - b7 - 9 - 13"),
        listOf(0, 2, 4, 7, 9, 11) to Triple("maj13", "Δ13", "1 - 3 - 5 - 7 - 9 - 13"),
        listOf(0, 2, 3, 7, 9, 10) to Triple("m13", "-13", "1 - b3 - 5 - b7 - 9 - 13"),
        listOf(0, 1, 4, 7, 9, 10) to Triple("13b9", "13(b9)", "1 - 3 - 5 - b7 - b9 - 13"),
        listOf(0, 3, 4, 7, 9, 10) to Triple("13#9", "13(#9)", "1 - 3 - 5 - b7 - #9 - 13"),
        listOf(0, 2, 4, 6, 9, 10) to Triple("13#11", "13(#11)", "1 - 3 - 5 - b7 - 9 - #11 - 13"),
        listOf(0, 2, 4, 8, 9, 10) to Triple("7b13", "7(b13)", "1 - 3 - 5 - b7 - b13"),
        listOf(0, 2, 5, 7, 9, 10) to Triple("13sus4", "13sus", "1 - 4 - 5 - b7 - 9 - 13"),

        // --- 7. Altered Chords (Jazz Super Locrian) ---
        listOf(0, 1, 4, 8, 10) to Triple("7alt", "7(b9,b13)", "1 - 3 - #5 - b7 - b9"),
        listOf(0, 3, 4, 8, 10) to Triple("7alt(#9)", "7(#9,b13)", "1 - 3 - #5 - b7 - #9"),
        listOf(0, 1, 4, 6, 10) to Triple("7b9b5", "7(b9,b5)", "1 - 3 - b5 - b7 - b9"),
        listOf(0, 3, 4, 6, 10) to Triple("7#9b5", "7(#9,b5)", "1 - 3 - b5 - b7 - #9"),

        // --- 8. Jazz Voicings with Omitted 5th (no5) ---
        listOf(0, 4, 10) to Triple("7(no5)", "dom7", "1 - 3 - b7"),
        listOf(0, 4, 11) to Triple("maj7(no5)", "Δ7", "1 - 3 - 7"),
        listOf(0, 3, 10) to Triple("m7(no5)", "-7", "1 - b3 - b7"),
        listOf(0, 3, 11) to Triple("m(maj7)(no5)", "-Δ7", "1 - b3 - 7"),
        listOf(0, 2, 10) to Triple("7sus2(no5)", "7sus2", "1 - 2 - b7"),
        listOf(0, 5, 10) to Triple("7sus4(no5)", "7sus", "1 - 4 - b7"),

        // --- 9. Two-note Dyads / Power Chords ---
        listOf(0, 7) to Triple("5", "P5", "1 - 5"),
        listOf(0, 4) to Triple("(no5)", "3rd", "1 - 3"),
        listOf(0, 3) to Triple("m(no5)", "m3rd", "1 - b3"),
        listOf(0, 5) to Triple("sus4(no5)", "4th", "1 - 4"),
        listOf(0, 2) to Triple("sus2(no5)", "2nd", "1 - 2")
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
     * Computes BOTH:
     * - Primary Name (e.g. Slash / Bass representation or direct name like F/D)
     * - Equivalent Name (e.g. Functional root chord like Dm7)
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
                equivalentName = "",
                variantName = "",
                formula = "1",
                notesList = listOf(rootName)
            )
        }

        // Test potential chord roots and collect candidate interpretations
        val candidateChords = mutableListOf<Triple<Int, String, String>>() // (rootPc, chordName, formula)

        for (rootPc in uniquePitchClasses) {
            val rootName = NOTE_NAMES[rootPc]
            val intervals = uniquePitchClasses.map { (it - rootPc + 12) % 12 }.sorted()

            CHORD_DEFINITIONS[intervals]?.let { (suffix, _, formula) ->
                candidateChords.add(Triple(rootPc, "$rootName$suffix", formula))
            }
        }

        val notesFormatted = uniquePitchClasses.map { NOTE_NAMES[it] }.joinToString(" · ")

        if (candidateChords.isNotEmpty()) {
            // Find root-based chord matching the lowest bass note if possible
            val bassCandidate = candidateChords.firstOrNull { it.first == lowestPitchClass }
            // Find inverted candidate (where root != lowest bass)
            val nonBassCandidate = candidateChords.firstOrNull { it.first != lowestPitchClass }

            val primaryName: String
            val equivalentName: String
            val variantName: String

            if (nonBassCandidate != null && bassCandidate != null && nonBassCandidate.first != bassCandidate.first) {
                // Inverted or polychord voicing: e.g. bass D with F major triad -> Dm7 vs F/D
                primaryName = bassCandidate.second
                equivalentName = "${nonBassCandidate.second}/$lowestNoteName"
                variantName = nonBassCandidate.second
            } else if (bassCandidate != null) {
                primaryName = bassCandidate.second
                // Genuine music-theory pro alternate notation / substitution
                equivalentName = when {
                    bassCandidate.second.endsWith("maj7") -> bassCandidate.second.replace("maj7", "Δ7")
                    bassCandidate.second.endsWith("m7") -> bassCandidate.second.replace("m7", "-7")
                    bassCandidate.second.endsWith("7") && !bassCandidate.second.endsWith("maj7") && !bassCandidate.second.endsWith("m7") -> bassCandidate.second.replace("7", "dom7")
                    bassCandidate.second.endsWith("m7b5") -> bassCandidate.second.replace("m7b5", "ø7")
                    bassCandidate.second.endsWith("dim7") -> bassCandidate.second.replace("dim7", "°7")
                    bassCandidate.second.endsWith("dim") -> bassCandidate.second.replace("dim", "°")
                    bassCandidate.second.endsWith("aug") -> bassCandidate.second.replace("aug", "+")
                    bassCandidate.second.endsWith("maj9") -> bassCandidate.second.replace("maj9", "Δ9")
                    bassCandidate.second.endsWith("m9") -> bassCandidate.second.replace("m9", "-9")
                    bassCandidate.second.endsWith("9") && !bassCandidate.second.endsWith("maj9") && !bassCandidate.second.endsWith("m9") -> bassCandidate.second.replace("9", "dom9")
                    bassCandidate.second.endsWith("maj11") -> bassCandidate.second.replace("maj11", "Δ11")
                    bassCandidate.second.endsWith("m11") -> bassCandidate.second.replace("m11", "-11")
                    bassCandidate.second.endsWith("maj13") -> bassCandidate.second.replace("maj13", "Δ13")
                    bassCandidate.second.endsWith("m13") -> bassCandidate.second.replace("m13", "-13")
                    bassCandidate.second.endsWith("sus4") -> "${NOTE_NAMES[(lowestPitchClass + 5) % 12]}sus2/$lowestNoteName"
                    bassCandidate.second.endsWith("sus2") -> "${NOTE_NAMES[(lowestPitchClass + 7) % 12]}sus4/$lowestNoteName"
                    bassCandidate.second.endsWith("6") -> "${NOTE_NAMES[(lowestPitchClass + 9) % 12]}m7/$lowestNoteName"
                    bassCandidate.second.endsWith("m6") -> "${NOTE_NAMES[(lowestPitchClass + 9) % 12]}m7b5/$lowestNoteName"
                    bassCandidate.second.endsWith("m") -> "${bassCandidate.second} Min"
                    bassCandidate.second == lowestNoteName -> "$lowestNoteName Maj"
                    else -> ""
                }
                variantName = if (equivalentName.isNotEmpty()) equivalentName else bassCandidate.third
            } else {
                // Inverted slash chord (lowest bass is an inversion note)
                val topCandidate = candidateChords.first()
                primaryName = "${topCandidate.second}/$lowestNoteName"
                equivalentName = topCandidate.second
                variantName = topCandidate.second
            }

            return DetectedChord(
                primaryName = primaryName,
                equivalentName = equivalentName,
                variantName = variantName,
                formula = notesFormatted,
                notesList = uniquePitchClasses.map { NOTE_NAMES[it] }
            )
        }

        // Voicing approximation for 3+ notes
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
                hasMaj3 && hasDom7 && has13th -> "13"
                hasMaj3 && hasMaj7 && has13th -> "maj13"
                hasMin3 && hasDom7 && has11th -> "m11"
                hasMaj3 && hasDom7 && has9th -> "9"
                hasMaj3 && hasMaj7 && has9th -> "maj9"
                hasMin3 && hasDom7 && has9th -> "m9"
                hasMaj3 && hasPerf5 && hasMaj7 -> "maj7"
                hasMaj3 && hasPerf5 && hasDom7 -> "7"
                hasMin3 && hasPerf5 && hasDom7 -> "m7"
                hasMaj3 && hasPerf5 -> ""
                hasMin3 && hasPerf5 -> "m"
                else -> ""
            }

            if (inferredSuffix.isNotEmpty()) {
                val notesFormatted = uniquePitchClasses.map { NOTE_NAMES[it] }.joinToString(" · ")
                return DetectedChord(
                    primaryName = "$rootName$inferredSuffix",
                    equivalentName = when (inferredSuffix) {
                        "maj7" -> "${rootName}Δ7"
                        "m7" -> "$rootName-7"
                        "7" -> "$rootName dom7"
                        else -> ""
                    },
                    variantName = "",
                    formula = notesFormatted,
                    notesList = uniquePitchClasses.map { NOTE_NAMES[it] }
                )
            }
        }

        // Clean fallback: root note without fake "Harmonie" or "Cluster"
        val rootName = NOTE_NAMES[lowestPitchClass]
        val clusterStr = uniquePitchClasses.map { NOTE_NAMES[it] }.joinToString(" · ")
        return DetectedChord(
            primaryName = rootName,
            equivalentName = "",
            variantName = "",
            formula = clusterStr,
            notesList = uniquePitchClasses.map { NOTE_NAMES[it] }
        )
    }
}
