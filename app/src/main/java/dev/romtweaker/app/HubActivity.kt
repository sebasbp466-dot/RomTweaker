package dev.romtweaker.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/** Pantalla principal nueva: Material Design 3 + blur. Los ajustes antiguos siguen en MainActivity. */
class HubActivity : Activity() {
    private lateinit var m: M3
    private lateinit var scroll: ScrollView
    private lateinit var nav: LinearLayout
    private lateinit var snack: TextView
    private var tab = 0
    private var items: List<IcoItem> = emptyList()
    private var rootState = "Comprobando…"
    private var compat: List<SysFeature>? = null
    private val ui = Handler(Looper.getMainLooper())
    private val hide = Runnable { snack.visibility = View.GONE }

    private val hubPrefs get() = getSharedPreferences("hub", MODE_PRIVATE)
    private val icoPrefs get() = getSharedPreferences("ico", MODE_PRIVATE)

    private val descs = mapOf(
        "Paquetes de iconos" to "Cambia los iconos del sistema y de la barra de estado (Android + SystemUI).",
        "Iconos de señal" to "Estilo de las barras de cobertura móvil.",
        "Iconos de Wi-Fi" to "Estilo del icono de Wi-Fi.",
        "Fondo de notificaciones" to "Fondo de las tarjetas de notificación y del menú de apagado.",
        "Marco de toasts" to "Forma del marco de los avisos emergentes.",
        "Forma de iconos (Iconify)" to "Máscara de iconos de apps (18 formas de Iconify).",
        "Iconos de Ajustes" to "Iconos de la app Ajustes.",
        "Esquinas de pantalla" to "Radio de esquinas de ventanas y paneles.",
        "Chip de privacidad" to "Forma del indicador de cámara/micrófono.",
        "Botones y diálogos" to "Botones y diálogos del panel rápido."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        m = M3(this, hubPrefs.getBoolean("glass", false))
        items = IcoCat.load(this)
        tab = savedInstanceState?.getInt("tab") ?: 0
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = FrameLayout(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        scroll = ScrollView(this)
        scroll.clipToPadding = false
        col.addView(scroll, LinearLayout.LayoutParams(m.MP, 0, 1f))
        nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.background = m.shape(m.a(m.surfaceCont, if (m.glass) 0.6f else 0.95f), 0)
        col.addView(nav, LinearLayout.LayoutParams(m.MP, m.WC))
        root.addView(col, FrameLayout.LayoutParams(m.MP, m.MP))

        snack = m.tv("", 14f, m.onPrimary, true)
        snack.setPadding(m.dp(18), m.dp(12), m.dp(18), m.dp(12))
        snack.background = m.shape(m.a(m.primary, 0.96f), 16)
        snack.visibility = View.GONE
        val sl = FrameLayout.LayoutParams(m.WC, m.WC)
        sl.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        sl.setMargins(m.dp(16), 0, m.dp(16), m.dp(100))
        root.addView(snack, sl)
        setContentView(root)

        root.setOnApplyWindowInsetsListener { v, ins ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = ins.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(0, b.top, 0, b.bottom)
            }
            ins
        }
        if (Build.VERSION.SDK_INT >= 30 && !m.dark) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(mask, mask)
        }
        applyBlur()
        show(tab)
        Thread {
            val r = Sx.su("id -u")
            rootState = if (r.second.trim() == "0") "Concedido ✓" else "No disponible (${r.second.take(50)})"
            runOnUiThread { if (tab == 0) refresh() }
        }.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    override fun onResume() {
        super.onResume()
        applyBlur()
        if (tab == 3 && ::scroll.isInitialized) refresh()
    }

    private fun applyBlur() {
        val r = hubPrefs.getInt("blur", 90)
        var on = false
        if (Build.VERSION.SDK_INT >= 31) {
            on = try { windowManager.isCrossWindowBlurEnabled && r > 0 } catch (e: Throwable) { false }
        }
        val alpha = if (on) (if (m.glass) 0.45f else 0.74f) else 1f
        window.setBackgroundDrawable(ColorDrawable(m.a(m.surface, alpha)))
        if (Build.VERSION.SDK_INT >= 31) window.setBackgroundBlurRadius(if (on) r else 0)
    }

    private fun say(s: String) {
        snack.text = s
        snack.visibility = View.VISIBLE
        ui.removeCallbacks(hide)
        ui.postDelayed(hide, 6500)
    }

