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
        val ok = Xp.putAll(
            mapOf<String, Any>(
                "lk_style" to cfg.style, "lk_color" to cfg.color.toString(), "lk_accent" to cfg.accent.toString(),
                "lk_glass" to cfg.glass.toString(), "lk_filter" to cfg.filter.toString(), "lk_weight" to cfg.weight.toString(),
                "lk_wdth" to cfg.wdth.toString(), "lk_rond" to cfg.rond.toString(), "lk_slnt" to cfg.slnt.toString(), "lk_on" to on
            )
        )
        RtLog.i("módulo", "estilo del reloj nativo enviado: estilo=" + cfg.style + " activo=" + on + " ok=" + ok)
        return ok
    }
}
