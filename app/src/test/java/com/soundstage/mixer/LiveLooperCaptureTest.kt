package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.looper.DrumPadPage
import com.soundstage.mixer.looper.LiveLooperContent
import com.soundstage.mixer.looper.LiveLooperOverlays
import com.soundstage.mixer.looper.LoopCase
import com.soundstage.mixer.looper.LoopPatch
import com.soundstage.mixer.looper.LoopPatchChip
import com.soundstage.mixer.looper.LooperController
import com.soundstage.mixer.looper.LooperPageSelector
import com.soundstage.mixer.looper.LoopsSummaryCard
import kotlinx.coroutines.delay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures the LiveLooper page on its reference canvas (891 x 411, the JSX preview size),
 * using the real LooperController so the state machine is exercised, not faked.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w891dp-h411dp-440dpi", sdk = [36])
class LiveLooperCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent { content() }
        composeTestRule.mainClock.advanceTimeBy(1_200)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    @Test
    fun live_looper_patch() = capture("live_looper_patch") {
        val patches = listOf(
            LoopPatch("p1", "Verse groove", List(8) { LoopCase(it) }),
            LoopPatch("p2", "Chorus stack", List(8) { LoopCase(it) })
        )
        val ctrl = remember {
            LooperController(clock = { 0.35 }, setlistName = "Sunday Show", initialActiveId = "p1", initialPatches = patches)
        }
        val state by ctrl.state.collectAsState()
        val actions = remember { ctrl.actions() }
        // The patch window is a store-owned flag: drive it through the controller's actions.
        LaunchedEffect(Unit) { actions.onOpenPatches() }
        val now = remember { 0.35 }
        Box(Modifier.fillMaxSize().background(Color(0xFF0F141D))) {
            LiveLooperContent(state, now, actions, Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 60.dp, bottom = 8.dp))
            LiveLooperOverlays(state, actions, Modifier.fillMaxSize())
        }
    }

    @Test
    fun live_looper_page() = capture("live_looper_page") {
        val t0 = remember { System.nanoTime() }
        val barMs = 4 * 60000.0 / 119.0
        val clock: () -> Double = { (System.nanoTime() - t0) / 1e6 / barMs }
        val ctrl = remember {
            LooperController(
                clock = clock,
                setlistName = "Sunday Show",
                initialActiveId = "p1",
                initialPatches = listOf(
                    LoopPatch("p1", "Verse groove", List(8) { LoopCase(it) }),
                    LoopPatch("p2", "Chorus stack", List(8) { LoopCase(it) })
                )
            )
        }
        LaunchedEffect(Unit) {
            // A couple of seconds of transport so the rings show a position.
            repeat(150) { ctrl.tick(); delay(8) }
        }
        val state by ctrl.state.collectAsState()
        val actions = remember { ctrl.actions() }
        val now = remember { 0.35 }
        Box(Modifier.fillMaxSize().background(Color(0xFF0F141D))) {
            Row(
                Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.width(168.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LooperPageSelector(DrumPadPage.LiveLooper, {})
                        LoopPatchChip(state, actions)
                    }
                    Spacer(Modifier.weight(1f))
                    LoopsSummaryCard(state, Modifier.fillMaxWidth().height(56.dp))
                }
                LiveLooperContent(state, now, actions, Modifier.weight(1f).fillMaxHeight().padding(top = 54.dp))
            }
            LiveLooperOverlays(state, actions, Modifier.fillMaxSize())
        }
    }
}
