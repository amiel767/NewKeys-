package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.soundstage.mixer.instruments.AeHost
import com.soundstage.mixer.instruments.AeInstrument
import com.soundstage.mixer.instruments.AeInstrumentWindow
import com.soundstage.mixer.instruments.AeManager
import com.soundstage.mixer.instruments.initAetherInstruments
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * No-op engine: the window's own loop calls level()/scope() every frame, and the native
 * library is absent under Robolectric. Everything else is the real UI.
 */
private class SilentHost : AeHost {
    override fun loadInstrument(strip: Int, fxId: Int) = true
    override fun unloadInstrument(strip: Int) = Unit
    override fun setParam(strip: Int, index: Int, value: Float) = Unit
    override fun noteOn(strip: Int, note: Int, velocity: Int) = Unit
    override fun noteOff(strip: Int, note: Int) = Unit
    override fun allNotesOff(strip: Int) = Unit
    override fun level(strip: Int) = 0f
}

/**
 * Captures the real Æther instrument windows on their fixed logical stage (1000 x 460),
 * so the scale is exactly 1 and the letterbox does not kick in. Same recipe as
 * [FxWindowCaptureTest]: fonts must be assigned, and the clock is frozen before a fixed
 * advance so the entry animation is deterministic.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h460dp-440dpi", sdk = [36])
class AeInstrumentCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun capture(name: String, inst: AeInstrument, preset: Int = 0) {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        initAetherInstruments(context)
        val manager = AeManager(SilentHost(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        manager.load(0, inst)
        if (preset != 0) manager.loadPreset(0, preset)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.size(1000.dp, 460.dp).background(Color(0xFF07090F))) {
                AeInstrumentWindow(manager = manager, strip = 0, onClose = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    @Test fun ae_leads_window() = capture("ae_leads_window", AeInstrument.LEADS)

    @Test fun ae_sax_window() = capture("ae_sax_window", AeInstrument.SAX)

    @Test fun ae_organ_window() = capture("ae_organ_window", AeInstrument.ORGAN)

    @Test fun ae_synth_window() = capture("ae_synth_window", AeInstrument.SYNTH)
}
