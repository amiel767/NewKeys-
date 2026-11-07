package com.soundstage.mixer.instruments

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.aetherkeys.organ.OrganFaderId
import com.aetherkeys.organ.OrganKnobId
import com.aetherkeys.organ.OrganListener
import com.aetherkeys.organ.OrganPreset
import com.aetherkeys.organ.OrganScreen
import com.aetherkeys.organ.OrganState

/**
 * The 46 parameters of `fx_organ.h`, in engine order, and what drives each one.
 *
 * [ui] names the control in the delivered organ panel, or is null when nothing on screen reaches
 * the parameter. The gaps are the point of the table: they are what the engine still owes the UI.
 */
internal object OrganParams {
    const val COUNT = 46

    data class Row(val index: Int, val name: String, val ui: String?, val note: String = "")

    val table: List<Row> = buildList {
        val feet = listOf("16'", "5⅓'", "8'", "4'", "2⅔'", "2'", "1⅗'", "1⅓'", "1'")
        feet.forEachIndexed { i, f -> add(Row(i, "Upper $f", "Drawbars $i")) }
        feet.forEachIndexed { i, f ->
            add(Row(9 + i, "Lower $f", null, "the panel has a single drawbar row: the lower manual is preset-only"))
        }
        add(Row(18, "Perc On", "B3 Perc master"))
        add(Row(19, "Perc Soft", "Soft"))
        add(Row(20, "Perc Fast", "Fast"))
        add(Row(21, "Perc 3rd", "Third"))
        add(Row(22, "Vib type", "V1 C1 V2 C2 V3 C3", "panel order V1 C1 V2 C2 V3 C3 is remapped to engine V1 V2 V3 C1 C2 C3"))
        add(Row(23, "Vib Upper", null, "the panel has no per-manual switch; engine reads upper||lower"))
        add(Row(24, "Vib Lower", null, "same"))
        add(Row(25, "Rotary speed", "FAST / SLOW", "the panel has no STOP: only .5 and 1 are reachable"))
        add(Row(26, "Click", null, "preset only"))
        add(Row(27, "Tone", null, "preset only: the panel's EQ is a real EQ now, not the voicing shelf"))
        add(Row(28, "Drive", "Drive"))
        add(Row(29, "Space", null, "preset only"))
        add(Row(30, "Level", null, "preset only"))
        add(Row(31, "Reverb mode", "Room / Stage / Hall"))
        add(Row(32, "Treble", "EQ Treble"))
        add(Row(33, "Cabinet", "Rotary ON/OFF"))
        add(Row(34, "Amount", "Reverb Amount"))
        add(Row(35, "VelSense", null, "preset only"))
        add(Row(36, "Mid", "EQ Mid", "peaking bell at 800 Hz, +/-18 dB, flat at .5"))
        add(Row(37, "Rot Rise", "Rotary Rise", "rotor ramp 0.7 s * 2^((v-.5)*4): .5 is the hardware's own"))
        add(Row(38, "Rot Fall", "Rotary Fall", "same law on the 2.5 s drum ramp"))
        add(Row(39, "Rot Depth", "Rotary Depth", "Leslie AM 0.70/0.50 at .5, doubled at 1"))
        add(Row(40, "Rot Mic", "Rotary Mic", "Doppler at .5, doubled at 1"))
        add(Row(41, "Rot Balance", "Horn / Drum", "horn * 2^(2(v-.5)), drum * 2^(-2(v-.5)): even at .5"))
        add(Row(42, "Model", "Organ Model", "0 Pipe, .5 B3, 1 Farf: a real voicing switch"))
        add(Row(43, "Model dial", "Organ Model dial", "+/-4 dB of top character, neutral at .5"))
        add(Row(44, "Octave", "Oct +/-", "transposes the notes, .5 = 0, -2..+2"))
        add(Row(45, "Bass", "EQ Bass", "low shelf at 250 Hz, +/-18 dB, flat at .5"))
    }

    /** Controls the panel draws that no engine parameter consumes yet. */
    val uiOnly: List<Pair<String, String>> = emptyList()
}

