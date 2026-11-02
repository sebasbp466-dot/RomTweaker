package dev.romtweaker.app

import android.app.Activity
import android.app.WallpaperManager
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Estilos de reloj predefinidos (propios, inspirados en la galería de HyperOS). */
object StudioPresets {
    class P(val name: String, val cat: String, val cfg: StudioCfg)

    val cats = listOf("Clásicos", "Cristal", "Color", "Mezcla", "Vertical", "Revista", "Contorno")

    private fun mk(name: String, cat: String, layout: String, w: Int, wd: Int, ro: Int, fx: String, tr: Int, color: Int, size: Int, font: String = "flex", ol: Int = 0): P {
        val c = StudioCfg()
        c.layout = layout; c.wght = w; c.wdth = wd; c.rond = ro; c.fx = fx; c.tr = tr; c.color = color; c.size = size; c.font = font; c.ol = ol
        return P(name, cat, c)
    }

    private const val W = 0xFFFFFFFF.toInt()
    val all: List<P> = listOf(
        mk("Grueso", "Clásicos", "stack", 800, 100, 0, "solid", 0, W, 190),
        mk("Fino alto", "Clásicos", "stack", 200, 50, 0, "solid", 0, W, 215),
        mk("Condensado", "Clásicos", "stack", 500, 50, 0, "solid", 0, W, 200),
        mk("En línea", "Clásicos", "inline", 500, 90, 0, "solid", 0, W, 230),
        mk("Ancho", "Clásicos", "stack", 500, 140, 0, "solid", 0, W, 170),
        mk("Redondo", "Clásicos", "stack", 600, 100, 100, "solid", 0, W, 190),

        mk("Cristal", "Cristal", "stack", 600, 90, 0, "glass", 35, W, 190),
        mk("Cristal fino", "Cristal", "stack", 150, 70, 0, "glass", 20, W, 210),
        mk("Líquido", "Cristal", "stack", 700, 110, 100, "glass", 50, W, 180),
        mk("Cristal ancho", "Cristal", "stack", 500, 130, 40, "glass", 40, W, 175),
        mk("Hielo", "Cristal", "stack", 300, 80, 0, "glass", 60, 0xFFBFE9FF.toInt(), 200),

        mk("Naranja", "Color", "stack", 800, 100, 0, "solid", 0, 0xFFFF7A3D.toInt(), 190),
        mk("Neón", "Color", "stack", 400, 60, 100, "solid", 0, 0xFF4ADE80.toInt(), 200),
        mk("Rosa", "Color", "stack", 700, 90, 50, "solid", 0, 0xFFFF5C9E.toInt(), 190),
        mk("Violeta", "Color", "stack", 700, 100, 60, "solid", 0, 0xFFB388FF.toInt(), 190),
        mk("Dorado", "Color", "stack", 300, 70, 0, "solid", 0, 0xFFFFD60A.toInt(), 205),
        mk("Menta", "Color", "inline", 600, 90, 100, "solid", 0, 0xFF4ADE80.toInt(), 230),

        mk("Mezcla", "Mezcla", "stack", 800, 100, 0, "blend", 20, W, 190),
        mk("Mezcla fina", "Mezcla", "stack", 300, 70, 0, "blend", 10, W, 210),
        mk("Superposición", "Mezcla", "stack", 700, 100, 0, "overlay", 20, W, 190),
        mk("Superpos. fina", "Mezcla", "stack", 300, 70, 0, "overlay", 30, W, 210),

        mk("Vertical fino", "Vertical", "col", 200, 60, 0, "solid", 0, W, 200),
        mk("Vertical grueso", "Vertical", "col", 800, 90, 0, "solid", 0, W, 200),
        mk("Vertical cristal", "Vertical", "col", 500, 80, 0, "glass", 35, W, 200),
        mk("Vertical color", "Vertical", "col", 700, 70, 60, "solid", 0, 0xFFFF7A3D.toInt(), 200),

        mk("Serif", "Revista", "stack", 400, 100, 0, "solid", 0, W, 190, "serif"),
        mk("Serif fino", "Revista", "stack", 300, 100, 0, "solid", 0, W, 205, "serif"),
        mk("Serif en línea", "Revista", "inline", 500, 100, 0, "solid", 0, W, 230, "serif"),
        mk("Mono", "Revista", "stack", 500, 100, 0, "solid", 0, W, 170, "mono"),

        mk("Contorno", "Contorno", "stack", 500, 90, 0, "solid", 0, W, 190, "flex", 1),
        mk("Contorno fino", "Contorno", "stack", 300, 70, 0, "solid", 0, W, 210, "flex", 1),
        mk("Contorno vertical", "Contorno", "col", 400, 70, 0, "solid", 0, W, 200, "flex", 1)
    )

