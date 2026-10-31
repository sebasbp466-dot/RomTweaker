package dev.romtweaker.app

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

object ClassicHost {
    fun page(act: Activity, say: (String) -> Unit): View {
        val ui = Ui(act, makePalette(act, false))
        val cp = ClassicPages(act, ui, say)
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(ui.p.bg)
        val row = ui.row()
        val frame = FrameLayout(act)
        val names = listOf("Info", "Colores", "Overlays", "Sistema", "Perfiles", "Interfaz")
        fun show(i: Int) {
            row.removeAllViews()
            for (k in names.indices) row.addView(ui.chip(names[k], k == i) { show(k) })
            frame.removeAllViews()
            frame.addView(cp.tab(i), FrameLayout.LayoutParams(MATCH, MATCH))
        }
        cp.onTab = { show(it) }
        show(0)
        root.addView(ui.hscroll(row))
        root.addView(frame, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return root
    }
}
