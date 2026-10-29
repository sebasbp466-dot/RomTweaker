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
        hookLock(prefs)
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
            val m = TextView::class.java.getDeclaredMethod("onDraw", android.graphics.Canvas::class.java)
            hook(m).intercept { chain ->
                val tv = chain.thisObject as? TextView
                if (tv == null || prefs == null ||
                    !tv.javaClass.name.startsWith("com.android.systemui.shared.clocks.view.FlexClock") ||
                    !prefs.getBoolean("lk_on", false)) {
                    chain.proceed()
                } else {
                    val p = tv.paint
                    val os = p.shader
                    val ost = p.style
                    val osw = p.strokeWidth
                    try {
                        val st = prefs.getString("lk_style", "glass") ?: "glass"
                        val col = (prefs.getString("lk_color", "-1") ?: "-1").toIntOrNull() ?: -1
                        val acc = (prefs.getString("lk_accent", "-34243") ?: "-34243").toIntOrNull() ?: -34243
                        val k = ((prefs.getString("lk_glass", "80") ?: "80").toIntOrNull() ?: 80) / 100f
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
}
