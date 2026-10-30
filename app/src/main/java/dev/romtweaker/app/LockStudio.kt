package dev.romtweaker.app

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** Estudio de personalización de la pantalla de bloqueo (relojes estilo Apple Liquid Glass y HyperOS). */
object LockStudio {
    private val palette = listOf(
        0xFFFFFFFF.toInt(), 0xFFFF7A3D.toInt(), 0xFF4DA3FF.toInt(), 0xFFFF5C9E.toInt(),
        0xFF4ADE80.toInt(), 0xFFFFD60A.toInt(), 0xFFB388FF.toInt(), 0xFF111111.toInt()
    )

    private fun slider(act: Activity, m: M3, label: String, min: Int, max: Int, v: Int, live: (Int) -> Unit, done: () -> Unit): LinearLayout {
        val l = LinearLayout(act)
        l.orientation = LinearLayout.VERTICAL
        val t = m.tv("$label: $v", 14f, m.onSurfaceVar)
        l.addView(t, m.lp(m.MP, m.WC, 0, 10, 0, 0))
        val sb = SeekBar(act)
        sb.max = max - min
        sb.progress = v - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, pr: Int, fromUser: Boolean) {
                val x = pr + min
                t.text = "$label: $x"
                if (fromUser) live(x)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { done() }
        })
        l.addView(sb, m.lp(m.MP, m.WC))
        return l
    }

    private fun swatch(act: Activity, m: M3, col: Int, sel: Boolean, click: () -> Unit): View {
        val v = View(act)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(col)
        g.setStroke(m.dp(if (sel) 3 else 1), if (sel) m.primary else m.a(m.outline, 0.7f))
        v.background = g
        v.setOnClickListener { click() }
        return v
    }

    fun wallpaper(act: Activity): Drawable? =
        try { WallpaperManager.getInstance(act).drawable } catch (e: Throwable) { null }

    fun page(act: Activity, m: M3, say: (String) -> Unit, refresh: () -> Unit): View {
        val cfg = ClockCfg.load(act)
        val lp = act.getSharedPreferences("lock", Activity.MODE_PRIVATE)
        val p = LinearLayout(act)
        p.orientation = LinearLayout.VERTICAL
        p.setPadding(m.dp(16), m.dp(36), m.dp(16), m.dp(24))
        p.addView(m.tv("Pantalla de bloqueo", 34f, m.onSurface), m.lp(m.MP, m.WC, 8, 0, 8, 2))
        p.addView(m.tv("Relojes Liquid Glass y HyperOS con vista previa en vivo", 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 8, 0, 8, 14))

        // ---- vista previa ----
        val dm = act.resources.displayMetrics
        val pw = (dm.widthPixels * 0.62f).toInt()
        val ph = (pw * 2.05f).toInt()
        val scale = pw.toFloat() / dm.widthPixels.toFloat()
        val box = FrameLayout(act)
        val wd = wallpaper(act)
        if (wd != null) {
            val iv = ImageView(act)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageDrawable(wd)
            box.addView(iv, FrameLayout.LayoutParams(-1, -1))
        } else {
            val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF3B1E6B.toInt(), 0xFFB5476B.toInt(), 0xFFFF9A5A.toInt()))
            box.background = g
        }
        val dim = View(act)
        dim.setBackgroundColor(0x33000000)
        box.addView(dim, FrameLayout.LayoutParams(-1, -1))
        val face = ClockFace(act)
        face.cfg = cfg
        face.scale = scale
        val fl = FrameLayout.LayoutParams(-1, -2)
        fun place() {
            fl.gravity = if (cfg.pos == 0) Gravity.TOP else if (cfg.pos == 2) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
            fl.topMargin = if (cfg.pos == 0) (ph * 0.10f).toInt() else 0
            fl.bottomMargin = if (cfg.pos == 2) (ph * 0.16f).toInt() else 0
            face.layoutParams = fl
        }
        place()
        box.addView(face)
        val hint = m.tv("Desliza para desbloquear", 9f, Color.WHITE)
        hint.alpha = 0.8f
        val hl = FrameLayout.LayoutParams(-2, -2)
        hl.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        hl.bottomMargin = m.dp(14)
        box.addView(hint, hl)
        box.clipToOutline = true
        box.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, m.dp(28).toFloat()) }
        }
        val bl = LinearLayout.LayoutParams(pw, ph)
        bl.gravity = Gravity.CENTER_HORIZONTAL
        bl.bottomMargin = m.dp(16)
        p.addView(box, bl)

        fun live() { place(); face.requestLayout(); face.invalidate() }
        fun commit() { cfg.save(act); if (lp.getBoolean("lk_native", false)) LockXp.apply(cfg, true); refresh() }

        // ---- presets ----
        val cp = m.card()
        cp.addView(m.tv("Presets", 18f, m.onSurface, true), m.lp(m.MP, m.WC, 0, 0, 0, 10))
        val fp = RtFlow(act, m.dp(8))
        fun preset(name: String, st: String, col: Int, acc: Int, g: Int, fi: Int, w: Int) {
            fp.addView(m.chip(name, false) { cfg.style = st; cfg.color = col; cfg.accent = acc; cfg.glass = g; cfg.filter = fi; cfg.weight = w; commit() })
        }
        preset("Cristal", "glass", 0xFFFFFFFF.toInt(), 0xFFFF7A3D.toInt(), 85, 0, 600)
        preset("Sólido", "hy_digital", 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 80, 0, 700)
        preset("Rombo", "hy_stack", 0xFFFFFFFF.toInt(), 0xFFFF7A3D.toInt(), 80, 1, 800)
        preset("Revista", "hy_outline", 0xFFFFFFFF.toInt(), 0xFFFFD60A.toInt(), 80, 0, 800)
        preset("Neón", "hy_dual", 0xFF4ADE80.toInt(), 0xFFB388FF.toInt(), 80, 3, 600)
        preset("Rubor", "glass_stack", 0xFFFF5C9E.toInt(), 0xFFFFD60A.toInt(), 70, 2, 500)
        cp.addView(fp, m.lp(m.MP, m.WC))
        p.addView(cp, m.lp(m.MP, m.WC, 0, 0, 0, 12))

        // ---- estilos ----
        val cs = m.card()
        cs.addView(m.tv("Estilo del reloj", 18f, m.onSurface, true), m.lp(m.MP, m.WC, 0, 0, 0, 10))
        val flow = RtFlow(act, m.dp(10))
        for (st in ClockStyles.list) {
            val id = st.first
            val t = LinearLayout(act)
            t.orientation = LinearLayout.VERTICAL
            t.gravity = Gravity.CENTER_HORIZONTAL
            t.setPadding(m.dp(8), m.dp(8), m.dp(8), m.dp(6))
            val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF2A1B4A.toInt(), 0xFF8A3E5E.toInt()))
            bg.cornerRadius = m.dp(16).toFloat()
            if (cfg.style == id) bg.setStroke(m.dp(3), m.primary)
            t.background = bg
            val mini = ClockFace(act)
            val mc = cfg.copy()
            mc.style = id
            mc.date = false
            mc.size = 90
            mini.cfg = mc
            mini.scale = 0.30f
            t.addView(mini, LinearLayout.LayoutParams(m.dp(104), -2))
            t.addView(m.tv(st.second, 11f, Color.WHITE, true), m.lp(m.WC, m.WC, 0, 2, 0, 0))
            t.setOnClickListener { cfg.style = id; commit() }
            flow.addView(t)
        }
        cs.addView(flow, m.lp(m.MP, m.WC))
        p.addView(cs, m.lp(m.MP, m.WC, 0, 0, 0, 12))

        // ---- ajustes ----
        val ca = m.card()
        ca.addView(m.tv("Ajustes", 18f, m.onSurface, true))
        ca.addView(slider(act, m, "Grosor", 100, 900, cfg.weight, { cfg.weight = it; live() }, { commit() }))
        ca.addView(slider(act, m, "Tamaño", 56, 150, cfg.size, { cfg.size = it; live() }, { commit() }))
        ca.addView(slider(act, m, "Transparencia de cristal", 0, 100, cfg.glass, { cfg.glass = it; live() }, { commit() }))
        ca.addView(m.tv("Color del reloj", 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 14, 0, 6))
        val f1 = RtFlow(act, m.dp(8))
        val pal1 = ArrayList<Int>(palette); pal1.add(m.primary)
        for (col in pal1) { val cc = col; f1.addView(swatch(act, m, cc, cfg.color == cc) { cfg.color = cc; commit() }, ViewGroup.LayoutParams(m.dp(38), m.dp(38))) }
        ca.addView(f1, m.lp(m.MP, m.WC))
        ca.addView(m.tv("Color de acento (HyperOS)", 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 14, 0, 6))
        val f2 = RtFlow(act, m.dp(8))
        for (col in pal1) { val cc = col; f2.addView(swatch(act, m, cc, cfg.accent == cc) { cfg.accent = cc; commit() }, ViewGroup.LayoutParams(m.dp(38), m.dp(38))) }
        ca.addView(f2, m.lp(m.MP, m.WC))
        ca.addView(m.tv("Posición", 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 14, 0, 6))
        val f3 = RtFlow(act, m.dp(8))
        val posNames = listOf("Arriba", "Centro", "Abajo")
        for (i in posNames.indices) { val pi = i; f3.addView(m.chip(posNames[i], cfg.pos == pi) { cfg.pos = pi; commit() }) }
        ca.addView(f3, m.lp(m.MP, m.WC))
        ca.addView(m.tv("Filtro del cristal (se ve en el reloj real)", 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 14, 0, 6))
        val f4 = RtFlow(act, m.dp(8))
        val fnames = listOf("Ninguno", "Acanalado", "Estriado", "Ondas")
        for (i in fnames.indices) { val fi = i; f4.addView(m.chip(fnames[i], cfg.filter == fi) { cfg.filter = fi; commit() }) }
        ca.addView(f4, m.lp(m.MP, m.WC))
        ca.addView(m.switchRow("Mostrar fecha", "", cfg.date) { v -> cfg.date = v; commit() }, m.lp(m.MP, m.WC, 0, 12, 0, 0))
        ca.addView(m.switchRow("Formato 24 horas", "", cfg.h24) { v -> cfg.h24 = v; commit() }, m.lp(m.MP, m.WC, 0, 8, 0, 0))
        ca.addView(m.switchRow("Mostrar segundos", "", cfg.seconds) { v -> cfg.seconds = v; commit() }, m.lp(m.MP, m.WC, 0, 8, 0, 0))
        p.addView(ca, m.lp(m.MP, m.WC, 0, 0, 0, 12))

        // ---- reloj nativo (Xposed) ----
        val cr = m.card()
        cr.addView(m.tv("Aplicar al reloj real del sistema", 18f, m.onSurface, true))
        cr.addView(m.tv("Modifica el reloj nativo de tu pantalla de bloqueo con el módulo LSPosed (sin pantalla simulada). Requiere activar RomTweaker en LSPosed con ámbito «Interfaz del sistema».", 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 4, 0, 8))
        cr.addView(m.switchRow("Aplicar a la pantalla de bloqueo real", "", lp.getBoolean("lk_native", false)) { v ->
            lp.edit().putBoolean("lk_native", v).apply()
            val ok = LockXp.apply(cfg, v)
            say(if (ok) (if (v) "Aplicado. Pulsa «Reiniciar SystemUI»" else "Desactivado") else "El módulo no está activo en LSPosed")
        })
        cr.addView(m.tonal("Reiniciar SystemUI") {
            LockXp.apply(cfg, lp.getBoolean("lk_native", false))
            say("Reiniciando SystemUI…")
            Thread { Sx.su("killall com.android.systemui") }.start()
        }, m.lp(m.WC, m.WC, 0, 10, 0, 0))
        p.addView(cr, m.lp(m.MP, m.WC, 0, 0, 0, 12))

        // ---- pantalla de bloqueo propia ----
        val cl = m.card()
        cl.addView(m.tv("Pantalla de bloqueo propia (experimental)", 18f, m.onSurface, true))
        cl.addView(m.tv("Se muestra encima de la pantalla de bloqueo del sistema al encender la pantalla. Desliza hacia arriba para desbloquear: el sistema pedirá tu PIN, huella o patrón como siempre.", 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 4, 0, 8))
        cl.addView(m.switchRow("Activar mi pantalla de bloqueo", "", lp.getBoolean("lock_on", false)) { v ->
            lp.edit().putBoolean("lock_on", v).apply()
            say(if (v) "Activando…" else "Desactivado")
            Thread {
                if (v) {
                    Sx.su("appops set ${act.packageName} SYSTEM_ALERT_WINDOW allow; pm grant ${act.packageName} android.permission.POST_NOTIFICATIONS; cmd deviceidle whitelist +${act.packageName}")
                    act.runOnUiThread {
                        try { act.startForegroundService(Intent(act, LockService::class.java)); say("Activa. Apaga y enciende la pantalla para verla") } catch (e: Throwable) { say("No pude iniciar el servicio: ${e.message}") }
                    }
                } else {
                    act.runOnUiThread { act.stopService(Intent(act, LockService::class.java)) }
                }
            }.start()
        })
        cl.addView(m.tonal("Probar ahora") { act.startActivity(Intent(act, LockActivity::class.java)) }, m.lp(m.WC, m.WC, 0, 10, 0, 0))
        p.addView(cl, m.lp(m.MP, m.WC, 0, 0, 0, 12))
        return p
    }
}
