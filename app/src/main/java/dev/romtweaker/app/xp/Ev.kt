package dev.romtweaker.app.xp

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Registro y eventos del módulo DENTRO de SystemUI.
 * Todo está protegido con try/catch: registrar nunca debe romper SystemUI.
 *  - ModLog: archivo rt_module.log (1 línea por evento, rota a 600 KB) en la carpeta de datos de SystemUI. La app lo lee con root.
 *  - Ev.warn/err: además avisan a la app con un broadcast (máx. uno igual cada 30 s) para que salga en «Salud y reportes».
 *  - SafeMode: si SystemUI se reinicia 4+ veces en 60 s, el módulo se queda quieto 10 min para evitar un bucle.
 *  - Snap: instantánea de la estructura de la pantalla de bloqueo (sin textos personales).
 */
object Ev {
    const val APP = "dev.romtweaker.app"
    const val DIR = "/data/user_de/0/com.android.systemui"

    private val last = HashMap<String, Long>()
    private val seen = HashMap<String, IntArray>()
    private val steps = LinkedHashMap<String, String>()
    private val stepWarns = HashMap<String, Int>()
    @Volatile private var cur = ""
    @JvmField @Volatile var mod: XposedModule? = null
    @JvmField var keep: Any? = null
    private var lastSnapWhy = 0L

    fun app(): Application? = try {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Application
    } catch (e: Throwable) { null }

    private fun mark() {
        val c = cur
        if (c.isNotEmpty()) synchronized(stepWarns) { stepWarns[c] = (stepWarns[c] ?: 0) + 1 }
    }

    /** Ejecuta un paso de instalación y apunta si salió bien, con avisos o falló. */
    fun step(name: String, block: () -> Unit) {
        cur = name
        try {
            block()
            val w = synchronized(stepWarns) { stepWarns[name] ?: 0 }
            synchronized(steps) { steps[name] = if (w == 0) "ok" else "avisos(" + w + ")" }
        } catch (e: Throwable) {
            synchronized(steps) { steps[name] = "FALLO" }
            ModLog.add('E', "paso", name + ": " + Log.getStackTraceString(e))
            send("error", "paso", name + ": " + e)
        } finally {
            cur = ""
        }
    }

    fun helloText(): String = synchronized(steps) { steps.entries.joinToString(" ") { it.key + "=" + it.value } }

    fun info(x: XposedModule, tag: String, msg: String) {
        try { x.log(Log.INFO, tag, msg) } catch (e: Throwable) { /* nada */ }
        ModLog.add('I', tag, msg)
    }

    fun warn(x: XposedModule, tag: String, msg: String) {
        try { x.log(Log.WARN, tag, msg) } catch (e: Throwable) { /* nada */ }
        ModLog.add('W', tag, msg)
        mark()
        send("warn", tag, msg)
        autoSnap("aviso: " + msg.take(60))
    }

    fun err(x: XposedModule, tag: String, msg: String) {
        try { x.log(Log.ERROR, tag, msg) } catch (e: Throwable) { /* nada */ }
        ModLog.add('E', tag, msg)
        mark()
        send("error", tag, msg)
        autoSnap("error: " + msg.take(60))
    }

    /** Un error ignorado a propósito: solo al archivo y agrupado (1, 10, 100…). */
    fun swallow(t: Throwable) {
        try {
            val st = t.stackTrace
            val top = st.firstOrNull { it.className.startsWith("dev.romtweaker") } ?: st.firstOrNull()
            val key = t.javaClass.name + "@" + (if (top != null) top.fileName + ":" + top.lineNumber else "?")
            val c = synchronized(seen) {
                if (seen.size > 300) seen.clear()
                val a = seen.getOrPut(key) { IntArray(1) }
                a[0]++
                a[0]
            }
            if (c == 1 || c == 10 || c == 100 || c == 1000 || c % 10000 == 0) {
                ModLog.add('S', "ignorado", key + " x" + c + ": " + (t.message ?: "") + (if (c == 1) "\n" + Log.getStackTraceString(t).lines().take(12).joinToString("\n") else ""))
            }
        } catch (e: Throwable) { /* nada */ }
    }

