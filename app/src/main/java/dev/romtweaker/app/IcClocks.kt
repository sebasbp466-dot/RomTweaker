package dev.romtweaker.app

import android.app.Activity
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
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

/** Relojes de pantalla de bloqueo portados de Iconify (60 estilos), con sus valores por defecto (margen 100/40 dp, tamaño 100 %). */
object IcClocks {
    const val COUNT = 60
    const val D_TOP = 100
    const val D_BOTTOM = 40
    const val D_SCALE = 100

    private fun bridge(name: String, k: String, v: Any): Boolean = try {
        val c = Class.forName("dev.romtweaker.app.xp.XpBridge")
        val inst = c.getField("INSTANCE").get(null)
        val mt = c.methods.first { it.name == name && it.parameterTypes.size == 2 }
        (mt.invoke(inst, k, v) as? Boolean) ?: true
    } catch (e: Throwable) { false }

    private fun push(p: android.content.SharedPreferences): Boolean {
        var ok = bridge("putBool", "ic_on", p.getBoolean("ic_on", false))
        ok = bridge("putString", "ic_style", p.getInt("ic_style", 1).toString()) && ok
        ok = bridge("putString", "ic_top", p.getInt("ic_top", D_TOP).toString()) && ok
        ok = bridge("putString", "ic_bottom", p.getInt("ic_bottom", D_BOTTOM).toString()) && ok
        ok = bridge("putString", "ic_scale", p.getInt("ic_scale", D_SCALE).toString()) && ok
        ok = bridge("putBool", "ic_date", p.getBoolean("ic_date", true)) && ok
        return ok
    }

