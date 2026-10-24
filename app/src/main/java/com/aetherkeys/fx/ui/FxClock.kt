package com.aetherkeys.fx.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

/**
 * One time source for the FX window, in seconds since the first frame.
 * It measures real frame deltas (vsync), so a slow frame never speeds up or freezes the motion.
 * [fullRate] = open panel, capped at [fullFps]. Otherwise rack cards are refreshed at [miniFps].
 * Changing a preset does not restart or recreate this clock.
 *
 * The cap matters on 90/120 Hz panels: the scene is a full 825x343 redraw (gradients,
 * shadows, knobs), and repainting it at every vsync for no visible gain is what made
 * the window hot. 30 im/s reads as smooth for a knob or a slow particle field while
 * costing half to a quarter of the frames. Elapsed time comes from the real deltas, so
 * the motion keeps its true speed whatever the cap is.
 */
@Composable
fun rememberFxClock(fullRate: Boolean, miniFps: Int = 15, fullFps: Int = 30): State<Float> =
    produceState(initialValue = 0f, fullRate, miniFps, fullFps) {
        var last = -1L
        var elapsed = 0f
        while (true) {
            val nowNs = withFrameNanos { it }
            if (last >= 0L) elapsed += (nowNs - last) / 1_000_000_000f
            last = nowNs
            value = elapsed
            delay(1000L / (if (fullRate) fullFps else miniFps).coerceIn(5, 60))
        }
    }

/** Reduced motion and card rate come from settings state, never hard-coded in a scene. */
data class FxAnimPrefs(val reducedMotion: Boolean = false, val miniFps: Int = 15)