    fun same(a: StudioCfg, b: StudioCfg) = a.layout == b.layout && a.wght == b.wght && a.wdth == b.wdth && a.rond == b.rond &&
        a.fx == b.fx && a.font == b.font && a.ol == b.ol && a.color == b.color
}

/** Fondos generados (propios) y utilidades para poner un fondo en la pantalla de bloqueo. */
object StudioBg {
    private val pal = arrayOf(
        intArrayOf(0xFF0F3B3B.toInt(), 0xFF1B6E6E.toInt(), 0xFF15262B.toInt()),
        intArrayOf(0xFF3A1C71.toInt(), 0xFFD76D77.toInt(), 0xFFFFAF7B.toInt()),
        intArrayOf(0xFF0F2027.toInt(), 0xFF2C5364.toInt(), 0xFF1CB5E0.toInt()),
        intArrayOf(0xFF0B3D2E.toInt(), 0xFF3DDC97.toInt(), 0xFF1A237E.toInt()),
        intArrayOf(0xFF42275A.toInt(), 0xFF734B6D.toInt(), 0xFFFF9A9E.toInt()),
        intArrayOf(0xFF141E30.toInt(), 0xFF243B55.toInt(), 0xFF0B0F18.toInt())
    )
    val count = pal.size

    fun gen(i: Int, w: Int, h: Int): Bitmap {
        val c = pal[i.coerceIn(0, pal.size - 1)]
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w * 0.8f, h.toFloat(), c, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = RadialGradient(w * 0.2f, h * 0.25f, w * 0.7f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = RadialGradient(w * 0.85f, h * 0.72f, w * 0.8f, (c[2] and 0x00FFFFFF) or 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return b
    }

    fun setLock(ctx: Activity, bmp: Bitmap): Boolean = try {
        WallpaperManager.getInstance(ctx).setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
        true
    } catch (e: Throwable) { false }
}

/** Utilidades visuales comunes del estilo HyperOS (botones tipo píldora, animaciones de pulsación). */
object Hx {
    const val BLUE = 0xFF3D8BFF.toInt()

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun press(v: View) {
        v.setOnTouchListener { x, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> x.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    x.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(2.4f)).start()
            }
            false
        }
    }

    fun pill(ctx: Context, text: String, bg: Int, fg: Int, click: () -> Unit): TextView {
        val t = TextView(ctx)
        t.text = text
        t.setTextColor(fg)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.setPadding(dp(ctx, 18f), dp(ctx, 9f), dp(ctx, 18f), dp(ctx, 9f))
        val g = GradientDrawable()
        g.cornerRadius = dp(ctx, 40f).toFloat()
        g.setColor(bg)
        t.background = g
        t.setOnClickListener { click() }
        press(t)
        return t
    }

    fun bgBitmap(ctx: Context): Bitmap = StudioWall.get(ctx) ?: StudioBg.gen(0, 240, 420)
}

