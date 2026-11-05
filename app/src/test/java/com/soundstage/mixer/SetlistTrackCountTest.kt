package com.soundstage.mixer

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.soundstage.mixer.ui.setlists.CreateMode
import com.soundstage.mixer.viewmodel.MixerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "A new Setlist opens on eight faders instead of none."
 *
 * The setlist file is written by `createSetlistFromScreen` and read back by `selectScene`.
 * The writer used to omit `visibleTracksCount`, so the reader fell back to
 * `tracks.count { it.isEnabled }` — which is 8, because every track ships enabled. A fresh
 * setlist now opens on no strip at all: only the "+" that creates the first one and asks
 * for its sound source. This test walks the real create -> open round trip and asserts the
 * count that lands in the UI state, so the fallback cannot silently come back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class SetlistTrackCountTest {

    private fun vm(): MixerViewModel =
        MixerViewModel(ApplicationProvider.getApplicationContext<Application>())

    /**
     * Both the write and the read hop through `Dispatchers.IO` and back to
     * `Dispatchers.Main`, and Robolectric's main looper is paused. Without idling it the
     * final state assignment never runs and the assertion would read the startup default,
     * passing no matter what the loader did.
     */
    private fun awaitMain(seconds: Double = 8.0, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + (seconds * 1000).toLong()
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return true
            Thread.sleep(50)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return condition()
    }

    @Test
    fun aBrandNewSetlistOpensOnNoTrack() {
        val viewModel = vm()
        val name = "CountCheck${System.currentTimeMillis()}"

        viewModel.createSetlistFromScreen(name, CreateMode.Blank)
        assertTrue(
            "the setlist file must be written before it is opened",
            awaitMain { viewModel.uiState.value.scenes.any { it.name == name } }
        )

        viewModel.openProject(name)
        assertTrue(
            "the setlist must actually load, or the count below proves nothing",
            awaitMain { viewModel.uiState.value.activeSceneId == name }
        )

        assertEquals(
            "a fresh setlist must open on no track, not on every enabled track",
            0,
            viewModel.uiState.value.visibleTracksCount
        )
    }
}
