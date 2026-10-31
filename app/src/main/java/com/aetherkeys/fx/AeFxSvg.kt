package com.aetherkeys.fx

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asImageBitmap
import com.caverock.androidsvg.SVG
import kotlin.math.roundToInt

/**
 * Les éléments dessinés en SVG, isolés.
 *
 * POURQUOI DES SVG ICI. Trois choses de cette fenêtre ne se reproduisent pas
 * proprement avec des primitives Compose : le fond de carte (dégradé + liseré
 * interne + ombre portée), les puits creusés (trois ombres internes
 * superposées) et le bouton power (anneau + relief + halo). En SVG elles
 * s'écrivent exactement comme dans la référence, et le rendu ne dépend plus de
 * l'ordre des `drawBehind` ni des arrondis de Compose.
 *
 * RÈGLES (les mêmes que le paquet d'assets des instruments Æther, pour que les
 * deux familles d'assets se lisent pareil) :
 *   - chaque SVG est AUTONOME : ses propres dégradés, aucun `<text>`, aucun
 *     filtre. Un filtre SVG n'est pas rendu par AndroidSVG et sortirait noir.
 *   - les identifiants de dégradé sont uniques DANS un SVG. Deux SVG distincts
 *     peuvent réutiliser `g`, ils sont analysés séparément.
 *   - les couleurs sont écrites en dur : un SVG n'hérite d'aucun thème.
 */
object AeFxSvg {

    /** Fond de carte : dégradé vertical, liseré haut clair, contour. */
    fun card(hot: Boolean): String {
        val top = if (hot) "#373E4C" else "#323845"
        val bot = if (hot) "#252A37" else "#222733"
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 209 341" width="209" height="341">""" +
            """<defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="$top"/><stop offset="1" stop-color="$bot"/></linearGradient>""" +
            """<linearGradient id="hl" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#FFFFFF" stop-opacity=".11"/>""" +
            """<stop offset=".012" stop-color="#FFFFFF" stop-opacity="0"/></linearGradient></defs>""" +
            """<rect x=".5" y=".5" width="208" height="340" rx="15.5" fill="url(#bg)" """ +
            """stroke="#FFFFFF" stroke-opacity=".13"/>""" +
            """<rect x=".5" y=".5" width="208" height="340" rx="15.5" fill="url(#hl)"/></svg>"""
    }

    /** Puits creusé : les trois ombres internes de la référence, dans un SVG. */
    fun well(radius: Float): String {
        val r = radius
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" preserveAspectRatio="none" """ +
            """width="100" height="100"><defs>""" +
            """<linearGradient id="w" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#0E1420"/><stop offset="1" stop-color="#0B0F18"/></linearGradient>""" +
            """<linearGradient id="s" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#000000" stop-opacity=".62"/>""" +
            """<stop offset=".34" stop-color="#000000" stop-opacity="0"/></linearGradient></defs>""" +
            """<rect x="0" y="0" width="100" height="100" rx="$r" fill="url(#w)"/>""" +
            """<rect x="0" y="0" width="100" height="100" rx="$r" fill="url(#s)"/>""" +
            """<rect x="0.5" y="0.5" width="99" height="99" rx="$r" fill="none" """ +
            """stroke="#FFFFFF" stroke-opacity=".14"/></svg>"""
    }

    /** Bouton sobre : dégradé d'acier, liseré haut, ombre basse. */
    fun ghost(radius: Float): String =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" preserveAspectRatio="none" """ +
            """width="100" height="100"><defs>""" +
            """<linearGradient id="b" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#2D3041"/><stop offset="1" stop-color="#252839"/></linearGradient>""" +
            """<linearGradient id="h" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#FFFFFF" stop-opacity=".13"/>""" +
            """<stop offset=".5" stop-color="#FFFFFF" stop-opacity="0"/></linearGradient></defs>""" +
            """<rect x=".5" y=".5" width="99" height="99" rx="$radius" fill="url(#b)" """ +
            """stroke="#FFFFFF" stroke-opacity=".13"/>""" +
            """<rect x=".5" y=".5" width="99" height="99" rx="$radius" fill="url(#h)"/></svg>"""

