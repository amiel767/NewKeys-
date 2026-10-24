package com.soundstage.mixer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.soundstage.mixer.ui.components.TrackColorMode
import com.soundstage.mixer.ui.components.TrackPalettes
import com.soundstage.mixer.viewmodel.MixerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wiring for Settings > Interface > Track colors: the page open/close flags, the
 * setters, the random assignment and its persistence. The palette/mode *logic* is
 * covered by TrackColorsLogicTest; this test covers the ViewModel plumbing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class TrackColorsWiringTest {

    private fun vm(): MixerViewModel =
        MixerViewModel(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun openAndCloseThePage() {
        val viewModel = vm()
        assertFalse(viewModel.uiState.value.trackColorsOpen)
        viewModel.openTrackColorsPage()
        assertTrue(viewModel.uiState.value.trackColorsOpen)
        viewModel.closeTrackColorsPage()
        assertFalse(viewModel.uiState.value.trackColorsOpen)
    }

    @Test
    fun modeAndPaletteSettersAreMirrored() {
        val viewModel = vm()
        viewModel.setTrackColorMode(TrackColorMode.Single)
        assertEquals(TrackColorMode.Single, viewModel.uiState.value.trackColorMode)
        viewModel.setTrackColorPalette(TrackPalettes.Ocean.id)
        assertEquals(TrackPalettes.Ocean.id, viewModel.uiState.value.trackColorPaletteId)
    }

    @Test
    fun randomizeFillsEightLanes() {
        val viewModel = vm()
        viewModel.setTrackColorMode(TrackColorMode.Random)
        viewModel.randomizeTrackColors()
        val lanes = viewModel.uiState.value.trackRandomColorArgb
        assertEquals(8, lanes.size)
        assertEquals(8, lanes.toSet().size)
    }

    @Test
    fun randomizeTwiceUsuallyDiffers() {
        val viewModel = vm()
        viewModel.randomizeTrackColors()
        val first = viewModel.uiState.value.trackRandomColorArgb
        // 8 draws from 8 colours: the same permutation twice is a 1-in-40320 event.
        repeat(6) {
            viewModel.randomizeTrackColors()
            if (viewModel.uiState.value.trackRandomColorArgb != first) return
        }
        assertNotEquals(first, viewModel.uiState.value.trackRandomColorArgb)
    }

    @Test
    fun singleColorIsMirrored() {
        val viewModel = vm()
        viewModel.setTrackSingleColor(0xFFEC4899.toInt())
        assertEquals(0xFFEC4899.toInt(), viewModel.uiState.value.trackSingleColorArgb)
    }

    @Test
    fun dynamicToggleIsMirrored() {
        val viewModel = vm()
        viewModel.setTrackColorDynamic(true)
        assertTrue(viewModel.uiState.value.trackColorDynamic)
        viewModel.setTrackColorDynamic(false)
        assertFalse(viewModel.uiState.value.trackColorDynamic)
    }

    @Test
    fun settingsSurviveARestore() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val first = MixerViewModel(app)
        // Restore runs asynchronously; wait for it before writing, or it can land
        // after the setters and revert them.
        awaitRestore()
        // Order matters: picking a palette selects the Palette mode, so the Random
        // mode must be set after it (that is what the UI does too).
        first.setTrackColorPalette(TrackPalettes.Sunset.id)
        first.setTrackColorMode(TrackColorMode.Random)
        first.setTrackColorDynamic(true)
        first.randomizeTrackColors()
        val lanes = first.uiState.value.trackRandomColorArgb

        // A fresh ViewModel over the same prefs must restore the same scheme.
        val second = MixerViewModel(app)
        awaitRestore()
        val restored = second.uiState.value
        assertEquals(TrackColorMode.Random, restored.trackColorMode)
        assertEquals(TrackPalettes.Sunset.id, restored.trackColorPaletteId)
        assertTrue(restored.trackColorDynamic)
        assertEquals(lanes, restored.trackRandomColorArgb)
    }

    /**
     * The boot restore runs on a background dispatcher and the default page is
     * SETLISTS both before and after it, so there is no state sentinel to poll.
     * Give it a fixed, generous window instead.
     */
    private fun awaitRestore() {
        Thread.sleep(2_000)
    }
}
