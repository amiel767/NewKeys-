package com.soundstage.mixer

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.ui.MixerScreen
import com.soundstage.mixer.ui.theme.DarkBg
import com.soundstage.mixer.ui.theme.SoundfontLiveMixerTheme
import com.soundstage.mixer.viewmodel.MixerViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])
class KeyboardCollapsedCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun mixer_keyboard_visible_lines_collapsed_capture() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MixerViewModel(app)
        // Keyboard deployed but the layer lines stay folded: do NOT call
        // toggleKeyboardLayer(), so isKeyboardLayerExpanded stays false.
        composeTestRule.setContent {
            SoundfontLiveMixerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    MixerScreen(viewModel = viewModel, skipSplash = true)
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        Thread.sleep(1_200) // let the async state restore finish before forcing the page
        composeTestRule.mainClock.advanceTimeBy(500)
        // The ViewModel restores the saved page asynchronously and forces SETLISTS,
        // so navigation must be redone after that restore, not only before.
        composeTestRule.runOnIdle {
            viewModel.navigateToPage(com.soundstage.mixer.model.AppScreenPage.MIXER)
            viewModel.cycleKeyboardExpansion()
        }
        composeTestRule.mainClock.advanceTimeBy(2_500)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/mixer_keyboard_collapsed.png")
    }
}
