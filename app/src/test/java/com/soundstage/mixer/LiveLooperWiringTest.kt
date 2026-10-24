package com.soundstage.mixer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.soundstage.mixer.looper.RecordSource
import com.soundstage.mixer.viewmodel.MixerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "LiveLooper is not really wired": the page's buttons must reach the controller and the
 * controller's state must mirror back into the ViewModel's UI state. This exercises that
 * exact chain through the public API the page uses — `liveLooperActions()` — instead of
 * the controller directly, so a broken binding between the two is caught.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class LiveLooperWiringTest {

    private fun vm(): MixerViewModel =
        MixerViewModel(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun caseActionsReachTheControllerAndMirrorBack() {
        val viewModel = vm()
        val actions = viewModel.liveLooperActions()

        // The record-source chips are the simplest mutation to observe: the page's
        // action must change the case, and the case must come back in the UI state.
        assertTrue(viewModel.uiState.value.liveLooperState.cases[0].sources.pads)
        actions.onToggleSource(0, RecordSource.Pads)
        assertFalse(
            "toggling Pads off through the page action must be visible in the UI state",
            viewModel.uiState.value.liveLooperState.cases[0].sources.pads
        )
        assertTrue(viewModel.uiState.value.liveLooperState.cases[0].sources.keys)
    }

    @Test
    fun volumeReachesTheController() {
        val viewModel = vm()
        val actions = viewModel.liveLooperActions()
        actions.onVolume(1, 42)
        assertEquals(42, viewModel.uiState.value.liveLooperState.cases[1].volume)
    }

    @Test
    fun muteIsIgnoredOnAnEmptyCase() {
        // Reference rule (LoopsPreview.jsx `mutePress`): `if (c.layers === 0) return`.
        // Muting an empty case is meaningless, so the action must be a no-op — not a
        // silent toggle that would show MUTE on a case with nothing in it.
        val viewModel = vm()
        val actions = viewModel.liveLooperActions()
        actions.onToggleMute(1)
        assertFalse(viewModel.uiState.value.liveLooperState.cases[1].mute)
    }

    @Test
    fun openingTheSheetReachesTheController() {
        val viewModel = vm()
        val actions = viewModel.liveLooperActions()
        actions.onSheet(3)
        assertEquals(3, viewModel.uiState.value.liveLooperState.sheetCaseId)
        actions.onSheet(null)
        assertEquals(null, viewModel.uiState.value.liveLooperState.sheetCaseId)
    }

    @Test
    fun renamingACaseReachesTheController() {
        val viewModel = vm()
        val actions = viewModel.liveLooperActions()
        actions.onRename(2, "Verse")
        assertEquals("Verse", viewModel.uiState.value.liveLooperState.cases[2].name)
    }
}
