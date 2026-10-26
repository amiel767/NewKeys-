package com.soundstage.mixer.instruments

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.caverock.androidsvg.SVG
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Reference stage of the validated preview. Everything is placed in these units and scaled uniformly: no size drift. */
const val AE_W = 1000f
const val AE_H = 460f
val LocalAeScale = compositionLocalOf { 1f }

/** Fits the 1000 x 460 stage into the available box (letterboxed, centred). Recommended window ratio = AE_W / AE_H. */
@Composable
fun AeStage(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val s = min(constraints.maxWidth / AE_W, constraints.maxHeight / AE_H)
        val d = LocalDensity.current
        CompositionLocalProvider(LocalAeScale provides s) {
            Box(Modifier.size(with(d) { (AE_W * s).toDp() }, with(d) { (AE_H * s).toDp() })) { content() }
        }
    }
}

@Composable fun Number.su(): Dp = with(LocalDensity.current) { (this@su.toFloat() * LocalAeScale.current).toDp() }
@Composable fun Number.fs(): TextUnit = with(LocalDensity.current) { (this@fs.toFloat() * LocalAeScale.current).toSp() }
fun Modifier.at(x: Number, y: Number, w: Number, h: Number): Modifier = composed { this.offset(x.su(), y.su()).size(w.su(), h.su()) }

object AeC {
    val tx = Color(0xFFE6EAF3); val mute = Color(0xFF8D96AB); val gold = Color(0xFFE8CF94); val goldDim = Color(0xFFC9B88F)
    val reg = Color(0xFFF2D58A); val knobLabel = Color(0xFF9A92C8); val violet = Color(0xFFB9A8FF)
    val p1 = Color(0xFF7A5CFF); val p2 = Color(0xFFE05CFF); val p3 = Color(0xFFFFB35C)
}

/** Assign the app's bundled fonts once at startup (Cinzel, Share Tech Mono, Sora, Plus Jakarta Sans). Defaults are system fallbacks. */
object AeFonts {
    var plate: FontFamily = FontFamily.Serif
    var mono: FontFamily = FontFamily.Monospace
    var ui: FontFamily = FontFamily.SansSerif
    var display: FontFamily = FontFamily.SansSerif
}

@Composable
fun AeT(
    text: String, size: Number, color: Color, modifier: Modifier = Modifier, font: FontFamily = AeFonts.ui,
    weight: FontWeight = FontWeight.Bold, spacing: Float = 0f, align: TextAlign = TextAlign.Start,
) = Text(text, modifier, color, fontSize = size.fs(), fontFamily = font, fontWeight = weight, letterSpacing = spacing.em, textAlign = align, maxLines = 1, softWrap = false)

/** Renders an [AeSvgs] asset at the exact pixel size of the element (cached). Needs com.caverock:androidsvg-aar. */
object AeSvgCache {
    private val cache = LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(64)
    fun get(svg: String, w: Int, h: Int): androidx.compose.ui.graphics.ImageBitmap {
        val key = "${svg.hashCode()}|$w|$h"
        cache.get(key)?.let { return it }
        val doc = SVG.getFromString(svg)
        doc.setDocumentWidth(w.toFloat()); doc.setDocumentHeight(h.toFloat())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        doc.renderToCanvas(android.graphics.Canvas(bmp))
        return bmp.asImageBitmap().also { cache.put(key, it) }
    }
}

fun Modifier.aeSvg(svg: String): Modifier = this.drawWithCache {
    val bmp = AeSvgCache.get(svg, size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))
    onDrawBehind { drawImage(bmp) }
}

/** Walnut: gradient + seeded grain lines (no filters, deterministic). */
fun Modifier.aeWood(): Modifier = this.drawBehind {
    drawRect(Brush.horizontalGradient(listOf(Color(0xFF4A2815), Color(0xFF6A3A20), Color(0xFF3A1E0E))))
    val r = Random(4)
    repeat(150) {
        val x = r.nextFloat() * size.width
        drawLine(Color.Black.copy(alpha = 0.04f + r.nextFloat() * 0.12f), Offset(x, 0f), Offset(x + r.nextFloat() * 6f - 3f, size.height), 1f + r.nextFloat() * 2f)
    }
}
