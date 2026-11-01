package dev.romtweaker.app.xp

import android.content.Context
import android.content.SharedPreferences
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference

/** Relojes de Iconify en la pantalla de bloqueo real: oculta el reloj de serie e inyecta el layout en keyguard_root_view. Las notificaciones se anclan debajo. */
object IcModule {
    private const val TAG = "RomTweaker"
    private const val APP = "dev.romtweaker.app"
    private const val BASE = "com.android.systemui.shared.clocks.view.FlexClock"
    private const val SECT = "com.android.systemui.keyguard.ui.view.layout.sections."
    private var cont: WeakReference<FrameLayout>? = null
    private var rootRef: WeakReference<ViewGroup>? = null
    private var contKey = ""
    private var building = false
    private var lastView: WeakReference<View>? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var errs = 0
    private val stock = java.util.WeakHashMap<View, Boolean>()
    private var mirroring = false

    private fun find(c0: Class<*>?, name: String): java.lang.reflect.Method? {
        var c = c0
        while (c != null) {
            val m = try { c.getDeclaredMethod(name, android.graphics.Canvas::class.java) } catch (e: Throwable) { null }
            if (m != null) return m
            c = c.superclass
        }
        return null
    }

    fun install(x: XposedModule, cl: ClassLoader, prefs: SharedPreferences?) {
        try {
            val grp = try { Class.forName(BASE + "ViewGroup", false, cl) } catch (e: Throwable) { null }
            val txt = try { Class.forName(BASE + "TextView", false, cl) } catch (e: Throwable) { null }
            val done = HashSet<java.lang.reflect.Method>()
            val list = ArrayList<java.lang.reflect.Method>()
            find(grp, "dispatchDraw")?.let { if (done.add(it)) list.add(it) }
            find(txt, "onDraw")?.let { if (done.add(it)) list.add(it) }
            if (list.isEmpty()) { x.log(Log.WARN, TAG, "ic: sin clases FlexClock"); return }
            for (mm in list) {
                x.hook(mm).intercept { chain ->
                    val v = chain.thisObject as? View
                    if (v == null || !v.javaClass.name.startsWith(BASE)) {
                        chain.proceed()
                    } else {
                        lastView = WeakReference(v)
                        if (!stock.containsKey(v)) stock[v] = true
                        val on = prefs?.getBoolean("ic_on", false) ?: false
                        var hide = false
                        try { hide = handle(x, v, prefs, on) } catch (e: Throwable) {
                            if (errs++ < 5) x.log(Log.WARN, TAG, "ic: error " + Log.getStackTraceString(e))
                        }
                        if (hide) null else chain.proceed()
                    }
                }
            }
            // Cuando el sistema recalcula las posiciones de las notificaciones, volvemos a anclarlas bajo el reloj.
            for (sn in listOf("DefaultNotificationStackScrollLayoutSection", "AodNotificationIconsSection")) {
                try {
                    val k = Class.forName(SECT + sn, false, cl)
                    val m = k.declaredMethods.firstOrNull { it.name == "applyConstraints" } ?: continue
                    x.hook(m).intercept { chain ->
                        val r = chain.proceed()
                        try {
                            val root = rootRef?.get()
                            val c = cont?.get()
                            if (root != null && c != null && c.parent === root) root.post { try { anchor(root, c.id) } catch (e: Throwable) { } }
                        } catch (e: Throwable) { }
                        r
                    }
                    x.log(Log.INFO, TAG, "ic: ancla de notificaciones en $sn")
                } catch (e: Throwable) { x.log(Log.INFO, TAG, "ic: sin $sn en esta ROM") }
            }
            val l = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
                if (k != null && k.startsWith("ic_")) lastView?.get()?.let { v -> v.post { try { v.invalidate(); (v.parent as? View)?.invalidate() } catch (e: Throwable) { } } }
            }
            listener = l
            prefs?.registerOnSharedPreferenceChangeListener(l)
            x.log(Log.INFO, TAG, "ic: instalado (" + list.size + " hooks)")
        } catch (e: Throwable) { x.log(Log.WARN, TAG, "ic: fallo instalando " + e) }
    }

    private fun root(v: View): ViewGroup? {
        var p: android.view.ViewParent? = v.parent
        while (p != null) {
            val pv = p as? View ?: return null
            if (pv.id != View.NO_ID) {
                val n = try { v.resources.getResourceEntryName(pv.id) } catch (e: Throwable) { "" }
                if (n == "keyguard_root_view") return pv as? ViewGroup
            }
            p = pv.parent
        }
        return null
    }

    private fun blank(): RenderEffect = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(ColorMatrix(FloatArray(20))))

    /** devuelve true si hay que NO dibujar el reloj de serie */
    private fun handle(x: XposedModule, v: View, prefs: SharedPreferences?, on: Boolean): Boolean {
        val cur = cont?.get()
        val sc = prefs?.getBoolean("sc_on", false) ?: false
        if (!on && !sc) {
            if (cur != null) {
                cont = null; contKey = ""
                v.post { try { val r = cur.parent as? ViewGroup; r?.removeView(cur); r?.let { hideDate(it, false); it.requestLayout() } } catch (e: Throwable) { } }
            }
            return false
        }
        fun gi(k: String, d: Int) = (prefs?.getString(k, d.toString()) ?: d.toString()).toIntOrNull() ?: d
        val n = gi("ic_style", 1)
        val top = gi("ic_top", 100)
        val bot = gi("ic_bottom", 40)
        val scl = gi("ic_scale", 100)
        val hd = prefs?.getBoolean("ic_date", true) ?: true
        val scCfg = if (sc) (prefs?.getString("sc_cfg", "") ?: "") else ""
        val key = if (sc) "S|$scCfg" else "$n|$top|$bot|$scl|$hd"
        if (cur != null && cur.isAttachedToWindow && key == contKey) { startMirror(cur); return true }
        if (building) return true
        val r = root(v) ?: return false
        building = true
        v.post {
            try { build(x, r, cur, n, top, bot, scl, if (sc) true else hd, key, if (sc) scCfg else null) }
            catch (e: Throwable) { contKey = ""; x.log(Log.WARN, TAG, "ic: build " + Log.getStackTraceString(e)) }
            finally { building = false }
        }
        return true
    }

    /** Copia la transparencia y el desplazamiento del reloj de serie (que anima SystemUI) al nuestro, fotograma a fotograma. */
    private fun startMirror(c: FrameLayout) {
        if (mirroring) return
        mirroring = true
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(t: Long) {
                try {
                    val cc = cont?.get()
                    val r = rootRef?.get()
                    if (cc == null || r == null || !cc.isAttachedToWindow || !r.isShown) { mirroring = false; return }
                    var best = -1f
                    var bty = 0f
                    for (e in stock.entries) {
                        val sv = e.key
                        if (!sv.isAttachedToWindow || !sv.isShown) continue
                        var a = 1f
                        var ty = 0f
                        var p: View? = sv
                        var d = 0
                        while (p != null && p !== r && d < 10) { a *= p.alpha; ty += p.translationY; p = p.parent as? View; d++ }
                        if (a > best) { best = a; bty = ty }
                    }
                    if (best >= 0f) {
                        if (cc.alpha != best) cc.alpha = best
                        if (cc.translationY != bty) cc.translationY = bty
                    }
                    Choreographer.getInstance().postFrameCallback(this)
                } catch (e: Throwable) { mirroring = false }
            }
        }
        Choreographer.getInstance().postFrameCallback(cb)
    }

    private fun dateView(r: ViewGroup): View? {
        val id = r.resources.getIdentifier("keyguard_slice_view", "id", r.context.packageName)
        return if (id != 0) r.findViewById(id) else null
    }
    private fun hideDate(r: ViewGroup, hide: Boolean) { try { dateView(r)?.setRenderEffect(if (hide) blank() else null) } catch (e: Throwable) { } }

    private fun cap(s: String): String {
        val sb = StringBuilder(s.length)
        var up = true
        for (ch in s) { sb.append(if (up && ch.isLetter()) ch.uppercaseChar() else ch); up = ch.isWhitespace() || ch == '\n' }
        return sb.toString()
    }
    private fun titleAll(v: View) {
        if (v is TextView) {
            val fix = { t: TextView -> val s = t.text?.toString() ?: ""; if (s.isNotEmpty()) { val u = cap(s); if (u != s) t.text = u } }
            fix(v)
            v.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun afterTextChanged(e: Editable?) { fix(v) }
            })
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) titleAll(v.getChildAt(i))
    }
    private fun scaleText(v: View, f: Float) {
        if (v is TextView) v.setTextSize(TypedValue.COMPLEX_UNIT_PX, v.textSize * f)
        if (v is ViewGroup) for (i in 0 until v.childCount) scaleText(v.getChildAt(i), f)
    }

    private fun build(x: XposedModule, r: ViewGroup, cur: FrameLayout?, n: Int, top: Int, bot: Int, sc: Int, hd: Boolean, key: String, studio: String?) {
        val ctx = r.context
        val d = ctx.resources.displayMetrics.density
        val inflated: View
        val ml = FrameLayout.LayoutParams(-1, -2)
        if (studio != null) {
            val sk = dev.romtweaker.app.StudioClock(ctx)
            val cfg = dev.romtweaker.app.StudioCfg.parse(studio)
            sk.applyConfig(cfg)
            sk.translationX = cfg.cx / 100f * ctx.resources.displayMetrics.widthPixels
            inflated = sk
            ml.width = -2
            ml.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ml.topMargin = (cfg.top * d).toInt()
            ml.bottomMargin = (24 * d).toInt()
        } else {
            val pkg = ctx.createPackageContext(APP, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
            val id = pkg.resources.getIdentifier("preview_lockscreen_clock_$n", "layout", APP)
            if (id == 0) { x.log(Log.WARN, TAG, "ic: no existe el layout $n en la app"); return }
            inflated = LayoutInflater.from(pkg).inflate(id, null)
            if (n != 10) titleAll(inflated)
            if (sc != 100) scaleText(inflated, sc / 100f)
            ml.topMargin = (top * d).toInt()
            ml.bottomMargin = (bot * d).toInt()
        }
        val c: FrameLayout
        if (cur != null && cur.parent === r) {
            c = cur
            c.removeAllViews()
        } else {
            (cur?.parent as? ViewGroup)?.removeView(cur)
            c = FrameLayout(ctx)
            c.id = View.generateViewId()
            var lp: ViewGroup.LayoutParams
            try {
                val k = Class.forName("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams", false, r.javaClass.classLoader)
                lp = k.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).newInstance(-1, -2) as ViewGroup.LayoutParams
                for (f in listOf("topToTop", "startToStart", "endToEnd")) k.getField(f).setInt(lp, 0)
            } catch (e: Throwable) { lp = FrameLayout.LayoutParams(-1, -2) }
            c.layoutParams = lp
            r.addView(c)
        }
        c.addView(inflated, ml)
        cont = WeakReference(c)
        rootRef = WeakReference(r)
        contKey = key
        hideDate(r, hd)
        try { anchor(r, c.id) } catch (e: Throwable) { x.log(Log.WARN, TAG, "ic: ancla " + e) }
        x.log(Log.INFO, TAG, "ic: reloj " + (if (studio != null) "estudio" else n.toString()) + " top=$top bottom=$bot escala=$sc")
        startMirror(c)
    }

    /** Pone las notificaciones (y los iconos de AOD) justo debajo del contenedor del reloj. */
    private fun anchor(r: ViewGroup, cid: Int) {
        val k = Class.forName("androidx.constraintlayout.widget.ConstraintSet", false, r.javaClass.classLoader)
        val cs = k.getConstructor().newInstance()
        for (i in 0 until r.childCount) { val ch = r.getChildAt(i); if (ch.id == View.NO_ID) ch.id = View.generateViewId() }
        val I = Int::class.javaPrimitiveType
        k.methods.first { it.name == "clone" && it.parameterTypes.size == 1 && it.parameterTypes[0].isAssignableFrom(r.javaClass) }.invoke(cs, r)
        val conn = k.getMethod("connect", I, I, I, I)
        val clear = k.getMethod("clear", I, I)
        conn.invoke(cs, cid, 6, 0, 6)
        conn.invoke(cs, cid, 7, 0, 7)
        conn.invoke(cs, cid, 3, 0, 3)
        for (name in listOf("nssl_placeholder", "aod_notification_icon_container")) {
            val nid = r.resources.getIdentifier(name, "id", r.context.packageName)
            if (nid != 0) { clear.invoke(cs, nid, 3); conn.invoke(cs, nid, 3, cid, 4) }
        }
        k.methods.first { it.name == "applyTo" && it.parameterTypes.size == 1 && it.parameterTypes[0].isAssignableFrom(r.javaClass) }.invoke(cs, r)
    }
}
