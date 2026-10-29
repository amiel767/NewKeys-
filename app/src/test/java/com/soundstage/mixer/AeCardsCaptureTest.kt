package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.instruments.AeHost
import com.soundstage.mixer.instruments.AeInstrumentsTabContent
import com.soundstage.mixer.instruments.AeInstrument
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

private class SilentHostCards : AeHost {
    override fun loadInstrument(strip: Int, fxId: Int) = true
    override fun unloadInstrument(strip: Int) = Unit
    override fun setParam(strip: Int, index: Int, value: Float) = Unit
    override fun noteOn(strip: Int, note: Int, velocity: Int) = Unit
    override fun noteOff(strip: Int, note: Int) = Unit
    override fun allNotesOff(strip: Int) = Unit
    override fun level(strip: Int) = 0f
}

/**
 * Captures the four Æther instrument cards, so the power toggle at the card's top-right can be
 * checked in both states: everything off, then one card powered (green).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w900dp-h420dp-440dpi", sdk = [36])
class AeCardsCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun capture(name: String, load: AeInstrument?) {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        initAetherInstruments(context)
        val manager = AeManager(SilentHostCards(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        load?.let { manager.load(0, it) }
        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0E131C)).padding(16.dp)) {
                AeInstrumentsTabContent(manager = manager, strip = 0, onOpen = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    @Test fun ae_cards_all_off() = capture("ae_cards_all_off", null)

    @Test fun ae_cards_synth_on() = capture("ae_cards_synth_on", AeInstrument.SYNTH)
}
