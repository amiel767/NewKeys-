package com.soundstage.mixer.ui.components

/**
 * Advanced Music Theory Engine:
 * - Full Degree Recognition (Arabic 1, 2m, 3mineur, 4aug, 5dim, etc. & Roman I, ii, iii, IV, V, vi, vii°)
 * - Roman Numeral Analysis & Converter
 * - Chord Progression Detection (2-5-1, 1-6-2-5, 1-5-6-4, etc.)
 * - Logical Passing Chord ("Accord de passage") Analyzer & Classifier
 */
data class AnalyzedChord(
    val originalChord: String,
    val romanNumeral: String,
    val degreeArabic: String,
    val isDiatonic: Boolean,
    val isPassingChord: Boolean,
    val passingDescription: String? = null
)

data class ProgressionAnalysis(
    val progressionName: String?,
    val chords: List<AnalyzedChord>,
    val passingChordsCount: Int
)

object HarmonicProgressionCalculator {

    private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT_NAMES = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    private val FLAT_TO_SHARP = mapOf("Db" to "C#", "Eb" to "D#", "Gb" to "F#", "Ab" to "G#", "Bb" to "A#")

    fun standardizeNote(note: String): String = FLAT_TO_SHARP[note] ?: note

    fun noteToDisplay(note: String, useFlats: Boolean): String {
        val std = standardizeNote(note)
        val idx = NOTE_NAMES.indexOf(std)
        return if (idx != -1) {
            if (useFlats) FLAT_NAMES[idx] else NOTE_NAMES[idx]
        } else note
    }

    /**
     * Parse any degree input string (e.g. "3mineur", "4aug", "5dim", "ii", "IV#", "bVII")
     * into a semitone offset from root key and chord quality suffix.
     */
    fun parseDegreeToChord(degreeStr: String, rootKey: String, useFlats: Boolean): String? {
        val clean = degreeStr.trim()
        val stdRoot = standardizeNote(rootKey)
        val rootIdx = NOTE_NAMES.indexOf(stdRoot)
        if (rootIdx == -1) return null

        // Diatonic major scale semitone offsets: 1->0, 2->2, 3->4, 4->5, 5->7, 6->9, 7->11
        val scaleOffsets = listOf(0, 2, 4, 5, 7, 9, 11)

        val lower = clean.lowercase()

        // 1. Identify alteration (flat 'b' or sharp '#')
        var accidentalShift = 0
        var core = clean

        if (clean.startsWith("b") || clean.startsWith("♭")) {
            accidentalShift = -1
            core = clean.drop(1)
        } else if (clean.startsWith("#") || clean.startsWith("♯")) {
            accidentalShift = 1
            core = clean.drop(1)
        } else if (clean.endsWith("#") || clean.endsWith("♯")) {
            accidentalShift = 1
            core = clean.dropLast(1)
        } else if (clean.endsWith("b") && !lower.contains("dim") && !lower.contains("sub") && !lower.contains("7b5")) {
            accidentalShift = -1
            core = clean.dropLast(1)
        }

        val coreLower = core.lowercase()

        // 2. Identify Degree Number (1..7) or Roman (I..VII)
        var degreeNum = -1
        var qualitySuffix = ""

        when {
            coreLower.startsWith("1") || coreLower.startsWith("i") && !coreLower.startsWith("iv") && !coreLower.startsWith("ix") && !coreLower.startsWith("ii") && !coreLower.startsWith("iii") -> {
                degreeNum = 1
                qualitySuffix = core.replace(Regex("(?i)^(1|i)"), "")
            }
            coreLower.startsWith("2") || coreLower.startsWith("ii") && !coreLower.startsWith("iii") -> {
                degreeNum = 2
                qualitySuffix = core.replace(Regex("(?i)^(2|ii)"), "")
            }
            coreLower.startsWith("3") || coreLower.startsWith("iii") -> {
                degreeNum = 3
                qualitySuffix = core.replace(Regex("(?i)^(3|iii)"), "")
            }
            coreLower.startsWith("4") || coreLower.startsWith("iv") -> {
                degreeNum = 4
                qualitySuffix = core.replace(Regex("(?i)^(4|iv)"), "")
            }
            coreLower.startsWith("5") || coreLower.startsWith("v") && !coreLower.startsWith("vi") && !coreLower.startsWith("vii") -> {
                degreeNum = 5
                qualitySuffix = core.replace(Regex("(?i)^(5|v)"), "")
            }
            coreLower.startsWith("6") || coreLower.startsWith("vi") && !coreLower.startsWith("vii") -> {
                degreeNum = 6
                qualitySuffix = core.replace(Regex("(?i)^(6|vi)"), "")
            }
            coreLower.startsWith("7") || coreLower.startsWith("vii") -> {
                degreeNum = 7
                qualitySuffix = core.replace(Regex("(?i)^(7|vii)"), "")
            }
        }

        if (degreeNum !in 1..7) return null

        val baseSemitone = scaleOffsets[degreeNum - 1]
        val totalSemitone = (baseSemitone + accidentalShift).mod(12)
        val noteIdx = (rootIdx + totalSemitone).mod(12)
        val targetNote = if (useFlats) FLAT_NAMES[noteIdx] else NOTE_NAMES[noteIdx]

        // 3. Normalize quality suffix ("mineur", "aug", "dim", "m", "maj", etc.)
        val normQuality = when {
            qualitySuffix.matches(Regex("(?i)^(mineur|min|m)$")) -> "m"
            qualitySuffix.matches(Regex("(?i)^(mineur7|min7|m7)$")) -> "m7"
            qualitySuffix.matches(Regex("(?i)^(aug|augmenté|\\+)$")) -> "aug"
            qualitySuffix.matches(Regex("(?i)^(dim|diminué|°)$")) -> "dim"
            qualitySuffix.matches(Regex("(?i)^(dim7|°7)$")) -> "dim7"
            qualitySuffix.matches(Regex("(?i)^(maj7|m7|7|sus4|sus2|add9|9|6|6/9)$")) -> qualitySuffix
            qualitySuffix.matches(Regex("(?i)^(majeur|maj)$")) -> ""
            qualitySuffix.isEmpty() -> {
                // Default diatonic quality
                when (degreeNum) {
                    2, 3, 6 -> if (accidentalShift == 0) "m" else ""
                    7 -> if (accidentalShift == 0) "dim" else ""
                    else -> ""
                }
            }
            else -> qualitySuffix
        }

        return "$targetNote$normQuality"
    }

