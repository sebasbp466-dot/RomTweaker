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

    fun prop(k: String): String = sh("getprop " + q(k))
}
