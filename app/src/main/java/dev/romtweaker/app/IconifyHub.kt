package dev.romtweaker.app

import android.content.Context
import java.io.File

/** Shell root propio (prueba varios su). Los overlays de Iconify (GPL-3.0) se instalan desde aquí. */
object Sx {
    private var variant = -1
    private fun variants(cmd: String): List<Array<String>> = listOf(
        arrayOf("su", "-c", cmd),
        arrayOf("/data/adb/ksu/bin/su", "-c", cmd),
        arrayOf("/system/xbin/su", "-c", cmd),
        arrayOf("/system/bin/su", "-c", cmd),
        arrayOf("su", "0", "sh", "-c", cmd)
    )

    fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    fun su(cmd: String): Pair<Int, String> {
        val vs = variants(cmd)
        val order = ArrayList<Int>()
        if (variant >= 0) order.add(variant)
        for (i in vs.indices) if (i != variant) order.add(i)
        var last = Pair(-1, "sin root")
        for (i in order) {
            try {
                val pr = ProcessBuilder(*vs[i]).redirectErrorStream(true).start()
                val out = pr.inputStream.bufferedReader().readText()
                val code = pr.waitFor()
                if (code == 127) { last = Pair(code, out.trim()); continue }
                variant = i
                return Pair(code, out.trim())
            } catch (e: Exception) {
                last = Pair(-1, e.message ?: "error")
            }
        }
        return last
    }
}

class IcoPart(val id: String, val pkg: String, val target: String)
class IcoItem(val group: String, val label: String, val parts: List<IcoPart>)

object IcoCat {
    fun load(ctx: Context): List<IcoItem> {
        val out = ArrayList<IcoItem>()
        try {
            ctx.assets.open("iconify/index.txt").bufferedReader().useLines { lines ->
                for (ln in lines) {
                    val p = ln.split("|")
                    if (p.size < 3) continue
                    val parts = ArrayList<IcoPart>()
                    for (s in p[2].split(",")) {
                        val q = s.split(":")
                        if (q.size == 3) parts.add(IcoPart(q[0], q[1], q[2]))
                    }
                    if (parts.isNotEmpty()) out.add(IcoItem(p[0], p[1], parts))
                }
            }
        } catch (e: Exception) {
        }
        return out
    }
}

object IcoOps {
    private const val MOD = "/data/adb/modules/romtweaker_ico"
    private const val OVL = "/data/adb/modules/romtweaker_ico/system/product/overlay"

    fun enabled(pkg: String): Boolean {
        val out = Sx.su("cmd overlay list --user 0").second
        for (l in out.lines()) {
            val t = l.trim()
            if (t.startsWith("[x]") && t.endsWith(pkg)) return true
        }
        return false
    }

    private fun stage(ctx: Context, id: String): String {
        val tmp = File(ctx.cacheDir, "$id.apk")
        ctx.assets.open("iconify/$id.apk").use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        val dst = "/data/local/tmp/rt_ico/$id.apk"
        Sx.su("mkdir -p /data/local/tmp/rt_ico && cp " + Sx.q(tmp.absolutePath) + " " + dst + " && chmod 644 " + dst)
        return dst
    }

    private fun installEnable(ctx: Context, p: IcoPart, log: StringBuilder): Boolean {
        val dst = stage(ctx, p.id)
        var r = Sx.su("pm install -r -d $dst")
        if (!r.second.contains("Success")) {
            Sx.su("pm uninstall ${p.pkg}")
            r = Sx.su("pm install -r -d $dst")
        }
        if (!r.second.contains("Success")) {
            log.append(" [pm: ").append(r.second.take(90)).append("]")
            return false
        }
        Sx.su("cmd overlay enable --user 0 ${p.pkg}")
        val ok = enabled(p.pkg)
        if (!ok) log.append(" [${p.id}: el sistema no lo activó]")
        return ok
    }

    private fun moduleAdd(ctx: Context, p: IcoPart) {
        val dst = stage(ctx, p.id)
        Sx.su(
            "mkdir -p $OVL; [ -f $MOD/module.prop ] || printf 'id=romtweaker_ico\\nname=RomTweaker Iconify overlays\\nversion=1\\nversionCode=1\\nauthor=RomTweaker\\ndescription=Overlays de Iconify (GPL-3.0)\\n' > $MOD/module.prop; " +
                "cp $dst $OVL/${p.id}.apk; chmod 644 $OVL/${p.id}.apk"
        )
    }

    fun apply(ctx: Context, item: IcoItem, all: List<IcoItem>): String {
        val log = StringBuilder()
        val keep = HashSet<String>()
        for (p in item.parts) keep.add(p.pkg)
        for (o in all) {
            if (o.group != item.group) continue
            for (p in o.parts) {
                if (!keep.contains(p.pkg)) {
                    Sx.su("cmd overlay disable --user 0 ${p.pkg}; rm -f $OVL/${p.id}.apk")
                }
            }
        }
        var viaMod = false
        var sysui = false
        var bad = 0
        for (p in item.parts) {
            val ok = try { installEnable(ctx, p, log) } catch (e: Exception) { log.append(" [${e.message}]"); false }
            if (ok) {
                Sx.su("rm -f $OVL/${p.id}.apk")
            } else {
                bad++
                try { moduleAdd(ctx, p); viaMod = true } catch (e: Exception) { }
            }
            if (p.target == "com.android.systemui") sysui = true
        }
        if (sysui) Sx.su("killall com.android.systemui")
        return when {
            bad == 0 -> "${item.group} · ${item.label}: aplicado ✓"
            viaMod -> "${item.group} · ${item.label}: no se pudo en vivo; quedó como módulo KernelSU, reinicia.$log"
            else -> "${item.group} · ${item.label}: falló.$log"
        }
    }

    fun clear(group: String, all: List<IcoItem>): String {
        var sysui = false
        for (o in all) {
            if (o.group != group) continue
            for (p in o.parts) {
                Sx.su("cmd overlay disable --user 0 ${p.pkg}; rm -f $OVL/${p.id}.apk")
                if (p.target == "com.android.systemui") sysui = true
            }
        }
        if (sysui) Sx.su("killall com.android.systemui")
        return "$group: original restaurado"
    }

    fun clearAll(all: List<IcoItem>): String {
        for (o in all) for (p in o.parts) Sx.su("cmd overlay disable --user 0 ${p.pkg}")
        Sx.su("rm -rf $MOD; killall com.android.systemui")
        return "Todos los overlays de Iconify desactivados"
    }
}

/** Puente (por reflexión) con la parte LSPosed; si el módulo no se compiló, simplemente no está. */
object Xp {
    private fun br(): Any? = try {
        Class.forName("dev.romtweaker.app.xp.XpBridge").getField("INSTANCE").get(null)
    } catch (e: Throwable) { null }

    fun included(): Boolean = br() != null

    fun status(): String {
        val b = br() ?: return "El módulo LSPosed no está incluido en esta compilación."
        return try { b.javaClass.getMethod("status").invoke(b) as String } catch (e: Throwable) { "Estado desconocido" }
    }

    fun putString(k: String, v: String): Boolean {
        val b = br() ?: return false
        return try { b.javaClass.getMethod("putString", String::class.java, String::class.java).invoke(b, k, v) as Boolean } catch (e: Throwable) { false }
    }

    fun putBool(k: String, v: Boolean): Boolean {
        val b = br() ?: return false
        return try { b.javaClass.getMethod("putBool", String::class.java, Boolean::class.javaPrimitiveType).invoke(b, k, v) as Boolean } catch (e: Throwable) { false }
    }
}