    private fun renderNav() {
        nav.removeAllViews()
        val names = listOf("Inicio", "Iconify", "Xposed", "Bloqueo", "Más")
        val icons = listOf("⌂", "✦", "⚡", "◐", "☰")
        for (i in names.indices) {
            val it2 = m.navItem(icons[i], names[i], i == tab) { show(i) }
            nav.addView(it2, LinearLayout.LayoutParams(0, m.WC, 1f))
        }
    }

    private fun show(t: Int) {
        tab = t
        scroll.removeAllViews()
        val v = when (t) {
            0 -> pageHome()
            1 -> pageIconify()
            2 -> pageXposed()
            3 -> LockStudio.page(this, m, { msg -> say(msg) }, { refresh() })
            else -> pageMore()
        }
        scroll.addView(v)
        scroll.scrollTo(0, 0)
        renderNav()
    }

    private fun refresh() {
        val y = scroll.scrollY
        show(tab)
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun page(title: String, sub: String): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.setPadding(m.dp(16), m.dp(36), m.dp(16), m.dp(24))
        p.addView(m.tv(title, 34f, m.onSurface), m.lp(m.MP, m.WC, 8, 0, 8, 2))
        p.addView(m.tv(sub, 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 8, 0, 8, 18))
        return p
    }

    private fun add(p: LinearLayout, c: View) {
        p.addView(c, m.lp(m.MP, m.WC, 0, 0, 0, 12))
    }

