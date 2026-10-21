package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aetherkeys.fx.ui.FxEngine
import com.aetherkeys.fx.ui.FxShellStore
import com.aetherkeys.fx.ui.FxWindowHost
import com.aetherkeys.fx.ui.fxModules
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Records every engine call so the shell can be captured without the native library. */
private class RecordingEngine : FxEngine {
    val effects = HashMap<Pair<Int, Int>, Int>()
    override fun setEffect(strip: Int, slot: Int, id: Int): Boolean {
        effects[strip to slot] = id
        return true
    }
    override fun setInstrument(strip: Int, id: Int) = true
    override fun setParam(strip: Int, slot: Int, index: Int, value: Float) = Unit
    override fun setInstrumentParam(strip: Int, index: Int, value: Float) = Unit
    override fun setBypass(strip: Int, slot: Int, bypassed: Boolean) = Unit
    override fun applyPreset(strip: Int, slot: Int, presetIndex: Int) = true
    override fun meter(strip: Int, slot: Int, index: Int) = 0f
}

/**
 * Captures the real FX window (window 840 x 389 logical, as measured on the
 * 1728 x 800 references at 2x) with the Amp (10) open, for a side-by-side with
 * `ref-10-amp.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])
class FxWindowCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun capture(name: String, configure: (FxShellStore) -> Unit) {
        val store = FxShellStore(fxModules(), RecordingEngine())
        configure(store)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.size(840.dp, 389.dp).background(Color(0xFF0B0F17))) {
                FxWindowHost(store = store, scale = 1f)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    @Test
    fun fx_window_amp() {
        capture("fx_window_amp") { store ->
            store.addEffect(10)
            store.openId = 10
        }
    }

    @Test
    fun fx_window_rack() {
        capture("fx_window_rack") { store ->
            store.addEffect(10)
            store.addEffect(0)
            store.addEffect(1)
        }
    }
}
