package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.instruments.AeHost
import com.soundstage.mixer.instruments.AeInstrument
import com.soundstage.mixer.instruments.AeInstrumentWindow
import com.soundstage.mixer.instruments.AeInstrumentsTabContent
import com.soundstage.mixer.instruments.AeManager
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

private class PianoSilentHost : AeHost {
    override fun loadInstrument(strip: Int, fxId: Int) = true
    override fun unloadInstrument(strip: Int) = Unit
    override fun setParam(strip: Int, index: Int, value: Float) = Unit
    override fun noteOn(strip: Int, note: Int, velocity: Int) = Unit
    override fun noteOff(strip: Int, note: Int) = Unit
    override fun allNotesOff(strip: Int) = Unit
    override fun level(strip: Int) = 0f
}

/** Piano window, one capture per factory preset, so the 9 models can be compared side by side. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h460dp-440dpi", sdk = [36])
class AePianoCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun capture(name: String, preset: Int, cards: Boolean = false) {
        val context = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
        initAetherInstruments(context)
        val manager = AeManager(PianoSilentHost(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        manager.load(0, AeInstrument.PIANO)
        if (preset > 0) manager.loadPreset(0, preset)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.size(1000.dp, 460.dp).background(Color(0xFF07090F))) {
                if (cards) AeInstrumentsTabContent(manager = manager, strip = 0, onOpen = {})
                else AeInstrumentWindow(manager = manager, strip = 0, onClose = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    @Test fun ae_piano_window() = capture("ae_piano_window", 0)
    @Test fun ae_piano_felt() = capture("ae_piano_felt", 1)
    @Test fun ae_piano_bright() = capture("ae_piano_bright", 3)
    @Test fun ae_piano_electric() = capture("ae_piano_electric", 9)
    @Test fun ae_piano_cards() = capture("ae_piano_cards", 0, cards = true)
}
