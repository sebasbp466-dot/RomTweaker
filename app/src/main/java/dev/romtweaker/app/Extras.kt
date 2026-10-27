package dev.romtweaker.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

object Blur {
    fun pref(ctx: Context): Boolean = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("blur", true)

    fun active(act: Activity): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val glass = act.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("glass", false)
        return glass && pref(act) && act.windowManager.isCrossWindowBlurEnabled
    }

    fun apply(act: Activity, glass: Boolean, bg: Int) {
        if (glass && active(act)) {
            act.window.setBackgroundDrawable(ColorDrawable(Color.parseColor("#990A0E24")))
            act.window.setBackgroundBlurRadius(90)
        } else {
            act.window.setBackgroundDrawable(ColorDrawable(bg))
        }
    }

    fun enableSystem(act: Activity, report: (String) -> Unit) {
        Adapt.async(act, report) {
            val rp = Adapt.findResetprop()
            val cmd = "settings put global disable_window_blurs 0; setprop persist.sysui.disableBlur false" +
                (if (rp.isNotEmpty()) "; " + rp + " persist.sysui.disableBlur false" else "")
            val r = Root.su(cmd)
            "Blur del sistema solicitado (" + r.first + "). Reinicia SystemUI o el teléfono si no se nota."
        }
    }
}

object Report {
    fun build(act: Activity, logText: String): String {
        val sb = StringBuilder()
        sb.append("=== RomTweaker informe ===\n").append(RomInfo.summary()).append("\n\n")
        sb.append("su: ").append(Adapt.suName()).append('\n')
        sb.append("SELinux: ").append(Root.su("getenforce").second.trim()).append('\n')
        sb.append("Fingerprint: ").append(Root.prop("ro.build.fingerprint")).append('\n')
        sb.append("Overlays: ").append(Adapt.listOverlays().lines().count { it.trim().startsWith("[") }).append("\n\n")
        sb.append("Métodos:\n").append(Adapt.methodsTable(act)).append("\n\n")
        sb.append("Último mensaje:\n").append(logText)
        return sb.toString()
    }

    fun copy(act: Activity, logText: String, report: (String) -> Unit) {
        Adapt.async(act, report) {
            val txt = build(act, logText)
            act.runOnUiThread {
                val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("RomTweaker", txt))
            }
            val f = File(act.cacheDir, "informe.txt")
            f.writeText(txt)
            Root.su("mkdir -p /sdcard/Download/RomTweaker && cp " + Root.q(f.absolutePath) + " /sdcard/Download/RomTweaker/informe.txt && chmod 666 /sdcard/Download/RomTweaker/informe.txt")
            "Informe copiado al portapapeles y guardado en Descargas/RomTweaker/informe.txt. Pégalo en el chat."
        }
    }
}

object Explorer {
    private val cache = HashMap<String, List<String>>()
    private val pkgs = listOf(
        "android" to "Framework", "com.android.systemui" to "SystemUI",
        "com.android.settings" to "Ajustes", "com.android.launcher3" to "Launcher"
    )

    private fun load(pkg: String): List<String> {
        val c = cache[pkg]
        if (c != null) return c
        val r = Root.su("cat /data/local/tmp/rt_res/" + pkg + ".txt")
        val l = if (r.first == 0) r.second.lines().map { it.trim() }.filter { it.contains("/") } else emptyList()
        if (l.isNotEmpty()) cache[pkg] = l
        return l
    }

    private fun dtype(res: String): String {
        return when (res.substringBefore("/")) {
            "integer" -> "int"
            "dimen" -> "dp"
            "bool" -> "bool"
            "color" -> "color"
            else -> "string"
        }
    }

