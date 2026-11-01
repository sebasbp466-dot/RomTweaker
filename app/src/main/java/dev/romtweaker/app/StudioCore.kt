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
import android.graphics.Matrix
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
import android.widget.LinearLayout
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

    fun enc(): String = "layout=$layout;wght=$wght;wdth=$wdth;rond=$rond;slnt=$slnt;fx=$fx;tr=$tr;color=$color;size=$size;top=$top;cx=$cx;sc=$sc;date=${if (date) 1 else 0}"
    fun copy(): StudioCfg = parse(enc())

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

    private fun supports(t: Typeface): Boolean {
        val q = Paint()
        q.typeface = t
        return try { q.setFontVariationSettings("'wght' 400,'wdth' 85") } catch (e: Throwable) { false }
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

/** Dibuja solo los dígitos (con el efecto del shader); la fecha va aparte para que no se vea afectada. */
class DigitsView(ctx: Context) : View(ctx) {
    var cfg = StudioCfg()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var hh = "00"
    private var mm = "00"
    private var capH = 0f
    private var pad = 0f
    private val blank: Bitmap by lazy { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }

    companion object {
        const val GAP = 0.10f
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
        return cfg.size * d * cfg.sc / 100f * (if (cfg.layout == "stack") 1f else 0.55f)
    }

    private fun setup(size: Float) {
        p.reset()
        p.isAntiAlias = true
        p.typeface = StudioFont.get()
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        try { p.setFontVariationSettings("'wght' " + cfg.wght + ",'wdth' " + cfg.wdth + ",'ROND' " + cfg.rond + ",'slnt' " + cfg.slnt) } catch (e: Throwable) { }
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

    override fun onMeasure(ws: Int, hs: Int) {
        val size = px()
        setup(size)
        val r = Rect()
        p.getTextBounds("0", 0, 1, r)
        capH = r.height().toFloat()
        pad = size * 0.10f
        val stack = cfg.layout == "stack"
        val w = if (stack) maxOf(p.measureText(hh), p.measureText(mm)) else p.measureText("$hh:$mm")
        val h = if (stack) capH * (2f + GAP) else capH
        setMeasuredDimension((w + 2 * pad).toInt() + 1, (h + 2 * pad).toInt() + 1)
    }

    override fun onDraw(c: Canvas) {
        setup(px())
        val x = width / 2f
        if (cfg.layout == "stack") {
            val y1 = pad + capH
            c.drawText(hh, x, y1, p)
            c.drawText(mm, x, y1 + capH * (1f + GAP), p)
        } else {
            c.drawText("$hh:$mm", x, pad + capH, p)
        }
    }

    fun applyFx() {
        if (Build.VERSION.SDK_INT < 33) return
        post {
            try {
                if (width == 0 || height == 0) return@post
                val rs = RuntimeShader(AGSL)
                val col = cfg.color
                val t = cfg.tr / 100f
                val mode = when (cfg.fx) { "glass" -> 1f; "blend" -> 2f; "overlay" -> 3f; else -> 0f }
                var a1 = 1f
                var a2 = 1f
                if (mode == 1f) { a1 = 0.9f * (1f - t) + 0.1f; a2 = 0.5f * (1f - t) + 0.05f }
                val r = Color.red(col) / 255f
                val g = Color.green(col) / 255f
                val b = Color.blue(col) / 255f
                rs.setFloatUniform("h", height.toFloat())
                rs.setFloatUniform("mode", mode)
                rs.setFloatUniform("tr", t)
                rs.setFloatUniform("c1", r, g, b, a1)
                rs.setFloatUniform("c2", r, g, b, a2)
                val wb = StudioWall.get(context)
                val sh = BitmapShader(wb ?: blank, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                if (wb != null) {
                    val loc = IntArray(2)
                    getLocationOnScreen(loc)
                    val dm = resources.displayMetrics
                    val s = maxOf(dm.widthPixels / wb.width.toFloat(), dm.heightPixels / wb.height.toFloat())
                    val mx = Matrix()
                    mx.setScale(s, s)
                    mx.postTranslate((dm.widthPixels - wb.width * s) / 2f - loc[0], (dm.heightPixels - wb.height * s) / 2f - loc[1])
                    sh.setLocalMatrix(mx)
                }
                rs.setInputShader("wall", sh)
                rs.setFloatUniform("hasWall", if (wb != null) 1f else 0f)
                setRenderEffect(RenderEffect.createRuntimeShaderEffect(rs, "inp"))
            } catch (e: Throwable) { }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        applyFx()
    }
}

/** Reloj completo: fecha encima + dígitos. Se usa igual en el editor y dentro de SystemUI. */
class StudioClock(ctx: Context) : LinearLayout(ctx) {
    private val dateTv = TextView(ctx)
    val digits = DigitsView(ctx)
    var cfg = StudioCfg()
    private var rcv: BroadcastReceiver? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        dateTv.includeFontPadding = false
        dateTv.typeface = StudioFont.get()
        addView(dateTv, LayoutParams(-2, -2))
        addView(digits, LayoutParams(-2, -2))
    }

    private fun cap(s: String): String = if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1)

    fun refreshDate() {
        dateTv.text = cap(android.text.format.DateFormat.format("EEE, d 'de' MMM", Calendar.getInstance()).toString())
    }

    fun setCfg(c: StudioCfg) {
        cfg = c
        digits.cfg = c
        dateTv.visibility = if (c.date) VISIBLE else GONE
        dateTv.setTextColor(c.color)
        dateTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (c.size * c.sc / 100f * 0.10f).coerceIn(11f, 44f))
        try { dateTv.fontVariationSettings = "'wght' 500" } catch (e: Throwable) { }
        refreshDate()
        digits.refresh()
        digits.requestLayout()
        digits.invalidate()
        digits.applyFx()
    }

    fun applyCfg(s: String) = setCfg(StudioCfg.parse(s))

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
        digits.refresh()
        digits.applyFx()
    }

    override fun onDetachedFromWindow() {
        rcv?.let { try { context.unregisterReceiver(it) } catch (e: Throwable) { } }
        rcv = null
        super.onDetachedFromWindow()
    }
}
