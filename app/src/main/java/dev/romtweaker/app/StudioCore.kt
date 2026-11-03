package dev.romtweaker.app

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.ViewGroup
import android.widget.TextView
import java.io.File
import java.util.Calendar

/** Configuración del reloj del editor estilo HyperOS (se guarda como texto clave=valor). */
class StudioCfg {
    var layout = "stack"
    var wght = 500
    var wdth = 90
    var rond = 0
    var slnt = 0
    var fx = "glass"
    var tr = 40
    var color = 0xFFFFFFFF.toInt()
    var size = 170
    var top = 110f
    var cx = 0f
    var sc = 100f
    var date = true
    var font = "flex"
    var ol = 0
    var ic = 0          // 0 = reloj propio, 1..60 = reloj de Iconify
    var c2 = 0          // segundo color (degradado); 0 = ninguno
    var glow = 0        // brillo suave 0..100
    var shadow = 0      // sombra 0..100
    var cmode = 0       // 0 manual, 1 adaptar al fondo, 2 Material You, 3 Material You dúo, 5 colores originales (Iconify)
    var xs = 100        // estiramiento horizontal %
    var gap = 10        // separación entre líneas %
    var sig = ""        // firma
    var datef = 0       // formato de fecha

    fun enc(): String = "layout=$layout;wght=$wght;wdth=$wdth;rond=$rond;slnt=$slnt;fx=$fx;tr=$tr;color=$color;size=$size;top=$top;cx=$cx;sc=$sc;date=${if (date) 1 else 0};font=$font;ol=$ol;ic=$ic;c2=$c2;glow=$glow;shadow=$shadow;cmode=$cmode;xs=$xs;gap=$gap;datef=$datef;sig=" + java.net.URLEncoder.encode(sig, "UTF-8")
    fun copy(): StudioCfg = parse(enc())

    /** Copia solo el estilo (no la posición, escala, firma ni formato de fecha). */
    fun styleFrom(p: StudioCfg) {
        layout = p.layout; wght = p.wght; wdth = p.wdth; rond = p.rond; slnt = p.slnt; fx = p.fx; tr = p.tr; color = p.color
        size = p.size; font = p.font; ol = p.ol; ic = p.ic; c2 = p.c2; glow = p.glow; shadow = p.shadow; cmode = p.cmode; xs = p.xs; gap = p.gap
    }

    companion object {
        fun parse(s: String?): StudioCfg {
            val c = StudioCfg()
            if (s.isNullOrEmpty()) return c
            for (kv in s.split(";")) {
                val i = kv.indexOf('=')
                if (i < 0) continue
                val k = kv.substring(0, i)
                val v = kv.substring(i + 1)
                try {
                    when (k) {
                        "layout" -> c.layout = v
                        "wght" -> c.wght = v.toInt()
                        "wdth" -> c.wdth = v.toInt()
                        "rond" -> c.rond = v.toInt()
                        "slnt" -> c.slnt = v.toInt()
                        "fx" -> c.fx = v
                        "tr" -> c.tr = v.toInt()
                        "color" -> c.color = v.toInt()
                        "size" -> c.size = v.toInt()
                        "top" -> c.top = v.toFloat()
                        "cx" -> c.cx = v.toFloat()
                        "sc" -> c.sc = v.toFloat()
                        "date" -> c.date = v == "1"
                        "font" -> c.font = v
                        "ol" -> c.ol = v.toInt()
                        "ic" -> c.ic = v.toInt()
                        "c2" -> c.c2 = v.toInt()
                        "glow" -> c.glow = v.toInt()
                        "shadow" -> c.shadow = v.toInt()
                        "cmode" -> c.cmode = v.toInt()
                        "xs" -> c.xs = v.toInt()
                        "gap" -> c.gap = v.toInt()
                        "datef" -> c.datef = v.toInt()
                        "sig" -> c.sig = java.net.URLDecoder.decode(v, "UTF-8")
                    }
                } catch (e: Throwable) { }
            }
            return c
        }

        fun load(ctx: Context): StudioCfg = parse(ctx.getSharedPreferences("lock", Context.MODE_PRIVATE).getString("sc_cfg", ""))
    }
}

object StudioFont {
    private var tf: Typeface? = null
    private val assets = HashMap<String, Typeface?>()
    const val APP = "dev.romtweaker.app"
    val varFonts = setOf("antonio", "bigshoulders", "bodoni", "caveat", "syne")
    val assetFonts = listOf("anton", "antonio", "bigshoulders", "bodoni", "italiana", "caveat", "dmserif", "syne", "bebas")

