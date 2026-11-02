package dev.romtweaker.app

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Editor del reloj estilo HyperOS: arrastra con dos dedos (o un dedo sobre el reloj), pellizca para escalar,
 * guía de centrado con vibración, barra inferior y panel con pestañas Reloj / Fuente / Fondo.
 */
class ClockEditActivity : Activity() {
    private lateinit var m: M3
    private lateinit var cfg: StudioCfg
    private lateinit var clock: StudioClock
    private lateinit var root: FrameLayout
    private lateinit var stage: FrameLayout
    private lateinit var wallIv: ImageView
    private lateinit var guide: View
    private lateinit var frame: GradientDrawable
    private lateinit var topBar: FrameLayout
    private lateinit var hintTv: TextView
    private lateinit var bottomBar: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var body: LinearLayout
    private val tabViews = ArrayList<TextView>()
    private val rings = ArrayList<Pair<View, StudioCfg>>()
    private var tab = 0
    private var open = false
    private var rad = 0f
    private var sMin = 0.6f
    private var shrink = 0f
    private var shrinkAnim: ValueAnimator? = null
    private var scaleDet: ScaleGestureDetector? = null
    private var lx = 0f
    private var ly = 0f
    private var lastN = 0
    private var rawCx = 0f
    private var snapped = false
    private var grabbed = false
    private var moved = false
    private var inClock = false
    private var downT = 0L
    private var downX = 0f
    private var downY = 0f
    private var bgSel = -1
    private var customBg: Bitmap? = null
    private var fromGallery = false
    private var closing = false
    private var sw = 0
    private var sh = 0

