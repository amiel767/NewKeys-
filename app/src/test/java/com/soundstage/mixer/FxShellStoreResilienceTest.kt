package com.soundstage.mixer

import com.aetherkeys.fx.ui.FxEngine
import com.aetherkeys.fx.ui.FxShellStore
import com.aetherkeys.fx.ui.fxModules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The native library is missing: every call throws, which is what the JNI
 * bridge does when the mixer is not up.
 */
private class DeadEngine : FxEngine {
    private fun dead(): Nothing = throw IllegalStateException("no native library")
    override fun setEffect(strip: Int, slot: Int, id: Int) = dead()
    override fun setInstrument(strip: Int, id: Int) = dead()
    override fun setParam(strip: Int, slot: Int, index: Int, value: Float) = dead()
    override fun setInstrumentParam(strip: Int, index: Int, value: Float) = dead()
    override fun setBypass(strip: Int, slot: Int, bypassed: Boolean) = dead()
    override fun applyPreset(strip: Int, slot: Int, presetIndex: Int) = dead()
    override fun meter(strip: Int, slot: Int, index: Int) = dead()
}

/**
 * A library that loads but rejects the writes, so an effect can be placed in a
 * slot and the parameter, bypass and preset paths are actually reached.
 */
private class WriteDeadEngine : FxEngine {
    override fun setEffect(strip: Int, slot: Int, id: Int) = true
    override fun setInstrument(strip: Int, id: Int) = true
    override fun setParam(strip: Int, slot: Int, index: Int, value: Float) = throw IllegalStateException("write refused")
    override fun setInstrumentParam(strip: Int, index: Int, value: Float) = throw IllegalStateException("write refused")
    override fun setBypass(strip: Int, slot: Int, bypassed: Boolean) = throw IllegalStateException("write refused")
    override fun applyPreset(strip: Int, slot: Int, presetIndex: Int): Boolean = throw IllegalStateException("write refused")
    override fun meter(strip: Int, slot: Int, index: Int) = 0f
}

/**
 * A failing engine must not be able to crash the FX window: an exception thrown
 * from a Compose event handler tears the window down with no way back. Every
 * call is folded into the store's notice instead.
 */
class FxShellStoreResilienceTest {

    @Test
    fun addEffectWithDeadEngineReportsInsteadOfThrowing() {
        val s = FxShellStore(fxModules(), DeadEngine())
        assertFalse("a refused add must report failure", s.addEffect(10))
        assertNotNull("the failure must surface as a notice", s.notice)
        assertEquals("the slot must stay free", 0, s.effectCount())
    }

    @Test
    fun setInstrumentWithDeadEngineReportsInsteadOfThrowing() {
        val s = FxShellStore(fxModules(), DeadEngine())
        assertFalse(s.setInstrument(5))
        assertNotNull(s.notice)
    }

    @Test
    fun setParamWithRefusingEngineReportsInsteadOfThrowing() {
        val s = FxShellStore(fxModules(), WriteDeadEngine())
        assertTrue("the effect is placed, only the writes fail", s.addEffect(10))
        s.setParam(10, 0, 0.5f)
        assertNotNull("the refused write must surface", s.notice)
    }

    @Test
    fun bypassAndPresetWithRefusingEngineReportInsteadOfThrowing() {
        val s = FxShellStore(fxModules(), WriteDeadEngine())
        assertTrue(s.addEffect(10))
        s.toggleBypass(10)
        assertNotNull(s.notice)
        s.applyPreset(10, 0)
        assertNotNull(s.notice)
    }

    @Test
    fun removeEffectWithRefusingEngineReportsInsteadOfThrowing() {
        val s = FxShellStore(fxModules(), WriteDeadEngine())
        s.addEffect(10)
        s.removeEffect(10)
        assertEquals(0, s.effectCount())
    }
}
