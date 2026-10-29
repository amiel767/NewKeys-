package com.soundstage.mixer

import com.soundstage.mixer.model.AetherTrackState
import com.soundstage.mixer.model.SnapshotJson
import com.soundstage.mixer.model.SubSceneSnapshot
import com.soundstage.mixer.model.TrackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Æther block of a snapshot, through the real JSON codec.
 *
 * An Æther instrument is part of a track's identity, like its soundfont, so a snapshot has to
 * carry it: the instrument, the preset index and every knob value. What these tests protect is
 * the *absence* case as much as the presence one — a track with no instrument must write no
 * block, and a blob written before the field existed must still load.
 *
 * Robolectric, not a plain JVM test: the codec is built on `org.json`, whose stub on the JVM
 * throws "not mocked". Robolectric supplies the real implementation.
 */
@RunWith(RobolectricTestRunner::class)
class AetherSnapshotJsonTest {

    private fun snapshot(aether: AetherTrackState?) = TrackSnapshot(
        id = 3, volume = 0.7f, pan = -0.25f, soundfontName = "rhodes.sf2",
        midiChannel = 4, aether = aether,
    )

    @Test
    fun aTrackWithAnInstrumentRoundTripsItsNamePresetAndValues() {
        val st = AetherTrackState("LEADS", 5, listOf(0.1f, 0.2f, 0.3f, 0.9f))
        val back = SnapshotJson.trackFromJson(SnapshotJson.trackToJson(snapshot(st)), 3)
        assertEquals(st, back.aether)
        // The rest of the track must survive alongside it.
        assertEquals(0.7f, back.volume, 1e-6f)
        assertEquals(-0.25f, back.pan, 1e-6f)
        assertEquals("rhodes.sf2", back.soundfontName)
        assertEquals(4, back.midiChannel)
    }

    @Test
    fun aTrackWithoutAnInstrumentWritesNoAetherBlock() {
        val o = SnapshotJson.trackToJson(snapshot(null))
        assertFalse(o.has("aetherInstrument"))
        assertNull(SnapshotJson.trackFromJson(o, 3).aether)
    }

    @Test
    fun aBlobFromBeforeInstrumentsExistedStillLoads() {
        // A track written by an older build: no Æther keys at all. Reading it must not throw
        // and must not invent an instrument.
        val o = org.json.JSONObject().apply {
            put("id", 1); put("volume", 0.5); put("soundfontName", "piano.sf2")
        }
        assertNull(SnapshotJson.trackFromJson(o, 1).aether)
    }

    @Test
    fun aHalfWrittenAetherBlockIsIgnoredRatherThanGuessed() {
        // Name present but no values: nothing to restore, so the block is dropped instead of
        // producing an instrument at an arbitrary state.
        val o = org.json.JSONObject().apply {
            put("id", 1); put("aetherInstrument", "ORGAN")
        }
        assertNull(SnapshotJson.trackFromJson(o, 1).aether)
    }

    @Test
    fun aWholeSnapshotCarriesTheInstrumentThroughTheMapCodec() {
        val snap = SubSceneSnapshot(slotName = "INTRO", tracks = listOf(snapshot(AetherTrackState("SAX", 2, listOf(0f, 1f)))))
        val back = SnapshotJson.mapFromJson(SnapshotJson.mapToJson(mapOf("slot_a" to snap)))
        assertEquals(AetherTrackState("SAX", 2, listOf(0f, 1f)), back["slot_a"]?.tracks?.first()?.aether)
    }
}

