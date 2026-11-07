package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.aetherkeys.organ.OrganScreen
import com.aetherkeys.organ.OrganState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Écran orgue du zip, rendu tel quel. L'UI se met à l'échelle uniformément depuis 891x411. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h460dp-440dpi", sdk = [36])
class OrganUiCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun shot(name: String, tweak: (OrganState) -> Unit = {}) {
        val state = OrganState()
        tweak(state)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF07090F))) { OrganScreen(state = state) }
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    @Test fun organ_ui() = shot("organ_ui")
}
