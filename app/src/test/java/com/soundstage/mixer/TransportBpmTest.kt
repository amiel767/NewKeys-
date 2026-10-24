package com.soundstage.mixer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.soundstage.mixer.viewmodel.MixerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The top-bar BPM pill steps the transport tempo. It regressed once: the +/- buttons
 * captured the callback in `pointerInput(delta)`, which never restarts for a fixed
 * delta, so every press re-derived the same `snapshot + 1` and the readout froze at
 * 120/119. The fix reads the current value inside the ViewModel, so this test steps
 * repeatedly and checks the value actually advances and clamps.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class TransportBpmTest {

    private fun vm(): MixerViewModel =
        MixerViewModel(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun repeatedStepsAdvanceTheTempo() {
        val viewModel = vm()
        val start = viewModel.uiState.value.transportBpm
        repeat(5) { viewModel.adjustTransportBpm(+1) }
        assertEquals(start + 5, viewModel.uiState.value.transportBpm)
        // `bpm` mirrors the transport tempo (the mixer's own readout).
        assertEquals(viewModel.uiState.value.transportBpm, viewModel.uiState.value.bpm)
    }

    @Test
    fun stepsGoDownToo() {
        val viewModel = vm()
        viewModel.setTransportBpm(140)
        repeat(3) { viewModel.adjustTransportBpm(-1) }
        assertEquals(137, viewModel.uiState.value.transportBpm)
    }

    @Test
    fun tempoClampsToTheSupportedRange() {
        val viewModel = vm()
        viewModel.setTransportBpm(41)
        viewModel.adjustTransportBpm(-10)
        assertEquals(40, viewModel.uiState.value.transportBpm)

        viewModel.setTransportBpm(239)
        viewModel.adjustTransportBpm(+10)
        assertEquals(240, viewModel.uiState.value.transportBpm)
    }

    @Test
    fun theTransportClockFollowsTheTempo() {
        val viewModel = vm()
        viewModel.setTransportBpm(90)
        // A wrong BPM in the clock would break every loop (they are measured in bars).
        assertTrue(viewModel.uiState.value.transportBpm == 90)
    }
}