/** Iconos simples dibujados (Fondo, IA, Profundidad, Efectos) para la barra inferior del editor. */
class HxIcon(ctx: Context, private val kind: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val r = 11f * d
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.8f * d
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = Color.WHITE
        when (kind) {
            0 -> {
                c.drawRoundRect(RectF(cx - r, cy - r, cx + r, cy + r), 4f * d, 4f * d, p)
                val pa = Path()
                pa.moveTo(cx - r, cy + r * 0.55f)
                pa.lineTo(cx - r * 0.3f, cy - r * 0.05f)
                pa.lineTo(cx + r * 0.25f, cy + r * 0.45f)
                pa.lineTo(cx + r * 0.55f, cy + r * 0.15f)
                pa.lineTo(cx + r, cy + r * 0.6f)
                c.drawPath(pa, p)
                c.drawCircle(cx + r * 0.4f, cy - r * 0.45f, 2.2f * d, p)
            }
            1 -> {
                val pa = Path()
                pa.moveTo(cx, cy - r)
                pa.quadTo(cx + r * 0.12f, cy - r * 0.12f, cx + r, cy)
                pa.quadTo(cx + r * 0.12f, cy + r * 0.12f, cx, cy + r)
                pa.quadTo(cx - r * 0.12f, cy + r * 0.12f, cx - r, cy)
                pa.quadTo(cx - r * 0.12f, cy - r * 0.12f, cx, cy - r)
                c.drawPath(pa, p)
            }
            2 -> {
                c.drawRoundRect(RectF(cx - r, cy - r * 0.55f, cx + r * 0.35f, cy + r), 4f * d, 4f * d, p)
                c.drawRoundRect(RectF(cx - r * 0.35f, cy - r, cx + r, cy + r * 0.55f), 4f * d, 4f * d, p)
            }
            else -> {
                c.drawCircle(cx - r * 0.35f, cy, r * 0.62f, p)
                c.drawCircle(cx + r * 0.35f, cy, r * 0.62f, p)
            }
        }
    }
}

/** Miniatura de pantalla de bloqueo con el reloj escalado (galería y panel del editor). */
object StudioTile {
    fun make(ctx: Context, cfg: StudioCfg, pw: Int, ph: Int, radiusDp: Float, bg: Bitmap): FrameLayout {
        val dm = ctx.resources.displayMetrics
        val sw = dm.widthPixels
        val sh = dm.heightPixels
        val k = pw / sw.toFloat()
        val card = FrameLayout(ctx)
        val iv = ImageView(ctx)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.setImageBitmap(bg)
        card.addView(iv, FrameLayout.LayoutParams(-1, -1))
        val dim = View(ctx)
        dim.setBackgroundColor(0x26000000)
        card.addView(dim, FrameLayout.LayoutParams(-1, -1))
        val screen = FrameLayout(ctx)
        val clk = StudioClock(ctx)
        clk.applyConfig(cfg)
        val clp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        clp.topMargin = (cfg.top * dm.density).toInt()
        screen.addView(clk, clp)
        screen.pivotX = 0f
        screen.pivotY = 0f
        screen.scaleX = k
        screen.scaleY = k
        card.addView(screen, FrameLayout.LayoutParams(sw, sh))
        val rad = radiusDp * dm.density
        card.clipToOutline = true
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, rad) }
        }
        return card
    }
}

/**
 * Galería de estilos de la pantalla de bloqueo, estilo HyperOS: categorías en vertical, carrusel de tarjetas
 * en horizontal, la tarjeta se expande hasta el editor. Se abre con pulsación larga en el bloqueo o desde la app.
 */
class LockGalleryActivity : Activity() {
    private class Page(val cat: String, val items: List<StudioPresets.P>, val view: FrameLayout, val title: TextView) {
        val cards = ArrayList<FrameLayout>()
        var pos = 0f
        var built = false
    }

    private lateinit var root: FrameLayout
    private lateinit var stage: FrameLayout
    private lateinit var nameTv: TextView
    private lateinit var custBtn: TextView
    private lateinit var topBar: FrameLayout
    private val pages = ArrayList<Page>()
    private var vpos = 0f
    private var sw = 0
    private var sh = 0
    private var pw = 0
    private var ph = 0
    private var cy = 0f
    private var vstep = 0f
    private var hstep = 0f
    private var busy = false
    private var anim: ValueAnimator? = null
    private var vt: VelocityTracker? = null
    private var dX = 0f
    private var dY = 0f
    private var axis = 0
    private var startV = 0f
    private var startC = 0f
    private var lastIdx = -1

