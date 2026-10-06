package com.soundstage.mixer.utils

enum class NotationSystem(val label: String, val shortName: String) {
    NASHVILLE("Nashville Numbers", "Nashville"),
    STANDARD("Standard Chords", "Standard"),
    ROMAN("Roman Numerals", "Roman"),
    SOLFEGE("Solfège (Do-Ré-Mi)", "Solfège");

    fun next(): NotationSystem = when (this) {
        NASHVILLE -> STANDARD
        STANDARD -> ROMAN
        ROMAN -> SOLFEGE
        SOLFEGE -> NASHVILLE
    }
}

object ChordNotationConverter {
    val roots = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

    private val chromaticRoots = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val flatRoots = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    val solfegeNames = mapOf(
        "C" to "Do", "C#" to "Do#", "Db" to "Réb",
        "D" to "Ré", "D#" to "Ré#", "Eb" to "Mib",
        "E" to "Mi",
        "F" to "Fa", "F#" to "Fa#", "Gb" to "Solb",
        "G" to "Sol", "G#" to "Sol#", "Ab" to "Lab",
        "A" to "La", "A#" to "La#", "Bb" to "Sib",
        "B" to "Si"
    )

    // Complete rich chord extensions palette requested by user
    val fullExtensions = listOf(
        "m", "7", "maj7", "11", "13", "b", "#",
        "m7", "sus2", "sus4", "add9",
        "9", "m9", "maj9", "m11", "m13",
        "dim", "dim7", "aug", "6", "m6", "6/9", "7b5", "7#5", "7b9", "7#9", "/bass"
    )

    // Major scale degrees: I, ii, iii, IV, V, vi, vii°
    private val majorScaleIntervals = listOf(0, 2, 4, 5, 7, 9, 11)
    private val majorDegreeSuffixes = listOf("", "m", "m", "", "", "m", "dim")
    private val romanNumeralsMajor = listOf("I", "ii", "iii", "IV", "V", "vi", "vii°")
    private val nashvilleMajor = listOf("1", "2m", "3m", "4", "5", "6m", "7°")

    // Natural Minor scale degrees: i, ii°, III, iv, v (or V), VI, VII (or vii°)
    private val minorScaleIntervals = listOf(0, 2, 3, 5, 7, 8, 10)
    private val minorDegreeSuffixes = listOf("m", "dim", "", "m", "m", "", "")
    private val romanNumeralsMinor = listOf("i", "ii°", "III", "iv", "v", "VI", "VII")
    private val nashvilleMinor = listOf("1m", "2°", "3", "4m", "5m", "6", "7")

    /**
     * Generates diatonic chords for given root key and mode (Major / Minor),
     * converted automatically to target NotationSystem.
     */
    fun getDiatonicChords(
        rootKey: String,
        isMinor: Boolean = false,
        system: NotationSystem = NotationSystem.NASHVILLE
    ): List<String> {
        val rootIndex = rootToSemitone(rootKey)
        val intervals = if (isMinor) minorScaleIntervals else majorScaleIntervals
        val suffixes = if (isMinor) minorDegreeSuffixes else majorDegreeSuffixes

        return intervals.mapIndexed { degree, interval ->
            val semitone = (rootIndex + interval) % 12
            val noteName = getNoteSpelling(semitone, rootKey)
            val suffix = suffixes[degree]
            val standardChord = "$noteName$suffix"

            when (system) {
                NotationSystem.STANDARD -> standardChord
                NotationSystem.ROMAN -> if (isMinor) romanNumeralsMinor[degree] else romanNumeralsMajor[degree]
                NotationSystem.NASHVILLE -> if (isMinor) nashvilleMinor[degree] else nashvilleMajor[degree]
                NotationSystem.SOLFEGE -> {
                    val solfegeRoot = solfegeNames[noteName] ?: noteName
                    "$solfegeRoot$suffix"
                }
            }
        }
    }

