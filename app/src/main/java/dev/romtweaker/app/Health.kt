package dev.romtweaker.app

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.io.File
import java.util.Date

/**
 * Comprobaciones de salud de la app y generación del informe de diagnóstico.
 * El informe contiene datos del equipo y de la ROM, el estado de cada pieza, ajustes no personales, los registros
 * de la app y del módulo y una instantánea de la estructura de la pantalla de bloqueo (sin textos personales).
 * Nunca incluye claves, mensajes, textos de notificaciones ni datos de cuentas.
 */
object Health {
    /** Carpeta de datos de SystemUI donde el módulo guarda su registro y la instantánea (solo se lee con root). */
    const val DE = "/data/user_de/0/com.android.systemui"

    /** state: 0 = bien, 1 = aviso, 2 = fallo */
    class Check(val title: String, val state: Int, val detail: String, val fix: String = "")

    fun ago(ms: Long): String {
        if (ms <= 0L) return "nunca"
        val s = (System.currentTimeMillis() - ms) / 1000L
        return when {
            s < 90L -> "hace $s s"
            s < 5400L -> "hace " + (s / 60L) + " min"
            s < 172800L -> "hace " + (s / 3600L) + " h"
            else -> "hace " + (s / 86400L) + " días"
        }
    }

    private fun rootManager(ctx: Context): String {
        val l = listOf(
            "me.weishu.kernelsu" to "KernelSU", "com.rifsxd.ksunext" to "KernelSU Next", "com.sukisu.ultra" to "SukiSU Ultra",
            "com.topjohnwu.magisk" to "Magisk", "io.github.vvb2060.magisk" to "Magisk Alpha", "me.bmax.apatch" to "APatch"
        )
        for ((p, n) in l) {
            try {
                ctx.packageManager.getPackageInfo(p, 0)
                return n
            } catch (e: Throwable) { /* no está */ }
        }
        return ""
    }

