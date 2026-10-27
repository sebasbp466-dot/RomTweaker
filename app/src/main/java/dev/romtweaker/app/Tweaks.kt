package dev.romtweaker.app

import android.app.Activity
import android.widget.LinearLayout
import android.widget.ScrollView

class Res(val target: String, val type: String, val name: String)
class Opt(val label: String, val values: List<String>)
class Tweak(val id: String, val label: String, val dtype: String, val resources: List<Res>, val options: List<Opt>)

object Catalog {
    private const val SUI = "com.android.systemui"

    private fun same(label: String, v: String, n: Int): Opt = Opt(label, List(n) { v })

    val list: List<Tweak> = listOf(
        Tweak(
            "font", "Fuente del sistema", "string",
            listOf(
                Res("android", "string", "config_bodyFontFamily"),
                Res("android", "string", "config_bodyFontFamilyMedium"),
                Res("android", "string", "config_headlineFontFamily"),
                Res("android", "string", "config_headlineFontFamilyMedium")
            ),
            listOf(
                same("Sans", "sans-serif", 4), same("Serif", "serif", 4), same("Mono", "monospace", 4),
                same("Condensada", "sans-serif-condensed", 4), same("Casual", "casual", 4), same("Cursiva", "cursive", 4)
            )
        ),
        Tweak(
            "anim", "Velocidad de las animaciones del sistema", "int",
            listOf(
                Res("android", "integer", "config_shortAnimTime"),
                Res("android", "integer", "config_mediumAnimTime"),
                Res("android", "integer", "config_longAnimTime")
            ),
            listOf(
                Opt("Muy rápida", listOf("80", "160", "200")),
                Opt("Rápida", listOf("120", "240", "300")),
                Opt("Lenta", listOf("300", "600", "750")),
                Opt("Muy lenta", listOf("400", "800", "1000"))
            )
        ),
        Tweak(
            "qs_cols", "Mosaicos por fila (panel rápido)", "int",
            listOf(Res(SUI, "integer", "quick_settings_num_columns"), Res(SUI, "integer", "quick_settings_infinite_grid_num_columns")),
            listOf(same("2", "2", 2), same("3", "3", 2), same("4", "4", 2), same("5", "5", 2))
        ),
        Tweak(
            "qqs", "Mosaicos del panel rápido compacto", "int",
            listOf(Res(SUI, "integer", "quick_qs_panel_max_tiles")),
            listOf(same("4", "4", 1), same("5", "5", 1), same("6", "6", 1), same("8", "8", 1))
        ),
        Tweak(
            "qs_radius", "Redondeo de los mosaicos del panel rápido", "dp",
            listOf(Res(SUI, "dimen", "qs_corner_radius")),
            listOf(same("0", "0", 1), same("8", "8", 1), same("14", "14", 1), same("24", "24", 1), same("32", "32", 1))
        ),
        Tweak(
            "clock", "Tamaño del reloj (barra de estado)", "sp",
            listOf(Res(SUI, "dimen", "status_bar_clock_size")),
            listOf(same("12", "12", 1), same("14", "14", 1), same("16", "16", 1), same("18", "18", 1), same("20", "20", 1))
        ),
        Tweak(
            "sb_pad", "Margen lateral de la barra de estado", "dp",
            listOf(Res(SUI, "dimen", "status_bar_padding_start"), Res(SUI, "dimen", "status_bar_padding_end")),
            listOf(same("0", "0", 2), same("8", "8", 2), same("16", "16", 2), same("24", "24", 2))
        )
    )
}

object TweakOps {
    private fun ident(r: Res): String = r.target + ":" + r.type + "/" + r.name

    fun lookup(r: Res): String {
        val cmds = listOf(
            "cmd overlay lookup --user 0 " + r.target + " " + ident(r),
            "cmd overlay lookup " + r.target + " " + ident(r)
        )
        for (c in cmds) {
            val x = Root.su(c)
            if (x.first == 0 && x.second.isNotEmpty() && !x.second.contains("Error") && !x.second.contains("not found")) return x.second
        }
        return ""
    }

    fun probe(t: Tweak): String? {
        for (r in t.resources) {
            val v = lookup(r)
            if (v.isNotEmpty()) return v
        }
        return null
    }

    private fun variants(dtype: String, value: String): List<Pair<String, String>> {
        val n = value.toIntOrNull() ?: 0
        return when (dtype) {
            "string" -> listOf("string" to value, "0x03" to value)
            "int" -> listOf("int" to value, "0x10" to value)
            "dp" -> {
                val e = ((n shl 8) or 1).toString()
                listOf("0x05" to e, "dimension" to e)
            }
            "sp" -> {
                val e = ((n shl 8) or 2).toString()
                listOf("0x05" to e, "dimension" to e)
            }
            "color" -> listOf("color" to value, "0x1c" to value)
            "bool" -> listOf("boolean" to value, "0x12" to (if (value == "true") "0xffffffff" else "0x0"))
            else -> listOf("string" to value)
        }
    }

    private fun fabCmd(r: Res, name: String, type: String, data: String): String =
        "cmd overlay fabricate --target " + r.target + " --name " + name + " " + ident(r) + " " + type + " " + Root.q(data) +
            " && cmd overlay enable --user 0 \$(cmd overlay list --user 0 | grep -o '[A-Za-z0-9_.]*:" + name + "' | head -n 1)"

