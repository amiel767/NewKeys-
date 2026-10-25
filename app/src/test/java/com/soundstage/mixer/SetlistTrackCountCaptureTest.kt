package com.soundstage.mixer

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.model.AppScreenPage
import com.soundstage.mixer.ui.MixerScreen
import com.soundstage.mixer.ui.setlists.CreateMode
import com.soundstage.mixer.ui.theme.DarkBg
import com.soundstage.mixer.ui.theme.SoundfontLiveMixerTheme
import com.soundstage.mixer.viewmodel.MixerViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Visual proof of the "new Setlist opens on eight faders" fix: create a setlist the
 * way the Setlists screen does, open it, and capture the mixer. `MixerScreen` renders
 * `tracks.take(visibleTracksCount)`, so the strip count in the image *is* the count
 * the user reported. A second capture adds tracks back, proving the "+" still works.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])
class SetlistTrackCountCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun idleUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return condition()
    }

    @Test
    fun aFreshSetlistRendersOneFader() {
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
        // The ViewModel restores its saved state asynchronously and re-asserts the saved
        // page, so the setlist must be created *after* that restore lands — creating it
        // before setContent just gets overwritten.
        composeTestRule.mainClock.advanceTimeBy(1_500)
        Thread.sleep(1_200)

        val name = "OneTrack${System.currentTimeMillis()}"
        composeTestRule.runOnIdle {
            viewModel.createSetlistFromScreen(name, CreateMode.Blank)
        }
        composeTestRule.runOnIdle { viewModel.openProject(name) }
        // Write and load both bounce through Dispatchers.IO -> Dispatchers.Main, and the
        // test main looper is paused, so idle it until the load actually lands.
        idleUntil(10_000) { viewModel.uiState.value.activeSceneId == name }

        composeTestRule.runOnIdle { viewModel.navigateToPage(AppScreenPage.MIXER) }
        composeTestRule.mainClock.advanceTimeBy(1_500)

        assertEquals(
            "the mixer renders tracks.take(visibleTracksCount), so this is what is drawn",
            1,
            viewModel.uiState.value.visibleTracksCount
        )
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/setlist_1_track.png")

        // The "+" affordance must still grow the mixer back to the full eight.
        composeTestRule.runOnIdle { repeat(7) { viewModel.addSoundTrack() } }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        assertEquals(8, viewModel.uiState.value.visibleTracksCount)
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/setlist_8_tracks.png")
    }
}
