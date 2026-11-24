package dev.romtweaker.app

object Root {
    fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    fun su(cmd: String): Pair<Int, String> {
        val t0 = System.currentTimeMillis()
        val r = su0(cmd)
        RtLog.cmd("root", cmd, r.first, r.second, System.currentTimeMillis() - t0)
        return r
    }

    private fun su0(cmd: String): Pair<Int, String> {
        return try {
            val p = Runtime.getRuntime().exec(Adapt.suCmd(cmd))
            val out = StringBuilder()
            val er = StringBuilder()
            val t1 = Thread { try { out.append(p.inputStream.bufferedReader().readText()) } catch (e: Exception) { RtLog.swallow(e) } }
            val t2 = Thread { try { er.append(p.errorStream.bufferedReader().readText()) } catch (e: Exception) { RtLog.swallow(e) } }
            t1.start(); t2.start()
            if (!p.waitFor(90, java.util.concurrent.TimeUnit.SECONDS)) {
                try { p.destroyForcibly() } catch (e: Exception) { RtLog.swallow(e) }
                t1.join(500); t2.join(500)
                Pair(-2, "tiempo agotado: " + cmd.take(60))
            } else {
                t1.join(3000); t2.join(3000)
                Pair(p.exitValue(), (out.toString() + er.toString()).trim())
            }
        } catch (e: Exception) {
            Pair(-1, e.message ?: "error")
        }
    }

    fun sh(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = StringBuilder()
            val t1 = Thread { try { out.append(p.inputStream.bufferedReader().readText()) } catch (e: Exception) { RtLog.swallow(e) } }
            val t2 = Thread { try { p.errorStream.readBytes() } catch (e: Exception) { RtLog.swallow(e) } }
            t1.start(); t2.start()
            if (!p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) { try { p.destroyForcibly() } catch (e: Exception) { RtLog.swallow(e) } }
            t1.join(1000)
            out.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun prop(k: String): String { val v = sh("getprop " + q(k)); return if (v.isNotEmpty()) v else su("getprop " + q(k)).second }; fun allProps(): String { val r = su("getprop"); if (r.first == 0) { if (r.second.isNotEmpty()) return r.second }; return sh("getprop") }
}

/** Ejecuta con root y devuelve la salida en bytes (por ejemplo screencap -p), con tiempo máximo. */
object Rx {
    fun bytes(cmd: String, tmoSec: Long = 20): ByteArray? {
        return try {
            val t0 = System.currentTimeMillis()
            val p = ProcessBuilder(*Adapt.suCmd(cmd)).start()
            var out: ByteArray? = null
            val t1 = Thread { try { out = p.inputStream.readBytes() } catch (e: Exception) { RtLog.swallow(e) } }
            val t2 = Thread { try { p.errorStream.readBytes() } catch (e: Exception) { RtLog.swallow(e) } }
            t1.start()
            t2.start()
            if (!p.waitFor(tmoSec, java.util.concurrent.TimeUnit.SECONDS)) {
                try { p.destroyForcibly() } catch (e: Exception) { RtLog.swallow(e) }
                t1.join(500)
                RtLog.w("root", "tiempo agotado (" + tmoSec + " s): " + cmd.take(80))
                return null
            }
            t1.join(5000)
            t2.join(1000)
            RtLog.d("root", "bytes " + (System.currentTimeMillis() - t0) + "ms: " + cmd.take(80) + " → " + (out?.size ?: -1) + " bytes")
            out
        } catch (e: Throwable) {
            RtLog.w("root", "Rx.bytes falló: " + cmd.take(80), e)
            null
        }
    }
}