    /**
     * Le bouton power. L'anneau est ouvert en haut et une hampe courte descend
     * dans l'ouverture — c'est le tracé de la référence. Allumé, un halo
     * l'entoure ; éteint, il est gris et sans halo.
     */
    fun power(on: Boolean): String {
        val ink = if (on) "#DFE4EE" else "#8D96AB"
        val halo = if (on) {
            """<defs><radialGradient id="gl" cx=".5" cy=".5" r=".5">""" +
                """<stop offset="0" stop-color="#8FB2E8" stop-opacity=".55"/>""" +
                """<stop offset=".55" stop-color="#8FB2E8" stop-opacity=".18"/>""" +
                """<stop offset="1" stop-color="#8FB2E8" stop-opacity="0"/></radialGradient></defs>""" +
                """<circle cx="24" cy="24" r="24" fill="url(#gl)"/>"""
        } else ""
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" width="48" height="48">""" +
            halo +
            """<g fill="none" stroke="$ink" stroke-width="3.1" stroke-linecap="round">""" +
            """<path d="M15.4 14.2a13 13 0 1 0 17.2 0"/><path d="M24 9.5v10.6"/></g></svg>"""
    }

    /** Corps de potard : culasse, gorge, liseré. Le capuchon est dessiné à part. */
    fun knobBody(): String =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 56 56" width="56" height="56"><defs>""" +
            """<radialGradient id="c" cx=".42" cy=".30" r=".78">""" +
            """<stop offset="0" stop-color="#4A5163"/><stop offset=".62" stop-color="#2A3040"/>""" +
            """<stop offset="1" stop-color="#171C28"/></radialGradient>""" +
            """<linearGradient id="e" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#FFFFFF" stop-opacity=".20"/>""" +
            """<stop offset=".55" stop-color="#FFFFFF" stop-opacity="0"/>""" +
            """<stop offset="1" stop-color="#000000" stop-opacity=".45"/></linearGradient></defs>""" +
            """<circle cx="28" cy="28" r="20" fill="url(#c)"/>""" +
            """<circle cx="28" cy="28" r="20" fill="url(#e)"/>""" +
            """<circle cx="28" cy="28" r="20" fill="none" stroke="#000000" stroke-opacity=".55"/>""" +
            """<circle cx="28" cy="28" r="14.5" fill="none" stroke="#FFFFFF" stroke-opacity=".10"/></svg>"""

    /** Emplacement libre : cadre en pointillé + le `+`. */
    fun emptySlot(): String =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 209 341" width="209" height="341"><defs>""" +
            """<linearGradient id="f" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#0E1420"/><stop offset="1" stop-color="#0B0F18"/>""" +
            """</linearGradient>""" +
            """<linearGradient id="s" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#000000" stop-opacity=".75"/>""" +
            """<stop offset=".3" stop-color="#000000" stop-opacity="0"/></linearGradient></defs>""" +
            """<rect x="1" y="1" width="207" height="339" rx="15" fill="url(#f)"/>""" +
            """<rect x="1" y="1" width="207" height="339" rx="15" fill="url(#s)"/>""" +
            """<rect x="1" y="1" width="207" height="339" rx="15" fill="none" """ +
            """stroke="#FFFFFF" stroke-opacity=".14" stroke-width="1.4" stroke-dasharray="7 6"/>""" +
            """<g stroke="#8D96AB" stroke-opacity=".85" stroke-width="2.4" stroke-linecap="round">""" +
            """<path d="M104.5 158.5v24"/><path d="M92.5 170.5h24"/></g></svg>"""

    /** Le creux du bouton engage, éteint : ombre haute, lumière basse. */
    fun pressedWell(): String =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" preserveAspectRatio="none" """ +
            """width="100" height="100"><defs>""" +
            """<linearGradient id="p" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#1B2130"/><stop offset="1" stop-color="#232A3A"/>""" +
            """</linearGradient><linearGradient id="d" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#000000" stop-opacity=".72"/>""" +
            """<stop offset=".55" stop-color="#000000" stop-opacity="0"/>""" +
            """<stop offset="1" stop-color="#FFFFFF" stop-opacity=".10"/></linearGradient></defs>""" +
            """<rect x="0" y="0" width="100" height="100" rx="33" fill="url(#p)"/>""" +
            """<rect x="0" y="0" width="100" height="100" rx="33" fill="url(#d)"/></svg>"""

    /** La corbeille, révélée en tirant une carte vers le bas. */
    fun trash(open: Float): String {
        val lift = open * 4.0
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 44 44" width="44" height="44"><defs>""" +
            """<linearGradient id="t" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#5A2231"/><stop offset="1" stop-color="#3A1520"/>""" +
            """</linearGradient></defs>""" +
            """<rect x="9" y="14" width="26" height="24" rx="5" fill="url(#t)" """ +
            """stroke="#FF3B5C" stroke-opacity=".55"/>""" +
            """<g stroke="#FF8095" stroke-width="2" stroke-linecap="round">""" +
            """<path d="M17 21v11"/><path d="M22 21v11"/><path d="M27 21v11"/></g>""" +
            """<g transform="translate(0 ${-lift}) rotate(${-open * 26} 22 14)">""" +
            """<rect x="8" y="10" width="28" height="4" rx="2" fill="#7A2E40"/>""" +
            """<path d="M18 10v-2.6a1.6 1.6 0 0 1 1.6-1.6h4.8a1.6 1.6 0 0 1 1.6 1.6V10" """ +
            """fill="none" stroke="#7A2E40" stroke-width="2"/></g></svg>"""
    }