    /**
     * Applies an extension intelligently to a base chord or active chord symbol.
     */
    fun applyExtension(baseChord: String, ext: String): String {
        val trimmed = baseChord.trim()
        if (trimmed.isEmpty()) return ext
        val cleanExt = ext.trim()

        if (cleanExt.startsWith("/")) {
            return "$trimmed$cleanExt"
        }

        // If chord is in Nashville notation (e.g. "1", "2m", "6m")
        if (trimmed.all { it.isDigit() || it == 'm' || it == '°' || it == '#' || it == 'b' }) {
            val baseNumber = trimmed.filter { it.isDigit() }
            val hasMinor = trimmed.contains("m")
            return when (cleanExt) {
                "7" -> if (hasMinor) "${baseNumber}m7" else "${baseNumber}7"
                "maj7" -> "${baseNumber}maj7"
                "m7" -> "${baseNumber}m7"
                "sus2" -> "${baseNumber}sus2"
                "sus4" -> "${baseNumber}sus4"
                "add9" -> if (hasMinor) "${baseNumber}m(add9)" else "${baseNumber}add9"
                "9" -> if (hasMinor) "${baseNumber}m9" else "${baseNumber}9"
                "11" -> if (hasMinor) "${baseNumber}m11" else "${baseNumber}11"
                "13" -> if (hasMinor) "${baseNumber}m13" else "${baseNumber}13"
                "b" -> if (trimmed.startsWith("#")) "b" + trimmed.removePrefix("#") else if (trimmed.startsWith("b")) trimmed else "b$trimmed"
                "#" -> if (trimmed.startsWith("b")) "#" + trimmed.removePrefix("b") else if (trimmed.startsWith("#")) trimmed else "#$trimmed"
                "dim", "dim7" -> "${baseNumber}dim"
                "aug" -> "${baseNumber}aug"
                "6" -> if (hasMinor) "${baseNumber}m6" else "${baseNumber}6"
                else -> "$trimmed$cleanExt"
            }
        }

        // Standard chord root extraction
        val root = extractRoot(trimmed) ?: trimmed
        var suffix = trimmed.substring(root.length)

        when (cleanExt) {
            "b" -> {
                return if (root.endsWith("#")) {
                    root.dropLast(1) + suffix
                } else if (root.endsWith("b")) {
                    root + suffix
                } else {
                    "${root}b$suffix"
                }
            }
            "#" -> {
                return if (root.endsWith("b")) {
                    root.dropLast(1) + suffix
                } else if (root.endsWith("#")) {
                    root + suffix
                } else {
                    "${root}#$suffix"
                }
            }
            "7" -> {
                suffix = if (suffix.startsWith("m") && !suffix.startsWith("maj")) "m7"
                else if (suffix.startsWith("dim")) "dim7"
                else "7"
            }
            "maj7" -> suffix = "maj7"
            "m7" -> suffix = "m7"
            "m" -> suffix = if (suffix.startsWith("m")) "" else "m"
            "sus2" -> suffix = "sus2"
            "sus4" -> suffix = "sus4"
            "add9" -> suffix = if (suffix.startsWith("m")) "m(add9)" else "add9"
            "9" -> suffix = if (suffix.startsWith("m")) "m9" else "9"
            "m9" -> suffix = "m9"
            "maj9" -> suffix = "maj9"
            "11" -> suffix = if (suffix.startsWith("m") && !suffix.startsWith("maj")) "m11" else "11"
            "m11" -> suffix = "m11"
            "13" -> suffix = if (suffix.startsWith("m") && !suffix.startsWith("maj")) "m13" else "13"
            "m13" -> suffix = "m13"
            "dim", "dim7" -> suffix = cleanExt
            "aug" -> suffix = "aug"
            "6" -> suffix = if (suffix.startsWith("m")) "m6" else "6"
            "m6" -> suffix = "m6"
            "6/9" -> suffix = "6/9"
            else -> suffix = cleanExt
        }

        return "$root$suffix"
    }