    /**
     * Convert full text with any degrees (Arabic or Roman) into real Chords in Key.
     */
    fun convertAllDegreesToChords(text: String, rootKey: String, useFlats: Boolean): String {
        // Regex to capture degree tokens:
        // Examples: 1, 2m, 3mineur, 4aug, 5dim, 6mineur, 7dim, b3, #4, b7, I, ii, iii, IV, V, vi, vii°, etc.
        val degreeRegex = Regex("""\b([b#♭♯]?(?:[1-7]|I|i|II|ii|III|iii|IV|iv|V|v|VI|vi|VII|vii)(?:mineur|min|majeur|maj7|maj|aug|dim7|dim|°|sus4|sus2|add9|m7|7|m)?)\b""")
        return degreeRegex.replace(text) { match ->
            val token = match.value
            parseDegreeToChord(token, rootKey, useFlats) ?: token
        }
    }

    /**
     * Given a chord name and a key, determine Roman Numeral and analyze if it's a Passing Chord ("Accord de passage").
     */
    fun analyzeChordInKey(chord: String, rootKey: String, previousChord: String? = null, nextChord: String? = null): AnalyzedChord {
        val stdRootKey = standardizeNote(rootKey)
        val keyRootIdx = NOTE_NAMES.indexOf(stdRootKey)

        val chordRegex = Regex("""^([A-G][b#]?)(.*)$""")
        val match = chordRegex.find(chord)
        if (match == null || keyRootIdx == -1) {
            return AnalyzedChord(chord, chord, chord, isDiatonic = false, isPassingChord = false)
        }

        val rootNote = match.groupValues[1]
        val suffix = match.groupValues[2]
        val stdRootNote = standardizeNote(rootNote)
        val chordRootIdx = NOTE_NAMES.indexOf(stdRootNote)
        if (chordRootIdx == -1) {
            return AnalyzedChord(chord, chord, chord, isDiatonic = false, isPassingChord = false)
        }

        // Semitone interval relative to key (0..11)
        val interval = (chordRootIdx - keyRootIdx).mod(12)

        // Diatonic major scale: 0->I, 2->ii, 4->iii, 5->IV, 7->V, 9->vi, 11->vii°
        val isMinor = suffix.startsWith("m") && !suffix.startsWith("maj")
        val isDim = suffix.contains("dim") || suffix.contains("°") || suffix.contains("m7b5")
        val isAug = suffix.contains("aug") || suffix.contains("+")
        val isDom7 = suffix == "7" || suffix == "9" || suffix == "13"

        var romanBase: String
        var degreeArabic: String
        var isDiatonic = false

        when (interval) {
            0 -> {
                romanBase = if (isMinor) "i" else "I"
                degreeArabic = if (isMinor) "1m" else "1"
                isDiatonic = !isMinor
            }
            1 -> {
                romanBase = "bII"
                degreeArabic = "b2"
            }
            2 -> {
                romanBase = if (isMinor) "ii" else "II"
                degreeArabic = if (isMinor) "2m" else "2"
                isDiatonic = isMinor
            }
            3 -> {
                romanBase = "bIII"
                degreeArabic = "b3"
            }
            4 -> {
                romanBase = if (isMinor) "iii" else "III"
                degreeArabic = if (isMinor) "3m" else "3"
                isDiatonic = isMinor
            }
            5 -> {
                romanBase = if (isMinor) "iv" else "IV"
                degreeArabic = if (isMinor) "4m" else "4"
                isDiatonic = !isMinor
            }
            6 -> {
                romanBase = "#IV"
                degreeArabic = "#4"
            }
            7 -> {
                romanBase = if (isMinor) "v" else "V"
                degreeArabic = if (isMinor) "5m" else "5"
                isDiatonic = !isMinor
            }
            8 -> {
                romanBase = "bVI"
                degreeArabic = "b6"
            }
            9 -> {
                romanBase = if (isMinor) "vi" else "VI"
                degreeArabic = if (isMinor) "6m" else "6"
                isDiatonic = isMinor
            }
            10 -> {
                romanBase = "bVII"
                degreeArabic = "b7"
            }
            11 -> {
                romanBase = if (isDim) "vii°" else "VII"
                degreeArabic = if (isDim) "7dim" else "7"
                isDiatonic = isDim
            }
            else -> {
                romanBase = "?"
                degreeArabic = "?"
            }
        }

        // Add extensions to Roman Numeral
        val romanExtension = when {
            suffix.startsWith("maj7") -> "maj7"
            suffix.startsWith("m7") -> "7"
            suffix == "7" -> "7"
            suffix == "dim7" || suffix == "°7" -> "°7"
            isDim && !romanBase.contains("°") -> "°"
            isAug -> "+"
            suffix.startsWith("sus4") -> "sus4"
            suffix.startsWith("add9") -> "(add9)"
            else -> ""
        }

        val fullRoman = "$romanBase$romanExtension"

        // ================= PASSING CHORD (ACCORD DE PASSAGE) DETECTION =================
        var isPassing = false
        var passingDesc: String? = null

        // 1. Secondary Dominant (V7 / X): Non-diatonic dominant 7th resolving down a fifth / up a fourth
        if (isDom7 || suffix.isEmpty()) {
            when (interval) {
                9 -> { // A7 in Key C -> resolves to Dm (ii)
                    isPassing = true
                    passingDesc = "Dominante secondaire (V/ii)"
                }
                4 -> { // E7 in Key C -> resolves to Am (vi)
                    isPassing = true
                    passingDesc = "Dominante secondaire (V/vi)"
                }
                2 -> { // D7 in Key C -> resolves to G (V)
                    isPassing = true
                    passingDesc = "Dominante secondaire (V/V)"
                }
                11 -> { // B7 in Key C -> resolves to Em (iii)
                    isPassing = true
                    passingDesc = "Dominante secondaire (V/iii)"
                }
                0 -> { // C7 in Key C -> resolves to F (IV)
                    if (suffix.contains("7")) {
                        isPassing = true
                        passingDesc = "Dominante secondaire (V/IV)"
                    }
                }
            }
        }

        // 2. Chromatic Passing Diminished 7th chords:
        if (isDim) {
            when (interval) {
                1 -> { // C#dim7 leading to Dm
                    isPassing = true
                    passingDesc = "Accord de passage chromatique (#I°7 → ii)"
                }
                3 -> { // D#dim7 leading to Em
                    isPassing = true
                    passingDesc = "Accord de passage chromatique (#II°7 → iii)"
                }
                6 -> { // F#dim7 leading to G / C/G
                    isPassing = true
                    passingDesc = "Accord de passage chromatique (#IV°7 → V)"
                }
                8 -> { // G#dim7 leading to Am
                    isPassing = true
                    passingDesc = "Accord de passage chromatique (#V°7 → vi)"
                }
            }
        }

        // 3. Tritone Substitution (SubV):
        if ((interval == 1 || interval == 6 || interval == 8) && isDom7) {
            isPassing = true
            passingDesc = "Substitution tritonique (SubV)"
        }

        return AnalyzedChord(
            originalChord = chord,
            romanNumeral = fullRoman,
            degreeArabic = degreeArabic,
            isDiatonic = isDiatonic,
            isPassingChord = isPassing,
            passingDescription = passingDesc
        )
    }