    /** Puits encastré d'une vignette : deux ombres internes, sans fond opaque. */
    fun insetWell(): String =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" preserveAspectRatio="none" """ +
            """width="100" height="100"><defs>""" +
            """<linearGradient id="i" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#000000" stop-opacity=".70"/>""" +
            """<stop offset=".28" stop-color="#000000" stop-opacity="0"/></linearGradient></defs>""" +
            """<rect x="0" y="0" width="100" height="100" rx="11" fill="url(#i)"/>""" +
            """<rect x=".5" y=".5" width="99" height="99" rx="11" fill="none" """ +
            """stroke="#FFFFFF" stroke-opacity=".04"/></svg>"""

    /** Le cadre pointillé du `+` d'un emplacement libre. */
    fun dashedBox(): String =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40" width="40" height="40">""" +
            """<rect x="1" y="1" width="38" height="38" rx="12" fill="#FFFFFF" fill-opacity=".04" """ +
            """stroke="#8FB2E8" stroke-opacity=".30" stroke-width="1" stroke-dasharray="4 3"/></svg>"""

    /** La carte d'un effet dans le catalogue. */
    fun catalogCard(): String =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" preserveAspectRatio="none" """ +
            """width="100" height="100"><defs>""" +
            """<linearGradient id="c" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#20283A"/><stop offset="1" stop-color="#1A2130"/></linearGradient>""" +
            """<linearGradient id="l" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#FFFFFF" stop-opacity=".06"/>""" +
            """<stop offset=".4" stop-color="#FFFFFF" stop-opacity="0"/></linearGradient></defs>""" +
            """<rect x=".5" y=".5" width="99" height="99" rx="12.5" fill="url(#c)" """ +
            """stroke="#FFFFFF" stroke-opacity=".07"/>""" +
            """<rect x=".5" y=".5" width="99" height="99" rx="12.5" fill="url(#l)"/></svg>"""

    /** Le champ du preset : un puits avec un liseré haut clair. */
    fun presetField(): String =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" preserveAspectRatio="none" """ +
            """width="100" height="100"><defs>""" +
            """<linearGradient id="p" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#0E1420"/><stop offset="1" stop-color="#0B0F18"/></linearGradient>""" +
            """<linearGradient id="h" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="#000000" stop-opacity=".62"/>""" +
            """<stop offset=".45" stop-color="#000000" stop-opacity="0"/></linearGradient></defs>""" +
            """<rect x="0" y="0" width="100" height="100" rx="9" fill="url(#p)"/>""" +
            """<rect x="0" y="0" width="100" height="100" rx="9" fill="url(#h)"/>""" +
            """<rect x=".5" y=".5" width="99" height="99" rx="9" fill="none" """ +
            """stroke="#FFFFFF" stroke-opacity=".14"/></svg>"""
}

/**
 * Cache de rendu : un SVG coûteux à analyser, on le rasterise une fois par taille.
 *
 * On passe par `setDocumentWidth/Height` puis `renderToCanvas`, et PAS par
 * `renderToPicture` : c'est la méthode que les instruments Æther utilisent, elle
 * est vérifiée sur l'appareil. L'échec est JOURNALISÉ, jamais avalé — un SVG qui
 * ne se dessine pas doit se voir dans les logs, sinon on mesure une fenêtre
 * vide en croyant que le code est bon.
 */
private object AeFxSvgCache {
    private val map = androidx.collection.LruCache<String, Bitmap>(64)

    fun get(svg: String, w: Int, h: Int): Bitmap {
        val k = "${svg.hashCode()}|$w|$h"
        map[k]?.let { return it }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val doc = SVG.getFromString(svg)
            doc.setDocumentWidth(w.toFloat())
            doc.setDocumentHeight(h.toFloat())
            doc.renderToCanvas(Canvas(bmp))
        } catch (t: Throwable) {
            android.util.Log.e("AeFxSvg", "SVG illisible (${w}×$h) : ${t.message}")
        }
        map.put(k, bmp)
        return bmp
    }
}

/** Peint un SVG isolé, mis à l'échelle de la boîte. */
fun Modifier.fxSvg(svg: String): Modifier = this.drawWithCache {
    val bmp = AeFxSvgCache.get(svg, size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))
    onDrawBehind { drawImage(bmp.asImageBitmap()) }
}
