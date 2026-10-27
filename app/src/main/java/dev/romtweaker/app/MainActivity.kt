package dev.romtweaker.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Ov(val target: String, val pkg: String, var enabled: Boolean)

class MainActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var logView: TextView
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private var glass = false
    private var selColor = "6750A4"
    private var selStyle = "TONAL_SPOT"
    private var ovFilter = ""
    private var ovList: List<Ov> = emptyList()
    private var ovBox: LinearLayout? = null

    private val tabs = listOf("Inicio", "Colores", "Overlays", "Sistema", "Perfiles", "Interfaz")
    private val presets = listOf("6750A4", "B3261E", "E8590C", "F2A900", "2E7D32", "00897B", "0288D1", "1E3A8A", "8E24AA", "D81B60", "5D4037", "607D8B")
    private val styles = listOf("TONAL_SPOT", "VIBRANT", "EXPRESSIVE", "RAINBOW", "FRUIT_SALAD", "MONOCHROMATIC", "SPRITZ")

    private fun uiPrefs() = getSharedPreferences("ui", Context.MODE_PRIVATE)
    private fun backup() = getSharedPreferences("backup", Context.MODE_PRIVATE)
    private fun profDir(): File {
        val d = File(filesDir, "profiles")
        d.mkdirs()
        return d
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        glass = uiPrefs().getBoolean("glass", false)
        ui = Ui(this, makePalette(this, glass))
        val p = ui.p
        window.setBackgroundDrawable(ColorDrawable(p.bg))
        window.isNavigationBarContrastEnforced = false

        val frame = FrameLayout(this)
        if (glass) frame.addView(ui.glassBackdrop(), FrameLayout.LayoutParams(MATCH, MATCH))
        else frame.setBackgroundColor(p.bg)

        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        frame.addView(main, FrameLayout.LayoutParams(MATCH, MATCH))

        val header = ui.row()
        header.setPadding(ui.dp(22), ui.dp(12), ui.dp(16), ui.dp(8))
        val title = TextView(this)
        title.text = "RomTweaker"
        title.textSize = 24f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(p.onSurface)
        title.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        header.addView(title)
        header.addView(ui.chip(if (glass) "Liquid Glass" else "Material You", true) { setStyle(!glass) })
        main.addView(header)

        val logCard = ui.card()
        val llp = logCard.layoutParams as LinearLayout.LayoutParams
        llp.setMargins(ui.dp(16), 0, ui.dp(16), ui.dp(10))
        logCard.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10))
        logView = TextView(this)
        logView.text = "Listo. Toca este cuadro para expandirlo."
        logView.textSize = 12f
        logView.typeface = Typeface.MONOSPACE
        logView.setTextColor(p.onSurface)
        logView.maxLines = 4
        logView.ellipsize = TextUtils.TruncateAt.END
        logView.setOnClickListener { logView.maxLines = if (logView.maxLines == 4) 60 else 4 }
        logCard.addView(logView)
        main.addView(logCard)

        content = FrameLayout(this)
        main.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))

        nav = ui.row()
        nav.background = if (glass) ui.glassBg(32) else ui.round(p.surface, 28)
        nav.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
        val nlp = LinearLayout.LayoutParams(MATCH, WRAP)
        nlp.setMargins(ui.dp(14), ui.dp(4), ui.dp(14), ui.dp(10))
        nav.layoutParams = nlp
        main.addView(nav)

        setContentView(frame)

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            main.setOnApplyWindowInsetsListener { v, ins ->
                val b = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(0, b.top, 0, b.bottom)
                ins
            }
            if (glass) {
                window.insetsController?.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }
        }

        showTab(uiPrefs().getInt("tab", 0))
    }

    override fun onDestroy() {
        ui.stop()
        super.onDestroy()
    }

    private fun setStyle(g: Boolean) {
        if (g != glass) {
            uiPrefs().edit().putBoolean("glass", g).apply()
            recreate()
        }
    }

    private fun say(s: String) {
        logView.text = s
    }

    private fun exec(cmd: String, after: (() -> Unit)? = null) {
        say("Ejecutando…")
        Thread {
            val r = Root.su(cmd)
            runOnUiThread {
                say("[" + r.first + "] " + (if (r.second.isEmpty()) "OK" else r.second))
                after?.invoke()
            }
        }.start()
    }

    private fun showTab(i: Int) {
        uiPrefs().edit().putInt("tab", i).apply()
        nav.removeAllViews()
        for (k in tabs.indices) nav.addView(ui.navItem(tabs[k], k == i) { showTab(k) })
        content.removeAllViews()
        val pg: View = when (i) {
            0 -> pageHome()
            1 -> pageColors()
            2 -> pageOverlays()
            3 -> pageSystem()
            4 -> pageProfiles()
            else -> TweakUi.page(this, ui) { say(it) }
        }
        content.addView(pg, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun page(build: (LinearLayout) -> Unit): ScrollView {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(24))
        build(col)
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(col)
        return sv
    }

    // ---------------- INICIO ----------------
    private fun sysInfo(): String {
        val lin = listOf("ro.lineage.version", "ro.lineage.build.version", "ro.modversion")
            .map { Root.prop(it) }.firstOrNull { it.isNotEmpty() }
        return "ROM: " + (lin ?: "no detectada (usa Sistema → Buscar)") +
            "\nBuild: " + Root.prop("ro.build.display.id") +
            "\nAndroid " + Build.VERSION.RELEASE + " · SDK " + Build.VERSION.SDK_INT +
            "\nDispositivo: " + Build.MANUFACTURER + " " + Build.DEVICE +
            "\nParche: " + Build.VERSION.SECURITY_PATCH
    }

    private fun pageHome(): View = page { col ->
        val c1 = ui.card()
        c1.addView(ui.heading("Dispositivo"))
        val inf = ui.body("Leyendo información…"); c1.addView(inf); Thread { val s = RomInfo.summary(); runOnUiThread { inf.text = s } }.start()
        col.addView(c1)

        val c2 = ui.card()
        c2.addView(ui.heading("Root"))
        c2.addView(ui.body("Comprueba que la app tiene acceso root."))
        c2.addView(ui.button("Comprobar root", true) { exec("id; su -v 2>/dev/null") })
        col.addView(c2); col.addView(Actions.diagCard(this, ui))

        val c3 = ui.card()
        c3.addView(ui.heading("Apariencia de la app"))
        c3.addView(ui.body("Elige el estilo visual."))
        val r = ui.row()
        r.addView(ui.chip("Material You", !glass) { setStyle(false) })
        r.addView(ui.chip("Liquid Glass", glass) { setStyle(true) })
        c3.addView(ui.hscroll(r))
        col.addView(c3)

        val c4 = ui.card()
        c4.addView(ui.heading("Próximamente"))
        c4.addView(ui.body("Overlays con iconos propios, islas dinámicas y reproductor de música."))
        col.addView(c4)
    }

    // ---------------- COLORES ----------------
    private fun hexOf(e: EditText): String {
        val s = e.text.toString().trim().trimStart('#').uppercase()
        return if (s.matches(Regex("[0-9A-F]{6}"))) s else selColor
    }

    private fun swatch(hex: String): View {
        val v = View(this)
        val g = GradientDrawable()
        g.setShape(GradientDrawable.OVAL)
        g.setColor(Color.parseColor("#" + hex))
        g.setStroke(ui.dp(3), if (hex == selColor) ui.p.onSurface else Color.TRANSPARENT)
        v.background = g
        val lp = LinearLayout.LayoutParams(ui.dp(40), ui.dp(40))
        lp.setMargins(0, ui.dp(4), ui.dp(10), ui.dp(8))
        v.layoutParams = lp
        v.setOnClickListener {
            selColor = hex
            showTab(1)
        }
        return v
    }

    private fun themeJson(src: String): String {
        val j = JSONObject()
        if (src == "preset") {
            j.put("android.theme.customization.accent_color", selColor)
            j.put("android.theme.customization.system_palette", selColor)
            j.put("android.theme.customization.theme_style", selStyle)
        }
        j.put("android.theme.customization.color_source", src)
        j.put("_applied_timestamp", System.currentTimeMillis())
        return j.toString()
    }

    private fun pageColors(): View = page { col ->
        val c = ui.card()
        c.addView(ui.heading("Color del sistema"))
        c.addView(ui.body("Cambia la paleta Material You de todo Android: Ajustes, barra de estado y apps."))
        for (start in listOf(0, 6)) {
            val r = ui.row()
            for (j in start until start + 6) r.addView(swatch(presets[j]))
            c.addView(r)
        }
        val hexIn = ui.input("HEX, ej. 3DDC84")
        hexIn.setText(selColor)
        c.addView(hexIn)
        c.addView(ui.body("Estilo de paleta"))
        val sr = ui.row()
        for (s in styles) {
            sr.addView(ui.chip(s.lowercase().replace('_', ' '), s == selStyle) {
                selColor = hexOf(hexIn)
                selStyle = s
                showTab(1)
            })
        }
        c.addView(ui.hscroll(sr))
        c.addView(ui.button("Aplicar color", true) {
            selColor = hexOf(hexIn)
            Actions.color(this, themeJson("preset")) { say(it) }
        })
        c.addView(ui.button("Usar colores del fondo de pantalla") {
            Actions.color(this, themeJson("home_wallpaper")) { say(it) }
        })
        col.addView(c)

        val m = ui.card()
        m.addView(ui.heading("Modo claro / oscuro"))
        val mr = ui.row()
        mr.addView(ui.chip("Claro", false) { Actions.night(this, "no") { say(it) } })
        mr.addView(ui.chip("Oscuro", false) { Actions.night(this, "yes") { say(it) } })
        mr.addView(ui.chip("Automático", false) { Actions.night(this, "auto") { say(it) } })
        m.addView(ui.hscroll(mr))
        col.addView(m)
    }

    // ---------------- OVERLAYS ----------------
    private fun parseOverlays(out: String): List<Ov> {
        val res = ArrayList<Ov>()
        var target = ""
        for (raw in out.lines()) {
            val l = raw.trim()
            if (l.isEmpty()) continue
            if (l.startsWith("[x]")) res.add(Ov(target, l.substring(3).trim(), true))
            else if (l.startsWith("[ ]")) res.add(Ov(target, l.substring(3).trim(), false))
            else if (l.startsWith("---")) continue
            else target = l
        }
        return res.sortedBy { it.pkg }
    }

    private fun loadOverlays(report: Boolean) {
        Thread {
            val r = Root.su("cmd overlay list --user 0").let { if (it.first == 0) it else { val t = Adapt.listOverlays(); if (t.isEmpty()) it else Pair(0, t) } }
            val l = if (r.first == 0) parseOverlays(r.second) else emptyList()
            runOnUiThread {
                ovList = l
                val b = ovBox
                if (b != null) renderOv(b)
                if (report) say(if (r.first == 0) "Overlays encontrados: " + l.size else "[" + r.first + "] " + r.second)
            }
        }.start()
    }

    private fun renderOv(list: LinearLayout) {
        list.removeAllViews()
        if (ovList.isEmpty()) {
            list.addView(ui.body("Pulsa «Actualizar lista» (necesita root)."))
            return
        }
        val f = ovFilter.lowercase()
        var n = 0
        for (o in ovList) {
            if (f.isNotEmpty() && !o.pkg.lowercase().contains(f)) continue
            if (n >= 80) {
                list.addView(ui.body("… hay más, afina la búsqueda."))
                break
            }
            list.addView(ui.toggle(o.pkg, o.enabled) { on -> toggleOv(o, on) })
            n++
        }
        if (n == 0) list.addView(ui.body("Sin resultados."))
    }

    private fun toggleOv(o: Ov, on: Boolean) {
        val pk = Root.q(o.pkg)
        val cmd = if (on) "cmd overlay enable-exclusive --user 0 --category " + pk + " || cmd overlay enable --user 0 " + pk
        else "cmd overlay disable --user 0 " + pk
        Actions.overlay(this, o.pkg, on) { msg -> say(msg); loadOverlays(false) }
    }

    private fun pageOverlays(): View = page { col ->
        val c = ui.card()
        col.addView(ShapeLib.card(this, ui) { say(it) }); c.addView(ui.heading("Overlays de tu ROM"))
        c.addView(ui.body("Formas de iconos, fuentes, barra de navegación y más. Se activan y desactivan con root."))
        val filters = listOf("Todos" to "", "Iconos" to "icon", "Fuentes" to "font", "Navegación" to "nav", "Colores" to "color", "Temas" to "theme")
        val fr = ui.row()
        for (fl in filters) {
            fr.addView(ui.chip(fl.first, ovFilter == fl.second) {
                ovFilter = fl.second
                showTab(2)
            })
        }
        c.addView(ui.hscroll(fr))
        val search = ui.input("Buscar overlay…")
        search.setText(ovFilter)
        c.addView(search)
        c.addView(ui.button("Actualizar lista", true) { loadOverlays(true) })
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        c.addView(list)
        ovBox = list
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                ovFilter = s?.toString()?.trim() ?: ""
                renderOv(list)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
        })
        col.addView(c)
        renderOv(list)
        if (ovList.isEmpty()) loadOverlays(false)
    }

    // ---------------- SISTEMA ----------------
    private fun setAnim(f: Float) { Actions.anim(this, f) { say(it) }; return
        exec(
            "settings put global window_animation_scale " + f +
                "; settings put global transition_animation_scale " + f +
                "; settings put global animator_duration_scale " + f
        )
    }

    private fun propCmd(k: String, v: String): String =
        "(command -v resetprop >/dev/null && resetprop " + Root.q(k) + " " + Root.q(v) + ") || setprop " + Root.q(k) + " " + Root.q(v)

    private fun setProp(k: String, v: String) {
        if (k.isEmpty()) {
            say("Escribe una propiedad")
            return
        }
        if (!backup().contains(k)) backup().edit().putString(k, Root.prop(k)).apply()
        Actions.prop(this, k, v) { say(it) }
    }

    private fun restoreProps() {
        val all = backup().all
        if (all.isEmpty()) {
            say("No hay nada que restaurar")
            return
        }
        val cmd = all.entries.joinToString("; ") { propCmd(it.key, it.value.toString()) }
        backup().edit().clear().apply()
        exec(cmd)
    }

    private fun searchProps(q: String) {
        if (q.isEmpty()) {
            say("Escribe qué buscar")
            return
        }
        say("Buscando…")
        Thread {
            val lines = Root.sh("getprop").lines().filter { it.lowercase().contains(q.lowercase()) }.take(40)
            runOnUiThread { say(if (lines.isEmpty()) "Sin resultados" else lines.joinToString("\n")) }
        }.start()
    }

    private fun pageSystem(): View = page { col ->
        val d = ui.card()
        d.addView(ui.heading("Densidad (DPI)"))
        val cur = ui.body("DPI actual: …")
        d.addView(cur)
        Thread {
            val r = Root.su("wm density").second
            runOnUiThread { cur.text = r.replace("\n", " · ") }
        }.start()
        val dpi = ui.input("DPI, ej. 380")
        d.addView(dpi)
        d.addView(ui.button("Aplicar DPI", true) {
            val n = dpi.text.toString().toIntOrNull()
            if (n != null && n in 120..800) Actions.dpi(this, n) { say(it) } else say("DPI inválido (120-800)")
        })
        d.addView(ui.button("Restablecer DPI") { Actions.dpiReset(this) { say(it) } })
        col.addView(d)

        val a = ui.card()
        a.addView(ui.heading("Animaciones"))
        val ar = ui.row()
        ar.addView(ui.chip("Apagadas", false) { setAnim(0f) })
        ar.addView(ui.chip("0.5×", false) { setAnim(0.5f) })
        ar.addView(ui.chip("1×", false) { setAnim(1f) })
        a.addView(ui.hscroll(ar))
        val an = ui.input("Escala personalizada, ej. 0.75")
        a.addView(an)
        a.addView(ui.button("Aplicar escala", true) {
            val f = an.text.toString().toFloatOrNull()
            if (f != null && f >= 0f && f <= 10f) setAnim(f) else say("Valor inválido")
        })
        col.addView(a)

        val pr = ui.card()
        pr.addView(ui.heading("Propiedades del sistema"))
        pr.addView(ui.body("Con backup automático. Las ro.* necesitan resetprop de tu gestor root."))
        val key = ui.input("propiedad, o texto a buscar")
        val value = ui.input("valor")
        pr.addView(key)
        pr.addView(value)
        pr.addView(ui.button("Leer") {
            val k = key.text.toString().trim()
            say(if (k.isEmpty()) "Escribe una propiedad" else k + " = " + Root.prop(k))
        })
        pr.addView(ui.button("Buscar en todas las propiedades") { searchProps(key.text.toString().trim()) })
        pr.addView(ui.button("Aplicar (con backup)", true) { setProp(key.text.toString().trim(), value.text.toString()) })
        pr.addView(ui.button("Restaurar todo lo cambiado") { restoreProps() })
        col.addView(pr)
    }

    // ---------------- PERFILES ----------------
    private fun safeName(n: String): String = n.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun captureProfile(name: String) {
        say("Guardando perfil…")
        Thread {
            val o = JSONObject()
            o.put("name", name)
            o.put("build", Root.prop("ro.build.display.id"))
            val d = Root.su("wm density").second
            val m = Regex("Override density: (\\d+)").find(d)
            val dpi = m?.groupValues?.get(1)?.toIntOrNull()
            o.put("dpi", dpi ?: JSONObject.NULL)
            val anim = Root.su("settings get global window_animation_scale").second.toFloatOrNull()
            o.put("anim", anim ?: JSONObject.NULL)
            val th = Root.su("settings get secure theme_customization_overlay_packages").second
            o.put("theme", if (th.isEmpty() || th == "null") JSONObject.NULL else th)
            val ovs = JSONArray()
            val lst = Root.su("cmd overlay list --user 0")
            if (lst.first == 0) {
                for (ov in parseOverlays(lst.second)) if (ov.enabled) ovs.put(ov.pkg)
            }
            o.put("overlays", ovs)
            val props = JSONObject()
            for (k in backup().all.keys) props.put(k, Root.prop(k))
            o.put("props", props)
            try {
                File(profDir(), safeName(name) + ".json").writeText(o.toString(2))
                runOnUiThread {
                    say("Perfil «" + name + "» guardado")
                    showTab(4)
                }
            } catch (e: Exception) {
                runOnUiThread { say("Error al guardar: " + e.message) }
            }
        }.start()
    }

    private fun applyProfile(o: JSONObject) {
        val cmds = ArrayList<String>()
        val pr = o.optJSONObject("props")
        if (pr != null) {
            for (k in pr.keys()) {
                if (!backup().contains(k)) backup().edit().putString(k, Root.prop(k)).apply()
                cmds.add(propCmd(k, pr.optString(k)))
            }
        }
        if (o.has("anim") && !o.isNull("anim")) {
            val f = o.optDouble("anim")
            cmds.add(
                "settings put global window_animation_scale " + f +
                    "; settings put global transition_animation_scale " + f +
                    "; settings put global animator_duration_scale " + f
            )
        }
        if (o.has("theme") && !o.isNull("theme")) {
            cmds.add("settings put secure theme_customization_overlay_packages " + Root.q(o.optString("theme")))
        }
        val ovs = o.optJSONArray("overlays")
        if (ovs != null) {
            for (i in 0 until ovs.length()) {
                val pk = Root.q(ovs.optString(i))
                cmds.add("cmd overlay enable-exclusive --user 0 --category " + pk + " >/dev/null 2>&1 || cmd overlay enable --user 0 " + pk + " >/dev/null 2>&1")
            }
        }
        if (o.has("dpi")) {
            cmds.add(if (o.isNull("dpi")) "wm density reset" else "wm density " + o.optInt("dpi"))
        }
        if (cmds.isEmpty()) say("El perfil está vacío") else exec(cmds.joinToString("; "))
    }

    private fun summary(o: JSONObject): String {
        val parts = ArrayList<String>()
        if (o.has("dpi")) parts.add(if (o.isNull("dpi")) "DPI por defecto" else "DPI " + o.optInt("dpi"))
        if (o.has("anim") && !o.isNull("anim")) parts.add("anim " + o.optDouble("anim"))
        if (o.has("theme") && !o.isNull("theme")) parts.add("colores")
        val ov = o.optJSONArray("overlays")
        if (ov != null && ov.length() > 0) parts.add(ov.length().toString() + " overlays")
        val pr = o.optJSONObject("props")
        if (pr != null && pr.length() > 0) parts.add(pr.length().toString() + " props")
        return if (parts.isEmpty()) "Perfil vacío" else parts.joinToString(" · ")
    }

    private fun importProfiles() {
        say("Importando…")
        Thread {
            val ls = Root.su("ls /sdcard/Download/RomTweaker/*.json")
            val files = if (ls.first == 0) ls.second.lines().filter { it.endsWith(".json") } else emptyList()
            var n = 0
            for (f in files) {
                val c = Root.su("cat " + Root.q(f))
                if (c.first == 0) {
                    try {
                        JSONObject(c.second)
                        File(profDir(), File(f).name).writeText(c.second)
                        n++
                    } catch (e: Exception) {
                    }
                }
            }
            runOnUiThread {
                say("Perfiles importados: " + n)
                showTab(4)
            }
        }.start()
    }

    private fun pageProfiles(): View = page { col ->
        val c = ui.card()
        c.addView(ui.heading("Guardar perfil"))
        c.addView(ui.body("Guarda DPI, animaciones, colores, overlays activos y propiedades cambiadas para reaplicarlos o llevarlos a otra ROM."))
        val name = ui.input("Nombre del perfil")
        c.addView(name)
        c.addView(ui.button("Guardar configuración actual", true) {
            val n = name.text.toString().trim()
            if (n.isEmpty()) say("Pon un nombre") else captureProfile(n)
        })
        c.addView(ui.button("Importar desde Descargas/RomTweaker") { importProfiles() })
        col.addView(c)

        val files = profDir().listFiles()?.filter { it.name.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()
        if (files.isEmpty()) col.addView(ui.body("Aún no hay perfiles guardados."))
        for (f in files) {
            val o: JSONObject? = try { JSONObject(f.readText()) } catch (e: Exception) { null }
            val pc = ui.card()
            pc.addView(ui.heading(o?.optString("name", f.nameWithoutExtension) ?: f.nameWithoutExtension))
            pc.addView(ui.body(if (o != null) summary(o) else "Archivo ilegible"))
            val ar = ui.row()
            if (o != null) ar.addView(ui.chip("Aplicar", true) { applyProfile(o) })
            ar.addView(ui.chip("Exportar", false) {
                exec(
                    "mkdir -p /sdcard/Download/RomTweaker && cp " + Root.q(f.absolutePath) +
                        " /sdcard/Download/RomTweaker/ && chmod 666 /sdcard/Download/RomTweaker/" + Root.q(f.name) +
                        " && echo 'Exportado a Descargas/RomTweaker/" + f.name + "'"
                )
            })
            ar.addView(ui.chip("Borrar", false) {
                f.delete()
                showTab(4)
            })
            pc.addView(ui.hscroll(ar))
            col.addView(pc)
        }
    }
}
