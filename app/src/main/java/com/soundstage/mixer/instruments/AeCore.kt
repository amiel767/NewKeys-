package com.soundstage.mixer.instruments

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

/** The two Æther Instruments. [fxId] = id of the native Source in FxRegistry (FX_ORGAN = 6, FX_SYNTH = 14). */
enum class AeInstrument(val fxId: Int, val title: String, val paramCount: Int) {
    ORGAN(6, "Æther Organ", 31), SYNTH(14, "Æther Synth", 32);
    /** Parameters that switch instantly on a preset change (the others glide). */
    val discrete: IntArray get() = if (this == ORGAN) intArrayOf(18, 19, 20, 21, 22, 23, 24, 25) else intArrayOf(11, 18, 19, 26)
}

class AePreset(val name: String, val category: String, val values: FloatArray)

/** Value text shown under the knobs. Same formulas as the validated preview. */
object AeFmt {
    private val wn = arrayOf("SIN", "TRI", "SAW", "SQR")
    private fun pct(v: Float) = "${(v * 100).toInt()}%"
    fun format(inst: AeInstrument, i: Int, v: Float): String = if (inst == AeInstrument.ORGAN) {
        when (i) { 26, 27, 28, 29 -> pct(v); 30 -> "${(-30 + v * 36).toInt()}dB"; else -> "" }
    } else when (i) {
        0, 1 -> wn[(v * 3).roundToInt().coerceIn(0, 3)]
        2 -> "${(v * 50).toInt()}ct"
        3, 4, 6, 8, 9, 10, 17, 21, 22, 23, 24, 25 -> pct(v)
        5 -> "×${1 + (v * 2).roundToInt()}"
        7 -> { val f = 20f * 1000f.pow(v); if (f >= 1000f) "${"%.1f".format(f / 1000f)}kHz" else "${f.toInt()}Hz" }
        16, 20 -> "${"%.1f".format(0.1f * 100f.pow(v))}Hz"
        27 -> "${(v * v * 1500).toInt()}ms"
        28 -> "${1 + (v * 7).roundToInt()}"
        29 -> "${(v * 12).roundToInt()}st"
        30 -> "${((v - 0.5f) * 200).toInt()}ct"
        31 -> "${(-40 + v * 46).toInt()}dB"
        else -> ""
    }
}

/**
 * What the app must provide. Maps 1:1 onto the native FxMixer facade (fx_mixer.h):
 *   loadInstrument -> setInstrument(strip, fxId)   unloadInstrument -> setInstrument(strip, -1)
 *   setParam -> setInstrumentParam(strip, index, norm)   noteOn/noteOff/allNotesOff -> same names on FxMixer
 * Everything must be safe to call from the UI thread (post to the audio thread inside the JNI layer).
 */
interface AeHost {
    fun loadInstrument(strip: Int, fxId: Int): Boolean
    fun unloadInstrument(strip: Int)
    fun setParam(strip: Int, index: Int, value: Float)
    fun noteOn(strip: Int, note: Int, velocity: Int)
    fun noteOff(strip: Int, note: Int)
    fun allNotesOff(strip: Int)
    /** Output level 0..1 of the strip's instrument, for the animations. Return 0f if unavailable. */
    fun level(strip: Int): Float = 0f
}

/** One loaded instrument on one strip. Lives in [AeManager], so it survives the window being closed. */
class AeSession(val strip: Int, val instrument: AeInstrument, init: FloatArray) {
    val values = mutableStateListOf<Float>().also { l -> init.forEach { l.add(it) } }
    var preset by mutableIntStateOf(0)
    var page by mutableIntStateOf(0)
    var level by mutableFloatStateOf(0f)
    var tweening by mutableStateOf(false)
    val held = mutableStateMapOf<Int, Boolean>()
    @Volatile var noteEvents = 0
}

/**
 * App-scoped (create once, e.g. in the Application or a singleton ViewModel). Selecting an instrument loads it
 * immediately; closing the window does not unload it; [unload] does. MIDI enters through the onMidi* functions.
 */
class AeManager(private val host: AeHost, private val scope: CoroutineScope) {
    val sessions = mutableStateMapOf<Int, AeSession>()
    private val tweens = HashMap<Int, Job>()
    private val sustain = HashMap<Int, Boolean>()
    private val pending = HashMap<Int, MutableSet<Int>>()

