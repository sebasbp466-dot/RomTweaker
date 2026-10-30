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
            val agsl = """
                uniform shader inp;
                uniform float h;
                uniform half4 c1;
                uniform half4 c2;
                uniform float mode;
                uniform float edge;
                half4 main(float2 p) {
                    float2 q = p;
                    if (mode > 2.5) { q.x += sin(p.y * 0.03) * 7.0; }
                    half4 s = inp.eval(q);
                    float t = clamp(p.y / h, 0.0, 1.0);
                    half4 c = mix(c1, c2, half(t));
                    float a = s.a * c.a;
                    if (mode > 0.5 && mode < 1.5) { a *= 0.62 + 0.38 * abs(sin(p.x * 0.45)); }
                    if (mode > 1.5 && mode < 2.5) { a *= 0.5 + 0.5 * fract(p.x / 16.0); }
                    half3 rgb = c.rgb;
                    if (edge > 0.5) {
                        float d = 2.5;
                        float au = inp.eval(q + float2(0.0, -d)).a;
                        float ad = inp.eval(q + float2(0.0, d)).a;
                        float al = inp.eval(q + float2(-d, 0.0)).a;
                        float ar = inp.eval(q + float2(d, 0.0)).a;
                        float rim = clamp(s.a - min(min(au, ad), min(al, ar)), 0.0, 1.0);
                        float top = clamp(s.a - au, 0.0, 1.0);
                        float bot = clamp(s.a - ad, 0.0, 1.0);
                        rgb = rgb + (half3(1.0) - rgb) * half(0.85 * rim * (0.4 + 0.6 * top));
                        rgb = rgb * half(1.0 - 0.35 * bot);
                        a = max(a, rim * 0.8);
                    }
                    return half4(rgb * a, a);
                }
            """
            val applied = java.util.WeakHashMap<android.view.View, String>()
            hook(mm).intercept { chain ->
                val v = chain.thisObject as? android.view.View
                if (v == null || !v.javaClass.name.startsWith(base)) {
                    chain.proceed()
                } else if (!(prefs?.getBoolean("lk_on", true) ?: true)) {
                    if (applied.remove(v) != null) v.post { try { v.setRenderEffect(null) } catch (e: Throwable) { } }
                    chain.proceed()
                } else {
                    if (seen.add(v.javaClass.name)) log(Log.INFO, TAG, "dibuja ${v.javaClass.name} ${v.width}x${v.height}")
                    diag(v)
                    val st = prefs?.getString("lk_style", "glass") ?: "glass"
                    val col = (prefs?.getString("lk_color", "-1") ?: "-1").toIntOrNull() ?: -1
                    val acc = (prefs?.getString("lk_accent", "-34243") ?: "-34243").toIntOrNull() ?: -34243
                    val k = ((prefs?.getString("lk_glass", "80") ?: "80").toIntOrNull() ?: 80) / 100f
                    val fl = (prefs?.getString("lk_filter", "0") ?: "0").toIntOrNull() ?: 0
                    val wt = (prefs?.getString("lk_weight", "0") ?: "0").toIntOrNull() ?: 0
                    val key = "$st|$col|$acc|$k|$fl|$wt|${v.height}"
                    if (applied[v] != key) {
                        applied[v] = key
                        val hh = Math.max(1f, v.height.toFloat())
                        v.post {
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    val rs = android.graphics.RuntimeShader(agsl)
                                    val glass = st.startsWith("glass")
                                    val c2 = if (glass) col else acc
                                    val a1 = if (glass) 0.95f else 1f
                                    val a2 = if (glass) 0.30f + 0.5f * (1f - k) else 1f
                                    rs.setFloatUniform("h", hh)
                                    rs.setFloatUniform("mode", fl.toFloat())
                                    rs.setFloatUniform("edge", if (glass) 1f else 0f)
                                    rs.setFloatUniform("c1", android.graphics.Color.red(col) / 255f, android.graphics.Color.green(col) / 255f, android.graphics.Color.blue(col) / 255f, a1)
                                    rs.setFloatUniform("c2", android.graphics.Color.red(c2) / 255f, android.graphics.Color.green(c2) / 255f, android.graphics.Color.blue(c2) / 255f, a2)
                                    v.setRenderEffect(android.graphics.RenderEffect.createRuntimeShaderEffect(rs, "inp"))
                                    if (wt > 0) applyWeight(v, wt)
                                    log(Log.INFO, TAG, "efecto aplicado: $st h=$hh")
                                }
                            } catch (e: Throwable) { log(Log.WARN, TAG, "renderEffect: $e") }
                        }
                    }
                    if (wt > 0) enforceWeight(v, wt)
                    chain.proceed()
                }
            }
            log(Log.INFO, TAG, "reloj de bloqueo (v2) enganchado en ${mm.declaringClass.name}.$name")
        } catch (e: Throwable) {
            log(Log.WARN, TAG, "hookLock2 falló: ${e.message}")
        }
    }

    private fun applyWeight(v: android.view.View, w: Int) {
        if (v is android.view.ViewGroup) {
            for (i in 0 until v.childCount) applyWeight(v.getChildAt(i), w)
        } else if (v is TextView) {
            try { val want = "'wght' " + w; if (v.fontVariationSettings != want) v.setFontVariationSettings(want) } catch (e: Throwable) { }
        }
    }

    private val lastW = java.util.WeakHashMap<android.view.View, Long>()
    private var diagDone = false

    private fun enforceWeight(v: android.view.View, w: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        val last = lastW[v] ?: 0L
        if (now - last < 400L) return
        lastW[v] = now
        applyWeight(v, w)
    }

    private fun diag(v: android.view.View) {
        if (diagDone) return
        diagDone = true
        try {
            val sb = StringBuilder("diag:")
            var first: Class<*>? = null
            fun walk(x: android.view.View) {
                if (x is android.view.ViewGroup) {
                    for (i in 0 until x.childCount) walk(x.getChildAt(i))
                } else if (x is TextView) {
                    if (first == null) first = x.javaClass
                    sb.append(" [").append(x.javaClass.name).append(" fvs=").append(x.fontVariationSettings).append(" t=").append(x.text).append("]")
                }
            }
            walk(v)
            log(Log.INFO, TAG, sb.toString())
            val names = LinkedHashSet<String>()
            var c: Class<*>? = first
            while (c != null && c != TextView::class.java) {
                for (m in c.declaredMethods) if (Regex("(?i)style|weight|variation|axis|font|anim").containsMatchIn(m.name)) names.add(m.name)
                c = c.superclass
            }
            log(Log.INFO, TAG, "metodos: " + names.joinToString(",").take(700))
        } catch (e: Throwable) { log(Log.WARN, TAG, "diag: $e") }
    }
}
