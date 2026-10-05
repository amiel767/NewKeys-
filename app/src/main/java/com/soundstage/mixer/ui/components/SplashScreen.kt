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
 * - Edge-to-edge Deep Studio Navy background (#0B1329)
 * - Step 1: Architectural Monogram Logo appears in center
 * - Step 2: "Æ" appears centered under the logo
 * - Step 3: "ther" emerges, pushing "Æ" smoothly to the left
 * - Step 4: "Keys" drops from above with a crisp, cushioned impact
 * - Fluid synchronization without rush
 */
@Composable
fun LiveKeysSplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(true) }

    // Animation Stage Controller
    var logoVisible by remember { mutableStateOf(false) }
    var aeVisible by remember { mutableStateOf(false) }
    var therEmerge by remember { mutableStateOf(false) }
    var keysDrop by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 1. Logo fades & scales in
        delay(100)
        logoVisible = true

        // 2. "Æ" appears centered
        delay(600)
        aeVisible = true

        // 3. "ther" emerges & pushes "Æ" left
        delay(550)
        therEmerge = true

        // 4. "Keys" drops down with cushioned impact
        delay(450)
        keysDrop = true

        // 5. Breathing settle phase
        delay(1100)
        isVisible = false
        delay(400)
        onFinished()
    }

    // Logo animations
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )
    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.85f,
        animationSpec = tween(durationMillis = 750, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "logoScale"
    )

    // "Æ" animations (Appears centered, then pushed left)
    val aeAlpha by animateFloatAsState(
        targetValue = if (aeVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "aeAlpha"
    )
    val aeScale by animateFloatAsState(
        targetValue = if (aeVisible) 1f else 0.75f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "aeScale"
    )
    val aeShiftX by animateDpAsState(
        targetValue = if (therEmerge) (-48).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessLow
        ),
        label = "aeShiftX"
    )

    // "ther" animations (Emerges from behind Æ as it shifts left)
    val therAlpha by animateFloatAsState(
        targetValue = if (therEmerge) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "therAlpha"
    )
    val therSlideX by animateDpAsState(
        targetValue = if (therEmerge) (-44).dp else (-80).dp,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessLow
        ),
        label = "therSlideX"
    )

    // "Keys" animations (Drops from top with impact spring)
    val keysAlpha by animateFloatAsState(
        targetValue = if (keysDrop) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
        label = "keysAlpha"
    )
    val keysOffsetY by animateDpAsState(
        targetValue = if (keysDrop) 0.dp else (-55).dp,
        animationSpec = spring(
            dampingRatio = 0.62f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "keysOffsetY"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(400)),
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
                // 1. Architectural Vector Monogram Logo
                Box(
                    modifier = Modifier
                        .size(190.dp)
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

                Spacer(modifier = Modifier.height(34.dp))

                // 2. Large Cinematic Title: "Æ" -> "ther" emerges -> "Keys" drops from above
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // "Æ"
                        Text(
                            text = "Æ",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = Color.White,
                            modifier = Modifier
                                .offset(x = aeShiftX)
                                .scale(aeScale)
                                .alpha(aeAlpha)
                        )

                        // "ther" emerging as Æ moves left
                        Text(
                            text = "ther",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.sp,
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier
                                .offset(x = therSlideX)
                                .alpha(therAlpha)
                        )

                        Spacer(modifier = Modifier.width(if (therEmerge) 14.dp else 0.dp))

                        // "Keys" dropping from top
                        if (therEmerge) {
                            Text(
                                text = "Keys",
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = 3.sp,
                                color = Color(0xFFA5C8FF),
                                modifier = Modifier
                                    .offset(x = (-44).dp, y = keysOffsetY)
                                    .alpha(keysAlpha)
                            )
                        }
                    }
                }
            }
        }
    }
}