    private val palette = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFFF7A3D.toInt(), 0xFF4DA3FF.toInt(), 0xFFFF5C9E.toInt(),
        0xFF4ADE80.toInt(), 0xFFFFD60A.toInt(), 0xFFB388FF.toInt(), 0xFF111111.toInt()
    )

    private fun dp(v: Float): Int = Hx.dp(this, v)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try { setShowWhenLocked(true) } catch (e: Throwable) { }
        try {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(WindowInsets.Type.systemBars())
            window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (e: Throwable) { }
        m = M3(this, true)
        val dm = resources.displayMetrics
        sw = dm.widthPixels
        sh = dm.heightPixels
        val ex = intent?.getStringExtra("cfg")
        fromGallery = ex != null
        cfg = if (ex != null) StudioCfg.parse(ex) else StudioCfg.load(this)
        rawCx = cfg.cx

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        stage = FrameLayout(this)
        stage.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, rad) }
        }
        stage.clipToOutline = true
        root.addView(stage, FrameLayout.LayoutParams(-1, -1))

        wallIv = ImageView(this)
        wallIv.scaleType = ImageView.ScaleType.CENTER_CROP
        val wd = LockStudio.wallpaper(this)
        if (wd != null) wallIv.setImageDrawable(wd) else wallIv.setImageBitmap(Hx.bgBitmap(this))
        stage.addView(wallIv, FrameLayout.LayoutParams(-1, -1))
        val dim = View(this)
        dim.setBackgroundColor(0x26000000)
        stage.addView(dim, FrameLayout.LayoutParams(-1, -1))

        guide = View(this)
        guide.setBackgroundColor(0xCCFFFFFF.toInt())
        guide.alpha = 0f
        stage.addView(guide, FrameLayout.LayoutParams(dp(1.5f), -1, Gravity.CENTER_HORIZONTAL))

        clock = StudioClock(this)
        frame = GradientDrawable()
        frame.cornerRadius = dp(18f).toFloat()
        frame.setColor(Color.TRANSPARENT)
        frame.setStroke(dp(2f), 0x99FFFFFF.toInt())
        clock.background = frame
        stage.addView(clock, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        clock.applyConfig(cfg)
        updatePos()

        // Barra superior: Cancelar / título + pista / Aplicar
        topBar = FrameLayout(this)
        topBar.addView(Hx.pill(this, "Cancelar", 0x2EFFFFFF, Color.WHITE) { cancel() }, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
        topBar.addView(Hx.pill(this, "Aplicar", Hx.BLUE, Color.WHITE) { applyAll() }, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        val titleCol = LinearLayout(this)
        titleCol.orientation = LinearLayout.VERTICAL
        titleCol.gravity = Gravity.CENTER_HORIZONTAL
        val title = TextView(this)
        title.text = "Pantalla de bloqueo"
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        title.typeface = Typeface.DEFAULT_BOLD
        titleCol.addView(title)
        hintTv = TextView(this)
        hintTv.text = "Pellizca y arrastra con dos dedos"
        hintTv.setTextColor(0xB3FFFFFF.toInt())
        hintTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        titleCol.addView(hintTv)
        topBar.addView(titleCol, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        val tl = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        tl.setMargins(dp(16f), dp(30f), dp(16f), 0)
        root.addView(topBar, tl)

        // Barra inferior: Fondo / IA / Profundidad / Efectos
        bottomBar = LinearLayout(this)
        bottomBar.orientation = LinearLayout.HORIZONTAL
        fun bbtn(kind: Int, label: String, click: () -> Unit) {
            val it1 = LinearLayout(this)
            it1.orientation = LinearLayout.VERTICAL
            it1.gravity = Gravity.CENTER_HORIZONTAL
            val circ = FrameLayout(this)
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(0x33FFFFFF)
            circ.background = g
            circ.addView(HxIcon(this, kind), FrameLayout.LayoutParams(-1, -1))
            it1.addView(circ, LinearLayout.LayoutParams(dp(54f), dp(54f)))
            val lab = TextView(this)
            lab.text = label
            lab.setTextColor(0xCCFFFFFF.toInt())
            lab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            lab.gravity = Gravity.CENTER
            val llp = LinearLayout.LayoutParams(-2, -2)
            llp.topMargin = dp(5f)
            it1.addView(lab, llp)
            it1.setOnClickListener { click() }
            Hx.press(it1)
            bottomBar.addView(it1, LinearLayout.LayoutParams(0, -2, 1f))
        }
        bbtn(0, "Fondo") { openPanel(2) }
        bbtn(1, "IA") { Toast.makeText(this, "IA: próximamente (necesita la API de Gemini)", Toast.LENGTH_SHORT).show() }
        bbtn(2, "Profundidad") { Toast.makeText(this, "Profundidad: próximamente (sujeto delante del reloj)", Toast.LENGTH_SHORT).show() }
        bbtn(3, "Efectos") { openPanel(1) }
        val bl = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        bl.setMargins(dp(22f), 0, dp(22f), dp(26f))
        root.addView(bottomBar, bl)

        // Panel inferior
        panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.isClickable = true
        panel.setPadding(dp(16f), dp(14f), dp(16f), dp(16f))
        val pbg = GradientDrawable()
        pbg.cornerRadius = dp(30f).toFloat()
        pbg.setColor(0xF2181B20.toInt())
        panel.background = pbg
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val x = TextView(this)
        x.text = "✕"
        x.setTextColor(Color.WHITE)
        x.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        x.gravity = Gravity.CENTER
        val xg = GradientDrawable()
        xg.shape = GradientDrawable.OVAL
        xg.setColor(0x26FFFFFF)
        x.background = xg
        x.setOnClickListener { closePanel() }
        Hx.press(x)
        head.addView(x, LinearLayout.LayoutParams(dp(38f), dp(38f)))
        val seg = LinearLayout(this)
        seg.orientation = LinearLayout.HORIZONTAL
        val sg = GradientDrawable()
        sg.cornerRadius = dp(40f).toFloat()
        sg.setColor(0x1FFFFFFF)
        seg.background = sg
        seg.setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
        for ((i, nm) in listOf("Reloj", "Fuente", "Fondo").withIndex()) {
            val t = TextView(this)
            t.text = nm
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(7f), 0, dp(7f))
            t.setOnClickListener { tab = i; refreshTabs(); fillBody() }
            seg.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
            tabViews.add(t)
        }
        val slp = LinearLayout.LayoutParams(0, -2, 1f)
        slp.leftMargin = dp(12f)
        head.addView(seg, slp)
        panel.addView(head, m.lp(m.MP, m.WC, 0, 0, 0, 10))
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(body)
        panel.addView(sv, LinearLayout.LayoutParams(-1, (sh * 0.34f).toInt()))
        val pl = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        pl.setMargins(dp(8f), 0, dp(8f), dp(8f))
        root.addView(panel, pl)
        panel.visibility = View.INVISIBLE
        val availH = sh - (sh * 0.34f + dp(110f)) - dp(84f) - dp(10f)
        sMin = (availH / sh).coerceIn(0.4f, 0.8f)
        refreshTabs()

        scaleDet = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: ScaleGestureDetector): Boolean { grab(true); return true }
            override fun onScale(d: ScaleGestureDetector): Boolean {
                cfg.sc = (cfg.sc * d.scaleFactor).coerceIn(40f, 300f)
                clock.applyConfig(cfg)
                return true
            }
        })
        stage.setOnTouchListener { _, e -> touch(e); true }
        setContentView(root)

        // Entrada suave de la interfaz (la escena ya viene expandida desde la galería)
        if (!fromGallery) { stage.alpha = 0f; stage.animate().alpha(1f).setDuration(260).start() }
        topBar.alpha = 0f
        topBar.translationY = -dp(14f).toFloat()
        topBar.animate().alpha(1f).translationY(0f).setStartDelay(120).setDuration(380).setInterpolator(DecelerateInterpolator(1.6f)).start()
        bottomBar.alpha = 0f
        bottomBar.translationY = dp(24f).toFloat()
        bottomBar.animate().alpha(1f).translationY(0f).setStartDelay(160).setDuration(420).setInterpolator(DecelerateInterpolator(1.6f)).start()
        clock.scaleX = 0.96f
        clock.scaleY = 0.96f
        clock.animate().scaleX(1f).scaleY(1f).setStartDelay(100).setDuration(420).setInterpolator(OvershootInterpolator(2.2f)).start()
        hintTv.postDelayed({ hintTv.animate().alpha(0.55f).setDuration(600).start() }, 4000)
    }

    private fun updatePos() {
        val lp = clock.layoutParams as FrameLayout.LayoutParams
        lp.topMargin = dp(cfg.top)
        clock.layoutParams = lp
        clock.translationX = cfg.cx / 100f * sw
    }

    private fun hitClock(rx: Float, ry: Float): Boolean {
        val loc = IntArray(2)
        clock.getLocationOnScreen(loc)
        val s = stage.scaleX * clock.scaleX
        val pad = dp(26f)
        return rx >= loc[0] - pad && rx <= loc[0] + clock.width * s + pad && ry >= loc[1] - pad && ry <= loc[1] + clock.height * s + pad
    }

    /** Efecto de "agarrar" el reloj: crece un poco, el marco se ilumina y aparece la guía de centrado. */
    private fun grab(on: Boolean) {
        if (grabbed == on) return
        grabbed = on
        clock.animate().cancel()
        clock.animate().scaleX(if (on) 1.04f else 1f).scaleY(if (on) 1.04f else 1f)
            .setDuration(if (on) 150L else 340L)
            .setInterpolator(if (on) DecelerateInterpolator() else OvershootInterpolator(3f)).start()
        frame.setStroke(dp(if (on) 3f else 2f), if (on) 0xFFFFFFFF.toInt() else 0x99FFFFFF.toInt())
        guide.animate().alpha(if (on) (if (snapped) 1f else 0.22f) else 0f).setDuration(170).start()
        if (on) hintTv.animate().alpha(0.3f).setDuration(150).start()
    }

    private fun touch(e: MotionEvent) {
        scaleDet?.onTouchEvent(e)
        val n = e.pointerCount
        var cx = 0f
        var cy = 0f
        for (i in 0 until n) { cx += e.getX(i); cy += e.getY(i) }
        cx /= n
        cy /= n
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downT = e.eventTime
                downX = e.rawX
                downY = e.rawY
                moved = false
                lastN = 0
                rawCx = cfg.cx
                inClock = hitClock(e.rawX, e.rawY)
                if (inClock) grab(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val up = e.actionMasked == MotionEvent.ACTION_UP
                val tapIn = up && !moved && inClock && e.eventTime - downT < 320
                val tapOut = up && !moved && !inClock && open
                grab(false)
                lastN = 0
                clock.digits.applyFx()
                if (tapIn) openPanel(0) else if (tapOut) closePanel()
            }
            MotionEvent.ACTION_POINTER_UP -> { lastN = -1 }
            else -> {
                if (n != lastN) { lx = cx; ly = cy; lastN = n }
                if (e.actionMasked == MotionEvent.ACTION_MOVE) {
                    val slop = ViewConfiguration.get(this).scaledTouchSlop
                    if (!moved && (abs(e.rawX - downX) > slop || abs(e.rawY - downY) > slop || n >= 2)) moved = true
                    if (n >= 2 || (inClock && moved)) {
                        if (!grabbed) grab(true)
                        val dm = resources.displayMetrics
                        val s = stage.scaleX
                        cfg.top = (cfg.top + (cy - ly) / dm.density / s).coerceIn(0f, 700f)
                        rawCx = (rawCx + (cx - lx) / dm.widthPixels * 100f / s).coerceIn(-60f, 60f)
                        val was = snapped
                        snapped = abs(rawCx) < 2.0f
                        cfg.cx = if (snapped) 0f else rawCx
                        if (snapped != was) {
                            if (snapped) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            guide.animate().alpha(if (snapped) 1f else 0.22f).setDuration(120).start()
                        }
                        lx = cx
                        ly = cy
                        updatePos()
                    }
                }
            }
        }
    }

    // ---------- Panel ----------

    private fun refreshTabs() {
        for ((i, t) in tabViews.withIndex()) {
            val sel = i == tab
            t.setTextColor(if (sel) Color.WHITE else 0xB3FFFFFF.toInt())
            t.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val g = GradientDrawable()
            g.cornerRadius = dp(40f).toFloat()
            g.setColor(if (sel) Hx.BLUE else Color.TRANSPARENT)
            t.background = g
        }
    }

    private fun animShrink(to: Float) {
        shrinkAnim?.cancel()
        val a = ValueAnimator.ofFloat(shrink, to)
        a.duration = 440
        a.interpolator = DecelerateInterpolator(1.8f)
        a.addUpdateListener { shrink = it.animatedValue as Float; applyShrink() }
        shrinkAnim = a
        a.start()
    }

    private fun applyShrink() {
        val s = 1f - shrink * (1f - sMin)
        stage.pivotX = sw / 2f
        stage.pivotY = 0f
        stage.scaleX = s
        stage.scaleY = s
        stage.translationY = shrink * dp(84f)
        rad = shrink * dp(26f) / s
        stage.invalidateOutline()
    }

    private fun openPanel(t: Int) {
        tab = t
        refreshTabs()
        fillBody()
        if (open) return
        open = true
        panel.visibility = View.VISIBLE
        panel.post {
            panel.translationY = panel.height.toFloat()
            panel.animate().translationY(0f).setDuration(440).setInterpolator(DecelerateInterpolator(1.9f)).start()
        }
        animShrink(1f)
        bottomBar.animate().alpha(0f).setDuration(160).start()
        topBar.animate().translationY(dp(0f).toFloat()).start()
    }

    private fun closePanel() {
        if (!open) return
        open = false
        panel.animate().translationY(panel.height.toFloat()).setDuration(300).setInterpolator(DecelerateInterpolator())
            .withEndAction { if (!open) panel.visibility = View.INVISIBLE }.start()
        animShrink(0f)
        bottomBar.animate().alpha(1f).setStartDelay(120).setDuration(300).start()
    }

    private fun lab(t: String): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextColor(0xB3FFFFFF.toInt())
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        v.setPadding(0, dp(12f), 0, dp(6f))
        return v
    }

    private fun slider(label: String, min: Int, max: Int, v: Int, on: (Int) -> Unit): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.text = "$label: $v"
        t.setTextColor(0xB3FFFFFF.toInt())
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        t.setPadding(0, dp(8f), 0, 0)
        l.addView(t)
        val sb = SeekBar(this)
        tint(sb)
        sb.max = max - min
        sb.progress = v - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, pr: Int, fromUser: Boolean) {
                t.text = "$label: ${pr + min}"
                if (fromUser) { on(pr + min); clock.applyConfig(cfg) }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        l.addView(sb, LinearLayout.LayoutParams(-1, -2))
        return l
    }

    private fun tint(sb: SeekBar) {
        try {
            sb.progressTintList = ColorStateList.valueOf(Hx.BLUE)
            sb.thumbTintList = ColorStateList.valueOf(Color.WHITE)
            sb.progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
        } catch (e: Throwable) { }
    }

    private fun swatch(col: Int, sel: Boolean, click: () -> Unit): View {
        val v = View(this)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(col)
        g.setStroke(dp(if (sel) 3f else 1f), if (sel) Hx.BLUE else 0x55FFFFFF)
        v.background = g
        v.setOnClickListener { click() }
        Hx.press(v)
        return v
    }

    private fun applyPreset(p: StudioCfg) {
        cfg.layout = p.layout; cfg.wght = p.wght; cfg.wdth = p.wdth; cfg.rond = p.rond; cfg.slnt = p.slnt
        cfg.fx = p.fx; cfg.tr = p.tr; cfg.color = p.color; cfg.size = p.size; cfg.font = p.font; cfg.ol = p.ol
        clock.applyConfig(cfg)
        clock.scaleX = 0.94f
        clock.scaleY = 0.94f
        clock.animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(OvershootInterpolator(2.6f)).start()
        refreshRings()
    }

    private fun refreshRings() {
        for ((v, c) in rings) {
            val g = GradientDrawable()
            g.cornerRadius = dp(14f).toFloat()
            g.setColor(Color.TRANSPARENT)
            g.setStroke(dp(3f), if (StudioPresets.same(cfg, c)) Hx.BLUE else Color.TRANSPARENT)
            v.foreground = g
        }
    }

    private fun fillBody() {
        body.removeAllViews()
        rings.clear()
        when (tab) {
            0 -> {
                val tw = dp(66f)
                val th = (tw * sh / sw.toFloat()).toInt()
                val bg = Hx.bgBitmap(this)
                for (c in StudioPresets.cats) {
                    body.addView(lab(c))
                    val hs = HorizontalScrollView(this)
                    hs.isHorizontalScrollBarEnabled = false
                    val row = LinearLayout(this)
                    row.orientation = LinearLayout.HORIZONTAL
                    for (pr in StudioPresets.all.filter { it.cat == c }) {
                        val t = StudioTile.make(this, pr.cfg, tw, th, 14f, bg)
                        t.setOnClickListener { applyPreset(pr.cfg) }
                        Hx.press(t)
                        rings.add(Pair(t, pr.cfg))
                        val lp = LinearLayout.LayoutParams(tw, th)
                        lp.rightMargin = dp(10f)
                        row.addView(t, lp)
                    }
                    hs.addView(row)
                    body.addView(hs, LinearLayout.LayoutParams(-1, -2))
                }
                refreshRings()
            }
            1 -> fillFont()
            else -> fillBg()
        }
    }

    private class Fs(val w: Int, val wd: Int, val ro: Int, val font: String)

    private fun fillFont() {
        body.addView(lab("Fuente"))
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val list = listOf(Fs(200, 70, 0, "flex"), Fs(400, 90, 0, "flex"), Fs(600, 100, 0, "flex"), Fs(800, 100, 0, "flex"),
            Fs(600, 100, 100, "flex"), Fs(500, 140, 0, "flex"), Fs(400, 100, 0, "serif"), Fs(500, 100, 0, "mono"))
        for (f in list) {
            val t = TextView(this)
            t.text = "35"
            t.setTextColor(Color.WHITE)
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            t.gravity = Gravity.CENTER
            when (f.font) {
                "serif" -> t.typeface = Typeface.create(Typeface.SERIF, f.w, false)
                "mono" -> t.typeface = Typeface.create(Typeface.MONOSPACE, f.w, false)
                else -> {
                    t.typeface = StudioFont.get()
                    try { t.fontVariationSettings = "'wght' ${f.w},'wdth' ${f.wd},'ROND' ${f.ro}" } catch (e: Throwable) { }
                }
            }
            val sel = cfg.wght == f.w && cfg.wdth == f.wd && cfg.rond == f.ro && cfg.font == f.font
            val g = GradientDrawable()
            g.cornerRadius = dp(16f).toFloat()
            g.setColor(0x1FFFFFFF)
            g.setStroke(dp(if (sel) 3f else 1f), if (sel) Hx.BLUE else 0x22FFFFFF)
            t.background = g
            t.setOnClickListener {
                cfg.wght = f.w; cfg.wdth = f.wd; cfg.rond = f.ro; cfg.font = f.font
                clock.applyConfig(cfg)
                fillBody()
            }
            Hx.press(t)
            val lp = LinearLayout.LayoutParams(dp(66f), dp(66f))
            lp.rightMargin = dp(10f)
            row.addView(t, lp)
        }
        hs.addView(row)
        body.addView(hs, LinearLayout.LayoutParams(-1, -2))

        // Tamaño: A pequeña — barra — A grande
        val sr = LinearLayout(this)
        sr.orientation = LinearLayout.HORIZONTAL
        sr.gravity = Gravity.CENTER_VERTICAL
        val a1 = TextView(this)
        a1.text = "A"
        a1.setTextColor(0xB3FFFFFF.toInt())
        a1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        val a2 = TextView(this)
        a2.text = "A"
        a2.setTextColor(Color.WHITE)
        a2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        val sb = SeekBar(this)
        tint(sb)
        sb.max = 160
        sb.progress = (cfg.size - 100).coerceIn(0, 160)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, pr: Int, fromUser: Boolean) { if (fromUser) { cfg.size = pr + 100; clock.applyConfig(cfg) } }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        sr.addView(a1)
        sr.addView(sb, LinearLayout.LayoutParams(0, -2, 1f))
        sr.addView(a2)
        body.addView(sr, m.lp(m.MP, m.WC, 0, 10, 0, 0))

        body.addView(lab("Efectos"))
        val er = LinearLayout(this)
        er.orientation = LinearLayout.HORIZONTAL
        for (p in listOf("solid" to "Sólido", "blend" to "Mezcla", "glass" to "Cristal", "overlay" to "Superpos.")) {
            val sel = cfg.fx == p.first
            val t = TextView(this)
            t.text = p.second
            t.setTextColor(Color.WHITE)
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(11f), 0, dp(11f))
            val g = GradientDrawable()
            g.cornerRadius = dp(14f).toFloat()
            g.setColor(if (sel) 0x333D8BFF else 0x1AFFFFFF)
            g.setStroke(dp(if (sel) 2.5f else 1f), if (sel) Hx.BLUE else 0x22FFFFFF)
            t.background = g
            t.setOnClickListener { cfg.fx = p.first; clock.applyConfig(cfg); fillBody() }
            Hx.press(t)
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.rightMargin = dp(6f)
            er.addView(t, lp)
        }
        body.addView(er, LinearLayout.LayoutParams(-1, -2))
        body.addView(slider("Transparencia", 0, 100, cfg.tr) { cfg.tr = it })

        body.addView(lab("Color"))
        val cs = HorizontalScrollView(this)
        cs.isHorizontalScrollBarEnabled = false
        val cr = LinearLayout(this)
        cr.orientation = LinearLayout.HORIZONTAL
        for (c in palette) {
            val col = c
            val lp = LinearLayout.LayoutParams(dp(36f), dp(36f))
            lp.rightMargin = dp(10f)
            cr.addView(swatch(col, cfg.color == col) { cfg.color = col; clock.applyConfig(cfg); fillBody() }, lp)
        }
        cs.addView(cr)
        body.addView(cs, LinearLayout.LayoutParams(-1, -2))

        body.addView(m.switchRow("Mostrar fecha", "", cfg.date) { v -> cfg.date = v; clock.applyConfig(cfg) }, m.lp(m.MP, m.WC, 0, 10, 0, 0))
        body.addView(m.switchRow("Solo contorno", "", cfg.ol == 1) { v -> cfg.ol = if (v) 1 else 0; clock.applyConfig(cfg) }, m.lp(m.MP, m.WC, 0, 0, 0, 0))
        body.addView(lab("Ajustes finos"))
        body.addView(slider("Grosor", 100, 900, cfg.wght) { cfg.wght = it })
        body.addView(slider("Ancho", 50, 150, cfg.wdth) { cfg.wdth = it })
        body.addView(slider("Redondez", 0, 100, cfg.rond) { cfg.rond = it })
        body.addView(slider("Inclinación", -10, 0, cfg.slnt) { cfg.slnt = it })
    }

    // ---------- Fondo ----------

    private fun selectBg(idx: Int) {
        bgSel = idx
        when {
            idx in 0 until StudioBg.count -> wallIv.setImageBitmap(StudioBg.gen(idx, sw / 2, sh / 2))
            idx == 100 && customBg != null -> wallIv.setImageBitmap(customBg)
            else -> {
                val wd = LockStudio.wallpaper(this)
                if (wd != null) wallIv.setImageDrawable(wd) else wallIv.setImageBitmap(Hx.bgBitmap(this))
            }
        }
        wallIv.alpha = 0.4f
        wallIv.animate().alpha(1f).setDuration(320).start()
        fillBody()
    }

    private fun fillBg() {
        body.addView(lab("Fondo de la pantalla de bloqueo"))
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        fun dot(idx: Int, b: Bitmap?, label: String?) {
            val v = FrameLayout(this)
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            if (b != null) iv.setImageBitmap(b) else iv.setBackgroundColor(0xFF2B3238.toInt())
            v.addView(iv, FrameLayout.LayoutParams(-1, -1))
            if (label != null) {
                val t = TextView(this)
                t.text = label
                t.setTextColor(Color.WHITE)
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                t.typeface = Typeface.DEFAULT_BOLD
                v.addView(t, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            }
            v.clipToOutline = true
            v.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v2: View, o: Outline) { o.setRoundRect(0, 0, v2.width, v2.height, dp(14f).toFloat()) }
            }
            val g = GradientDrawable()
            g.cornerRadius = dp(14f).toFloat()
            g.setColor(Color.TRANSPARENT)
            g.setStroke(dp(3f), if (bgSel == idx) Hx.BLUE else Color.TRANSPARENT)
            v.foreground = g
            v.setOnClickListener { if (idx == 100) pickImage() else selectBg(idx) }
            Hx.press(v)
            val lp = LinearLayout.LayoutParams(dp(62f), dp(106f))
            lp.rightMargin = dp(10f)
            row.addView(v, lp)
        }
        dot(-1, Hx.bgBitmap(this), "Actual")
        for (i in 0 until StudioBg.count) dot(i, StudioBg.gen(i, 90, 160), null)
        dot(100, customBg, "Galería")
        hs.addView(row)
        body.addView(hs, LinearLayout.LayoutParams(-1, -2))
        val note = TextView(this)
        note.text = "El fondo se cambia al pulsar Aplicar."
        note.setTextColor(0x88FFFFFF.toInt())
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        note.setPadding(0, dp(10f), 0, 0)
        body.addView(note)
    }

    private fun pickImage() {
        try {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "image/*"
            startActivityForResult(i, 78)
        } catch (e: Throwable) { Toast.makeText(this, "No se pudo abrir la galería", Toast.LENGTH_SHORT).show() }
    }

    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        if (rc != 78 || res != RESULT_OK) return
        val u = data?.data ?: return
        try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
            var ss = 1
            while (maxOf(o.outWidth, o.outHeight) / ss > 2400) ss *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = ss
            customBg = contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o2) }
            if (customBg != null) selectBg(100)
        } catch (e: Throwable) { Toast.makeText(this, "No se pudo leer la imagen", Toast.LENGTH_SHORT).show() }
    }

    // ---------- Salir / Aplicar ----------

    private fun cancel() {
        if (closing) return
        closing = true
        setResult(RESULT_CANCELED)
        root.animate().alpha(0f).setDuration(220).withEndAction { finish(); overridePendingTransition(0, 0) }.start()
    }

    private fun applyAll() {
        if (closing) return
        closing = true
        val p = getSharedPreferences("lock", MODE_PRIVATE)
        p.edit().putString("sc_cfg", cfg.enc()).putBoolean("sc_on", true).putBoolean("ic_on", false).apply()
        val sel = bgSel
        val cb = customBg
        Thread {
            var ok = true
            if (sel in 0 until StudioBg.count) ok = StudioBg.setLock(this, StudioBg.gen(sel, sw, sh))
            else if (sel == 100 && cb != null) ok = StudioBg.setLock(this, cb)
            val pushed = StudioPush.push(p)
            runOnUiThread {
                Toast.makeText(this, if (!ok) "No pude cambiar el fondo" else if (pushed) "Aplicado. Bloquea la pantalla para verlo" else "Guardado, pero el módulo LSPosed no está activo", Toast.LENGTH_LONG).show()
                setResult(RESULT_OK)
                root.animate().alpha(0f).scaleX(0.95f).scaleY(0.95f).setDuration(260).withEndAction { finish(); overridePendingTransition(0, 0) }.start()
            }
        }.start()
    }

    override fun onBackPressed() { if (open) closePanel() else cancel() }
}

