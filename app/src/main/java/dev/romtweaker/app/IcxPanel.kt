package dev.romtweaker.app

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar

/** Tanda 1 de Iconify (LSPosed): ocultar elementos de SystemUI y tamaño del reloj de la barra. Se aplican al reiniciar SystemUI. */
object IcxPanel {
    private val opts = listOf(
        Triple("hx_lockicon", "Ocultar candado/huella del bloqueo", "Quita el icono de la pantalla de bloqueo"),
        Triple("hx_lockcarrier", "Ocultar operador en el bloqueo", "Texto del operador bajo la barra"),
        Triple("hx_lockstatus", "Ocultar iconos de la barra en el bloqueo", "Wi-Fi, batería… solo en la pantalla de bloqueo"),
        Triple("hx_footer", "Ocultar botones del pie de notificaciones", "Borrar todo, ajustes, historial"),
        Triple("hx_silent", "Ocultar el texto «Silenciosas»", "Cabecera de notificaciones silenciosas"),
        Triple("hx_qscarrier", "Ocultar operador en ajustes rápidos", "Grupo de operadores del panel")
    )

    private fun bridge(name: String, k: String, v: Any): Boolean = try {
        val c = Class.forName("dev.romtweaker.app.xp.XpBridge")
        val inst = c.getField("INSTANCE").get(null)
        val mt = c.methods.first { it.name == name && it.parameterTypes.size == 2 }
        (mt.invoke(inst, k, v) as? Boolean) ?: true
    } catch (e: Throwable) { false }

    fun card(act: Activity, m: M3, say: (String) -> Unit): View {
        val p = act.getSharedPreferences("lock", Activity.MODE_PRIVATE)
        val c = m.card()
        c.addView(m.tv("Extras de Iconify · ocultar elementos", 18f, m.onSurface, true))
        c.addView(m.tv("Funciones del módulo de Iconify portadas a LSPosed. Todas apagadas por defecto; actívalas y pulsa «Reiniciar SystemUI».", 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 4, 0, 8))
        for (o in opts) {
            val key = o.first
            c.addView(m.switchRow(o.second, o.third, p.getBoolean(key, false)) { v ->
                p.edit().putBoolean(key, v).apply()
                say(if (bridge("putBool", key, v)) "Guardado. Reinicia SystemUI para aplicar" else "El módulo no está activo en LSPosed")
            }, m.lp(m.MP, m.WC, 0, 6, 0, 0))
        }
        val size = p.getInt("hx_clock_size", 0)
        val t = m.tv(if (size == 0) "Tamaño del reloj de la barra: original" else "Tamaño del reloj de la barra: $size sp", 14f, m.onSurfaceVar)
        c.addView(t, m.lp(m.MP, m.WC, 0, 14, 0, 0))
        val sb = SeekBar(act)
        sb.max = 24
        sb.progress = if (size == 0) 0 else (size - 7).coerceIn(1, 24)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, pr: Int, fromUser: Boolean) {
                t.text = if (pr == 0) "Tamaño del reloj de la barra: original" else "Tamaño del reloj de la barra: ${pr + 7} sp"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                val pr = s?.progress ?: 0
                val v = if (pr == 0) 0 else pr + 7
                p.edit().putInt("hx_clock_size", v).apply()
                bridge("putString", "hx_clock_size", v.toString())
            }
        })
        c.addView(sb, m.lp(m.MP, m.WC))
        c.addView(m.tonal("Reiniciar SystemUI") {
            say("Reiniciando SystemUI…")
            Thread { Sx.su("killall com.android.systemui") }.start()
        }, m.lp(m.WC, m.WC, 0, 12, 0, 0))
        return c
    }
}
