package com.soundstage.mixer

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shipped piano pack against the three defects that shipped silently once:
 * a blob written without its PIAP container, a region length written in bytes where the
 * engine reads frames, and a layer count the engine did not cover. All three load "fine"
 * as far as Gradle is concerned, so only parsing the real asset catches them.
 */
class PianoPackFormatTest {

    private fun packFile(): File {
        val candidates = listOf(
            "src/main/assets/pianopack/piano.bin",
            "app/src/main/assets/pianopack/piano.bin",
        )
        return candidates.map(::File).firstOrNull { it.exists() }
            ?: error("pack introuvable depuis ${File(".").absolutePath}")
    }

    @Test
    fun theShippedPackParsesAndItsRegionsFitInsideThePayload() {
        val bytes = packFile().readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("PIAP", String(bytes, 0, 4, Charsets.US_ASCII))
        val rate = buf.getInt(8)
        val count = buf.getInt(12)
        val pcmLen = buf.getInt(16)
        val recLen = buf.getInt(20)
        // 44,1 kHz : la banque S90 FULL GRAND porte de l'energie jusqu'a 11 kHz
        // (mesure : 3,7 % entre 5 et 8 kHz, 1,3 % au-dessus de 11 kHz sur l'aigu).
        // A 22,05 kHz le plafond tombe a 11 kHz et le filtre anti-repliement
        // emporte exactement la bande qui fait la difference avec l'ancienne banque.
        assertEquals("taux du pack", 44100, rate)
        assertEquals("régions", 68, count)
        assertEquals("taille des fiches", count * 24, recLen)
        assertTrue("en-tête + fiches + charge utile", 28 + recLen + pcmLen <= bytes.size)

        val layers = mutableSetOf<Int>()
        val roots = mutableSetOf<Int>()
        var looped = 0
        for (i in 0 until count) {
            val r = 28 + i * 24
            val root = bytes[r].toInt() and 0xFF
            val layer = bytes[r + 1].toInt() and 0xFF
            val offset = buf.getInt(r + 4)
            val frames = buf.getInt(r + 8)
            val loopEnd = buf.getInt(r + 16)
            // Stereo interleaved ADPCM: one byte holds one frame, two nibbles.
            assertTrue("région $i tient dans la charge utile", offset + frames <= pcmLen)
            assertTrue("région $i non vide", frames > 0)
            if (loopEnd > 0) looped++
            roots += root
            layers += layer
        }
        // Le moteur adapte son echelle de velocite au nombre de couches du pack
        // (`pick()` fait `velocity * layerCount / 128`).  Le SF2 en porte 7 plages,
        // que le build fusionne en 4 : PP, MP, MF, FF.
        assertEquals("couches couvertes", (0 until 4).toSet(), layers)
        assertEquals("toutes les régions bouclées", count, looped)
        // 17 fondamentales de C2 (36) a C6 (84), tous les 3 demi-tons : aucune note
        // n'est a plus de 1,5 demi-ton de son echantillon.  L'ancienne banque
        // s'arretait a 108 et couvrait 21..108 ; celle-ci couvre 0..127.
        assertEquals("nombre de fondamentales", 17, roots.size)
        // Les racines vont de C2 (36) a C6 (84).  Au-dessus, le SF2 etire
        // lui-meme l'echantillon de C6 jusqu'a 127 : c'est son mapping, et c'est
        // le son que la reference produit.  On verifie donc la couverture utile
        // du clavier jouable, pas la hauteur de la derniere racine.
        assertTrue("grave couvert", roots.min() <= 36)
        assertTrue("aigu couvert", roots.max() >= 84)
    }
}
