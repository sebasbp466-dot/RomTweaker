package dev.romtweaker.app

import android.app.Activity
import android.content.Context
import android.widget.LinearLayout

object Adapt {
    class Step(val name: String, val cmd: String)

    @Volatile
    private var suVariant = -1

    private val suNames = listOf(
        "su -c", "su 0 sh -c", "/system/bin/su", "/system/xbin/su", "/debug_ramdisk/su", "/sbin/su",
        "magisk su", "/data/adb/ksu/bin/su", "/data/adb/ap/bin/su", "/system/sbin/su", "/vendor/bin/su", "/su/bin/su"
    )

    private val suBuilders: List<(String) -> Array<String>> = listOf(
        { c -> arrayOf("su", "-c", c) },
        { c -> arrayOf("su", "0", "sh", "-c", c) },
        { c -> arrayOf("/system/bin/su", "-c", c) },
        { c -> arrayOf("/system/xbin/su", "-c", c) },
        { c -> arrayOf("/debug_ramdisk/su", "-c", c) },
        { c -> arrayOf("/sbin/su", "-c", c) },
        { c -> arrayOf("magisk", "su", "-c", c) },
        { c -> arrayOf("/data/adb/ksu/bin/su", "-c", c) },
        { c -> arrayOf("/data/adb/ap/bin/su", "-c", c) },
        { c -> arrayOf("/system/sbin/su", "-c", c) },
        { c -> arrayOf("/vendor/bin/su", "-c", c) },
        { c -> arrayOf("/su/bin/su", "-c", c) }
    )

    fun suName(): String = if (suVariant < 0) "sin detectar" else suNames[suVariant]

    @Synchronized
    fun suCmd(cmd: String): Array<String> {
        if (suVariant < 0) detect()
        return suBuilders[suVariant](cmd)
    }

    private fun detect() {
        for (i in suBuilders.indices) {
            try {
                val p = Runtime.getRuntime().exec(suBuilders[i]("id"))
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor()
                if (out.contains("uid=0")) {
                    suVariant = i
                    return
                }
            } catch (e: Exception) {
            }
        }
        suVariant = 0
    }

    private fun bad(r: Pair<Int, String>): Boolean {
        val o = r.second
        return r.first != 0 || o.contains("Exception") || o.contains("Error:") || o.contains("not found") ||
            o.contains("Unknown command") || o.contains("Permission denied") || o.contains("inaccessible")
    }

    private fun stat(ctx: Context) = ctx.getSharedPreferences("adapt_stats", Context.MODE_PRIVATE)

    private fun score(ctx: Context, feature: String, name: String): Int {
        val p = stat(ctx)
        return p.getInt(feature + "|" + name + "|ok", 0) * 2 - p.getInt(feature + "|" + name + "|bad", 0)
    }

    fun attempt(ctx: Context, feature: String, steps: List<Step>, verify: (() -> Boolean)?): String {
        val prefs = ctx.getSharedPreferences("adapt", Context.MODE_PRIVATE)
        val st = stat(ctx)
        val pref = prefs.getString(feature, "")
        val ordered = steps.sortedWith(
            compareByDescending<Step> { score(ctx, feature, it.name) }.thenBy { if (it.name == pref) 0 else 1 }
        )
        val fails = StringBuilder()
        for (s in ordered) {
            val r = Root.su(s.cmd)
            val ok = if (verify != null) verify() else !bad(r)
            val key = feature + "|" + s.name
            if (ok) {
                st.edit().putInt(key + "|ok", st.getInt(key + "|ok", 0) + 1).apply()
                prefs.edit().putString(feature, s.name).apply()
                val extra = if (fails.isEmpty()) "" else "\n(descartados antes:" + fails + ")"
                return "✓ " + feature + " · método «" + s.name + "»" + extra
            }
            st.edit().putInt(key + "|bad", st.getInt(key + "|bad", 0) + 1).apply()
            fails.append("\n  ✗ ").append(s.name).append(": ").append(r.second.lines().firstOrNull() ?: "")
        }
        return "✗ " + feature + ": ningún método funcionó" + fails
    }

    fun methodsTable(ctx: Context): String {
        val st = stat(ctx)
        val feats = HashMap<String, ArrayList<String>>()
        for (k in st.all.keys) {
            val p = k.split("|")
            if (p.size == 3) {
                val l = feats.getOrPut(p[0]) { ArrayList() }
                if (!l.contains(p[1])) l.add(p[1])
            }
        }
        val sb = StringBuilder()
        for ((f, ms) in feats.toSortedMap()) {
            sb.append(f).append(":\n")
            for (m in ms) {
                sb.append("  ").append(m).append("  ✓").append(st.getInt(f + "|" + m + "|ok", 0))
                    .append(" ✗").append(st.getInt(f + "|" + m + "|bad", 0)).append('\n')
            }
        }
        return sb.toString().trim()
    }