    private fun cur(): Int = vpos.roundToInt().coerceIn(0, pages.size - 1)
    private fun idx(pg: Page): Int = pg.pos.roundToInt().coerceIn(0, pg.items.size - 1)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try { setShowWhenLocked(true) } catch (e: Throwable) { }
        try {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(WindowInsets.Type.systemBars())
            window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (e: Throwable) { }
        val dm = resources.displayMetrics
        sw = dm.widthPixels
        sh = dm.heightPixels
        pw = (sw * 0.60f).toInt()
        ph = (pw * sh / sw.toFloat()).toInt()
        cy = sh * 0.52f
        vstep = ph * 1.10f
        hstep = pw * 0.92f

        root = FrameLayout(this)
        root.setBackgroundColor(0xFF0A0B0F.toInt())
        stage = FrameLayout(this)
        root.addView(stage, FrameLayout.LayoutParams(-1, -1))

        val bg = Hx.bgBitmap(this)
        for (c in StudioPresets.cats) {
            val items = StudioPresets.all.filter { it.cat == c }
            val v = FrameLayout(this)
            v.pivotX = sw / 2f
            v.pivotY = cy
            val t = TextView(this)
            t.text = c
            t.setTextColor(Color.WHITE)
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            t.typeface = Typeface.DEFAULT_BOLD
            t.gravity = Gravity.CENTER
            val tlp = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
            tlp.topMargin = (sh * 0.125f).toInt()
            v.addView(t, tlp)
            stage.addView(v, FrameLayout.LayoutParams(-1, -1))
            pages.add(Page(c, items, v, t))
        }
        bgRef = bg

        nameTv = TextView(this)
        nameTv.setTextColor(0xB3FFFFFF.toInt())
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        nameTv.gravity = Gravity.CENTER
        val nlp = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        nlp.topMargin = (cy + ph / 2f + Hx.dp(this, 10f)).toInt()
        root.addView(nameTv, nlp)

        custBtn = Hx.pill(this, "Personalizar", 0x33FFFFFF, Color.WHITE) { openEditor() }
        custBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        custBtn.setPadding(Hx.dp(this, 14f), Hx.dp(this, 6f), Hx.dp(this, 14f), Hx.dp(this, 6f))
        val clp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        clp.topMargin = (cy + ph / 2f + Hx.dp(this, 34f)).toInt()
        root.addView(custBtn, clp)

        topBar = FrameLayout(this)
        topBar.addView(Hx.pill(this, "Salir", 0x2EFFFFFF, Color.WHITE) { leave() }, FrameLayout.LayoutParams(-2, -2, Gravity.START))
        topBar.addView(Hx.pill(this, "Aplicar", Hx.BLUE, Color.WHITE) { applyOnly() }, FrameLayout.LayoutParams(-2, -2, Gravity.END))
        val tl = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        tl.setMargins(Hx.dp(this, 16f), Hx.dp(this, 30f), Hx.dp(this, 16f), 0)
        root.addView(topBar, tl)

        stage.setOnTouchListener { _, e -> touch(e); true }
        setContentView(root)

        // Entrada: la pantalla de bloqueo "se aleja" y aparece la galería
        root.alpha = 0f
        root.scaleX = 1.12f
        root.scaleY = 1.12f
        layoutAll()
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(DecelerateInterpolator(1.7f)).start()
    }

    private var bgRef: Bitmap? = null

