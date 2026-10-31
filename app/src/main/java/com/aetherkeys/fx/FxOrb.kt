package com.aetherkeys.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * L'ORBE DE SPACE — RECYCLÉ, PAS RÉINVENTÉ.
 *
 * Le dessin vient de `sceneOrb` (`com.soundstage.mixer.ui.fx.FxScenes.kt`), qui
 * est lui-même le portage de `orb()` de la fenêtre branchée. Il est repris tel
 * quel : la sphère, les anneaux, les trois queues de particules, le champ
 * d'étoiles, l'anneau de diffusion. On ne redessine rien.
 *
 * CE QUI CHANGE, ET C'EST TOUT : la couleur ne vient plus d'un accent unique
 * mais de la PALETTE DU PRESET. `sceneOrb` faisait `hsl(268 - damp * 75, …)` —
 * une seule teinte, calculée depuis l'amortissement. Ici la palette donne la
 * teinte, la saturation et la lumière, et un preset « multi » mélange DEUX
 * teintes. C'est la modification demandée, et elle est locale : le reste de la
 * géométrie est identique.
 *
 * LE MÉLANGE SE FAIT EN RVB. Mélanger deux teintes en degrés fait traverser le
 * cercle chromatique à l'envers : 30 (orange) et 268 (violet) donnent 149, un
 * vert. En RVB on obtient un vrai cocktail.
 */

private const val TAU = 6.2831855f

/** Une palette d'orbe : ce que la nébuleuse utilise à un instant donné. */
private class OrbPal(
    var h: Float, var dh: Float, var s: Float, var l: Float, var h2: Float, var multi: Float,
)

/** `hsl` de `FxScenes.kt`, repris tel quel. */
private fun hsl(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = (((h % 360f) + 360f) % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r, g, b) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r + m, g + m, b + m)
}

/** `oval` de `FxScenes.kt`, repris tel quel. */
private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, rot: Float,
                           color: Color, width: Float, effect: PathEffect?) {
    val path = Path()
    for (i in 0..64) {
        val a = i * TAU / 64
        val x = rx * cos(a); val y = ry * sin(a)
        val px = cx + x * cos(rot) - y * sin(rot)
        val py = cy + x * sin(rot) + y * cos(rot)
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color = color, style = Stroke(width = width, pathEffect = effect))
}

/** L'état animé de l'orbe : le temps, et les valeurs lissées vers leur cible. */
private class OrbAnim {
    var t = 0f
    var drawn = false
    val sm = FloatArray(7) { .5f }
    var pal = OrbPal(268f, 75f, 48f, 74f, 268f, 0f)
    var palInit = false
}

/**
 * Dessine l'orbe. [vals] sont les sept valeurs qui pilotent la géométrie
 * (size, decay, pre, damp, width, mix, diff), [palette] la couleur du preset.
 *
 * `live = false` ne fige pas l'orbe : il le RALENTIT jusqu'à l'arrêt, comme la
 * référence. Une fois arrêté la dernière image reste à l'écran, donc éteindre
 * un effet laisse une image nette au lieu d'un vide.
 */
