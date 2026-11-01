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
        hookWeight(param.classLoader, prefs)
        hookLockSmall(param.classLoader, prefs)
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

    private val mLast = HashMap<String, String>()
    private val mPend = HashMap<String, String>()
    private val mRun = HashMap<String, Runnable>()
    private val liveViews = java.util.WeakHashMap<android.view.View, Boolean>()
    private var liveListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Envía a la app las medidas REALES del reloj cuando dejan de cambiar (700 ms estables) y reaplica el efecto al cambiar ajustes. */
    private fun pushMetrics(v: android.view.View, kind: String, prefs: SharedPreferences?) {
        try {
            liveViews[v] = true
            if (liveListener == null && prefs != null) {
                val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    try { for (x in liveViews.keys.toList()) x.post { x.requestLayout(); x.invalidate() } } catch (e: Throwable) { }
                }
                liveListener = l
                prefs.registerOnSharedPreferenceChangeListener(l)
            }
            if (!v.isShown || v.width <= 0 || v.alpha < 0.99f) return
            val dm = v.resources.displayMetrics
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            val arr = org.json.JSONArray()
            fun walk(x: android.view.View) {
                if (x is android.view.ViewGroup) {
                    for (i in 0 until x.childCount) walk(x.getChildAt(i))
                } else if (x is TextView && x.visibility == android.view.View.VISIBLE && x.text.isNotEmpty()) {
                    val l2 = IntArray(2)
                    x.getLocationOnScreen(l2)
                    val o = org.json.JSONObject()
                    o.put("t", x.text.toString())
                    o.put("cx", (l2[0] + x.width / 2f).toDouble())
                    o.put("base", (l2[1] + x.baseline).toDouble())
                    o.put("size", x.textSize.toDouble())
                    arr.put(o)
                }
            }
            walk(v)
            if (arr.length() == 0) return
            val j = org.json.JSONObject()
            j.put("sw", dm.widthPixels); j.put("sh", dm.heightPixels)
            j.put("gx", loc[0]); j.put("gy", loc[1]); j.put("gw", v.width); j.put("gh", v.height)
            j.put("d", arr)
            val str = j.toString()
            if (str == mLast[kind]) {
                mRun[kind]?.let { v.removeCallbacks(it) }
                mRun.remove(kind); mPend.remove(kind)
                return
            }
            if (str == mPend[kind]) return
            mPend[kind] = str
            mRun[kind]?.let { v.removeCallbacks(it) }
            val ctx = v.context
            val r = Runnable {
                try {
                    mLast[kind] = str
                    mPend.remove(kind); mRun.remove(kind)
                    val intent = android.content.Intent("dev.romtweaker.app.CLOCK_METRICS").setPackage("dev.romtweaker.app")
                    intent.putExtra("kind", kind)
                    intent.putExtra("json", str)
                    ctx.sendBroadcast(intent)
                    log(Log.INFO, TAG, "medidas enviadas ($kind): " + str.take(200))
                } catch (e: Throwable) { log(Log.WARN, TAG, "metricas: $e") }
            }
            mRun[kind] = r
            v.postDelayed(r, 700L)
        } catch (e: Throwable) { log(Log.WARN, TAG, "metricas: $e") }
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
            var m: java.lang.reflect.Method? = null
            if (grp != null) {
                try { log(Log.INFO, TAG, "FlexClockViewGroup declara: " + grp.declaredMethods.map { it.name }.distinct().sorted().joinToString(",").take(600)) } catch (e: Throwable) { }
                for (cand in listOf("dispatchDraw", "onLayout", "onMeasure")) {
                    if (m == null) m = grp.declaredMethods.firstOrNull { it.name == cand }
                }
            }
            var c: Class<*>? = target
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
                    pushMetrics(v, "stack", prefs)
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
                                    val c2 = if (glass || st == "solid") col else acc
                                    val a1 = if (glass) 0.95f else 1f
                                    val a2 = if (glass) 0.30f + 0.5f * (1f - k) else 1f
                                    rs.setFloatUniform("h", hh)
                                    rs.setFloatUniform("mode", fl.toFloat())
                                    rs.setFloatUniform("edge", if (glass) 1f else 0f)
                                    rs.setFloatUniform("c1", android.graphics.Color.red(col) / 255f, android.graphics.Color.green(col) / 255f, android.graphics.Color.blue(col) / 255f, a1)
                                    rs.setFloatUniform("c2", android.graphics.Color.red(c2) / 255f, android.graphics.Color.green(c2) / 255f, android.graphics.Color.blue(c2) / 255f, a2)
                                    v.setRenderEffect(android.graphics.RenderEffect.createRuntimeShaderEffect(rs, "inp"))
                                    
                                    log(Log.INFO, TAG, "efecto aplicado: $st h=$hh")
                                }
                            } catch (e: Throwable) { log(Log.WARN, TAG, "renderEffect: $e") }
                        }
                    }
                    
                    chain.proceed()
                }
            }
            log(Log.INFO, TAG, "reloj de bloqueo (v2) enganchado en ${mm.declaringClass.name}.${mm.name}")
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

    private fun setWght(s: String, w: Int): String {
        val re = Regex("'wght'\\s*[-0-9.]+")
        return if (re.containsMatchIn(s)) re.replace(s, "'wght' " + w) else if (s.isEmpty()) "'wght' " + w else "'wght' " + w + ", " + s
    }

    private val dumped = HashSet<String>()

    private fun dumpObj(o: Any?): String {
        if (o == null) return "null"
        val sb = StringBuilder(o.javaClass.simpleName).append("{")
        var c: Class<*>? = o.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                try { f.isAccessible = true; sb.append(f.name).append("=").append(("" + f.get(o)).take(140)).append("; ") } catch (e: Throwable) { }
            }
            c = c.superclass
        }
        return sb.append("}").toString().take(900)
    }

    /** Impone wght dentro del objeto devuelto (cadenas, mapas, listas o pares clave/valor). */
    private fun forceWght(o: Any?, w: Int, depth: Int): Boolean {
        if (o == null || depth > 3) return false
        var changed = false
        try {
            val fk = o.javaClass.getDeclaredField("key")
            fk.isAccessible = true
            if (fk.get(o) == "wght") {
                val fv = o.javaClass.getDeclaredField("value")
                fv.isAccessible = true
                if (fv.type == java.lang.Double.TYPE) fv.set(o, w.toDouble()) else fv.set(o, w.toFloat())
                changed = true
            }
        } catch (e: Throwable) { }
        var c: Class<*>? = o.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                try {
                    f.isAccessible = true
                    val v = f.get(o)
                    if (v is String && v.contains("wght")) {
                        f.set(o, setWght(v, w)); changed = true
                    } else if (v is MutableMap<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        val mm = v as MutableMap<Any?, Any?>
                        val old = mm["wght"]
                        if (old != null) {
                            mm["wght"] = if (old is Double) w.toDouble() else if (old is Int) w else w.toFloat()
                            changed = true
                        }
                    } else if (v is Iterable<*>) {
                        for (e in v) if (forceWght(e, w, depth + 1)) changed = true
                    } else if (v != null && v.javaClass.name.startsWith("com.android.systemui")) {
                        if (forceWght(v, w, depth + 1)) changed = true
                    }
                } catch (e: Throwable) { }
            }
            c = c.superclass
        }
        return changed
    }

    /** Los métodos estáticos buildFontVariations / buildAnimationTargetVariation construyen los ejes de la fuente. */
    private fun hookWeight(cl: ClassLoader, prefs: SharedPreferences?) {
        try {
            val base = "com.android.systemui.shared.clocks.view.FlexClock"
            val t = Class.forName(base + "TextView", false, cl)
            val names = setOf("buildFontVariations", "buildAnimationTargetVariation")
            var c: Class<*>? = t
            var n = 0
            while (c != null && c != TextView::class.java) {
                for (m in c.declaredMethods) {
                    if (m.name in names) {
                        hook(m).intercept { chain ->
                            val r = chain.proceed()
                            if (r != null) {
                                if (dumped.add(m.name)) log(Log.INFO, TAG, "dump " + m.name + ": " + dumpObj(r))
                                val wt = (prefs?.getString("lk_weight", "0") ?: "0").toIntOrNull() ?: 0
                                val on = prefs?.getBoolean("lk_on", true) ?: true
                                if (on) {
                                    val ax = HashMap<String, Int>()
                                    if (wt > 0) ax["wght"] = wt
                                    val wd = prefs?.getString("lk_wdth", null)?.toIntOrNull()
                                    if (wd != null) ax["wdth"] = wd
                                    val ro = prefs?.getString("lk_rond", null)?.toIntOrNull()
                                    if (ro != null) ax["ROND"] = ro
                                    val sl = prefs?.getString("lk_slnt", null)?.toIntOrNull()
                                    if (sl != null) ax["slnt"] = sl
                                    if (ax.isNotEmpty()) forceAx(r, ax, 0)
                                }
                            }
                            r
                        }
                        n++
                    }
                }
                c = c.superclass
            }
            log(Log.INFO, TAG, "grosor: " + n + " metodos enganchados")
        } catch (e: Throwable) { log(Log.WARN, TAG, "hookWeight: " + e) }
    }

    private fun setAxis(s: String, k: String, w: Int): String {
        val re = Regex("'" + k + "'\\s*[-0-9.]+")
        return if (re.containsMatchIn(s)) re.replace(s, "'" + k + "' " + w) else s
    }

    /** Impone varios ejes (wght, wdth, ROND, slnt) dentro del objeto devuelto. */
    private fun forceAx(o: Any?, ax: Map<String, Int>, depth: Int): Boolean {
        if (o == null || depth > 3) return false
        var changed = false
        try {
            val fk = o.javaClass.getDeclaredField("key")
            fk.isAccessible = true
            val kv = fk.get(o)
            if (kv is String && ax.containsKey(kv)) {
                val fv = o.javaClass.getDeclaredField("value")
                fv.isAccessible = true
                val w = ax[kv] ?: 0
                if (fv.type == java.lang.Double.TYPE) fv.set(o, w.toDouble()) else fv.set(o, w.toFloat())
                changed = true
            }
        } catch (e: Throwable) { }
        var c: Class<*>? = o.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                try {
                    f.isAccessible = true
                    val v = f.get(o)
                    if (v is String && v.contains("wght")) {
                        var ns: String = v
                        for ((k, w) in ax) ns = setAxis(ns, k, w)
                        if (ns != v) { f.set(o, ns); changed = true }
                    } else if (v is MutableMap<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        val mm = v as MutableMap<Any?, Any?>
                        for ((k, w) in ax) {
                            val old = mm[k]
                            if (old != null) {
                                mm[k] = if (old is Double) w.toDouble() else if (old is Int) w else w.toFloat()
                                changed = true
                            }
                        }
                    } else if (v is Iterable<*>) {
                        for (e in v) if (forceAx(e, ax, depth + 1)) changed = true
                    } else if (v != null && v.javaClass.name.startsWith("com.android.systemui")) {
                        if (forceAx(v, ax, depth + 1)) changed = true
                    }
                } catch (e: Throwable) { }
            }
            c = c.superclass
        }
        return changed
    }

    private fun fxApply(v: android.view.View, prefs: SharedPreferences?, applied: java.util.WeakHashMap<android.view.View, String>) {
        val st = prefs?.getString("lk_style", "glass") ?: "glass"
        val col = (prefs?.getString("lk_color", "-1") ?: "-1").toIntOrNull() ?: -1
        val acc = (prefs?.getString("lk_accent", "-34243") ?: "-34243").toIntOrNull() ?: -34243
        val k = ((prefs?.getString("lk_glass", "80") ?: "80").toIntOrNull() ?: 80) / 100f
        val fl = (prefs?.getString("lk_filter", "0") ?: "0").toIntOrNull() ?: 0
        val key = st + "|" + col + "|" + acc + "|" + k + "|" + fl + "|" + v.height
        if (applied[v] == key) return
        applied[v] = key
        val hh = Math.max(1f, v.height.toFloat())
        v.post {
            try {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    val rs = android.graphics.RuntimeShader(dev.romtweaker.app.FlexPreview.AGSL)
                    val glass = st.startsWith("glass")
                    val c2 = if (glass || st == "solid") col else acc
                    val a1 = if (glass) 0.95f else 1f
                    val a2 = if (glass) 0.30f + 0.5f * (1f - k) else 1f
                    rs.setFloatUniform("h", hh)
                    rs.setFloatUniform("mode", fl.toFloat())
                    rs.setFloatUniform("edge", if (glass) 1f else 0f)
                    rs.setFloatUniform("c1", android.graphics.Color.red(col) / 255f, android.graphics.Color.green(col) / 255f, android.graphics.Color.blue(col) / 255f, a1)
                    rs.setFloatUniform("c2", android.graphics.Color.red(c2) / 255f, android.graphics.Color.green(c2) / 255f, android.graphics.Color.blue(c2) / 255f, a2)
                    v.setRenderEffect(android.graphics.RenderEffect.createRuntimeShaderEffect(rs, "inp"))
                    log(Log.INFO, TAG, "efecto (reloj pequeno) aplicado: " + st)
                }
            } catch (e: Throwable) { log(Log.WARN, TAG, "fxApply: " + e) }
        }
    }

    /** El reloj compacto (con notificaciones) es un FlexClockTextView suelto: se engancha su propio onDraw. */
    private fun hookLockSmall(cl: ClassLoader, prefs: SharedPreferences?) {
        try {
            val base = "com.android.systemui.shared.clocks.view.FlexClock"
            val t = Class.forName(base + "TextView", false, cl)
            var c: Class<*>? = t
            var m: java.lang.reflect.Method? = null
            while (c != null && c != TextView::class.java && m == null) {
                m = try { c.getDeclaredMethod("onDraw", android.graphics.Canvas::class.java) } catch (e: Throwable) { null }
                c = c.superclass
            }
            val mm = m ?: run { log(Log.WARN, TAG, "reloj pequeno: sin onDraw propio"); return }
            val applied = java.util.WeakHashMap<android.view.View, String>()
            hook(mm).intercept { chain ->
                val v = chain.thisObject as? android.view.View
                if (v != null && v.javaClass.name.startsWith(base) &&
                    (v.parent?.javaClass?.name ?: "") != base + "ViewGroup" &&
                    (prefs?.getBoolean("lk_on", true) ?: true)) {
                    pushMetrics(v, "compact", prefs)
                    fxApply(v, prefs, applied)
                }
                chain.proceed()
            }
            log(Log.INFO, TAG, "reloj pequeno enganchado en " + mm.declaringClass.name)
        } catch (e: Throwable) { log(Log.WARN, TAG, "hookLockSmall: " + e) }
    }
}