    private fun enabled(name: String): Boolean {
        for (l in Adapt.listOverlays().lines()) {
            val t = l.trim()
            if (t.startsWith("[x]") && t.substring(3).trim().endsWith(":" + name)) return true
        }
        return false
    }

    fun applyOption(act: Activity, t: Tweak, o: Opt, report: (String) -> Unit) {
        Adapt.async(act, report) {
            val sb = StringBuilder()
            var ok = 0
            for ((i, r) in t.resources.withIndex()) {
                if (lookup(r).isEmpty()) {
                    sb.append("· ").append(r.name).append(": no existe en tu ROM\n")
                    continue
                }
                val name = "rt_" + t.id + "_" + i
                val steps = variants(t.dtype, o.values[i]).map { Adapt.Step(it.first, fabCmd(r, name, it.first, it.second)) }
                val res = Adapt.attempt(act, "fab-" + t.dtype, steps) { enabled(name) }
                if (res.startsWith("✓")) {
                    ok++
                    sb.append("✓ ").append(r.name).append('\n')
                } else {
                    sb.append("✗ ").append(r.name).append(" — ").append(res.lines().drop(1).take(2).joinToString(" ")).append('\n')
                }
            }
            if (ok > 0) sb.append("\nSi no ves el cambio, pulsa «Reiniciar SystemUI».")
            sb.toString().trim()
        }
    }

    fun restore(act: Activity, t: Tweak?, report: (String) -> Unit) {
        Adapt.async(act, report) {
            val prefix = if (t == null) ":rt_" else ":rt_" + t.id + "_"
            var n = 0
            for (l in Adapt.listOverlays().lines()) {
                val x = l.trim()
                if (x.startsWith("[x]")) {
                    val id = x.substring(3).trim()
                    if (id.contains(prefix)) {
                        Root.su("cmd overlay disable --user 0 " + Root.q(id))
                        n++
                    }
                }
            }
            if (n == 0) "No había ajustes activos." else "Restaurado (" + n + " recursos). Si no ves el cambio, pulsa «Reiniciar SystemUI»."
        }
    }
}

object TweakUi {
    private fun parseRes(pkg: String, res: String): Res? {
        val p = res.split("/")
        if (pkg.isEmpty() || p.size != 2 || p[0].isEmpty() || p[1].isEmpty()) return null
        return Res(pkg, p[0], p[1])
    }

    fun page(act: Activity, ui: Ui, report: (String) -> Unit): ScrollView {
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(24))

        val intro = ui.card()
        intro.addView(ui.heading("Personalización del sistema"))
        intro.addView(ui.body("Overlays fabricados al instante con root, sin reiniciar. Cada ajuste solo se aplica si tu ROM tiene ese recurso."))
        intro.addView(ui.button("Reiniciar SystemUI", true) { Actions.restartSystemUi(act, report) })
        intro.addView(ui.button("Quitar todos mis ajustes") { TweakOps.restore(act, null, report) })
        col.addView(intro)

        for (t in Catalog.list) {
            val c = ui.card()
            c.addView(ui.heading(t.label))
            val st = ui.body("Comprobando…")
            c.addView(st)
            val row = ui.row()
            for (o in t.options) row.addView(ui.chip(o.label, false) { TweakOps.applyOption(act, t, o, report) })
            row.addView(ui.chip("Original", false) { TweakOps.restore(act, t, report) })
            c.addView(ui.hscroll(row))
            col.addView(c)
            Thread {
                val cur = TweakOps.probe(t)
                act.runOnUiThread {
                    st.text = if (cur != null) "Disponible · actual: " + cur.lines().first() else "No disponible en tu ROM"
                }
            }.start()
        }

        val cc = ui.card()
        cc.addView(ui.heading("Recurso personalizado"))
        cc.addView(ui.body("Para probar cualquier recurso. Ej.: paquete com.android.systemui, recurso integer/quick_qs_panel_max_tiles, tipo int. Tipos: string, int, dp, sp, bool, color (valor 0xAARRGGBB)."))
        val pkg = ui.input("Paquete, ej. com.android.systemui")
        val resIn = ui.input("Recurso, ej. integer/quick_qs_panel_max_tiles")
        val type = ui.input("Tipo: string, int, dp, sp, bool o color")
        type.setText("int")
        val value = ui.input("Valor")
        cc.addView(pkg)
        cc.addView(resIn)
        cc.addView(type)
        cc.addView(value)
        cc.addView(ui.button("Leer valor actual") {
            val r = parseRes(pkg.text.toString().trim(), resIn.text.toString().trim())
            if (r == null) {
                report("Escribe paquete y recurso (tipo/nombre)")
            } else {
                Adapt.async(act, report) {
                    val v = TweakOps.lookup(r)
                    if (v.isEmpty()) "No existe o no se pudo leer" else "Valor actual: " + v
                }
            }
        })
        cc.addView(ui.button("Aplicar", true) {
            val r = parseRes(pkg.text.toString().trim(), resIn.text.toString().trim())
            val dt = type.text.toString().trim().ifEmpty { "int" }
            val v = value.text.toString().trim()
            if (r == null || v.isEmpty()) {
                report("Completa paquete, recurso y valor")
            } else {
                val o = Opt("x", listOf(v))
                TweakOps.applyOption(act, Tweak("custom", "personalizado", dt, listOf(r), listOf(o)), o, report)
            }
        })
        col.addView(cc)
        col.addView(Explorer.card(act, ui, pkg, resIn, type, report))

        val sv = ScrollView(act)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(col)
        return sv
    }
}
