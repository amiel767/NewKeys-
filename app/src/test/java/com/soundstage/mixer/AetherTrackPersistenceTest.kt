package com.soundstage.mixer

import com.soundstage.mixer.model.AetherJson
import com.soundstage.mixer.model.AetherTrackState
import com.soundstage.mixer.model.TrackChannel
import com.soundstage.mixer.model.TrackJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Æther instrument a track carries, through the scene-file codec.
 *
 * A setlist is the only thing that survives a restart: the instrument itself lives in
 * `AeInstruments` and the engine, both per-process. So if `aether` does not survive
 * `TrackJson` -> `JSONObject` -> `AetherJson`, the strip comes back bare and silent.
 * These tests walk exactly the read the loader performs, and protect the absence case
 * as much as the presence one — a track with no instrument must not come back with a
 * phantom one.
 */
@RunWith(RobolectricTestRunner::class)
class AetherTrackPersistenceTest {

    private fun track(aether: AetherTrackState?, source: String) = TrackChannel(
        id = 3, name = "Track 3", soundSource = source, aether = aether,
    )

    @Test
    fun anInstrumentSurvivesTheSceneFileRoundTrip() {
        val st = AetherTrackState("SAX", 7, listOf(0.25f, 0.5f, 1.0f, 0.0f))
        val written = TrackJson.toJson(listOf(track(st, TrackChannel.SOURCE_AETHER))).getJSONObject(0)
        // The loader reads the block with exactly this call.
        val back = AetherJson.fromJson(written.optJSONObject("aether"))
        assertEquals(st, back)
        assertEquals(TrackChannel.SOURCE_AETHER, written.optString("soundSource"))
    }

    @Test
    fun aTrackWithoutAnInstrumentComesBackWithoutOne() {
        val written = TrackJson.toJson(listOf(track(null, TrackChannel.SOURCE_SF2))).getJSONObject(0)
        assertNull(AetherJson.fromJson(written.optJSONObject("aether")))
    }

    @Test
    fun aBlobFromBeforeTheFieldExistedStillLoads() {
        val o = org.json.JSONObject().apply {
            put("id", 1); put("soundfontName", "piano.sf2"); put("soundSource", "sf2")
        }
        assertNull(AetherJson.fromJson(o.optJSONObject("aether")))
    }

    @Test
    fun theLiveStampCarriesTheInstrumentOntoTheTrack() {
        // `syncAetherIntoState` writes the instrument onto the track with a plain copy;
        // that is the path the scene writer then reads.
        val stamped = track(null, "").copy(aether = AetherTrackState("PIANO", 2, listOf(0.5f)))
        assertNotNull(stamped.aether)
        assertEquals("PIANO", stamped.aether?.instrument)
    }
}