    private fun line(c: LinearLayout, k: String, v: String) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.addView(m.tv(k, 14f, m.onSurfaceVar), LinearLayout.LayoutParams(m.dp(110), m.WC))
        r.addView(m.tv(v, 14f, m.onSurface, true), LinearLayout.LayoutParams(0, m.WC, 1f))
        c.addView(r, m.lp(m.MP, m.WC, 0, 4, 0, 0))
    }

    // ---------- Inicio ----------
    private fun pageHome(): View {
        val p = page("RomTweaker", "Personalización root para cualquier ROM")
        val c = m.card()
        c.addView(m.tv("Tu dispositivo", 18f, m.onSurface, true), m.lp(m.MP, m.WC, 0, 0, 0, 6))
        line(c, "Equipo", "${Build.MANUFACTURER} ${Build.MODEL}")
        line(c, "Sistema", Build.DISPLAY)
        line(c, "Android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        line(c, "Root", rootState)
        line(c, "LSPosed", if (Xp.included()) Xp.status() else "Módulo no incluido")
        add(p, c)

        val c2 = m.card()
        c2.addView(m.tv("Iconify portado", 18f, m.onSurface, true))
        val groups = HashSet<String>()
        for (i in items) groups.add(i.group)
        c2.addView(m.tv("${items.size} estilos en ${groups.size} categorías, compilados desde el código abierto de Iconify (GPL-3.0). Iconify apunta a AOSP/Pixel: si un estilo no aparece en tu ROM, usa «Original» para volver.", 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 4, 0, 12))
        c2.addView(m.filled("Abrir estilos de Iconify") { show(1) })
        add(p, c2)

        val c3 = m.card()
        c3.addView(m.tv("Ajustes del sistema", 18f, m.onSurface, true))
        c3.addView(m.tv("Colores, perfiles, formas de iconos, Centro de control propio, explorador de recursos, importación de overlays y diagnóstico.", 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 4, 0, 12))
        c3.addView(m.tonal("Abrir ajustes del sistema") { hubPrefs.edit().putInt("ico_sec", 1).apply(); show(1) })
        add(p, c3)
        return p
    }

    // ---------- Iconify ----------
    private fun applyItem(item: IcoItem) {
        say("Aplicando ${item.group} · ${item.label}…")
        Thread {
            val r = IcoOps.apply(this, item, items)
            if (r.contains("✓") || r.contains("módulo")) icoPrefs.edit().putString(item.group, item.label).apply()
            runOnUiThread { say(r); refresh() }
        }.start()
    }

    private fun clearGroup(g: String) {
        say("Restaurando $g…")
        Thread {
            val r = IcoOps.clear(g, items)
            icoPrefs.edit().remove(g).apply()
            runOnUiThread { say(r); refresh() }
        }.start()
    }

    private fun pageIconify(): View {
        val p = page("Iconify", "${items.size} estilos portados · código de Mahmud0808/Iconify")
        val sec = hubPrefs.getInt("ico_sec", 0)
        val seg = RtFlow(this, m.dp(8))
        seg.addView(m.chip("Estilos Iconify", sec == 0) { hubPrefs.edit().putInt("ico_sec", 0).apply(); show(1) })
        seg.addView(m.chip("Sistema y ajustes", sec == 1) { hubPrefs.edit().putInt("ico_sec", 1).apply(); show(1) })
        p.addView(seg, m.lp(m.MP, m.WC, 0, 0, 0, 14))
        if (sec == 1) {
            for (v in ClassicHost.sections(this, m) { msg -> say(msg) }) add(p, v)
            return p
        }
        if (items.isEmpty()) {
            val c = m.card()
            c.addView(m.tv("Esta compilación no incluye overlays de Iconify (falló su compilación en GitHub Actions). Vuelve a ejecutar el comando de actualización.", 14f, m.onSurfaceVar))
            add(p, c)
            return p
        }
        val groups = LinkedHashMap<String, MutableList<IcoItem>>()
        for (i in items) groups.getOrPut(i.group) { ArrayList() }.add(i)
        for (entry in groups.entries) {
            val g = entry.key
            val cur = icoPrefs.getString(g, "") ?: ""
            val c = m.card()
            c.addView(m.tv(g, 20f, m.onSurface, true))
            val d = descs[g]
            if (d != null) c.addView(m.tv(d, 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 2, 0, 10))
            val flow = RtFlow(this, m.dp(8))
            flow.addView(m.chip("Original", cur.isEmpty()) { clearGroup(g) })
            for (it2 in entry.value) {
                val item = it2
                flow.addView(IcoPreview.tile(this, m, item, cur == item.label) { applyItem(item) })
            }
            c.addView(flow, m.lp(m.MP, m.WC))
            add(p, c)
        }
        return p
    }

    // ---------- Xposed ----------
    private fun saveClock(fmt: String, on: Boolean) {
        hubPrefs.edit().putString("clock_fmt", fmt).putBoolean("clock_on", on).apply()
        val ok = Xp.putString("clock_fmt", fmt) && Xp.putBool("clock_on", on)
        say(if (ok) "Enviado a SystemUI" else "Guardado; conecta LSPosed para aplicarlo")
    }

    private fun sampleTime(pat: String): String =
        try { android.text.format.DateFormat.format(pat, System.currentTimeMillis()).toString() } catch (e: Throwable) { "patrón inválido" }

    private fun pageXposed(): View {
        val p = page("Xposed / LSPosed", "Hooks dentro de SystemUI, según lo que tenga tu ROM")
        val c = m.card()
        c.addView(m.tv("Estado", 18f, m.onSurface, true))
        c.addView(m.tv(Xp.status(), 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 4, 0, 10))
        val row = LinearLayout(this)
        row.addView(m.tonal("Abrir LSPosed") {
            val i = packageManager.getLaunchIntentForPackage("org.lsposed.manager")
            if (i != null) startActivity(i) else say("Abre LSPosed desde su notificación")
        })
        row.addView(m.tonal("Reiniciar SystemUI") {
            Thread { Sx.su("killall com.android.systemui"); runOnUiThread { say("SystemUI reiniciado") } }.start()
        }, m.lp(m.WC, m.WC, 8, 0, 0, 0))
        c.addView(row)
        add(p, c)

        val cc = m.card()
        cc.addView(m.tv("Compatibilidad con tu ROM", 18f, m.onSurface, true))
        val cs = compat
        if (cs == null) {
            cc.addView(m.tv("Analizando SystemUI…", 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 4, 0, 0))
            Thread {
                val r = SysScan.features(this)
                compat = r
                runOnUiThread { if (tab == 2) refresh() }
            }.start()
        } else {
            for (f in cs) {
                val t = if (f.found) "✓ " + f.label + " · " + f.support else "✗ " + f.label + " · no existe en esta ROM"
                cc.addView(m.tv(t, 13f, if (f.found) m.onSurface else m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 6, 0, 0))
            }
        }
        add(p, cc)

        val c2 = m.card()
        c2.addView(m.tv("Reloj de la barra de estado", 18f, m.onSurface, true))
        c2.addView(m.tv("Cada estilo muestra la hora real. Toca uno o escribe tu propio patrón.", 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 2, 0, 10))
        val cur = hubPrefs.getString("clock_fmt", "HH:mm") ?: "HH:mm"
        val f = m.field("HH:mm:ss", cur)
        val pv = m.tv(sampleTime(cur), 26f, m.onSurface, true)
        val flow = RtFlow(this, m.dp(8))
        val presets = listOf("HH:mm", "HH:mm:ss", "h:mm a", "EEE HH:mm", "d MMM HH:mm", "EEE d MMM · HH:mm")
        for (pt in presets) {
            val pat = pt
            flow.addView(m.chip(sampleTime(pat), cur == pat) {
                f.setText(pat)
                saveClock(pat, true)
            })
        }
        c2.addView(flow, m.lp(m.MP, m.WC, 0, 0, 0, 12))
        f.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { pv.text = sampleTime(s.toString()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        c2.addView(f, m.lp(m.MP, m.WC, 0, 0, 0, 8))
        c2.addView(pv, m.lp(m.MP, m.WC, 0, 0, 0, 10))
        c2.addView(m.switchRow("Usar formato propio", "", hubPrefs.getBoolean("clock_on", false)) { v ->
            saveClock(f.text.toString(), v)
        })
        c2.addView(m.filled("Guardar y aplicar") { saveClock(f.text.toString(), true) }, m.lp(m.WC, m.WC, 0, 10, 0, 0))
        add(p, c2)

        val c3 = m.card()
        c3.addView(m.tv("Cómo activarlo", 18f, m.onSurface, true))
        c3.addView(m.tv("1. LSPosed → Módulos → RomTweaker → activar.\n2. Marca «Interfaz del sistema» en el ámbito.\n3. Reinicia SystemUI.\n\nEl reloj de bloqueo y la cabecera de tu ROM usan un sistema distinto al que Iconify enganchaba; los marcados «en preparación» se activarán cuando estén probados en tu versión.", 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 4, 0, 0))
        add(p, c3)
        return p
    }

    // ---------- Más ----------
    private fun pageMore(): View {
        val p = page("Más", "Apariencia, créditos y mantenimiento")
        val c = m.card()
        c.addView(m.tv("Apariencia", 18f, m.onSurface, true), m.lp(m.MP, m.WC, 0, 0, 0, 8))
        c.addView(m.switchRow("Estilo cristal", "Superficies más transparentes sobre el blur", hubPrefs.getBoolean("glass", false)) { v ->
            hubPrefs.edit().putBoolean("glass", v).apply()
            recreate()
        })
        c.addView(m.tv("Intensidad del blur", 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 14, 0, 0))
        val sb = SeekBar(this)
        sb.max = 150
        sb.progress = hubPrefs.getInt("blur", 90)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                if (fromUser) { hubPrefs.edit().putInt("blur", v).apply(); applyBlur() }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        c.addView(sb, m.lp(m.MP, m.WC))
        val ok = Build.VERSION.SDK_INT >= 31 && try { windowManager.isCrossWindowBlurEnabled } catch (e: Throwable) { false }
        c.addView(m.tv(if (ok) "Blur de ventana activo" else "Tu sistema tiene el blur desactivado (ahorro de batería o ajuste del desarrollador); se usa fondo sólido.", 12f, m.onSurfaceVar))
        add(p, c)

        val c2 = m.card()
        c2.addView(m.tv("Mantenimiento", 18f, m.onSurface, true), m.lp(m.MP, m.WC, 0, 0, 0, 8))
        c2.addView(m.tonal("Desactivar todos los overlays de Iconify") {
            say("Desactivando…")
            Thread {
                val r = IcoOps.clearAll(items)
                icoPrefs.edit().clear().apply()
                runOnUiThread { say(r); refresh() }
            }.start()
        })
        add(p, c2)

        val c3 = m.card()
        c3.addView(m.tv("Créditos y licencia", 18f, m.onSurface, true))
        c3.addView(m.tv("Los estilos de la pestaña Iconify provienen de Iconify (Mahmud0808, GPL-3.0), que a su vez usa recursos de AOSPMods, Substratum, icons8 e iconsax. Por eso RomTweaker se distribuye bajo GPL-3.0. Código del módulo LSPosed: libxposed (Apache-2.0).", 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 4, 0, 0))
        add(p, c3)
        return p
    }
}
