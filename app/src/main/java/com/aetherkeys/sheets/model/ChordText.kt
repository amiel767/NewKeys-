package com.aetherkeys.sheets.model

/**
 * Analyse du texte d'accords, l'inverse de [Theory.format] et [Theory.barText].
 *
 * C'est ce qui permet l'edition rapide depuis la fenetre flottante : on relit la ligne
 * d'accords telle qu'elle est affichee, on la modifie, et on la relit. Sans cette
 * relecture, « edition rapide » voudrait dire « rouvrir l'editeur complet ».
 *
 * Grammaire d'une section, une barre par groupe :
 *
 *     "1 4 | 5 6m | 4 - | %"
 *
 * - les barres sont separees par `|`
 * - dans une barre, les accords sont separes par des espaces (`-` = case vide)
 * - `%` = reprise de la barre precedente, `N.C.` = pas d'accord
 * - un accord s'ecrit en degre (`1`, `b3`, `#4`, `5/7`) ou en note (`C`, `F#m7`, `Bb/D`)
 * - qualite : `m`, `dim`, `aug`, `sus2`, `sus4`, `5`
 * - extensions : `7`, `maj7`, `6`, `9`, `add9`
 */
object ChordText {

    private val SHARP_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val LETTER_PC = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
    private val DEGREE_PC = mapOf(
        "1" to 0, "b2" to 1, "2" to 2, "b3" to 3, "3" to 4, "4" to 5,
        "#4" to 6, "5" to 7, "b6" to 8, "6" to 9, "b7" to 10, "7" to 11,
    )

    /**
     * Tete d'accord : degre (`b3`, relatif a [keyPc]) ou note (`F#`, absolue).
     * Un degre se relit toujours comme un degre et une note comme une note, quelle
     * que soit la notation affichee : on relit ce qui est ecrit.
     */
    private fun parseRoot(token: String, keyPc: Int): Pair<Int, String>? {
        if (token.isEmpty()) return null
        DEGREE_PC[token.take(2)]?.let { return (keyPc + it + 120) % 12 to token.drop(2) }
        DEGREE_PC[token.take(1)]?.let { return (keyPc + it + 120) % 12 to token.drop(1) }
        val base = LETTER_PC[token[0].uppercaseChar()] ?: return null
        var rest = token.drop(1)
        var pc = base
        when (rest.firstOrNull()) {
            '#' -> { pc = (base + 1) % 12; rest = rest.drop(1) }
            'b' -> { pc = (base + 11) % 12; rest = rest.drop(1) }
            else -> {}
        }
        return pc to rest
    }

    /** Un accord complet, ou null si le texte n'en est pas un. */
    fun parseChord(text: String, keyPc: Int): Chord? {
        val token = text.trim()
        if (token.isEmpty() || token == "-") return null
        val (root, afterRoot) = parseRoot(token, keyPc) ?: return null
        val slash = afterRoot.indexOf('/')
        val body = if (slash >= 0) afterRoot.take(slash) else afterRoot
        val bassText = if (slash >= 0) afterRoot.drop(slash + 1) else null

        val lower = body.lowercase()
        val quality = when {
            lower.startsWith("maj7") || lower.startsWith("maj") -> Quality.Major
            lower.startsWith("dim") -> Quality.Dim
            lower.startsWith("aug") -> Quality.Aug
            lower.startsWith("sus2") -> Quality.Sus2
            lower.startsWith("sus4") -> Quality.Sus4
            lower.startsWith("sus") -> Quality.Sus4
            lower.startsWith("add9") -> Quality.Major
            lower.startsWith("m") -> Quality.Minor
            lower.startsWith("5") -> Quality.Power5
            else -> Quality.Major
        }

        val exts = mutableSetOf<Ext>()
        if (lower.contains("maj7")) exts += Ext.Maj7
        else if (lower.contains("7")) exts += Ext.Seven
        if (lower.contains("6")) exts += Ext.Six
        if (lower.contains("add9")) exts += Ext.Add9
        else if (lower.contains("9")) exts += Ext.Nine

        val bass = bassText?.let { parseRoot(it.trim(), keyPc)?.first }
        return Chord(root = root, quality = quality, exts = exts, bass = bass)
    }

    /** Une barre : ses accords separes par des espaces, `%` et `N.C.` compris. */
    fun parseBar(text: String, keyPc: Int): Bar {
        val token = text.trim()
        if (token == "%") return Bar(repeatPrevious = true)
        if (token.equals("N.C.", ignoreCase = true) || token.equals("NC", ignoreCase = true)) return Bar(noChord = true)
        val chords = token.split(' ').filter { it.isNotEmpty() }.map { parseChord(it, keyPc) }
        return Bar(chords = chords.ifEmpty { listOf(null) })
    }

    /** La ligne d'accords d'une section, telle que la relit [applyToSection]. */
    fun sectionLine(section: Section, keyPc: Int, notation: Notation): String =
        section.bars.joinToString(" | ") { Theory.barText(it, keyPc, notation, PassingDisplay.Raw) }

    /**
     * Applique une ligne relue a une section. Les identifiants de barres existantes sont
     * conserves tant qu'il y a correspondance : sinon l'editeur perdrait la selection.
     */
    fun applyToSection(section: Section, line: String, keyPc: Int): Section {
        val parsed = line.split('|').map { parseBar(it, keyPc) }
        val bars = parsed.mapIndexed { i, bar -> bar.copy(id = section.bars.getOrNull(i)?.id ?: bar.id) }
        return section.copy(bars = bars)
    }

    /** Nom de note d'un demi-ton, en diese (l'analyse accepte aussi les bemols). */
    fun noteName(pc: Int): String = SHARP_NAMES[((pc % 12) + 12) % 12]
}