    private fun supports(t: Typeface): Boolean {
        val q = Paint()
        q.typeface = t
        return try { q.setFontVariationSettings("'wght' 400,'wdth' 85") } catch (e: Throwable) { false }
    }

    /** Fuentes incluidas en la app (assets/fonts/<nombre>.ttf, solo dígitos). También funciona desde SystemUI. */
    fun asset(ctx: Context, name: String): Typeface? {
        if (assets.containsKey(name)) return assets[name]
        var t: Typeface? = null
        try {
            val am = if (ctx.packageName == APP) ctx.assets else ctx.createPackageContext(APP, 0).assets
            t = Typeface.createFromAsset(am, "fonts/$name.ttf")
        } catch (e: Throwable) { }
        assets[name] = t
        return t
    }

    fun get(): Typeface {
        tf?.let { return it }
        var r: Typeface = Typeface.DEFAULT
        var found = false
        for (path in listOf("/system/fonts/GoogleSansFlex-Regular.ttf", "/system_ext/fonts/GoogleSansFlex-Regular.ttf", "/product/fonts/GoogleSansFlex-Regular.ttf")) {
            try {
                val f = File(path)
                if (f.exists()) {
                    val t = Typeface.createFromFile(f)
                    if (supports(t)) { r = t; found = true; break }
                }
            } catch (e: Throwable) { }
        }
        if (!found) {
            for (fam in listOf("google-sans-flex-clock", "google-sans-flex", "google-sans-clock", "google-sans", "sans-serif")) {
                val t = Typeface.create(fam, Typeface.NORMAL)
                if (supports(t)) { r = t; break }
            }
        }
        tf = r
        return r
    }
}

object StudioWall {
    private var bmp: Bitmap? = null
    private var tried = false

    fun reset() { bmp = null; tried = false }

    fun get(ctx: Context): Bitmap? {
        if (bmp != null || tried) return bmp
        tried = true
        try {
            val wm = WallpaperManager.getInstance(ctx)
            fun file(which: Int): android.os.ParcelFileDescriptor? = try {
                wm.javaClass.getMethod("getWallpaperFile", Int::class.javaPrimitiveType).invoke(wm, which) as? android.os.ParcelFileDescriptor
            } catch (e: Throwable) { null }
            var pfd = file(WallpaperManager.FLAG_LOCK)
            if (pfd == null) pfd = file(WallpaperManager.FLAG_SYSTEM)
            if (pfd != null) {
                pfd.use {
                    val o = BitmapFactory.Options()
                    o.inJustDecodeBounds = true
                    BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, o)
                    var ss = 1
                    while (maxOf(o.outWidth, o.outHeight) / ss > 600) ss *= 2
                    val o2 = BitmapFactory.Options()
                    o2.inSampleSize = ss
                    bmp = BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, o2)
                }
            }
            if (bmp == null) {
                val d = wm.drawable
                if (d != null && d.intrinsicWidth > 0) {
                    val s = 600f / maxOf(d.intrinsicWidth, d.intrinsicHeight)
                    val w = maxOf(1, (d.intrinsicWidth * s).toInt())
                    val h = maxOf(1, (d.intrinsicHeight * s).toInt())
                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    d.setBounds(0, 0, w, h)
                    d.draw(Canvas(b))
                    bmp = b
                }
            }
        } catch (e: Throwable) { }
        return bmp
    }
}


/** Colores finales del reloj: manual, adaptado al fondo o Material You (colores del sistema). */
object StudioColor {
    private fun hsv(h: Float, s: Float, v: Float): Int = Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

    private fun sys(ctx: Context, id: Int, fb: Int): Int = try { ctx.resources.getColor(id, null) } catch (e: Throwable) { fb }

