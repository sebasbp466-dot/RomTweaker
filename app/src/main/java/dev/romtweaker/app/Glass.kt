package dev.romtweaker.app

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Superficie estilo Liquid Glass: relleno translúcido con luz superior, borde especular y brillo interior. */
class LiquidGlassDrawable(private val radius: Float, private val tint: Int, private val dark: Boolean, private val density: Float) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    private fun argb(a: Float, c: Int) = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    override fun onBoundsChange(b: android.graphics.Rect) {
        val w = b.width().toFloat(); val h = b.height().toFloat()
        val base = if (dark) 0xFFFFFFFF.toInt() else 0xFFFFFFFF.toInt()
        val a1 = if (dark) 0.16f else 0.55f
        val a2 = if (dark) 0.06f else 0.30f
        fill.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(argb(a1, base), argb(a2, tint), argb(a2 * 0.8f, tint)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        rim.strokeWidth = 1.2f * density
        rim.shader = LinearGradient(0f, 0f, w, h, intArrayOf(argb(0.85f, 0xFFFFFFFF.toInt()), argb(0.08f, 0xFFFFFFFF.toInt()), argb(0.50f, 0xFFFFFFFF.toInt())), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        glow.shader = LinearGradient(0f, 0f, 0f, h * 0.45f, argb(if (dark) 0.14f else 0.35f, 0xFFFFFFFF.toInt()), 0x00FFFFFF, Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas) {
        val b = bounds
        r.set(b)
        c.drawRoundRect(r, radius, radius, fill)
        val g = RectF(r.left + 2 * density, r.top + 2 * density, r.right - 2 * density, r.top + r.height() * 0.45f)
        c.drawRoundRect(g, radius * 0.8f, radius * 0.8f, glow)
        val h = rim.strokeWidth / 2
        r.inset(h, h)
        c.drawRoundRect(r, radius, radius, rim)
    }

    override fun getOutline(o: android.graphics.Outline) { o.setRoundRect(bounds, radius) }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(f: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
