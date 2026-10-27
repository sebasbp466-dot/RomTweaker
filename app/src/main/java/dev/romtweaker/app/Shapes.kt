package dev.romtweaker.app

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class Shape(val id: String, val label: String, val path: String)

class ShapeView(ctx: Context, private val shapePath: Path) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    var normal: Int = 0
    var accent: Int = 0

    override fun onDraw(canvas: Canvas) {
        val s = Math.min(width, height) / 100f
        fill.color = if (isSelected) accent else normal
        canvas.save()
        canvas.scale(s, s)
        canvas.drawPath(shapePath, fill)
        canvas.restore()
    }
}

object ShapeLib {
    const val PREFIX = "dev.romtweaker.shape."

    fun pkg(id: String): String = PREFIX + id

    fun load(ctx: Context): List<Shape> {
        val res = ArrayList<Shape>()
        try {
            val txt = ctx.assets.open("shapes.txt").bufferedReader().readText()
            for (raw in txt.lines()) {
                val l = raw.trim()
                if (l.isEmpty() || l.startsWith("#")) continue
                val p = l.split("|")
                if (p.size >= 3) res.add(Shape(p[0].trim(), p[1].trim(), p[2].trim()))
            }
        } catch (e: Exception) {
        }
        return res
    }

    fun parsePath(data: String): Path {
        val path = Path()
        val tok = Regex("[MLCZmlcz]|-?\\d*\\.?\\d+").findAll(data).map { it.value }.toList()
        var i = 0
        var cmd = ' '
        while (i < tok.size) {
            val t = tok[i]
            if (t.length == 1 && t[0].isLetter()) {
                cmd = t[0].uppercaseChar()
                i++
                if (cmd == 'Z') path.close()
                continue
            }
            when (cmd) {
                'M' -> {
                    if (i + 1 < tok.size) path.moveTo(tok[i].toFloat(), tok[i + 1].toFloat())
                    i += 2
                    cmd = 'L'
                }
                'L' -> {
                    if (i + 1 < tok.size) path.lineTo(tok[i].toFloat(), tok[i + 1].toFloat())
                    i += 2
                }
                'C' -> {
                    if (i + 5 < tok.size) {
                        path.cubicTo(
                            tok[i].toFloat(), tok[i + 1].toFloat(), tok[i + 2].toFloat(),
                            tok[i + 3].toFloat(), tok[i + 4].toFloat(), tok[i + 5].toFloat()
                        )
                    }
                    i += 6
                }
                else -> i++
            }
        }
        return path
    }

    fun card(act: Activity, ui: Ui, report: (String) -> Unit): LinearLayout {
        val c = ui.card()
        c.addView(ui.heading("Formas de iconos"))
        c.addView(ui.body("Cambia la forma de los iconos del launcher. Si tu ROM no deja activarla en caliente, se instala como módulo de KernelSU y hay que reiniciar."))
        val shapes = load(act)
        if (shapes.isEmpty()) {
            c.addView(ui.body("Esta versión no incluye formas."))
            return c
        }
        val views = ArrayList<Pair<Shape, ShapeView>>()
        for (chunk in shapes.chunked(4)) {
            val row = ui.row()
            for (s in chunk) {
                val cell = LinearLayout(act)
                cell.orientation = LinearLayout.VERTICAL
                cell.gravity = Gravity.CENTER_HORIZONTAL
                cell.setPadding(0, ui.dp(6), 0, ui.dp(6))
                cell.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                val sv = ShapeView(act, parsePath(s.path))
                sv.normal = ui.p.onVariant
                sv.accent = ui.p.primary
                sv.layoutParams = LinearLayout.LayoutParams(ui.dp(52), ui.dp(52))
                val lb = TextView(act)
                lb.text = s.label
                lb.textSize = 11f
                lb.gravity = Gravity.CENTER
                lb.setTextColor(ui.p.onVariant)
                cell.addView(sv)
                cell.addView(lb)
                cell.setOnClickListener {
                    for (pr in views) pr.second.isSelected = (pr.first.id == s.id)
                    ShapeOps2.apply(act, s, report)
                }
                row.addView(cell)
                views.add(Pair(s, sv))
            }
            c.addView(row)
        }
        c.addView(ui.button("Volver a la forma original") {
            for (pr in views) pr.second.isSelected = false
            ShapeOps2.reset(act, report)
        })
        Thread {
            val active = Adapt.listOverlays().lines().map { it.trim() }
                .firstOrNull { it.startsWith("[x]") && it.substring(3).trim().startsWith(PREFIX) }
                ?.substring(3)?.trim()
            act.runOnUiThread {
                for (pr in views) pr.second.isSelected = (active != null && active == pkg(pr.first.id))
            }
        }.start()
        return c
    }
}

