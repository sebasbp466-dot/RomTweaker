package dev.romtweaker.app

import android.app.Activity
import android.view.View
import android.widget.LinearLayout

/** Los ajustes antiguos como categorías desplegables dentro de la pestaña Iconify (mismo estilo M3 y blur). */
object ClassicHost {
    fun sections(act: Activity, m: M3, say: (String) -> Unit): List<View> {
        val pal = Palette(
            false, 0, m.a(m.onSurface, 0.06f), m.onSurface, m.onSurfaceVar,
            m.primary, m.onPrimary, m.a(m.onSurface, 0.12f), m.onSurface, m.a(m.onSurfaceVar, 0.5f)
        )
        val ui = Ui(act, pal)
        val cp = ClassicPages(act, ui, say)
        val names = listOf("Información y diagnóstico", "Colores del sistema", "Overlays de tu ROM", "Sistema (DPI, animaciones, props)", "Perfiles", "Interfaz y Centro de control")
        val descs = listOf("Dispositivo, root, diagnóstico y estilo de la app.", "Paleta Material You, tema claro/oscuro.", "Activar o desactivar overlays, formas y extras.", "Densidad, escala de animaciones y propiedades.", "Guardar, aplicar e importar configuraciones.", "Ajustes de interfaz y Centro de control propio.")
        val bodies = arrayOfNulls<LinearLayout>(names.size)
        val heads = arrayOfNulls<android.widget.TextView>(names.size)
        fun fill(i: Int) {
            val b = bodies[i] ?: return
            b.removeAllViews()
            b.addView(cp.tab(i), LinearLayout.LayoutParams(MATCH, WRAP))
        }
        cp.onTab = { i -> fill(i) }
        val out = ArrayList<View>()
        for (i in names.indices) {
            val c = m.card()
            val h = m.tv("▸  " + names[i], 18f, m.onSurface, true)
            heads[i] = h
            c.addView(h)
            c.addView(m.tv(descs[i], 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 2, 0, 4))
            val b = LinearLayout(act)
            b.orientation = LinearLayout.VERTICAL
            b.visibility = View.GONE
            bodies[i] = b
            c.addView(b, m.lp(m.MP, m.WC, 0, 8, 0, 0))
            var built = false
            val toggle = View.OnClickListener {
                if (b.visibility == View.GONE) {
                    if (!built) { built = true; fill(i) }
                    b.visibility = View.VISIBLE
                    h.text = "▾  " + names[i]
                } else {
                    b.visibility = View.GONE
                    h.text = "▸  " + names[i]
                }
            }
            h.setOnClickListener(toggle)
            out.add(c)
        }
        return out
    }
}