/** Panel vibrato order (V1 C1 V2 C2 V3 C3) to the engine's own V1 V2 V3 C1 C2 C3. */
private val VIB_TO_ENGINE = intArrayOf(0, 3, 1, 4, 2, 5)

/** Panel model order (0 B3, 1 Pipe, 2 Farf) to the engine's (0 Pipe, .5 B3, 1 Farf). */
private fun modelParam(index: Int) = when (index) {
    1 -> 0f
    2 -> 1f
    else -> 0.5f
}

/**
 * Bridges [OrganListener] onto the engine's normalized 0..1 parameters. [write] is
 * `(index, value) -> Unit`, which keeps the mapping testable without an audio session.
 */
class AeOrganListener(private val write: (Int, Float) -> Unit) : OrganListener {

    override fun onDrawbar(index: Int, value: Int) = write(index, value / 8f)

    override fun onPercussion(on: Boolean, soft: Boolean, fast: Boolean, third: Boolean) {
        write(18, if (on) 1f else 0f)
        write(19, if (soft) 1f else 0f)
        write(20, if (fast) 1f else 0f)
        write(21, if (third) 1f else 0f)
    }

    override fun onVibrato(type: Int) {
        write(22, (if (type in 0..5) VIB_TO_ENGINE[type] else 0) / 5f)
        val on = if (type >= 0) 1f else 0f
        write(23, on)
        write(24, on)
    }

    override fun onRotarySpeed(fast: Boolean) = write(25, if (fast) 1f else 0.5f)

    override fun onCabinet(on: Boolean) = write(33, if (on) 1f else 0f)

    override fun onKnob(id: OrganKnobId, value01: Float) {
        when (id) {
            OrganKnobId.EqTreble -> write(32, value01)
            OrganKnobId.EqMid -> write(36, value01)
            OrganKnobId.EqBass -> write(45, value01)
            OrganKnobId.Drive -> write(28, value01)
            OrganKnobId.ReverbAmount -> write(34, value01)
            OrganKnobId.RotaryRise -> write(37, value01)
            OrganKnobId.RotaryFall -> write(38, value01)
        }
    }

    override fun onFader(id: OrganFaderId, value01: Float) = when (id) {
        OrganFaderId.RotaryDepth -> write(39, value01)
        OrganFaderId.RotaryMic -> write(40, value01)
        OrganFaderId.RotaryHornDrum -> write(41, value01)
    }

    override fun onOctave(octave: Int) = write(44, (octave + 2) / 4f)

    override fun onReverbMode(mode: Float) = write(31, mode)

    override fun onModel(index: Int) = write(42, modelParam(index))

    override fun onModelDial(value01: Float) = write(43, value01)

    override fun onPresetApplied(preset: OrganPreset) {
        for (i in 0 until 9) {
            write(i, preset.upper[i] / 8f)
            write(9 + i, preset.lower[i] / 8f)
        }
        onPercussion(preset.perc.on, preset.perc.soft, preset.perc.fast, preset.perc.third)
        onVibrato(if (preset.vib.upper || preset.vib.lower) preset.vib.type else -1)
        onRotarySpeed(preset.rotary >= 1f)
        onCabinet(preset.cabinet)
        write(26, preset.click)
        write(27, preset.tone)
        write(28, preset.drive)
        write(29, preset.space)
        write(30, preset.level)
    }
}

/**
 * The organ panel, wired to one strip's engine parameters.
 *
 * The panel owns the sound while its window is open: opening it pushes the loaded preset into all
 * 36 parameters, so the engine matches what the panel shows instead of whatever the last session
 * left behind. The state is remembered per strip, so closing and reopening keeps the tweaks.
 */
@Composable
fun AeOrganScreen(c: AeCtl) {
    val state = remember(c.s.strip) { OrganState() }
    val listener = remember(c) { AeOrganListener { i, v -> c.set(i, v) } }
    DisposableEffect(state) { listener.onPresetApplied(state.currentPreset); onDispose { } }
    // The window draws its back arrow at x 10..40, and the panel starts at 8. Shift the block right
    // so the arrow lands on empty backdrop instead of on the Oct panel.
    OrganScreen(modifier = Modifier.fillMaxSize(), state = state, listener = listener, startInset = 40f)
}