@Composable
fun FxOrb(
    vals: List<Float>,
    palette: FxPalette?,
    live: Boolean = true,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val anim = remember { OrbAnim() }
    val motes = remember {
        // 70 étoiles à positions fixes, chacune avec sa phase. `Random` n'est pas
        // utilisé : une suite déterministe suffit et garde la capture stable.
        List(70) { i ->
            val a = sin(i * 12.9898f) * 43758.547f
            val b = sin(i * 78.233f) * 12345.678f
            val c = sin(i * 39.425f) * 24634.634f
            Triple(a - a.toInt(), b - b.toInt(), c - c.toInt())
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        var spd = 1f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) .016f else min(.05f, (now - last) / 1e9f)
                last = now
                spd += ((if (live) 1f else 0f) - spd) * min(1f, dt * 9.5f)
                if (!live && spd < .004f) spd = 0f
                if (!live && spd == 0f && anim.drawn) return@withFrameNanos
                anim.drawn = true
                anim.t += dt * spd
                // Les sept réglages glissent vers leur cible : c'est ce qui rend
                // un changement de preset fluide au lieu de sauter.
                for (i in 0 until 7) {
                    val tgt = vals.getOrElse(i) { anim.sm[i] }
                    anim.sm[i] += (tgt - anim.sm[i]) * min(1f, dt * 4.2f)
                }
                val p = palette ?: FxDesign.SPACE.palettes.getValue("Hall")
                if (!anim.palInit) {
                    anim.pal = OrbPal(p.h, p.dh, p.s, p.l, p.h2, if (p.multi) 1f else 0f)
                    anim.palInit = true
                }
                val op = anim.pal
                op.let {
                    val k = min(1f, dt * 3f)
                    it.h += (p.h - it.h) * k
                    it.dh += (p.dh - it.dh) * k
                    it.s += (p.s - it.s) * k
                    it.l += (p.l - it.l) * k
                    it.h2 += (p.h2 - it.h2) * k
                    val tm = if (p.multi) 1f else 0f
                    it.multi += (tm - it.multi) * min(1f, dt * 2.6f)
                }
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        drawOrb(anim, vals, scale, motes)
    }
}

/**
 * Le corps du dessin. Séparé pour rester lisible, et parce que c'est LUI qu'on
 * compare à `sceneOrb` : tout ce qui suit est le portage, ligne pour ligne, avec
 * `colAt` branché sur la palette.
 */
