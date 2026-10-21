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
 * Captures the real FX window on its fixed logical canvas (825 x 343, the
 * reference's PWL/PHL) laid out on the 840 x 389 window measured on the
 * 1728 x 800 references at 2x, for a side-by-side with `ref-NN-*.png`.
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
                FxWindowHost(store = store)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    /** `fx_layout_spec.json` order: Ampli (10), Maximizer (4), then the rest. */
    @Test
    fun fx_window_amp() {
        capture("fx_window_amp") { store ->
            store.addEffect(10)
            store.openId = 10
        }
    }

    @Test
    fun fx_window_echo() {
        capture("fx_window_echo") { store ->
            store.addEffect(1)
            store.openId = 1
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
