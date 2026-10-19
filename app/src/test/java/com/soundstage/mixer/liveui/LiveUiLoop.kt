package com.soundstage.mixer.liveui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.model.ActivePopup
import com.soundstage.mixer.model.AppScreenPage
import com.soundstage.mixer.ui.MixerScreen
import com.soundstage.mixer.ui.theme.DarkBg
import com.soundstage.mixer.ui.theme.SoundfontLiveMixerTheme
import com.soundstage.mixer.viewmodel.MixerViewModel
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * LiveUiLoop — renders the real Compose UI and lets an external process drive it.
 *
 * Reads [dir]/cmd.json, applies navigation/clicks, re-renders to [dir]/screen.png
 * and beats [dir]/status.txt. No emulator, no audio: Robolectric draws the real
 * composables off-screen. The loop never returns on purpose; kill the process to stop.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w891dp-h411dp-land-xxhdpi", sdk = [36])
class LiveUiLoop {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun run() {
        val dir = File(System.getenv("LIVEUI_DIR") ?: "/tmp/liveui").also { it.mkdirs() }
        val cmdFile = File(dir, "cmd.json")
        val imgFile = File(dir, "screen.png")
        val statusFile = File(dir, "status.txt")

        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MixerViewModel(app)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            SoundfontLiveMixerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    MixerScreen(viewModel = viewModel, skipSplash = true)
                }
            }
        }

        // Capture into a temp file then rename, so readers never see a half-written PNG.
        val tmpFile = File(dir, "screen.tmp.png")
        var lastSeq = -1L
        statusFile.writeText("ready")
        while (true) {
            try {
                val raw = if (cmdFile.exists()) cmdFile.readText() else ""
                var paused = false
                if (raw.isNotBlank()) {
                    val o = JSONObject(raw)
                    val seq = o.optLong("seq", 0L)
                    if (seq != lastSeq) {
                        lastSeq = seq
                        o.optString("page").takeIf { it.isNotBlank() }?.let { p ->
                            runCatching { viewModel.navigateToPage(AppScreenPage.valueOf(p)) }
                        }
                        o.optString("popup").takeIf { it.isNotBlank() }?.let { p ->
                            runCatching {
                                val popup = ActivePopup.valueOf(p)
                                if (popup == ActivePopup.NONE) viewModel.closePopup() else viewModel.openPopup(popup)
                            }
                        }
                        o.optJSONArray("clicks")?.let { arr ->
                            for (i in 0 until arr.length()) {
                                val pt = arr.getJSONArray(i)
                                val x = pt.getDouble(0).toFloat()
                                val y = pt.getDouble(1).toFloat()
                                runCatching {
                                    composeTestRule.onRoot().performTouchInput { click(Offset(x, y)) }
                                }
                            }
                        }
                        // Grow the mixer to N visible tracks (JSX reference draws 4).
                        o.optInt("tracks", 0).takeIf { it > 0 }?.let { n ->
                            runCatching {
                                while (viewModel.uiState.value.visibleTracksCount < n) {
                                    viewModel.addSoundTrack()
                                }
                            }
                        }
                        o.optBoolean("sustain", false).let { on ->
                            runCatching { if (viewModel.uiState.value.isSustainActive != on) viewModel.toggleSustain() }
                        }
                    }
                    paused = o.optBoolean("pause", false)
                }
                composeTestRule.mainClock.advanceTimeBy(16)
                if (!paused) {
                    composeTestRule.onRoot().captureRoboImage(tmpFile.absolutePath)
                    tmpFile.renameTo(imgFile)
                    statusFile.writeText("ok ${System.currentTimeMillis()}")
                }
            } catch (t: Throwable) {
                runCatching { statusFile.writeText("err ${t.javaClass.simpleName}: ${t.message}") }
            }
            Thread.sleep(150)
        }
    }
}
