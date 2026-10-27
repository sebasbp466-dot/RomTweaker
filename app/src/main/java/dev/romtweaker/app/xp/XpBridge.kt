package dev.romtweaker.app.xp

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/** Application que se conecta al servicio de LSPosed para enviar ajustes al módulo. */
class RtApp : Application(), XposedServiceHelper.OnServiceListener {
    override fun onCreate() {
        super.onCreate()
        try { XposedServiceHelper.registerListener(this) } catch (e: Throwable) { }
    }

    override fun onServiceBind(service: XposedService) { XpBridge.service = service }
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

    fun putString(k: String, v: String): Boolean = try {
        val p = service?.getRemotePreferences("rt")
        if (p == null) false else { p.edit().putString(k, v).apply(); true }
    } catch (e: Throwable) { false }

    fun putBool(k: String, v: Boolean): Boolean = try {
        val p = service?.getRemotePreferences("rt")
        if (p == null) false else { p.edit().putBoolean(k, v).apply(); true }
    } catch (e: Throwable) { false }
}
