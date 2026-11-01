package dev.romtweaker.app.xp

import android.content.Context
import android.content.SharedPreferences
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference

/** Relojes de Iconify en la pantalla de bloqueo real: se oculta el reloj de serie y se inyecta un layout propio en keyguard_root_view. */
object IcModule {
    private const val TAG = "RomTweaker"
    private const val APP = "dev.romtweaker.app"
    private const val BASE = "com.android.systemui.shared.clocks.view.FlexClock"
    private var cont: WeakReference<FrameLayout>? = null
    private var contKey = ""
    private var lastView: WeakReference<View>? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var errs = 0

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
                        val on = prefs?.getBoolean("ic_on", false) ?: false
                        var hide = false
                        try { hide = handle(x, v, prefs, on) } catch (e: Throwable) {
                            if (errs++ < 5) x.log(Log.WARN, TAG, "ic: error " + Log.getStackTraceString(e))
                        }
                        if (hide) null else chain.proceed()
                    }
                }
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
        if (!on) {
            if (cur != null) {
                v.post { try { (cur.parent as? ViewGroup)?.removeView(cur); restoreDate(cur) } catch (e: Throwable) { } }
                cont = null; contKey = ""
            }
            return false
        }
        val n = (prefs?.getString("ic_style", "1") ?: "1").toIntOrNull() ?: 1
        val top = (prefs?.getString("ic_top", "100") ?: "100").toIntOrNull() ?: 100
        val sc = (prefs?.getString("ic_scale", "100") ?: "100").toIntOrNull() ?: 100
        val hd = prefs?.getBoolean("ic_date", true) ?: true
        val key = "$n|$top|$sc|$hd"
        if (cur != null && cur.isAttachedToWindow && key == contKey) return true
        val r = root(v) ?: return false
        if (key == contKey && cur != null && cur.isAttachedToWindow) return true
        contKey = key
        v.post {
            try {
                if (cur != null) (cur.parent as? ViewGroup)?.removeView(cur)
                val c = build(x, r, n, top, sc)
                if (c != null) {
                    cont = WeakReference(c)
                    hideDate(r, hd)
                }
            } catch (e: Throwable) { x.log(Log.WARN, TAG, "ic: build " + Log.getStackTraceString(e)) }
        }
        return true
    }

    private fun dateView(r: ViewGroup): View? {
        val id = r.resources.getIdentifier("keyguard_slice_view", "id", r.context.packageName)
        return if (id != 0) r.findViewById(id) else null
    }
    private fun hideDate(r: ViewGroup, hide: Boolean) { try { dateView(r)?.setRenderEffect(if (hide) blank() else null) } catch (e: Throwable) { } }
    private fun restoreDate(c: View) { try { (c.parent as? ViewGroup)?.let { hideDate(it, false) } } catch (e: Throwable) { } }

    private fun build(x: XposedModule, r: ViewGroup, n: Int, top: Int, sc: Int): FrameLayout? {
        val ctx = r.context
        val pkg = ctx.createPackageContext(APP, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
        val id = pkg.resources.getIdentifier("preview_lockscreen_clock_$n", "layout", APP)
        if (id == 0) { x.log(Log.WARN, TAG, "ic: no existe el layout $n en la app"); return null }
        val inflated = LayoutInflater.from(pkg).inflate(id, null)
        val c = FrameLayout(ctx)
        c.id = View.generateViewId()
        c.addView(inflated, FrameLayout.LayoutParams(-1, -2))
        val d = ctx.resources.displayMetrics.density
        var lp: ViewGroup.MarginLayoutParams
        try {
            val k = Class.forName("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams", false, r.javaClass.classLoader)
            lp = k.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).newInstance(-1, -2) as ViewGroup.MarginLayoutParams
            for (f in listOf("topToTop", "startToStart", "endToEnd")) k.getField(f).setInt(lp, 0)
        } catch (e: Throwable) {
            lp = FrameLayout.LayoutParams(-1, -2)
        }
        lp.topMargin = (top * d).toInt()
        c.layoutParams = lp
        c.pivotY = 0f
        c.scaleX = sc / 100f
        c.scaleY = sc / 100f
        r.addView(c)
        x.log(Log.INFO, TAG, "ic: reloj $n inyectado top=$top scale=$sc lp=" + lp.javaClass.simpleName)
        return c
    }
}