    fun card(act: Activity, ui: Ui, pkgIn: EditText, resIn: EditText, typeIn: EditText, report: (String) -> Unit): LinearLayout {
        val c = ui.card()
        c.addView(ui.heading("Explorador de recursos"))
        c.addView(ui.body("Busca recursos reales de tu ROM (usa el volcado que genera el comando de Termux). Toca uno para cargarlo arriba."))
        var cur = "com.android.systemui"
        val curLbl = ui.body("Paquete: SystemUI")
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        val search = ui.input("Buscar, ej. qs_ o clock")

        fun render() {
            val q = search.text.toString().trim().lowercase()
            val pk = cur
            Thread {
                val all = load(pk)
                val hits = if (q.isEmpty()) all.take(40) else all.filter { it.lowercase().contains(q) }.take(60)
                act.runOnUiThread {
                    box.removeAllViews()
                    if (all.isEmpty()) box.addView(ui.body("No hay volcado para " + pk + ". Ejecuta otra vez el comando de actualización en Termux."))
                    for (h in hits) {
                        val tv = TextView(act)
                        tv.text = h
                        tv.textSize = 12f
                        tv.setTextColor(ui.p.onSurface)
                        tv.setPadding(0, ui.dp(8), 0, ui.dp(8))
                        tv.setOnClickListener {
                            pkgIn.setText(pk)
                            resIn.setText(h)
                            typeIn.setText(dtype(h))
                            Thread {
                                val v = TweakOps.lookup(Res(pk, h.substringBefore("/"), h.substringAfter("/")))
                                act.runOnUiThread {
                                    report(h + "\nValor actual: " + (if (v.isEmpty()) "(no se pudo leer)" else v))
                                }
                            }.start()
                        }
                        box.addView(tv)
                    }
                }
            }.start()
        }

        val pr = ui.row()
        for (e in pkgs) {
            pr.addView(ui.chip(e.second, false) {
                cur = e.first
                curLbl.text = "Paquete: " + e.second
                render()
            })
        }
        c.addView(ui.hscroll(pr))
        c.addView(curLbl)
        c.addView(search)
        c.addView(box)
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                render()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
        })
        render()
        return c
    }
}

object ExtOps {
    private const val DIR = "/sdcard/Download/RomTweaker/importar"

    fun card(act: Activity, ui: Ui, report: (String) -> Unit): LinearLayout {
        val c = ui.card()
        c.addView(ui.heading("Importar overlays y módulos"))
        c.addView(ui.body("Copia a Descargas/RomTweaker/importar archivos .apk (overlays) o .zip (módulos) de otras ROMs, Iconify o temas, y pulsa Buscar."))
        val list = LinearLayout(act)
        list.orientation = LinearLayout.VERTICAL
        c.addView(ui.button("Buscar archivos", true) { scan(act, ui, list, report) })
        c.addView(list)
        return c
    }

    private fun scan(act: Activity, ui: Ui, list: LinearLayout, report: (String) -> Unit) {
        Thread {
            val r = Root.su("mkdir -p " + DIR + "; ls -1 " + DIR)
            val files = r.second.lines().map { it.trim() }.filter { it.endsWith(".apk", true) || it.endsWith(".zip", true) }
            act.runOnUiThread {
                list.removeAllViews()
                if (files.isEmpty()) list.addView(ui.body("No hay archivos en Descargas/RomTweaker/importar"))
                for (f in files) {
                    val row = ui.row()
                    val tv = TextView(act)
                    tv.text = f
                    tv.textSize = 13f
                    tv.setTextColor(ui.p.onSurface)
                    tv.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                    row.addView(tv)
                    row.addView(ui.chip("Instalar", true) { install(act, f, report) })
                    list.addView(row)
                }
            }
        }.start()
    }

    private fun install(act: Activity, name: String, report: (String) -> Unit) {
        Adapt.async(act, report) {
            val path = DIR + "/" + name
            if (name.endsWith(".zip", true)) installZip(act, path) else installApk(act, path, name)
        }
    }

    private fun overlayIds(): Set<String> {
        val s = HashSet<String>()
        for (l in Adapt.listOverlays().lines()) {
            val t = l.trim()
            if (t.startsWith("[") && t.length > 3) s.add(t.substring(3).trim())
        }
        return s
    }