    /** Default MIDI CC map (editable). Value = parameter index. CC1 on the organ drives the Leslie speed. */
    val ccOrgan = mutableMapOf(74 to 27, 91 to 29, 7 to 30, 71 to 28)
    val ccSynth = mutableMapOf(74 to 7, 71 to 8, 73 to 12, 72 to 15, 1 to 23, 91 to 25, 7 to 31, 5 to 27)

    fun presets(i: AeInstrument): List<AePreset> = if (i == AeInstrument.ORGAN) AePresets.organ else AePresets.synth

    /** Starts the instrument now (audio is live as soon as this returns true). Re-selecting the same one keeps its state. */
    fun load(strip: Int, inst: AeInstrument): AeSession? {
        sessions[strip]?.let { if (it.instrument == inst) return it else unload(strip) }
        if (!host.loadInstrument(strip, inst.fxId)) return null
        val p = presets(inst)[0]
        val s = AeSession(strip, inst, p.values)
        sessions[strip] = s
        p.values.forEachIndexed { i, v -> host.setParam(strip, i, v) }
        return s
    }

    fun unload(strip: Int) {
        tweens.remove(strip)?.cancel()
        if (sessions.remove(strip) != null) { host.allNotesOff(strip); host.unloadInstrument(strip) }
        sustain.remove(strip); pending.remove(strip)
    }

    fun setParam(strip: Int, index: Int, value: Float) {
        val s = sessions[strip] ?: return
        if (index !in s.values.indices) return
        val v = value.coerceIn(0f, 1f)
        s.values[index] = v
        host.setParam(strip, index, v)
    }

    fun loadPreset(strip: Int, index: Int) {
        val s = sessions[strip] ?: return
        val list = presets(s.instrument)
        val n = ((index % list.size) + list.size) % list.size
        s.preset = n
        val to = list[n].values
        val from = s.values.toFloatArray()
        val disc = s.instrument.discrete
        disc.forEach { setParam(strip, it, to[it]) }
        tweens.remove(strip)?.cancel()
        s.tweening = true
        tweens[strip] = scope.launch {
            val t0 = System.nanoTime()
            while (true) {
                val u = ((System.nanoTime() - t0) / 1e6f / 420f).coerceIn(0f, 1f)
                val e = 1f - (1f - u) * (1f - u) * (1f - u)
                for (k in to.indices) if (k !in disc) setParam(strip, k, from[k] + (to[k] - from[k]) * e)
                if (u >= 1f) break
                delay(16)
            }
            s.tweening = false
        }
    }

    fun noteOn(strip: Int, note: Int, velocity: Int = 100) {
        val s = sessions[strip] ?: return
        s.held[note] = true; s.noteEvents++
        host.noteOn(strip, note, velocity.coerceIn(1, 127))
    }

    fun noteOff(strip: Int, note: Int) {
        val s = sessions[strip] ?: return
        if (sustain[strip] == true) { pending.getOrPut(strip) { mutableSetOf() }.add(note); return }
        s.held.remove(note); host.noteOff(strip, note)
    }

    fun setSustain(strip: Int, down: Boolean) {
        sustain[strip] = down
        if (!down) pending.remove(strip)?.forEach { sessions[strip]?.held?.remove(it); host.noteOff(strip, it) }
    }

    fun pollLevel(strip: Int) { sessions[strip]?.level = host.level(strip).coerceIn(0f, 1f) }

    // ---- MIDI entry points: call these from the app's MIDI dispatcher. Channel n (0-based) = strip n.
    /** @return true if an Æther instrument consumed the event (so the app can skip FluidSynth for it). */
    fun onMidiNoteOn(channel: Int, note: Int, velocity: Int): Boolean {
        if (sessions[channel] == null) return false
        if (velocity == 0) noteOff(channel, note) else noteOn(channel, note, velocity)
        return true
    }
    fun onMidiNoteOff(channel: Int, note: Int): Boolean { if (sessions[channel] == null) return false; noteOff(channel, note); return true }
    fun onMidiCc(channel: Int, cc: Int, value: Int): Boolean {
        val s = sessions[channel] ?: return false
        if (cc == 64) { setSustain(channel, value >= 64); return true }
        if (cc == 1 && s.instrument == AeInstrument.ORGAN) { setParam(channel, 25, if (value > 63) 1f else 0.5f); return true }
        val map = if (s.instrument == AeInstrument.ORGAN) ccOrgan else ccSynth
        val idx = map[cc] ?: return false
        setParam(channel, idx, value / 127f); return true
    }
    fun onMidiAllNotesOff(channel: Int) { sessions[channel]?.let { it.held.clear(); host.allNotesOff(channel) } }
}