private fun DrawScope.drawOrb(
    a: OrbAnim,
    vals: List<Float>,
    scale: Float,
    motes: List<Triple<Float, Float, Float>>,
) {
    val sm = a.sm
    val p = a.pal
    val t = a.t
    val w = size.width
    val h = size.height
    val sz = sm[0]; val dc = sm[1]; val pd = sm[2]; val wd = sm[4]; val mx = sm[5]; val df = sm[6]

    fun toRgb(hue: Float): Triple<Float, Float, Float> {
        val c = hsl(hue, p.s / 100f, p.l / 100f)
        return Triple(c.red, c.green, c.blue)
    }
    fun colAt(u: Float, alpha: Float): Color {
        val A = toRgb(p.h)
        val B = toRgb(p.h2)
        val m = p.multi * u
        return Color(
            red = A.first + (B.first - A.first) * m,
            green = A.second + (B.second - A.second) * m,
            blue = A.third + (B.third - A.third) * m,
            alpha = alpha,
        )
    }

    val cx = w / 2f
    val cy = h * .45f
    val r = min(w * .135f, h * .115f) * (.78f + sz * .62f) * (1f + .03f * sin(t * 2f)) * scale
    val core = (FxDesign.SPACE.palettes.values.firstOrNull()?.core) ?: Color(0xFFEEE8FF)
    val rim = Color(0xFF1D1A38)

    // Le halo de fond, éteint vers les bords.
    // La maquette a un rayon INTERNE (R*.3) : le halo est plat au centre puis
    // decroit. Un degrade partant du centre serait plus faible sur cette couronne.
    val a0 = .18f + .48f * mx
    drawCircle(
        brush = Brush.radialGradient(
            0f to colAt(.4f, a0), (.30f / 4.2f) to colAt(.4f, a0), 1f to Color.Transparent,
            center = Offset(cx, cy), radius = r * 4.2f,
        ),
        radius = r * 4.2f, center = Offset(cx, cy),
    )

    // Les 70 étoiles, chacune scintillant sur sa phase.
    for ((mxp, myp, ph) in motes) {
        drawRect(
            Color.White.copy(alpha = .2f + .5f * abs(sin(t * 1.3f + ph * TAU))),
            topLeft = Offset(mxp * w, myp * h), size = Size(1.2f, 1.2f),
        )
    }

    // Les anneaux qui s'éloignent.
    for (ring in 0 until 3) {
        val u = (t * (.12f + sz * .2f) + ring / 3f) % 1f
        if (u < pd * .4f) continue
        drawCircle(colAt(.15f, 1f).copy(alpha = (1f - u) * .4f), radius = r * (1.1f + u * 4.2f),
            center = Offset(cx, cy), style = Stroke(width = 1.6f))
    }

    // L'anneau pointillé en biais.
    var rx = r * 1.75f
    oval(cx, cy, rx, rx * (.3f + wd * .18f), -.35f + sin(t * .25f) * wd * .55f,
        colAt(.3f, 1f).copy(alpha = .3f + .5f * mx), 2.2f,
        PathEffect.dashPathEffect(floatArrayOf(4f + dc * 36f, 9f), -t * 30f * (.3f + wd)))

    // La traîne de particules.
    rx = r * 2.7f
    val ry = rx * .36f
    val tl1 = .25f + cos(t * .2f) * wd * .4f
    val n = 22 + (sz * 14f).roundToInt()
    val l = 3 + (dc * 6f).roundToInt()
    for (i in 0 until n) {
        val an = t * (.25f + wd * .9f) + i * TAU / n
        for (s in 0 until l) {
            val aa = an - s * .08f * (.4f + dc)
            val wob = sin(aa * 5f + t * 3f) * df * r * .45f
            val x = (rx + wob) * cos(aa); val y = (ry + wob * .4f) * sin(aa)
            drawCircle(
                if (s == 0) Color.White else colAt(i.toFloat() / n, 1f),
                radius = 2.1f * (1f - s.toFloat() / l * .5f),
                center = Offset(cx + x * cos(tl1) - y * sin(tl1), cy + x * sin(tl1) + y * cos(tl1)),
                alpha = (1f - s.toFloat() / l) * .85f,
            )
        }
    }

    // L'anneau de diffusion, borné pour rester dans la case.
    rx = min(r * 3.7f, w * .30f)
    val ry2 = rx * .30f
    val tl2 = -.6f + sin(t * .17f) * wd * .3f
    for (e in 0 until 3) {
        oval(cx, cy, rx * (1f + e * .06f), ry2 * (1f + e * .06f), tl2,
            colAt(.6f, 1f).copy(alpha = .05f + .25f * dc * (1f - e / 3f)), 1f, null)
    }
    for (c in 0 until 3) {
        val an = -t * (.18f + wd * .6f) + c * 2.094f
        val t2 = 8 + (dc * 20f).roundToInt()
        for (s in 0 until t2) {
            val aa = an + s * .05f
            drawCircle(
                if (s == 0) Color.White else colAt(c.toFloat() / 3f, 1f),
                radius = 3f * (1f - s.toFloat() / t2 * .6f),
                center = Offset(cx + rx * cos(aa) * cos(tl2) - ry2 * sin(aa) * sin(tl2),
                                cy + rx * cos(aa) * sin(tl2) + ry2 * sin(aa) * cos(tl2)),
                alpha = (1f - s.toFloat() / t2) * (.3f + .6f * mx),
            )
        }
    }

    // La sphère : cœur clair, teinte au milieu, bord sombre.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(core, colAt(.55f, 1f), rim),
            center = Offset(cx - r * .35f, cy - r * .4f), radius = r * 1.4f,
        ),
        radius = r, center = Offset(cx, cy),
    )

    // Les méridiens gravés, dans la sphère.
    for (q in 0 until 3) {
        oval(cx, cy, r * abs(cos(t * .4f * (.5f + wd) + q * 1.05f)), r, 0f,
            Color.White.copy(alpha = .1f + .1f * df), .8f, null)
    }

    drawCircle(Color.White.copy(alpha = .08f + .4f * mx), radius = r * 1.08f,
        center = Offset(cx, cy), style = Stroke(width = 1f))
}
