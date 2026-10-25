package com.soundstage.mixer

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.model.AppScreenPage
import com.soundstage.mixer.ui.pages.DrumPadScreen
import com.soundstage.mixer.ui.theme.DarkBg
import com.soundstage.mixer.ui.theme.SoundfontLiveMixerTheme
import com.soundstage.mixer.viewmodel.MixerViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real LiveLooper page and drives the controller actions (arm record,
 * play, open the patch window) so the whole new-LiveLooper path runs, then captures
 * the result. Any exception thrown while rendering or reacting to an action fails
 * the test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])
class LiveLooperPageCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun live_looper_page_renders_and_actions_run() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MixerViewModel(app)
        composeTestRule.setContent {
            SoundfontLiveMixerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    val uiState by viewModel.uiState.collectAsState()
                    DrumPadScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        startOnLiveLooper = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.runOnIdle { viewModel.navigateToPage(AppScreenPage.DRUMPAD) }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/live_looper_page.png")

        val actions = viewModel.liveLooperActions()
        composeTestRule.runOnIdle {
            actions.onRec(0)      // arm record on L1
            actions.onPlay(1)     // play an empty case (toast path)
            actions.onFocus(2)    // open the focused view
            actions.onSheet(3)    // open the case settings sheet
        }
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/live_looper_page_actions.png")
    }
}