    private fun build(pg: Page) {
        if (pg.built) return
        pg.built = true
        val bg = bgRef ?: Hx.bgBitmap(this)
        for (pr in pg.items) {
            val card = StudioTile.make(this, pr.cfg, pw, ph, 28f, bg)
            val lp = FrameLayout.LayoutParams(pw, ph, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            lp.topMargin = (cy - ph / 2f).toInt()
            pg.view.addView(card, lp)
            pg.cards.add(card)
        }
        layoutAll()
    }

    /** Coloca todas las páginas y tarjetas según la posición (vertical vpos, horizontal pos de cada página). */
    private fun layoutAll() {
        for ((i, pg) in pages.withIndex()) {
            val d = i - vpos
            val ad = abs(d)
            if (ad > 1.7f) { pg.view.visibility = View.INVISIBLE; continue }
            pg.view.visibility = View.VISIBLE
            if (!pg.built) build(pg)
            pg.view.translationY = d * vstep
            val s = 1f - 0.10f * min(1f, ad)
            pg.view.scaleX = s
            pg.view.scaleY = s
            pg.view.alpha = 1f - 0.5f * min(1f, ad)
            pg.title.alpha = (1f - ad * 1.7f).coerceIn(0f, 1f)
            for ((j, c) in pg.cards.withIndex()) {
                val dj = j - pg.pos
                val a = abs(dj)
                if (a > 2.6f) { c.visibility = View.INVISIBLE; continue }
                c.visibility = View.VISIBLE
                c.translationX = dj * hstep
                val sc = 1f - 0.18f * min(1f, a)
                c.scaleX = sc
                c.scaleY = sc
                c.alpha = 1f - 0.45f * min(1f, a)
                c.translationZ = 10f * (1f - min(1f, a))
            }
        }
        val pg = pages[cur()]
        val near = (1f - abs(vpos - vpos.roundToInt()) * 3f).coerceIn(0f, 1f)
        val nearH = (1f - abs(pg.pos - pg.pos.roundToInt()) * 3f).coerceIn(0f, 1f)
        val a = min(near, nearH)
        custBtn.alpha = a
        nameTv.alpha = a
        val i = idx(pg)
        pg.items.getOrNull(i)?.let { nameTv.text = it.name }
        val key = cur() * 100 + i
        if (key != lastIdx) {
            if (lastIdx != -1) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            lastIdx = key
        }
    }

    private fun animTo(from: Float, to: Float, set: (Float) -> Unit) {
        anim?.cancel()
        val a = ValueAnimator.ofFloat(from, to)
        a.duration = 360
        a.interpolator = DecelerateInterpolator(1.8f)
        a.addUpdateListener { set(it.animatedValue as Float); layoutAll() }
        anim = a
        a.start()
    }

    private fun touch(e: MotionEvent) {
        if (busy) return
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anim?.cancel()
                vt?.recycle()
                vt = VelocityTracker.obtain()
                vt?.addMovement(e)
                dX = e.x; dY = e.y; axis = 0
                startV = vpos
                startC = pages[cur()].pos
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                val dx = e.x - dX
                val dy = e.y - dY
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (axis == 0 && max(abs(dx), abs(dy)) > slop) {
                    axis = if (abs(dx) > abs(dy)) 1 else 2
                    if (axis == 1) startC = pages[cur()].pos else startV = vpos
                }
                if (axis == 1) {
                    val pg = pages[cur()]
                    pg.pos = (startC - dx / hstep).coerceIn(-0.35f, pg.items.size - 1 + 0.35f)
                    layoutAll()
                } else if (axis == 2) {
                    vpos = (startV - dy / vstep).coerceIn(-0.25f, pages.size - 1 + 0.25f)
                    layoutAll()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt?.addMovement(e)
                vt?.computeCurrentVelocity(1000)
                val vx = vt?.xVelocity ?: 0f
                val vy = vt?.yVelocity ?: 0f
                vt?.recycle()
                vt = null
                if (e.actionMasked == MotionEvent.ACTION_CANCEL) { snap(); return }
                when (axis) {
                    1 -> {
                        val pg = pages[cur()]
                        val t = (pg.pos - vx / hstep * 0.22f).roundToInt().coerceIn(0, pg.items.size - 1)
                        animTo(pg.pos, t.toFloat()) { pg.pos = it }
                    }
                    2 -> {
                        val t = (vpos - vy / vstep * 0.22f).roundToInt().coerceIn(0, pages.size - 1)
                        animTo(vpos, t.toFloat()) { vpos = it }
                    }
                    else -> tap(e.x, e.y)
                }
            }
        }
    }

    private fun snap() {
        val pg = pages[cur()]
        animTo(vpos, cur().toFloat()) { vpos = it }
        pg.pos = idx(pg).toFloat()
        layoutAll()
    }

