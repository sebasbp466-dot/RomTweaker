package dev.romtweaker.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import java.util.Calendar

/** Configuración del reloj de bloqueo (se guarda en las preferencias «lock»). */
class ClockCfg {
    var style = "glass"
    var weight = 600
    var size = 96
    var color = Color.WHITE
    var accent = 0xFFFF7A3D.toInt()
    var date = true
    var h24 = true
    var seconds = false
    var pos = 1
    var glass = 80
    var filter = 0
    var wdth = 85
    var rond = 0
    var slnt = 0

    fun copy(): ClockCfg {
        val c = ClockCfg()
        c.style = style; c.weight = weight; c.size = size; c.color = color; c.accent = accent
        c.date = date; c.h24 = h24; c.seconds = seconds; c.pos = pos; c.glass = glass; c.filter = filter; c.wdth = wdth; c.rond = rond; c.slnt = slnt
        return c
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences("lock", Context.MODE_PRIVATE).edit()
            .putString("style", style).putInt("weight", weight).putInt("size", size)
            .putInt("color", color).putInt("accent", accent).putBoolean("date", date)
            .putBoolean("h24", h24).putBoolean("seconds", seconds).putInt("pos", pos).putInt("glass", glass).putInt("filter", filter).putInt("wdth", wdth).putInt("rond", rond).putInt("slnt", slnt)
            .apply()
    }

    companion object {
        fun load(ctx: Context): ClockCfg {
            val p = ctx.getSharedPreferences("lock", Context.MODE_PRIVATE)
            val c = ClockCfg()
            c.style = p.getString("style", "glass") ?: "glass"
            c.weight = p.getInt("weight", 600)
            c.size = p.getInt("size", 96)
            c.color = p.getInt("color", Color.WHITE)
            c.accent = p.getInt("accent", 0xFFFF7A3D.toInt())
            c.date = p.getBoolean("date", true)
            c.h24 = p.getBoolean("h24", true)
            c.seconds = p.getBoolean("seconds", false)
            c.pos = p.getInt("pos", 1)
            c.glass = p.getInt("glass", 80)
            c.filter = p.getInt("filter", 0)
            c.wdth = p.getInt("wdth", 85)
            c.rond = p.getInt("rond", 0)
            c.slnt = p.getInt("slnt", 0)
            return c
        }
    }
}

object ClockStyles {
    val list = listOf(
        Pair("glass", "Liquid Glass"),
        Pair("glass_stack", "Glass apilado"),
        Pair("glass_thin", "Glass fino"),
        Pair("hy_stack", "HyperOS bloque"),
        Pair("hy_outline", "HyperOS contorno"),
        Pair("hy_dual", "HyperOS dual"),
        Pair("hy_digital", "HyperOS digital"),
        Pair("hy_analog", "HyperOS analógico")
    )
}

/** Dibuja los relojes. `scale` permite usarlo en miniaturas y en la vista previa. */
class ClockFace(ctx: Context) : View(ctx) {
    var cfg = ClockCfg()
    var scale = 1f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hd = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            hd.postDelayed(this, if (cfg.seconds || cfg.style == "hy_analog") 1000L else 10000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        hd.post(tick)
    }

    override fun onDetachedFromWindow() {
        hd.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun px(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics) * scale
    private fun dpx(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics) * scale
    private fun a(color: Int, f: Float): Int = (color and 0x00FFFFFF) or ((f * 255).toInt() shl 24)
    private fun tf(w: Int, family: String): Typeface = Typeface.create(Typeface.create(family, Typeface.NORMAL), Math.max(100, Math.min(900, w)), false)
    private fun stacked(): Boolean = cfg.style == "glass_stack" || cfg.style == "hy_stack" || cfg.style == "hy_outline"

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val s = px(cfg.size.toFloat())
        var h = if (cfg.style == "hy_analog") s * 1.9f else if (stacked()) s * 1.8f else s * 1.2f
        if (cfg.date) h += s * 0.3f
        setMeasuredDimension(w, h.toInt())
    }

    private fun glass(c: Canvas, t: String, cx: Float, base: Float, s: Float, w: Int) {
        val k = cfg.glass / 100f
        p.reset()
        p.isAntiAlias = true
        p.textAlign = Paint.Align.CENTER
        p.textSize = s
        p.typeface = tf(w, "sans-serif")
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.06f
        p.color = a(cfg.color, 0.10f * k)
        c.drawText(t, cx, base, p)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, base - s * 0.75f, 0f, base, a(cfg.color, 0.92f), a(cfg.color, 0.28f + 0.5f * (1f - k)), Shader.TileMode.CLAMP)
        c.drawText(t, cx, base, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = dpx(1.4f)
        p.color = a(Color.WHITE, 0.70f * k)
        c.drawText(t, cx, base, p)
    }

    private fun plain(c: Canvas, t: String, cx: Float, base: Float, s: Float, w: Int, family: String, color: Int, stroke: Boolean) {
        p.reset()
        p.isAntiAlias = true
        p.textAlign = Paint.Align.CENTER
        p.textSize = s
        p.typeface = tf(w, family)
        p.color = color
        if (stroke) { p.style = Paint.Style.STROKE; p.strokeWidth = dpx(2.2f) } else p.style = Paint.Style.FILL
        c.drawText(t, cx, base, p)
    }