    fun wallTone(ctx: Context): Pair<Int, Int> {
        val b = StudioWall.get(ctx) ?: return Pair(Color.WHITE, 0)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        val y0 = (b.height * 0.08f).toInt(); val y1 = (b.height * 0.45f).toInt().coerceAtLeast(y0 + 1)
        val st = maxOf(1, b.width / 24)
        var y = y0
        while (y < y1 && y < b.height) {
            var x = 0
            while (x < b.width) { val p = b.getPixel(x, y); r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++; x += st }
            y += maxOf(1, (y1 - y0) / 16)
        }
        if (n == 0L) return Pair(Color.WHITE, 0)
        val avg = Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
        val lum = (0.299 * Color.red(avg) + 0.587 * Color.green(avg) + 0.114 * Color.blue(avg)) / 255.0
        val h = FloatArray(3)
        Color.colorToHSV(avg, h)
        return if (lum > 0.55) Pair(hsv(h[0], minOf(0.55f, h[1] + 0.25f), 0.16f), hsv(h[0] + 22f, minOf(0.6f, h[1] + 0.3f), 0.30f))
        else Pair(hsv(h[0], h[1] * 0.30f, 0.98f), hsv(h[0] + 22f, h[1] * 0.45f + 0.1f, 0.95f))
    }

    /** (color1, color2); color2 == 0 significa sin degradado. */
    fun resolve(ctx: Context, c: StudioCfg): Pair<Int, Int> = when (c.cmode) {
        1 -> wallTone(ctx).let { Pair(it.first, if (c.c2 != 0) it.second else 0) }
        2 -> Pair(sys(ctx, android.R.color.system_accent1_100, c.color), 0)
        3 -> Pair(sys(ctx, android.R.color.system_accent1_200, c.color), sys(ctx, android.R.color.system_accent3_200, c.c2))
        else -> Pair(c.color, c.c2)
    }
}

/** Efecto de render (Cristal / Mezcla / Superposición) sobre cualquier vista (dígitos o reloj Iconify). */
object StudioFx {
    private val blank: Bitmap by lazy { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }
    const val AGSL = DigitsView.AGSL
    var lastError: String? = null

    fun needs(c: StudioCfg) = c.fx == "glass" || c.fx == "blend" || c.fx == "overlay"

    fun apply(v: View, cfg: StudioCfg, c1: Int, c2: Int) {
        if (Build.VERSION.SDK_INT < 33) return
        v.post {
            try {
                if (!needs(cfg)) { v.setRenderEffect(null); return@post }
                if (v.width == 0 || v.height == 0) return@post
                val rs = RuntimeShader(AGSL)
                val t = cfg.tr / 100f
                val mode = when (cfg.fx) { "glass" -> 1f; "blend" -> 2f; "overlay" -> 3f; else -> 0f }
                var a1 = 1f
                var a2 = 1f
                if (mode == 1f) { a1 = 0.9f * (1f - t) + 0.1f; a2 = 0.5f * (1f - t) + 0.05f }
                val e2 = if (c2 != 0) c2 else c1
                rs.setFloatUniform("h", v.height.toFloat())
                rs.setFloatUniform("mode", mode)
                rs.setFloatUniform("tr", t)
                rs.setFloatUniform("c1", Color.red(c1) / 255f, Color.green(c1) / 255f, Color.blue(c1) / 255f, a1)
                rs.setFloatUniform("c2", Color.red(e2) / 255f, Color.green(e2) / 255f, Color.blue(e2) / 255f, a2)
                val wb = StudioWall.get(v.context)
                val sh = BitmapShader(wb ?: blank, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                if (wb != null) {
                    val loc = IntArray(2)
                    v.getLocationOnScreen(loc)
                    val dm = v.resources.displayMetrics
                    val s = maxOf(dm.widthPixels / wb.width.toFloat(), dm.heightPixels / wb.height.toFloat())
                    val mx = Matrix()
                    mx.setScale(s, s)
                    mx.postTranslate((dm.widthPixels - wb.width * s) / 2f - loc[0], (dm.heightPixels - wb.height * s) / 2f - loc[1])
                    sh.setLocalMatrix(mx)
                }
                rs.setInputShader("wall", sh)
                rs.setFloatUniform("hasWall", if (wb != null) 1f else 0f)
                v.setRenderEffect(RenderEffect.createRuntimeShaderEffect(rs, "inp"))
            } catch (e: Throwable) {
                lastError = e.toString()
                try { android.util.Log.w("RomTweaker", "fx: shader no válido, uso sólido: $e") } catch (x: Throwable) { }
                try { v.setRenderEffect(null) } catch (x: Throwable) { }
            }
        }
    }
}

/** Dibuja solo los dígitos (con el efecto del shader); la fecha va aparte para que no se vea afectada. */
class DigitsView(ctx: Context) : View(ctx) {
    var cfg = StudioCfg()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var hh = "00"
    private var mm = "00"
    private var capH = 0f
    var pad = 0f
        private set
    private var rawW = 0f

