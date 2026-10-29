package com.soundstage.mixer.instruments

import android.content.Context
import android.util.Log
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.soundstage.mixer.R
import com.soundstage.mixer.audio.NativeAudioBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Assigns the bundled fonts to the instrument UI once, at startup. */
fun initAetherInstruments(context: Context) {
    AeFonts.plate = FontFamily(Font(R.font.cinzel))
    AeFonts.mono = FontFamily(Font(R.font.share_tech_mono))
    AeFonts.ui = FontFamily(Font(R.font.plus_jakarta_sans))
    AeFonts.display = FontFamily(Font(R.font.sora))
    AeBanks.app = context.applicationContext
    AeUserStore.app = context.applicationContext
}

/**
 * Reads the sampled banks shipped in `assets/saxpack/`. They are loaded on demand and kept
 * decoded as a ByteArray, because the native side copies what it needs: a 2 MB array held
 * once is cheaper than re-reading the asset on every instrument load.
 */
object AeBanks {
    /** Set by [initAetherInstruments]; null in unit tests, where the load simply fails. */
    @Volatile var app: Context? = null
    private val cache = HashMap<String, ByteArray>()
    /** Bank file per instrument type (0 soprano, 1 alto, 2 tenor, 3 baritone), empty = none. */
    private val saxFiles = arrayOf("saxpack_sop.bin", "saxpack_alt.bin", "saxpack_ten.bin", "saxpack_bar.bin")

    /** The bank belonging to an instrument, or null when it has none (organ, synth, leads). */
    fun fileFor(instrument: AeInstrument, saxType: Int): String? = when (instrument) {
        AeInstrument.SAX -> saxFiles[saxType.coerceIn(0, 3)]
        else -> null
    }

    fun read(name: String): ByteArray? {
        cache[name]?.let { return it }
        val ctx = app ?: return null
        return try {
            ctx.assets.open("saxpack/$name").use { it.readBytes() }.also { cache[name] = it }
        } catch (e: Throwable) {
            Log.e("AeBanks", "Cannot read asset saxpack/$name: ${e.message}")
            null
        }
    }
}

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

    override fun loadBank(strip: Int, instrument: AeInstrument, type: Int, mix: Float): Boolean {
        // Only the sax ships a bank today. `type` is the sax's first parameter, the same
        // value the engine reads to pick a formant set, so the two stay in step.
        if (instrument != AeInstrument.SAX) return false
        val name = AeBanks.fileFor(instrument, type) ?: return false
        val bytes = AeBanks.read(name) ?: return false
        val ok = NativeAudioBridge.safeLoadInstrumentBank(strip, bytes)
        // The bank is the voice, not a layer on top of the synthesis: a mix of 1 makes the
        // engine output the samples alone. The synthesis still runs underneath to cover the
        // seams between regions, but it is weighted out of the sum.
        if (ok) NativeAudioBridge.safeSetInstrumentSampleMix(strip, mix.coerceIn(0f, 1f))
        Log.d("AeInstrumentHost", "loadBank(strip=$strip, $name, ${bytes.size} o) -> $ok")
        return ok
    }

    override fun bankMix(strip: Int): Float = NativeAudioBridge.safeInstrumentSampleMix(strip)
}

/**
 * Application-scoped instrument manager. Created once so a loaded instrument survives the
 * soundfont window being closed — the zip requires that (closing the window must not unload).
 */
object AeInstruments {
    /** Supervisor so one failed preset tween never cancels the others. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val manager: AeManager by lazy { AeManager(AeInstrumentHost, scope) }

    /**
     * Set by the ViewModel. Called before an instrument loads on a strip so the app can
     * release whatever else occupies it — an Æther instrument replaces the soundfont,
     * exactly like loading a soundfont replaces the Æther instrument.
     */
    var onBeforeLoad: ((Int) -> Unit)? = null

    /**
     * Called after an instrument has been installed on a strip, so the app can push the
     * track's own fader/pan onto the strip: a source renders outside FluidSynth, so from
     * that moment the strip is the only place its volume lives.
     */
    var onLoaded: ((Int) -> Unit)? = null

    fun session(strip: Int): AeSession? = manager.sessions[strip]

    fun isLoaded(strip: Int): Boolean = manager.sessions.containsKey(strip)

    /** Display name of the instrument on a strip, or null when the strip has no Æther instrument. */
    fun nameOn(strip: Int): String? = manager.sessions[strip]?.instrument?.title

    /** Loads an instrument on a strip and returns true when the engine accepted it. */
    fun load(strip: Int, instrument: AeInstrument): Boolean {
        if (manager.sessions[strip]?.instrument == instrument) return true
        onBeforeLoad?.invoke(strip)
        val ok = manager.load(strip, instrument) != null
        if (ok) onLoaded?.invoke(strip)
        return ok
    }

    fun unload(strip: Int) = manager.unload(strip)
}
