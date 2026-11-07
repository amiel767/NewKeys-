package com.aetherkeys.sheets.model

object Theory {

    // Le choix d'enharmonie suit l'armure, pas la hauteur brute : un morceau en Bb
    // s'ecrit avec des bemols, un morceau en A avec des dieses. Sans cela, la meme
    // feuille affichait « A# » en Bb et « Ab » en A, deux noms pour la meme touche.
    private val SHARP_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT_NAMES = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    /**
     * Tonalites dont l'armure s'ecrit avec des bemols : F, Bb, Eb, Ab, Db.
     * L'index 6 (six demi-tons) est ambigu — F# majeur a 6 dieses, Gb majeur 6 bemols —
     * on le laisse du cote dieses, qui est le nom affiche avant ce correctif.
     */
    private val FLAT_KEYS = setOf(5, 10, 3, 8, 1)

    /**
     * Noms des douze tonalites. Derive de [noteName] pour qu'il n'existe qu'UNE table
     * dans le module : l'editeur, la liste et l'accordeur affichaient sinon des
     * orthographes differentes pour la meme touche.
     */
    val KEY_NAMES: List<String> = (0 until 12).map { noteName(it, it) }

    // Nashville scale degrees in semitones (1..7)
    private val DEGREE_OFFSETS = intArrayOf(0, 0, 2, 4, 5, 7, 9, 11)

    fun degreeToRoot(degree: Int, keyPc: Int): Int {
        val deg = degree.coerceIn(1, 7)
        val offset = DEGREE_OFFSETS[deg]
        return (keyPc + offset + 120) % 12
    }

    /** Nom de note accorde a l'armure de [keyPc]. */
    fun noteName(pc: Int, keyPc: Int = 0): String {
        val idx = (pc % 12 + 12) % 12
        val key = ((keyPc % 12) + 12) % 12
        return if (key in FLAT_KEYS) FLAT_NAMES[idx] else SHARP_NAMES[idx]
    }

    /** Une tonalite s'ecrit-elle avec des bemols ? (sert au choix d'enharmonie) */
    fun usesFlats(keyPc: Int): Boolean = (((keyPc % 12) + 12) % 12) in FLAT_KEYS

    private fun rootToDegree(rootPc: Int, keyPc: Int): String {
        val interval = (rootPc - keyPc + 120) % 12
        return when (interval) {
            0 -> "1"
            1 -> "b2"
            2 -> "2"
            3 -> "b3"
            4 -> "3"
            5 -> "4"
            6 -> "#4"
            7 -> "5"
            8 -> "b6"
            9 -> "6"
            10 -> "b7"
            11 -> "7"
            else -> "1"
        }
    }

    fun format(
        chord: Chord,
        keyPc: Int,
        notation: Notation = Notation.Nashville,
        passing: PassingDisplay = PassingDisplay.Auto
    ): String {
        val rootStr = if (notation == Notation.Nashville) {
            rootToDegree(chord.root, keyPc)
        } else {
            noteName(chord.root, keyPc)
        }

        val qualityStr = when (chord.quality) {
            Quality.Major -> ""
            Quality.Minor -> "m"
            Quality.Dim -> "dim"
            Quality.Aug -> "aug"
            Quality.Sus2 -> "sus2"
            Quality.Sus4 -> "sus4"
            Quality.Power5 -> "5"
        }

        val extStr = buildString {
            if (chord.exts.contains(Ext.Maj7)) append("maj7")
            else if (chord.exts.contains(Ext.Seven)) append("7")
            if (chord.exts.contains(Ext.Six)) append("6")
            if (chord.exts.contains(Ext.Nine)) append("9")
            if (chord.exts.contains(Ext.Add9)) append("add9")
        }

        val bassStr = if (chord.bass != null) {
            val b = if (notation == Notation.Nashville) rootToDegree(chord.bass, keyPc) else noteName(chord.bass, keyPc)
            "/$b"
        } else ""

        return "$rootStr$qualityStr$extStr$bassStr"
    }

    fun barText(
        bar: Bar,
        keyPc: Int,
        notation: Notation,
        passing: PassingDisplay
    ): String {
        if (bar.noChord) return "N.C."
        if (bar.repeatPrevious) return "%"
        val nonNullChords = bar.chords.filterNotNull()
        if (nonNullChords.isEmpty()) return ""
        return bar.chords.map { chord ->
            chord?.let { format(it, keyPc, notation, passing) } ?: "-"
        }.joinToString("  ")
    }
}
