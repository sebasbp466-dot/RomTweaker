package dev.romtweaker.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Base64
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Pegatina generada con IA (una sola ranura). En SystemUI la pone el módulo; en la app se lee de las preferencias. */
object StudioSticker {
    var b64: String = ""
    private var cache: Bitmap? = null
    private var key = 0

    fun bmp(ctx: Context): Bitmap? {
        val s = if (b64.isNotEmpty()) b64 else (ctx.getSharedPreferences("lock", Context.MODE_PRIVATE).getString("sticker", "") ?: "")
        if (s.isEmpty()) return null
        if (s.hashCode() == key && cache != null) return cache
        key = s.hashCode()
        cache = try { Base64.decode(s, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) } } catch (e: Throwable) { null }
        return cache
    }

    /** Quita el fondo blanco liso conectado con el borde (respaldo si ML Kit no está). */
    fun cutWhite(src: Bitmap): Bitmap {
        val w = 360
        val h = (src.height * w / src.width.toFloat()).toInt().coerceAtLeast(1)
        val b = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val seen = BooleanArray(w * h)
        val q = IntArray(w * h)
        var qh = 0
        var qt = 0
        fun white(i: Int): Boolean { val c = px[i]; return Color.red(c) > 232 && Color.green(c) > 232 && Color.blue(c) > 232 }
        fun push(i: Int) { if (!seen[i] && white(i)) { seen[i] = true; q[qt++] = i } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (qh < qt) {
            val i = q[qh++]
            px[i] = 0
            val x = i % w
            val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        b.setPixels(px, 0, w, 0, 0, w, h)
        return b
    }

    fun encode(b: Bitmap): String {
        val w = 340
        val s = if (b.width > w) Bitmap.createScaledBitmap(b, w, (b.height * w / b.width.toFloat()).toInt().coerceAtLeast(1), true) else b
        val bo = java.io.ByteArrayOutputStream()
        s.compress(Bitmap.CompressFormat.PNG, 90, bo)
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
    }
}

/** Dibuja un reloj descrito por un JSON (el motor de "diseños"): textos con tokens, formas, agujas, marcas, anillos y pegatina. */
class CustomClockView(ctx: Context) : View(ctx) {
    var cfg = StudioCfg()
    private var els: JSONArray? = null
    private var boxH = 100f
    private var spec = ""
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var hr = 0f
    private var mi = 0f
    private var sc = 0f
    var hasSec = false
        private set
    private var bat = 100
    private val secTick = object : Runnable {
        override fun run() { if (!hasSec || !isAttachedToWindow) return; refresh(); postDelayed(this, 1000) }
    }

    fun setSpec(s: String) {
        if (s == spec) return
        spec = s
        els = null
        hasSec = false
        try {
            val j = JSONObject(s)
            els = j.optJSONArray("elements")
            boxH = (j.optJSONObject("box")?.optDouble("h", 100.0) ?: 100.0).toFloat().coerceIn(30f, 260f)
            val a = els
            if (a != null) for (i in 0 until a.length()) { val e = a.optJSONObject(i); if (e != null && e.optString("type") == "hand" && e.optString("which") == "second") hasSec = true }
        } catch (e: Throwable) { }
        removeCallbacks(secTick)
        if (hasSec && isAttachedToWindow) post(secTick)
        requestLayout()
        invalidate()
    }

    private fun unit(): Float = cfg.size * resources.displayMetrics.density * cfg.sc / 100f * 1.6f / 100f

    var pad = 0f
        private set

    fun inner(): RectF = RectF(pad * 0.4f, pad * 0.4f, width - pad * 0.4f, height - pad * 0.4f)

    override fun onMeasure(ws: Int, hs: Int) {
        val u = unit()
        pad = 6f * u
        setMeasuredDimension((100f * u + 2 * pad).toInt() + 1, (boxH * u + 2 * pad).toInt() + 1)
    }

    fun refresh() {
        val c = Calendar.getInstance()
        val h = c.get(Calendar.HOUR)
        val m = c.get(Calendar.MINUTE)
        val s = c.get(Calendar.SECOND)
        val a = (h + m / 60f) * 30f
        val b = m * 6f + s * 0.1f
        val sv = if (hasSec) s * 6f else 0f
        try {
            val bi = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            if (bi != null) { val l = bi.getIntExtra("level", 100); val sc2 = bi.getIntExtra("scale", 100); if (sc2 > 0) bat = l * 100 / sc2 }
        } catch (e: Throwable) { }
        hr = a; mi = b; sc = sv
        invalidate()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (hasSec) post(secTick) }
    override fun onDetachedFromWindow() { removeCallbacks(secTick); super.onDetachedFromWindow() }

    private var c1 = Color.WHITE
    private var c2 = Color.WHITE

    private fun col(s: String?, def: Int): Int = when {
        s.isNullOrEmpty() -> def
        s == "p" -> c1
        s == "s" -> c2
        s == "w" -> Color.WHITE
        s == "k" -> 0xFF111111.toInt()
        else -> try { Color.parseColor(s) } catch (e: Throwable) { def }
    }

    private fun tok(t: String): String {
        if (!t.contains('{')) return t
        val cal = Calendar.getInstance()
        var H = cal.get(Calendar.HOUR_OF_DAY)
        val is24 = android.text.format.DateFormat.is24HourFormat(context)
        if (!is24) { H %= 12; if (H == 0) H = 12 }
        val HH = String.format("%02d", H)
        val h12 = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        val mm = String.format("%02d", cal.get(Calendar.MINUTE))
        fun f(pt: String) = android.text.format.DateFormat.format(pt, cal).toString()
        return t.replace("{HH}", HH).replace("{H}", H.toString()).replace("{hh}", String.format("%02d", h12)).replace("{h}", h12.toString())
            .replace("{mm}", mm).replace("{m}", cal.get(Calendar.MINUTE).toString())
            .replace("{H1}", HH.substring(0, 1)).replace("{H2}", HH.substring(1, 2)).replace("{m1}", mm.substring(0, 1)).replace("{m2}", mm.substring(1, 2))
            .replace("{ddd}", f("EEE")).replace("{dddd}", f("EEEE")).replace("{dd}", f("dd")).replace("{d}", f("d"))
            .replace("{MMM}", f("MMM")).replace("{MMMM}", f("MMMM")).replace("{yyyy}", f("yyyy"))
            .replace("{ampm}", if (cal.get(Calendar.AM_PM) == 0) "AM" else "PM").replace("{bat}", bat.toString()).replace("{sec}", String.format("%02d", cal.get(Calendar.SECOND)))
    }

    private fun frac(v: String?): Float = when (v) {
        "bat" -> bat / 100f
        "min" -> Calendar.getInstance().get(Calendar.MINUTE) / 60f
        "hour" -> (Calendar.getInstance().get(Calendar.HOUR) + Calendar.getInstance().get(Calendar.MINUTE) / 60f) / 12f
        "sec" -> Calendar.getInstance().get(Calendar.SECOND) / 60f
        "day" -> (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60 + Calendar.getInstance().get(Calendar.MINUTE)) / 1440f
        else -> 1f
    }

    private fun font(pt: Paint, e: JSONObject) {
        val f = e.optString("font", "flex")
        val w = e.optInt("wght", 500).coerceIn(100, 900)
        when {
            f == "serif" -> pt.typeface = Typeface.create(Typeface.SERIF, w, false)
            f == "mono" -> pt.typeface = Typeface.create(Typeface.MONOSPACE, w, false)
            f in StudioFont.assetFonts && StudioFont.asset(context, f) != null -> {
                pt.typeface = StudioFont.asset(context, f)
                if (f in StudioFont.varFonts) try { pt.setFontVariationSettings("'wght' $w") } catch (x: Throwable) { }
            }
            else -> {
                pt.typeface = StudioFont.get()
                try { pt.setFontVariationSettings("'wght' $w,'wdth' ${e.optInt("wdth", 100)},'ROND' ${e.optInt("rond", 0)}") } catch (x: Throwable) { }
            }
        }
    }

    private fun F(e: JSONObject, k: String, d: Float): Float = e.optDouble(k, d.toDouble()).toFloat()

    override fun onDraw(c: Canvas) {
        val a = els ?: return
        val cols = StudioColor.resolve(context, cfg)
        c1 = cols.first
        c2 = if (cols.second != 0) cols.second else cols.first
        val u = unit()
        c.save()
        c.translate(pad, pad)
        for (i in 0 until a.length()) {
            val e = a.optJSONObject(i) ?: continue
            try { drawEl(c, e, u) } catch (x: Throwable) { }
        }
        c.restore()
    }

    private fun paintBase(e: JSONObject, fill: Boolean, u: Float) {
        p.reset()
        p.isAntiAlias = true
        p.style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        val cc = col(e.optString("col", "p"), c1)
        p.color = cc
        p.alpha = (255 * F(e, "a", 1f).coerceIn(0f, 1f)).toInt()
        val g = F(e, "glow", 0f)
        val sh = F(e, "shadow", 0f)
        if (g > 0f) p.setShadowLayer(g * u, 0f, 0f, cc)
        else if (sh > 0f) p.setShadowLayer(sh * u, 0f, sh * u * 0.4f, 0x99000000.toInt())
    }

    private fun grad(e: JSONObject, top: Float, bot: Float) {
        val s2 = e.optString("col2", "")
        if (s2.isNotEmpty()) p.shader = LinearGradient(0f, top, 0f, bot, col(e.optString("col", "p"), c1), col(s2, c2), Shader.TileMode.CLAMP)
    }

    private fun drawEl(c: Canvas, e: JSONObject, u: Float) {
        val x = F(e, "x", 50f) * u
        val y = F(e, "y", 50f) * u
        val rot = F(e, "rot", 0f)
        c.save()
        if (rot != 0f) c.rotate(rot, x, y)
        when (e.optString("type")) {
            "text" -> {
                val size = F(e, "size", 20f) * u
                paintBase(e, e.optInt("ol", 0) == 0, u)
                font(p, e)
                p.textSize = size
                p.textAlign = when (e.optString("align", "c")) { "l" -> Paint.Align.LEFT; "r" -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
                p.letterSpacing = F(e, "track", 0f) * u / maxOf(size, 1f)
                if (e.optInt("ol", 0) == 1) p.strokeWidth = maxOf(1.5f, size * 0.03f)
                var t = tok(e.optString("text", ""))
                if (e.optInt("up", 0) == 1) t = t.uppercase()
                val lines = t.split("\n")
                val r = android.graphics.Rect()
                p.getTextBounds("0", 0, 1, r)
                val capH = r.height().toFloat()
                val lh = size * F(e, "lh", 1f)
                val total = lines.size * lh
                grad(e, y - total / 2f, y + total / 2f)
                for ((k, ln) in lines.withIndex()) c.drawText(ln, x, y - total / 2f + lh * (k + 0.5f) + capH / 2f, p)
            }
            "shape" -> {
                val w = F(e, "w", 20f) * u
                val h = F(e, "h", F(e, "w", 20f)) * u
                val sw = F(e, "sw", 0f) * u
                val shape = e.optString("shape", "rect")
                paintBase(e, sw <= 0f && shape != "line" && shape != "ring", u)
                p.strokeWidth = if (sw > 0f) sw else maxOf(1.5f * u, 1f)
                grad(e, y - h / 2f, y + h / 2f)
                val f = if (e.has("val")) frac(e.optString("val")) else 1f
                val rc = RectF(x - w / 2f, y - h / 2f, x - w / 2f + w * (if (shape == "rect" || shape == "rrect" || shape == "pill") f else 1f), y + h / 2f)
                val rr = F(e, "r", 0f) * u
                when (shape) {
                    "rect" -> c.drawRect(rc, p)
                    "rrect" -> c.drawRoundRect(rc, rr, rr, p)
                    "pill" -> c.drawRoundRect(rc, minOf(w, h) / 2f, minOf(w, h) / 2f, p)
                    "circle" -> c.drawOval(RectF(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f), p)
                    "ring" -> { p.style = Paint.Style.STROKE; c.drawOval(RectF(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f), p) }
                    "arc" -> {
                        p.style = Paint.Style.STROKE
                        val st = F(e, "start", -90f)
                        val sweep = if (e.has("val")) frac(e.optString("val")) * 360f else F(e, "sweep", 270f)
                        c.drawArc(RectF(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f), st, sweep, false, p)
                    }
                    "line" -> { p.style = Paint.Style.STROKE; c.drawLine(x, y, F(e, "x2", 50f) * u, F(e, "y2", 50f) * u, p) }
                    "tri" -> { val pa = Path(); pa.moveTo(x, y - h / 2f); pa.lineTo(x + w / 2f, y + h / 2f); pa.lineTo(x - w / 2f, y + h / 2f); pa.close(); c.drawPath(pa, p) }
                    "star" -> {
                        val n = e.optInt("n", 5).coerceIn(3, 16)
                        val pa = Path()
                        for (k in 0 until n * 2) {
                            val ang = Math.toRadians(-90.0 + k * 180.0 / n)
                            val rad = if (k % 2 == 0) w / 2f else w / 4.2f
                            val px = x + (Math.cos(ang) * rad).toFloat()
                            val py = y + (Math.sin(ang) * rad).toFloat()
                            if (k == 0) pa.moveTo(px, py) else pa.lineTo(px, py)
                        }
                        pa.close(); c.drawPath(pa, p)
                    }
                    "heart" -> {
                        val pa = Path()
                        pa.moveTo(x, y + h * 0.42f)
                        pa.cubicTo(x - w * 0.62f, y + h * 0.02f, x - w * 0.42f, y - h * 0.5f, x, y - h * 0.16f)
                        pa.cubicTo(x + w * 0.42f, y - h * 0.5f, x + w * 0.62f, y + h * 0.02f, x, y + h * 0.42f)
                        pa.close(); c.drawPath(pa, p)
                    }
                    "cross" -> { val t = minOf(w, h) * 0.32f; c.drawRect(RectF(x - w / 2f, y - t / 2f, x + w / 2f, y + t / 2f), p); c.drawRect(RectF(x - t / 2f, y - h / 2f, x + t / 2f, y + h / 2f), p) }
                }
            }
            "hand" -> {
                paintBase(e, false, u)
                val ang = when (e.optString("which", "minute")) { "hour" -> hr; "second" -> sc; else -> mi }
                val len = F(e, "len", 30f) * u
                val tail = F(e, "tail", 0f) * u
                val w = F(e, "w", 2f) * u
                p.strokeWidth = w
                p.strokeCap = if (e.optString("tip") == "flat") Paint.Cap.BUTT else Paint.Cap.ROUND
                val r = Math.toRadians(ang.toDouble())
                val sx = x - (Math.sin(r) * tail).toFloat()
                val sy = y + (Math.cos(r) * tail).toFloat()
                val ex = x + (Math.sin(r) * len).toFloat()
                val ey = y - (Math.cos(r) * len).toFloat()
                c.drawLine(sx, sy, ex, ey, p)
                when (e.optString("tip")) {
                    "circle" -> { p.style = Paint.Style.FILL; c.drawCircle(ex, ey, F(e, "tr", 3f) * u, p) }
                    "arrow" -> {
                        p.style = Paint.Style.FILL
                        val pa = Path()
                        val s = F(e, "tr", 3f) * u
                        pa.moveTo(ex + (Math.sin(r) * s).toFloat(), ey - (Math.cos(r) * s).toFloat())
                        pa.lineTo(ex + (Math.cos(r) * s * 0.7f).toFloat(), ey + (Math.sin(r) * s * 0.7f).toFloat())
                        pa.lineTo(ex - (Math.cos(r) * s * 0.7f).toFloat(), ey - (Math.sin(r) * s * 0.7f).toFloat())
                        pa.close(); c.drawPath(pa, p)
                    }
                }
                if (e.optInt("hub", 0) == 1) { p.style = Paint.Style.FILL; c.drawCircle(x, y, w * 0.9f, p) }
            }
            "ticks" -> {
                paintBase(e, false, u)
                val n = e.optInt("n", 12).coerceIn(2, 120)
                val r0 = F(e, "r0", 40f) * u
                val r1 = F(e, "r1", 46f) * u
                val every = e.optInt("every", 0)
                val r1b = F(e, "r1b", F(e, "r1", 46f) + 3f) * u
                for (k in 0 until n) {
                    val emph = every > 0 && k % every == 0
                    val ang = Math.toRadians(k * 360.0 / n)
                    p.strokeWidth = F(e, if (emph) "ew" else "w", 1.2f) * u
                    val rr = if (emph) r1b else r1
                    c.drawLine(x + (Math.sin(ang) * r0).toFloat(), y - (Math.cos(ang) * r0).toFloat(), x + (Math.sin(ang) * rr).toFloat(), y - (Math.cos(ang) * rr).toFloat(), p)
                }
            }
            "dots" -> {
                paintBase(e, true, u)
                val n = e.optInt("n", 12).coerceIn(2, 120)
                val rad = F(e, "r", 40f) * u
                val every = e.optInt("every", 0)
                for (k in 0 until n) {
                    val emph = every > 0 && k % every == 0
                    val ang = Math.toRadians(k * 360.0 / n)
                    c.drawCircle(x + (Math.sin(ang) * rad).toFloat(), y - (Math.cos(ang) * rad).toFloat(), F(e, if (emph) "es" else "size", 1.5f) * u, p)
                }
            }
            "numerals" -> {
                val rad = F(e, "r", 38f) * u
                val size = F(e, "size", 8f) * u
                val roman = arrayOf("XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI")
                val step = if (e.optInt("step", 1) <= 0) 1 else e.optInt("step", 1)
                var k = 0
                while (k < 12) {
                    paintBase(e, true, u)
                    font(p, e)
                    p.textSize = size
                    p.textAlign = Paint.Align.CENTER
                    val ang = Math.toRadians(k * 30.0)
                    val s = if (e.optString("set") == "roman") roman[k] else (if (k == 0) "12" else k.toString())
                    val r = android.graphics.Rect()
                    p.getTextBounds("0", 0, 1, r)
                    c.drawText(s, x + (Math.sin(ang) * rad).toFloat(), y - (Math.cos(ang) * rad).toFloat() + r.height() / 2f, p)
                    k += step
                }
            }
            "sticker" -> {
                val b = StudioSticker.bmp(context)
                val w = F(e, "w", 40f) * u
                if (b != null) {
                    val h = w * b.height / b.width
                    p.reset(); p.isFilterBitmap = true; p.alpha = (255 * F(e, "a", 1f).coerceIn(0f, 1f)).toInt()
                    c.drawBitmap(b, null, RectF(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f), p)
                } else {
                    paintBase(e, false, u)
                    p.alpha = 90
                    p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f * u, 4f * u), 0f)
                    p.strokeWidth = 1.2f * u
                    c.drawRoundRect(RectF(x - w / 2f, y - w / 2f, x + w / 2f, y + w / 2f), 6f * u, 6f * u, p)
                }
            }
        }
        c.restore()
    }

    fun applyFx() {
        val cols = StudioColor.resolve(context, cfg)
        if (!StudioFx.needs(cfg)) { if (android.os.Build.VERSION.SDK_INT >= 31) post { try { setRenderEffect(null) } catch (e: Throwable) { } }; return }
        StudioFx.apply(this, cfg, cols.first, cols.second)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); applyFx() }
}

