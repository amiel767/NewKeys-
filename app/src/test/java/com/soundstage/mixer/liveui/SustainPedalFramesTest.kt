package com.soundstage.mixer.liveui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.ui.components.v2.SustainPedal
import com.soundstage.mixer.ui.theme.SoundfontLiveMixerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real [SustainPedal] composable off-screen and writes one PNG per
 * animation frame, so the spring can be turned into a GIF without a device.
 *
 * Set `PEDAL_FRAMES_DIR` to choose the output directory (default `/tmp/pedal_frames`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w200dp-h200dp-xxhdpi", sdk = [36])
class SustainPedalFramesTest {

    @get:Rule
    val rule = createComposeRule()

    private var frame = 0

    @Test
    fun captureSpringFrames() {
        val dir = File(System.getenv("PEDAL_FRAMES_DIR") ?: "/tmp/pedal_frames")
        dir.listFiles()?.forEach { it.delete() }
        dir.mkdirs()

        var value by mutableStateOf(0)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SoundfontLiveMixerTheme {
                Box(
                    Modifier.fillMaxSize().background(Color(0xFF0B0F16)),
                    contentAlignment = Alignment.Center,
                ) {
                    SustainPedal(value = value, caseSize = 42.dp)
                }
            }
        }

        // 0 -> 127 (press, firmer spring), then 127 -> 0 (release, softer return).
        capture(24, dir) { value }
        value = 127
        capture(30, dir) { value }
        value = 0
        capture(30, dir) { value }
    }

    private fun capture(frames: Int, dir: File, value: () -> Int) {
        repeat(frames) {
            // Compose the frame at the current clock, then step 16 ms like a 60 Hz screen.
            rule.mainClock.advanceTimeByFrame()
            rule.onRoot().captureRoboImage(
                File(dir, "frame_%03d.png".format(frame)).absolutePath
            )
            frame++
        }
    }
}