    private fun tap(x: Float, y: Float) {
        val pg = pages[cur()]
        if (abs(y - cy) > ph / 2f) return
        val half = pw / 2f
        val dx = x - sw / 2f
        if (abs(dx) <= half) openEditor()
        else if (dx < 0 && idx(pg) > 0) animTo(pg.pos, (idx(pg) - 1).toFloat()) { pg.pos = it }
        else if (dx > 0 && idx(pg) < pg.items.size - 1) animTo(pg.pos, (idx(pg) + 1).toFloat()) { pg.pos = it }
    }

    /** La tarjeta central se expande hasta ocupar toda la pantalla y se abre el editor sobre ella. */
    private fun openEditor() {
        if (busy) return
        val pg = pages[cur()]
        val j = idx(pg)
        val card = pg.cards.getOrNull(j) ?: return
        val pr = pg.items[j]
        busy = true
        anim?.cancel()
        for (p in pages) if (p !== pg) p.view.animate().alpha(0f).setDuration(220).start()
        for ((k, c) in pg.cards.withIndex()) if (k != j) c.animate().alpha(0f).setDuration(220).start()
        pg.title.animate().alpha(0f).setDuration(180).start()
        nameTv.animate().alpha(0f).setDuration(150).start()
        custBtn.animate().alpha(0f).setDuration(150).start()
        topBar.animate().alpha(0f).setDuration(180).start()
        card.translationZ = 30f
        card.animate().scaleX(sw / pw.toFloat()).scaleY(sh / ph.toFloat()).translationY(sh / 2f - cy)
            .setDuration(360).setInterpolator(DecelerateInterpolator(1.6f))
            .withEndAction {
                val i = Intent(this, ClockEditActivity::class.java)
                i.putExtra("cfg", pr.cfg.enc())
                try { startActivityForResult(i, 91) } catch (e: Throwable) { busy = false }
                overridePendingTransition(0, 0)
            }.start()
    }

    private fun resetAfterEdit() {
        val pg = pages[cur()]
        val j = idx(pg)
        for (p in pages) p.view.animate().alpha(1f - 0.5f * min(1f, abs(pages.indexOf(p) - vpos))).setDuration(260).start()
        for ((k, c) in pg.cards.withIndex()) {
            val a = abs(k - pg.pos)
            val sc = 1f - 0.18f * min(1f, a)
            val al = 1f - 0.45f * min(1f, a)
            c.animate().scaleX(sc).scaleY(sc).translationY(0f).alpha(al).setDuration(300).setInterpolator(DecelerateInterpolator(1.5f)).start()
        }
        pg.cards.getOrNull(j)?.translationZ = 10f
        pg.title.animate().alpha(1f).setDuration(260).start()
        nameTv.animate().alpha(1f).setDuration(260).start()
        custBtn.animate().alpha(1f).setDuration(260).start()
        topBar.animate().alpha(1f).setDuration(260).start()
        busy = false
    }

    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        if (rc != 91) return
        if (res == RESULT_OK) { finish(); overridePendingTransition(0, 0) } else resetAfterEdit()
    }

    private fun leave() {
        if (busy) return
        busy = true
        root.animate().alpha(0f).scaleX(1.08f).scaleY(1.08f).setDuration(240).setInterpolator(DecelerateInterpolator())
            .withEndAction { finish(); overridePendingTransition(0, 0) }.start()
    }

    /** Aplica el estilo elegido sin pasar por el editor. */
    private fun applyOnly() {
        if (busy) return
        val pg = pages[cur()]
        val pr = pg.items.getOrNull(idx(pg)) ?: return
        busy = true
        val p = getSharedPreferences("lock", MODE_PRIVATE)
        p.edit().putString("sc_cfg", pr.cfg.enc()).putBoolean("sc_on", true).putBoolean("ic_on", false).apply()
        val pushed = StudioPush.push(p)
        Toast.makeText(this, if (pushed) "Aplicado: ${pr.name}" else "Guardado, pero el módulo LSPosed no está activo", Toast.LENGTH_LONG).show()
        root.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(260).setInterpolator(DecelerateInterpolator())
            .withEndAction { finish(); overridePendingTransition(0, 0) }.start()
    }

    override fun onBackPressed() { leave() }
}
