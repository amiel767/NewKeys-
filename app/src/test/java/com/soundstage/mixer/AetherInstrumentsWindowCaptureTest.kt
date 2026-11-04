package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.instruments.AeHost
import com.soundstage.mixer.instruments.AeInstrument
import com.soundstage.mixer.instruments.AeManager
import com.soundstage.mixer.instruments.AetherInstrumentsWindow
import com.soundstage.mixer.instruments.SoundSourceChooserPopup
import com.soundstage.mixer.instruments.initAetherInstruments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class AeWinSilentHost : AeHost {
    override fun loadInstrument(strip: Int, fxId: Int) = true
    override fun unloadInstrument(strip: Int) = Unit
    override fun setParam(strip: Int, index: Int, value: Float) = Unit
    override fun noteOn(strip: Int, note: Int, velocity: Int) = Unit
    override fun noteOff(strip: Int, note: Int) = Unit
    override fun allNotesOff(strip: Int) = Unit
    override fun level(strip: Int) = 0f
}

/**
 * Captures the new Æther Instruments window (list state) and the sound-source chooser, so the
 * migration of the Soundfont window's shell can be compared against it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w900dp-h460dp-440dpi", sdk = [36])
class AetherInstrumentsWindowCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun manager(load: AeInstrument? = null): AeManager {
        val context = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
        initAetherInstruments(context)
        val m = AeManager(AeWinSilentHost(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        load?.let { m.load(0, it) }
        return m
    }

    /** Full-bleed: no padding, so the rows are exactly as wide as in the real window. */
    @Test fun aether_window_list() {
        val m = manager()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0E131C))) {
                AetherInstrumentsWindow(manager = m, strip = 0, isOpen = true, onClose = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/aether_window_list.png")
    }

    @Test fun sound_source_chooser() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0E131C))) {
                SoundSourceChooserPopup(onPickSoundfonts = {}, onPickAether = {}, onClose = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/sound_source_chooser.png")
    }

    /**
     * The instrument interface, hosted *inside* the window rather than in an overlay of its own.
     * Captured at the real 1000x460 stage so the layout can be checked against the mock-up; the
     * piano is used because it is the busiest of the five (model list, groups of knobs, plate).
     */
    @Test fun aether_window_instrument() {
        val m = manager(AeInstrument.PIANO)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0E131C))) {
                AetherInstrumentsWindow(manager = m, strip = 0, isOpen = true, onClose = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/aether_window_instrument.png")
    }
}
