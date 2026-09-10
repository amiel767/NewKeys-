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
        delay(80)
        startFadeIn = true
        delay(2100)
        isVisible = false
        delay(350)
        onFinished()
    }

    val animatedFade by animateFloatAsState(
        targetValue = if (startFadeIn && isVisible) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
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
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF06070B))
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
                // Glowing SoundStage Delta Loop Logo
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .shadow(40.dp, RoundedCornerShape(32.dp), spotColor = Color(0x9900E5FF))
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF080A10))
                        .border(
                            1.8.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFFF2A55).copy(alpha = glowAlpha),
                                    Color(0xFF9D00FF).copy(alpha = glowAlpha),
                                    Color(0xFF00E5FF).copy(alpha = glowAlpha)
                                )
                            ),
                            RoundedCornerShape(32.dp)
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_soundstage_logo),
                        contentDescription = "SoundStage Final Logo",
                        modifier = Modifier
                            .fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SOUND",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 6.sp,
                        color = Color.White
                    )
                    Text(
                        text = "STAGE",
                        fontSize = 30.sp,
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

                // Glowing Neon Equalizer Waves Animation
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(18.dp, 30.dp, 42.dp, 24.dp, 36.dp, 18.dp, 28.dp).forEachIndexed { idx, h ->
                        val barScale by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(400 + idx * 90, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "eq_bar_$idx"
                        )
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(h * barScale)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF00E5FF), Color(0xFF9D00FF), Color(0xFFFF2A55))
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
