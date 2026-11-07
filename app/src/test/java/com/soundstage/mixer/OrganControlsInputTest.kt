package com.soundstage.mixer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.aetherkeys.organ.DrawbarUnit
import com.aetherkeys.organ.OrganHFader
import com.aetherkeys.organ.OrganVFader
import com.aetherkeys.organ.OrganKnob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The controls must answer the finger, and must never hand back a value outside their range: the
 * drawbar is 0..8 steps, the faders and the knob are 0..100. These drive the real gesture handlers.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h600dp-440dpi", sdk = [36])
class OrganControlsInputTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test fun drawbar_takes_the_step_under_the_finger_and_clamps() {
        var last = -1
        composeTestRule.setContent {
            Box(Modifier.size(30.dp, 160.dp)) {
                DrawbarUnit(R.drawable.organ_thumb_brown, 4, 0, { last = it }, Modifier.testTag("c"))
            }
        }
        // The thumb rests at the top for 0 and at the bottom for 8, as on the panel.
        composeTestRule.onNodeWithTag("c").performTouchInput { click(Offset(centerX, 4f)) }
        assertEquals(0, last)
        composeTestRule.onNodeWithTag("c").performTouchInput { click(Offset(centerX, height - 4f)) }
        assertEquals(8, last)
    }

    @Test fun drawbar_drag_reaches_the_bottom_step() {
        var last = -1
        composeTestRule.setContent {
            Box(Modifier.size(30.dp, 160.dp)) {
                DrawbarUnit(R.drawable.organ_thumb_brown, 0, 0, { last = it }, Modifier.testTag("c"))
            }
        }
        composeTestRule.onNodeWithTag("c").performTouchInput { swipeDown() }
        assertEquals(8, last)
    }

    @Test fun vertical_fader_spans_the_full_range_and_stays_bounded() {
        val seen = mutableListOf<Float>()
        composeTestRule.setContent {
            Box(Modifier.size(13.dp, 200.dp)) { OrganVFader(50f, { seen += it }, Modifier.fillMaxSize().testTag("c")) }
        }
        composeTestRule.onNodeWithTag("c").performTouchInput { click(Offset(centerX, 1f)) }
        composeTestRule.onNodeWithTag("c").performTouchInput { click(Offset(centerX, height - 1f)) }
        assertEquals(100f, seen.first(), 0.001f)
        assertEquals(0f, seen.last(), 0.001f)
        assertTrue("the fader must never leave 0..100", seen.all { it in 0f..100f })
    }

    @Test fun horizontal_fader_spans_the_full_range_and_stays_bounded() {
        val seen = mutableListOf<Float>()
        composeTestRule.setContent {
            Box(Modifier.size(200.dp, 13.dp)) { OrganHFader(50f, { seen += it }, Modifier.fillMaxSize().testTag("c")) }
        }
        composeTestRule.onNodeWithTag("c").performTouchInput { click(Offset(1f, centerY)) }
        composeTestRule.onNodeWithTag("c").performTouchInput { click(Offset(width - 1f, centerY)) }
        assertEquals(0f, seen.first(), 0.001f)
        assertEquals(100f, seen.last(), 0.001f)
        assertTrue("the fader must never leave 0..100", seen.all { it in 0f..100f })
    }

    @Test fun knob_drag_raises_the_value_and_clamps_at_the_top() {
        val seen = mutableListOf<Float>()
        composeTestRule.setContent {
            Box(Modifier.size(200.dp)) { OrganKnob(52, 50f, { seen += it }, Modifier.testTag("c")) }
        }
        // The drag runs well past the top of the control: the knob must clamp, not run away.
        composeTestRule.onNodeWithTag("c").performTouchInput {
            swipe(Offset(centerX, height - 2f), Offset(centerX, -300f), durationMillis = 400)
        }
        assertTrue("the drag must have moved the value", seen.isNotEmpty())
        assertTrue("the knob must never leave 0..100", seen.all { it in 0f..100f })
        assertEquals("a long upward drag must stop exactly at the top", 100f, seen.max(), 0.001f)
    }
}
