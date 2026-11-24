package dev.romtweaker.app

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.util.Log
import java.io.File
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.regex.Pattern

/**
 * Registro propio de RomTweaker, pensado para que una persona experta pueda resolver un fallo
 * SOLO leyendo este archivo (sin pedirle nada más a quien lo sufre).
 *
 *  - Se guarda en filesDir/logs/rt.log. Al llegar a 1 MB rota (rt.log.1 … rt.log.4): 5 MB como máximo.
 *  - Cada línea: fecha · nivel/etiqueta · [proceso:hilo] · mensaje.
 *    Niveles: D detalle, I información, W aviso, E error, S error silenciado
 *    (un fallo que el código ignora a propósito; se agrupa y solo se cuenta).
 *  - Nunca se guarda contenido personal: ni textos de notificaciones ni mensajes.
 *    Las claves y tokens conocidos se tachan antes de escribir.
 */
object RtLog {
    const val TAG = "RomTweaker"
    private const val MAX_BYTES = 1_000_000L
    private const val KEEP = 5
    private const val RING = 500

    @Volatile private var dir: File? = null
    @Volatile private var h: Handler? = null
    private var wr: Writer? = null
    private var nLines = 0
    private val ring = ArrayDeque<String>()
    private val seen = HashMap<String, IntArray>()
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    @Volatile private var t0 = System.currentTimeMillis()

    /** Cuando el código corre dentro de SystemUI (módulo) no hay archivo propio: las líneas se reenvían a este receptor. */
    @JvmField @Volatile var sink: ((Char, String, String) -> Unit)? = null

    fun started(): Long = t0

    fun init(ctx: Context) {
        if (dir != null) return
        synchronized(this) {
            if (dir != null) return
            val d = File(ctx.applicationContext.filesDir, "logs")
            d.mkdirs()
            val ht = HandlerThread("rt-log", Process.THREAD_PRIORITY_BACKGROUND)
            ht.start()
            h = Handler(ht.looper)
            t0 = System.currentTimeMillis()
            dir = d
        }
        header(ctx)
    }