/** Diseños hechos a mano (también sirven de ejemplo para Gemini). */
object StudioDesigns {
    val all: List<Pair<String, String>> = listOf(
        "Luna" to """{"box":{"h":120},"elements":[
{"type":"shape","shape":"circle","x":50,"y":42,"w":78,"h":78,"col":"p","a":0.16},
{"type":"shape","shape":"ring","x":50,"y":42,"w":78,"h":78,"sw":0.8,"col":"p"},
{"type":"text","text":"{HH}","x":50,"y":32,"size":36,"font":"anton","col":"p"},
{"type":"text","text":"{mm}","x":50,"y":54,"size":36,"font":"anton","col":"s"},
{"type":"text","text":"{dddd} {d} {MMMM}","x":50,"y":96,"size":5.5,"wght":600,"track":1.4,"up":1,"col":"p"}]}""",
        "Fichas" to """{"box":{"h":112},"elements":[
{"type":"shape","shape":"rrect","x":27,"y":28,"w":44,"h":50,"r":9,"col":"p"},
{"type":"shape","shape":"rrect","x":73,"y":28,"w":44,"h":50,"r":9,"col":"s"},
{"type":"shape","shape":"rrect","x":27,"y":84,"w":44,"h":50,"r":9,"col":"s"},
{"type":"shape","shape":"rrect","x":73,"y":84,"w":44,"h":50,"r":9,"col":"p"},
{"type":"text","text":"{H1}","x":27,"y":28,"size":34,"font":"anton","col":"k"},
{"type":"text","text":"{H2}","x":73,"y":28,"size":34,"font":"anton","col":"k"},
{"type":"text","text":"{m1}","x":27,"y":84,"size":34,"font":"anton","col":"k"},
{"type":"text","text":"{m2}","x":73,"y":84,"size":34,"font":"anton","col":"k"}]}""",
        "Solar" to """{"box":{"h":100},"elements":[
{"type":"shape","shape":"arc","x":50,"y":50,"w":92,"h":92,"sw":2.2,"val":"day","start":-90,"col":"s"},
{"type":"ticks","x":50,"y":50,"n":60,"r0":38,"r1":41,"w":0.5,"every":5,"r1b":44,"ew":1.2,"col":"p","a":0.8},
{"type":"hand","which":"hour","x":50,"y":50,"len":22,"w":3.2,"tail":4,"col":"p"},
{"type":"hand","which":"minute","x":50,"y":50,"len":33,"w":1.8,"tail":5,"col":"s","tip":"circle","tr":2.2},
{"type":"text","text":"{ddd} {d}","x":50,"y":70,"size":5,"wght":600,"track":1,"up":1,"col":"p","a":0.85}]}""",
        "Tarjeta" to """{"box":{"h":80},"elements":[
{"type":"shape","shape":"rrect","x":50,"y":40,"w":96,"h":72,"r":14,"col":"p","a":0.18},
{"type":"text","text":"{HH}:{mm}","x":50,"y":32,"size":34,"wght":300,"wdth":60,"col":"p"},
{"type":"text","text":"{dddd}, {d} de {MMMM}","x":50,"y":56,"size":6,"wght":500,"col":"p"},
{"type":"shape","shape":"rect","x":14,"y":66,"w":72,"h":3,"col":"p","a":0.25},
{"type":"shape","shape":"rect","x":14,"y":66,"w":72,"h":3,"val":"bat","col":"s"},
{"type":"text","text":"{bat}%","x":88,"y":66,"size":5,"align":"r","col":"p"}]}""",
        "Barra retro" to """{"box":{"h":60},"elements":[
{"type":"text","text":"{HH}:{mm}","x":50,"y":22,"size":38,"font":"bebas","track":2,"col":"p","col2":"#FFFFFF"},
{"type":"shape","shape":"pill","x":50,"y":47,"w":92,"h":6,"col":"p","a":0.2},
{"type":"shape","shape":"pill","x":4,"y":47,"w":92,"h":6,"val":"bat","col":"s"}]}""",
        "Órbita" to """{"box":{"h":100},"elements":[
{"type":"shape","shape":"ring","x":50,"y":50,"w":88,"h":88,"sw":0.5,"col":"p","a":0.5},
{"type":"shape","shape":"ring","x":50,"y":50,"w":50,"h":50,"sw":0.5,"col":"p","a":0.5},
{"type":"hand","which":"hour","x":50,"y":50,"len":25,"w":0,"tip":"circle","tr":5,"col":"p"},
{"type":"hand","which":"minute","x":50,"y":50,"len":44,"w":0,"tip":"circle","tr":3.5,"col":"s"},
{"type":"text","text":"{h}","x":50,"y":50,"size":16,"font":"syne","wght":800,"col":"p"}]}""",
        "Gigante" to """{"box":{"h":120},"elements":[
{"type":"text","text":"{H}","x":50,"y":44,"size":88,"font":"bodoni","wght":500,"col":"p"},
{"type":"shape","shape":"rect","x":50,"y":79,"w":50,"h":0.8,"col":"p","a":0.6},
{"type":"text","text":"{mm}","x":50,"y":98,"size":22,"font":"bodoni","wght":400,"col":"s"}]}""",
        "Estrella" to """{"box":{"h":110},"elements":[
{"type":"shape","shape":"star","n":12,"x":50,"y":50,"w":96,"h":96,"col":"p","a":0.18},
{"type":"shape","shape":"star","n":12,"x":50,"y":50,"w":96,"h":96,"sw":0.7,"col":"p"},
{"type":"text","text":"{HH}\n{mm}","x":50,"y":50,"size":30,"lh":0.95,"font":"bigshoulders","wght":800,"col":"p"},
{"type":"text","text":"{ddd} {d} {MMM}","x":50,"y":106,"size":5,"track":1.5,"up":1,"col":"s"}]}""",
        "Doble anillo" to """{"box":{"h":100},"elements":[
{"type":"shape","shape":"arc","x":50,"y":50,"w":92,"h":92,"sw":4,"val":"hour","start":-90,"col":"p"},
{"type":"shape","shape":"arc","x":50,"y":50,"w":76,"h":76,"sw":4,"val":"min","start":-90,"col":"s"},
{"type":"shape","shape":"arc","x":50,"y":50,"w":60,"h":60,"sw":4,"val":"bat","start":-90,"col":"p","a":0.6},
{"type":"text","text":"{HH}:{mm}","x":50,"y":50,"size":15,"font":"bebas","track":1,"col":"p"}]}""",
        "Cinta" to """{"box":{"h":70},"elements":[
{"type":"shape","shape":"rrect","x":50,"y":35,"w":98,"h":40,"r":20,"col":"p","a":0.2},
{"type":"text","text":"{HH}","x":27,"y":35,"size":30,"font":"anton","col":"p"},
{"type":"shape","shape":"circle","x":50,"y":35,"w":4,"h":4,"col":"s"},
{"type":"text","text":"{mm}","x":73,"y":35,"size":30,"font":"anton","col":"s"},
{"type":"text","text":"{dddd} · {d} {MMM}","x":50,"y":62,"size":5,"track":1.2,"up":1,"col":"p"}]}""",
        "Corazón" to """{"box":{"h":100},"elements":[
{"type":"shape","shape":"heart","x":50,"y":48,"w":88,"h":88,"col":"p","a":0.9},
{"type":"text","text":"{HH}\n{mm}","x":50,"y":46,"size":26,"lh":0.95,"font":"anton","col":"k"},
{"type":"text","text":"{d} {MMM}","x":50,"y":96,"size":5,"track":1,"up":1,"col":"s"}]}""",
        "Pulso" to """{"box":{"h":90},"elements":[
{"type":"dots","x":50,"y":45,"n":60,"r":40,"size":0.7,"every":5,"es":1.5,"col":"p","a":0.7},
{"type":"hand","which":"second","x":50,"y":45,"len":38,"w":0.8,"tail":6,"col":"s"},
{"type":"text","text":"{HH}:{mm}","x":50,"y":45,"size":20,"wght":200,"wdth":70,"col":"p"},
{"type":"text","text":"{sec}","x":50,"y":60,"size":5,"col":"s"}]}""",
        "Neón" to """{"box":{"h":70},"elements":[
{"type":"text","text":"{HH}:{mm}","x":50,"y":30,"size":40,"font":"syne","wght":800,"col":"p","glow":5},
{"type":"shape","shape":"pill","x":50,"y":60,"w":60,"h":2.4,"col":"s","glow":4}]}""",
        "Reloj de bolsillo" to """{"box":{"h":100},"elements":[
{"type":"shape","shape":"ring","x":50,"y":50,"w":94,"h":94,"sw":1.2,"col":"p"},
{"type":"shape","shape":"ring","x":50,"y":50,"w":84,"h":84,"sw":0.4,"col":"p","a":0.6},
{"type":"numerals","x":50,"y":50,"r":35,"size":8,"set":"roman","font":"bodoni","col":"p"},
{"type":"hand","which":"hour","x":50,"y":50,"len":22,"w":2.6,"tail":4,"tip":"arrow","tr":3.5,"col":"p"},
{"type":"hand","which":"minute","x":50,"y":50,"len":32,"w":1.6,"tail":5,"tip":"arrow","tr":3,"col":"p"},
{"type":"hand","which":"second","x":50,"y":50,"len":34,"w":0.6,"tail":8,"col":"s","hub":1}]}""",
        "Columna" to """{"box":{"h":150},"elements":[
{"type":"text","text":"{H1}","x":50,"y":22,"size":48,"font":"anton","col":"p"},
{"type":"text","text":"{H2}","x":50,"y":58,"size":48,"font":"anton","col":"p"},
{"type":"shape","shape":"rect","x":30,"y":82,"w":40,"h":1,"col":"s"},
{"type":"text","text":"{m1}","x":50,"y":105,"size":48,"font":"anton","col":"s"},
{"type":"text","text":"{m2}","x":50,"y":141,"size":48,"font":"anton","col":"s"}]}""",
        "Marco" to """{"box":{"h":110},"elements":[
{"type":"shape","shape":"rrect","x":50,"y":55,"w":92,"h":100,"r":10,"sw":1.2,"col":"p"},
{"type":"text","text":"{dddd}","x":50,"y":16,"size":5.5,"track":2,"up":1,"col":"s"},
{"type":"text","text":"{HH}","x":50,"y":42,"size":38,"font":"bodoni","wght":500,"col":"p"},
{"type":"text","text":"{mm}","x":50,"y":76,"size":38,"font":"bodoni","wght":500,"col":"p","a":0.6},
{"type":"text","text":"{d} {MMMM}","x":50,"y":98,"size":5.5,"track":1.5,"up":1,"col":"s"}]}""",
        "Radar" to """{"box":{"h":100},"elements":[
{"type":"shape","shape":"ring","x":50,"y":50,"w":90,"h":90,"sw":0.5,"col":"p","a":0.5},
{"type":"shape","shape":"ring","x":50,"y":50,"w":60,"h":60,"sw":0.5,"col":"p","a":0.5},
{"type":"shape","shape":"ring","x":50,"y":50,"w":30,"h":30,"sw":0.5,"col":"p","a":0.5},
{"type":"shape","shape":"line","x":5,"y":50,"x2":95,"y2":50,"sw":0.4,"col":"p","a":0.4},
{"type":"shape","shape":"line","x":50,"y":5,"x2":50,"y2":95,"sw":0.4,"col":"p","a":0.4},
{"type":"hand","which":"second","x":50,"y":50,"len":45,"w":1,"col":"s","tip":"circle","tr":2},
{"type":"text","text":"{HH}:{mm}","x":50,"y":50,"size":13,"font":"bebas","track":1,"col":"p"}]}""",
        "Barras" to """{"box":{"h":110},"elements":[
{"type":"text","text":"{HH}:{mm}","x":50,"y":24,"size":34,"wght":600,"wdth":80,"col":"p"},
{"type":"text","text":"HORA","x":10,"y":50,"size":4,"align":"l","track":1.5,"col":"p","a":0.7},
{"type":"shape","shape":"pill","x":10,"y":58,"w":80,"h":4,"col":"p","a":0.2},
{"type":"shape","shape":"pill","x":10,"y":58,"w":80,"h":4,"val":"day","col":"p"},
{"type":"text","text":"MINUTO","x":10,"y":72,"size":4,"align":"l","track":1.5,"col":"p","a":0.7},
{"type":"shape","shape":"pill","x":10,"y":80,"w":80,"h":4,"col":"s","a":0.2},
{"type":"shape","shape":"pill","x":10,"y":80,"w":80,"h":4,"val":"min","col":"s"},
{"type":"text","text":"BATERÍA {bat}%","x":10,"y":94,"size":4,"align":"l","track":1.5,"col":"p","a":0.7},
{"type":"shape","shape":"pill","x":10,"y":102,"w":80,"h":4,"col":"p","a":0.2},
{"type":"shape","shape":"pill","x":10,"y":102,"w":80,"h":4,"val":"bat","col":"p"}]}"""
    )
}