    private fun cap(s: String): String {
        val sb = StringBuilder(s.length)
        var up = true
        for (ch in s) { sb.append(if (up && ch.isLetter()) ch.uppercaseChar() else ch); up = ch.isWhitespace() }
        return sb.toString()
    }
    private fun titleAll(v: View) {
        if (v is TextView) {
            val s = v.text?.toString() ?: ""
            if (s.isNotEmpty()) { val u = cap(s); if (u != s) v.text = u }
            v.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun afterTextChanged(e: Editable?) { val t = v.text?.toString() ?: ""; val u = cap(t); if (u != t) v.text = u }
            })
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) titleAll(v.getChildAt(i))
    }
    private fun scaleText(v: View, f: Float) {
        if (v is TextView) v.setTextSize(TypedValue.COMPLEX_UNIT_PX, v.textSize * f)
        if (v is ViewGroup) for (i in 0 until v.childCount) scaleText(v.getChildAt(i), f)
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

    /** Miniatura fiel de la pantalla de bloqueo: mismo layout, mismos márgenes en dp y una notificación de ejemplo debajo. */
    private fun preview(act: Activity, m: M3, n: Int, top: Int, bot: Int, sc: Int): View {
        val dm = act.resources.displayMetrics
        val sw = dm.widthPixels
        val sh = dm.heightPixels
        val pw = (sw * 0.46f).toInt()
        val ph = (pw * sh / sw.toFloat()).toInt()
        val k = pw / sw.toFloat()
        val box = FrameLayout(act)
        val wd = LockStudio.wallpaper(act)
        if (wd != null) {
            val iv = ImageView(act)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageDrawable(wd)
            box.addView(iv, FrameLayout.LayoutParams(-1, -1))
        } else box.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF0F3B3B.toInt(), 0xFF1B6E6E.toInt(), 0xFF15262B.toInt()))
        val dim = View(act)
        dim.setBackgroundColor(0x33000000)
        box.addView(dim, FrameLayout.LayoutParams(-1, -1))
        val screen = LinearLayout(act)
        screen.orientation = LinearLayout.VERTICAL
        try {
            val rid = act.resources.getIdentifier("preview_lockscreen_clock_$n", "layout", act.packageName)
            val v = LayoutInflater.from(act).inflate(rid, null)
            if (n != 10) titleAll(v)
            if (sc != 100) scaleText(v, sc / 100f)
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = m.dp(top)
            lp.bottomMargin = m.dp(bot)
            screen.addView(v, lp)
        } catch (e: Throwable) {
            screen.addView(m.tv("No se pudo mostrar este reloj: " + e.javaClass.simpleName, 40f, 0xFFFFFFFF.toInt()))
        }
        val nt = LinearLayout(act)
        nt.orientation = LinearLayout.VERTICAL
        nt.setPadding(m.dp(20), m.dp(14), m.dp(20), m.dp(14))
        nt.background = m.shape(0xE61C1F22.toInt(), 28)
        nt.addView(m.tv("Notificación de ejemplo", 15f, 0xFFFFFFFF.toInt(), true))
        nt.addView(m.tv("Así se acomodan las notificaciones", 13f, 0xFFB8C0C8.toInt()))
        val nl = LinearLayout.LayoutParams(-1, -2)
        nl.setMargins(m.dp(16), 0, m.dp(16), 0)
        screen.addView(nt, nl)
        screen.pivotX = 0f
        screen.pivotY = 0f
        screen.scaleX = k
        screen.scaleY = k
        box.addView(screen, FrameLayout.LayoutParams(sw, sh))
        box.clipToOutline = true
        box.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, m.dp(24).toFloat()) }
        }
        val holder = FrameLayout(act)
        val bl = FrameLayout.LayoutParams(pw, ph)
        bl.gravity = Gravity.CENTER_HORIZONTAL
        holder.addView(box, bl)
        return holder
    }

    fun card(act: Activity, m: M3, say: (String) -> Unit, refresh: () -> Unit): View {
        val p = act.getSharedPreferences("lock", Activity.MODE_PRIVATE)
        val cur = p.getInt("ic_style", 1).coerceIn(1, COUNT)
        val top = p.getInt("ic_top", D_TOP)
        val bot = p.getInt("ic_bottom", D_BOTTOM)
        val sc = p.getInt("ic_scale", D_SCALE)
        val c = m.card()
        c.addView(m.tv("Relojes de Iconify", 18f, m.onSurface, true))
        c.addView(m.tv("60 estilos portados de Iconify con sus valores por defecto. Sustituyen al reloj y la fecha de tu pantalla de bloqueo real, y las notificaciones se colocan debajo.", 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 4, 0, 10))
        c.addView(preview(act, m, cur, top, bot, sc), m.lp(m.MP, m.WC, 0, 0, 0, 10))

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
            if (!ok) say("El módulo no está activo en LSPosed")
            else if (v) say("Aplicado. Bloquea la pantalla para verlo")
            else { say("Restaurando el reloj original (reiniciando SystemUI)…"); Thread { Sx.su("killall com.android.systemui") }.start() }
        })
        c.addView(m.switchRow("Ocultar la fecha de serie", "", p.getBoolean("ic_date", true)) { v ->
            p.edit().putBoolean("ic_date", v).apply(); push(p)
        })
        c.addView(slider(act, m, "Margen superior (dp)", 0, 300, top) { x -> p.edit().putInt("ic_top", x).apply(); push(p); refresh() })
        c.addView(slider(act, m, "Margen inferior (dp)", 0, 200, bot) { x -> p.edit().putInt("ic_bottom", x).apply(); push(p); refresh() })
        c.addView(slider(act, m, "Tamaño del texto (%)", 50, 150, sc) { x -> p.edit().putInt("ic_scale", x).apply(); push(p); refresh() })
        val br = LinearLayout(act)
        br.orientation = LinearLayout.HORIZONTAL
        br.addView(m.tonal("Valores de Iconify") {
            p.edit().putInt("ic_top", D_TOP).putInt("ic_bottom", D_BOTTOM).putInt("ic_scale", D_SCALE).putBoolean("ic_date", true).apply()
            push(p); say("Valores por defecto de Iconify"); refresh()
        }, m.lp(m.WC, m.WC, 0, 0, 8, 0))
        br.addView(m.tonal("Reiniciar SystemUI") {
            push(p)
            say("Reiniciando SystemUI…")
            Thread { Sx.su("killall com.android.systemui") }.start()
        }, m.lp(m.WC, m.WC))
        c.addView(br, m.lp(m.WC, m.WC, 0, 14, 0, 0))
        return c
    }
}
