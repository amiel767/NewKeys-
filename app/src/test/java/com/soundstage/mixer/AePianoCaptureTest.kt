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

    private fun capture(name: String, preset: Int, cards: Boolean = false, edit: Boolean = false) {
        val context = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
        initAetherInstruments(context)
        val manager = AeManager(PianoSilentHost(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        manager.load(0, AeInstrument.PIANO)
        if (preset > 0) manager.loadPreset(0, preset)
        // `edit` moves one knob off the preset so the save button appears: without a real
        // divergence the capture would show the header the user sees at rest, which is
        // exactly the case the button has to stay out of.
        if (edit) manager.setParam(0, 0, (manager.sessions[0]?.values?.get(0) ?: 0.5f) + 0.3f)
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

    // Un fichier par modele, dans l'ordre de AePianoModels.all : c'est ce que la page
    // de demo affiche a cote de chaque MP3.
    @Test fun ae_model_00_felt() = capture("ae_model_00_felt", 0)
    @Test fun ae_model_01_studio() = capture("ae_model_01_studio", 1)
    @Test fun ae_model_02_bright() = capture("ae_model_02_bright", 2)
    @Test fun ae_model_03_mellow() = capture("ae_model_03_mellow", 3)
    @Test fun ae_model_04_dark() = capture("ae_model_04_dark", 4)
    @Test fun ae_model_05_cottage() = capture("ae_model_05_cottage", 5)
    @Test fun ae_model_06_parlor() = capture("ae_model_06_parlor", 6)
    @Test fun ae_model_07_saloon() = capture("ae_model_07_saloon", 7)
    @Test fun ae_model_08_tine() = capture("ae_model_08_tine", 8)
    @Test fun ae_model_09_ballad() = capture("ae_model_09_ballad", 9)
    @Test fun ae_model_10_dx() = capture("ae_model_10_dx", 10)
    @Test fun ae_model_11_epiano() = capture("ae_model_11_epiano", 11)

    @Test fun ae_piano_window() = capture("ae_piano_window", 0)
    @Test fun ae_piano_felt() = capture("ae_piano_felt", 1)
    @Test fun ae_piano_bright() = capture("ae_piano_bright", 3)
    @Test fun ae_piano_electric() = capture("ae_piano_electric", 9)
    @Test fun ae_piano_cards() = capture("ae_piano_cards", 0, cards = true)
    @Test fun ae_piano_modified() = capture("ae_piano_modified", 0, edit = true)
}
