package dev.romtweaker.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Abre el gestor de LSPosed / Vector. Hay varias formas porque Android 11+ oculta paquetes ajenos
 * y el gestor "parásito" de LSPosed no tiene icono propio: se abre desde su notificación o con un código de marcador.
 */
object Lsp {
    val pkgs = listOf("org.lsposed.manager", "org.matrix.vector.manager", "org.matrix.vector", "io.github.lsposed.manager", "com.lsposed.manager")

    fun installedPkg(ctx: Context): String? {
        for (p in pkgs) {
            try {
                ctx.packageManager.getPackageInfo(p, 0)
                return p
            } catch (e: Throwable) { /* no está instalado */ }
        }
        return null
    }

    fun open(act: Activity, say: (String) -> Unit) {
        Thread {
            var done = ""
            try {
                // 1) lanzador normal, si el gestor es una app instalada
                for (p in pkgs) {
                    val li = act.packageManager.getLaunchIntentForPackage(p)
                    if (li != null) {
                        act.runOnUiThread { try { act.startActivity(li) } catch (e: Throwable) { RtLog.swallow(e) } }
                        done = p
                        break
                    }
                }
                // 2) gestor parásito: el mismo intento que usa la notificación de LSPosed
                if (done.isEmpty()) {
                    val r = Sx.su("am start -a android.intent.action.MAIN -c org.lsposed.manager.LAUNCH_MANAGER -n com.android.shell/.BugreportWarningActivity")
                    val bad = r.first != 0 || r.second.contains("Error") || r.second.contains("Exception")
                    RtLog.i("lsposed", "intento parásito → código " + r.first + " " + r.second.take(160))
                    if (!bad) done = "gestor integrado"
                }
                // 3) código secreto del marcador (*#*#5776733#*#*)
                if (done.isEmpty()) {
                    try { act.sendBroadcast(Intent("android.provider.Telephony.SECRET_CODE", Uri.parse("android_secret_code://5776733"))) } catch (e: Throwable) { RtLog.swallow(e) }
                    val r = Sx.su("am broadcast -a android.provider.Telephony.SECRET_CODE -d android_secret_code://5776733")
                    RtLog.i("lsposed", "código secreto → " + r.first + " " + r.second.take(120))
                    // 4) último recurso: monkey sobre los nombres conocidos
                    for (p in pkgs) {
                        val m = Sx.su("monkey -p " + p + " -c android.intent.category.LAUNCHER 1")
                        if (m.first == 0 && !m.second.contains("No activities found")) { done = p; break }
                    }
                }
            } catch (e: Throwable) { RtLog.w("lsposed", "no pude abrir el gestor", e) }
            val msg = if (done.isNotEmpty()) "Pedí abrir el gestor de LSPosed. Si no aparece, ábrelo desde su notificación o marca *#*#5776733#*#* en el teléfono."
            else "No pude abrirlo. Ábrelo desde la notificación de LSPosed o marca *#*#5776733#*#* en el teléfono."
            act.runOnUiThread { say(msg) }
        }.start()
    }
}
