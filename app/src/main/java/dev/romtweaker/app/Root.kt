package dev.romtweaker.app

object Root {
    fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    fun su(cmd: String): Pair<Int, String> {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = StringBuilder()
            val t = Thread {
                try { out.append(p.inputStream.bufferedReader().readText()) } catch (e: Exception) {}
            }
            t.start()
            val err = p.errorStream.bufferedReader().readText()
            t.join()
            val code = p.waitFor()
            Pair(code, (out.toString() + err).trim())
        } catch (e: Exception) {
            Pair(-1, e.message ?: "error")
        }
    }

    fun sh(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            p.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun prop(k: String): String { val v = sh("getprop " + q(k)); return if (v.isNotEmpty()) v else su("getprop " + q(k)).second }; fun allProps(): String { val r = su("getprop"); if (r.first == 0) { if (r.second.isNotEmpty()) return r.second }; return sh("getprop") }
}
