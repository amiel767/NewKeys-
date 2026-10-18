package com.soundstage.mixer.ui.components.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Fond du Mixer V2 :
 *  - Dégradé vertical (haut bleu-slate → bas noir-charbon)
 *  - Vignette lumineuse en haut (blanc 4.5%) et sombre en bas (noir 28%)
 *
 * NOTE : pour un grain fin (noise) fidèle au preview React, fournir
 *        R.drawable.noise_tile (120x120 PNG) et décommenter la ligne
 *        "// Image(...)" ci-dessous.
 */
@Composable
fun MixerBackgroundV2(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF151B27),
                        Color(0xFF111620)
                    )
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Lumière diffuse en haut (halo blanc)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.045f), Color.Transparent),
                    center = Offset(size.width / 2f, 0f),
                    radius = size.width * 0.8f
                )
            )
            // Vignette sombre en bas
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height),
                    radius = size.height
                )
            )
        }
        // Optionnel — grain fin (remplacer par ta ressource) :
        // Image(
        //     painter = painterResource(R.drawable.noise_tile),
        //     contentDescription = null,
        //     contentScale = ContentScale.Crop,
        //     modifier = Modifier.fillMaxSize(),
        //     alpha = 0.045f
        // )
        content()
    }
}
