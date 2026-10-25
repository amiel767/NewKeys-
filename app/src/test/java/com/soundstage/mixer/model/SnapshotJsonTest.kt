package com.soundstage.mixer.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Snapshot persistence, on a real `org.json`.
 *
 * The plain JVM test runtime ships a stubbed `org.json` (every method throws
 * "not mocked"), so the JSON round-trip is exercised here under Robolectric
 * instead. The pure decisions stay in [SnapshotLogicTest], which needs no runtime.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SnapshotJsonTest {

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
    fun nullCustomColourMeansDefaultNotBlack() {
        // A missing key must stay null: reading it as 0 would paint the strip black.
        val o = JSONObject().apply { put("id", 1); put("volume", 0.5) }
        assertNull(SnapshotJson.trackFromJson(o, 1).customColorArgb)
        // An explicit value survives.
        val o2 = JSONObject().apply { put("customColorArgb", 0xFF00FF00.toInt()) }
        assertEquals(0xFF00FF00.toInt(), SnapshotJson.trackFromJson(o2, 1).customColorArgb)
    }

    @Test
    fun snapshotRoundTripKeepsBpmAndPerTrackFx() {
        val fxMaster = FxParameters(reverbMix = 0.6f, isReverbEnabled = true, delayTime = 0.4f)
        val fxTrack2 = FxParameters(chorusMix = 0.5f, isChorusEnabled = true, eqGain = 0.8f)
        val snap = SubSceneSnapshot(
            slotName = "S2",
            tracks = listOf(SnapshotLogic.captureTrack(track(1))),
            bpm = 137,
            fxParameters = fxMaster,
            trackFx = mapOf(0 to fxMaster, 2 to fxTrack2),
            timestamp = 1234L
        )
        val back = SnapshotJson.fromJson("S2", SnapshotJson.toJson(snap))

        assertEquals(137, back.bpm)
        assertEquals(0.6f, back.trackFx.getValue(0).reverbMix, 0f)
        assertEquals(0.5f, back.trackFx.getValue(2).chorusMix, 0f)
        assertEquals(0.8f, back.trackFx.getValue(2).eqGain, 0f)
        assertEquals(1234L, back.timestamp)
        assertEquals(1, back.tracks.size)
        assertEquals(-5, back.tracks[0].transpose)
        assertEquals(0xFF112233.toInt(), back.tracks[0].customColorArgb)
    }

    @Test
    fun snapshotWithoutNewFieldsStillLoads() {
        // A blob written by an older build: no bpm, no trackFx, no performance fields.
        val legacy = JSONObject().apply {
            put("slotName", "INTRO")
            put("masterVolume", 0.7)
            put("tracks", JSONArray().apply {
                put(JSONObject().apply { put("id", 1); put("volume", 0.5) })
            })
        }
        val snap = SnapshotJson.fromJson("INTRO", legacy)
        assertEquals(120, snap.bpm)
        assertTrue(snap.trackFx.isEmpty())
        assertNull(snap.fxParameters)
        assertEquals(1, snap.tracks.size)
        // Defaults, not garbage.
        assertTrue(snap.tracks[0].sustain)
        assertEquals(2, snap.tracks[0].pitchBendRange)
    }

    @Test
    fun mapRoundTripPreservesEverySlot() {
        val map = mapOf(
            "slot_default" to SubSceneSnapshot(slotName = "Default", tracks = emptyList()),
            "S1" to SubSceneSnapshot(slotName = "S1", tracks = listOf(SnapshotLogic.captureTrack(track()))),
        )
        val back = SnapshotJson.mapFromJson(SnapshotJson.mapToJson(map))
        assertEquals(2, back.size)
        assertNotNull(back["S1"])
        assertEquals(1, back.getValue("S1").tracks.size)
    }

    @Test
    fun malformedSnapshotMapDoesNotThrow() {
        // JSONObject with a non-object value must be skipped, not crash the restore.
        val bad = JSONObject().apply { put("S1", "not an object") }
        assertTrue(SnapshotJson.mapFromJson(bad).isEmpty())
    }

}
