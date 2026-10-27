package dev.romtweaker.app

import android.app.Activity
import android.content.Context
import java.io.File

object ShapeOps2 {
    private const val MOD = "/data/adb/modules/romtweaker_overlays"
    private const val TARGET = "android:string/config_icon_mask"

    private fun norm(s: String): String = s.replace(Regex("\\s+"), "")

    private fun effective(path: String): Boolean? {
        for (c in listOf(
            "cmd overlay lookup --user 0 android " + TARGET,
            "cmd overlay lookup android " + TARGET
        )) {
            val r = Root.su(c)
            if (r.first == 0 && r.second.isNotEmpty()) return norm(r.second).contains(norm(path))
        }
        return null
    }

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
        "L=\$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1 | cut -d/ -f1); " +
            "am force-stop \$L; rm -f /data/data/\$L/databases/app_icons.db* /data/user/0/\$L/databases/app_icons.db* 2>/dev/null; true"

    private fun fabCmd(path: String, type: String): String =
        "cmd overlay fabricate --target android --name rtshape " + TARGET + " " + type + " " + Root.q(path) +
            " && cmd overlay enable --user 0 \$(cmd overlay list --user 0 | grep -o '[A-Za-z0-9_.]*:rtshape' | head -n 1)"

    private fun installModule(ctx: Context): String {
        val shapes = ShapeLib.load(ctx)
        for (s in shapes) Root.su("pm uninstall " + ShapeLib.pkg(s.id) + " >/dev/null 2>&1; true")
        val dir = MOD + "/system/product/overlay"
        val prep = Root.su(
            "mkdir -p " + dir + " && printf 'id=romtweaker_overlays\\nname=RomTweaker overlays\\nversion=1\\nversionCode=1\\nauthor=RomTweaker\\ndescription=Overlays generados por RomTweaker\\n' > " + MOD + "/module.prop"
        )
        if (prep.first != 0) return "✗ No se pudo crear el módulo: " + prep.second
        var n = 0
        for (s in shapes) {
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
            val log = StringBuilder()
            val apk = extract(act, s.id)
            val present = Adapt.listOverlays().contains(pk)
            if (!present && apk == null) {
                return@async "✗ El overlay «" + s.label + "» no está dentro de la app."
            }
            if (!present && apk != null) {
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
                log.append(r.lines().first()).append('\n')
            }
            if (Adapt.listOverlays().contains(pk)) {
                val steps = listOf(
                    Adapt.Step("enable-exclusive", "cmd overlay enable-exclusive --user 0 --category " + pq),
                    Adapt.Step("enable", "cmd overlay enable --user 0 " + pq),
                    Adapt.Step("om enable", "om enable " + pq)
                )
                val en = Adapt.attempt(act, "overlay-on", steps) { Adapt.overlayEnabled(pk) }
                log.append(en.lines().first()).append('\n')
                if (en.startsWith("✓")) {
                    val eff = effective(s.path)
                    if (eff != false) {
                        Root.su(relaunch())
                        return@async log.toString() + (if (eff == null) "No pude comprobar si se aplica. " else "") +
                            "Forma «" + s.label + "» activa. Launcher reiniciado."
                    }
                    log.append("✗ Activo, pero Android no aplica el recurso (política del overlay).\n")
                }
            }
            val fab = Adapt.attempt(
                act, "overlay-fabricado",
                listOf(
                    Adapt.Step("fabricate string", fabCmd(s.path, "string")),
                    Adapt.Step("fabricate 0x03", fabCmd(s.path, "0x03"))
                )
            ) { effective(s.path) == true }
            log.append(fab.lines().first()).append('\n')
            if (fab.startsWith("✓")) {
                Root.su(relaunch())
                return@async log.toString() + "Forma «" + s.label + "» activa (overlay fabricado). Launcher reiniciado."
            }
            val modFile = MOD + "/system/product/overlay/" + s.id + ".apk"
            if (Root.su("test -f " + modFile).first == 0 && Adapt.listOverlays().contains(pk)) {
                return@async log.toString() +
                    "✗ El overlay ya está como módulo y activo, pero tu sistema o launcher no usa esa forma. Mándame el diagnóstico de Termux."
            }
            log.append("Pasando a instalación como módulo de KernelSU…\n")
            log.toString() + installModule(act)
        }
    }

    fun reset(act: Activity, report: (String) -> Unit) {
        Adapt.async(act, report) {
            val out = StringBuilder()
            for (l in Adapt.listOverlays().lines()) {
                val t = l.trim()
                if (t.startsWith("[x]")) {
                    val pk = t.substring(3).trim()
                    if (pk.startsWith(ShapeLib.PREFIX) || pk.endsWith(":rtshape")) {
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
