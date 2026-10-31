package com.soundstage.mixer.instruments

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Water surface: wave equation on a 128 x 72 grid, anisotropic so rings read as perspective ellipses. */
class AeWaves {
    companion object { const val GW = 128; const val GH = 72 }
    private var h0 = FloatArray(GW * GH); private var h1 = FloatArray(GW * GH)
    val pixels = IntArray(GW * GH)
    class Ring(val x: Float, val y: Float, var age: Float, val amp: Float)
    val rings = ArrayList<Ring>()
    private var next = 2f
    private val queued = ArrayList<FloatArray>()
    fun step() {
        val cx = 0.30f; val cy = 0.13f; val dm = 0.9925f
        for (y in 1 until GH - 1) for (x in 1 until GW - 1) {
            val i = y * GW + x
            h0[i] = (2f * h1[i] - h0[i] + cx * (h1[i - 1] + h1[i + 1] - 2f * h1[i]) + cy * (h1[i - GW] + h1[i + GW] - 2f * h1[i])) * dm
        }
        val t = h0; h0 = h1; h1 = t
    }
    fun drop(ux: Float, uy: Float, a: Float) {
        val cx = ux * GW; val cy = uy * GH
        for (y in max(1, (cy - 4).toInt()) until min(GH - 1, (cy + 5).toInt())) for (x in max(1, (cx - 5).toInt()) until min(GW - 1, (cx + 6).toInt())) {
            val r = ((x - cx) / 1.6f).pow(2) + (y - cy).pow(2); h1[y * GW + x] -= a * exp(-r)
        }
        rings.add(Ring(ux, uy, 0f, a))
    }
    private fun randn(): Float { var a = 0f; repeat(4) { a += Random.nextFloat() }; return (a - 2f) * 1.7f }
    /** Drops at irregular, log-normal intervals; sometimes a second drop follows. */
    fun ambient(dt: Float) {
        val it = queued.iterator()
        while (it.hasNext()) { val q = it.next(); q[0] -= dt; if (q[0] <= 0f) { drop(q[1], q[2], q[3]); it.remove() } }
        next -= dt; if (next > 0f) return
        val ux = 0.28f + Random.nextFloat() * 0.44f; val uy = 0.72f + Random.nextFloat() * 0.2f
        drop(ux, uy, 0.28f + Random.nextFloat() * 0.45f)
        if (Random.nextFloat() < 0.3f) queued.add(floatArrayOf(0.35f + Random.nextFloat() * 0.6f, (ux + (Random.nextFloat() - 0.5f) * 0.08f).coerceIn(0.2f, 0.8f), (uy + (Random.nextFloat() - 0.5f) * 0.06f).coerceIn(0.68f, 0.95f), 0.2f + Random.nextFloat() * 0.25f))
        next = exp(ln(4.2f) + randn() * 0.75f)
    }
    fun tickRings(dt: Float) { val it = rings.iterator(); while (it.hasNext()) { val r = it.next(); r.age += dt; if (r.age > 3f) it.remove() } }
    fun toPixels(): IntArray { for (i in 0 until GW * GH) { val v = (h1[i] * 0.5f + 0.5f).coerceIn(0f, 1f); pixels[i] = (0xFF shl 24) or ((v * 255f).toInt() shl 16) }; return pixels }
}

enum class AePType { PETAL, SPARK, LEAF, DUST, STAR, NEON }