    /** Avisa a la app (máximo uno igual cada 30 s y 20 por minuto). */
    fun send(type: String, tag: String, msg: String, mv: Int = 0, sdk: Int = 0) {
        try {
            val now = android.os.SystemClock.uptimeMillis()
            val key = type + "|" + tag + "|" + msg.take(60)
            synchronized(last) {
                val t = last[key]
                if (t != null && now - t < 30_000L) return
                if (last.size > 200) last.clear()
                last[key] = now
            }
            val a = app() ?: return
            val i = Intent("dev.romtweaker.app.MODULE_EVENT").setPackage(APP)
            i.putExtra("type", type)
            i.putExtra("tag", tag)
            i.putExtra("msg", msg.take(700))
            i.putExtra("mv", mv)
            i.putExtra("sdk", sdk)
            a.sendBroadcast(i)
        } catch (e: Throwable) { /* nada */ }
    }

    fun later(type: String, msg: String, ms: Long) {
        try {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    send(type, "módulo", msg, dev.romtweaker.app.ModVer.MV, android.os.Build.VERSION.SDK_INT)
                } catch (e: Throwable) { /* nada */ }
            }, ms)
        } catch (e: Throwable) { /* nada */ }
    }

    /** Escucha la petición de instantánea que hace la app (preferencia remota snap_req). */
    fun watchPrefs(p: SharedPreferences?) {
        if (p == null) return
        try {
            val l = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
                if (k == "snap_req") {
                    try { Handler(Looper.getMainLooper()).post { IcModule.snap("pedida desde la app", true) } } catch (e: Throwable) { swallow(e) }
                }
            }
            keep = l
            p.registerOnSharedPreferenceChangeListener(l)
        } catch (e: Throwable) { swallow(e) }
    }

    /** Tras un aviso o error, guarda una instantánea (como mucho una cada 5 minutos). */
    private fun autoSnap(why: String) {
        try {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastSnapWhy < 300_000L && lastSnapWhy != 0L) return
            lastSnapWhy = now
            Handler(Looper.getMainLooper()).postDelayed({ IcModule.snap(why, false) }, 1500L)
        } catch (e: Throwable) { /* nada */ }
    }
}

object ModLog {
    private const val MAX = 600_000L
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    @Synchronized
    fun add(level: Char, tag: String, msg: String) {
        try {
            val dir = File(Ev.DIR)
            if (!dir.isDirectory) return
            val f = File(dir, "rt_module.log")
            if (f.length() > MAX) {
                val o = File(dir, "rt_module.log.1")
                o.delete()
                f.renameTo(o)
            }
            val line = fmt.format(Instant.now().atZone(ZoneId.systemDefault())) + " " + level + "/" + tag +
                " [" + android.os.Process.myPid() + ":" + android.os.Process.myTid() + "] " + msg.replace("\n", "\n    ") + "\n"
            java.io.FileOutputStream(f, true).use { it.write(line.toByteArray(Charsets.UTF_8)) }
        } catch (e: Throwable) { /* nada */ }
    }
}

/** Protección contra bucles: 4+ arranques de SystemUI en 60 s → el módulo se queda quieto 10 minutos. */
object SafeMode {
    private const val WINDOW = 60_000L
    private const val LIMIT = 4
    private const val PAUSE = 600_000L

    /** true = el módulo no debe enganchar nada en este arranque. */
    fun tripped(): Boolean {
        try {
            val dir = File(Ev.DIR)
            if (!dir.isDirectory) return false
            val f = File(dir, "rt_boot.txt")
            val now = System.currentTimeMillis()
            val lines = if (f.exists()) f.readLines() else emptyList()
            var until = 0L
            val starts = ArrayList<Long>()
            for (l in lines) {
                val v = l.drop(1).trim().toLongOrNull() ?: continue
                if (l.startsWith("U")) {
                    if (v > now && v - now <= PAUSE + 60_000L) until = maxOf(until, v)
                } else if (l.startsWith("B")) starts.add(v)
            }
            if (until > now) {
                ModLog.add('W', "seguro", "modo seguro activo: quedan " + ((until - now) / 1000L) + " s")
                return true
            }
            val recent = starts.count { now - it in 0..WINDOW }
            val keep = starts.takeLast(8)
            if (recent >= LIMIT) {
                f.writeText(keep.joinToString("") { "B" + it + "\n" } + "U" + (now + PAUSE) + "\n")
                ModLog.add('E', "seguro", "SystemUI arrancó " + (recent + 1) + " veces en 60 s: el módulo se pausa 10 min")
                return true
            }
            f.writeText((keep + now).joinToString("") { "B" + it + "\n" })
        } catch (e: Throwable) {
            ModLog.add('W', "seguro", "no pude leer/escribir rt_boot.txt: " + e)
        }
        return false
    }
}

