package com.soundstage.mixer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Raised panel, translated from the JSX `Module` (MixerPreview.jsx:81).
 *
 * The JSX reads as "relief" through three stacked cues, not through a border:
 *   boxShadow: "inset 0 1px 0 rgba(255,255,255,.09), 0 5px 12px rgba(0,0,0,.35)"
 *   background: linear-gradient(180deg, panelTop, panelBottom)
 *   border: 1px solid rgba(255,255,255,.05)   <- 5 %, i.e. visually absent
 * Compose has no inset shadow, so the top highlight is drawn explicitly over the
 * content, and the drop shadow comes from Modifier.shadow.
 */
@Composable
fun MixerModuleV2(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    panelTop: Color = Color(0xFF2B3348),
    panelBottom: Color = Color(0xFF1D2433),
    elevation: Dp = 5.dp,
    highlightAlpha: Float = 0.09f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false, ambientColor = Color.Black, spotColor = Color(0x59000000))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(panelTop, panelBottom)))
            .drawWithContent {
                drawContent()
                drawLine(
                    color = Color.White.copy(alpha = highlightAlpha),
                    start = Offset(size.width * 0.06f, 0.5f),
                    end = Offset(size.width * 0.94f, 0.5f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            },
        content = content
    )
}

/** Rounded +/- step button, translated from the JSX `RoundStep` (MixerPreview.jsx:91). */
@Composable
fun RoundStepV2(
    size: Dp = 30.dp,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(2.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = Color(0x59000000))
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x0AFFFFFF))))
            .drawWithContent {
                drawContent()
                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = Offset(size.toPx() * 0.3f, 1f),
                    end = Offset(size.toPx() * 0.7f, 1f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}
