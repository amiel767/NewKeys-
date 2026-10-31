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
        assertEquals("taux du pack", 44100, rate)
        assertEquals("régions", 232, count)
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
        // The engine scales its velocity mapping to the pack's layer count.  It used to
        // hardcode 5, so 11 of this pack's layers would never have been played.
        assertEquals("couches couvertes", (0 until 8).toSet(), layers)
        assertEquals("toutes les régions bouclées", count, looped)
        // 29 roots, 3 semitones apart: no note is more than 1.5 semitones from its sample.
        assertEquals("nombre de fondamentales", 29, roots.size)
        assertTrue("étendue de hauteur", roots.min() <= 24 && roots.max() >= 105)
    }
}
