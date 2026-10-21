package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.ui.components.KeyboardPressStyle
import com.soundstage.mixer.ui.components.KeyboardThemes
import com.soundstage.mixer.ui.components.VirtualKeyboardActions
import com.soundstage.mixer.ui.components.VirtualKeyboardContainer
import com.soundstage.mixer.ui.components.KeyboardLine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])
class VirtualKeyboardCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val lines = listOf(
        KeyboardLine("0", "S90 Full Grand", Color(0xFF8FB2E8), 36, 84),
        KeyboardLine("1", "Motif XF Galaxy DX", Color(0xFFD4B06A), 40, 88)
    )

    @Test
    fun virtual_keyboard_capture() {
        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0F17))) {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                    VirtualKeyboardContainer(
                        lines = lines,
                        expanded = true,
                        pressedNotes = setOf(48, 52, 55),
                        theme = KeyboardThemes.byId("classic"),
                        pressStyle = KeyboardPressStyle.Color,
                        actions = VirtualKeyboardActions.NoOp,
                        modifier = Modifier.height(150.dp)
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/virtual_keyboard.png")
    }
}
