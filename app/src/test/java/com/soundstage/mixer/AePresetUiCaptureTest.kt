package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.instruments.AeHost
import com.soundstage.mixer.instruments.AeInstrument
import com.soundstage.mixer.instruments.AeInstrumentWindow
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

private class QuietHost : AeHost {
    override fun loadInstrument(strip: Int, fxId: Int) = true
    override fun unloadInstrument(strip: Int) = Unit
    override fun setParam(strip: Int, index: Int, value: Float) = Unit
    override fun noteOn(strip: Int, note: Int, velocity: Int) = Unit
    override fun noteOff(strip: Int, note: Int) = Unit
    override fun allNotesOff(strip: Int) = Unit
    override fun level(strip: Int) = 0f
}

/**
 * The two states that the preset feature added: the grouped list (with a User entry that
 * carries a Delete button) and the naming sheet. Both are reached by touch, exactly as a
 * user reaches them, so the capture cannot show a state the app cannot produce.
 *
 * The window runs an infinite animation loop, so the clock is frozen and advanced by hand;
 * input is injected and then a frame is advanced for the state to land.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h460dp-440dpi", sdk = [36])
class AePresetUiCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun open(inst: AeInstrument): Pair<android.content.Context, AeManager> {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        initAetherInstruments(context)
        val manager = AeManager(QuietHost(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        manager.load(0, inst)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.size(1000.dp, 460.dp).background(Color(0xFF07090F))) {
                AeInstrumentWindow(manager = manager, strip = 0, onClose = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        return context to manager
    }

    private fun clickStage(x: Float, y: Float, density: Float) {
        composeTestRule.onRoot().performTouchInput { click(Offset(x * density, y * density)) }
        composeTestRule.mainClock.advanceTimeBy(400)
    }

    /** The list open on a fresh save: a "User" group header and a Delete on the entry. */
    @Test
    fun ae_leads_preset_list() {
        val (context, manager) = open(AeInstrument.LEADS)
        manager.saveUserPreset(0, "My Lead")
        composeTestRule.mainClock.advanceTimeBy(200)
        val density = context.resources.displayMetrics.density
        clickStage(753f, 23f, density)   // the preset chip in the header (now at x 640..866)
        composeTestRule.mainClock.advanceTimeBy(600)
        // The User group sits at the very bottom of a 32-preset list, so scroll to it:
        // a capture of the top would only show the factory groups and prove nothing
        // about the feature being captured.
        repeat(12) {
            composeTestRule.onNode(hasTestTag("aePresetList")).performTouchInput { swipeUp() }
            composeTestRule.mainClock.advanceTimeBy(60)
        }
        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/ae_preset_list.png")
    }

    /**
     * The Intensity slider, on the leads "PLAY" page. Clicking the tab is the only way to
     * reach it, so the capture proves the control is actually placed where the request
     * asked (the empty band under the mode chips) and not merely declared.
     */
    @Test
    fun ae_leads_intensity_slider() {
        val (context, _) = open(AeInstrument.LEADS)
        val density = context.resources.displayMetrics.density
        clickStage(500f, 23f, density)   // the "PLAY" tab (4th of 4 in the shared tab row, x 264..532)
        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/ae_leads_intensity.png")
    }

    /** The naming sheet, reached by the floppy to the left of the chip. */
    @Test
    fun ae_preset_naming_sheet() {
        val (context, _) = open(AeInstrument.LEADS)
        val density = context.resources.displayMetrics.density
        clickStage(567f, 23f, density)   // the save-to-disk button (now at x 552..582)
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.onNode(isDialog()).captureRoboImage(filePath = "src/test/screenshots/ae_preset_naming.png")
    }
}
