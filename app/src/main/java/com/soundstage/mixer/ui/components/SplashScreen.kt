package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.R
import com.soundstage.mixer.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun LiveKeysSplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(true) }
    var startFadeIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        startFadeIn = true
        delay(1900)
        isVisible = false
        delay(400)
        onFinished()
    }

    val animatedFade by animateFloatAsState(
        targetValue = if (startFadeIn && isVisible) 1f else 0f,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "splash_fade"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(400)),
        modifier = modifier
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "splash_pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.97f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070709))
                .clickable {
                    isVisible = false
                    onFinished()
                }
                .testTag("splash_screen"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .alpha(animatedFade)
                    .scale(scale),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Glowing Minimalist SoundWave / Piano Keys Logo on Pure Black
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .shadow(36.dp, CircleShape, spotColor = Color(0x66FFFFFF))
                        .clip(CircleShape)
                        .background(Color(0xFF000000))
                        .border(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = glowAlpha), Color(0x66FFFFFF).copy(alpha = glowAlpha * 0.6f))), CircleShape)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.soundstage_minimalist_logo_1788942728746),
                        contentDescription = "SoundStage Minimalist Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SOUND",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 6.sp,
                        color = Color.White
                    )
                    Text(
                        text = "STAGE",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 5.sp,
                        color = NeonCyan
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "PROFESSIONAL LIVE AUDIO ENGINE",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.5.sp,
                    color = Color(0x99FFFFFF),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Equalizer Bar Animation
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(18.dp, 28.dp, 38.dp, 22.dp, 32.dp, 16.dp, 24.dp).forEach { h ->
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(h)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.White, NeonCyan)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
