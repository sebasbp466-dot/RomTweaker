package dev.romtweaker.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/** Editor del reloj estilo HyperOS: mueve y escala con dos dedos, panel con pestañas Reloj / Fuente y efectos. */
class ClockEditActivity : Activity() {
    private lateinit var m: M3
    private lateinit var cfg: StudioCfg
    private lateinit var clock: StudioClock
    private lateinit var root: FrameLayout
    private lateinit var panel: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var pill: TextView
    private var tab = 0
    private var open = false
    private var scaleDet: ScaleGestureDetector? = null
    private var lx = 0f
    private var ly = 0f
    private var lastN = 0

    private val palette = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFFF7A3D.toInt(), 0xFF4DA3FF.toInt(), 0xFFFF5C9E.toInt(),
        0xFF4ADE80.toInt(), 0xFFFFD60A.toInt(), 0xFFB388FF.toInt(), 0xFF111111.toInt()
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try { setShowWhenLocked(true) } catch (e: Throwable) { }
        try {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(WindowInsets.Type.systemBars())
            window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (e: Throwable) { }
        m = M3(this, true)
        cfg = StudioCfg.load(this)
        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val wd = LockStudio.wallpaper(this)
        if (wd != null) {
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageDrawable(wd)
            root.addView(iv, FrameLayout.LayoutParams(-1, -1))
        } else {
            root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF0F3B3B.toInt(), 0xFF1B6E6E.toInt(), 0xFF15262B.toInt()))
        }
        val dim = View(this)
        dim.setBackgroundColor(0x22000000)
        root.addView(dim, FrameLayout.LayoutParams(-1, -1))

        clock = StudioClock(this)
        clock.background = m.shape(Color.TRANSPARENT, 14, 2, 0x99FFFFFF.toInt())
        root.addView(clock, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        clock.applyConfig(cfg)
        updatePos()

        val hint = m.tv("Mueve y escala el reloj con dos dedos", 13f, 0xCCFFFFFF.toInt())
        hint.gravity = Gravity.CENTER
        root.addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).also { it.topMargin = m.dp(78) })

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.addView(m.tonal("Cancelar") { finish() }, m.lp(m.WC, m.WC))
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(m.filled("Aplicar") { applyAll() }, m.lp(m.WC, m.WC))
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).also { it.setMargins(m.dp(16), m.dp(30), m.dp(16), 0) })

        pill = m.filled("Personalizar") { toggle() }
        root.addView(pill, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).also { it.bottomMargin = m.dp(36) })

        panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.isClickable = true
        panel.setPadding(m.dp(18), m.dp(14), m.dp(18), m.dp(22))
        panel.background = LiquidGlassDrawable(m.dp(28).toFloat(), m.surfaceCont, m.dark, resources.displayMetrics.density)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(m.tonal("✕") { toggle() }, m.lp(m.WC, m.WC, 0, 0, 12, 0))
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        head.addView(tabs, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(head, m.lp(m.MP, m.WC, 0, 0, 0, 10))
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(this)
        sv.addView(body)
        val sh = (resources.displayMetrics.heightPixels * 0.40f).toInt()
        panel.addView(sv, LinearLayout.LayoutParams(-1, sh))
        val pl = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        pl.setMargins(m.dp(8), 0, m.dp(8), m.dp(8))
        root.addView(panel, pl)
        panel.visibility = View.INVISIBLE

        fun fillTabs() {
            tabs.removeAllViews()
            tabs.addView(m.chip("Reloj", tab == 0) { tab = 0; fillTabs(); fillBody() }, m.lp(m.WC, m.WC, 0, 0, 8, 0))
            tabs.addView(m.chip("Fuente", tab == 1) { tab = 1; fillTabs(); fillBody() }, m.lp(m.WC, m.WC))
        }
        fillTabs()
        fillBody()

        scaleDet = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                cfg.sc = (cfg.sc * d.scaleFactor).coerceIn(40f, 300f)
                clock.applyConfig(cfg)
                return true
            }
        })
        root.setOnTouchListener { _, e -> touch(e); true }
        setContentView(root)
    }

    private fun updatePos() {
        val lp = clock.layoutParams as FrameLayout.LayoutParams
        lp.topMargin = m.dp(cfg.top.toInt())
        clock.layoutParams = lp
        clock.translationX = cfg.cx / 100f * resources.displayMetrics.widthPixels
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
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { lastN = 0; clock.digits.applyFx() }
            MotionEvent.ACTION_POINTER_UP -> { lastN = -1 }
            else -> {
                if (n != lastN) { lx = cx; ly = cy; lastN = n }
                if (e.actionMasked == MotionEvent.ACTION_MOVE) {
                    val dm = resources.displayMetrics
                    cfg.top = (cfg.top + (cy - ly) / dm.density).coerceIn(0f, 700f)
                    cfg.cx = (cfg.cx + (cx - lx) / dm.widthPixels * 100f).coerceIn(-60f, 60f)
                    lx = cx
                    ly = cy
                    updatePos()
                }
            }
        }
    }

    private fun toggle() {
        open = !open
        if (open) {
            panel.visibility = View.VISIBLE
            panel.post {
                panel.translationY = panel.height.toFloat()
                panel.animate().translationY(0f).setDuration(380).setInterpolator(OvershootInterpolator(0.7f)).start()
            }
            pill.animate().alpha(0f).setDuration(150).start()
        } else {
            panel.animate().translationY(panel.height.toFloat()).setDuration(260).withEndAction { panel.visibility = View.INVISIBLE }.start()
            pill.animate().alpha(1f).setDuration(250).start()
        }
    }

    private fun slider(label: String, min: Int, max: Int, v: Int, on: (Int) -> Unit): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        val t = m.tv("$label: $v", 13f, m.onSurfaceVar)
        l.addView(t, m.lp(m.MP, m.WC, 0, 8, 0, 0))
        val sb = SeekBar(this)
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
        l.addView(sb, m.lp(m.MP, m.WC))
        return l
    }

    private fun swatch(col: Int, sel: Boolean, click: () -> Unit): View {
        val v = object : View(this) {
            override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(m.dp(36), m.dp(36)) }
        }
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(col)
        g.setStroke(m.dp(if (sel) 3 else 1), if (sel) m.primary else m.a(m.outline, 0.7f))
        v.background = g
        v.setOnClickListener { click() }
        return v
    }

    private fun fillBody() {
        body.removeAllViews()
        if (tab == 0) {
            body.addView(m.tv("Estilo", 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 0, 0, 6))
            val fl = RtFlow(this, m.dp(8))
            fun preset(name: String, layout: String, w: Int, wd: Int, ro: Int) {
                fl.addView(m.chip(name, cfg.layout == layout && cfg.wght == w && cfg.wdth == wd && cfg.rond == ro) {
                    cfg.layout = layout; cfg.wght = w; cfg.wdth = wd; cfg.rond = ro
                    clock.applyConfig(cfg); fillBody()
                })
            }
            preset("Grueso", "stack", 800, 100, 0)
            preset("Fino", "stack", 200, 70, 0)
            preset("Condensado", "stack", 400, 50, 0)
            preset("Redondo", "stack", 600, 100, 100)
            preset("Ancho", "stack", 500, 140, 0)
            preset("En línea", "inline", 500, 90, 0)
            body.addView(fl, m.lp(m.MP, m.WC))
            body.addView(m.tv("Color", 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 14, 0, 6))
            val cf = RtFlow(this, m.dp(8))
            for (c in palette) { val x = c; cf.addView(swatch(x, cfg.color == x) { cfg.color = x; clock.applyConfig(cfg); fillBody() }, ViewGroup.LayoutParams(m.dp(36), m.dp(36))) }
            body.addView(cf, m.lp(m.MP, m.WC))
            body.addView(m.switchRow("Mostrar fecha", "", cfg.date) { v -> cfg.date = v; clock.applyConfig(cfg) }, m.lp(m.MP, m.WC, 0, 12, 0, 0))
            body.addView(slider("Tamaño base", 80, 260, cfg.size) { cfg.size = it })
        } else {
            body.addView(slider("Grosor", 100, 900, cfg.wght) { cfg.wght = it })
            body.addView(slider("Ancho", 50, 150, cfg.wdth) { cfg.wdth = it })
            body.addView(slider("Redondez", 0, 100, cfg.rond) { cfg.rond = it })
            body.addView(slider("Inclinación", -10, 0, cfg.slnt) { cfg.slnt = it })
            body.addView(m.tv("Efectos", 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 14, 0, 6))
            val ef = RtFlow(this, m.dp(8))
            for (p in listOf("solid" to "Sólido", "blend" to "Mezcla", "glass" to "Cristal", "overlay" to "Superposición")) {
                val id = p.first
                ef.addView(m.chip(p.second, cfg.fx == id) { cfg.fx = id; clock.applyConfig(cfg); fillBody() })
            }
            body.addView(ef, m.lp(m.MP, m.WC))
            body.addView(slider("Transparencia", 0, 100, cfg.tr) { cfg.tr = it })
        }
    }

    private fun applyAll() {
        val p = getSharedPreferences("lock", MODE_PRIVATE)
        p.edit().putString("sc_cfg", cfg.enc()).putBoolean("sc_on", true).putBoolean("ic_on", false).apply()
        val ok = StudioPush.push(p)
        Toast.makeText(this, if (ok) "Aplicado. Bloquea la pantalla para verlo" else "Guardado, pero el módulo LSPosed no está activo", Toast.LENGTH_LONG).show()
        finish()
    }
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
