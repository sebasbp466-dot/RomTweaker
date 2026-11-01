package dev.romtweaker.app.xp

import android.content.SharedPreferences
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.libxposed.api.XposedModule

/**
 * Tanda 1 de funciones de Iconify: ocultar elementos de SystemUI (icono de candado, operador del bloqueo, iconos de la barra en el bloqueo,
 * botones del pie de notificaciones, texto de "silenciosas", operador de ajustes rápidos) y tamaño del reloj de la barra de estado.
 * Un solo hook en View.onAttachedToWindow; todo protegido con try/catch y apagado por defecto.
 */
object IcxHide {
    private const val TAG = "RomTweaker"
    private const val PKG = "com.android.systemui"
    private const val CLOCK = "com.android.systemui.statusbar.policy.Clock"

    class Rule(val key: String, val ids: List<String>, val classes: List<String>, val within: List<String>)

    private val rules = listOf(
        Rule("hx_lockicon", listOf("device_entry_icon_view", "device_entry_icon_bg", "device_entry_icon_fg", "lock_icon", "lock_icon_view", "lock_icon_bg"), emptyList(), emptyList()),
        Rule("hx_lockcarrier", listOf("keyguard_carrier_text"), emptyList(), emptyList()),
        Rule("hx_lockstatus", listOf("status_icon_area", "keyguard_carrier_text"), emptyList(), listOf("keyguard_header", "keyguard_status_bar", "keyguard_status_bar_container")),
        Rule("hx_footer", listOf("dismiss_text", "settings_button", "history_button", "manage_text"), emptyList(), emptyList()),
        Rule("hx_silent", emptyList(), listOf("com.android.systemui.statusbar.notification.stack.SectionHeaderView"), emptyList()),
        Rule("hx_qscarrier", listOf("carrier_group", "qs_carrier_group", "qs_carriers"), emptyList(), emptyList())
    )

    private var idMap: HashMap<Int, ArrayList<Rule>>? = null
    private var errs = 0

    private fun blank(): RenderEffect = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(ColorMatrix(FloatArray(20))))

    private fun buildMap(v: View) {
        val m = HashMap<Int, ArrayList<Rule>>()
        for (r in rules) for (n in r.ids) {
            val id = v.resources.getIdentifier(n, "id", PKG)
            if (id != 0) m.getOrPut(id) { ArrayList() }.add(r)
        }
        idMap = m
    }

    private fun within(v: View, names: List<String>): Boolean {
        if (names.isEmpty()) return true
        var p = v.parent
        var depth = 0
        while (p is View && depth < 14) {
            val pv = p as View
            if (pv.id != View.NO_ID) {
                val n = try { pv.resources.getResourceEntryName(pv.id) } catch (e: Throwable) { "" }
                if (n in names) return true
            }
            p = pv.parent
            depth++
        }
        return false
    }

    private fun hide(v: View) {
        v.post {
            try {
                v.setRenderEffect(blank())
                v.isClickable = false
                v.isEnabled = false
            } catch (e: Throwable) { }
        }
    }

    private fun onAttach(x: XposedModule, v: View, prefs: SharedPreferences?) {
        if (prefs == null) return
        if (idMap == null) buildMap(v)
        val id = v.id
        if (id != View.NO_ID) {
            val l = idMap?.get(id)
            if (l != null) for (r in l) {
                if (prefs.getBoolean(r.key, false) && within(v, r.within)) { hide(v); return }
            }
        }
        val cn = v.javaClass.name
        for (r in rules) {
            if (r.classes.isNotEmpty() && cn in r.classes && prefs.getBoolean(r.key, false)) { hide(v); return }
        }
        if (cn == CLOCK && v is TextView) {
            val s = (prefs.getString("hx_clock_size", "0") ?: "0").toIntOrNull() ?: 0
            if (s in 8..40) v.post { try { v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, s.toFloat()) } catch (e: Throwable) { } }
        }
    }

    fun install(x: XposedModule, prefs: SharedPreferences?) {
        try {
            val m = View::class.java.getDeclaredMethod("onAttachedToWindow")
            x.hook(m).intercept { chain ->
                val r = chain.proceed()
                try {
                    val v = chain.thisObject as? View
                    if (v != null && v.context.packageName == PKG) onAttach(x, v, prefs)
                } catch (e: Throwable) { if (errs++ < 5) x.log(Log.WARN, TAG, "hx: " + Log.getStackTraceString(e)) }
                r
            }
            x.log(Log.INFO, TAG, "hx: ocultar elementos instalado")
        } catch (e: Throwable) { x.log(Log.WARN, TAG, "hx: fallo instalando " + e) }
    }
}
