package com.soundstage.mixer.ui.settings // ← adapt to the real package

/*
 * Settings sidebar icons as Compose ImageVectors. Copy this file as is.
 * 24 × 24 grid, 2 px stroke, round caps and joins. Black in the file: the real color comes from `tint`.
 *
 * Usage (replace the old drawn circles in the tab list):
 *
 *   Icon(
 *       imageVector = SettingsTabIcons.Interface,   // Audio, Interface, Midi, System
 *       contentDescription = null,
 *       tint = if (selected) Color(0xFF8FB2E8) else Color(0xFF8E99AD),
 *       modifier = Modifier.size(24.dp)
 *   )
 *
 * Needs only compose-ui and compose-material3 (Icon). No material-icons-extended.
 */

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object SettingsTabIcons {

    /** Audio: a symmetric waveform, five bars, the tallest in the middle. */
    val Audio: ImageVector by lazy {
        icon("SettingsAudio") {
            line("M4,10.5v3 M8,7v10 M12,3.5v17 M16,7v10 M20,10.5v3")
        }
    }

    /** Interface: an app window with a header bar and a side panel. */
    val Interface: ImageVector by lazy {
        icon("SettingsInterface") {
            line("M6,4 H18 a3,3 0 0 1 3,3 V17 a3,3 0 0 1 -3,3 H6 a3,3 0 0 1 -3,-3 V7 a3,3 0 0 1 3,-3 Z")
            line("M3,9.5 H21 M9.5,9.5 V20")
        }
    }

    /** MIDI: the 5-pin DIN connector, a ring and five pins on the upper half. */
    val Midi: ImageVector by lazy {
        icon("SettingsMidi") {
            line("M3,12 a9,9 0 1,0 18,0 a9,9 0 1,0 -18,0 Z")
            dots(
                "M5.9,13 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0 Z " +
                    "M7.3,9 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0 Z " +
                    "M11.1,7.6 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0 Z " +
                    "M14.9,9 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0 Z " +
                    "M16.3,13 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0 Z"
            )
        }
    }

    /** System: a chip with pins on the four sides. */
    val System: ImageVector by lazy {
        icon("SettingsSystem") {
            line("M7.5,5 H16.5 a2.5,2.5 0 0 1 2.5,2.5 V16.5 a2.5,2.5 0 0 1 -2.5,2.5 H7.5 a2.5,2.5 0 0 1 -2.5,-2.5 V7.5 a2.5,2.5 0 0 1 2.5,-2.5 Z")
            line("M10.5,9.5 H13.5 a1,1 0 0 1 1,1 V13.5 a1,1 0 0 1 -1,1 H10.5 a1,1 0 0 1 -1,-1 V10.5 a1,1 0 0 1 1,-1 Z")
            line("M9.5,2 v3 M14.5,2 v3 M9.5,19 v3 M14.5,19 v3 M2,9.5 h3 M2,14.5 h3 M19,9.5 h3 M19,14.5 h3")
        }
    }
}

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply(block).build()

private fun ImageVector.Builder.line(d: String) = addPath(
    pathData = addPathNodes(d),
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round
)

private fun ImageVector.Builder.dots(d: String) = addPath(
    pathData = addPathNodes(d),
    fill = SolidColor(Color.Black)
)
