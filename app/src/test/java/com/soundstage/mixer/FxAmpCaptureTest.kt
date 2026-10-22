package com.soundstage.mixer

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.fx.FxController
import com.soundstage.mixer.fx.FxRegistry
import com.soundstage.mixer.ui.fx.FxWindow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])
class FxAmpCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun fx_amp_capture() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val controller = FxController(0, app)
        // Effect 10 defaults, as setEffect would install them once the native
        // bridge is live; Robolectric has no native library.
        FxRegistry.info(10).params.forEachIndexed { i, p -> controller.params[0][i] = p.default }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0F1120)), contentAlignment = Alignment.TopStart) {
                FxWindow(
                    effectId = 10,
                    slot = 0,
                    controller = controller,
                    level = 0.8f,
                    onClose = {},
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_200)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/fx_amp.png")
    }
}