    /** Ejecuta todas las comprobaciones. Usa root: llamar siempre desde un hilo de fondo. */
    fun run(ctx: Context): List<Check> {
        val out = ArrayList<Check>()
        RtLog.i("salud", "comprobando el estado de la app")

        val id = Sx.su("id")
        val rootOk = id.second.contains("uid=0")
        val mgr = rootManager(ctx)
        out.add(
            if (rootOk) Check("Root", 0, "Concedido" + (if (mgr.isNotEmpty()) " · " + mgr else ""))
            else Check("Root", 2, "No disponible: " + id.second.take(90).ifEmpty { "sin respuesta" },
                "Abre tu gestor de root (KernelSU, Magisk o APatch) y concede permiso a RomTweaker.")
        )

        val lp = Lsp.installedPkg(ctx)
        out.add(
            if (lp != null) Check("Gestor de LSPosed", 0, lp)
            else Check("Gestor de LSPosed", 1, "No lo veo como app instalada (puede ser un gestor integrado).", "Usa «Abrir LSPosed» en Herramientas.")
        )

        val st = Xp.status()
        out.add(
            if (!Xp.included()) Check("Módulo en esta compilación", 2, st)
            else if (st.startsWith("Conectado")) Check("Conexión con LSPosed", 0, st)
            else Check("Conexión con LSPosed", 1, st, "Activa RomTweaker en LSPosed (ámbito: Interfaz del sistema), reinicia SystemUI y abre la app otra vez.")
        )

        val ml = ctx.getSharedPreferences("modlog", Context.MODE_PRIVATE)
        val ht = ml.getLong("hello_t", 0L)
        val mv = ml.getInt("mv", 0)
        out.add(
            when {
                ht == 0L -> Check("Módulo en SystemUI", 1, "Todavía no ha reportado nada.",
                    "Reinicia SystemUI (Herramientas). Si sigue igual, activa el módulo en LSPosed con el ámbito «Interfaz del sistema».")
                mv < ModVer.MV -> Check("Módulo en SystemUI", 1, "Se cargó una versión antigua (v" + mv + ", esperada v" + ModVer.MV + ").", "Reinicia SystemUI para cargar la nueva.")
                else -> Check("Módulo en SystemUI", 0, "Activo · reportó " + ago(ht) + " · " + (ml.getString("hello_msg", "") ?: ""))
            }
        )

        if (rootOk) {
            val bt = Sx.su("cat $DE/rt_boot.txt 2>/dev/null").second
            val now = System.currentTimeMillis()
            var until = 0L
            for (l in bt.lines()) {
                if (!l.startsWith("U")) continue
                val v = l.drop(1).trim().toLongOrNull() ?: continue
                if (v > now && v - now < 11 * 60000L) until = maxOf(until, v)
            }
            if (until > 0L) out.add(
                Check("Modo seguro", 2, "El módulo está en pausa " + ((until - now) / 60000L + 1L) + " min más: SystemUI se reinició varias veces seguidas.",
                    "Pulsa «Salir del modo seguro» cuando quieras reactivarlo.")
            )
        }

        val ms = ClockMetrics.status(ctx)
        val msOk = ms.startsWith("Medidas reales del reloj recibidas")
        out.add(Check("Medidas del reloj real", if (msOk) 0 else 1, ms.take(160), if (msOk) "" else "En Bloqueo pulsa «Recalibrar»."))

        out.add(
            if (Settings.canDrawOverlays(ctx)) Check("Mostrar sobre otras apps", 0, "Concedido")
            else Check("Mostrar sobre otras apps", 1, "Sin permiso: el panel flotante no se verá.", "Ajustes > Apps > RomTweaker > Mostrar sobre otras apps.")
        )
        if (Build.VERSION.SDK_INT >= 33) {
            out.add(
                if (ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) Check("Notificaciones", 0, "Concedido")
                else Check("Notificaciones", 1, "Sin permiso.", "Ajustes > Apps > RomTweaker > Notificaciones.")
            )
        }
        val pm = ctx.getSystemService(PowerManager::class.java)
        out.add(
            if (pm != null && pm.isIgnoringBatteryOptimizations(ctx.packageName)) Check("Batería sin restricciones", 0, "Concedido")
            else Check("Batería sin restricciones", 1, "El sistema podría cerrar los servicios de RomTweaker.", "Ajustes > Apps > RomTweaker > Batería > Sin restricciones.")
        )

        val bg = File(ctx.filesDir, "prev_bg.jpg")
        out.add(
            if (bg.exists()) Check("Fondo de la vista previa", 0, "Capturado " + ago(bg.lastModified()))
            else Check("Fondo de la vista previa", 0, "Aún sin capturar: se usa el fondo del sistema.")
        )

        val ch = CrashLog.head(ctx)
        if (ch.isNotEmpty()) {
            val ct = CrashLog.time(ctx)
            val recent = System.currentTimeMillis() - ct < 7L * 86400000L
            out.add(Check("Último cierre inesperado", if (recent) 1 else 0, ago(ct) + " · " + ch, "Va incluido en el informe."))
        } else {
            out.add(Check("Cierres inesperados", 0, "Ninguno registrado"))
        }

        val w = ml.getInt("warns", 0)
        val er = ml.getInt("errs", 0)
        out.add(
            if (er > 0) Check("Avisos del módulo", 1, er.toString() + " errores y " + w + " avisos desde la última limpieza (van en el informe).")
            else if (w > 0) Check("Avisos del módulo", 1, w.toString() + " avisos (van en el informe).")
            else Check("Avisos del módulo", 0, "Sin avisos")
        )
        RtLog.i("salud", "resultado: " + out.joinToString(" | ") { it.title + "=" + it.state })
        return out
    }

    private fun mark(c: Check): String = when (c.state) { 0 -> "[OK]    "; 1 -> "[AVISO] "; else -> "[FALLO] " }