    /**
     * Analyze full sequence of chords and detect classic harmonic progressions.
     */
    fun analyzeProgression(chords: List<String>, rootKey: String): ProgressionAnalysis {
        if (chords.isEmpty()) {
            return ProgressionAnalysis(null, emptyList(), 0)
        }

        val analyzed = chords.mapIndexed { idx, chord ->
            val prev = chords.getOrNull(idx - 1)
            val next = chords.getOrNull(idx + 1)
            analyzeChordInKey(chord, rootKey, prev, next)
        }

        val passingCount = analyzed.count { it.isPassingChord }

        // Form signature from Roman numerals
        val romanTokens = analyzed.map {
            it.romanNumeral.replace(Regex("""(maj7|7|°7|\+|sus4|\(add9\))"""), "").trim()
        }

        val sequenceStr = romanTokens.joinToString("-")

        val detectedName = when {
            sequenceStr.contains("ii-V-I") || sequenceStr.contains("iim-V-I") -> "Cadence Jazz 2-5-1 (ii - V - I)"
            sequenceStr.contains("I-vi-ii-V") || sequenceStr.contains("I-vim-iim-V") -> "Tourne Jazz 1-6-2-5 (I - vi - ii - V)"
            sequenceStr.contains("I-V-vi-IV") || sequenceStr.contains("I-V-vim-IV") -> "Progression Pop / Worship (I - V - vi - IV)"
            sequenceStr.contains("vi-IV-I-V") || sequenceStr.contains("vim-IV-I-V") -> "Progression Émotionnelle (vi - IV - I - V)"
            sequenceStr.contains("I-IV-V") || sequenceStr.contains("I-IV-V-I") -> "Progression Majeure 1-4-5 (I - IV - V)"
            sequenceStr.contains("i-bVI-bIII-bVII") -> "Progression Mineure Épique (i - bVI - bIII - bVII)"
            sequenceStr.contains("I-IV-I-V") -> "Cadence Traditionnelle (I - IV - I - V)"
            else -> null
        }

        return ProgressionAnalysis(
            progressionName = detectedName,
            chords = analyzed,
            passingChordsCount = passingCount
        )
    }

    /**
     * Extract chord sequence from text with [C], [Am], etc., or plain chord text.
     */
    fun extractChordsFromText(text: String): List<String> {
        val bracketRegex = Regex("""\[([A-G][b#]?(?:[a-zA-Z0-9#°ø/+-]*))\]""")
        val bracketMatches = bracketRegex.findAll(text).map { it.groupValues[1] }.toList()
        if (bracketMatches.isNotEmpty()) return bracketMatches

        // Plain chords on lines
        val plainChordRegex = Regex("""\b([A-G][b#]?(?:maj7|min7|m7|7|m|maj|dim|aug|sus2|sus4|add9|9|11|13|m9|ø7|6)?(/[A-G][b#]?)?)\b""")
        return plainChordRegex.findAll(text).map { it.value }.toList()
    }
}
