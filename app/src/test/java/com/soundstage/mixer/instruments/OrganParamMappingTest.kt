package com.soundstage.mixer.instruments

import com.aetherkeys.organ.OrganFaderId
import com.aetherkeys.organ.OrganKnobId
import com.aetherkeys.organ.OrganPercussion
import com.aetherkeys.organ.OrganPreset
import com.aetherkeys.organ.OrganVibrato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The panel-to-engine parameter table. Every control the panel offers must land on the parameter
 * `fx_organ.h` reads, and every control with no parameter must write nothing at all, so a knob that
 * the engine ignores cannot quietly overwrite a neighbouring one.
 */
class OrganParamMappingTest {

    private val written = mutableMapOf<Int, Float>()
    private val listener = AeOrganListener { i, v -> written[i] = v }

    private fun assertWrote(index: Int, expected: Float) {
        val got = written[index]
        assertEquals("param $index", expected, got ?: Float.NaN, 1e-4f)
    }

    @Test fun drawbar_maps_zero_to_eight_onto_0_1() {
        listener.onDrawbar(0, 8)
        listener.onDrawbar(8, 0)
        assertWrote(0, 1f)
        assertWrote(8, 0f)
    }

    @Test fun percussion_writes_the_four_rockers() {
        listener.onPercussion(on = true, soft = false, fast = true, third = false)
        assertWrote(18, 1f); assertWrote(19, 0f); assertWrote(20, 1f); assertWrote(21, 0f)
    }

    @Test fun vibrato_type_is_remapped_from_panel_order_to_engine_order() {
        // Panel order V1 C1 V2 C2 V3 C3; engine order V1 V2 V3 C1 C2 C3.
        listener.onVibrato(0); assertWrote(22, 0f)   // V1 -> engine 0
        listener.onVibrato(1); assertWrote(22, 0.6f) // C1 -> engine 3
        listener.onVibrato(2); assertWrote(22, 0.2f) // V2 -> engine 1
        listener.onVibrato(3); assertWrote(22, 0.8f) // C2 -> engine 4
        listener.onVibrato(4); assertWrote(22, 0.4f) // V3 -> engine 2
        listener.onVibrato(5); assertWrote(22, 1f)   // C3 -> engine 5
    }

    @Test fun vibrato_off_clears_both_manuals() {
        listener.onVibrato(-1)
        assertWrote(23, 0f); assertWrote(24, 0f)
    }

    @Test fun rotary_speed_has_no_stop_from_the_panel() {
        listener.onRotarySpeed(true); assertWrote(25, 1f)
        listener.onRotarySpeed(false); assertWrote(25, 0.5f)
    }

    @Test fun cabinet_is_the_engine_cabinet() {
        listener.onCabinet(true); assertWrote(33, 1f)
        listener.onCabinet(false); assertWrote(33, 0f)
    }

    @Test fun eq_and_drive_land_on_their_parameters() {
        listener.onKnob(OrganKnobId.EqTreble, 0.7f); assertWrote(32, 0.7f)
        listener.onKnob(OrganKnobId.EqMid, 0.9f); assertWrote(36, 0.9f)
        listener.onKnob(OrganKnobId.EqBass, 0.4f); assertWrote(45, 0.4f)
        listener.onKnob(OrganKnobId.Drive, 0.9f); assertWrote(28, 0.9f)
        listener.onKnob(OrganKnobId.ReverbAmount, 0.2f); assertWrote(34, 0.2f)
    }

    @Test fun the_rotary_knobs_own_their_rotor_ramps() {
        listener.onKnob(OrganKnobId.RotaryRise, 0.25f); assertWrote(37, 0.25f)
        listener.onKnob(OrganKnobId.RotaryFall, 0.75f); assertWrote(38, 0.75f)
    }

    @Test fun the_rotary_faders_own_depth_mic_and_balance() {
        listener.onFader(OrganFaderId.RotaryDepth, 0.1f); assertWrote(39, 0.1f)
        listener.onFader(OrganFaderId.RotaryMic, 0.6f); assertWrote(40, 0.6f)
        listener.onFader(OrganFaderId.RotaryHornDrum, 0.9f); assertWrote(41, 0.9f)
    }

    @Test fun octave_is_centred_on_the_parameter_and_spans_the_engine_range() {
        listener.onOctave(-2); assertWrote(44, 0f)
        listener.onOctave(0); assertWrote(44, 0.5f)
        listener.onOctave(2); assertWrote(44, 1f)
    }

    @Test fun the_model_selector_maps_its_own_order_onto_the_engines() {
        listener.onModel(1); assertWrote(42, 0f)    // Pipe
        listener.onModel(0); assertWrote(42, 0.5f)  // B3, the neutral voicing
        listener.onModel(2); assertWrote(42, 1f)    // Farf
    }

    @Test fun the_model_dial_is_its_own_parameter() {
        listener.onModelDial(0.35f); assertWrote(43, 0.35f)
    }

    @Test fun every_panel_control_now_reaches_the_engine() {
        assertEquals("the panel has no dead control left", 0, OrganParams.uiOnly.size)
        assertEquals("the table must cover the whole engine contract", OrganParams.COUNT, OrganParams.table.size)
        assertEquals(
            "every engine index must be described exactly once",
            (0 until OrganParams.COUNT).toList(),
            OrganParams.table.map { it.index },
        )
    }

    @Test fun reverb_mode_keeps_its_normalized_value() {
        listener.onReverbMode(0f); assertWrote(31, 0f)
        listener.onReverbMode(0.5f); assertWrote(31, 0.5f)
        listener.onReverbMode(1f); assertWrote(31, 1f)
    }

    @Test fun a_preset_pushes_every_parameter_it_owns() {
        val preset = OrganPreset(
            "t", listOf(8, 0, 8, 0, 0, 0, 0, 0, 0), listOf(1, 2, 3, 4, 5, 6, 7, 8, 0),
            OrganPercussion(true, true, false, true), OrganVibrato(3, true, true), 1f,
            click = 0.2f, tone = 0.6f, drive = 0.4f, space = 0.1f, level = 0.8f, cabinet = true,
        )
        listener.onPresetApplied(preset)
        assertWrote(0, 1f); assertWrote(2, 1f); assertWrote(8, 0f)
        assertWrote(9, 1f / 8f); assertWrote(17, 0f)
        assertWrote(18, 1f); assertWrote(19, 1f); assertWrote(20, 0f); assertWrote(21, 1f)
        assertWrote(26, 0.2f); assertWrote(27, 0.6f); assertWrote(28, 0.4f); assertWrote(29, 0.1f); assertWrote(30, 0.8f)
        assertWrote(33, 1f)
        assertNull("the preset must not touch the reverb mode, which is not part of it", written[31])
    }
}
