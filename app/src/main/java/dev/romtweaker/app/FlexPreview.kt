package dev.romtweaker.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import java.io.File
import java.util.Calendar

/** Vista previa fiel del reloj Flex de SystemUI: misma fuente variable y mismo shader que el módulo. */
class FlexPreview(ctx: Context) : View(ctx) {
    var cfg = ClockCfg()
    var compact = false
    var metrics: ClockMetrics? = null
    var scale = 1f
    var fontName = "sistema"
    private var tf: Typeface = Typeface.DEFAULT
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    companion object {
        const val AGSL = """
            uniform shader inp;
            uniform float h;
            uniform half4 c1;
            uniform half4 c2;
            uniform float mode;
            uniform float edge;
            half4 main(float2 p) {
                float2 q = p;
                if (mode > 2.5) { q.x += sin(p.y * 0.03) * 7.0; }
                half4 s = inp.eval(q);
                float t = clamp(p.y / h, 0.0, 1.0);
                half4 c = mix(c1, c2, half(t));
                float a = s.a * c.a;
                if (mode > 0.5 && mode < 1.5) { a *= 0.62 + 0.38 * abs(sin(p.x * 0.45)); }
                if (mode > 1.5 && mode < 2.5) { a *= 0.5 + 0.5 * fract(p.x / 16.0); }
                half3 rgb = c.rgb;
                if (edge > 0.5) {
                    float d = 2.5;
                    float au = inp.eval(q + float2(0.0, -d)).a;
                    float ad = inp.eval(q + float2(0.0, d)).a;
                    float al = inp.eval(q + float2(-d, 0.0)).a;
                    float ar = inp.eval(q + float2(d, 0.0)).a;
                    float rim = clamp(s.a - min(min(au, ad), min(al, ar)), 0.0, 1.0);
                    float top = clamp(s.a - au, 0.0, 1.0);
                    float bot = clamp(s.a - ad, 0.0, 1.0);
                    rgb = rgb + (half3(1.0) - rgb) * half(0.85 * rim * (0.4 + 0.6 * top));
                    rgb = rgb * half(1.0 - 0.35 * bot);
                    a = max(a, rim * 0.8);
                }
                return half4(rgb * a, a);
            }
        """
    }

    init { loadFont() }

    private fun supports(t: Typeface): Boolean {
        val q = Paint()
        q.typeface = t
        return try { q.setFontVariationSettings("'wght' 400,'wdth' 85") } catch (e: Throwable) { false }
    }

    private fun loadFont() {
        for (path in listOf("/system/fonts/GoogleSansFlex-Regular.ttf", "/system_ext/fonts/GoogleSansFlex-Regular.ttf", "/product/fonts/GoogleSansFlex-Regular.ttf")) {
            try {
                val f = File(path)
                if (f.exists()) {
                    val t = Typeface.createFromFile(f)
                    if (supports(t)) { tf = t; fontName = f.name; return }
                }
            } catch (e: Throwable) { }
        }
        try {
            val ai = context.packageManager.getApplicationInfo("com.android.systemui", 0)
            val zf = java.util.zip.ZipFile(ai.sourceDir)
            val list = ArrayList<java.util.zip.ZipEntry>()
            val en = zf.entries()
            val re = Regex("(?i)^res/.*\\.(ttf|otf)$")
            while (en.hasMoreElements()) {
                val e = en.nextElement()
                if (re.matches(e.name)) list.add(e)
            }
            list.sortBy { val n = it.name.lowercase(); if (n.contains("flex")) 0 else if (n.contains("clock")) 1 else 2 }
            for (e in list) {
                val f = File(context.cacheDir, "fx_" + e.name.replace('/', '_'))
                if (!f.exists()) zf.getInputStream(e).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                val t = try { Typeface.createFromFile(f) } catch (x: Throwable) { null }
                if (t != null && supports(t)) { tf = t; fontName = e.name; zf.close(); return }
            }
            zf.close()
        } catch (e: Throwable) { }
        for (fam in listOf("google-sans-flex-clock", "google-sans-flex", "google-sans-clock", "google-sans", "sans-serif")) {
            val t = Typeface.create(fam, Typeface.NORMAL)
            if (supports(t)) { tf = t; fontName = fam; return }
        }
        fontName = "sans-serif (sin ejes variables)"
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cal = Calendar.getInstance()
        var hr = cal.get(Calendar.HOUR_OF_DAY)
        if (!android.text.format.DateFormat.is24HourFormat(context)) { hr %= 12; if (hr == 0) hr = 12 }
        val hh = String.format("%02d", hr)
        val mm = String.format("%02d", cal.get(Calendar.MINUTE))
        p.reset()
        p.isAntiAlias = true
        p.typeface = tf
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        try { p.setFontVariationSettings("'wght' " + cfg.weight + ",'wdth' " + cfg.wdth + ",'ROND' " + cfg.rond + ",'slnt' " + cfg.slnt) } catch (e: Throwable) { }
        val mt = metrics
        if (mt != null) {
            val sc = scale
            val cx = (mt.sw / 2f - mt.gx) * sc
            if (compact) {
                p.textSize = mt.sh * 0.101f * sc
                c.drawText(hh + ":" + mm, cx, (mt.sh * 0.143f - mt.gy) * sc, p)
            } else {
                p.textSize = mt.sh * 0.216f * sc
                c.drawText(hh, cx, (mt.sh * 0.482f - mt.gy) * sc, p)
                c.drawText(mm, cx, (mt.sh * 0.653f - mt.gy) * sc, p)
            }
        } else if (compact) {
            p.textSize = h * 0.101f
            c.drawText(hh + ":" + mm, w / 2f, h * 0.143f, p)
        } else {
            p.textSize = h * 0.216f
            c.drawText(hh, w / 2f, h * 0.482f, p)
            c.drawText(mm, w / 2f, h * 0.653f, p)
        }
    }

    fun refreshFx() { invalidate(); applyFx() }

    fun applyFx() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        post {
            try {
                val hh = Math.max(1f, height.toFloat())
                val rs = RuntimeShader(AGSL)
                val glass = cfg.style.startsWith("glass")
                val col = cfg.color
                val c2 = if (glass || cfg.style == "solid") col else cfg.accent
                val k = cfg.glass / 100f
                val a1 = if (glass) 0.95f else 1f
                val a2 = if (glass) 0.30f + 0.5f * (1f - k) else 1f
                rs.setFloatUniform("h", hh)
                rs.setFloatUniform("mode", cfg.filter.toFloat())
                rs.setFloatUniform("edge", if (glass) 1f else 0f)
                rs.setFloatUniform("c1", Color.red(col) / 255f, Color.green(col) / 255f, Color.blue(col) / 255f, a1)
                rs.setFloatUniform("c2", Color.red(c2) / 255f, Color.green(c2) / 255f, Color.blue(c2) / 255f, a2)
                setRenderEffect(RenderEffect.createRuntimeShaderEffect(rs, "inp"))
            } catch (e: Throwable) { }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        applyFx()
    }
}