/** Petals, sparks, leaves, dust, stars, neon: one kind per model, ambient + bursts on notes. */
class AeParticles {
    class P(var x: Float, var y: Float, var vx: Float, var vy: Float, var r: Float, val vr: Float, val s: Float, var l: Float, var ph: Float, val life: Float, val type: AePType, val color: Color)
    val list = ArrayList<P>()
    var type = AePType.PETAL
    var colors: List<Color> = listOf(Color.White)
    val ambientRate: Float get() = when (type) { AePType.PETAL -> 1.6f; AePType.LEAF -> 1.2f; AePType.SPARK -> 2f; AePType.DUST -> 2.5f; AePType.STAR -> 1.5f; AePType.NEON -> 2f }
    fun spawn(n: Int, w: Float, h: Float, dp: Float, x: Float? = null, y: Float? = null, burst: Boolean = false) {
        repeat(n) {
            if (list.size >= 90) return
            val ty = type; val c = colors[Random.nextInt(colors.size)]
            val s = (if (ty == AePType.DUST) 1.2f else if (ty == AePType.STAR) 1.6f else 3.6f + Random.nextFloat() * 3.4f) * dp
            val slow = ty == AePType.DUST || ty == AePType.STAR || ty == AePType.NEON
            val falls = ty == AePType.PETAL || ty == AePType.LEAF
            list.add(P(x ?: (Random.nextFloat() * w), y ?: (if (falls) -10f * dp else Random.nextFloat() * h * 0.7f),
                if (burst) (Random.nextFloat() - 0.5f) * 60f * dp else (Random.nextFloat() - 0.3f) * 14f * dp,
                if (burst) -(30f + Random.nextFloat() * 50f) * dp else (8f + Random.nextFloat() * 16f) * dp * (if (slow) 0.15f else 1f),
                Random.nextFloat() * 6.28f, (Random.nextFloat() - 0.5f) * 2.2f, s, 1f, Random.nextFloat() * 6.28f, if (falls) 12f else 4f + Random.nextFloat() * 4f, ty, c))
        }
    }
    fun update(dt: Float, w: Float, h: Float, dp: Float) {
        val it = list.iterator()
        while (it.hasNext()) {
            val p = it.next(); p.l -= dt / p.life
            if (p.l <= 0f || p.y > h * 0.98f || p.y < -60f) { it.remove(); continue }
            p.ph += dt; p.x += (p.vx + sin(p.ph * 1.3f) * 10f * dp) * dt; p.y += p.vy * dt; p.r += p.vr * dt
            if (p.vy < 0f) p.vy += 40f * dp * dt
        }
    }
    fun draw(d: DrawScope) {
        list.forEach { p ->
            val tw = if (p.type == AePType.STAR || p.type == AePType.DUST) 0.5f + 0.5f * sin(p.ph * 2.4f) else 1f
            val a = (min(p.l * 3f, 1f) * tw).coerceIn(0f, 1f)
            val c = Offset(p.x, p.y)
            if (p.type == AePType.PETAL || p.type == AePType.LEAF) {
                val k = if (p.type == AePType.LEAF) 0.38f else 0.6f
                d.rotate(p.r * 57.3f, c) {
                    drawOval(p.color.copy(alpha = a * 0.45f), Offset(p.x - p.s, p.y - p.s * k), Size(2f * p.s, 2f * p.s * k))
                    drawOval(Color.White.copy(alpha = a * 0.15f), Offset(p.x - p.s * 0.6f, p.y - p.s * k * 0.55f), Size(p.s * 0.9f, p.s * k * 0.4f))
                }
            } else {
                val rad = p.s * 3.2f
                d.drawCircle(Brush.radialGradient(listOf(p.color.copy(alpha = a * (if (p.type == AePType.DUST) 0.25f else 0.4f)), Color.Transparent), c, rad), rad, c, blendMode = BlendMode.Plus)
                if (p.type == AePType.STAR || p.type == AePType.NEON) {
                    d.drawRect(Color.White.copy(alpha = a * 0.5f), Offset(p.x - p.s * 0.2f, p.y - p.s * 2.4f), Size(p.s * 0.4f, p.s * 4.8f))
                    d.drawRect(Color.White.copy(alpha = a * 0.5f), Offset(p.x - p.s * 2.4f, p.y - p.s * 0.2f), Size(p.s * 4.8f, p.s * 0.4f))
                }
            }
        }
    }
}

/**
 * The scene behind both instruments. [procedural] = false: a photo scene (piano) cross-faded between models.
 * [procedural] = true: animated sky + water with the floating instruments reflected (strings).
 * Android 13+: AGSL shader (refraction, specular, reflection). Older: a Canvas version with the same look, simpler.
 */
