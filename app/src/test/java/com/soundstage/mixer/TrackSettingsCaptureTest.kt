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
class TrackSettingsCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun track_settings_scrim_capture() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MixerViewModel(app)
        composeTestRule.setContent {
            SoundfontLiveMixerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    MixerScreen(viewModel = viewModel, skipSplash = true)
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        Thread.sleep(1_200)
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.runOnIdle {
            viewModel.navigateToPage(com.soundstage.mixer.model.AppScreenPage.MIXER)
            viewModel.openTrackSettings(1)
        }
        composeTestRule.mainClock.advanceTimeBy(2_500)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/track_settings_scrim.png")
    }
}
