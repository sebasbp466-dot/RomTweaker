package dev.romtweaker.app.xp

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/** Application que se conecta al servicio de LSPosed para enviar ajustes al módulo. */
class RtApp : Application(), XposedServiceHelper.OnServiceListener {
    override fun onCreate() {
        super.onCreate()
        try {
            val prev = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { t, e ->
                try { java.io.File(filesDir, "crash.txt").writeText(java.util.Date().toString() + " [" + t.name + "]\n" + android.util.Log.getStackTraceString(e)) } catch (x: Throwable) { }
                if (prev != null) prev.uncaughtException(t, e)
            }
        } catch (e: Throwable) { }
        try { XposedServiceHelper.registerListener(this) } catch (e: Throwable) { }
    }

    override fun onServiceBind(service: XposedService) { XpBridge.service = service; XpBridge.flush() }
    override fun onServiceDied(service: XposedService) { XpBridge.service = null }
}

object XpBridge {
    @Volatile
    @JvmField
    var service: XposedService? = null

    fun status(): String {
        val s = service ?: return "Sin conexión con LSPosed. Activa el módulo RomTweaker en LSPosed y vuelve a abrir la app."
        return try { "Conectado a ${s.frameworkName} ${s.frameworkVersion} (API ${s.apiVersion})" } catch (e: Throwable) { "Conectado a LSPosed" }
    }

    private val pending = LinkedHashMap<String, Any>()

    private fun prefs(): android.content.SharedPreferences? = try { service?.getRemotePreferences("rt") } catch (e: Throwable) { null }

    private fun write(p: android.content.SharedPreferences, m: Map<String, Any>): Boolean = try {
        val e = p.edit()
        for ((k, v) in m) when (v) {
            is Boolean -> e.putBoolean(k, v)
            is String -> e.putString(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Float -> e.putFloat(k, v)
        }
        e.apply()
        true
    } catch (t: Throwable) { false }

    /** Escribe varios ajustes en UNA sola edición (atómico para el módulo). Si aún no hay conexión, quedan en cola y se envían al conectar. */
    @Synchronized
    fun putAll(m: Map<String, Any>): Boolean {
        val p = prefs()
        if (p == null) { pending.putAll(m); return false }
        return write(p, m)
    }

    @Synchronized
    fun flush() {
        if (pending.isEmpty()) return
        val p = prefs() ?: return
        if (write(p, pending)) pending.clear()
    }

    fun putString(k: String, v: String): Boolean = putAll(mapOf(k to v))

    fun putBool(k: String, v: Boolean): Boolean = putAll(mapOf(k to v))
}