class AeSceneRuntime(val procedural: Boolean) {
    val waves = AeWaves(); val particles = AeParticles()
    var frame by mutableIntStateOf(0)
    var w = 0; var h = 0; var dp = 1f
    var t = 0f; var parX = 0f; var parY = 0f
    private var bgA: ImageBitmap? = null; private var bgB: ImageBitmap? = null; private var mixAB = 0f; private var mixGo = false
    /** sk0 sk1 sk2 glow wcol (3 each) + sun (x, y, intensity, falloff) */
    val cur = floatArrayOf(.6f, .78f, .95f, .86f, .88f, .97f, 1f, .86f, .86f, 1f, .8f, .7f, .55f, .72f, .86f, .62f, .4f, .9f, 5f)
    private var target = cur.copyOf()
    private val heightBmp: Bitmap = Bitmap.createBitmap(AeWaves.GW, AeWaves.GH, Bitmap.Config.ARGB_8888)
    private val blank: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    var instBmp: Bitmap? = null; var instCanvas: android.graphics.Canvas? = null
    private var rs: Any? = null
    private val bgShaders = HashMap<Bitmap, Shader>()

    fun resize(width: Int, height: Int, density: Float) {
        if (width == w && height == h) return
        w = width; h = height; dp = density
        if (procedural && width > 8 && height > 8) { val b = Bitmap.createBitmap(width / 2, height / 2, Bitmap.Config.ARGB_8888); instBmp = b; instCanvas = android.graphics.Canvas(b) }
    }
    fun setBackground(img: ImageBitmap?) {
        if (bgA == null) { bgA = img; bgB = img; mixAB = 0f } else { if (mixAB > 0f && mixAB < 1f) bgA = bgB; bgB = img; mixAB = 0f; mixGo = true }
    }
    /** 19 floats: sk0, sk1, sk2, glow, wcol, sun(x, y, intensity, falloff). */
    fun setPalette(p: FloatArray) { target = p }
    fun update(dt: Float, layerDraw: ((android.graphics.Canvas, Int, Int, Float) -> Unit)? = null) {
        t += dt
        waves.ambient(dt); waves.step(); waves.step(); waves.tickRings(dt)
        heightBmp.setPixels(waves.toPixels(), 0, AeWaves.GW, 0, 0, AeWaves.GW, AeWaves.GH)
        if (mixGo && mixAB < 1f) { mixAB = min(1f, mixAB + dt * 1.1f); if (mixAB >= 1f) { bgA = bgB; mixAB = 0f; mixGo = false } }
        val a = 1f - exp(-dt * 1.8f); for (i in cur.indices) cur[i] += (target[i] - cur[i]) * a
        particles.update(dt, w.toFloat(), h.toFloat(), dp)
        val ib = instBmp; val ic = instCanvas
        if (layerDraw != null && ib != null && ic != null) { ib.eraseColor(0); layerDraw(ic, ib.width, ib.height, t) }
        frame++
    }
    fun draw(d: DrawScope) { if (Build.VERSION.SDK_INT >= 33) drawAgsl(d) else drawFallback(d) }

