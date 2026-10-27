package dev.romtweaker.app.xp

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/** Módulo LSPosed (libxposed API 101/102). Hooks mínimos y defensivos para SystemUI. */
class ModuleMain : XposedModule() {
    companion object { const val TAG = "RomTweaker" }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "módulo cargado en ${param.processName}")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != "com.android.systemui") return
        if (!param.isFirstPackage) return
        val prefs: SharedPreferences? = try { getRemotePreferences("rt") } catch (e: Throwable) { null }
        hookClock(param.classLoader, prefs)
    }

    private fun hookClock(cl: ClassLoader, prefs: SharedPreferences?) {
        try {
            val c = Class.forName("com.android.systemui.statusbar.policy.Clock", false, cl)
            var found: java.lang.reflect.Method? = null
            for (x in c.declaredMethods) {
                if (x.name == "getSmallTime" && x.parameterTypes.isEmpty()) found = x
            }
            val target = found
            if (target == null) {
                log(Log.WARN, TAG, "Clock.getSmallTime no existe en esta ROM")
                return
            }
            hook(target).intercept { chain ->
                val orig = chain.proceed()
                val on = prefs?.getBoolean("clock_on", false) ?: false
                val fmt = prefs?.getString("clock_fmt", "") ?: ""
                if (on && fmt.isNotEmpty()) {
                    try { android.text.format.DateFormat.format(fmt, System.currentTimeMillis()) } catch (e: Throwable) { orig }
                } else {
                    orig
                }
            }
            log(Log.INFO, TAG, "hook del reloj instalado")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "no se pudo enganchar el reloj", t)
        }
    }
}
