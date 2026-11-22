package dev.romtweaker.app

object Root {
    fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    fun su(cmd: String): Pair<Int, String> {
        return try {
            val p = Runtime.getRuntime().exec(Adapt.suCmd(cmd))
            val out = StringBuilder()
            val er = StringBuilder()
            val t1 = Thread { try { out.append(p.inputStream.bufferedReader().readText()) } catch (e: Exception) {} }
            val t2 = Thread { try { er.append(p.errorStream.bufferedReader().readText()) } catch (e: Exception) {} }
            t1.start(); t2.start()
            if (!p.waitFor(90, java.util.concurrent.TimeUnit.SECONDS)) {
                try { p.destroyForcibly() } catch (e: Exception) {}
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
            val t1 = Thread { try { out.append(p.inputStream.bufferedReader().readText()) } catch (e: Exception) {} }
            val t2 = Thread { try { p.errorStream.readBytes() } catch (e: Exception) {} }
            t1.start(); t2.start()
            if (!p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) { try { p.destroyForcibly() } catch (e: Exception) {} }
            t1.join(1000)
            out.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun prop(k: String): String { val v = sh("getprop " + q(k)); return if (v.isNotEmpty()) v else su("getprop " + q(k)).second }; fun allProps(): String { val r = su("getprop"); if (r.first == 0) { if (r.second.isNotEmpty()) return r.second }; return sh("getprop") }
}
