package dev.romtweaker.app

import android.app.Activity
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** Relojes de pantalla de bloqueo portados de Iconify (60 estilos). */
object IcClocks {
    const val COUNT = 60

    private fun bridge(name: String, k: String, v: Any): Boolean = try {
        val c = Class.forName("dev.romtweaker.app.xp.XpBridge")
        val inst = c.getField("INSTANCE").get(null)
        val mt = c.methods.first { it.name == name && it.parameterTypes.size == 2 }
        (mt.invoke(inst, k, v) as? Boolean) ?: true
    } catch (e: Throwable) { false }

    private fun push(p: android.content.SharedPreferences): Boolean {
        var ok = bridge("putBool", "ic_on", p.getBoolean("ic_on", false))
        ok = bridge("putString", "ic_style", p.getInt("ic_style", 1).toString()) && ok
        ok = bridge("putString", "ic_top", p.getInt("ic_top", 100).toString()) && ok
        ok = bridge("putString", "ic_scale", p.getInt("ic_scale", 100).toString()) && ok
        ok = bridge("putBool", "ic_date", p.getBoolean("ic_date", true)) && ok
        return ok
    }

    private fun slider(act: Activity, m: M3, label: String, min: Int, max: Int, v: Int, done: (Int) -> Unit): LinearLayout {
        val l = LinearLayout(act)
        l.orientation = LinearLayout.VERTICAL
        val t = m.tv("$label: $v", 14f, m.onSurfaceVar)
        l.addView(t, m.lp(m.MP, m.WC, 0, 10, 0, 0))
        val sb = SeekBar(act)
        sb.max = max - min
        sb.progress = v - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, pr: Int, fromUser: Boolean) { t.text = "$label: ${pr + min}" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { done((s?.progress ?: 0) + min) }
        })
        l.addView(sb, m.lp(m.MP, m.WC))
        return l
    }

    fun card(act: Activity, m: M3, say: (String) -> Unit, refresh: () -> Unit): View {
        val p = act.getSharedPreferences("lock", Activity.MODE_PRIVATE)
        val cur = p.getInt("ic_style", 1).coerceIn(1, COUNT)
        val c = m.card()
        c.addView(m.tv("Relojes de Iconify (experimental)", 18f, m.onSurface, true))
        c.addView(m.tv("60 estilos portados de Iconify. Sustituyen al reloj y la fecha de tu pantalla de bloqueo real mediante el módulo LSPosed.", 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 4, 0, 10))

        val dm = act.resources.displayMetrics
        val box = FrameLayout(act)
        val wd = LockStudio.wallpaper(act)
        if (wd != null) {
            val iv = ImageView(act)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageDrawable(wd)
            box.addView(iv, FrameLayout.LayoutParams(-1, -1))
        } else box.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF0F3B3B.toInt(), 0xFF1B6E6E.toInt(), 0xFF15262B.toInt()))
        val dim = View(act)
        dim.setBackgroundColor(0x55000000)
        box.addView(dim, FrameLayout.LayoutParams(-1, -1))
        try {
            val rid = act.resources.getIdentifier("preview_lockscreen_clock_$cur", "layout", act.packageName)
            val v = LayoutInflater.from(act).inflate(rid, null)
            val fl = FrameLayout.LayoutParams(-1, -2)
            fl.gravity = Gravity.TOP
            fl.topMargin = m.dp(12)
            box.addView(v, fl)
        } catch (e: Throwable) {
            val t = m.tv("No se pudo mostrar la vista previa: " + e.javaClass.simpleName, 12f, 0xFFFFFFFF.toInt())
            box.addView(t, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        box.clipToOutline = true
        box.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, m.dp(20).toFloat()) }
        }
        c.addView(box, m.lp(m.MP, m.dp(230), 0, 0, 0, 10))

        fun set(n: Int) {
            val x = if (n < 1) COUNT else if (n > COUNT) 1 else n
            p.edit().putInt("ic_style", x).apply()
            if (p.getBoolean("ic_on", false)) push(p)
            refresh()
        }
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(m.tonal("◀") { set(cur - 1) }, m.lp(m.WC, m.WC))
        val lab = m.tv("Reloj $cur de $COUNT", 15f, m.onSurface, true)
        lab.gravity = Gravity.CENTER
        row.addView(lab, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(m.tonal("▶") { set(cur + 1) }, m.lp(m.WC, m.WC))
        c.addView(row, m.lp(m.MP, m.WC, 0, 0, 0, 6))

        c.addView(m.switchRow("Usar en la pantalla de bloqueo", "Oculta el reloj y la fecha de serie", p.getBoolean("ic_on", false)) { v ->
            p.edit().putBoolean("ic_on", v).apply()
            val ok = push(p)
            say(if (!ok) "El módulo no está activo en LSPosed" else if (v) "Se aplicará al bloquear la pantalla (si no, reinicia SystemUI)" else "Reloj original restaurado")
        })
        c.addView(m.switchRow("Ocultar la fecha de serie", "", p.getBoolean("ic_date", true)) { v ->
            p.edit().putBoolean("ic_date", v).apply(); push(p)
        })
        c.addView(slider(act, m, "Posición vertical (dp)", 0, 400, p.getInt("ic_top", 100)) { x -> p.edit().putInt("ic_top", x).apply(); push(p) })
        c.addView(slider(act, m, "Tamaño (%)", 50, 150, p.getInt("ic_scale", 100)) { x -> p.edit().putInt("ic_scale", x).apply(); push(p) })
        c.addView(m.tonal("Reiniciar SystemUI") {
            push(p)
            say("Reiniciando SystemUI…")
            Thread { Sx.su("killall com.android.systemui") }.start()
        }, m.lp(m.WC, m.WC, 0, 12, 0, 0))
        return c
    }
}
