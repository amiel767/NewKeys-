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
 * The 36 parameters of `fx_organ.h`, in engine order, and what drives each one.
 *
 * [ui] names the control in the delivered organ panel, or is null when nothing on screen reaches
 * the parameter. The gaps are the point of the table: they are what the engine still owes the UI.
 */
internal object OrganParams {
    const val COUNT = 36

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
        add(Row(27, "Tone", "EQ Bass", "the engine's voicing doubles as the bass band"))
        add(Row(28, "Drive", "Drive"))
        add(Row(29, "Space", null, "preset only"))
        add(Row(30, "Level", null, "preset only"))
        add(Row(31, "Reverb mode", "Room / Stage / Hall"))
        add(Row(32, "Treble", "EQ Treble"))
        add(Row(33, "Cabinet", "Rotary ON/OFF"))
        add(Row(34, "Amount", "Reverb Amount"))
        add(Row(35, "VelSense", null, "preset only"))
    }

    /** Controls the panel draws that no engine parameter consumes yet. */
    val uiOnly: List<Pair<String, String>> = listOf(
        "EQ Mid" to "the engine has one tone shelf, no mid band",
        "Rotary Rise / Fall" to "the Leslie ramp is fixed at 0.7 s / 2.5 s in the engine",
        "Rotary Depth / Mic" to "no engine parameter",
        "Horn / Drum" to "no engine parameter",
        "Organ Model (B3 / Pipe / Farf) + dial" to "the engine is one additive tonewheel voicing",
        "Octave ±" to "must transpose the incoming notes, not a parameter",
    )
}

/** Panel vibrato order (V1 C1 V2 C2 V3 C3) to the engine's own V1 V2 V3 C1 C2 C3. */
private val VIB_TO_ENGINE = intArrayOf(0, 3, 1, 4, 2, 5)

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
            OrganKnobId.EqBass -> write(27, value01)
            OrganKnobId.Drive -> write(28, value01)
            OrganKnobId.ReverbAmount -> write(34, value01)
            else -> Unit // EQ Mid, Rotary Rise / Fall: no engine parameter yet
        }
    }

    override fun onFader(id: OrganFaderId, value01: Float) = Unit // Depth / Mic / Horn-Drum: no engine parameter yet

    override fun onOctave(octave: Int) = Unit // applied to the note numbers upstream, not a parameter

    override fun onReverbMode(mode: Float) = write(31, mode)

    override fun onModel(index: Int) = Unit

    override fun onModelDial(value01: Float) = Unit

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
