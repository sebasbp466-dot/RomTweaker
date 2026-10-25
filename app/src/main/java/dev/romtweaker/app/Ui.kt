package dev.romtweaker.app

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class Palette(
    val glass: Boolean,
    val bg: Int,
    val surface: Int,
    val onSurface: Int,
    val onVariant: Int,
    val primary: Int,
    val onPrimary: Int,
    val container: Int,
    val onContainer: Int,
    val outline: Int
)

fun makePalette(ctx: Context, glass: Boolean): Palette {
    if (glass) {
        return Palette(
            true, Color.parseColor("#0A0E24"), Color.parseColor("#26FFFFFF"),
            Color.WHITE, Color.parseColor("#B8FFFFFF"),
            Color.parseColor("#6C8CFF"), Color.WHITE,
            Color.parseColor("#33FFFFFF"), Color.WHITE, Color.parseColor("#66FFFFFF")
        )
    }
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    if (Build.VERSION.SDK_INT >= 31) {
        fun c(id: Int): Int = ctx.getColor(id)
        if (dark) {
            return Palette(
                false,
                c(android.R.color.system_neutral1_900), c(android.R.color.system_neutral1_800),
                c(android.R.color.system_neutral1_100), c(android.R.color.system_neutral2_200),
                c(android.R.color.system_accent1_200), c(android.R.color.system_accent1_800),
                c(android.R.color.system_accent2_700), c(android.R.color.system_accent2_100),
                c(android.R.color.system_neutral2_500)
            )
        }
        return Palette(
            false,
            c(android.R.color.system_neutral1_10), c(android.R.color.system_accent1_50),
            c(android.R.color.system_neutral1_900), c(android.R.color.system_neutral2_700),
            c(android.R.color.system_accent1_600), c(android.R.color.system_accent1_0),
            c(android.R.color.system_accent2_100), c(android.R.color.system_accent2_900),
            c(android.R.color.system_neutral2_500)
        )
    }
    if (dark) {
        return Palette(
            false, Color.parseColor("#141218"), Color.parseColor("#211F26"),
            Color.parseColor("#E6E0E9"), Color.parseColor("#CAC4D0"),
            Color.parseColor("#D0BCFF"), Color.parseColor("#381E72"),
            Color.parseColor("#4A4458"), Color.parseColor("#E8DEF8"), Color.parseColor("#938F99")
        )
    }
    return Palette(
        false, Color.parseColor("#FEF7FF"), Color.parseColor("#F3EDF7"),
        Color.parseColor("#1D1B20"), Color.parseColor("#49454F"),
        Color.parseColor("#6750A4"), Color.WHITE,
        Color.parseColor("#E8DEF8"), Color.parseColor("#1D192B"), Color.parseColor("#79747E")
    )
}

class Ui(val ctx: Context, val p: Palette) {
    private val anims = ArrayList<ObjectAnimator>()

    fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun round(fill: Int, r: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(r).toFloat()
        if (stroke != 0) g.setStroke(dp(1), stroke)
        return g
    }