    companion object {
        const val AGSL = """
            uniform shader inp;
            uniform shader wall;
            uniform float h;
            uniform half4 c1;
            uniform half4 c2;
            uniform float mode;
            uniform float tr;
            uniform float hasWall;
            half4 main(float2 p) {
                half4 s = inp.eval(p);
                float t = clamp(p.y / h, 0.0, 1.0);
                half4 c = mix(c1, c2, half(t));
                float a = s.a * c.a;
                half3 rgb = c.rgb;
                if (mode > 0.5 && mode < 1.5) {
                    float d = 2.5;
                    float au = inp.eval(p + float2(0.0, -d)).a;
                    float ad = inp.eval(p + float2(0.0, d)).a;
                    float al = inp.eval(p + float2(-d, 0.0)).a;
                    float ar = inp.eval(p + float2(d, 0.0)).a;
                    float rim = clamp(s.a - min(min(au, ad), min(al, ar)), 0.0, 1.0);
                    float top = clamp(s.a - au, 0.0, 1.0);
                    float bot = clamp(s.a - ad, 0.0, 1.0);
                    rgb = rgb + (half3(1.0) - rgb) * half(0.85 * rim * (0.4 + 0.6 * top));
                    rgb = rgb * half(1.0 - 0.35 * bot);
                    a = max(a, rim * 0.8);
                } else if (mode > 1.5 && mode < 2.5) {
                    half3 w = c.rgb;
                    if (hasWall > 0.5) { w = wall.eval(p).rgb; }
                    rgb = half3(clamp(w * 1.3 + half3(0.10), 0.0, 1.0));
                    a = s.a * (1.0 - 0.7 * tr);
                } else if (mode > 2.5) {
                    a = s.a * 0.7 * (1.0 - tr);
                }
                return half4(rgb * a, a);
            }
        """
    }

    private fun px(): Float {
        val d = resources.displayMetrics.density
        return cfg.size * d * cfg.sc / 100f * (when (cfg.layout) { "stack", "grid", "diag" -> 1f; "col" -> 0.78f; else -> 0.55f })
    }

    private fun setup(size: Float) {
        p.reset()
        p.isAntiAlias = true
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        val f = cfg.font
        when {
            f == "serif" -> p.typeface = Typeface.create(Typeface.SERIF, cfg.wght.coerceIn(100, 900), false)
            f == "mono" -> p.typeface = Typeface.create(Typeface.MONOSPACE, cfg.wght.coerceIn(100, 900), false)
            f in StudioFont.assetFonts && StudioFont.asset(context, f) != null -> {
                p.typeface = StudioFont.asset(context, f)
                if (f in StudioFont.varFonts) try { p.setFontVariationSettings("'wght' " + cfg.wght) } catch (e: Throwable) { }
            }
            else -> {
                p.typeface = StudioFont.get()
                try { p.setFontVariationSettings("'wght' " + cfg.wght + ",'wdth' " + cfg.wdth + ",'ROND' " + cfg.rond + ",'slnt' " + cfg.slnt) } catch (e: Throwable) { }
            }
        }
        if (cfg.ol == 1) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(2f, size * 0.028f)
            p.strokeJoin = Paint.Join.ROUND
        }
    }

    fun refresh() {
        val cal = Calendar.getInstance()
        var hr = cal.get(Calendar.HOUR_OF_DAY)
        if (!android.text.format.DateFormat.is24HourFormat(context)) { hr %= 12; if (hr == 0) hr = 12 }
        val a = String.format("%02d", hr)
        val b = String.format("%02d", cal.get(Calendar.MINUTE))
        if (a != hh || b != mm) { hh = a; mm = b; requestLayout(); invalidate() }
    }

    init { refresh() }

    fun inner(): RectF = RectF(pad * 0.55f, pad * 0.55f, width - pad * 0.55f, height - pad * 0.55f)

    private fun dw(): Float { var w = 0f; for (ch in (hh + mm)) w = maxOf(w, p.measureText(ch.toString())); return w }

