package dev.romtweaker.app

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/** Layout que reparte chips en varias filas. */
class RtFlow(ctx: Context, private val gap: Int) : ViewGroup(ctx) {
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val maxW = MeasureSpec.getSize(wSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v.visibility == View.GONE) continue
            v.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val w = v.measuredWidth
            val h = v.measuredHeight
            if (x > 0 && x + w > maxW) { x = 0; y += rowH + gap; rowH = 0 }
            x += w + gap
            if (h > rowH) rowH = h
        }
        setMeasuredDimension(MeasureSpec.getSize(wSpec), y + rowH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v.visibility == View.GONE) continue
            val w = v.measuredWidth
            val h = v.measuredHeight
            if (x > 0 && x + w > maxW) { x = 0; y += rowH + gap; rowH = 0 }
            v.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + w, paddingTop + y + h)
            x += w + gap
            if (h > rowH) rowH = h
        }
    }
}

/** Material Design 3 (colores dinámicos Material You) hecho con Views. */
class M3(val ctx: Context, var glass: Boolean) {
    val MP = ViewGroup.LayoutParams.MATCH_PARENT
    val WC = ViewGroup.LayoutParams.WRAP_CONTENT
    val dark: Boolean = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun c(res: Int, fb: Long): Int = if (Build.VERSION.SDK_INT >= 31) ctx.getColor(res) else fb.toInt()

    val primary = if (dark) c(android.R.color.system_accent1_200, 0xFFD0BCFF) else c(android.R.color.system_accent1_600, 0xFF6750A4)
    val onPrimary = if (dark) c(android.R.color.system_accent1_800, 0xFF381E72) else c(android.R.color.system_accent1_0, 0xFFFFFFFF)
    val secContainer = if (dark) c(android.R.color.system_accent2_700, 0xFF4A4458) else c(android.R.color.system_accent2_100, 0xFFE8DEF8)
    val onSecContainer = if (dark) c(android.R.color.system_accent2_100, 0xFFE8DEF8) else c(android.R.color.system_accent2_900, 0xFF1D192B)
    val surface = if (dark) c(android.R.color.system_neutral1_900, 0xFF141218) else c(android.R.color.system_neutral1_10, 0xFFFEF7FF)
    val surfaceCont = if (dark) c(android.R.color.system_neutral2_800, 0xFF211F26) else c(android.R.color.system_neutral2_100, 0xFFF3EDF7)
    val onSurface = if (dark) c(android.R.color.system_neutral1_100, 0xFFE6E0E9) else c(android.R.color.system_neutral1_900, 0xFF1D1B20)
    val onSurfaceVar = if (dark) c(android.R.color.system_neutral2_200, 0xFFCAC4D0) else c(android.R.color.system_neutral2_700, 0xFF49454F)
    val outline = if (dark) c(android.R.color.system_neutral2_400, 0xFF938F99) else c(android.R.color.system_neutral2_500, 0xFF79747E)

    val cardA: Float get() = if (glass) 0.50f else 0.90f

    fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
    fun a(color: Int, f: Float): Int = (color and 0x00FFFFFF) or ((f * 255).toInt() shl 24)

    fun shape(color: Int, r: Int, strokeW: Int = 0, strokeC: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(r).toFloat()
        if (strokeW > 0) g.setStroke(dp(strokeW), strokeC)
        return g
    }

    fun ripple(base: GradientDrawable, r: Int): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(a(onSurface, 0.12f)), base, shape(Color.WHITE, r))

    fun lp(w: Int, h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.setMargins(dp(l), dp(t), dp(r), dp(b))
        return p
    }

    fun tv(s: CharSequence, sp: Float, color: Int, medium: Boolean = false): TextView {
        val t = TextView(ctx)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        if (medium) t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        return t
    }

    fun card(): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(20), dp(18), dp(20), dp(18))
        l.background = shape(a(surfaceCont, cardA), 28, if (glass) 1 else 0, a(Color.WHITE, 0.28f))
        return l
    }

    private fun button(text: String, bg: Int, fg: Int, click: () -> Unit): TextView {
        val t = tv(text, 14f, fg, true)
        t.gravity = Gravity.CENTER
        t.minHeight = dp(40)
        t.setPadding(dp(24), dp(8), dp(24), dp(8))
        t.background = ripple(shape(bg, 20), 20)
        t.setOnClickListener { click() }
        return t
    }

    fun filled(text: String, click: () -> Unit): TextView = button(text, primary, onPrimary, click)
    fun tonal(text: String, click: () -> Unit): TextView = button(text, secContainer, onSecContainer, click)

    fun chip(text: String, selected: Boolean, click: () -> Unit): TextView {
        val t = tv(text, 13f, if (selected) onSecContainer else onSurfaceVar, true)
        t.gravity = Gravity.CENTER
        t.minHeight = dp(34)
        t.setPadding(dp(14), dp(6), dp(14), dp(6))
        val base = if (selected) shape(secContainer, 10) else shape(Color.TRANSPARENT, 10, 1, a(outline, 0.8f))
        t.background = ripple(base, 10)
        t.setOnClickListener { click() }
        return t
    }

    fun navItem(icon: String, label: String, sel: Boolean, click: () -> Unit): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.VERTICAL
        l.gravity = Gravity.CENTER_HORIZONTAL
        l.setPadding(0, dp(10), 0, dp(10))
        val pill = LinearLayout(ctx)
        pill.gravity = Gravity.CENTER
        pill.background = shape(if (sel) secContainer else Color.TRANSPARENT, 16)
        pill.addView(tv(icon, 18f, if (sel) onSecContainer else onSurfaceVar), lp(dp(64), dp(32)))
        (pill.getChildAt(0) as TextView).gravity = Gravity.CENTER
        l.addView(pill, lp(WC, WC))
        l.addView(tv(label, 12f, if (sel) onSurface else onSurfaceVar, true), lp(WC, WC, 0, 4, 0, 0))
        l.setOnClickListener { click() }
        return l
    }

    fun switchRow(title: String, desc: String, on: Boolean, cb: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.addView(tv(title, 16f, onSurface))
        if (desc.isNotEmpty()) col.addView(tv(desc, 13f, onSurfaceVar))
        row.addView(col, LinearLayout.LayoutParams(0, WC, 1f))
        val sw = Switch(ctx)
        sw.isChecked = on
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(onPrimary, outline))
        sw.trackTintList = ColorStateList(states, intArrayOf(primary, a(outline, 0.35f)))
        sw.setOnCheckedChangeListener { _, v -> cb(v) }
        row.addView(sw, lp(WC, WC, 12, 0, 0, 0))
        return row
    }

    fun field(hint: String, value: String): EditText {
        val e = EditText(ctx)
        e.hint = hint
        e.setText(value)
        e.setSingleLine(true)
        e.inputType = InputType.TYPE_CLASS_TEXT
        e.setTextColor(onSurface)
        e.setHintTextColor(onSurfaceVar)
        e.textSize = 15f
        e.setPadding(dp(16), dp(12), dp(16), dp(12))
        e.background = shape(Color.TRANSPARENT, 12, 1, outline)
        return e
    }
}