    private fun installApk(act: Activity, path: String, name: String): String {
        val before = overlayIds()
        val tmp = "/data/local/tmp/rt_ext.apk"
        val cp = "cp " + Root.q(path) + " " + tmp + " && chmod 644 " + tmp + " && "
        val steps = listOf(
            Adapt.Step("pm install", cp + "pm install -r " + tmp),
            Adapt.Step("pm install -t -g", cp + "pm install -r -t -g " + tmp),
            Adapt.Step("pm install -d", cp + "pm install -r -d " + tmp)
        )
        val r = Adapt.attempt(act, "importar-apk", steps, null)
        val sb = StringBuilder(r.lines().first()).append('\n')
        if (r.startsWith("✓")) {
            val added = overlayIds() - before
            if (added.isEmpty()) {
                sb.append("Instalado (no apareció ningún overlay nuevo: puede ser una app normal).")
            } else {
                for (id in added) {
                    val q = Root.q(id)
                    val en = Adapt.attempt(
                        act, "overlay-on",
                        listOf(
                            Adapt.Step("enable-exclusive", "cmd overlay enable-exclusive --user 0 --category " + q),
                            Adapt.Step("enable", "cmd overlay enable --user 0 " + q),
                            Adapt.Step("om enable", "om enable " + q)
                        )
                    ) { Adapt.overlayEnabled(id) }
                    sb.append(en.lines().first()).append(" → ").append(id).append('\n')
                }
            }
            return sb.toString().trim()
        }
        val mid = "rt_ext_" + name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_]"), "_")
        val dir = "/data/adb/modules/" + mid
        val mk = Root.su(
            "mkdir -p " + dir + "/system/product/overlay && cp " + Root.q(path) + " " + dir + "/system/product/overlay/" + Root.q(name) +
                " && chmod 644 " + dir + "/system/product/overlay/" + Root.q(name) +
                " && printf 'id=" + mid + "\\nname=" + mid + "\\nversion=1\\nversionCode=1\\nauthor=RomTweaker\\ndescription=Importado\\n' > " + dir + "/module.prop"
        )
        sb.append(
            if (mk.first == 0) "Instalado como módulo (" + mid + "). Reinicia el teléfono."
            else "✗ No se pudo instalar como módulo: " + mk.second
        )
        return sb.toString().trim()
    }

    private fun installZip(act: Activity, path: String): String {
        val qp = Root.q(path)
        val steps = listOf(
            Adapt.Step("ksud module install", "ksud module install " + qp),
            Adapt.Step("ksud (ruta)", "/data/adb/ksud module install " + qp),
            Adapt.Step("magisk --install-module", "magisk --install-module " + qp),
            Adapt.Step(
                "manual (unzip)",
                "I=\$(unzip -p " + qp + " module.prop | grep '^id=' | cut -d= -f2 | tr -d '\\r'); " +
                    "[ -n \"\$I\" ] && mkdir -p /data/adb/modules/\$I && unzip -o " + qp + " -d /data/adb/modules/\$I"
            )
        )
        val r = Adapt.attempt(act, "importar-modulo", steps, null)
        return if (r.startsWith("✓")) r.lines().first() + "\nReinicia el teléfono para cargar el módulo." else r
    }
}

object Extras {
    private fun setBlur(act: Activity, on: Boolean) {
        act.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("blur", on).apply()
        act.recreate()
    }

    fun homeCards(act: Activity, ui: Ui, getLog: () -> String, report: (String) -> Unit): LinearLayout {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)

        val a = ui.card()
        a.addView(ui.heading("Informe y métodos"))
        a.addView(ui.body("Si algo falla, copia el informe y pégalo en el chat."))
        a.addView(ui.button("Copiar informe", true) { Report.copy(act, getLog(), report) })
        a.addView(ui.button("Ver métodos aprendidos") {
            Adapt.async(act, report) {
                val t = Adapt.methodsTable(act)
                if (t.isEmpty()) "Aún no hay métodos aprendidos." else t
            }
        })
        a.addView(ui.button("Reiniciar aprendizaje") {
            Adapt.resetLearning(act)
            report("Aprendizaje reiniciado")
        })
        box.addView(a)

        val b = ui.card()
        b.addView(ui.heading("Blur y cristal"))
        b.addView(ui.body("Blur real de ventana en el estilo Liquid Glass (necesita que tu ROM lo permita)."))
        val on = Blur.pref(act)
        val br = ui.row()
        br.addView(ui.chip("Blur activado", on) { setBlur(act, true) })
        br.addView(ui.chip("Blur desactivado", !on) { setBlur(act, false) })
        b.addView(ui.hscroll(br))
        b.addView(ui.button("Intentar activar el blur del sistema") { Blur.enableSystem(act, report) })
        box.addView(b)

        val c = ui.card()
        c.addView(ui.heading("Centro de control propio"))
        c.addView(ui.body("Panel flotante con interruptores, brillo y volumen que no depende de tu ROM. Se abre deslizando hacia abajo desde la esquina superior derecha, o desde la notificación."))
        c.addView(ui.button("1) Conceder permisos con root", true) { ControlCenter.grant(act, report) })
        c.addView(ui.button("2) Activar") { ControlCenter.enable(act, true, report) })
        c.addView(ui.button("Desactivar") { ControlCenter.enable(act, false, report) })
        c.addView(ui.button("Abrir panel ahora") { ControlCenter.open(act) })
        box.addView(c)

        val s = ui.card()
        s.addView(ui.heading("Sistema"))
        s.addView(ui.button("Reiniciar SystemUI") { Actions.restartSystemUi(act, report) })
        var armed = false
        s.addView(ui.button("Reiniciar teléfono") {
            if (!armed) {
                armed = true
                report("Pulsa otra vez para confirmar el reinicio")
                Handler(Looper.getMainLooper()).postDelayed({ armed = false }, 5000)
            } else {
                armed = false
                Actions.reboot(act, report)
            }
        })
        box.addView(s)
        return box
    }
}
