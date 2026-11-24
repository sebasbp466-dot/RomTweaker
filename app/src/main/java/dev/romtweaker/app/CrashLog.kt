package dev.romtweaker.app

import android.content.Context
import java.io.File
import java.util.Date

/**
 * Cierres inesperados de la app: se guardan los 3 últimos en filesDir/logs/crash_N.txt con la traza completa,
 * la memoria, el tiempo que llevaba abierta y las últimas líneas del registro. Además deja una marca "pendiente"
 * para preguntar al usuario, al abrir de nuevo, si quiere enviar el informe.
 */
object CrashLog {
    private const val KEEP = 3

    private fun dir(ctx: Context): File {
        val d = File(ctx.applicationContext.filesDir, "logs")
        d.mkdirs()
        return d
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("crash", Context.MODE_PRIVATE)

    fun record(ctx: Context, t: Thread, e: Throwable) {
        try {
            RtLog.add('E', "cierre", "La app se cerró por " + e.javaClass.name + ": " + e.message, e)
            val d = dir(ctx)
            for (i in KEEP - 1 downTo 1) {
                val a = File(d, "crash_" + (i - 1) + ".txt")
                if (a.exists()) a.renameTo(File(d, "crash_$i.txt"))
            }
            val rt = Runtime.getRuntime()
            val sb = StringBuilder()
            sb.append("===== CIERRE INESPERADO =====\n")
            sb.append("hora: ").append(Date()).append('\n')
            sb.append("hilo: ").append(t.name).append('\n')
            sb.append("la app llevaba abierta: ").append((System.currentTimeMillis() - RtLog.started()) / 1000).append(" s\n")
            sb.append("memoria usada: ").append((rt.totalMemory() - rt.freeMemory()) / 1048576)
                .append(" MB de ").append(rt.maxMemory() / 1048576).append(" MB\n")
            sb.append("--- traza ---\n").append(RtLog.trace(e, 90)).append('\n')
            sb.append("--- últimas líneas del registro ---\n").append(RtLog.tail(150)).append('\n')
            File(d, "crash_0.txt").writeText(RtLog.redact(sb.toString()))
            prefs(ctx).edit()
                .putBoolean("pending", true)
                .putLong("t", System.currentTimeMillis())
                .putString("head", e.javaClass.simpleName + ": " + (e.message ?: "").take(140))
                .commit()
        } catch (x: Throwable) { /* ya no se puede hacer más */ }
    }

    fun pending(ctx: Context): Boolean = try { prefs(ctx).getBoolean("pending", false) } catch (e: Throwable) { false }

    fun clearPending(ctx: Context) {
        try { prefs(ctx).edit().putBoolean("pending", false).apply() } catch (e: Throwable) { /* nada */ }
    }

    /** Primera línea del último fallo ("Clase: mensaje"), o vacío si no hay. */
    fun head(ctx: Context): String = try { if (files(ctx).isEmpty()) "" else (prefs(ctx).getString("head", "") ?: "") } catch (e: Throwable) { "" }

    fun time(ctx: Context): Long = try { prefs(ctx).getLong("t", 0L) } catch (e: Throwable) { 0L }

    fun files(ctx: Context): List<File> {
        val d = dir(ctx)
        val l = ArrayList<File>()
        for (i in 0 until KEEP) {
            val f = File(d, "crash_$i.txt")
            if (f.exists()) l.add(f)
        }
        return l
    }

    fun clear(ctx: Context) {
        try {
            for (f in files(ctx)) f.delete()
            prefs(ctx).edit().clear().apply()
        } catch (e: Throwable) { /* nada */ }
    }
}