/** Instantánea de la estructura de la pantalla de bloqueo: clases, ids, posiciones y estados. Nunca guarda textos (solo su longitud, y las horas). */
object Snap {
    private var lastAt = 0L
    private var count = 0

    fun write(root: View, why: String, extra: String, force: Boolean) {
        try {
            val t = android.os.SystemClock.uptimeMillis()
            if (!force && lastAt != 0L && t - lastAt < 3000L) return
            lastAt = t
            val ctx = root.context
            val dm = ctx.resources.displayMetrics
            val sb = StringBuilder(24000)
            sb.append("# Instantánea de la pantalla de bloqueo (sin textos personales)\n")
            sb.append("motivo: ").append(why).append(" · ").append(java.util.Date()).append('\n')
            sb.append("pantalla: ").append(dm.widthPixels).append('x').append(dm.heightPixels).append(" dpi=").append(dm.densityDpi).append('\n')
            sb.append("módulo: ").append(extra).append('\n')
            try {
                val kg = ctx.getSystemService(android.app.KeyguardManager::class.java)
                sb.append("bloqueo: activo=").append(kg?.isKeyguardLocked).append(" seguro=").append(kg?.isKeyguardSecure).append('\n')
                val pm = ctx.getSystemService(android.os.PowerManager::class.java)
                sb.append("pantalla encendida=").append(pm?.isInteractive).append('\n')
                val wm = android.app.WallpaperManager.getInstance(ctx)
                sb.append("fondo: id sistema=").append(wm.getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM))
                    .append(" id bloqueo=").append(wm.getWallpaperId(android.app.WallpaperManager.FLAG_LOCK))
                    .append(" animado=").append(wm.wallpaperInfo?.component?.flattenToShortString()).append('\n')
            } catch (e: Throwable) {
                sb.append("(estado del sistema no disponible: ").append(e).append(")\n")
            }
            sb.append("--- árbol: clase #id [izq,arr,der,aba] visibilidad ... ---\n")
            count = 0
            walk(root, 0, sb)
            if (count > 700) sb.append("… (recortado a 700 vistas)\n")
            File(Ev.DIR, "rt_snap.txt").writeText(sb.toString())
            ModLog.add('I', "instantánea", "guardada (" + why + ", " + count + " vistas)")
        } catch (e: Throwable) {
            Ev.swallow(e)
        }
    }

    fun note(text: String) {
        try { File(Ev.DIR, "rt_snap.txt").writeText("# " + text + " · " + java.util.Date() + "\n") } catch (e: Throwable) { /* nada */ }
    }

    private fun f2(v: Float): String = ((v * 100f).toInt() / 100f).toString()

    private fun walk(v: View, d: Int, sb: StringBuilder) {
        if (count++ > 700 || d > 16) return
        val simple = v.javaClass.name.substringAfterLast('.')
        val idn = if (v.id != View.NO_ID) {
            try { v.resources.getResourceEntryName(v.id) } catch (e: Throwable) { "0x" + Integer.toHexString(v.id) }
        } else ""
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        for (i in 0 until d) sb.append("  ")
        sb.append(simple)
        if (idn.isNotEmpty()) sb.append(" #").append(idn)
        sb.append(" [").append(loc[0]).append(',').append(loc[1]).append(',').append(loc[0] + v.width).append(',').append(loc[1] + v.height).append("] ")
        sb.append(
            when (v.visibility) {
                View.VISIBLE -> "V"
                View.INVISIBLE -> "I"
                else -> "G"
            }
        )
        if (v.visibility == View.VISIBLE && !v.isShown) sb.append("(oculta por un padre)")
        if (v.alpha != 1f) sb.append(" a=").append(f2(v.alpha))
        if (v.scaleX != 1f || v.scaleY != 1f) sb.append(" esc=").append(f2(v.scaleX)).append('x').append(f2(v.scaleY))
        if (v.translationX != 0f || v.translationY != 0f) sb.append(" mov=").append(f2(v.translationX)).append(',').append(f2(v.translationY))
        if (v is TextView) {
            val tx = v.text?.toString() ?: ""
            sb.append(" texto(").append(tx.length).append(") tam=").append(v.textSize.toInt())
            if (tx.length in 1..6 && tx.all { it.isDigit() || it == ':' }) sb.append(" \"").append(tx).append('"')
        }
        if (v is ImageView) sb.append(" img=").append(v.drawable?.javaClass?.simpleName)
        sb.append('\n')
        if (v is ViewGroup && !simple.contains("ExpandableNotificationRow")) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i), d + 1, sb)
        }
    }
}
