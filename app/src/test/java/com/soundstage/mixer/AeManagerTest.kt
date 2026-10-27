package com.soundstage.mixer

import com.soundstage.mixer.instruments.AeHost
import com.soundstage.mixer.instruments.AeInstrument
import com.soundstage.mixer.instruments.AeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manager is the single source of truth for "which Æther instrument sits on which
 * strip". It must stay pure logic over [AeHost] so it is testable without the native
 * engine — this is the layer the soundfont window and the mixer both read.
 */
class AeManagerTest {

    private class FakeHost : AeHost {
        val loaded = HashMap<Int, Int>()
        val params = HashMap<Int, MutableMap<Int, Float>>()
        val notesOn = mutableListOf<Pair<Int, Int>>()
        var failLoad = false

        override fun loadInstrument(strip: Int, fxId: Int): Boolean {
            if (failLoad) return false
            loaded[strip] = fxId
            params[strip] = HashMap()
            return true
        }
        override fun unloadInstrument(strip: Int) { loaded.remove(strip); params.remove(strip) }
        override fun setParam(strip: Int, index: Int, value: Float) { params[strip]?.put(index, value) }
        override fun noteOn(strip: Int, note: Int, velocity: Int) { notesOn += strip to note }
        override fun noteOff(strip: Int, note: Int) {}
        override fun allNotesOff(strip: Int) {}
        override fun level(strip: Int) = 0f
    }

    private fun manager(host: FakeHost) =
        AeManager(host, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

    @Test
    fun loadPushesInstrumentAndFirstPreset() {
        val host = FakeHost()
        val m = manager(host)
        val s = m.load(3, AeInstrument.ORGAN)
        assertEquals(3, s?.strip)
        assertEquals(AeInstrument.ORGAN.fxId, host.loaded[3])
        // the whole first preset is written to the engine on load
        assertEquals(AeInstrument.ORGAN.paramCount, host.params[3]?.size)
    }

    @Test
    fun loadIsIdempotentOnSameInstrument() {
        val host = FakeHost()
        val m = manager(host)
        val first = m.load(0, AeInstrument.SYNTH)
        val second = m.load(0, AeInstrument.SYNTH)
        assertTrue(first === second)
    }

    @Test
    fun switchingInstrumentOnAStripReplacesIt() {
        val host = FakeHost()
        val m = manager(host)
        m.load(2, AeInstrument.ORGAN)
        m.load(2, AeInstrument.SYNTH)
        assertEquals(1, m.sessions.size)
        assertEquals(AeInstrument.SYNTH, m.sessions[2]?.instrument)
        assertEquals(AeInstrument.SYNTH.fxId, host.loaded[2])
    }

    @Test
    fun engineRefusalLeavesNoSession() {
        val host = FakeHost().apply { failLoad = true }
        val m = manager(host)
        assertNull(m.load(1, AeInstrument.ORGAN))
        assertFalse(m.sessions.containsKey(1))
    }

    @Test
    fun unloadReleasesTheEngine() {
        val host = FakeHost()
        val m = manager(host)
        m.load(5, AeInstrument.ORGAN)
        m.unload(5)
        assertFalse(m.sessions.containsKey(5))
        assertFalse(host.loaded.containsKey(5))
    }

    @Test
    fun ccRoutesToTheDocumentedParameter() {
        val host = FakeHost()
        val m = manager(host)
        m.load(4, AeInstrument.ORGAN)
        // CC74 is cutoff on the organ (see AeManager.ccOrgan)
        assertTrue(m.onMidiCc(4, 74, 127))
        assertEquals(1f, host.params[4]?.get(27))
    }

    @Test
    fun midiIsIgnoredOnAStripWithoutInstrument() {
        val m = manager(FakeHost())
        assertFalse(m.onMidiNoteOn(6, 60, 100))
        assertFalse(m.onMidiCc(6, 74, 100))
    }

    @Test
    fun loadedStripConsumesItsNotes() {
        val host = FakeHost()
        val m = manager(host)
        m.load(1, AeInstrument.SYNTH)
        assertTrue(m.onMidiNoteOn(1, 64, 90))
        assertEquals(1 to 64, host.notesOn.first())
    }

    // --- UI mirror of engine-routed notes ------------------------------------------------
    // The app plays every note through the native queue, which hands it to the source
    // directly; the instrument window used to stay inert on external MIDI because nothing
    // told the manager. mirrorNote* feeds the UI and must NOT re-send the note.

    @Test
    fun mirrorNoteOnLightsTheKeyWithoutReplayingIt() {
        val host = FakeHost()
        val m = manager(host)
        m.load(2, AeInstrument.LEADS)
        val before = host.notesOn.size

        m.mirrorNoteOn(2, 67, 100)

        assertEquals(true, m.sessions[2]?.held?.get(67))
        assertEquals(67, m.sessions[2]?.lastNote)
        assertEquals(1, m.sessions[2]?.noteEvents)
        // A second native copy would double the voice, so nothing must reach the engine.
        assertEquals(before, host.notesOn.size)
    }

    @Test
    fun mirrorNoteOffReleasesTheKey() {
        val host = FakeHost()
        val m = manager(host)
        m.load(2, AeInstrument.LEADS)
        m.mirrorNoteOn(2, 67, 100)
        m.mirrorNoteOff(2, 67)
        assertEquals(null, m.sessions[2]?.held?.get(67))
    }

    @Test
    fun mirrorNoteOffOnAStripWithoutInstrumentIsIgnored() {
        val m = manager(FakeHost())
        m.mirrorNoteOn(5, 60, 100)  // must not create a session
        m.mirrorNoteOff(5, 60)
        assertFalse(m.sessions.containsKey(5))
    }

    @Test
    fun mirrorNoteOffOnlyReleasesItsOwnNote() {
        val host = FakeHost()
        val m = manager(host)
        m.load(0, AeInstrument.ORGAN)
        m.mirrorNoteOn(0, 60, 100)
        m.mirrorNoteOn(0, 64, 100)
        m.mirrorNoteOff(0, 60)
        assertEquals(null, m.sessions[0]?.held?.get(60))
        assertEquals(true, m.sessions[0]?.held?.get(64))
    }
}