    private fun bgShader(img: ImageBitmap?): Shader {
        val b = img?.asAndroidBitmap() ?: blank
        return bgShaders.getOrPut(b) { BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { if (Build.VERSION.SDK_INT >= 33) it.filterMode = BitmapShader.FILTER_MODE_LINEAR } }
    }
    @RequiresApi(33)
    private fun drawAgsl(d: DrawScope) {
        if (w < 8 || h < 8) return
        val s = (rs as? RuntimeShader) ?: RuntimeShader(AE_AGSL).also { rs = it }
        val hs = BitmapShader(heightBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { it.filterMode = BitmapShader.FILTER_MODE_LINEAR; it.setLocalMatrix(Matrix().apply { setScale(w / AeWaves.GW.toFloat(), h / AeWaves.GH.toFloat()) }) }
        val ib = instBmp ?: blank
        val ish = BitmapShader(ib, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { it.filterMode = BitmapShader.FILTER_MODE_LINEAR; it.setLocalMatrix(Matrix().apply { setScale(w / ib.width.toFloat(), h / ib.height.toFloat()) }) }
        val a = bgA?.asAndroidBitmap() ?: blank; val b = bgB?.asAndroidBitmap() ?: blank
        s.setInputShader("tA", bgShader(bgA)); s.setInputShader("tB", bgShader(bgB)); s.setInputShader("tH", hs); s.setInputShader("tI", ish)
        s.setFloatUniform("res", w.toFloat(), h.toFloat()); s.setFloatUniform("imgA", a.width.toFloat(), a.height.toFloat()); s.setFloatUniform("imgB", b.width.toFloat(), b.height.toFloat())
        s.setFloatUniform("par", parX, parY); s.setFloatUniform("t", t); s.setFloatUniform("mixAB", mixAB); s.setFloatUniform("mode", if (procedural) 1f else 0f)
        s.setFloatUniform("wlImg", 0.58f); s.setFloatUniform("wlS", 0.70f); s.setFloatUniform("cloud", 0.75f)
        s.setFloatUniform("sk0", cur[0], cur[1], cur[2]); s.setFloatUniform("sk1", cur[3], cur[4], cur[5]); s.setFloatUniform("sk2", cur[6], cur[7], cur[8])
        s.setFloatUniform("glow", cur[9], cur[10], cur[11]); s.setFloatUniform("wcol", cur[12], cur[13], cur[14]); s.setFloatUniform("sun", cur[15], cur[16], cur[17], cur[18])
        d.drawRect(ShaderBrush(s), size = d.size)
    }
    private fun drawFallback(d: DrawScope) {
        val W = d.size.width; val H = d.size.height
        if (!procedural) {
            fun cover(img: ImageBitmap?, alpha: Float) {
                if (img == null) return
                val sc = max(W / img.width, H / img.height); val dw = img.width * sc; val dh = img.height * sc
                d.drawImage(img, dstOffset = IntOffset(((W - dw) / 2f).roundToInt(), ((H - dh) / 2f).roundToInt()), dstSize = IntSize(dw.roundToInt(), dh.roundToInt()), alpha = alpha)
            }
            cover(bgA, 1f); if (mixAB > 0f) cover(bgB, mixAB)
        } else {
            val wl = H * 0.70f
            d.drawRect(Brush.verticalGradient(listOf(Color(cur[0], cur[1], cur[2]), Color(cur[3], cur[4], cur[5]), Color(cur[6], cur[7], cur[8])), 0f, wl), size = Size(W, wl))
            d.drawRect(Brush.verticalGradient(listOf(Color(cur[12], cur[13], cur[14]).copy(alpha = 0.9f), Color(cur[12] * 0.7f, cur[13] * 0.7f, cur[14] * 0.7f)), wl, H), Offset(0f, wl), Size(W, H - wl))
            instBmp?.let { ib ->
                val im = ib.asImageBitmap()
                d.drawImage(im, dstOffset = IntOffset.Zero, dstSize = IntSize(W.roundToInt(), H.roundToInt()))
                d.withTransform({ scale(1f, -1f, Offset(0f, wl)) }) { drawImage(im, dstOffset = IntOffset(0, 0), dstSize = IntSize(W.roundToInt(), H.roundToInt()), alpha = 0.3f) }
            }
        }
        waves.rings.forEach { r ->
            val rx = r.age * 0.12f * W; val a = (1f - r.age / 3f).coerceIn(0f, 1f) * 0.22f * r.amp.coerceAtMost(1.2f)
            d.drawOval(Color.White.copy(alpha = a), Offset(r.x * W - rx, r.y * H - rx * 0.28f), Size(2f * rx, 2f * rx * 0.28f), style = Stroke(1.4f * dp))
        }
    }
}

const val AE_AGSL = """
uniform shader tA; uniform shader tB; uniform shader tH; uniform shader tI;
uniform float2 res; uniform float2 imgA; uniform float2 imgB; uniform float2 par;
uniform float t; uniform float mixAB; uniform float mode; uniform float wlImg; uniform float wlS; uniform float cloud;
uniform float3 sk0; uniform float3 sk1; uniform float3 sk2; uniform float3 glow; uniform float3 wcol; uniform float4 sun;
float hash(float2 p){ return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float nz(float2 p){ float2 i = floor(p); float2 f = fract(p); f = f*f*(3.0-2.0*f);
  return mix(mix(hash(i), hash(i+float2(1.0,0.0)), f.x), mix(hash(i+float2(0.0,1.0)), hash(i+float2(1.0,1.0)), f.x), f.y); }
float fbm(float2 p){ float a = 0.5; float s = 0.0; for (int i = 0; i < 4; i++) { s += a*nz(p); p *= 2.03; a *= 0.5; } return s; }
float2 cov(float2 uv, float2 im){ float s = max(res.x/im.x, res.y/im.y); return (uv-0.5)*res/(im*s)+0.5; }
float H(float2 uv){ return float(tH.eval(uv*res).r) - 0.5; }
float2 slope(float2 uv){ float2 e = float2(1.0/128.0, 1.0/72.0);
  return float2(H(uv+float2(e.x,0.0)) - H(uv-float2(e.x,0.0)), H(uv+float2(0.0,e.y)) - H(uv-float2(0.0,e.y))); }
float3 skyc(float2 uv){
  float y = clamp(uv.y/wlS, 0.0, 1.0);
  float3 c = mix(sk0, sk1, smoothstep(0.0, 0.6, y));
  c = mix(c, sk2, smoothstep(0.5, 1.0, y));
  float2 q = float2(uv.x*res.x/res.y*1.6 + t*0.012, uv.y*3.2);
  float cl = fbm(q + fbm(q*0.7 + t*0.01)*1.4);
  c = mix(c, float3(1.0), smoothstep(0.5, 0.85, cl)*cloud*(1.0-y*0.4));
  float2 sp = (uv - sun.xy)*float2(res.x/res.y, 1.0);
  c += glow*exp(-dot(sp,sp)*sun.w)*sun.z*0.5;
  c += glow*0.025*(sin((uv.x+t*0.004)*11.0)*0.5+0.5)*(1.0-y);
  return c; }
float3 imageScene(float2 uv){
  float2 g = slope(uv);
  float2 uA = cov(uv, imgA) + par*0.012; float2 uB = cov(uv, imgB) + par*0.012;
  float m = smoothstep(wlImg-0.01, wlImg+0.04, uA.y);
  float hw = 0.19 + (uA.y - wlImg)*1.25;
  m *= 1.0 - smoothstep(hw-0.03, hw+0.03, abs(uA.x-0.5));
  float2 d = g*0.035*m + float2(sin(uA.y*95.0+t*1.2), cos(uA.x*70.0+t))*0.00035*m;
  float3 c = mix(float3(tA.eval((uA+d)*imgA).rgb), float3(tB.eval((uB+d)*imgB).rgb), mixAB);
  float3 n = normalize(float3(-g*14.0, 1.0));
  float sp = pow(max(dot(normalize(float3(0.35,0.55,0.75)), n), 0.0), 70.0)*m*smoothstep(0.0, 0.04, length(g));
  return c + float3(1.0,0.96,0.88)*sp*0.25; }
float3 procScene(float2 uv){
  if (uv.y < wlS) return skyc(uv);
  float2 g = slope(uv); float yy = (uv.y-wlS)/(1.0-wlS);
  float2 d = g*0.03 + float2(sin(uv.y*120.0+t*1.3), cos(uv.x*80.0+t*0.8))*0.0006*(0.3+yy);
  float3 rs = skyc(float2(uv.x+d.x, clamp(wlS-(uv.y-wlS)+d.y, 0.0, wlS)));
  float3 c = mix(wcol, rs, 0.35+0.4*(1.0-yy)); c *= 1.0-0.25*yy;
  half4 R = tI.eval(float2(uv.x+d.x*0.7, clamp(2.0*wlS-uv.y+d.y*0.7, 0.0, 1.0))*res);
  float ra = float(R.a); float k = 0.45*(1.0-yy*0.75);
  c = mix(c, float3(R.rgb)/max(ra, 0.001), ra*k);
  float3 n = normalize(float3(-g*14.0, 1.0));
  float sp = pow(max(dot(normalize(float3(0.3,0.5,0.8)), n), 0.0), 70.0)*smoothstep(0.0, 0.04, length(g));
  c += glow*sp*0.3;
  return mix(c, sk2, (1.0 - smoothstep(0.0, 0.06, uv.y-wlS))*0.5); }
half4 main(float2 p){
  float2 uv = p/res; float3 c;
  if (mode < 0.5) { c = imageScene(uv); } else { c = procScene(uv); half4 I = tI.eval(p); c = float3(I.rgb) + c*(1.0-float(I.a)); }
  c *= 1.0 - 0.18*pow(length(uv-0.5)*1.25, 2.0);
  return half4(half3(c), 1.0); }
"""
