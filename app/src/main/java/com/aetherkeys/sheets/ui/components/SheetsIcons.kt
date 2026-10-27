package com.aetherkeys.sheets.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icon set for Sheets. Same family as the mixer and settings icons:
 * 24 x 24 grid, 2 px stroke, round caps and joins, black in the file (the real
 * colour comes from `tint`). Solid shapes go through [dot] so they stay filled.
 */
object SheetsIcons {

    /** Chevron pointing left: back to the mixer, back out of a screen. */
    val Back: ImageVector by lazy {
        icon("SheetsBack") { line("M14.5,5 L7.5,12 L14.5,19") }
    }

    /** Arrow curving back on itself: undo. */
    val Undo: ImageVector by lazy {
        icon("SheetsUndo") {
            line("M9,7 H14.5 a4.5,4.5 0 0 1 0,9 H8")
            line("M12,3.5 L8.5,7 L12,10.5")
        }
    }

    /** Mirror of [Undo]: redo. */
    val Redo: ImageVector by lazy {
        icon("SheetsRedo") {
            line("M15,7 H9.5 a4.5,4.5 0 0 0 0,9 H16")
            line("M12,3.5 L15.5,7 L12,10.5")
        }
    }

    /** Filled triangle: open the scene / play the sheet. */
    val Play: ImageVector by lazy {
        icon("SheetsPlay") { dot("M8,5 L19,12 L8,19 Z") }
    }

    /** A pencil across a line: edit. */
    val Edit: ImageVector by lazy {
        icon("SheetsEdit") {
            line("M4,20 L5,15.5 L16.5,4 a2.1,2.1 0 0 1 3,3 L8,18.5 Z")
            line("M4,20 L9,19")
        }
    }

    /** Cogwheel: sheets settings. */
    val Settings: ImageVector by lazy {
        icon("SheetsSettings") {
            line("M12,8.6 a3.4,3.4 0 1,0 0,6.8 a3.4,3.4 0 1,0 0,-6.8 Z")
            line(
                "M12,2.6 v2.4 M12,19 v2.4 M2.6,12 h2.4 M19,12 h2.4 " +
                    "M5.4,5.4 l1.7,1.7 M16.9,16.9 l1.7,1.7 M18.6,5.4 l-1.7,1.7 M7.1,16.9 l-1.7,1.7"
            )
            line("M12,4.6 a7.4,7.4 0 1,0 0,14.8 a7.4,7.4 0 1,0 0,-14.8 Z")
        }
    }

    /** Filled five-point star: favourite. */
    val Star: ImageVector by lazy {
        icon("SheetsStar") {
            dot("M12,2.8 L14.7,9.3 L21.6,9.9 L16.4,14.5 L18,21.2 L12,17.5 L6,21.2 L7.6,14.5 L2.4,9.9 L9.3,9.3 Z")
        }
    }

    /** Filled plus: create. */
    val Plus: ImageVector by lazy {
        icon("SheetsPlus") { line("M12,5 v14 M5,12 h14") }
    }

    /** Filled padlock: the scene is locked. */
    val Lock: ImageVector by lazy {
        icon("SheetsLock") {
            line("M8,10.5 V8 a4,4 0 0 1 8,0 v2.5")
            dot("M6.6,10.5 H17.4 a1.4,1.4 0 0 1 1.4,1.4 V19 a1.4,1.4 0 0 1 -1.4,1.4 H6.6 A1.4,1.4 0 0 1 5.2,19 v-7.1 A1.4,1.4 0 0 1 6.6,10.5 Z")
        }
    }

    /** Open padlock: the scene is unlocked. */
    val Unlock: ImageVector by lazy {
        icon("SheetsUnlock") {
            line("M8,10.5 V8 a4,4 0 0 1 7.6,-1.7")
            dot("M6.6,10.5 H17.4 a1.4,1.4 0 0 1 1.4,1.4 V19 a1.4,1.4 0 0 1 -1.4,1.4 H6.6 A1.4,1.4 0 0 1 5.2,19 v-7.1 A1.4,1.4 0 0 1 6.6,10.5 Z")
        }
    }

    /** Chevron pointing down: an expanded drawer. */
    val ChevronDown: ImageVector by lazy {
        icon("SheetsChevronDown") { line("M6,9.5 L12,15.5 L18,9.5") }
    }

    /** Chevron pointing right: a collapsed drawer. */
    val ChevronRight: ImageVector by lazy {
        icon("SheetsChevronRight") { line("M9.5,6 L15.5,12 L9.5,18") }
    }

    /** Filled diamond: a stop mark. */
    val Stop: ImageVector by lazy {
        icon("SheetsStop") { dot("M12,4.5 L19.5,12 L12,19.5 L4.5,12 Z") }
    }

    /** Fermata: an arc over a dot. */
    val Fermata: ImageVector by lazy {
        icon("SheetsFermata") {
            line("M5.5,15 a6.5,6.5 0 0 1 13,0")
            dot("M11.1,15.6 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0 Z")
        }
    }

    /** Segno. */
    val Segno: ImageVector by lazy {
        icon("SheetsSegno") {
            line("M8.5,14.5 V9.5 a3.5,3.5 0 0 1 7,0 v5")
            dot("M7.6,16.4 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0 Z M14.6,16.4 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0 Z")
        }
    }

    /** Coda: a circle with a cross through it. */
    val Coda: ImageVector by lazy {
        icon("SheetsCoda") {
            line("M12,5.5 a6.5,6.5 0 1,0 0,13 a6.5,6.5 0 1,0 0,-13 Z")
            line("M7.4,7.4 L16.6,16.6 M16.6,7.4 L7.4,16.6")
        }
    }

    /** Accent: a wedge over the bar. */
    val Accent: ImageVector by lazy {
        icon("SheetsAccent") { line("M6,16 L12,8 L18,16") }
    }

    /** Push: a wedge pointing right. */
    val Push: ImageVector by lazy {
        icon("SheetsPush") { line("M8,6 L16,12 L8,18") }
    }

    /** Close: an X. */
    val Close: ImageVector by lazy {
        icon("SheetsClose") { line("M6.5,6.5 L17.5,17.5 M17.5,6.5 L6.5,17.5") }
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

private fun ImageVector.Builder.dot(d: String) = addPath(
    pathData = addPathNodes(d),
    fill = SolidColor(Color.Black)
)
