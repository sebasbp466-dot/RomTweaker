package dev.romtweaker.app

/** Envía la configuración del reloj al módulo LSPosed (preferencias remotas "rt"). */
object LockXp {
    private fun call(name: String, k: String, v: Any): Boolean = try {
        val c = Class.forName("dev.romtweaker.app.xp.XpBridge")
        val inst = c.getField("INSTANCE").get(null)
        val mt = c.methods.first { it.name == name && it.parameterTypes.size == 2 }
        (mt.invoke(inst, k, v) as? Boolean) ?: true
    } catch (e: Throwable) { false }

    fun apply(cfg: ClockCfg, on: Boolean): Boolean {
        var ok = call("putString", "lk_style", cfg.style)
        ok = call("putString", "lk_color", cfg.color.toString()) && ok
        ok = call("putString", "lk_accent", cfg.accent.toString()) && ok
        ok = call("putString", "lk_glass", cfg.glass.toString()) && ok
        ok = call("putBool", "lk_on", on) && ok
        return ok
    }
}
