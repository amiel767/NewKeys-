package com.soundstage.mixer

import com.aetherkeys.sheets.model.Chord
import com.aetherkeys.sheets.model.Ext
import com.aetherkeys.sheets.model.Notation
import com.aetherkeys.sheets.model.PassingDisplay
import com.aetherkeys.sheets.model.Theory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'orthographe d'une note suit l'armure, pas la hauteur brute. Sans cela la meme
 * touche s'ecrit « A# » dans un morceau en Bb et « Ab » dans un morceau en A, et
 * l'editeur, la liste et l'export ne sont plus d'accord entre eux.
 */
class TheorySpellingTest {

    @Test
    fun flatKeysSpellFlats() {
        // F, Bb, Eb, Ab, Db, Gb : les six armures a bemols.
        for (key in listOf(5, 10, 3, 8, 1)) {
            assertTrue("key $key devrait etre a bemols", Theory.usesFlats(key))
            assertEquals("Bb", Theory.noteName(10, key))
            assertEquals("Eb", Theory.noteName(3, key))
        }
    }

    @Test
    fun sharpKeysSpellSharps() {
        for (key in listOf(0, 7, 2, 9, 4, 11, 6)) {
            assertFalse("key $key devrait etre a dieses", Theory.usesFlats(key))
            assertEquals("A#", Theory.noteName(10, key))
            assertEquals("D#", Theory.noteName(3, key))
        }
    }

    @Test
    fun theSamePitchHasOneNamePerKey() {
        // Le point du correctif : une seule orthographe par armure, jamais deux.
        assertEquals("Bb", Theory.noteName(10, 10))
        assertEquals("A#", Theory.noteName(10, 9))
    }

    @Test
    fun keyNamesAgreeWithNoteName() {
        // KEY_NAMES est derive de noteName : la table ne peut pas diverger.
        for (pc in 0 until 12) {
            assertEquals(Theory.noteName(pc, pc), Theory.KEY_NAMES[pc])
        }
    }

    @Test
    fun keyNamesUseConventionalSpelling() {
        // C, Db, D, Eb, E, F, F#, G, Ab, A, Bb, B : les douze tonalites usuelles.
        assertEquals(
            listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"),
            Theory.KEY_NAMES,
        )
    }

    @Test
    fun degreesAreUnaffectedBySpelling() {
        // La notation chiffree est relative : elle ne doit pas bouger avec l'armure.
        val key = 10 // Bb
        assertEquals(10, Theory.degreeToRoot(1, key)) // Bb
        assertEquals(3, Theory.degreeToRoot(4, key))  // Eb
        assertEquals(5, Theory.degreeToRoot(5, key))  // F
        assertEquals(7, Theory.degreeToRoot(6, key))  // G
    }

    @Test
    fun nashvilleChartIsRelativeToItsKey() {
        val chart = Theory.format(
            Chord(root = 5), keyPc = 10,
            notation = Notation.Nashville, passing = PassingDisplay.Auto,
        )
        assertEquals("5", chart)
    }

    @Test
    fun standardChartFollowsTheKeySignature() {
        // Le meme accord (F) s'ecrit « F » en Bb et « E# » n'existe pas : on veut « F ».
        val inBb = Theory.format(
            Chord(root = 5), keyPc = 10,
            notation = Notation.Standard, passing = PassingDisplay.Auto,
        )
        assertEquals("F", inBb)
        // En A (diese), Bb s'ecrit A#.
        val inA = Theory.format(
            Chord(root = 10, exts = setOf(Ext.Seven)), keyPc = 9,
            notation = Notation.Standard, passing = PassingDisplay.Auto,
        )
        assertEquals("A#7", inA)
    }
}
