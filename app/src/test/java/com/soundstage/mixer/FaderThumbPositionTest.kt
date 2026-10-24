package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.ui.components.CustomVerticalFader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders four faders at distinct values. Before the fix every thumb sat at the
 * bottom of its travel (the animate block assigned the velocity, not the value);
 * this capture is the visual proof they now rest at four different heights.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])
class FaderThumbPositionTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun fader_positions() {
        // Manual clock: the fader animates towards its value, so autoAdvance would
        // capture a mid-animation frame. Settle it explicitly first.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Row(Modifier.fillMaxSize().background(Color(0xFF0B0F17))) {
                listOf(0.05f, 0.35f, 0.6f, 0.95f).forEach { v ->
                    Box(Modifier.width(150.dp).fillMaxHeight().padding(8.dp)) {
                        CustomVerticalFader(
                            value = v,
                            onValueChange = {},
                            auraColor = Color(0xFF19EF71),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(3_000)
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/fader_thumb_positions.png")
    }
}
