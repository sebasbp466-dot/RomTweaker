package dev.romtweaker.app

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

class SysFeature(val label: String, val found: Boolean, val support: String)

/** Mira dentro del SystemUI de ESTA ROM qué clases existen, para activar solo lo que es compatible. */
object SysScan {
    private val feats = listOf(
        Triple("Reloj de la barra de estado", "Lcom/android/systemui/statusbar/policy/Clock;", "soportado (formato propio)"),
        Triple("Reloj de bloqueo por plugins (Android 14+)", "Lcom/android/systemui/shared/clocks/ClockRegistry;", "en preparación"),
        Triple("Reloj de bloqueo clásico (Android 13 o menos)", "Lcom/android/keyguard/KeyguardClockSwitch;", "en preparación"),
        Triple("Cabecera del panel de ajustes", "Lcom/android/systemui/shade/ShadeHeaderController;", "en preparación"),
        Triple("Medidor de batería", "Lcom/android/systemui/battery/BatteryMeterViewController;", "en preparación"),
        Triple("Cuadrícula del panel rápido", "Lcom/android/systemui/qs/TileLayout;", "en preparación"),
        Triple("Paneles rápidos (QSPanel)", "Lcom/android/systemui/qs/QSPanel;", "en preparación")
    )

    private fun indexOf(h: ByteArray, n: ByteArray): Int {
        val f = n[0]
        val last = h.size - n.size
        var i = 0
        while (i <= last) {
            if (h[i] == f) {
                var j = 1
                while (j < n.size && h[i + j] == n[j]) j++
                if (j == n.size) return i
            }
            i++
        }
        return -1
    }

    private fun scanFile(path: String, needles: List<String>): Set<String> {
        val found = HashSet<String>()
        try {
            ZipFile(path).use { z ->
                val e = z.entries()
                while (e.hasMoreElements()) {
                    val en = e.nextElement()
                    if (!en.name.startsWith("classes") || !en.name.endsWith(".dex")) continue
                    val data = z.getInputStream(en).readBytes()
                    for (nd in needles) {
                        if (!found.contains(nd) && indexOf(data, nd.toByteArray(Charsets.UTF_8)) >= 0) found.add(nd)
                    }
                }
            }
        } catch (e: Throwable) {
        }
        return found
    }

    fun features(ctx: Context): List<SysFeature> {
        val needles = feats.map { it.second }
        var found: Set<String> = emptySet()
        val out = Sx.su("pm path com.android.systemui").second
        var path = ""
        for (l in out.lines()) if (l.startsWith("package:")) { path = l.removePrefix("package:").trim(); break }
        if (path.isNotEmpty()) {
            found = scanFile(path, needles)
            if (found.isEmpty()) {
                val tmp = File(ctx.cacheDir, "sysui_scan.apk")
                Sx.su("cp '" + path + "' '" + tmp.absolutePath + "' && chmod 644 '" + tmp.absolutePath + "'")
                found = scanFile(tmp.absolutePath, needles)
                tmp.delete()
            }
        }
        val res = ArrayList<SysFeature>()
        for (f in feats) res.add(SysFeature(f.first, found.contains(f.second), f.third))
        return res
    }
}