object ShapeOps {
    private const val MOD = "/data/adb/modules/romtweaker_overlays"

    private fun extract(ctx: Context, id: String): File? {
        return try {
            val f = File(ctx.filesDir, "ov_" + id + ".apk")
            ctx.assets.open("overlays/" + id + ".apk").use { i ->
                f.outputStream().use { o -> i.copyTo(o) }
            }
            f
        } catch (e: Exception) {
            null
        }
    }

    private fun relaunch(): String =
        "am force-stop \$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1 | cut -d/ -f1)"

    private fun installModule(ctx: Context): String {
        val dir = MOD + "/system/product/overlay"
        val prep = Root.su(
            "mkdir -p " + dir + " && printf 'id=romtweaker_overlays\\nname=RomTweaker overlays\\nversion=1\\nversionCode=1\\nauthor=RomTweaker\\ndescription=Overlays generados por RomTweaker\\n' > " + MOD + "/module.prop"
        )
        if (prep.first != 0) return "✗ No se pudo crear el módulo: " + prep.second
        var n = 0
        for (s in ShapeLib.load(ctx)) {
            val f = extract(ctx, s.id) ?: continue
            val dst = dir + "/" + s.id + ".apk"
            val r = Root.su(
                "cp " + Root.q(f.absolutePath) + " " + dst + " && chmod 644 " + dst +
                    " && (chcon u:object_r:system_file:s0 " + dst + " 2>/dev/null; true)"
            )
            if (r.first == 0) n++
        }
        return if (n > 0) "Instalado como módulo de KernelSU (" + n + " formas). Reinicia el teléfono y vuelve a pulsar la forma."
        else "✗ No se pudo copiar ningún overlay al módulo."
    }

    fun apply(act: Activity, s: Shape, report: (String) -> Unit) {
        Adapt.async(act, report) {
            val pk = ShapeLib.pkg(s.id)
            val pq = Root.q(pk)
            val sb = StringBuilder()
            val apk = extract(act, s.id)
            val installed = Adapt.listOverlays().contains(pk)
            if (apk == null && !installed) {
                return@async "✗ El overlay «" + s.label + "» no está dentro de la app (¿compilación sin overlays?)."
            }
            if (apk != null && !installed) {
                val tmp = "/data/local/tmp/rt_ov.apk"
                val r = Adapt.attempt(
                    act, "instalar-overlay",
                    listOf(
                        Adapt.Step(
                            "pm install",
                            "cp " + Root.q(apk.absolutePath) + " " + tmp + " && chmod 644 " + tmp + " && pm install -r " + tmp
                        )
                    )
                ) { Adapt.listOverlays().contains(pk) }
                sb.append(r.lines().first()).append('\n')
            }
            val steps = listOf(
                Adapt.Step("enable-exclusive", "cmd overlay enable-exclusive --user 0 --category " + pq),
                Adapt.Step("enable", "cmd overlay enable --user 0 " + pq),
                Adapt.Step("om enable", "om enable " + pq)
            )
            val en = Adapt.attempt(act, "overlay-on", steps) { Adapt.overlayEnabled(pk) }
            if (en.startsWith("✓")) {
                Root.su(relaunch())
                return@async sb.toString() + en + "\nForma «" + s.label + "» activa. Launcher reiniciado."
            }
            Root.su("pm uninstall " + pq)
            val mod = installModule(act)
            sb.toString() + en.lines().first() + "\n" + mod
        }
    }

    fun reset(act: Activity, report: (String) -> Unit) {
        Adapt.async(act, report) {
            val out = StringBuilder()
            for (l in Adapt.listOverlays().lines()) {
                val t = l.trim()
                if (t.startsWith("[x]")) {
                    val pk = t.substring(3).trim()
                    if (pk.startsWith(ShapeLib.PREFIX)) {
                        Root.su("cmd overlay disable --user 0 " + Root.q(pk) + " || om disable " + Root.q(pk))
                        out.append("Desactivada ").append(pk).append('\n')
                    }
                }
            }
            Root.su(relaunch())
            if (out.isEmpty()) "No había ninguna forma de RomTweaker activa." else out.toString().trim() + "\nLauncher reiniciado."
        }
    }
}
