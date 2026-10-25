package com.soundstage.mixer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the snapshot round-trip and the patch-switch release rule.
 *
 * These are plain JVM tests: [SnapshotLogic] and [SnapshotJson] hold the decisions,
 * so no Android runtime is needed.
 */
class SnapshotLogicTest {

    private fun track(id: Int = 1) = TrackChannel(
        id = id,
        name = "Track $id",
        volume = 0.42f,
        pan = -0.3f,
        isMuted = true,
        isSolo = true,
        isEnabled = false,
        soundfontName = "piano.sf2",
        patchName = "Grand",
        bank = 2,
        program = 7,
        reverbMix = 0.33f,
        splitNoteMin = 36,
        splitNoteMax = 96,
        velocityCurve = 0.75f,
        transpose = -5,
        octave = 2,
        sustain = false,
        aftertouch = true,
        programChange = false,
        pitchBendRange = 7,
        midiChannel = 9,
        customColorArgb = 0xFF112233.toInt()
    )

    @Test
    fun captureTrackKeepsEveryPerformanceField() {
        val snap = SnapshotLogic.captureTrack(track())
        assertEquals(0.42f, snap.volume, 0f)
        assertEquals(-5, snap.transpose)
        assertEquals(2, snap.octave)
        assertEquals(0.33f, snap.reverbSend, 0f)
        assertFalse(snap.sustain)
        assertTrue(snap.aftertouch)
        assertFalse(snap.programChange)
        assertEquals(7, snap.pitchBendRange)
        assertEquals(9, snap.midiChannel)
        assertEquals(0xFF112233.toInt(), snap.customColorArgb)
        // Split range and velocity curve travel with the strip too.
        assertEquals(36, snap.splitNoteMin)
        assertEquals(96, snap.splitNoteMax)
        assertEquals(0.75f, snap.velocityCurve, 0f)
    }

    @Test
    fun applyToTrackRestoresEveryCapturedField() {
        val live = track(id = 3)
        val stored = SnapshotLogic.captureTrack(track(id = 3)).copy(
            volume = 0.9f, pan = 0.5f, transpose = 4, octave = -1,
            sustain = true, aftertouch = false, programChange = true,
            pitchBendRange = 12, midiChannel = 0, customColorArgb = null
        )
        val restored = SnapshotLogic.applyToTrack(live, stored)
        assertEquals(0.9f, restored.volume, 0f)
        assertEquals(0.5f, restored.pan, 0f)
        assertEquals(4, restored.transpose)
        assertEquals(-1, restored.octave)
        assertTrue(restored.sustain)
        assertFalse(restored.aftertouch)
        assertEquals(12, restored.pitchBendRange)
        assertEquals(0, restored.midiChannel)
        assertNull(restored.customColorArgb)
    }

    @Test
    fun slotsToReleaseSkipsDrumAndPadAndKeepsNeededOnes() {
        val slots = listOf(
            AudioSlot(slotId = 0, soundFontId = 10, soundFontPath = "/a.sf2"),
            AudioSlot(slotId = 1, soundFontId = 11, soundFontPath = "/b.sf2"),
            AudioSlot(slotId = 8, soundFontId = 12, soundFontPath = "/drum.sf2"),
            AudioSlot(slotId = 9, soundFontId = 13, soundFontPath = "/pad.sf2"),
        )
        val released = SnapshotLogic.slotsToRelease(slots, setOf("/a.sf2"))
        // Slot 0 is still needed, and 8/9 belong to the surfaces: only 1 is freed.
        assertEquals(listOf(1), released)
    }

    @Test
    fun slotsToReleaseIgnoresEmptySlots() {
        val slots = listOf(
            AudioSlot(slotId = 0, soundFontId = -1, soundFontPath = null),
            AudioSlot(slotId = 1, soundFontId = 0, soundFontPath = null),
        )
        assertTrue(SnapshotLogic.slotsToRelease(slots, emptySet()).isEmpty())
    }

    @Test
    fun retainedPathsKeepTheIncomingPatchSlotsAndTheSurfaces() {
        val current = listOf(
            AudioSlot(slotId = 0, soundFontId = 1, soundFontPath = "/old.sf2"),
            AudioSlot(slotId = 8, soundFontId = 2, soundFontPath = "/drum.sf2"),
        )
        val retained = SnapshotLogic.retainedSoundfontPaths(
            targetTracks = listOf(track(1).copy(soundfontName = "new.sf2")),
            targetSlots = listOf(AudioSlot(slotId = 0, soundFontId = 3, soundFontPath = "/carried.sf2")),
            currentSlots = current,
            resolvePath = { name -> if (name.isEmpty()) null else "/$name" }
        )
        assertTrue(retained.contains("/new.sf2"))
        assertTrue(retained.contains("/carried.sf2"))
        // Drum surface survives a patch switch.
        assertTrue(retained.contains("/drum.sf2"))
        assertFalse(retained.contains("/old.sf2"))
    }
}