    override fun onMeasure(ws: Int, hs: Int) {
        val size = px()
        setup(size)
        val r = Rect()
        p.getTextBounds("0", 0, 1, r)
        capH = r.height().toFloat()
        pad = size * (0.10f + cfg.glow / 100f * 0.22f + cfg.shadow / 100f * 0.06f)
        val g = cfg.gap / 100f
        var w: Float
        val h: Float
        when (cfg.layout) {
            "stack" -> { w = maxOf(p.measureText(hh), p.measureText(mm)); h = capH * (2f + g) }
            "col" -> { w = dw(); h = capH * (4f + 3f * g) }
            "grid" -> { w = dw() * 2.3f; h = capH * (2f + g) + capH * 0.32f }
            "diag" -> { w = maxOf(p.measureText(hh), p.measureText(mm)) * 1.65f; h = capH * (2f + g) }
            else -> { w = p.measureText("$hh:$mm"); h = capH }
        }
        rawW = w + 2 * pad
        setMeasuredDimension((rawW * cfg.xs / 100f).toInt() + 1, (h + 2 * pad).toInt() + 1)
    }

    override fun onDraw(c: Canvas) {
        setup(px())
        val cols = StudioColor.resolve(context, cfg)
        val c1 = cols.first
        val c2 = cols.second
        val solid = !StudioFx.needs(cfg)
        if (solid) {
            p.color = c1
            if (c2 != 0) p.shader = LinearGradient(0f, pad, 0f, height - pad, c1, c2, Shader.TileMode.CLAMP)
            val sz = p.textSize
            if (cfg.glow > 0) p.setShadowLayer(sz * cfg.glow / 100f * 0.2f + 1f, 0f, 0f, c1)
            else if (cfg.shadow > 0) { val rr = sz * cfg.shadow / 100f * 0.06f + 1f; p.setShadowLayer(rr, 0f, rr * 0.4f, 0x99000000.toInt()) }
        }
        c.save()
        c.translate(width / 2f, 0f)
        c.scale(cfg.xs / 100f, 1f)
        c.translate(-rawW / 2f, 0f)
        val x = rawW / 2f
        val g = cfg.gap / 100f
        val y1 = pad + capH
        when (cfg.layout) {
            "stack" -> {
                c.drawText(hh, x, y1, p)
                c.drawText(mm, x, y1 + capH * (1f + g), p)
            }
            "col" -> {
                val all = hh + mm
                for (i in 0 until 4) c.drawText(all.substring(i, i + 1), x, y1 + i * capH * (1f + g), p)
            }
            "grid" -> {
                val d = dw() * 1.15f
                val x0 = x - d / 2f
                val x1 = x + d / 2f
                c.drawText(hh.substring(0, 1), x0, y1, p)
                c.drawText(hh.substring(1, 2), x1, y1 + capH * 0.32f, p)
                c.drawText(mm.substring(0, 1), x0, y1 + capH * (1f + g), p)
                c.drawText(mm.substring(1, 2), x1, y1 + capH * (1f + g) + capH * 0.32f, p)
            }
            "diag" -> {
                val m = maxOf(p.measureText(hh), p.measureText(mm))
                c.drawText(hh, pad + m / 2f, y1, p)
                c.drawText(mm, rawW - pad - m / 2f, y1 + capH * (1f + g), p)
            }
            else -> c.drawText("$hh:$mm", x, y1, p)
        }
        c.restore()
    }

    fun applyFx() {
        val cols = StudioColor.resolve(context, cfg)
        if (!StudioFx.needs(cfg)) { if (Build.VERSION.SDK_INT >= 31) post { try { setRenderEffect(null) } catch (e: Throwable) { } }; return }
        StudioFx.apply(this, cfg, cols.first, cols.second)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        applyFx()
    }
}

