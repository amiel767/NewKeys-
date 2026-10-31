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
 * reference's PWL x PHL) so the scale is exactly 1. The 1728 x 800 references
 * carry a page margin (12 px sides, 38 px top, 10 px bottom) around the window;
 * `compare_scaled.py` resizes for the side-by-side.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w825dp-h343dp-440dpi", sdk = [36])
class FxWindowCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun capture(name: String, configure: (FxShellStore) -> Unit) {
        val store = FxShellStore(fxModules(), RecordingEngine())
        configure(store)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.size(825.dp, 343.dp).background(Color(0xFF0B0F17))) {
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

    /**
     * One capture per effect, named after the reference file so the side-by-side
     * compares like with like. `ref-XX-<tag>.png` in fx-screens-spec/references.
     * One test per effect: `setContent` may only be called once per test.
     */
    private val tags = listOf(
        0 to "space", 1 to "echo", 2 to "aether-sculpt", 3 to "iron-grip",
        4 to "goodizer", 5 to "piano", 6 to "organ", 7 to "sax",
        8 to "strings", 9 to "guitar", 10 to "amp", 11 to "bass",
        12 to "brass", 13 to "voices", 14 to "synth",
    )

    private fun captureEffect(id: Int, tag: String) = capture("fx_window_$tag") { store ->
        store.addEffect(id)
        store.openId = id
    }


    @Test
    fun fx_window_space() = captureEffect(0, "space")

    @Test
    fun fx_window_aether_sculpt() = captureEffect(2, "aether-sculpt")

    @Test
    fun fx_window_iron_grip() = captureEffect(3, "iron-grip")

    @Test
    fun fx_window_goodizer() = captureEffect(4, "goodizer")

    @Test
    fun fx_window_piano() = captureEffect(5, "piano")

    @Test
    fun fx_window_organ() = captureEffect(6, "organ")

    @Test
    fun fx_window_sax() = captureEffect(7, "sax")

    @Test
    fun fx_window_strings() = captureEffect(8, "strings")

    @Test
    fun fx_window_guitar() = captureEffect(9, "guitar")

    @Test
    fun fx_window_bass() = captureEffect(11, "bass")

    @Test
    fun fx_window_brass() = captureEffect(12, "brass")

    @Test
    fun fx_window_voices() = captureEffect(13, "voices")

    @Test
    fun fx_window_synth() = captureEffect(14, "synth")

    @Test
    fun fx_window_rack() {
        capture("fx_window_rack") { store ->
            store.addEffect(10)
            store.addEffect(0)
            store.addEffect(1)
        }
    }
}