    /** Dibuja varias piezas seguidas centradas: (texto, color, peso). */
    private fun seq(c: Canvas, items: List<Triple<String, Int, Int>>, cx: Float, base: Float, s: Float, family: String, gap: Float) {
        p.reset()
        p.isAntiAlias = true
        p.textSize = s
        p.textAlign = Paint.Align.LEFT
        var total = 0f
        for (it2 in items) { p.typeface = tf(it2.third, family); total += p.measureText(it2.first) + gap }
        total -= gap
        var x = cx - total / 2f
        for (it2 in items) {
            p.typeface = tf(it2.third, family)
            p.color = it2.second
            c.drawText(it2.first, x, base, p)
            x += p.measureText(it2.first) + gap
        }
    }

    override fun onDraw(c: Canvas) {
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val mi = cal.get(Calendar.MINUTE)
        val se = cal.get(Calendar.SECOND)
        val hh = if (cfg.h24) String.format("%02d", h) else (if (h % 12 == 0) 12 else h % 12).toString()
        val mm = String.format("%02d", mi)
        val cx = width / 2f
        val s = px(cfg.size.toFloat())
        var y0 = 0f
        if (cfg.date) {
            val ds = DateFormat.format("EEE d MMM", cal.time).toString()
            p.reset()
            p.isAntiAlias = true
            p.textAlign = Paint.Align.CENTER
            p.textSize = s * 0.17f
            p.typeface = tf(600, "sans-serif")
            p.color = a(cfg.color, 0.9f)
            c.drawText(ds, cx, s * 0.2f, p)
            y0 = s * 0.3f
        }
        when (cfg.style) {
            "glass" -> glass(c, "$hh:$mm", cx, y0 + s * 0.95f, s, cfg.weight)
            "glass_thin" -> glass(c, "$hh:$mm", cx, y0 + s * 0.95f, s, 200)
            "glass_stack" -> {
                glass(c, hh, cx, y0 + s * 0.85f, s, cfg.weight)
                glass(c, mm, cx, y0 + s * 1.7f, s, cfg.weight)
            }
            "hy_stack" -> {
                plain(c, hh, cx, y0 + s * 0.85f, s, Math.max(cfg.weight, 700), "sans-serif-condensed", cfg.color, false)
                plain(c, mm, cx, y0 + s * 1.7f, s, Math.max(cfg.weight, 700), "sans-serif-condensed", cfg.accent, false)
            }
            "hy_outline" -> {
                plain(c, hh, cx, y0 + s * 0.85f, s, Math.max(cfg.weight, 700), "sans-serif-condensed", cfg.color, true)
                plain(c, mm, cx, y0 + s * 1.7f, s, Math.max(cfg.weight, 700), "sans-serif-condensed", cfg.accent, false)
            }
            "hy_dual" -> seq(c, listOf(Triple(hh, cfg.color, 200), Triple(mm, cfg.accent, 800)), cx, y0 + s * 0.95f, s, "sans-serif", s * 0.12f)
            "hy_digital" -> {
                val items = ArrayList<Triple<String, Int, Int>>()
                items.add(Triple(hh, cfg.color, cfg.weight))
                items.add(Triple(":", cfg.accent, cfg.weight))
                items.add(Triple(mm, cfg.color, cfg.weight))
                if (cfg.seconds) items.add(Triple(String.format("%02d", se), cfg.accent, 300))
                seq(c, items, cx, y0 + s * 0.95f, s * 0.9f, "sans-serif-medium", s * 0.03f)
            }
            "hy_analog" -> {
                val r = s * 0.85f
                val cy = y0 + r + dpx(4f)
                p.reset()
                p.isAntiAlias = true
                p.style = Paint.Style.STROKE
                p.strokeWidth = dpx(1.5f)
                p.color = a(cfg.color, 0.35f)
                c.drawCircle(cx, cy, r, p)
                p.style = Paint.Style.FILL
                for (i in 0 until 12) {
                    val ang = Math.toRadians(i * 30.0 - 90.0)
                    val rr = r * 0.86f
                    p.color = a(cfg.color, 0.9f)
                    c.drawCircle(cx + (rr * Math.cos(ang)).toFloat(), cy + (rr * Math.sin(ang)).toFloat(), if (i % 3 == 0) dpx(3.2f) else dpx(1.8f), p)
                }
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                val ha = ((h % 12) + mi / 60f) * 30f
                val ma = (mi + se / 60f) * 6f
                c.save(); c.rotate(ha, cx, cy)
                p.strokeWidth = dpx(6f); p.color = cfg.color
                c.drawLine(cx, cy, cx, cy - r * 0.5f, p)
                c.restore()
                c.save(); c.rotate(ma, cx, cy)
                p.strokeWidth = dpx(4.5f); p.color = cfg.color
                c.drawLine(cx, cy, cx, cy - r * 0.75f, p)
                c.restore()
                if (cfg.seconds) {
                    c.save(); c.rotate(se * 6f, cx, cy)
                    p.strokeWidth = dpx(1.8f); p.color = cfg.accent
                    c.drawLine(cx, cy + r * 0.12f, cx, cy - r * 0.8f, p)
                    c.restore()
                }
                p.style = Paint.Style.FILL
                p.color = cfg.accent
                c.drawCircle(cx, cy, dpx(5f), p)
            }
        }
    }
}
