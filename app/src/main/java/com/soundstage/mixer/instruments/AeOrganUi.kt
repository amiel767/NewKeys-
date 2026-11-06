package com.soundstage.mixer.instruments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * OrganLab — GABARIT. Only the slot of each control, nothing drawn inside.
 *
 * No knobs, no sliders, no drawbar caps, no panels, no readouts. Each control is one outlined
 * box at its measured position, carrying its own name. The frame and the wood background stay,
 * because those were approved.
 *
 * Positions come from the reference panel (632 x 364), scaled x 1.582 and y 1.264 onto this
 * 1000 x 460 stage. Every box below is a measured position, not an eye placement.
 *
 * The 36 engine parameters, one box each:
 *   0..8   nine drawbars, ONE row                 18..21 percussion ON SOFT FAST 3RD
 *   22     vibrato/chorus dial                     23/24  vibrato UPPER / LOWER
 *   25     rotary speed                            26     click
 *   27     bass (the engine's Tone)                28     drive
 *   29     space                                   30     level
 *   31     reverb mode ROOM/STAGE/HALL             32     treble
 *   33     cabinet                                 34     amount
 *   35     velocity sense
 */

/** Drawbar feet, left to right in the Hammond order (16' … 1'). */
private val FOOT = arrayOf("16'", "5⅓'", "8'", "4'", "2⅔'", "2'", "1⅗'", "1⅓'", "1'")

/**
 * End cheek: the same lacquered walnut as the cabinet, a shade darker, lit along its inner edge.
 * Kept from the console the user approved ("le cadre et le fond").
 */
@Composable
private fun BoxScope.Rail(x: Number, flip: Boolean) {
    val sh = RoundedCornerShape(topStart = if (flip) 0.su() else 24.su(), topEnd = if (flip) 24.su() else 0.su())
    Box(
        Modifier.at(x, 0, 46, 460).clip(sh).aeWood(Color(0xFF6A3A20), if (flip) 9 else 4)
            .drawBehind {
                val ex = if (flip) 0f else size.width
                drawLine(Color(0x33FFFFFF), Offset(ex, 0f), Offset(ex, size.height), 1.5f)
            },
    )
}

/** One control slot: an empty outlined box with its name. This is all the gabarit draws. */
@Composable
private fun Slot(label: String, x: Number, y: Number, w: Number, h: Number, accent: Color = AeC.organ) {
    val sh = RoundedCornerShape(5.su())
    Box(
        Modifier.at(x, y, w, h).clip(sh)
            .background(Color(0x14FFFFFF))
            .border(1.su(), accent.copy(alpha = 0.6f), sh),
        contentAlignment = Alignment.Center,
    ) {
        AeT(label, 8, accent, Modifier.padding(horizontal = 3.su()), AeFonts.mono, FontWeight.Bold, 0.08f, TextAlign.Center)
    }
}

/**
 * OrganLab gabarit.
 *
 * Five columns in the main row, seven then five in the lower rows. The gaps are what keep the
 * slots from touching: 12 between columns, 12 in the bottom row, 17 in the voicing row.
 */
@Composable
fun BoxScope.AeOrganUi(c: AeCtl, a: AeAnim) {
    Box(Modifier.fillMaxSize().drawBehind { drawWood(Color(0xFF6A3A20), 4) })
    Rail(0, flip = false)
    Rail(954, flip = true)

    // Content lives BETWEEN the cheeks: the left one ends at x=46, the right one starts at 954.
    // MARGIN keeps a 12-unit breathing space, so the columns run 58..942. Placing a slot at x=16
    // put it under the cheek, where the wood hid its border.
    val L = 58

    // No header row here: AeWindow already draws the back arrow at 10..40 and the preset pill
    // at 680..920, and a slot underneath them lost its border to that chrome.

    // ── UPPER: registration over the nine drawbars of the single row. 248 wide, x 58..306.
    Slot("UPPER · enregistrement", L, 52, 248, 28)
    for (i in 0 until 9) Slot(FOOT[i], L + i * 28, 92, 24, 160)

    // ── VIBRATO · CHORUS: the dial, then the two manual switches. 150 wide, x 318..468.
    Slot("VIBRATO · V1 V2 V3 C1 C2 C3", L + 260, 52, 150, 130)
    Slot("UPPER", L + 260, 194, 69, 58)
    Slot("LOWER", L + 341, 194, 69, 58)

    // ── PERCUSSION: the four rockers. 160 wide, x 480..640.
    Slot("ON", L + 422, 52, 160, 44)
    Slot("SOFT", L + 422, 104, 160, 44)
    Slot("FAST", L + 422, 156, 160, 44)
    Slot("3RD", L + 422, 208, 160, 44)

    // ── BASS / TREBLE: the two tone sliders. 290 wide, x 652..942.
    Slot("BASS", L + 594, 52, 290, 100)
    Slot("TREBLE", L + 594, 160, 290, 92)

    // ── Lower row, seven slots of 116 with a 12 gap: 58..942.
    val low = arrayOf("ROOM", "STAGE", "HALL", "CABINET", "AMOUNT", "SPEED", "ROTOR")
    for (i in low.indices) Slot(low[i], L + i * 128, 272, 116, 60)

    // ── Voicing row, five slots of 163 with a 17 gap: 58..941.
    val voicing = arrayOf("CLICK", "DRIVE", "SPACE", "LEVEL", "VEL SENS")
    for (i in voicing.indices) Slot(voicing[i], L + i * 180, 348, 163, 92)
}