    fun resetLearning(ctx: Context) {
        ctx.getSharedPreferences("adapt", Context.MODE_PRIVATE).edit().clear().apply()
        stat(ctx).edit().clear().apply()
    }

    fun async(act: Activity, report: (String) -> Unit, work: () -> String) {
        Thread {
            val msg = try {
                work()
            } catch (e: Exception) {
                "Error: " + e.message
            }
            act.runOnUiThread { report(msg) }
        }.start()
    }

    fun pidOf(name: String): String = Root.su("pidof " + name).second.trim()

    fun listOverlays(): String {
        for (c in listOf("cmd overlay list --user 0", "cmd overlay list", "om list")) {
            val r = Root.su(c)
            if (r.first == 0 && r.second.isNotEmpty()) return r.second
        }
        return ""
    }

    fun overlayEnabled(pkg: String): Boolean {
        for (l in listOverlays().lines()) {
            val t = l.trim()
            if (t.startsWith("[x]") && t.substring(3).trim() == pkg) return true
        }
        return false
    }

    fun findResetprop(): String {
        val r = Root.su("command -v resetprop || ls /data/adb/magisk/resetprop /data/adb/ksu/bin/resetprop /data/adb/ap/bin/resetprop 2>/dev/null | head -n 1")
        return if (r.first == 0) (r.second.lines().firstOrNull() ?: "").trim() else ""
    }
}

object Actions {
    fun color(act: Activity, json: String, report: (String) -> Unit) {
        val q = Root.q(json)
        val key = "theme_customization_overlay_packages"
        val steps = listOf(
            Adapt.Step("settings put", "settings put secure " + key + " " + q),
            Adapt.Step("settings usuario 0", "settings --user 0 put secure " + key + " " + q),
            Adapt.Step("cmd settings", "cmd settings put secure " + key + " " + q),
            Adapt.Step("content insert", "content insert --uri content://settings/secure --bind name:s:" + key + " --bind value:s:" + q),
            Adapt.Step("content update", "content update --uri content://settings/secure --bind value:s:" + q + " --where \"name='" + key + "'\"")
        )
        Adapt.async(act, report) {
            val res = Adapt.attempt(act, "color", steps) {
                Root.su("settings get secure " + key).second.trim() == json
            }
            if (res.startsWith("✓")) res + "\nSi los colores no cambian en unos segundos, tu ROM ignora este ajuste." else res
        }
    }

    fun night(act: Activity, mode: String, report: (String) -> Unit) {
        val v = if (mode == "yes") "2" else if (mode == "no") "1" else "0"
        val steps = listOf(
            Adapt.Step("cmd uimode", "cmd uimode night " + mode),
            Adapt.Step("ajuste ui_night_mode", "settings put secure ui_night_mode " + v),
            Adapt.Step("ajuste usuario 0", "settings --user 0 put secure ui_night_mode " + v),
            Adapt.Step("cmd settings", "cmd settings put secure ui_night_mode " + v)
        )
        Adapt.async(act, report) {
            Adapt.attempt(act, "modo", steps) {
                Root.su("cmd uimode night").second.trim().endsWith(mode) ||
                    Root.su("settings get secure ui_night_mode").second.trim() == v
            }
        }
    }

    fun overlay(act: Activity, pkg: String, on: Boolean, report: (String) -> Unit) {
        val pk = Root.q(pkg)
        val steps = if (on) listOf(
            Adapt.Step("enable-exclusive", "cmd overlay enable-exclusive --user 0 --category " + pk),
            Adapt.Step("enable", "cmd overlay enable --user 0 " + pk),
            Adapt.Step("enable-exclusive sin categoría", "cmd overlay enable-exclusive --user 0 " + pk),
            Adapt.Step("enable sin usuario", "cmd overlay enable " + pk),
            Adapt.Step("enable usuario actual", "cmd overlay enable --user current " + pk),
            Adapt.Step("enable todos", "cmd overlay enable --user all " + pk),
            Adapt.Step("om enable", "om enable " + pk)
        ) else listOf(
            Adapt.Step("disable", "cmd overlay disable --user 0 " + pk),
            Adapt.Step("disable sin usuario", "cmd overlay disable " + pk),
            Adapt.Step("disable usuario actual", "cmd overlay disable --user current " + pk),
            Adapt.Step("disable todos", "cmd overlay disable --user all " + pk),
            Adapt.Step("om disable", "om disable " + pk)
        )
        Adapt.async(act, report) {
            Adapt.attempt(act, if (on) "overlay-on" else "overlay-off", steps) { Adapt.overlayEnabled(pkg) == on }
        }
    }

