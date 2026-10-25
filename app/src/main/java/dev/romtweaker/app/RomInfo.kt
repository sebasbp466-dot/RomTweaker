package dev.romtweaker.app

import android.os.Build

object RomInfo {
    private val known = listOf(
        "ro.lineage.display.version" to "LineageOS",
        "ro.lineage.version" to "LineageOS",
        "ro.lineage.build.version" to "LineageOS",
        "ro.modversion" to "LineageOS",
        "ro.crdroid.version" to "crDroid",
        "ro.pixelexperience.version" to "PixelExperience",
        "ro.evolution.version" to "Evolution X",
        "ro.evolution.build.version" to "Evolution X",
        "ro.derp.version" to "DerpFest",
        "ro.arrow.version" to "ArrowOS",
        "ro.pixelos.version" to "PixelOS",
        "ro.aospa.version" to "Paranoid Android",
        "ro.rr.version" to "Resurrection Remix",
        "ro.miui.ui.version.name" to "MIUI",
        "ro.mi.os.version.name" to "HyperOS",
        "ro.build.version.oneui" to "One UI"
    )

    private val flavors = mapOf(
        "lineage" to "LineageOS", "aosp" to "AOSP", "crdroid" to "crDroid",
        "evolution" to "Evolution X", "derp" to "DerpFest", "arrow" to "ArrowOS",
        "aospa" to "Paranoid Android", "pixelexperience" to "PixelExperience",
        "pixelos" to "PixelOS", "rising" to "RisingOS", "voltage" to "VoltageOS",
        "havoc" to "Havoc-OS", "superior" to "SuperiorOS", "statix" to "Statix"
    )

    private val skip = setOf(
        "build", "product", "vendor", "system", "system_ext", "odm", "boot", "bootimage",
        "board", "hardware", "soc", "config", "telephony", "kernel", "bootloader", "treble",
        "radio", "wifi", "opengles", "vndk", "hwui", "media", "secure", "crypto", "apex",
        "com", "dalvik", "zygote", "sf", "surface_flinger", "adb", "setupwizard",
        "control_privapp_permissions", "virtual_ab", "actionable_compatible_property",
        "debuggable", "bionic", "arch", "cpu", "gfx", "codec2"
    )

    private fun readProps(): Map<String, String> {
        val map = HashMap<String, String>()
        val re = Regex("^\\[(.+?)\\]: \\[(.*)\\]")
        for (l in Root.sh("getprop").lines()) {
            val m = re.find(l.trim())
            if (m != null) map[m.groupValues[1]] = m.groupValues[2]
        }
        return map
    }

    fun summary(): String {
        val props = readProps()
        fun g(k: String): String = props[k] ?: ""

        var name = ""
        var ver = ""
        for ((k, n) in known) {
            val v = g(k)
            if (v.isNotEmpty()) {
                name = n
                ver = v
                break
            }
        }

        val flavor = g("ro.build.flavor")
        val pre = flavor.substringBefore("_").lowercase()
        if (name.isEmpty() && flavors.containsKey(pre)) name = flavors[pre] ?: ""

        if (name.isEmpty()) {
            val re = Regex("^ro\\.([a-z0-9_]+)\\.(version|display\\.version|build\\.version)$")
            for ((k, v) in props) {
                val m = re.find(k)
                if (m != null && v.isNotEmpty() && !skip.contains(m.groupValues[1])) {
                    name = m.groupValues[1]
                    ver = v
                    break
                }
            }
        }
        if (name.isEmpty()) name = "desconocida"

        val romLine = if (ver.isNotEmpty()) name + " " + ver else name + " (versión no expuesta por esta build)"
        val extra = if (ver.isEmpty() && pre.isNotEmpty()) {
            props.entries.filter { it.key.startsWith("ro." + pre + ".") }.take(3)
                .joinToString("") { "\n   " + it.key + " = " + it.value }
        } else ""

        val kernel = (Root.sh("uname -r") + " " + Root.sh("uname -v")).trim()
        val sb = StringBuilder()
        sb.append("ROM: ").append(romLine).append(extra).append('\n')
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" · SDK ").append(Build.VERSION.SDK_INT)
            .append(" · parche ").append(Build.VERSION.SECURITY_PATCH).append('\n')
        sb.append("Kernel: ").append(kernel).append('\n')
        sb.append("Build: ").append(g("ro.build.display.id")).append('\n')
        sb.append("Incremental: ").append(g("ro.build.version.incremental")).append('\n')
        sb.append("Fecha de build: ").append(g("ro.build.date")).append('\n')
        sb.append("Base: ").append(flavor).append('\n')
        sb.append("Dispositivo: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
            .append(" (").append(Build.DEVICE).append(")")
        return sb.toString()
    }
}
