package com.soundstage.mixer.ui.components

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Light blur applied to the page behind a floating window, so the window itself stays crisp.
 * The window must be a sibling of the blurred content, never a child, or it would blur itself.
 *
 * RenderEffect needs API 31; below that the scrim alone keeps the window readable.
 * Radius matches the JSX previews (2 to 3 px at the 1:1 canvas scale).
 */
fun Modifier.windowBackdropBlur(active: Boolean, radius: Dp = 2.5.dp): Modifier =
    if (active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blur(radius) else this