    fun anim(act: Activity, f: Float, report: (String) -> Unit) {
        val keys = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        val a = keys.joinToString("; ") { "settings put global " + it + " " + f }
        val b = keys.joinToString("; ") { "cmd settings put global " + it + " " + f }
        val c = keys.joinToString("; ") { "settings --user 0 put global " + it + " " + f }
        val d = keys.joinToString("; ") {
            "content update --uri content://settings/global --bind value:s:" + f + " --where \"name='" + it + "'\""
        }
        val steps = listOf(
            Adapt.Step("settings put", a), Adapt.Step("cmd settings", b),
            Adapt.Step("settings usuario 0", c), Adapt.Step("content update", d)
        )
        Adapt.async(act, report) {
            Adapt.attempt(act, "animaciones", steps) {
                val g = Root.su("settings get global window_animation_scale").second.trim().toFloatOrNull()
                g != null && Math.abs(g - f) < 0.01f
            }
        }
    }

    fun dpi(act: Activity, n: Int, report: (String) -> Unit) {
        val steps = listOf(
            Adapt.Step("wm density", "wm density " + n),
            Adapt.Step("wm density pantalla 0", "wm density " + n + " -d 0"),
            Adapt.Step("display_density_forced", "settings put secure display_density_forced " + n)
        )
        Adapt.async(act, report) {
            Adapt.attempt(act, "dpi", steps) { Root.su("wm density").second.contains(n.toString()) }
        }
    }

    fun dpiReset(act: Activity, report: (String) -> Unit) {
        val steps = listOf(
            Adapt.Step("wm density reset", "wm density reset"),
            Adapt.Step("wm density reset pantalla 0", "wm density reset -d 0"),
            Adapt.Step("borrar display_density_forced", "settings delete secure display_density_forced")
        )
        Adapt.async(act, report) {
            Adapt.attempt(act, "dpi-reset", steps) { !Root.su("wm density").second.contains("Override density") }
        }
    }

    fun prop(act: Activity, k: String, v: String, report: (String) -> Unit) {
        val kk = Root.q(k)
        val vv = Root.q(v)
        Adapt.async(act, report) {
            val rp = Adapt.findResetprop()
            val steps = ArrayList<Adapt.Step>()
            if (rp.isNotEmpty()) {
                steps.add(Adapt.Step("resetprop", rp + " " + kk + " " + vv))
                steps.add(Adapt.Step("resetprop -n", rp + " -n " + kk + " " + vv))
            }
            steps.add(Adapt.Step("magisk resetprop", "magisk resetprop " + kk + " " + vv))
            steps.add(Adapt.Step("setprop", "setprop " + kk + " " + vv))
            var res = Adapt.attempt(act, "propiedad", steps) { Root.prop(k) == v }
            if (res.startsWith("✗")) {
                val dir = "/data/adb/modules/romtweaker_props"
                val m = Root.su(
                    "mkdir -p " + dir + " && ([ -f " + dir + "/module.prop ] || printf 'id=romtweaker_props\\nname=RomTweaker props\\nversion=1\\nversionCode=1\\nauthor=RomTweaker\\ndescription=Propiedades de RomTweaker\\n' > " + dir + "/module.prop)" +
                        " && (grep -v " + Root.q("^" + k + "=") + " " + dir + "/system.prop > " + dir + "/system.prop.tmp 2>/dev/null; true)" +
                        " && echo " + Root.q(k + "=" + v) + " >> " + dir + "/system.prop.tmp && mv " + dir + "/system.prop.tmp " + dir + "/system.prop"
                )
                res += if (m.first == 0) "\nGuardada en un módulo de KernelSU para el próximo arranque. Reinicia el teléfono."
                else "\n✗ Tampoco se pudo guardar en módulo: " + m.second
                if (k.startsWith("ro.") && m.first != 0) res += "\nLas ro.* solo se cambian con resetprop o editando build.prop."
            }
            res
        }
    }

