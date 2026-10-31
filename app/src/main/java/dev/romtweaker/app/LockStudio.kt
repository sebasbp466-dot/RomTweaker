package dev.romtweaker.app

import android.app.Activity
import android.app.WallpaperManager
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** Editor de la pantalla de bloqueo real: vista previa fiel + ajustes que se aplican al reloj del sistema. */
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
        val v = object : View(act) {
            override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(m.dp(38), m.dp(38)) }
        }
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
        if (cfg.style != "glass" && cfg.style != "color" && cfg.style != "solid") {
            cfg.style = if (cfg.style.startsWith("glass")) "glass" else "color"
        }
        val lp = act.getSharedPreferences("lock", Activity.MODE_PRIVATE)
        val compact = lp.getBoolean("pv_compact", false)
        val p = LinearLayout(act)
        p.orientation = LinearLayout.VERTICAL
        p.setPadding(m.dp(16), m.dp(36), m.dp(16), m.dp(24))
        p.addView(m.tv("Pantalla de bloqueo", 34f, m.onSurface), m.lp(m.MP, m.WC, 8, 0, 8, 2))
        p.addView(m.tv("Vista previa fiel: misma fuente y mismos efectos que el reloj real", 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 8, 0, 8, 14))

        // ---- vista previa ----
        val dm = act.resources.displayMetrics
        val pw = (dm.widthPixels * 0.62f).toInt()
        val mt = ClockMetrics.load(act, compact)?.takeIf { it.gh < it.sh * 0.75f && it.gw > it.sw * 0.15f && it.gw <= it.sw }
        val ph = (pw * (if (mt != null) mt.sh.toFloat() / mt.sw else 2.16f)).toInt()
        val box = FrameLayout(act)
        val wd = wallpaper(act)
        if (wd != null) {
            val iv = ImageView(act)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageDrawable(wd)
            box.addView(iv, FrameLayout.LayoutParams(-1, -1))
        } else {
            box.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF0F3B3B.toInt(), 0xFF1B6E6E.toInt(), 0xFF15262B.toInt()))
        }
        val dim = View(act)
        dim.setBackgroundColor(0x22000000)
        box.addView(dim, FrameLayout.LayoutParams(-1, -1))
        val face = FlexPreview(act)
        face.cfg = cfg
        face.compact = compact
        if (mt != null) {
            val sc = pw.toFloat() / mt.sw
            face.metrics = mt
            face.scale = sc
            val fl = FrameLayout.LayoutParams((mt.gw * sc).toInt(), (mt.gh * sc).toInt())
            fl.leftMargin = (mt.gx * sc).toInt()
            fl.topMargin = (mt.gy * sc).toInt()
            box.addView(face, fl)
        } else {
            box.addView(face, FrameLayout.LayoutParams(-1, -1))
        }
        val date = TextView(act)
        date.setTextColor(Color.WHITE)
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, ph * 0.0187f)
        date.text = android.text.format.DateFormat.format("EEE, d MMM", java.util.Calendar.getInstance()).toString()
        val dl = FrameLayout.LayoutParams(-2, -2)
        dl.leftMargin = (pw * 0.083f).toInt()
        dl.topMargin = (ph * (if (compact) 0.205f else 0.072f)).toInt()
        box.addView(date, dl)
        box.clipToOutline = true
        box.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, m.dp(28).toFloat()) }
        }
        val bl = LinearLayout.LayoutParams(pw, ph)
        bl.gravity = Gravity.CENTER_HORIZONTAL
        bl.bottomMargin = m.dp(10)
        p.addView(box, bl)

        fun live() { face.cfg = cfg; face.refreshFx() }
        fun commit() { cfg.save(act); if (lp.getBoolean("lk_native", true)) LockXp.apply(cfg, true); if (lp.getBoolean("lk_live", false)) { Thread { Sx.su("input keyevent 223; sleep 1; input keyevent 224") }.start() }; refresh() }

        val fm = RtFlow(act, m.dp(8))
        fm.addView(m.chip("Pantalla completa", !compact) { lp.edit().putBoolean("pv_compact", false).apply(); refresh() })
        fm.addView(m.chip("Con notificaciones", compact) { lp.edit().putBoolean("pv_compact", true).apply(); refresh() })
        p.addView(fm, m.lp(m.MP, m.WC, 4, 0, 4, 6))
        p.addView(m.tv("Fuente de la vista previa: " + face.fontName, 11f, m.onSurfaceVar), m.lp(m.MP, m.WC, 8, 0, 8, 12))

        // ---- presets ----
        val cp = m.card()
        cp.addView(m.tv("Presets", 18f, m.onSurface, true), m.lp(m.MP, m.WC, 0, 0, 0, 10))
        val fp = RtFlow(act, m.dp(8))
        fun preset(name: String, st: String, col: Int, acc: Int, g: Int, fi: Int, w: Int, wd2: Int, ro: Int, sl: Int) {
            fp.addView(m.chip(name, false) {
                cfg.style = st; cfg.color = col; cfg.accent = acc; cfg.glass = g; cfg.filter = fi
                cfg.weight = w; cfg.wdth = wd2; cfg.rond = ro; cfg.slnt = sl
                commit()
            })
        }
        preset("Original", "solid", 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 80, 0, 400, 85, 0, 0)
        preset("Cristal", "glass", 0xFFFFFFFF.toInt(), 0xFFFF7A3D.toInt(), 85, 0, 500, 90, 0, 0)
        preset("Cristal fino", "glass", 0xFFFFFFFF.toInt(), 0xFFFF7A3D.toInt(), 90, 0, 100, 100, 0, 0)
        preset("Rombo", "color", 0xFFFFFFFF.toInt(), 0xFFFF7A3D.toInt(), 80, 1, 800, 120, 0, 0)
        preset("Revista", "color", 0xFFFFFFFF.toInt(), 0xFFFFD60A.toInt(), 80, 0, 900, 150, 0, 0)
        preset("Neón", "color", 0xFF4ADE80.toInt(), 0xFFB388FF.toInt(), 80, 3, 300, 100, 100, 0)
        preset("Rubor", "glass", 0xFFFF5C9E.toInt(), 0xFFFFD60A.toInt(), 70, 2, 500, 95, 100, -8)
        cp.addView(fp, m.lp(m.MP, m.WC))
        p.addView(cp, m.lp(m.MP, m.WC, 0, 0, 0, 12))

        // ---- relleno ----
        val cs = m.card()
        cs.addView(m.tv("Relleno", 18f, m.onSurface, true), m.lp(m.MP, m.WC, 0, 0, 0, 10))
        val fs = RtFlow(act, m.dp(8))
        val styles = listOf(Pair("glass", "Cristal"), Pair("color", "Degradado"), Pair("solid", "Sólido"))
        for (st in styles) { val id = st.first; fs.addView(m.chip(st.second, cfg.style == id) { cfg.style = id; commit() }) }
        cs.addView(fs, m.lp(m.MP, m.WC))
        cs.addView(m.tv("Filtro", 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 14, 0, 6))
        val f4 = RtFlow(act, m.dp(8))
        val fnames = listOf("Ninguno", "Acanalado", "Estriado", "Ondas")
        for (i in fnames.indices) { val fi = i; f4.addView(m.chip(fnames[i], cfg.filter == fi) { cfg.filter = fi; commit() }) }
        cs.addView(f4, m.lp(m.MP, m.WC))
        cs.addView(slider(act, m, "Transparencia de cristal", 0, 100, cfg.glass, { cfg.glass = it; live() }, { commit() }))
        p.addView(cs, m.lp(m.MP, m.WC, 0, 0, 0, 12))

        // ---- fuente ----
        val ca = m.card()
        ca.addView(m.tv("Fuente del reloj", 18f, m.onSurface, true))
        ca.addView(slider(act, m, "Grosor", 100, 900, cfg.weight, { cfg.weight = it; live() }, { commit() }))
        ca.addView(slider(act, m, "Ancho", 50, 150, cfg.wdth, { cfg.wdth = it; live() }, { commit() }))
        ca.addView(slider(act, m, "Redondez", 0, 100, cfg.rond, { cfg.rond = it; live() }, { commit() }))
        ca.addView(slider(act, m, "Inclinación", -10, 0, cfg.slnt, { cfg.slnt = it; live() }, { commit() }))
        p.addView(ca, m.lp(m.MP, m.WC, 0, 0, 0, 12))

        // ---- colores ----
        val cc = m.card()
        cc.addView(m.tv("Colores", 18f, m.onSurface, true))
        cc.addView(m.tv("Color del reloj", 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 10, 0, 6))
        val pal = ArrayList<Int>(palette)
        pal.add(m.primary)
        val f1 = RtFlow(act, m.dp(8))
        for (col in pal) { val x = col; f1.addView(swatch(act, m, x, cfg.color == x) { cfg.color = x; commit() }, ViewGroup.LayoutParams(m.dp(38), m.dp(38))) }
        cc.addView(f1, m.lp(m.MP, m.WC))
        if (cfg.style == "color") {
            cc.addView(m.tv("Color final del degradado", 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 14, 0, 6))
            val f2 = RtFlow(act, m.dp(8))
            for (col in pal) { val x = col; f2.addView(swatch(act, m, x, cfg.accent == x) { cfg.accent = x; commit() }, ViewGroup.LayoutParams(m.dp(38), m.dp(38))) }
            cc.addView(f2, m.lp(m.MP, m.WC))
        }
        p.addView(cc, m.lp(m.MP, m.WC, 0, 0, 0, 12))

        // ---- aplicar al reloj real ----
        val cr = m.card()
        cr.addView(m.tv("Reloj real del sistema", 18f, m.onSurface, true))
        cr.addView(m.tv("Los cambios se envían al reloj de tu pantalla de bloqueo mediante el módulo LSPosed (ámbito Interfaz del sistema). Si algo no se actualiza, reinicia SystemUI.", 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 4, 0, 8))
        cr.addView(m.switchRow("Aplicar a la pantalla de bloqueo", "", lp.getBoolean("lk_native", true)) { v ->
            lp.edit().putBoolean("lk_native", v).apply()
            val ok = LockXp.apply(cfg, v)
            say(if (ok) (if (v) "Aplicado" else "Reloj original restaurado al reiniciar SystemUI") else "El módulo no está activo en LSPosed")
        })
        cr.addView(m.tonal("Reiniciar SystemUI") {
            LockXp.apply(cfg, lp.getBoolean("lk_native", true))
            say("Reiniciando SystemUI…")
            Thread { Sx.su("killall com.android.systemui") }.start()
        }, m.lp(m.WC, m.WC, 0, 10, 0, 0))
        cr.addView(m.tonal("Ver bloqueo ahora") { Thread { Sx.su("input keyevent 223; sleep 1; input keyevent 224") }.start(); say("Mostrando tu bloqueo real…") }, m.lp(m.WC, m.WC, 0, 8, 0, 0))
        cr.addView(m.switchRow("Probar en vivo", "Tras cada cambio bloquea y muestra el reloj real", lp.getBoolean("lk_live", false)) { v -> lp.edit().putBoolean("lk_live", v).apply() }, m.lp(m.MP, m.WC, 0, 12, 0, 0))
        cr.addView(m.switchRow("Panel flotante (experimental)", "Controles encima de tu bloqueo real, si la ROM lo permite", lp.getBoolean("lk_panel", false)) { v ->
            lp.edit().putBoolean("lk_panel", v).apply()
            try { LockPanelService.toggle(act, v); say(if (v) "Panel activo: bloquea el teléfono para probarlo" else "Panel cerrado") } catch (e: Throwable) { say("No pude abrir el panel: " + e.message) }
        }, m.lp(m.MP, m.WC, 0, 8, 0, 0))
        cr.addView(m.tv(ClockMetrics.status(act), 12f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 10, 0, 0))
        p.addView(cr, m.lp(m.MP, m.WC, 0, 0, 0, 12))
        return p
    }
}
