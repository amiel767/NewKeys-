package com.soundstage.mixer.instruments

import com.soundstage.mixer.audio.NativeAudioBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Bridges [AeHost] onto the native FxMixer facade. An Æther instrument occupies a mixer
 * strip exactly like a soundfont: the same `setInstrument` / `setInstrumentParam` entry
 * points the FX window uses, so the engine needs no new native call.
 *
 * The strip index is the mixer channel (0..7 tracks, 8 drum, 9 tonic pad). Notes are NOT
 * pushed from here in normal use: the app already routes every note through the native
 * event queue (`AudioEngine.playMidiNote` -> `noteOn`), and `routeEventToSource` hands it
 * to the Source when the strip carries one. These note methods exist for the instrument
 * window's own on-screen keyboard.
 */
object AeInstrumentHost : AeHost {
    override fun loadInstrument(strip: Int, fxId: Int): Boolean =
        NativeAudioBridge.safeSetTrackInstrument(strip, fxId)

    override fun unloadInstrument(strip: Int) {
        NativeAudioBridge.safeSetTrackInstrument(strip, -1)
    }

    override fun setParam(strip: Int, index: Int, value: Float) {
        NativeAudioBridge.safeSetTrackInstrumentParam(strip, index, value)
    }

    override fun noteOn(strip: Int, note: Int, velocity: Int) {
        NativeAudioBridge.safeNoteOn(strip, note, velocity)
    }

    override fun noteOff(strip: Int, note: Int) {
        NativeAudioBridge.safeNoteOff(strip, note)
    }

    override fun allNotesOff(strip: Int) {
        NativeAudioBridge.safeAllNotesOff(strip)
    }

    override fun level(strip: Int): Float = NativeAudioBridge.safeFxStripPeak(strip)
}

/**
 * Application-scoped instrument manager. Created once so a loaded instrument survives the
 * soundfont window being closed — the zip requires that (closing the window must not unload).
 */
object AeInstruments {
    /** Supervisor so one failed preset tween never cancels the others. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val manager: AeManager by lazy { AeManager(AeInstrumentHost, scope) }

    fun session(strip: Int): AeSession? = manager.sessions[strip]

    fun isLoaded(strip: Int): Boolean = manager.sessions.containsKey(strip)

    /** Display name of the instrument on a strip, or null when the strip has no Æther instrument. */
    fun nameOn(strip: Int): String? = manager.sessions[strip]?.instrument?.title

    /** Loads an instrument on a strip and returns true when the engine accepted it. */
    fun load(strip: Int, instrument: AeInstrument): Boolean =
        manager.load(strip, instrument) != null

    fun unload(strip: Int) = manager.unload(strip)
}