    private fun appVersion(ctx: Context): String = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        pi.versionName + " (código " + pi.longVersionCode + ")"
    } catch (e: Throwable) { "?" }

    /** Resumen corto (para el cuerpo de un Issue o un mensaje). */
    fun summary(ctx: Context, checks: List<Check>): String {
        val sb = StringBuilder()
        sb.append("RomTweaker ").append(appVersion(ctx)).append(" · módulo v").append(ModVer.MV).append('\n')
        sb.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" (").append(Build.DEVICE).append(") · Android ")
            .append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("Build: ").append(Build.DISPLAY).append('\n')
        for (c in checks) if (c.state != 0) sb.append(mark(c)).append(c.title).append(": ").append(c.detail).append('\n')
        if (checks.all { it.state == 0 }) sb.append("Todas las comprobaciones salieron bien.\n")
        return sb.toString().trim()
    }

    private val hide = Regex("(?i)(key|token|secret|pass|clave|api)")

    private fun prefsDump(ctx: Context): String {
        val sb = StringBuilder()
        for (n in listOf("lock", "hub", "ui", "ico", "adapt", "backup")) {
            try {
                val all = ctx.getSharedPreferences(n, Context.MODE_PRIVATE).all
                if (all.isEmpty()) continue
                sb.append('[').append(n).append("]\n")
                for ((k, v) in all.toSortedMap()) {
                    if (hide.containsMatchIn(k)) { sb.append("  ").append(k).append(" = (oculto)\n"); continue }
                    val t = v?.toString() ?: "null"
                    sb.append("  ").append(k).append(" = ").append(if (t.length <= 60) t.replace('\n', ' ') else "<texto de " + t.length + " caracteres>").append('\n')
                }
            } catch (e: Throwable) { RtLog.swallow(e) }
        }
        return sb.toString()
    }

    private fun section(sb: StringBuilder, title: String) {
        sb.append("\n\n===== ").append(title).append(" =====\n")
    }

    /**
     * Informe completo. Con full=false es la versión corta (para pegar en un Issue: unos 40 KB).
     * Usa root y puede tardar unos segundos: llamar desde un hilo de fondo.
     */
    fun report(ctx: Context, checks: List<Check>, full: Boolean): String {
        val sb = StringBuilder(96 * 1024)
        sb.append("RomTweaker · informe de diagnóstico\n")
        sb.append("Generado: ").append(Date()).append('\n')
        sb.append("Versión de la app: ").append(appVersion(ctx)).append(" · repo ").append(AppInfo.REPO).append(" · módulo v").append(ModVer.MV).append('\n')
        sb.append("Tipo de informe: ").append(if (full) "completo" else "corto").append('\n')
        sb.append("Cómo leerlo: [OK]/[AVISO]/[FALLO] son comprobaciones; los registros llevan fecha, nivel (D detalle, I info, W aviso, E error, S error ignorado a propósito), etiqueta y mensaje.\n")

        section(sb, "EQUIPO Y ROM")
        try { sb.append(RomInfo.summary()) } catch (e: Throwable) { sb.append("(no pude leer la ROM: ").append(e).append(')') }
        val dm = ctx.resources.displayMetrics
        sb.append("\nPantalla: ").append(dm.widthPixels).append('x').append(dm.heightPixels).append(" dpi=").append(dm.densityDpi)
        sb.append("\nIdioma: ").append(java.util.Locale.getDefault().toLanguageTag())
        sb.append("\nSu detectado: ").append(Adapt.suName())
        try {
            val si = ctx.packageManager.getPackageInfo("com.android.systemui", 0)
            sb.append("\nSystemUI: ").append(si.versionName).append(" (código ").append(si.longVersionCode).append(')')
        } catch (e: Throwable) { sb.append("\nSystemUI: no visible (").append(e.javaClass.simpleName).append(')') }

        section(sb, "COMPROBACIONES")
        for (c in checks) {
            sb.append(mark(c)).append(c.title).append(" — ").append(c.detail).append('\n')
            if (c.fix.isNotEmpty() && c.state != 0) sb.append("          qué hacer: ").append(c.fix).append('\n')
        }

        if (full) {
            section(sb, "COMPATIBILIDAD CON TU SYSTEMUI")
            try {
                for (f in SysScan.features(ctx)) sb.append(if (f.found) "✓ " else "✗ ").append(f.label).append(" · ").append(if (f.found) f.support else "no existe en esta ROM").append('\n')
            } catch (e: Throwable) { sb.append("(análisis no disponible: ").append(e).append(")\n") }
        }

        section(sb, "AJUSTES (sin datos personales; textos largos solo con su tamaño)")
        sb.append(prefsDump(ctx))

        section(sb, "EVENTOS RECIENTES DEL MÓDULO (SystemUI → app)")
        val evs = ModuleEventReceiver.events(ctx)
        if (evs.isEmpty()) sb.append("(ninguno)\n")
        for (e in evs.takeLast(if (full) 40 else 15)) sb.append(ago(e[0].toLongOrNull() ?: 0L)).append(" · ").append(e[1]).append('/').append(e[2]).append(": ").append(e[3]).append('\n')

        val appLimit = if (full) 260_000 else 22_000
        val modLimit = if (full) 200_000 else 14_000

        section(sb, "REGISTRO DEL MÓDULO DENTRO DE SYSTEMUI (rt_module.log)")
        val ml = Sx.su("tail -c " + modLimit + " " + DE + "/rt_module.log 2>/dev/null").second
        sb.append(if (ml.isBlank()) "(vacío o sin acceso: sin root no se puede leer)\n" else ml + "\n")

        section(sb, "INSTANTÁNEA DE LA PANTALLA DE BLOQUEO (estructura, sin textos personales)")
        val sn = Sx.su("cat " + DE + "/rt_snap.txt 2>/dev/null").second
        sb.append(if (sn.isBlank()) "(todavía no hay: pulsa «Capturar estado del bloqueo» y vuelve a generar el informe)\n" else sn.take(if (full) 90_000 else 8_000) + "\n")

        section(sb, "ARRANQUES DE SYSTEMUI (modo seguro)")
        val bt = Sx.su("cat " + DE + "/rt_boot.txt 2>/dev/null").second
        sb.append(if (bt.isBlank()) "(sin datos)\n" else bt + "\n")

        section(sb, "CIERRES INESPERADOS DE LA APP")
        val cf = CrashLog.files(ctx)
        if (cf.isEmpty()) sb.append("(ninguno)\n")
        for (f in cf.take(if (full) 3 else 1)) {
            sb.append("--- ").append(f.name).append(" ---\n")
            sb.append(try { f.readText().take(if (full) 40_000 else 9_000) } catch (e: Throwable) { "(no se pudo leer)" }).append('\n')
        }

        section(sb, "REGISTRO DE LA APP (rt.log)")
        sb.append(RtLog.readTail(ctx, appLimit)).append('\n')

        if (full) {
            section(sb, "LOGCAT FILTRADO (RomTweaker, LSPosed/Vector, cierres del sistema)")
            val lc = Sx.su("logcat -d -b main,system,crash -v threadtime -t 6000 2>/dev/null | grep -E 'RomTweaker|AndroidRuntime|FATAL|dev\\.romtweaker|LSPosed|Vector|Xposed|libxposed|SystemUI.*(Exception|died|crash|Fatal)' | tail -n 700").second
            sb.append(if (lc.isBlank()) "(vacío o sin acceso)\n" else lc.take(160_000) + "\n")
        }
        val r = RtLog.redact(sb.toString())
        RtLog.i("salud", "informe " + (if (full) "completo" else "corto") + " generado: " + r.length + " caracteres")
        return r
    }

    /** Crea el .zip para compartir: informe + registros en bruto de la app y del módulo. */
    fun zip(ctx: Context, report: String): File? {
        return try {
            val dir = File(ctx.cacheDir, "share")
            dir.mkdirs()
            dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 6L * 3600000L) it.delete() }
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(Date())
            val f = File(dir, "RomTweaker-informe-" + stamp + ".zip")
            RtLog.sync()
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(f)).use { z ->
                fun put(name: String, data: ByteArray) {
                    z.putNextEntry(java.util.zip.ZipEntry(name))
                    z.write(data)
                    z.closeEntry()
                }
                put("informe.txt", report.toByteArray(Charsets.UTF_8))
                for (lf in RtLog.files(ctx)) put("app/" + lf.name, lf.readBytes())
                for (cf in CrashLog.files(ctx)) put("app/" + cf.name, cf.readBytes())
                val mb = Rx.bytes("cat " + DE + "/rt_module.log " + DE + "/rt_module.log.1 2>/dev/null", 20)
                if (mb != null && mb.isNotEmpty()) put("modulo/rt_module.log", RtLog.redact(String(mb, Charsets.UTF_8)).toByteArray(Charsets.UTF_8))
                val sb2 = Rx.bytes("cat " + DE + "/rt_snap.txt 2>/dev/null", 10)
                if (sb2 != null && sb2.isNotEmpty()) put("modulo/rt_snap.txt", sb2)
            }
            RtLog.i("salud", "zip creado: " + f.name + " (" + f.length() / 1024 + " KB)")
            f
        } catch (e: Throwable) {
            RtLog.w("salud", "no pude crear el zip", e)
            null
        }
    }

    /** Dirección para abrir un Issue en GitHub con el resumen ya escrito (el cuerpo va limitado: el informe completo se adjunta aparte). */
    fun issueUrl(ctx: Context, checks: List<Check>): String {
        if (!AppInfo.repoOk) return ""
        val sb = StringBuilder()
        sb.append("## ¿Qué pasó?\n(Cuéntalo con tus palabras y pega una captura si puedes)\n\n")
        sb.append("## ¿Qué esperabas que pasara?\n\n")
        sb.append("## Resumen automático\n```\n").append(summary(ctx, checks).take(1300)).append("\n```\n\n")
        sb.append("> Adjunta aquí el .zip de «Enviar informe» (Salud y reportes), o pega el informe copiado, para poder resolverlo sin más preguntas.\n")
        return "https://github.com/" + AppInfo.REPO + "/issues/new?labels=bug&title=" + Uri.encode("[Fallo] ") + "&body=" + Uri.encode(sb.toString())
    }
}