    private fun header(ctx: Context) {
        try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val dm = ctx.resources.displayMetrics
            val sb = StringBuilder()
            sb.append("===== RomTweaker · sesión iniciada =====\n")
            sb.append("app ").append(pi.versionName).append(" (código ").append(pi.longVersionCode)
                .append(") · repo ").append(AppInfo.REPO).append(" · protocolo del módulo ").append(ModVer.MV).append('\n')
            sb.append("equipo ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" (").append(Build.DEVICE).append(") · placa ").append(Build.BOARD)
                .append(" · hardware ").append(Build.HARDWARE).append('\n')
            sb.append("android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT)
                .append(") · parche ").append(Build.VERSION.SECURITY_PATCH).append(" · build ").append(Build.DISPLAY).append('\n')
            sb.append("abi ").append(Build.SUPPORTED_ABIS.joinToString(",")).append(" · idioma ")
                .append(java.util.Locale.getDefault().toLanguageTag()).append(" · pantalla ")
                .append(dm.widthPixels).append('x').append(dm.heightPixels).append(" dpi=").append(dm.densityDpi).append('\n')
            sb.append("memoria máxima ").append(Runtime.getRuntime().maxMemory() / 1048576).append(" MB · pid ").append(Process.myPid())
            add('I', "sesión", sb.toString(), null)
        } catch (e: Throwable) {
            add('W', "sesión", "no pude leer los datos de la app: " + e, null)
        }
    }

    fun d(tag: String, msg: String) = add('D', tag, msg, null)
    fun i(tag: String, msg: String) = add('I', tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = add('W', tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = add('E', tag, msg, t)

    private fun prio(l: Char): Int = when (l) {
        'E' -> Log.ERROR
        'W' -> Log.WARN
        'I' -> Log.INFO
        else -> Log.DEBUG
    }

    fun add(level: Char, tag: String, msg: String, t: Throwable?) {
        try {
            val now = System.currentTimeMillis()
            var body = msg
            if (t != null) body = body + "\n" + trace(t, 40)
            body = redact(body)
            try {
                Log.println(prio(level), TAG, "[" + tag + "] " + (if (body.length > 1800) body.take(1800) + "…" else body))
            } catch (e: Throwable) { /* logcat no disponible */ }
            val hh = h
            if (hh == null) {
                val sk = sink
                if (sk != null) sk(level, tag, body)
            }
            val line = fmt.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())) + " " + level + "/" + tag +
                " [" + Process.myPid() + ":" + Process.myTid() + "] " + body.replace("\n", "\n    ")
            synchronized(ring) {
                ring.addLast(line)
                while (ring.size > RING) ring.removeFirst()
            }
            if (hh != null) hh.post { write(line) }
        } catch (e: Throwable) { /* registrar nunca debe romper la app */ }
    }

    private fun write(line: String) {
        try {
            val d = dir ?: return
            val f = File(d, "rt.log")
            if (wr == null || (++nLines % 50 == 0 && f.length() > MAX_BYTES)) rotate(d, f)
            val ww = wr ?: java.io.OutputStreamWriter(java.io.FileOutputStream(f, true), Charsets.UTF_8).also { wr = it }
            ww.write(line)
            ww.write("\n")
            ww.flush()
        } catch (e: Throwable) {
            wr = null
        }
    }

    private fun rotate(d: File, f: File) {
        if (!f.exists() || f.length() <= MAX_BYTES) return
        try { wr?.close() } catch (e: Throwable) { wr = null }
        wr = null
        File(d, "rt.log." + (KEEP - 1)).delete()
        for (i in KEEP - 2 downTo 1) {
            val a = File(d, "rt.log.$i")
            if (a.exists()) a.renameTo(File(d, "rt.log." + (i + 1)))
        }
        f.renameTo(File(d, "rt.log.1"))
    }

    /** Un error que el código ignora a propósito: la primera vez se guarda con su traza; después solo se cuenta (1, 10, 100, 1000…). */
    fun swallow(t: Throwable) {
        try {
            val st = t.stackTrace
            val top = st.firstOrNull { it.className.startsWith("dev.romtweaker") } ?: st.firstOrNull()
            val key = t.javaClass.name + "@" + (if (top != null) top.fileName + ":" + top.lineNumber else "?")
            val c = synchronized(seen) {
                if (seen.size > 400) seen.clear()
                val a = seen.getOrPut(key) { IntArray(1) }
                a[0]++
                a[0]
            }
            if (c == 1 || c == 10 || c == 100 || c == 1000 || c % 10000 == 0) {
                add('S', "ignorado", key + " x" + c + ": " + (t.message ?: ""), if (c == 1) t else null)
            }
        } catch (e: Throwable) { /* nada que hacer */ }
    }

    /** Un comando con root: código de salida, tiempo y el principio de lo que respondió. */
    fun cmd(src: String, cmd: String, code: Int, out: String, ms: Long) {
        val flat = cmd.replace('\n', ' ').trim()
        val c = if (flat.length > 220) flat.take(220) + "…" else flat
        val o = out.replace('\n', '↵').take(if (code == 0) 120 else 400)
        add(if (code == 0) 'D' else 'W', src, "su[" + code + "] " + ms + "ms: " + c + (if (o.isNotEmpty()) " → " + o else ""), null)
    }

    fun trace(t: Throwable, maxLines: Int): String {
        val sw = java.io.StringWriter()
        t.printStackTrace(java.io.PrintWriter(sw))
        val txt = sw.toString().trim()
        val ls = txt.lines()
        return if (ls.size <= maxLines) txt else ls.take(maxLines).joinToString("\n") + "\n… (" + (ls.size - maxLines) + " líneas más)"
    }

    private val reKey = Pattern.compile("AIza[0-9A-Za-z_\\-]{20,}")
    private val reBot = Pattern.compile("\\b\\d{6,12}:[A-Za-z0-9_\\-]{30,}")
    private val reGh = Pattern.compile("gh[pousr]_[A-Za-z0-9]{20,}")
    private val reAuth = Pattern.compile("(?i)(bearer|authorization[:=]?)\\s+[A-Za-z0-9._\\-]{16,}")
    private val reKv = Pattern.compile("(?i)(api[_-]?key|token|secret|password|clave)\\s*[=:]\\s*\\S{6,}")

    /** Tacha claves y tokens conocidos (Gemini, Telegram, GitHub, cabeceras de autorización…). */
    fun redact(s: String): String {
        var r = s
        r = reKey.matcher(r).replaceAll("AIza…[oculto]")
        r = reBot.matcher(r).replaceAll("[token oculto]")
        r = reGh.matcher(r).replaceAll("[token oculto]")
        r = reAuth.matcher(r).replaceAll("$1 [oculto]")
        r = reKv.matcher(r).replaceAll("$1=[oculto]")
        return r
    }

    fun tail(n: Int): String = synchronized(ring) { ring.toList().takeLast(n).joinToString("\n") }

    /** Espera (máx. 1,5 s) a que todo lo pendiente llegue al archivo, para poder leerlo entero. */
    fun sync() {
        try {
            val hh = h ?: return
            val l = java.util.concurrent.CountDownLatch(1)
            hh.post { l.countDown() }
            l.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: Throwable) { /* nada */ }
    }

    fun files(ctx: Context): List<File> {
        val d = File(ctx.applicationContext.filesDir, "logs")
        val l = ArrayList<File>()
        for (i in KEEP - 1 downTo 1) {
            val f = File(d, "rt.log.$i")
            if (f.exists()) l.add(f)
        }
        val m = File(d, "rt.log")
        if (m.exists()) l.add(m)
        return l
    }

    fun readTail(ctx: Context, maxChars: Int): String {
        sync()
        val sb = StringBuilder()
        for (f in files(ctx).reversed()) {
            if (sb.length >= maxChars) break
            val s = try { f.readText() } catch (e: Throwable) { "" }
            sb.insert(0, s)
        }
        return if (sb.length > maxChars) "…(recortado)\n" + sb.substring(sb.length - maxChars) else sb.toString()
    }

    fun clear(ctx: Context) {
        sync()
        h?.post { try { wr?.close() } catch (e: Throwable) { /* nada */ }; wr = null }
        sync()
        for (f in files(ctx)) f.delete()
        synchronized(ring) { ring.clear() }
        synchronized(seen) { seen.clear() }
        CrashLog.clear(ctx)
        i("registro", "registros borrados por el usuario")
    }

    @Volatile private var wd: Thread? = null

    /** Vigila que la interfaz no se congele: si el hilo principal tarda más de 4 s en responder, guarda su pila. */
    fun watch(on: Boolean) {
        if (!on) {
            wd?.interrupt()
            wd = null
            return
        }
        if (wd != null) return
        val main = Handler(Looper.getMainLooper())
        val t = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val ok = java.util.concurrent.atomic.AtomicBoolean(false)
                    main.post { ok.set(true) }
                    Thread.sleep(4000)
                    if (!ok.get()) {
                        val st = Looper.getMainLooper().thread.stackTrace
                        add('E', "congelada", "La interfaz lleva más de 4 s sin responder. Pila del hilo principal:\n" +
                            st.take(30).joinToString("\n") { "  at " + it }, null)
                        Thread.sleep(6000)
                    }
                }
            } catch (e: InterruptedException) { /* fin de la vigilancia */ }
        }
        t.isDaemon = true
        t.name = "rt-watchdog"
        wd = t
        t.start()
    }
}
