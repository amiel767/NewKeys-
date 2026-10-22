package com.aetherkeys.fx.ui

import com.soundstage.mixer.fx.FxController

/**
 * Binds the shell's [FxEngine] to the existing [FxController]. No new native call
 * is introduced: every method maps to one already used by the old FX window.
 * The controller is bound to one strip, so the store is created per strip and the
 * strip argument is informational here.
 */
class FxControllerEngine(private val controller: FxController) : FxEngine {

    override fun setEffect(strip: Int, slot: Int, id: Int): Boolean = controller.setEffect(slot, id)

    override fun setInstrument(strip: Int, id: Int): Boolean = controller.setInstrument(id)

    override fun setParam(strip: Int, slot: Int, index: Int, value: Float) =
        controller.setEffectParam(slot, index, value)

    override fun setInstrumentParam(strip: Int, index: Int, value: Float) =
        controller.setInstrumentParam(index, value)

    override fun setBypass(strip: Int, slot: Int, bypassed: Boolean) =
        controller.setBypass(slot, bypassed)

    override fun applyPreset(strip: Int, slot: Int, presetIndex: Int): Boolean =
        controller.applyPreset(slot, presetIndex)

    override fun meter(strip: Int, slot: Int, index: Int): Float = controller.meter(slot, index)
}
