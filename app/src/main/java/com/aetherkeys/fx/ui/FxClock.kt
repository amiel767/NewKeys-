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
 * [fullRate] = open panel (every display frame). Otherwise rack cards are refreshed at [miniFps].
 * Changing a preset does not restart or recreate this clock.
 */
@Composable
fun rememberFxClock(fullRate: Boolean, miniFps: Int = 15): State<Float> =
    produceState(initialValue = 0f, fullRate, miniFps) {
        var last = -1L
        var elapsed = 0f
        while (true) {
            val nowNs = withFrameNanos { it }
            if (last >= 0L) elapsed += (nowNs - last) / 1_000_000_000f
            last = nowNs
            value = elapsed
            if (!fullRate) delay(1000L / miniFps.coerceIn(5, 60))
        }
    }

/** Reduced motion and card rate come from settings state, never hard-coded in a scene. */
data class FxAnimPrefs(val reducedMotion: Boolean = false, val miniFps: Int = 15)