    fun glassBg(r: Int, strong: Boolean = false): GradientDrawable {
        val top = if (strong) "#4DFFFFFF" else "#33FFFFFF"
        val g = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor(top), Color.parseColor("#0DFFFFFF"))
        )
        g.cornerRadius = dp(r).toFloat()
        g.setStroke(dp(1), Color.parseColor("#59FFFFFF"))
        return g
    }

    fun accentPill(): GradientDrawable {
        val g = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#4FC3FF"), Color.parseColor("#9B7BFF"))
        )
        g.cornerRadius = dp(100).toFloat()
        return g
    }

    fun ripple(base: Drawable): Drawable {
        val rc = if (p.glass) Color.parseColor("#40FFFFFF") else ((p.primary and 0x00FFFFFF) or 0x33000000)
        return RippleDrawable(ColorStateList.valueOf(rc), base, null)
    }

    fun row(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        return r
    }

    fun hscroll(inner: View): HorizontalScrollView {
        val h = HorizontalScrollView(ctx)
        h.isHorizontalScrollBarEnabled = false
        inner.layoutParams = FrameLayout.LayoutParams(WRAP, WRAP)
        h.addView(inner)
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.setMargins(0, dp(4), 0, dp(8))
        h.layoutParams = lp
        return h
    }

    fun card(): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(18), dp(16), dp(18), dp(16))
        l.background = if (p.glass) glassBg(28) else round(p.surface, 24)
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.setMargins(0, 0, 0, dp(14))
        l.layoutParams = lp
        return l
    }

    fun heading(t: String): TextView {
        val v = TextView(ctx)
        v.text = t
        v.setTextColor(p.onSurface)
        v.textSize = 17f
        v.typeface = Typeface.DEFAULT_BOLD
        v.setPadding(0, 0, 0, dp(6))
        return v
    }

    fun body(t: String): TextView {
        val v = TextView(ctx)
        v.text = t
        v.setTextColor(p.onVariant)
        v.textSize = 14f
        v.setPadding(0, 0, 0, dp(6))
        return v
    }

    fun button(t: String, filled: Boolean = false, onClick: () -> Unit): Button {
        val b = Button(ctx)
        b.text = t
        b.isAllCaps = false
        b.textSize = 14f
        b.stateListAnimator = null
        b.setTextColor(if (filled) p.onPrimary else p.onContainer)
        val base: Drawable = if (p.glass) {
            if (filled) accentPill() else glassBg(100, true)
        } else {
            if (filled) round(p.primary, 100) else round(p.container, 100)
        }
        b.background = ripple(base)
        b.minHeight = dp(46)
        b.minimumHeight = dp(46)
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.setMargins(0, dp(4), 0, dp(4))
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }

    fun chip(t: String, selected: Boolean, onClick: () -> Unit): TextView {
        val v = TextView(ctx)
        v.text = t
        v.textSize = 13f
        v.gravity = Gravity.CENTER
        v.setTextColor(if (selected) p.onPrimary else p.onContainer)
        v.setPadding(dp(14), dp(8), dp(14), dp(8))
        val base: Drawable = if (selected) {
            if (p.glass) accentPill() else round(p.primary, 100)
        } else {
            if (p.glass) glassBg(100) else round(p.container, 100)
        }
        v.background = ripple(base)
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.setMargins(0, 0, dp(8), 0)
        v.layoutParams = lp
        v.setOnClickListener { onClick() }
        return v
    }

    fun input(hint: String): EditText {
        val e = EditText(ctx)
        e.hint = hint
        e.setHintTextColor(p.onVariant)
        e.setTextColor(p.onSurface)
        e.setSingleLine()
        e.textSize = 15f
        e.background = if (p.glass) round(Color.parseColor("#1AFFFFFF"), 16, Color.parseColor("#40FFFFFF")) else round(p.container, 16)
        e.setPadding(dp(16), dp(12), dp(16), dp(12))
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.setMargins(0, dp(4), 0, dp(4))
        e.layoutParams = lp
        return e
    }

    fun toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(6), 0, dp(6))
        val tv = TextView(ctx)
        tv.text = label
        tv.setTextColor(p.onSurface)
        tv.textSize = 13f
        tv.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        val sw = Switch(ctx)
        sw.isChecked = checked
        val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(st, intArrayOf(p.onPrimary, p.outline))
        sw.trackTintList = ColorStateList(st, intArrayOf(p.primary, p.container))
        sw.setOnClickListener { onChange(sw.isChecked) }
        r.addView(tv)
        r.addView(sw)
        return r
    }

    fun navItem(label: String, selected: Boolean, onClick: () -> Unit): TextView {
        val t = TextView(ctx)
        t.text = label
        t.textSize = 12f
        t.gravity = Gravity.CENTER
        t.setTextColor(if (selected) p.onContainer else p.onVariant)
        t.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        t.setPadding(0, dp(10), 0, dp(10))
        if (selected) {
            t.background = ripple(if (p.glass) glassBg(100, true) else round(p.container, 100))
        }
        val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
        lp.setMargins(dp(3), 0, dp(3), 0)
        t.layoutParams = lp
        t.setOnClickListener { onClick() }
        return t
    }

    fun glassBackdrop(): FrameLayout {
        val f = FrameLayout(ctx)
        f.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#0A0E24"), Color.parseColor("#1B1048"), Color.parseColor("#082A45"))
        )
        blob(f, "#CC2BD4FF", 320, Gravity.TOP or Gravity.START, -90f, -20f, 70f, 90f, 9000L)
        blob(f, "#CCFF3DB4", 300, Gravity.CENTER_VERTICAL or Gravity.END, 110f, 40f, -60f, -80f, 11000L)
        blob(f, "#CCFFA23D", 280, Gravity.BOTTOM or Gravity.START, -60f, 60f, 80f, -40f, 13000L)
        return f
    }

    private fun blob(parent: FrameLayout, color: String, size: Int, grav: Int, x: Float, y: Float, dx: Float, dy: Float, dur: Long) {
        val c = Color.parseColor(color)
        val v = View(ctx)
        val g = GradientDrawable()
        g.setShape(GradientDrawable.OVAL)
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT)
        g.setGradientRadius(dp(size / 2).toFloat())
        g.setColors(intArrayOf(c, c and 0x00FFFFFF))
        v.background = g
        parent.addView(v, FrameLayout.LayoutParams(dp(size), dp(size), grav))
        val x0 = dp(x.toInt()).toFloat()
        val y0 = dp(y.toInt()).toFloat()
        v.translationX = x0
        v.translationY = y0
        val ax = ObjectAnimator.ofFloat(v, "translationX", x0, dp((x + dx).toInt()).toFloat())
        val ay = ObjectAnimator.ofFloat(v, "translationY", y0, dp((y + dy).toInt()).toFloat())
        for (a in listOf(ax, ay)) {
            a.duration = dur
            a.repeatCount = ObjectAnimator.INFINITE
            a.repeatMode = ObjectAnimator.REVERSE
            a.interpolator = AccelerateDecelerateInterpolator()
            a.start()
            anims.add(a)
        }
    }

    fun stop() {
        for (a in anims) a.cancel()
        anims.clear()
    }
}
