package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.R
import kotlinx.coroutines.delay

/**
 * Immersive Fullscreen SplashScreen for Æther Keys:
 * - 100% Edge-to-Edge Deep Studio Navy background (#0B1329) with no outer borders
 * - Logo slides down smoothly from the top to settle at its central position
 * - "Æther" slides in smoothly from the LEFT
 * - "Keys" slides in smoothly from the RIGHT
 * - Both converge into a unified, perfectly centered typographic title
 */
@Composable
fun LiveKeysSplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(true) }

    // Animation Stage Controller
    var logoStage by remember { mutableStateOf(false) }
    var textStage by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Step 1: Logo descends from above and settles
        delay(120)
        logoStage = true

        // Step 2: "Æther" and "Keys" glide in symmetrically from Left & Right
        delay(500)
        textStage = true

        // Step 3: Breathing phase then seamless exit
        delay(1600)
        isVisible = false
        delay(350)
        onFinished()
    }

    // 1. Logo Animations (Slides from Top to Center)
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoStage) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )
    val logoScale by animateFloatAsState(
        targetValue = if (logoStage) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )
    val logoOffsetY by animateDpAsState(
        targetValue = if (logoStage) 0.dp else (-70).dp,
        animationSpec = spring(
            dampingRatio = 0.76f,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoOffsetY"
    )

    // 2. "Æther" Animations (Glides in from Left)
    val aetherAlpha by animateFloatAsState(
        targetValue = if (textStage) 1f else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "aetherAlpha"
    )
    val aetherSlideX by animateDpAsState(
        targetValue = if (textStage) 0.dp else (-80).dp,
        animationSpec = spring(
            dampingRatio = 0.80f,
            stiffness = Spring.StiffnessLow
        ),
        label = "aetherSlideX"
    )

    // 3. "Keys" Animations (Glides in from Right)
    val keysAlpha by animateFloatAsState(
        targetValue = if (textStage) 1f else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "keysAlpha"
    )
    val keysSlideX by animateDpAsState(
        targetValue = if (textStage) 0.dp else 80.dp,
        animationSpec = spring(
            dampingRatio = 0.80f,
            stiffness = Spring.StiffnessLow
        ),
        label = "keysSlideX"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(350)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF090F20),
                            Color(0xFF0B1329),
                            Color(0xFF050813)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isVisible = false
                    onFinished()
                }
                .testTag("splash_screen"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Architectural Monogram Logo descending from top
                Box(
                    modifier = Modifier
                        .size(175.dp)
                        .offset(y = logoOffsetY)
                        .scale(logoScale)
                        .alpha(logoAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_aether_keys_logo),
                        contentDescription = "Æther Keys Monogram",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Typographic Title: "Æther" (from Left) & "Keys" (from Right) converging at Center
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // "Æther" - Single unified word
                        Text(
                            text = "Æther",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 0.5.sp,
                            color = Color.White,
                            modifier = Modifier
                                .offset(x = aetherSlideX)
                                .alpha(aetherAlpha)
                        )

                        // "Keys" - Studio Glacier Blue
                        Text(
                            text = "Keys",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.sp,
                            color = Color(0xFF8BB5F8),
                            modifier = Modifier
                                .offset(x = keysSlideX)
                                .alpha(keysAlpha)
                        )
                    }
                }
            }
        }
    }
}
