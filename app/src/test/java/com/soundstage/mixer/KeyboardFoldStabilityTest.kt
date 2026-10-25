package com.soundstage.mixer

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.soundstage.mixer.model.AppScreenPage
import com.soundstage.mixer.ui.MixerScreen
import com.soundstage.mixer.ui.theme.DarkBg
import com.soundstage.mixer.ui.theme.SoundfontLiveMixerTheme
import com.soundstage.mixer.viewmodel.MixerViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Folding the layer lines back must not move the mixer behind them.
 *
 * Regression guard for the reserved keyboard slot: it used to be latched from the
 * raw `onSizeChanged` height, so the fold animation (the container is still tall
 * mid-fold) re-latched a too-large height, grew the slot and pushed the top bar and
 * bottom bar up for a moment.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])
class KeyboardFoldStabilityTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun bottomBarTop(): Float {
        val bounds = composeTestRule.onNodeWithTag("mixer_bottom_bar").fetchSemanticsNode().boundsInRoot
        return bounds.top
    }

    @Test
    fun foldingTheLayerLinesDoesNotMoveTheMixer() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MixerViewModel(app)
        viewModel.navigateToPage(AppScreenPage.MIXER)
        composeTestRule.setContent {
            SoundfontLiveMixerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    MixerScreen(viewModel = viewModel, skipSplash = true)
                }
            }
        }
        // Let the async restore settle, then force the mixer page.
        composeTestRule.mainClock.advanceTimeBy(1_500)
        Thread.sleep(1_200)
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.runOnIdle {
            viewModel.navigateToPage(AppScreenPage.MIXER)
            viewModel.cycleKeyboardExpansion() // keyboard up, layers folded
        }
        composeTestRule.mainClock.advanceTimeBy(2_500)

        val topWithKeyboardFolded = bottomBarTop()

        // Open the layer lines, then fold them back: the scenario the user reported.
        composeTestRule.runOnIdle { viewModel.toggleKeyboardLayer() }
        composeTestRule.mainClock.advanceTimeBy(2_500)

        // Fold back and sample the whole animation: the bug was a transient push.
        composeTestRule.runOnIdle { viewModel.toggleKeyboardLayer() }
        var worstRise = 0f
        repeat(25) {
            composeTestRule.mainClock.advanceTimeBy(100)
            worstRise = maxOf(worstRise, topWithKeyboardFolded - bottomBarTop())
        }

        assertEquals(
            "the mixer must not move when the layer lines fold back",
            0f, worstRise, 0.5f
        )
    }
}