    fun restartSystemUi(act: Activity, report: (String) -> Unit) {
        val steps = listOf(
            Adapt.Step("killall", "killall com.android.systemui"),
            Adapt.Step("pkill", "pkill -f com.android.systemui"),
            Adapt.Step("kill pid", "kill \$(pidof com.android.systemui)"),
            Adapt.Step("am force-stop", "am force-stop com.android.systemui"),
            Adapt.Step("am crash", "am crash com.android.systemui")
        )
        Adapt.async(act, report) {
            val before = Adapt.pidOf("com.android.systemui")
            Adapt.attempt(act, "reiniciar-systemui", steps) {
                Thread.sleep(1800)
                val now = Adapt.pidOf("com.android.systemui")
                now.isNotEmpty() && now != before
            }
        }
    }

    fun reboot(act: Activity, report: (String) -> Unit) {
        val steps = listOf(
            Adapt.Step("svc power reboot", "svc power reboot"),
            Adapt.Step("reboot", "reboot"),
            Adapt.Step("broadcast REBOOT", "am broadcast -a android.intent.action.REBOOT")
        )
        Adapt.async(act, report) { Adapt.attempt(act, "reiniciar-telefono", steps, null) }
    }

    fun diagCard(act: Activity, ui: Ui): LinearLayout {
        val c = ui.card()
        c.addView(ui.heading("Compatibilidad con tu ROM"))
        val out = ui.body("La app prueba varios métodos y recuerda el que funciona en tu ROM. Pulsa para ver qué hay disponible.")
        c.addView(out)
        c.addView(ui.button("Probar compatibilidad", true) {
            out.text = "Probando…"
            Thread {
                val rep = Diag.run(act)
                act.runOnUiThread { out.text = rep }
            }.start()
        })
        return c
    }
}

object Diag {
    fun run(ctx: Context): String {
        val sb = StringBuilder()
        fun line(ok: Boolean, t: String) {
            sb.append(if (ok) "✓ " else "✗ ").append(t).append('\n')
        }
        val id = Root.su("id")
        line(id.first == 0 && id.second.contains("uid=0"), "Root (" + Adapt.suName() + ")")
        val se = Root.su("getenforce").second.trim()
        line(se.isNotEmpty(), "SELinux: " + (if (se.isEmpty()) "?" else se))

        val w = Adapt.attempt(
            ctx, "escritura-ajustes",
            listOf(
                Adapt.Step("settings put", "settings put global rt_adapt_test 1"),
                Adapt.Step("cmd settings", "cmd settings put global rt_adapt_test 1"),
                Adapt.Step("settings usuario 0", "settings --user 0 put global rt_adapt_test 1")
            )
        ) { Root.su("settings get global rt_adapt_test").second.trim() == "1" }
        Root.su("settings delete global rt_adapt_test")
        sb.append(w.lines().first()).append('\n')

        val lst = Adapt.listOverlays()
        val n = lst.lines().count { it.trim().startsWith("[") }
        line(lst.isNotEmpty(), "Overlays: " + (if (lst.isEmpty()) "no se pudo listar" else n.toString() + " encontrados"))
        line(lst.contains("com.android.systemui:accent"), "Colores dinámicos (Monet) presentes")
        val fab = Root.su("cmd overlay help 2>&1 | grep -c fabricate").second.trim()
        line(fab != "" && fab != "0", "Overlays fabricados (cmd overlay fabricate)")
        line(Root.su("test -d /data/adb/modules && echo ok").second.contains("ok"), "Módulos KernelSU (/data/adb/modules)")

        val d = Root.su("wm density")
        line(d.first == 0 && d.second.contains("density"), "DPI (wm density)")
        val u = Root.su("cmd uimode night")
        line(u.first == 0 && u.second.contains(":"), "Modo claro/oscuro (cmd uimode)")
        val t = Root.su("settings get secure theme_customization_overlay_packages")
        line(t.first == 0, "Ajuste de tema del sistema")
        val rp = Adapt.findResetprop()
        line(rp.isNotEmpty(), "resetprop: " + (if (rp.isEmpty()) "no encontrado (las ro.* no son editables)" else rp))
        line(Root.su("ls /data/local/tmp/rt_res 2>/dev/null | head -n 1").second.isNotEmpty(), "Volcado de recursos (explorador)")
        line(android.provider.Settings.canDrawOverlays(ctx), "Permiso de superposición (centro de control)")

        val learned = ctx.getSharedPreferences("adapt", Context.MODE_PRIVATE).all.entries
        if (learned.isNotEmpty()) {
            sb.append("\nMétodos aprendidos:\n")
            for (e in learned) sb.append("· ").append(e.key).append(" → ").append(e.value.toString()).append('\n')
        }
        return sb.toString().trim()
    }
}
