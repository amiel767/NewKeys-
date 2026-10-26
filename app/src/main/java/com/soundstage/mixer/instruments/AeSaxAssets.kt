package com.soundstage.mixer.instruments

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Pearls (touchpieces) and bell mouth, normalised to each cutout image (0..1). Measured on the supplied photos. */
object AeSaxGeo {
    class G(val aspect: Float, val pearls: List<Pair<Float, Float>>, val bell: Pair<Float, Float>)
    val soprano = G(0.9744f, listOf(0.3576f to 0.3448f, 0.3817f to 0.3602f, 0.3975f to 0.3864f, 0.4467f to 0.3837f, 0.5368f to 0.5412f, 0.5777f to 0.5837f, 0.6120f to 0.6181f, 0.5544f to 0.6199f), 0.9250f to 0.9285f)
    val alto = G(0.4044f, listOf(0.6484f to 0.3452f, 0.6566f to 0.3685f, 0.6217f to 0.3942f, 0.6402f to 0.4216f, 0.3570f to 0.6772f, 0.3119f to 0.7261f, 0.2483f to 0.7676f, 0.1826f to 0.7361f), 0.8023f to 0.5809f)
    val bari = G(0.5276f, listOf(0.5872f to 0.2206f, 0.5813f to 0.2398f, 0.5884f to 0.2559f, 0.5966f to 0.2745f, 0.4991f to 0.6307f, 0.5414f to 0.6543f, 0.5344f to 0.6884f, 0.4944f to 0.6685f), 0.8221f to 0.2020f)
    /** Image set per instrument type (0 soprano, 1 alto, 2 tenor, 3 baritone). Tenor reuses the alto photo until a tenor photo is supplied. */
    val files = arrayOf("soprano", "alto", "alto", "bari")
    fun of(type: Int): G = when (type) { 0 -> soprano; 3 -> bari; else -> alto }
}

/** Loads the cut-out photos from res/drawable-nodpi (ae_sax_<type>_<finish>.webp) without needing the app's R class. */
object AeSaxBitmaps {
    private val cache = LruCache<String, ImageBitmap>(8)
    val finishes = arrayOf("gold", "black", "silver", "copper")
    fun get(ctx: Context, type: Int, fin: Int): ImageBitmap? {
        val name = "ae_sax_${files(type)}_${finishes[fin.coerceIn(0, 3)]}"
        cache.get(name)?.let { return it }
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        if (id == 0) return null
        val bm = BitmapFactory.decodeResource(ctx.resources, id) ?: return null
        return bm.asImageBitmap().also { cache.put(name, it) }
    }
    private fun files(type: Int) = AeSaxGeo.files[type.coerceIn(0, 3)]
}
