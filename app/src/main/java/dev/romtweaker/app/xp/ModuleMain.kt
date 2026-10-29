package dev.romtweaker.app.xp

import android.content.SharedPreferences
import android.util.Log
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/** Módulo LSPosed (libxposed). Los hooks se buscan por estructura, no por nombres fijos de versión. */
class ModuleMain : XposedModule() {
    companion object {
        const val TAG = "RomTweaker"
        const val CLOCK = "com.android.systemui.statusbar.policy.Clock"
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "módulo cargado en ${param.processName}")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != "com.android.systemui") return
        if (!param.isFirstPackage) return
        val prefs: SharedPreferences? = try { getRemotePreferences("rt") } catch (e: Throwable) { null }
        hookClock(param.classLoader, prefs)
        hookLock2(param.classLoader, prefs)
    }

    private fun formatted(prefs: SharedPreferences?, orig: Any?): Any? {
        val on = prefs?.getBoolean("clock_on", false) ?: false
        val fmt = prefs?.getString("clock_fmt", "") ?: ""
        if (!on || fmt.isEmpty()) return orig
        return try { android.text.format.DateFormat.format(fmt, System.currentTimeMillis()) } catch (e: Throwable) { orig }
    }

    private fun hookClock(cl: ClassLoader, prefs: SharedPreferences?) {
        try {
            val c = Class.forName(CLOCK, false, cl)
            var target: java.lang.reflect.Method? = null
            for (x in c.declaredMethods) {
                if (x.parameterTypes.isEmpty() && CharSequence::class.java.isAssignableFrom(x.returnType) &&
                    (x.name == "getSmallTime" || x.name.contains("Time", true))
                ) {
                    target = x
                    if (x.name == "getSmallTime") break
                }
            }
            val t = target
            if (t != null) {
                hook(t).intercept { chain -> formatted(prefs, chain.proceed()) }
                log(Log.INFO, TAG, "reloj enganchado por método: ${t.name}")
                return
            }
            val st = TextView::class.java.getDeclaredMethod("setText", CharSequence::class.java, TextView.BufferType::class.java)
            hook(st).intercept { chain ->
                val self = chain.thisObject
                if (self != null && self.javaClass.name == CLOCK) {
                    chain.proceed(arrayOf(formatted(prefs, chain.args[0]), chain.args[1]))
                } else {
                    chain.proceed()
                }
            }
            log(Log.INFO, TAG, "reloj enganchado por TextView.setText (plan B)")
        } catch (e: Throwable) {
            log(Log.WARN, TAG, "esta ROM no tiene el reloj esperado: ${e.message}")
        }
    }

    private fun argb(c: Int, f: Float): Int = (c and 0x00FFFFFF) or ((f.coerceIn(0f, 1f) * 255).toInt() shl 24)

    /** Reloj nativo de la pantalla de bloqueo (FlexClock*): le aplica degradado/cristal/contorno al dibujar. */
    private fun hookLock(prefs: SharedPreferences?) {
        try {
            val seen = HashSet<String>()
            val m = TextView::class.java.getDeclaredMethod("onDraw", android.graphics.Canvas::class.java)
            hook(m).intercept { chain ->
                val tv = chain.thisObject as? TextView
                if (tv == null ||
                    !tv.javaClass.name.startsWith("com.android.systemui.shared.clocks.view.FlexClock") ||
                    !(prefs?.getBoolean("lk_on", true) ?: true)) {
                    chain.proceed()
                } else {
                    if (seen.add(tv.javaClass.name)) log(Log.INFO, TAG, "dibuja ${tv.javaClass.name} h=${tv.height} prefs=${prefs != null}")
                    val p = tv.paint
                    val os = p.shader
                    val ost = p.style
                    val osw = p.strokeWidth
                    try {
                        val st = prefs?.getString("lk_style", "glass") ?: "glass"
                        val col = (prefs?.getString("lk_color", "-1") ?: "-1").toIntOrNull() ?: -1
                        val acc = (prefs?.getString("lk_accent", "-34243") ?: "-34243").toIntOrNull() ?: -34243
                        val k = ((prefs?.getString("lk_glass", "80") ?: "80").toIntOrNull() ?: 80) / 100f
                        val h = Math.max(1f, tv.height.toFloat())
                        if (st.startsWith("glass")) {
                            p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, argb(col, 0.95f), argb(col, 0.30f + 0.5f * (1f - k)), android.graphics.Shader.TileMode.CLAMP)
                            p.setShadowLayer(tv.textSize * 0.10f, 0f, 0f, argb(-1, 0.55f * k))
                        } else if (st == "hy_outline") {
                            p.style = android.graphics.Paint.Style.STROKE
                            p.strokeWidth = Math.max(2f, tv.textSize * 0.035f)
                            p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, col, acc, android.graphics.Shader.TileMode.CLAMP)
                        } else {
                            p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, col, acc, android.graphics.Shader.TileMode.CLAMP)
                        }
                        chain.proceed()
                    } finally {
                        p.shader = os
                        p.style = ost
                        p.strokeWidth = osw
                        p.clearShadowLayer()
                    }
                }
            }
            log(Log.INFO, TAG, "reloj de bloqueo nativo enganchado")
        } catch (e: Throwable) {
            log(Log.WARN, TAG, "no pude enganchar el reloj de bloqueo: ${e.message}")
        }
    }

    private fun chainOf(c: Class<*>?): String {
        val sb = StringBuilder()
        var x = c
        while (x != null) { sb.append(x.name).append(" > "); x = x.superclass }
        return sb.toString()
    }

    /** Reloj nativo del bloqueo: envuelve su dibujo y tiñe lo dibujado con degradado (sirve sea cual sea su implementación). */
    private fun hookLock2(cl: ClassLoader, prefs: SharedPreferences?) {
        try {
            val base = "com.android.systemui.shared.clocks.view.FlexClock"
            val grp = try { Class.forName(base + "ViewGroup", false, cl) } catch (e: Throwable) { null }
            val txt = try { Class.forName(base + "TextView", false, cl) } catch (e: Throwable) { null }
            log(Log.INFO, TAG, "FlexClockViewGroup: " + chainOf(grp))
            log(Log.INFO, TAG, "FlexClockTextView: " + chainOf(txt))
            val target: Class<*> = grp ?: txt ?: run { log(Log.WARN, TAG, "no hay clases FlexClock en esta ROM"); return }
            val name = if (grp != null) "dispatchDraw" else "onDraw"
            var c: Class<*>? = target
            var m: java.lang.reflect.Method? = null
            while (c != null && m == null) {
                m = try { c.getDeclaredMethod(name, android.graphics.Canvas::class.java) } catch (e: Throwable) { null }
                c = c.superclass
            }
            val mm = m ?: run { log(Log.WARN, TAG, "no encontré $name en la jerarquía"); return }
            val seen = HashSet<String>()
            hook(mm).intercept { chain ->
                val v = chain.thisObject as? android.view.View
                val cv = chain.args[0] as? android.graphics.Canvas
                if (v == null || cv == null || !v.javaClass.name.startsWith(base) ||
                    !(prefs?.getBoolean("lk_on", true) ?: true)) {
                    chain.proceed()
                } else {
                    if (seen.add(v.javaClass.name)) log(Log.INFO, TAG, "dibuja ${v.javaClass.name} ${v.width}x${v.height}")
                    val w = v.width.toFloat()
                    val h = Math.max(1f, v.height.toFloat())
                    val sc = cv.saveLayer(0f, 0f, w, h, null)
                    val r = chain.proceed()
                    try {
                        val st = prefs?.getString("lk_style", "glass") ?: "glass"
                        val col = (prefs?.getString("lk_color", "-1") ?: "-1").toIntOrNull() ?: -1
                        val acc = (prefs?.getString("lk_accent", "-34243") ?: "-34243").toIntOrNull() ?: -34243
                        val k = ((prefs?.getString("lk_glass", "80") ?: "80").toIntOrNull() ?: 80) / 100f
                        val pt = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                        pt.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
                        if (st.startsWith("glass")) {
                            pt.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, argb(col, 0.95f), argb(col, 0.30f + 0.5f * (1f - k)), android.graphics.Shader.TileMode.CLAMP)
                        } else {
                            pt.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, col, acc, android.graphics.Shader.TileMode.CLAMP)
                        }
                        cv.drawRect(0f, 0f, w, h, pt)
                    } catch (e: Throwable) { }
                    cv.restoreToCount(sc)
                    r
                }
            }
            log(Log.INFO, TAG, "reloj de bloqueo (v2) enganchado en ${mm.declaringClass.name}.$name")
        } catch (e: Throwable) {
            log(Log.WARN, TAG, "hookLock2 falló: ${e.message}")
        }
    }
}