/** Envía la configuración del editor al módulo LSPosed. */
object StudioPush {
    private fun bridge(name: String, k: String, v: Any): Boolean = try {
        val c = Class.forName("dev.romtweaker.app.xp.XpBridge")
        val inst = c.getField("INSTANCE").get(null)
        val mt = c.methods.first { it.name == name && it.parameterTypes.size == 2 }
        (mt.invoke(inst, k, v) as? Boolean) ?: true
    } catch (e: Throwable) { false }

    fun push(p: android.content.SharedPreferences): Boolean {
        var ok = bridge("putString", "sc_cfg", p.getString("sc_cfg", "") ?: "")
        ok = bridge("putBool", "sc_on", p.getBoolean("sc_on", false)) && ok
        ok = bridge("putBool", "ic_on", p.getBoolean("ic_on", false)) && ok
        return ok
    }

    fun pushLp(p: android.content.SharedPreferences): Boolean = bridge("putBool", "lp_on", p.getBoolean("lp_on", false))
}

/** Tarjeta en la pestaña Bloqueo para abrir el editor. */
object StudioCard {
    fun card(act: Activity, m: M3, say: (String) -> Unit): View {
        val p = act.getSharedPreferences("lock", Activity.MODE_PRIVATE)
        val c = m.card()
        c.addView(m.tv("Editor estilo HyperOS", 18f, m.onSurface, true))
        c.addView(m.tv("Mueve y escala el reloj con dos dedos, elige estilo, fuente y efectos (Sólido, Mezcla, Cristal, Superposición). Sustituye al reloj de tu pantalla de bloqueo.", 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 4, 0, 10))
        c.addView(m.switchRow("Usar el reloj del editor", "Oculta el reloj y la fecha de serie", p.getBoolean("sc_on", false)) { v ->
            p.edit().putBoolean("sc_on", v).apply()
            if (v && p.getString("sc_cfg", "").isNullOrEmpty()) p.edit().putString("sc_cfg", StudioCfg().enc()).apply()
            val ok = StudioPush.push(p)
            if (!ok) say("El módulo no está activo en LSPosed")
            else if (v) say("Aplicado. Bloquea la pantalla para verlo")
            else { say("Restaurando el reloj original (reiniciando SystemUI)…"); Thread { Sx.su("killall com.android.systemui") }.start() }
        })
        c.addView(m.switchRow("Abrir con pulsación larga en el bloqueo", "Mantén pulsado un espacio vacío de la pantalla de bloqueo", p.getBoolean("lp_on", false)) { v ->
            p.edit().putBoolean("lp_on", v).apply()
            val ok = StudioPush.pushLp(p)
            say(if (!ok) "El módulo no está activo en LSPosed" else "Guardado. Reinicia SystemUI para aplicar")
        }, m.lp(m.MP, m.WC, 0, 8, 0, 0))
        val br = LinearLayout(act)
        br.orientation = LinearLayout.HORIZONTAL
        br.addView(m.filled("Galería de estilos") { act.startActivity(Intent(act, LockGalleryActivity::class.java)) }, m.lp(m.WC, m.WC, 0, 0, 8, 0))
        br.addView(m.tonal("Abrir editor") { act.startActivity(Intent(act, ClockEditActivity::class.java)) }, m.lp(m.WC, m.WC))
        c.addView(br, m.lp(m.WC, m.WC, 0, 12, 0, 0))
        c.addView(m.tonal("Reiniciar SystemUI") { say("Reiniciando SystemUI…"); Thread { Sx.su("killall com.android.systemui") }.start() }, m.lp(m.WC, m.WC, 0, 8, 0, 0))
        return c
    }
}
