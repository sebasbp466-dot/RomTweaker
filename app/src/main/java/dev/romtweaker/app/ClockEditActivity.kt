package dev.romtweaker.app

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.Choreographer
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
    private val INK = 0xFF14171B.toInt()
    private val INKD = 0x99141719.toInt()
    private var wallBase: Bitmap? = null
    private var wz = 1f
    private var wpx = 0f
    private var wpy = 0f
    private var wBlur = 0
    private var wBright = 0
    private var wSat = 100
    private var target = 0
    private var wallMode = false
    private var bodySv: ScrollView? = null
    private val hist = ArrayList<String>()
    private var aiSheet: AiSheet? = null
    private var depthWarned = false
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
        wallIv.scaleType = ImageView.ScaleType.MATRIX
        wallBase = StudioWall.hi(this) ?: Hx.bgBitmap(this)
        wallIv.setImageBitmap(wallBase)
        stage.addView(wallIv, FrameLayout.LayoutParams(-1, -1))
        target = getSharedPreferences("lock", MODE_PRIVATE).getInt("wall_target", 0)
        applyWallMatrix()
        val dim = View(this)
        dim.setBackgroundColor(0x26000000)
        stage.addView(dim, FrameLayout.LayoutParams(-1, -1))

        guide = View(this)
        guide.setBackgroundColor(0xCCFFFFFF.toInt())
        guide.alpha = 0f
        stage.addView(guide, FrameLayout.LayoutParams(dp(1.5f), -1, Gravity.CENTER_HORIZONTAL))

        clock = StudioClock(this)
        clock.edit = true
        hist.add(cfg.enc())
        depthMask = getSharedPreferences("lock", MODE_PRIVATE).getString("depth_mask", "") ?: ""
        if (cfg.depth == 1) {
            val mk = StudioDepth.decode(depthMask)
            if (mk == null) cfg.depth = 0
            else Thread {
                val wb = StudioWall.hi(this)
                val fg = if (wb != null) StudioDepth.compose(wb, mk) else null
                runOnUiThread { if (fg != null) { depthFg = fg; showDepth(true) } else cfg.depth = 0 }
            }.start()
        }
        baseTop = cfg.top
        stage.addView(clock, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        clock.applyConfig(cfg)
        initPos()

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
        hintTv.text = "Zoom y desplazamiento con dos dedos"
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
            it1.addView(circ, LinearLayout.LayoutParams(dp(46f), dp(46f)))
            val lab = TextView(this)
            lab.text = label
            lab.setTextColor(0xCCFFFFFF.toInt())
            lab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            lab.gravity = Gravity.CENTER
            val llp = LinearLayout.LayoutParams(-2, -2)
            llp.topMargin = dp(5f)
            it1.addView(lab, llp)
            it1.setOnClickListener { click() }
            Hx.press(it1)
            bottomBar.addView(it1, LinearLayout.LayoutParams(0, -2, 1f))
        }
        bbtn(0, "Fondo") { openPanel(3) }
        bbtn(1, "IA") { aiDialog() }
        bbtn(2, "Profundidad") { toggleDepth() }
        bbtn(3, "Efectos") { openPanel(1) }
        bbtn(4, "Sorpresa") { surprise() }
        bbtn(5, "Deshacer") { undo() }
        val bl = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        bl.setMargins(dp(10f), 0, dp(10f), dp(26f))
        root.addView(bottomBar, bl)

        // Panel inferior
        panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.isClickable = true
        panel.setPadding(dp(16f), dp(14f), dp(16f), dp(22f))
        panel.background = FrostDrawable(frostBmp(), dp(30f).toFloat(), sh)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val x = TextView(this)
        x.text = "✕"
        x.setTextColor(INK)
        x.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        x.gravity = Gravity.CENTER
        val xg = GradientDrawable()
        xg.shape = GradientDrawable.OVAL
        xg.setColor(0x14000000)
        x.background = xg
        x.setOnClickListener { closePanel() }
        Hx.press(x)
        head.addView(x, LinearLayout.LayoutParams(dp(38f), dp(38f)))
        val seg = LinearLayout(this)
        seg.orientation = LinearLayout.HORIZONTAL
        val sgd = GradientDrawable()
        sgd.cornerRadius = dp(40f).toFloat()
        sgd.setColor(0x14000000)
        seg.background = sgd
        seg.setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
        for ((i, nm) in listOf("Reloj", "Fuente", "Color", "Fondo").withIndex()) {
            val t = TextView(this)
            t.text = nm
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
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
        bodySv = sv
        sv.isVerticalScrollBarEnabled = false
        sv.addView(body)
        panel.addView(sv, LinearLayout.LayoutParams(-1, (sh * 0.34f).toInt()))
        val pl = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        pl.setMargins(0, 0, 0, 0)
        root.addView(panel, pl)
        panel.visibility = View.INVISIBLE
        val availH = sh - (sh * 0.34f + dp(110f)) - dp(84f) - dp(10f)
        refreshTabs()

        scaleDet = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: ScaleGestureDetector): Boolean { if (!wallMode) grab(true); return true }
            override fun onScale(d: ScaleGestureDetector): Boolean {
                if (wallMode) { wz = (wz * d.scaleFactor).coerceIn(1f, 4f); applyWallMatrix(); return true }
                cfg.sc = (cfg.sc * d.scaleFactor).coerceIn(40f, 300f)
                clock.applyConfig(cfg)
                return true
            }
        })
        stage.setOnTouchListener { _, e -> touch(e); true }
        setContentView(root)

        // La escena es una tarjeta (0.72 de la pantalla), como en HyperOS; viene ya así desde la galería
        sMin = (availH / sh).coerceIn(0.35f, 0.8f)
        if (fromGallery) { applyShrink() } else { fullP = 1f; applyShrink(); animFull(0f, null) }
        topBar.alpha = 0f
        topBar.translationY = -dp(14f).toFloat()
        topBar.animate().alpha(1f).translationY(0f).setStartDelay(120).setDuration(380).setInterpolator(DecelerateInterpolator(1.6f)).start()
        bottomBar.alpha = 0f
        bottomBar.translationY = dp(24f).toFloat()
        bottomBar.animate().alpha(1f).translationY(0f).setStartDelay(160).setDuration(420).setInterpolator(DecelerateInterpolator(1.6f)).start()
        sg.x = 0.93f; sg.t = 1f; kick()
        hintTv.postDelayed({ hintTv.animate().alpha(0.55f).setDuration(600).start() }, 4000)
    }

    private class Spr(var x: Float, val k: Float = 340f, val c: Float = 21f) {
        var v = 0f
        var t = x
        fun step(dt: Float) { val a = k * (t - x) - c * v; v += a * dt; x += v * dt }
        fun settled() = abs(t - x) < 0.25f && abs(v) < 4f
    }
    private val sx = Spr(0f)
    private val sy = Spr(0f)
    private val sg = Spr(1f, 520f, 25f)
    private var baseTop = 0f
    private var ticking = false
    private var lastT = 0L
    private var baseS = Hx.CARD
    private var fullP = 0f

    private val tick = object : Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            val dt = if (lastT == 0L) 0.016f else ((t - lastT) / 1e9f).coerceIn(0.004f, 0.033f)
            lastT = t
            sx.step(dt); sy.step(dt); sg.step(dt)
            clock.translationX = sx.x
            clock.translationY = sy.x
            clock.rotation = (sx.v / 380f).coerceIn(-8f, 8f)
            val sq = ((abs(sx.v) - abs(sy.v)) / 22000f).coerceIn(-0.07f, 0.07f)
            clock.scaleX = sg.x * (1f + sq)
            clock.scaleY = sg.x * (1f - sq)
            if (sx.settled() && sy.settled() && abs(sg.t - sg.x) < 0.002f && abs(sg.v) < 0.05f) {
                clock.translationX = sx.t; clock.translationY = sy.t; clock.rotation = 0f; clock.scaleX = sg.t; clock.scaleY = sg.t
                ticking = false; lastT = 0L
            } else Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun kick() {
        if (ticking) return
        ticking = true
        lastT = 0L
        Choreographer.getInstance().postFrameCallback(tick)
    }

    private fun initPos() {
        val lp = clock.layoutParams as FrameLayout.LayoutParams
        lp.topMargin = dp(baseTop)
        clock.layoutParams = lp
        sx.x = cfg.cx / 100f * sw; sx.t = sx.x
        sy.x = (cfg.top - baseTop) * resources.displayMetrics.density; sy.t = sy.x
        clock.translationX = sx.x
        clock.translationY = sy.x
    }

    private fun updatePos() {
        sx.t = cfg.cx / 100f * sw
        sy.t = (cfg.top - baseTop) * resources.displayMetrics.density
        kick()
    }

    private var tapKind = 0

    private fun cycleLayout() {
        val ic = cfg.ic > 0
        val an = !ic && cfg.layout == "analog"
        val v: View = if (ic) clock else if (an) clock.analog else clock.digits
        v.animate().cancel()
        v.animate().alpha(0f).setDuration(170).withEndAction {
            if (ic) cfg.ic = if (cfg.ic >= IcClocks.COUNT) 1 else cfg.ic + 1
            else if (an) cfg.an = cfg.an % 12 + 1
            else {
                val order = listOf("stack", "inline", "col", "grid", "diag", "words")
                cfg.layout = order[(order.indexOf(cfg.layout) + 1) % order.size]
            }
            clock.applyConfig(cfg)
            snap()
            v.scaleX = 0.93f; v.scaleY = 0.93f
            v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(OvershootInterpolator(2.4f)).start()
        }.start()
        root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun sigDialog() {
        val et = android.widget.EditText(this)
        et.setText(cfg.sig)
        et.hint = "Tu firma"
        android.app.AlertDialog.Builder(this).setTitle("Firma").setView(et)
            .setPositiveButton("Listo") { _, _ -> cfg.sig = et.text.toString().take(40); clock.applyConfig(cfg) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun dateCycle() {
        cfg.datef = (cfg.datef + 1) % 6
        clock.applyConfig(cfg)
        root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun hitClock(rx: Float, ry: Float): Boolean {
        val loc = IntArray(2)
        clock.getLocationOnScreen(loc)
        val s = stage.scaleX * clock.scaleX
        val pad = dp(60f)
        return rx >= loc[0] - pad && rx <= loc[0] + clock.width * s + pad && ry >= loc[1] - pad && ry <= loc[1] + clock.height * s + pad
    }

    /** Efecto de "agarrar" el reloj: crece un poco, el marco se ilumina y aparece la guía de centrado. */
    private fun grab(on: Boolean) {
        if (grabbed == on) return
        grabbed = on
        sg.t = if (on) 1.06f else 1f
        sg.v += if (on) 2.2f else -1.6f
        kick()
        clock.hot = on
        guide.animate().alpha(if (on) (if (snapped) 1f else 0.22f) else 0f).setDuration(170).start()
        if (on) hintTv.animate().alpha(0.3f).setDuration(150).start()
    }

    private fun touch(e: MotionEvent) {
        if (e.actionMasked == MotionEvent.ACTION_POINTER_DOWN && e.pointerCount == 2) {
            wallMode = !hitClock((e.getRawX(0) + e.getRawX(1)) / 2f, (e.getRawY(0) + e.getRawY(1)) / 2f)
            if (wallMode) hintTv.animate().alpha(0.9f).setDuration(120).start()
        }
        if (e.actionMasked == MotionEvent.ACTION_DOWN) wallMode = false
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
                tapKind = 0
                if (inClock) {
                    val loc = IntArray(2)
                    clock.getLocationOnScreen(loc)
                    val k = stage.scaleX * clock.scaleX
                    val lx0 = (e.rawX - loc[0]) / k
                    val ly0 = (e.rawY - loc[1]) / k
                    tapKind = if (clock.cycleHit(lx0, ly0)) 1 else if (clock.sigHit(lx0, ly0)) 2 else if (clock.dateHit(lx0, ly0)) 3 else 0
                    if (tapKind == 0) grab(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val up = e.actionMasked == MotionEvent.ACTION_UP
                val tapIn = up && !moved && inClock && e.eventTime - downT < 320
                if (up && moved && !wallMode) snap()
                val tapOut = up && !moved && !inClock && open
                grab(false)
                lastN = 0
                if (!tapIn) clock.digits.applyFx()
                if (tapIn) { when (tapKind) { 1 -> cycleLayout(); 2 -> sigDialog(); 3 -> dateCycle(); else -> openPanel(0) } } else if (tapOut) closePanel()
            }
            MotionEvent.ACTION_POINTER_UP -> { lastN = -1 }
            else -> {
                if (n != lastN) { lx = cx; ly = cy; lastN = n }
                if (e.actionMasked == MotionEvent.ACTION_MOVE) {
                    val slop = ViewConfiguration.get(this).scaledTouchSlop
                    if (!moved && (abs(e.rawX - downX) > slop || abs(e.rawY - downY) > slop || n >= 2)) moved = true
                    if (n >= 2 && wallMode) {
                        val s = stage.scaleX
                        wpx += (cx - lx) / s
                        wpy += (cy - ly) / s
                        lx = cx
                        ly = cy
                        applyWallMatrix()
                    } else if (n >= 2 || moved) {
                        if (!grabbed) grab(true)
                        tapKind = 0
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
            t.setTextColor(if (sel) INK else INKD)
            t.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val g = GradientDrawable()
            g.cornerRadius = dp(40f).toFloat()
            g.setColor(if (sel) 0xFFFFFFFF.toInt() else Color.TRANSPARENT)
            t.background = g
        }
    }

    private fun animShrink(to: Float) {
        shrinkAnim?.cancel()
        val a = ValueAnimator.ofFloat(shrink, to)
        a.duration = 460
        a.interpolator = DecelerateInterpolator(1.8f)
        a.addUpdateListener { shrink = it.animatedValue as Float; applyShrink() }
        shrinkAnim = a
        a.start()
    }

    private fun applyShrink() {
        val sOpen = minOf(sMin, baseS)
        val sBase = baseS + (1f - baseS) * fullP
        val s = sBase + shrink * (sOpen - sBase)
        val ty0 = (sh - sh * sBase) / 2f
        val tyOpen = dp(70f).toFloat()
        stage.pivotX = sw / 2f
        stage.pivotY = 0f
        stage.scaleX = s
        stage.scaleY = s
        stage.translationY = ty0 + shrink * (tyOpen - ty0)
        rad = dp(28f) * (1f - fullP) / s
        stage.invalidateOutline()
    }

    private fun animFull(to: Float, end: (() -> Unit)?) {
        val a = ValueAnimator.ofFloat(fullP, to)
        a.duration = 380
        a.interpolator = DecelerateInterpolator(1.7f)
        a.addUpdateListener { fullP = it.animatedValue as Float; applyShrink() }
        if (end != null) a.addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(an: android.animation.Animator) { end() } })
        a.start()
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
        topBar.animate().alpha(0f).setDuration(200).start()
    }

    private fun closePanel() {
        if (!open) return
        open = false
        panel.animate().translationY(panel.height.toFloat()).setDuration(300).setInterpolator(DecelerateInterpolator())
            .withEndAction { if (!open) panel.visibility = View.INVISIBLE }.start()
        animShrink(0f)
        bottomBar.animate().alpha(1f).setStartDelay(120).setDuration(300).start()
        topBar.animate().alpha(1f).setStartDelay(120).setDuration(300).start()
    }

    private fun lab(t: String): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextColor(INKD)
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        v.setPadding(0, dp(12f), 0, dp(6f))
        return v
    }

    private fun slider(label: String, min: Int, max: Int, v: Int, on: (Int) -> Unit): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.text = "$label: $v"
        t.setTextColor(INKD)
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
            override fun onStopTrackingTouch(s: SeekBar?) { snap() }
        })
        l.addView(sb, LinearLayout.LayoutParams(-1, -2))
        return l
    }

    private fun tint(sb: SeekBar) {
        try {
            sb.progressTintList = ColorStateList.valueOf(Hx.BLUE)
            sb.thumbTintList = ColorStateList.valueOf(Hx.BLUE)
            sb.progressBackgroundTintList = ColorStateList.valueOf(0x33000000)
        } catch (e: Throwable) { }
    }

    private fun swatch(col: Int, sel: Boolean, click: () -> Unit): View {
        val v = View(this)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(col)
        g.setStroke(dp(if (sel) 3f else 1f), if (sel) Hx.BLUE else 0x33000000)
        v.background = g
        v.setOnClickListener { click() }
        Hx.press(v)
        return v
    }

    private fun applyPreset(p: StudioCfg) {
        val tgt: View = if (cfg.ic > 0 || p.ic > 0) clock else if (cfg.layout == "analog" || p.layout == "analog") clock.analog else clock.digits
        cfg.styleFrom(p)
        snap()
        clock.applyConfig(cfg)
        sg.x = 0.9f; sg.t = 1f; sg.v += 3f; kick()
        tgt.alpha = 0.35f
        tgt.animate().alpha(1f).setDuration(320).start()
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
                val byCat = StudioPresets.all.groupBy { it.cat }
                val pending = ArrayList<Triple<LinearLayout, View, List<StudioPresets.P>>>()
                for (c in StudioPresets.catsAll) {
                    val items = byCat[c] ?: continue
                    body.addView(lab(c))
                    val hs = HorizontalScrollView(this)
                    hs.isHorizontalScrollBarEnabled = false
                    val row = LinearLayout(this)
                    row.orientation = LinearLayout.HORIZONTAL
                    hs.addView(row)
                    body.addView(hs, LinearLayout.LayoutParams(-1, th + dp(6f)))
                    pending.add(Triple(row, hs, items))
                }
                val check = {
                    val svw = bodySv
                    if (svw != null) {
                        val it = pending.iterator()
                        while (it.hasNext()) {
                            val p = it.next()
                            val top = p.second.top - svw.scrollY
                            if (top < svw.height + dp(260f) && top + p.second.height > -dp(260f)) {
                                for (pr in p.third) {
                                    val t = StudioTile.make(this, pr.cfg, tw, th, 14f, bg)
                                    t.setOnClickListener { applyPreset(pr.cfg) }
                                    Hx.press(t)
                                    rings.add(Pair(t, pr.cfg))
                                    p.first.addView(t, LinearLayout.LayoutParams(tw, th).also { l -> l.rightMargin = dp(10f) })
                                }
                                it.remove()
                            }
                        }
                        refreshRings()
                    }
                }
                bodySv?.setOnScrollChangeListener { _, _, _, _, _ -> check() }
                bodySv?.post { check() }
            }
            1 -> fillFont()
            2 -> fillColor()
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
            Fs(600, 100, 100, "flex"), Fs(500, 140, 0, "flex"), Fs(400, 100, 0, "serif"), Fs(500, 100, 0, "mono"),
            Fs(400, 100, 0, "anton"), Fs(300, 100, 0, "antonio"), Fs(700, 100, 0, "bigshoulders"), Fs(500, 100, 0, "bodoni"),
            Fs(400, 100, 0, "italiana"), Fs(700, 100, 0, "caveat"), Fs(400, 100, 0, "dmserif"), Fs(700, 100, 0, "syne"), Fs(400, 100, 0, "bebas"))
        for (f in list) {
            val t = TextView(this)
            t.text = "35"
            t.setTextColor(INK)
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            t.gravity = Gravity.CENTER
            when (f.font) {
                "serif" -> t.typeface = Typeface.create(Typeface.SERIF, f.w, false)
                "mono" -> t.typeface = Typeface.create(Typeface.MONOSPACE, f.w, false)
                in StudioFont.assetFonts -> {
                    t.typeface = StudioFont.asset(this, f.font) ?: Typeface.DEFAULT
                    if (f.font in StudioFont.varFonts) try { t.fontVariationSettings = "'wght' ${f.w}" } catch (e: Throwable) { }
                }
                else -> {
                    t.typeface = StudioFont.get()
                    try { t.fontVariationSettings = "'wght' ${f.w},'wdth' ${f.wd},'ROND' ${f.ro}" } catch (e: Throwable) { }
                }
            }
            val sel = cfg.wght == f.w && cfg.wdth == f.wd && cfg.rond == f.ro && cfg.font == f.font
            val g = GradientDrawable()
            g.cornerRadius = dp(16f).toFloat()
            g.setColor(0x14000000)
            g.setStroke(dp(if (sel) 3f else 1f), if (sel) Hx.BLUE else 0x22000000)
            t.background = g
            t.setOnClickListener {
                cfg.wght = f.w; cfg.wdth = f.wd; cfg.rond = f.ro; cfg.font = f.font
                if (cfg.ic > 0) cfg.ic = 0
                clock.digits.animate().cancel()
                clock.digits.alpha = 0.3f
                clock.applyConfig(cfg)
                clock.digits.animate().alpha(1f).setDuration(300).start()
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
        a1.setTextColor(INKD)
        a1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        val a2 = TextView(this)
        a2.text = "A"
        a2.setTextColor(INK)
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
            t.setTextColor(INK)
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(11f), 0, dp(11f))
            val g = GradientDrawable()
            g.cornerRadius = dp(14f).toFloat()
            g.setColor(if (sel) 0x333D8BFF else 0x14000000)
            g.setStroke(dp(if (sel) 2.5f else 1f), if (sel) Hx.BLUE else 0x22000000)
            t.background = g
            t.setOnClickListener { cfg.fx = p.first; clock.applyConfig(cfg); fillBody() }
            Hx.press(t)
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.rightMargin = dp(6f)
            er.addView(t, lp)
        }
        body.addView(er, LinearLayout.LayoutParams(-1, -2))
        body.addView(slider("Transparencia", 0, 100, cfg.tr) { cfg.tr = it })

        body.addView(lsw("Mostrar fecha", "", cfg.date) { v -> cfg.date = v; clock.applyConfig(cfg) }, m.lp(m.MP, m.WC, 0, 10, 0, 0))
        body.addView(lsw("Solo contorno", "", cfg.ol == 1) { v -> cfg.ol = if (v) 1 else 0; clock.applyConfig(cfg) }, m.lp(m.MP, m.WC, 0, 0, 0, 0))
        body.addView(lab("Formato de fecha"))
        val dfs = HorizontalScrollView(this)
        dfs.isHorizontalScrollBarEnabled = false
        val dfr = LinearLayout(this)
        val nowC = java.util.Calendar.getInstance()
        for ((i, f) in listOf("EEE, d MMM", "d 'de' MMMM", "EEEE d", "d/M/yyyy", "EEEE", "d MMM").withIndex()) {
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.rightMargin = dp(8f)
            dfr.addView(chip(android.text.format.DateFormat.format(f, nowC).toString(), cfg.datef == i, 0f) { cfg.datef = i; clock.applyConfig(cfg); snap(); fillBody() }, lp)
        }
        dfs.addView(dfr)
        body.addView(dfs, LinearLayout.LayoutParams(-1, -2))
        body.addView(lab("Compartir estilo"))
        val cpr = LinearLayout(this)
        cpr.addView(chip("Copiar estilo", false, 0f) {
            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("estilo", cfg.enc()))
            Toast.makeText(this, "Estilo copiado", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-2, -2).also { it.rightMargin = dp(8f) })
        cpr.addView(chip("Pegar estilo", false, 0f) {
            val t = (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (t.contains("layout=")) { val p = StudioCfg.parse(t); applyPreset(p); cfg.datef = p.datef; cfg.sig = p.sig; clock.applyConfig(cfg); Toast.makeText(this, "Estilo pegado", Toast.LENGTH_SHORT).show() }
            else Toast.makeText(this, "No hay un estilo copiado", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-2, -2))
        body.addView(cpr)
        body.addView(lab("Disposición"))
        val lr = LinearLayout(this)
        lr.orientation = LinearLayout.HORIZONTAL
        for (p in listOf("stack" to "Apilado", "inline" to "Línea", "col" to "Vertical", "grid" to "Cuadr.", "diag" to "Diagonal", "words" to "Texto", "analog" to "Aguja")) {
            val sel = cfg.layout == p.first && cfg.ic == 0
            val t = TextView(this)
            t.text = p.second
            t.setTextColor(INK)
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(10f), 0, dp(10f))
            val g = GradientDrawable()
            g.cornerRadius = dp(14f).toFloat()
            g.setColor(if (sel) 0x333D8BFF else 0x14000000)
            g.setStroke(dp(if (sel) 2.5f else 1f), if (sel) Hx.BLUE else 0x22000000)
            t.background = g
            t.setOnClickListener { cfg.layout = p.first; cfg.ic = 0; clock.applyConfig(cfg); fillBody() }
            Hx.press(t)
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.rightMargin = dp(5f)
            lr.addView(t, lp)
        }
        body.addView(lr, LinearLayout.LayoutParams(-1, -2))
        body.addView(lab("Brillo, sombra y desenfoque"))
        body.addView(slider("Brillo suave (glow)", 0, 100, cfg.glow) { cfg.glow = it })
        body.addView(slider("Sombra", 0, 100, cfg.shadow) { cfg.shadow = it })
        body.addView(lab("Ajustes finos"))
        body.addView(slider("Estiramiento", 60, 160, cfg.xs) { cfg.xs = it })
        body.addView(slider("Separación de líneas", 0, 40, cfg.gap) { cfg.gap = it })
        body.addView(slider("Grosor", 100, 900, cfg.wght) { cfg.wght = it })
        body.addView(slider("Ancho", 50, 150, cfg.wdth) { cfg.wdth = it })
        body.addView(slider("Redondez", 0, 100, cfg.rond) { cfg.rond = it })
        body.addView(slider("Inclinación", -10, 0, cfg.slnt) { cfg.slnt = it })
    }

    // ---------- Color ----------

    private fun chip(label: String, sel: Boolean, w: Float, click: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.setTextColor(INK)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
        val g = GradientDrawable()
        g.cornerRadius = dp(40f).toFloat()
        g.setColor(if (sel) 0x333D8BFF else 0x14000000)
        g.setStroke(dp(if (sel) 2.5f else 1f), if (sel) Hx.BLUE else 0x22000000)
        t.background = g
        t.setOnClickListener { click() }
        Hx.press(t)
        return t
    }

    private fun fillColor() {
        body.addView(lab("Modo de color"))
        val hs0 = HorizontalScrollView(this)
        hs0.isHorizontalScrollBarEnabled = false
        val mr = LinearLayout(this)
        mr.orientation = LinearLayout.HORIZONTAL
        val modes = ArrayList<Pair<Int, String>>()
        modes.add(0 to "Manual"); modes.add(1 to "Adaptar al fondo"); modes.add(2 to "Material You"); modes.add(3 to "Material You dúo")
        if (cfg.ic > 0) modes.add(5 to "Original")
        for (p in modes) {
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.rightMargin = dp(8f)
            mr.addView(chip(p.second, cfg.cmode == p.first, 0f) { cfg.cmode = p.first; if (p.first == 1 && cfg.c2 != 0) cfg.c2 = cfg.c2; clock.applyConfig(cfg); fillBody() }, lp)
        }
        hs0.addView(mr)
        body.addView(hs0, LinearLayout.LayoutParams(-1, -2))
        if (cfg.cmode == 0 || cfg.cmode == 5 || cfg.cmode == 1) {
            if (cfg.cmode != 1) {
                body.addView(lab("Colores"))
                val cs = HorizontalScrollView(this)
                cs.isHorizontalScrollBarEnabled = false
                val cr = LinearLayout(this)
                cr.orientation = LinearLayout.HORIZONTAL
                for (c in palette) {
                    val col = c
                    val lp = LinearLayout.LayoutParams(dp(36f), dp(36f))
                    lp.rightMargin = dp(10f)
                    cr.addView(swatch(col, cfg.cmode == 0 && cfg.color == col) { cfg.color = col; cfg.cmode = 0; clock.applyConfig(cfg); fillBody() }, lp)
                }
                cs.addView(cr)
                body.addView(cs, LinearLayout.LayoutParams(-1, -2))
                val hv = FloatArray(3)
                Color.colorToHSV(cfg.color, hv)
                body.addView(slider("Tono", 0, 360, hv[0].toInt()) { h ->
                    val q = FloatArray(3); Color.colorToHSV(cfg.color, q)
                    cfg.color = Color.HSVToColor(floatArrayOf(h.toFloat(), if (q[1] < 0.05f) 0.6f else q[1], maxOf(q[2], 0.5f))); cfg.cmode = 0
                })
                body.addView(slider("Saturación", 0, 100, (hv[1] * 100).toInt()) { sa ->
                    val q = FloatArray(3); Color.colorToHSV(cfg.color, q)
                    cfg.color = Color.HSVToColor(floatArrayOf(q[0], sa / 100f, maxOf(q[2], 0.5f))); cfg.cmode = 0
                })
                body.addView(slider("Luminosidad", 20, 100, (hv[2] * 100).toInt().coerceAtLeast(20)) { v ->
                    val q = FloatArray(3); Color.colorToHSV(cfg.color, q)
                    cfg.color = Color.HSVToColor(floatArrayOf(q[0], q[1], v / 100f)); cfg.cmode = 0
                })
            } else {
                val n = TextView(this)
                n.text = "El color se calcula según tu fondo de pantalla (claro u oscuro y su tono)."
                n.setTextColor(INKD)
                n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                n.setPadding(0, dp(10f), 0, 0)
                body.addView(n)
            }
        } else {
            val n = TextView(this)
            n.text = "Usa los colores dinámicos de tu sistema (Material You), los mismos que el tema del móvil."
            n.setTextColor(INKD)
            n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            n.setPadding(0, dp(10f), 0, 0)
            body.addView(n)
        }
        if (cfg.cmode != 3) {
            body.addView(lsw("Degradado", "", cfg.c2 != 0) { v ->
                if (v) {
                    val q = FloatArray(3); Color.colorToHSV(cfg.color, q)
                    cfg.c2 = Color.HSVToColor(floatArrayOf((q[0] + 40f) % 360f, minOf(1f, maxOf(q[1], 0.4f)), maxOf(q[2], 0.8f)))
                } else cfg.c2 = 0
                clock.applyConfig(cfg); fillBody()
            }, m.lp(m.MP, m.WC, 0, 10, 0, 0))
            if (cfg.c2 != 0 && cfg.cmode == 0) {
                val h2 = FloatArray(3)
                Color.colorToHSV(cfg.c2, h2)
                body.addView(slider("Tono del degradado", 0, 360, h2[0].toInt()) { h ->
                    cfg.c2 = Color.HSVToColor(floatArrayOf(h.toFloat(), maxOf(h2[1], 0.4f), maxOf(h2[2], 0.8f)))
                })
            }
        }
    }

    // ---------- Fondo ----------

    private fun selectBg(idx: Int) {
        bgSel = idx
        if (cfg.depth == 1) { cfg.depth = 0; showDepth(false) }
        val cb = customBg
        wallBase = when {
            idx in 0 until StudioBg.count -> StudioBg.gen(idx, sw / 2, sh / 2)
            idx == 100 && cb != null -> cb
            else -> StudioWall.hi(this) ?: Hx.bgBitmap(this)
        }
        wallIv.setImageBitmap(wallBase)
        wz = 1f; wpx = 0f; wpy = 0f
        applyWallMatrix()
        applyWallFx()
        wallIv.alpha = 0.4f
        wallIv.animate().alpha(1f).setDuration(320).start()
        panel.background = FrostDrawable(frostBmp(), dp(30f).toFloat(), sh)
        fillBody()
    }

    private fun wallMatrix(b: Bitmap, w: Int, h: Int): Matrix {
        val s0 = maxOf(w / b.width.toFloat(), h / b.height.toFloat())
        val s = s0 * wz
        val maxX = maxOf(0f, (b.width * s - w) / 2f)
        val maxY = maxOf(0f, (b.height * s - h) / 2f)
        wpx = wpx.coerceIn(-maxX, maxX)
        wpy = wpy.coerceIn(-maxY, maxY)
        val m = Matrix()
        m.setScale(s, s)
        m.postTranslate((w - b.width * s) / 2f + wpx, (h - b.height * s) / 2f + wpy)
        return m
    }

    private fun applyWallMatrix() {
        val b = wallBase ?: return
        wallIv.imageMatrix = wallMatrix(b, sw, sh)
        wallChanged()
    }

    private fun wallChanged() {
        if (cfg.depth == 1 && (wz != 1f || wpx != 0f || wpy != 0f || wBlur != 0 || wBright != 0 || wSat != 100)) {
            cfg.depth = 0
            showDepth(false)
            if (!depthWarned) { depthWarned = true; Toast.makeText(this, "Profundidad desactivada: el fondo cambió", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun applyWallFx() {
        val cm = ColorMatrix()
        cm.setSaturation(wSat / 100f)
        val f = 1f + wBright / 100f
        cm.postConcat(ColorMatrix(floatArrayOf(f, 0f, 0f, 0f, 0f, 0f, f, 0f, 0f, 0f, 0f, 0f, f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        wallIv.colorFilter = if (wBright == 0 && wSat == 100) null else ColorMatrixColorFilter(cm)
        try {
            val r = wBlur / 100f * 36f * resources.displayMetrics.density
            wallIv.setRenderEffect(if (wBlur > 0) RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP) else null)
        } catch (e: Throwable) { }
        wallChanged()
    }

    private fun bakeWall(sel: Int, cb: Bitmap?): Bitmap? {
        val base: Bitmap = when {
            sel in 0 until StudioBg.count -> StudioBg.gen(sel, sw, sh)
            sel == 100 && cb != null -> cb
            else -> StudioWall.hi(this) ?: return null
        }
        val o = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        Canvas(o).drawBitmap(base, wallMatrix(base, sw, sh), Paint(Paint.FILTER_BITMAP_FLAG))
        return StudioBg.filter(o, wBlur, wBright, wSat)
    }

    private fun frostBmp(): Bitmap {
        val b = wallBase ?: Hx.bgBitmap(this)
        val w = 28
        return Bitmap.createScaledBitmap(b, w, maxOf(2, w * b.height / b.width), true)
    }

    private fun lsw(title: String, desc: String, on: Boolean, cb: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.text = title
        t.setTextColor(INK)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        col.addView(t)
        if (desc.isNotEmpty()) { val d = TextView(this); d.text = desc; d.setTextColor(INKD); d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); col.addView(d) }
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        val sw2 = android.widget.Switch(this)
        sw2.isChecked = on
        try {
            val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            sw2.thumbTintList = ColorStateList(st, intArrayOf(Color.WHITE, 0xFFFFFFFF.toInt()))
            sw2.trackTintList = ColorStateList(st, intArrayOf(Hx.BLUE, 0x44000000))
        } catch (e: Throwable) { }
        sw2.setOnCheckedChangeListener { _, v -> cb(v) }
        row.addView(sw2)
        return row
    }

    private fun fillBg() {
        body.addView(lab("Aplicar el fondo a"))
        val tr = LinearLayout(this)
        for ((i, n) in listOf("Bloqueo", "Inicio", "Ambas").withIndex()) {
            tr.addView(chip(n, target == i, 0f) {
                target = i
                getSharedPreferences("lock", MODE_PRIVATE).edit().putInt("wall_target", i).apply()
                fillBody()
            }, LinearLayout.LayoutParams(-2, -2).also { it.rightMargin = dp(8f) })
        }
        body.addView(tr)
        body.addView(lab("Fondo"))
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
                t.setShadowLayer(4f, 0f, 0f, 0xAA000000.toInt())
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
        dot(100, customBg, "Galería")
        for (i in 0 until StudioBg.count) dot(i, StudioBg.gen(i, 90, 160), if (i % 6 == 0) StudioBg.label(i) else null)
        hs.addView(row)
        body.addView(hs, LinearLayout.LayoutParams(-1, -2))
        body.addView(lab("Ajustes del fondo"))
        body.addView(slider("Desenfoque", 0, 100, wBlur) { wBlur = it; applyWallFx() })
        body.addView(slider("Brillo", -50, 50, wBright) { wBright = it; applyWallFx() })
        body.addView(slider("Saturación", 0, 200, wSat) { wSat = it; applyWallFx() })
        val rr = LinearLayout(this)
        rr.addView(chip("Restablecer fondo", false, 0f) { wBlur = 0; wBright = 0; wSat = 100; selectBg(-1) }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = dp(10f) })
        body.addView(rr)
        val note = TextView(this)
        note.text = "Con dos dedos sobre el fondo (fuera del reloj) haces zoom y lo mueves. Todo se aplica al pulsar Aplicar."
        note.setTextColor(INKD)
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

    // ---------- Profundidad (sujeto delante del reloj) ----------

    private var depthIv: ImageView? = null
    private var depthFg: Bitmap? = null
    private var depthMask = ""
    private var depthBusy = false

    private fun curWallBitmap(): Bitmap? = when {
        bgSel in 0 until StudioBg.count -> StudioBg.gen(bgSel, 540, 1170)
        bgSel == 100 && customBg != null -> customBg
        else -> StudioWall.hi(this)
    }

    private fun showDepth(on: Boolean) {
        depthIv?.let { stage.removeView(it) }
        depthIv = null
        val fg = depthFg
        if (on && fg != null) {
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageBitmap(fg)
            iv.colorFilter = android.graphics.PorterDuffColorFilter(0x26000000, android.graphics.PorterDuff.Mode.SRC_ATOP)
            stage.addView(iv, stage.indexOfChild(clock) + 1, FrameLayout.LayoutParams(-1, -1))
            depthIv = iv
        }
    }

    private fun toggleDepth() {
        if (depthBusy) return
        if (cfg.depth == 1) {
            cfg.depth = 0
            showDepth(false)
            Toast.makeText(this, "Profundidad desactivada", Toast.LENGTH_SHORT).show()
            return
        }
        val src = curWallBitmap()
        if (src == null) { Toast.makeText(this, "No encuentro el fondo", Toast.LENGTH_SHORT).show(); return }
        depthBusy = true
        Toast.makeText(this, "Buscando el sujeto del fondo… (la primera vez descarga el modelo de Google)", Toast.LENGTH_LONG).show()
        Thread {
            val r = StudioDepth.segment(src)
            val enc = if (r.first != null) StudioDepth.encode(r.first!!) else ""
            runOnUiThread {
                depthBusy = false
                if (r.first == null) { Toast.makeText(this, "Profundidad: " + r.second, Toast.LENGTH_LONG).show(); return@runOnUiThread }
                depthFg = r.first
                depthMask = enc
                cfg.depth = 1
                showDepth(true)
                Toast.makeText(this, "Profundidad activada: el sujeto va delante del reloj", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    // ---------- IA (Gemini) ----------

    private fun aiDialog() {
        if (aiSheet != null) return
        if (open) closePanel()
        val sheet = AiSheet(this, object : AiSheet.Host {
            override fun style(c: StudioCfg) {
                cfg.styleFrom(c)
                cfg.datef = c.datef
                cfg.sig = c.sig
                clock.applyConfig(cfg)
                sg.x = 0.88f; sg.t = 1f; sg.v += 3f; kick()
                snap()
            }
            override fun curCfg(): StudioCfg = cfg
            override fun wallBmp(): Bitmap? = wallBase
            override fun useWall(b: Bitmap) { customBg = b; selectBg(100) }
            override fun closeSheet() { aiSheet?.let { root.removeView(it) }; aiSheet = null }
        })
        root.addView(sheet, FrameLayout.LayoutParams(-1, -1))
        aiSheet = sheet
    }

    // ---------- Deshacer / sorpresa ----------

    private fun snap() {
        val e = cfg.enc()
        if (hist.isEmpty() || hist[hist.size - 1] != e) { hist.add(e); if (hist.size > 40) hist.removeAt(0) }
    }

    private fun undo() {
        if (hist.size <= 1) { Toast.makeText(this, "Nada que deshacer", Toast.LENGTH_SHORT).show(); return }
        hist.removeAt(hist.size - 1)
        cfg.assign(StudioCfg.parse(hist[hist.size - 1]))
        updatePos()
        clock.applyConfig(cfg)
        sg.x = 0.92f; sg.t = 1f; sg.v += 2f; kick()
        root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (open) fillBody()
    }

    private fun surprise() {
        val l = StudioPresets.all.filter { it.cfg.ic == 0 }
        val p = l[java.util.Random().nextInt(l.size)]
        applyPreset(p.cfg)
        Toast.makeText(this, "Sorpresa: " + p.name + " (" + p.cat + ")", Toast.LENGTH_SHORT).show()
        root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
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
        p.edit().putString("sc_cfg", cfg.enc()).putBoolean("sc_on", true).putBoolean("ic_on", false).putString("depth_mask", if (cfg.depth == 1) depthMask else "").apply()
        StudioRecents.add(this, cfg.enc())
        val sel = bgSel
        val cb = customBg
        val changed = bgSel != -1 || wz != 1f || wpx != 0f || wpy != 0f || wBlur != 0 || wBright != 0 || wSat != 100
        val tgt = target
        Thread {
            var ok = true
            if (changed) { val bm = bakeWall(sel, cb); ok = bm != null && StudioBg.setWall(this, bm, tgt) }
            StudioWall.reset()
            val pushed = StudioPush.push(p)
            runOnUiThread {
                Toast.makeText(this, if (!ok) "No pude cambiar el fondo" else if (pushed) "Aplicado. Bloquea la pantalla para verlo" else "Guardado, pero el módulo LSPosed no está activo", Toast.LENGTH_LONG).show()
                setResult(RESULT_OK)
                topBar.animate().alpha(0f).setDuration(160).start()
                bottomBar.animate().alpha(0f).setDuration(160).start()
                if (open) { open = false; panel.animate().translationY(panel.height.toFloat()).setDuration(220).start(); animShrink(0f) }
                animFull(1f) { root.animate().alpha(0f).setDuration(160).withEndAction { finish(); overridePendingTransition(0, 0) }.start() }
            }
        }.start()
    }

    override fun onBackPressed() { if (aiSheet != null) aiSheet?.close() else if (open) closePanel() else cancel() }
}

/** Fondo de panel claro de cristal: el fondo de pantalla desenfocado + velo blanco, esquinas superiores redondeadas. */
class FrostDrawable(private val bmp: Bitmap, private val r: Float, private val screenH: Int) : Drawable() {
    private val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val veil = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        val cm = ColorMatrix()
        cm.setSaturation(1.5f)
        p.colorFilter = ColorMatrixColorFilter(cm)
        veil.color = 0xC8FFFFFF.toInt()
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = 2f
        edge.color = 0x99FFFFFF.toInt()
    }

    override fun draw(c: Canvas) {
        val b = bounds
        val path = Path()
        path.addRoundRect(RectF(b), floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW)
        c.save()
        c.clipPath(path)
        val frac = (b.height() / screenH.toFloat()).coerceIn(0.1f, 1f)
        val srcTop = (bmp.height * (1f - frac)).toInt().coerceIn(0, bmp.height - 1)
        c.drawBitmap(bmp, android.graphics.Rect(0, srcTop, bmp.width, bmp.height), b, p)
        c.drawRect(RectF(b), veil)
        c.restore()
        c.drawPath(path, edge)
    }

    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
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
        ok = bridge("putString", "depth_mask", p.getString("depth_mask", "") ?: "") && ok
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