/** Reloj completo: fecha encima + dígitos (o reloj de Iconify) + firma. Se usa igual en el editor y dentro de SystemUI. */
class StudioClock(ctx: Context) : LinearLayout(ctx) {
    private val dateTv = TextView(ctx)
    private val sigTv = TextView(ctx)
    val digits = DigitsView(ctx)
    private val holder = FrameLayout(ctx)
    var cfg = StudioCfg()
    private var rcv: BroadcastReceiver? = null
    private var holderKey = ""
    var edit = false
        set(v) { field = v; refreshSig(); invalidate() }
    var hot = false
        set(v) { field = v; invalidate() }
    private val op = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cyc = RectF()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)
        dateTv.includeFontPadding = false
        dateTv.typeface = StudioFont.get()
        sigTv.includeFontPadding = false
        sigTv.typeface = StudioFont.get()
        addView(dateTv, LayoutParams(-2, -2))
        addView(digits, LayoutParams(-2, -2))
        addView(holder, LayoutParams(-2, -2))
        addView(sigTv, LayoutParams(-2, -2))
        holder.visibility = GONE
        holder.clipChildren = false
    }

    private fun cap(s: String): String = if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1)

    fun refreshDate() {
        val f = when (cfg.datef) { 1 -> "d 'de' MMMM"; 2 -> "EEEE d"; 3 -> "d/M/yyyy"; else -> "EEE, d 'de' MMM" }
        dateTv.text = cap(android.text.format.DateFormat.format(f, Calendar.getInstance()).toString())
    }

    private fun refreshSig() {
        val show = cfg.sig.isNotEmpty() || edit
        sigTv.visibility = if (show) VISIBLE else GONE
        sigTv.text = if (cfg.sig.isNotEmpty()) cfg.sig else "Toca para añadir firma"
        sigTv.alpha = if (cfg.sig.isNotEmpty()) 1f else 0.55f
    }

    private fun cap2(s: String): String {
        val sb = StringBuilder(s.length)
        var up = true
        for (ch in s) { sb.append(if (up && ch.isLetter()) ch.uppercaseChar() else ch); up = ch.isWhitespace() || ch == '\n' }
        return sb.toString()
    }
    private fun titleAll(v: View) {
        if (v is TextView) {
            val fix = { t: TextView -> val s = t.text?.toString() ?: ""; if (s.isNotEmpty()) { val u = cap2(s); if (u != s) t.text = u } }
            fix(v)
            v.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun afterTextChanged(e: android.text.Editable?) { fix(v) }
            })
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) titleAll(v.getChildAt(i))
    }

    private fun tintAll(v: View, col: Int, glow: Float) {
        if (v is TextView) { v.setTextColor(col); if (glow > 0f) v.setShadowLayer(glow, 0f, 0f, col) else v.setShadowLayer(0f, 0f, 0f, 0) }
        if (v is ImageView) try { v.setColorFilter(col) } catch (e: Throwable) { }
        if (v is ViewGroup) for (i in 0 until v.childCount) tintAll(v.getChildAt(i), col, glow)
    }

    private fun buildHolder(c: StudioCfg) {
        holder.removeAllViews()
        holderKey = "" + c.ic
        try {
            val pkg = if (context.packageName == StudioFont.APP) context else context.createPackageContext(StudioFont.APP, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
            val id = pkg.resources.getIdentifier("preview_lockscreen_clock_" + c.ic, "layout", StudioFont.APP)
            if (id == 0) return
            val v = android.view.LayoutInflater.from(pkg).inflate(id, null)
            if (c.ic != 10) titleAll(v)
            holder.addView(v, FrameLayout.LayoutParams(-2, -2))
        } catch (e: Throwable) { try { android.util.Log.w("RomTweaker", "ic: holder $e") } catch (x: Throwable) { } }
    }

    fun applyConfig(c: StudioCfg) {
        cfg = c
        digits.cfg = c
        val cols = StudioColor.resolve(context, c)
        val ic = c.ic > 0
        digits.visibility = if (ic) GONE else VISIBLE
        holder.visibility = if (ic) VISIBLE else GONE
        dateTv.visibility = if (c.date && !ic) VISIBLE else GONE
        dateTv.setTextColor(cols.first)
        dateTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (c.size * c.sc / 100f * 0.10f).coerceIn(11f, 44f))
        try { dateTv.fontVariationSettings = "'wght' 500" } catch (e: Throwable) { }
        sigTv.setTextColor(cols.first)
        sigTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (c.size * c.sc / 100f * 0.085f).coerceIn(10f, 36f))
        refreshSig()
        refreshDate()
        if (ic) {
            if (holderKey != "" + c.ic || holder.childCount == 0) buildHolder(c)
            val k = (c.sc / 100f) * (c.size / 170f)
            holder.pivotX = holder.width / 2f
            holder.pivotY = 0f
            holder.scaleX = k
            holder.scaleY = k
            if (c.cmode != 5 && holder.childCount > 0) tintAll(holder, cols.first, c.glow / 100f * 18f * resources.displayMetrics.density)
            StudioFx.apply(holder, c, cols.first, cols.second)
        } else {
            digits.refresh()
            digits.requestLayout()
            digits.invalidate()
            digits.applyFx()
        }
        invalidate()
    }

    fun applyCfg(s: String) = applyConfig(StudioCfg.parse(s))

    // ----- modo edición: contornos como en HyperOS -----
    fun cycleHit(x: Float, y: Float): Boolean = edit && cyc.contains(x, y)
    fun sigHit(x: Float, y: Float): Boolean = edit && sigTv.visibility == VISIBLE && x >= sigTv.left - 20 && x <= sigTv.right + 20 && y >= sigTv.top - 20 && y <= sigTv.bottom + 20
    fun dateHit(x: Float, y: Float): Boolean = edit && dateTv.visibility == VISIBLE && x >= dateTv.left - 20 && x <= dateTv.right + 20 && y >= dateTv.top - 20 && y <= dateTv.bottom + 20

    override fun dispatchDraw(c: Canvas) {
        super.dispatchDraw(c)
        if (!edit) return
        val d = resources.displayMetrics.density
        op.style = Paint.Style.STROKE
        op.strokeWidth = (if (hot) 2.4f else 1.4f) * d
        op.color = if (hot) 0xFFFFFFFF.toInt() else 0xB3FFFFFF.toInt()
        val ic = cfg.ic > 0
        val r = RectF()
        if (ic) {
            val k = holder.scaleX
            r.set(holder.left + holder.width * (1 - k) / 2f, holder.top.toFloat(), holder.right - holder.width * (1 - k) / 2f, holder.top + holder.height * k)
            r.inset(-6 * d, -6 * d)
        } else {
            val i = digits.inner()
            r.set(digits.left + i.left, digits.top + i.top, digits.left + i.right, digits.top + i.bottom)
        }
        c.drawRoundRect(r, 16 * d, 16 * d, op)
        if (dateTv.visibility == VISIBLE) {
            val q = RectF(dateTv.left - 12 * d, dateTv.top - 4 * d, dateTv.right + 12 * d, dateTv.bottom + 4 * d)
            c.drawRoundRect(q, q.height() / 2f, q.height() / 2f, op)
        }
        if (sigTv.visibility == VISIBLE) {
            val q = RectF(sigTv.left - 12 * d, sigTv.top - 4 * d, sigTv.right + 12 * d, sigTv.bottom + 4 * d)
            c.drawRoundRect(q, q.height() / 2f, q.height() / 2f, op)
        }
        // icono "cambiar disposición" (arco con flecha) en la esquina inferior derecha del marco
        val cx = r.right - 16 * d
        val cy = r.bottom - 16 * d
        cyc.set(cx - 22 * d, cy - 22 * d, cx + 22 * d, cy + 22 * d)
        val fp = Paint(Paint.ANTI_ALIAS_FLAG)
        fp.color = 0x55000000
        c.drawCircle(cx, cy, 13 * d, fp)
        op.strokeCap = Paint.Cap.ROUND
        op.strokeWidth = 1.6f * d
        op.color = 0xFFFFFFFF.toInt()
        val ar = RectF(cx - 6 * d, cy - 6 * d, cx + 6 * d, cy + 6 * d)
        c.drawArc(ar, 200f, 250f, false, op)
        val ang = Math.toRadians(90.0 - 0.0)
        val ex = cx + 6 * d * Math.cos(Math.toRadians(450.0)).toFloat()
        val ey = cy + 6 * d * Math.sin(Math.toRadians(450.0)).toFloat()
        val pa = Path()
        pa.moveTo(ex - 3.2f * d, ey - 2.2f * d); pa.lineTo(ex, ey); pa.lineTo(ex - 3.4f * d, ey + 2.4f * d)
        c.drawPath(pa, op)
        if (ang < 0) return
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { refreshDate(); digits.refresh() }
        }
        rcv = r
        val f = IntentFilter()
        f.addAction(Intent.ACTION_TIME_TICK)
        f.addAction(Intent.ACTION_TIME_CHANGED)
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        try { context.registerReceiver(r, f) } catch (e: Throwable) { }
        refreshDate()
        if (cfg.ic > 0) applyConfig(cfg) else { digits.refresh(); digits.applyFx() }
    }

    override fun onDetachedFromWindow() {
        rcv?.let { try { context.unregisterReceiver(it) } catch (e: Throwable) { } }
        rcv = null
        super.onDetachedFromWindow()
    }
}
