package com.soundstage.mixer

import com.soundstage.mixer.model.AudioSlot
import com.soundstage.mixer.model.SnapshotLogic
import com.soundstage.mixer.model.TrackChannel
import com.soundstage.mixer.model.TrackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The patch-switch memory rule and the snapshot round-trip, tested without Android.
 *
 * The bug these lock down: switching patch freed the native soundfont buffers but
 * left `soundFontId` pointing at them in `audioSlots`, and the "still needed" set
 * ignored the Drum and Pad slots. The tests below fix both halves of the contract.
 */
class SnapshotLogicTest {

    private fun slot(
        id: Int,
        sfId: Int = -1,
        path: String? = null,
    ) = AudioSlot(slotId = id, soundFontId = sfId, soundFontPath = path)

    private fun track(
        id: Int,
        soundfontName: String = "",
        volume: Float = 0.8f,
        reverbMix: Float = 0.2f,
    ) = TrackChannel(id = id, name = "Track $id", soundfontName = soundfontName, volume = volume, reverbMix = reverbMix)

    // ------------------------------------------------------------------ release

    @Test
    fun slotsToRelease_returnsOnlySlotsTheIncomingPatchDoesNotNeed() {
        val current = listOf(
            slot(0, sfId = 11, path = "/sf/keep.sf2"),
            slot(1, sfId = 12, path = "/sf/drop.sf2"),
            slot(2, sfId = 13, path = "/sf/alsoDrop.sf2"),
        )
        val released = SnapshotLogic.slotsToRelease(current, setOf("/sf/keep.sf2"))
        assertEquals(listOf(1, 2), released)
    }

    @Test
    fun slotsToRelease_neverTouchesTheDrumAndPadSurfaces() {
        val current = listOf(
            slot(0, sfId = 11, path = "/sf/track.sf2"),
            slot(8, sfId = 21, path = "/sf/drumkit.sf2"),
            slot(9, sfId = 22, path = "/sf/pad.sf2"),
        )
        val released = SnapshotLogic.slotsToRelease(current, emptySet())
        assertEquals(listOf(0), released)
    }

    @Test
    fun slotsToRelease_skipsSlotsWithNoLoadedSoundfont() {
        val current = listOf(
            slot(0, sfId = -1, path = null),
            slot(1, sfId = 0, path = "/sf/weird.sf2"),
            slot(2, sfId = 12, path = null),
        )
        assertTrue(SnapshotLogic.slotsToRelease(current, emptySet()).isEmpty())
    }

    @Test
    fun slotsToRelease_isEmptyWhenEverythingIsStillNeeded() {
        val current = listOf(
            slot(0, sfId = 11, path = "/sf/a.sf2"),
            slot(1, sfId = 12, path = "/sf/b.sf2"),
        )
        assertTrue(SnapshotLogic.slotsToRelease(current, setOf("/sf/a.sf2", "/sf/b.sf2")).isEmpty())
    }

    // ------------------------------------------------------------------ retained

    @Test
    fun retainedSoundfontPaths_readsTracksAndPatchSlots() {
        val retained = SnapshotLogic.retainedSoundfontPaths(
            targetTracks = listOf(track(1, soundfontName = "piano.sf2")),
            targetSlots = listOf(slot(1, sfId = 11, path = "/sf/extra.sf2")),
            currentSlots = emptyList(),
            resolvePath = { name -> if (name == "piano.sf2") "/sf/piano.sf2" else null },
        )
        assertEquals(setOf("/sf/piano.sf2", "/sf/extra.sf2"), retained)
    }

    @Test
    fun retainedSoundfontPaths_keepsTheDrumAndPadSurfacesAlive() {
        val retained = SnapshotLogic.retainedSoundfontPaths(
            targetTracks = emptyList(),
            targetSlots = emptyList(),
            currentSlots = listOf(
                slot(8, sfId = 21, path = "/sf/drumkit.sf2"),
                slot(9, sfId = 22, path = "/sf/pad.sf2"),
            ),
            resolvePath = { null },
        )
        assertEquals(setOf("/sf/drumkit.sf2", "/sf/pad.sf2"), retained)
    }

    @Test
    fun retainedSoundfontPaths_ignoresUnresolvableTrackNames() {
        val retained = SnapshotLogic.retainedSoundfontPaths(
            targetTracks = listOf(track(1, soundfontName = "missing.sf2")),
            targetSlots = emptyList(),
            currentSlots = emptyList(),
            resolvePath = { null },
        )
        assertTrue(retained.isEmpty())
    }

    // ------------------------------------------------------------------ round trip

    @Test
    fun captureThenApply_restoresEveryRecalledField() {
        val original = TrackChannel(
            id = 3,
            name = "Track 3",
            volume = 0.42f,
            pan = -0.5f,
            isMuted = true,
            isSolo = true,
            isEnabled = false,
            soundfontName = "strings.sf2",
            patchName = "Cello",
            bank = 1,
            program = 42,
            reverbMix = 0.66f,
            splitNoteMin = 36,
            splitNoteMax = 96,
            velocityCurve = 0.75f,
            transpose = -5,
            octave = 2,
        )
        val snapshot = SnapshotLogic.captureTrack(original)
        val live = TrackChannel(id = 3, name = "Track 3")

        val restored = SnapshotLogic.applyToTrack(live, snapshot)

        assertEquals(original.volume, restored.volume, 0f)
        assertEquals(original.pan, restored.pan, 0f)
        assertTrue(restored.isMuted)
        assertTrue(restored.isSolo)
        assertFalse(restored.isEnabled)
        assertEquals("strings.sf2", restored.soundfontName)
        assertEquals("Cello", restored.patchName)
        assertEquals(1, restored.bank)
        assertEquals(42, restored.program)
        assertEquals(36, restored.splitNoteMin)
        assertEquals(96, restored.splitNoteMax)
        assertEquals(0.75f, restored.velocityCurve, 0f)
        // Pitch and reverb live in the native synth, but the state must carry them
        // back too: the mixer reads them on the very next recomposition.
        assertEquals(-5, restored.transpose)
        assertEquals(2, restored.octave)
        assertEquals(0.66f, restored.reverbMix, 0f)
    }

    @Test
    fun captureTrack_storesThePitchAndReverbFieldsTheEngineAlsoNeeds() {
        val snapshot = SnapshotLogic.captureTrack(
            track(3, soundfontName = "strings.sf2", reverbMix = 0.66f)
                .copy(transpose = -5, octave = 2)
        )
        assertEquals(0.66f, snapshot.reverbSend, 0f)
        assertEquals(-5, snapshot.transpose)
        assertEquals(2, snapshot.octave)
    }

    @Test
    fun applyToTrack_withMissingSnapshot_mutesInsteadOfDeleting() {
        val live = track(4, soundfontName = "keep.sf2", volume = 0.9f)
        val restored = SnapshotLogic.applyToTrack(live, null)

        assertFalse(restored.isEnabled)
        assertTrue(restored.isMuted)
        // The instrument the user added after the snapshot survives.
        assertEquals("keep.sf2", restored.soundfontName)
        assertEquals(0.9f, restored.volume, 0f)
    }

    @Test
    fun applyToTrack_withEmptySoundfontInSnapshot_keepsTheCurrentInstrument() {
        val live = track(1, soundfontName = "current.sf2")
        val snap = TrackSnapshot(id = 1, volume = 0.5f, pan = 0f, soundfontName = "")

        val restored = SnapshotLogic.applyToTrack(live, snap)

        assertEquals("current.sf2", restored.soundfontName)
        assertEquals(0.5f, restored.volume, 0f)
    }
}
