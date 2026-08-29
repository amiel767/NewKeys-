package com.example

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.ui.MixerScreen
import com.example.ui.theme.DarkBg
import com.example.ui.theme.SoundfontLiveMixerTheme
import com.example.viewmodel.MixerViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel6, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun mixer_screen_screenshot() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MixerViewModel(app)
    composeTestRule.setContent {
      SoundfontLiveMixerTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
          MixerScreen(viewModel = viewModel)
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/mixer_screen.png")
  }
}
