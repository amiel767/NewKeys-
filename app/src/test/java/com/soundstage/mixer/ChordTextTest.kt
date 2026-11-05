package com.soundstage.mixer

import com.aetherkeys.sheets.model.ChordText
import com.aetherkeys.sheets.model.Notation
import com.aetherkeys.sheets.model.Quality
import com.aetherkeys.sheets.model.Section
import com.aetherkeys.sheets.model.SectionKind
import com.aetherkeys.sheets.model.Theory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * L'edition rapide relit la ligne d'accords affichee. Ce test verifie l'aller-retour :
 * ce qui est ecrit a l'ecran doit se relire a l'identique, sinon l'appui long
 * abimerait la feuille au lieu de la modifier.
 */
class ChordTextTest {

    private val key = 0

    @Test
    fun degreeChordRoundTrips() {
        val c = ChordText.parseChord("1", key)!!
        assertEquals(0, c.root)
        assertEquals(Quality.Major, c.quality)
        assertEquals("1", Theory.format(c, key, Notation.Nashville))
    }

    @Test
    fun minorAndSeventhRoundTrip() {
        val c = ChordText.parseChord("6m7", key)!!
        assertEquals(9, c.root)
        assertEquals(Quality.Minor, c.quality)
        assertEquals("6m7", Theory.format(c, key, Notation.Nashville))
    }

    @Test
    fun flatDegreeRoundTrips() {
        val c = ChordText.parseChord("b3", key)!!
        assertEquals(3, c.root)
        assertEquals("b3", Theory.format(c, key, Notation.Nashville))
    }

    @Test
    fun slashChordKeepsBass() {
        val c = ChordText.parseChord("5/7", key)!!
        assertEquals(7, c.root)
        assertEquals(11, c.bass)
        assertEquals("5/7", Theory.format(c, key, Notation.Nashville))
    }

    @Test
    fun noteNameIsAcceptedInBothNotations() {
        // On relit ce qui est ecrit : une note se relit comme une note, en A-G aussi.
        val c = ChordText.parseChord("F#m7", key)!!
        assertEquals(6, c.root)
        assertEquals("F#m7", Theory.format(c, key, Notation.Standard))
    }

    @Test
    fun flatNoteReadsAsTheSameSemitone() {
        val c = ChordText.parseChord("Bb", key)!!
        assertEquals(10, c.root)
    }

    @Test
    fun emptySlotAndRepeatAreUnderstood() {
        assertNull(ChordText.parseChord("-", key))
        assertEquals(true, ChordText.parseBar("%", key).repeatPrevious)
        assertEquals(true, ChordText.parseBar("N.C.", key).noChord)
    }

    @Test
    fun sectionLineRoundTripsThroughApply() {
        val section = Section(
            kind = SectionKind.Verse, name = "Verse",
            bars = ChordText.applyToSection(
                Section(kind = SectionKind.Verse, name = "Verse", bars = emptyList()),
                "1 4 | 5 6m | 4 - | %", key,
            ).bars,
        )
        val line = ChordText.sectionLine(section, key, Notation.Nashville)
        assertEquals("1  4 | 5  6m | 4  - | %", line)

        // Reappliquer la ligne relue doit redonner exactement la meme section : c'est
        // l'invariant de l'edition rapide (ouvrir puis valider sans rien changer).
        val again = ChordText.applyToSection(section, line, key)
        assertEquals(section.bars.map { it.chords }, again.bars.map { it.chords })
        assertEquals(section.bars.map { it.repeatPrevious }, again.bars.map { it.repeatPrevious })
    }

    @Test
    fun barIdsArePreservedWhenTheCountMatches() {
        val original = ChordText.applyToSection(
            Section(kind = SectionKind.Verse, name = "Verse", bars = emptyList()), "1 | 4", key,
        )
        val edited = ChordText.applyToSection(original, "1 | 5", key)
        assertEquals(original.bars.map { it.id }, edited.bars.map { it.id })
    }

    @Test
    fun unknownTextDoesNotThrow() {
        assertNull(ChordText.parseChord("??", key))
        assertNull(ChordText.parseChord("", key))
    }
}