    /**
     * Converts a single chord into target notation system given the song key and mode.
     */
    fun convertChord(
        chord: String,
        songKey: String,
        targetSystem: NotationSystem,
        isMinor: Boolean = false
    ): String {
        val trimmed = chord.trim()
        if (trimmed.isEmpty()) return ""
        if (targetSystem == NotationSystem.STANDARD) return trimmed

        val root = extractRoot(trimmed) ?: return trimmed
        val suffix = trimmed.substring(root.length)

        val keyRoot = extractRoot(songKey.trim()) ?: "C"
        val keySemitone = rootToSemitone(keyRoot)
        val chordSemitone = rootToSemitone(root)
        val interval = (chordSemitone - keySemitone + 12) % 12

        val intervals = if (isMinor) minorScaleIntervals else majorScaleIntervals
        val scaleDegree = intervals.indexOf(interval)

        return when (targetSystem) {
            NotationSystem.STANDARD -> trimmed
            NotationSystem.SOLFEGE -> {
                val solfegeRoot = solfegeNames[root] ?: root
                "$solfegeRoot$suffix"
            }
            NotationSystem.ROMAN -> {
                if (scaleDegree != -1) {
                    val baseRoman = if (isMinor) romanNumeralsMinor[scaleDegree] else romanNumeralsMajor[scaleDegree]
                    val isMinorChord = suffix.startsWith("m") && !suffix.startsWith("maj")
                    val adjustedRoman = if (isMinorChord) baseRoman.lowercase() else baseRoman.uppercase()
                    val cleanSuffix = if (isMinorChord) suffix.removePrefix("m") else suffix
                    "$adjustedRoman$cleanSuffix"
                } else {
                    "bVII$suffix"
                }
            }
            NotationSystem.NASHVILLE -> {
                if (scaleDegree != -1) {
                    val isMinorChord = suffix.startsWith("m") && !suffix.startsWith("maj")
                    val cleanSuffix = if (isMinorChord) suffix.removePrefix("m") else suffix
                    val baseNumber = (scaleDegree + 1).toString()
                    val numberLabel = if (isMinorChord) "${baseNumber}m" else baseNumber
                    "$numberLabel$cleanSuffix"
                } else {
                    "b7$suffix"
                }
            }
        }
    }

    /**
     * Converts entire song text so chords in brackets or chord lines display in the active notation.
     */
    fun convertSheetText(
        text: String,
        songKey: String,
        targetSystem: NotationSystem,
        isMinor: Boolean = false
    ): String {
        if (targetSystem == NotationSystem.STANDARD || text.isEmpty()) return text

        val chordPattern = Regex("\\b([A-G][#b]?(?:m|maj|dim|aug|sus[24]|add\\d+|[0-9]+)*(?:/[A-G][#b]?)?)\\b")
        return text.lines().joinToString("\n") { line ->
            if (line.trim().startsWith("[") && line.contains("]")) {
                val header = line.substringBefore("]") + "]"
                val rest = line.substringAfter("]")
                header + chordPattern.replace(rest) { matchResult ->
                    convertChord(matchResult.value, songKey, targetSystem, isMinor)
                }
            } else {
                // If line looks like a chord line (separated by dots or spaces)
                chordPattern.replace(line) { matchResult ->
                    convertChord(matchResult.value, songKey, targetSystem, isMinor)
                }
            }
        }
    }

    fun extractRoot(chord: String): String? {
        if (chord.length >= 2 && (chord[1] == '#' || chord[1] == 'b')) {
            val twoChar = chord.substring(0, 2)
            if (twoChar[0].uppercaseChar() in 'A'..'G') return twoChar
        }
        if (chord.isNotEmpty() && chord[0].uppercaseChar() in 'A'..'G') {
            return chord.substring(0, 1).uppercase()
        }
        return null
    }

    private fun getNoteSpelling(semitone: Int, keyContext: String): String {
        val useFlats = keyContext.contains("b") || keyContext in listOf("F", "Bb", "Eb", "Ab", "Db", "Gb")
        return if (useFlats) {
            flatRoots[semitone % 12]
        } else {
            chromaticRoots[semitone % 12]
        }
    }

    fun rootToSemitone(root: String): Int {
        val clean = extractRoot(root)?.uppercase() ?: root.uppercase()
        val idx = chromaticRoots.indexOf(clean)
        if (idx != -1) return idx
        val flatIdx = flatRoots.indexOfFirst { it.equals(clean, ignoreCase = true) }
        if (flatIdx != -1) return flatIdx
        return 0
    }
}
