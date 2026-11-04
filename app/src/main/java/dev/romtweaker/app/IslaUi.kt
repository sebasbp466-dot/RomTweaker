package dev.romtweaker.app

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/**
 * Ajustes de la isla dinámica (módulo com.lancelot.island).
 * La isla guarda su configuración como texto "k=v;k=v" en Settings.Secure "isla_config".
 * Aquí se lee y se escribe ese mismo texto (con root), conservando las claves que no conocemos.
 */
object IslaCfg {
    const val KEY = "isla_config"
    const val PKG = "com.lancelot.island"

    val DEF: Map<String, String> = linkedMapOf(
        "en" to "1", "w" to "320", "h" to "64", "t" to "2", "r" to "32", "d" to "4000",
        "bg" to "FF000000", "tc" to "FFFFFFFF", "bc" to "FFCCCCCC", "ic" to "1",
        "st" to "0", "cm" to "110", "ep" to "0", "an" to "1", "vl" to "100",
        "hp" to "0", "gl" to "1", "au" to "1", "la" to "0", "br" to "24", "oh" to "1", "nm" to "2",
        "xn" to "1", "xc" to "1", "xl" to "1", "xm" to "1", "xt" to "1", "xb" to "1", "xa" to "1",
        "xk" to "1", "xg" to "1", "xy" to "1", "xj" to "1", "xv" to "0", "xp" to "0", "xr" to "1",
        "bp" to "1", "nd" to "1", "pm" to "0", "dt" to "1", "gs" to "100", "lq" to "70",
        "wv" to "0", "fs" to "0", "sl" to "0", "sd" to "23", "sh" to "7"
    )

    private val m = LinkedHashMap<String, String>()
    private val h = Handler(Looper.getMainLooper())
    private val saver = Runnable {
        val v = serialize()
        Thread { Sx.su("settings put secure $KEY " + Sx.q(v)) }.start()
    }

    private fun parse(s: String) {
        m.clear()
        for (p in s.split(";")) {
            val i = p.indexOf('=')
            if (i > 0) m[p.substring(0, i)] = p.substring(i + 1)
        }
    }

    fun load(ctx: Context): Boolean {
        val s = try { Settings.Secure.getString(ctx.contentResolver, KEY) } catch (e: Throwable) { null }
        if (s.isNullOrEmpty()) { m.clear(); return false }
        parse(s)
        return true
    }

    /** Plan B si el sistema no deja leer el ajuste: lo lee con root. */
    fun loadRoot(): Boolean {
        val r = Sx.su("settings get secure $KEY").second.trim()
        if (r.isEmpty() || r == "null" || !r.contains("=")) return false
        parse(r)
        return true
    }

    fun s(k: String): String = m[k] ?: DEF[k] ?: ""
    fun i(k: String): Int = s(k).toIntOrNull() ?: 0
    fun b(k: String): Boolean = s(k) == "1"

    fun set(k: String, v: String) {
        m[k] = v
        h.removeCallbacks(saver)
        h.postDelayed(saver, 200)
    }

    fun setNow(k: String, v: String) {
        m[k] = v
        h.removeCallbacks(saver)
        saver.run()
    }

    fun serialize(): String {
        val all = LinkedHashMap<String, String>()
        for ((k, d) in DEF) all[k] = m[k] ?: d
        for ((k, v) in m) if (!all.containsKey(k)) all[k] = v
        return all.entries.joinToString(";") { it.key + "=" + it.value }
    }

    fun reset() {
        m.clear()
        h.removeCallbacks(saver)
        Thread { Sx.su("settings delete secure $KEY") }.start()
    }

    fun color(k: String): Int = try { s(k).toLong(16).toInt() } catch (e: Throwable) { Color.BLACK }
}

/** Vista aproximada de la isla con los ajustes actuales. */
class IslaPreview(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (170 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0xFF1E3C72.toInt(), 0xFF2A5298.toInt(), 0xFFB06AB3.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, w, h), 22 * d, 22 * d, p)
        p.shader = null

        val st = IslaCfg.i("st")
        val sc = minOf(1f, (w - 24 * d) / (IslaCfg.i("w") * d))
        val cw = IslaCfg.i("cm") * d * sc
        val ch = 30f * d
        val cx = w / 2
        val bg = IslaCfg.color("bg")
        val glassy = st == 1 || st == 5

        // Cápsula compacta
        val cap = RectF(cx - cw / 2, 10 * d, cx + cw / 2, 10 * d + ch)
        fill(c, cap, ch / 2, bg, glassy, st)

        // Isla expandida
        val ew = IslaCfg.i("w") * d * sc
        val eh = IslaCfg.i("h") * d * sc
        val top = cap.bottom + 8 * d + IslaCfg.i("t") * d * 0.5f
        val er = minOf(IslaCfg.i("r") * d * sc, eh / 2)
        val ex = RectF(cx - ew / 2, top, cx + ew / 2, top + eh)
        fill(c, ex, er, bg, glassy, st)

        // Contenido
        val ic = IslaCfg.b("ic")
        var tx = ex.left + 16 * d
        if (ic) {
            p.style = Paint.Style.FILL
            p.color = 0xFF4DD0A1.toInt()
            val rr = minOf(eh / 2 - 10 * d, 16 * d)
            c.drawCircle(ex.left + 14 * d + rr, ex.centerY(), rr, p)
            tx = ex.left + 14 * d + rr * 2 + 12 * d
        }
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 14 * d
        p.color = IslaCfg.color("tc")
        c.drawText("Mensaje nuevo", tx, ex.centerY() - 2 * d, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = 12 * d
        p.color = IslaCfg.color("bc")
        c.drawText("Así se ve tu isla", tx, ex.centerY() + 15 * d, p)

        p.typeface = Typeface.DEFAULT
        p.textSize = 11 * d
        p.color = 0xB3FFFFFF.toInt()
        c.drawText("Vista aproximada", 14 * d, h - 12 * d, p)
    }

    private fun fill(c: Canvas, r: RectF, rad: Float, bg: Int, glassy: Boolean, st: Int) {
        p.style = Paint.Style.FILL
        p.color = if (glassy) (0x3DFFFFFF) else bg
        c.drawRoundRect(r, rad, rad, p)
        p.style = Paint.Style.STROKE
        if (st == 2) {
            val cy = 0xFF00E5FF.toInt()
            p.color = (cy and 0x00FFFFFF) or 0x40000000; p.strokeWidth = 6 * d; c.drawRoundRect(r, rad, rad, p)
            p.color = (cy and 0x00FFFFFF) or (0x90 shl 24); p.strokeWidth = 3 * d; c.drawRoundRect(r, rad, rad, p)
            p.color = cy; p.strokeWidth = 1.5f * d; c.drawRoundRect(r, rad, rad, p)
        } else if (glassy) {
            p.color = 0x80FFFFFF.toInt(); p.strokeWidth = 1.2f * d; c.drawRoundRect(r, rad, rad, p)
        } else if (IslaCfg.b("au") && st == 0) {
            p.color = 0x33FFFFFF; p.strokeWidth = 1f * d; c.drawRoundRect(r, rad, rad, p)
        }
        p.style = Paint.Style.FILL
    }
}

class IslaActivity : Activity() {
    private lateinit var m: M3
    private lateinit var col: LinearLayout
    private lateinit var prev: IslaPreview
    private var status: TextView? = null
    private var testN = 0

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        m = M3(this, getSharedPreferences("hub", MODE_PRIVATE).getBoolean("glass", false))
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setBackgroundDrawable(ColorDrawable(m.surface))
        val root = FrameLayout(this)
        val sv = ScrollView(this)
        sv.clipToPadding = false
        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(m.dp(16), m.dp(8), m.dp(16), m.dp(24))
        sv.addView(col, FrameLayout.LayoutParams(m.MP, m.WC))
        root.addView(sv, FrameLayout.LayoutParams(m.MP, m.MP))
        setContentView(root)
        root.setOnApplyWindowInsetsListener { v, ins ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = ins.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(0, bars.top, 0, bars.bottom)
            }
            ins
        }
        if (Build.VERSION.SDK_INT >= 30 && !m.dark) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(mask, mask)
        }
        val got = IslaCfg.load(this)
        build()
        Thread {
            val ok = got || IslaCfg.loadRoot()
            val inst = Sx.su("pm path ${IslaCfg.PKG}").second.contains("package:")
            runOnUiThread {
                if (!got && ok) build()
                status?.text = if (inst) "Módulo de la isla: instalado ✓ (actívalo en LSPosed para SystemUI)" else "Módulo de la isla: no instalado"
            }
        }.start()
    }

    private fun say(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun changed() { prev.invalidate() }

    private fun sec(title: String, desc: String = ""): LinearLayout {
        val c = m.card()
        c.addView(m.tv(title, 18f, m.onSurface, true), m.lp(m.MP, m.WC, 0, 0, 0, 4))
        if (desc.isNotEmpty()) c.addView(m.tv(desc, 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 0, 0, 8))
        col.addView(c, m.lp(m.MP, m.WC, 0, 0, 0, 12))
        return c
    }

    private fun sw(c: LinearLayout, title: String, desc: String, key: String) {
        c.addView(m.switchRow(title, desc, IslaCfg.b(key)) { v -> IslaCfg.set(key, if (v) "1" else "0"); changed() }, m.lp(m.MP, m.WC, 0, 8, 0, 4))
    }

    private fun slider(c: LinearLayout, label: String, key: String, min: Int, max: Int, unit: String) {
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.addView(m.tv(label, 14f, m.onSurface), LinearLayout.LayoutParams(0, m.WC, 1f))
        val vt = m.tv("${IslaCfg.i(key)}$unit", 14f, m.primary, true)
        head.addView(vt)
        c.addView(head, m.lp(m.MP, m.WC, 0, 10, 0, 0))
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = (IslaCfg.i(key) - min).coerceIn(0, max - min)
        sb.progressTintList = ColorStateList.valueOf(m.primary)
        sb.thumbTintList = ColorStateList.valueOf(m.primary)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                vt.text = "${v + min}$unit"
                if (fromUser) { IslaCfg.set(key, (v + min).toString()); changed() }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        c.addView(sb, m.lp(m.MP, m.WC))
    }

    private fun chips(c: LinearLayout, key: String, names: List<String>, perRow: Int = 3) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        fun fill() {
            box.removeAllViews()
            var row: LinearLayout? = null
            for ((idx, n) in names.withIndex()) {
                if (idx % perRow == 0) {
                    row = LinearLayout(this)
                    row.orientation = LinearLayout.HORIZONTAL
                    box.addView(row, m.lp(m.MP, m.WC, 0, 0, 0, 8))
                }
                row!!.addView(m.chip(n, IslaCfg.i(key) == idx) {
                    IslaCfg.set(key, idx.toString()); changed(); fill()
                }, m.lp(m.WC, m.WC, 0, 0, 8, 0))
            }
        }
        fill()
        c.addView(box, m.lp(m.MP, m.WC, 0, 8, 0, 0))
    }

    private fun swatches(c: LinearLayout, label: String, keys: List<String>, opts: List<List<String>>) {
        c.addView(m.tv(label, 14f, m.onSurface), m.lp(m.MP, m.WC, 0, 12, 0, 6))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        fun fill() {
            row.removeAllViews()
            for (o in opts) {
                val sel = keys.indices.all { IslaCfg.s(keys[it]).equals(o[it], true) }
                val v = View(this)
                val g = GradientDrawable()
                g.shape = GradientDrawable.OVAL
                g.setColor(o[0].toLong(16).toInt())
                g.setStroke(m.dp(if (sel) 3 else 1), if (sel) m.primary else m.a(m.outline, 0.6f))
                v.background = g
                v.setOnClickListener {
                    for (i in keys.indices) IslaCfg.set(keys[i], o[i])
                    changed(); fill()
                }
                row.addView(v, m.lp(m.dp(36), m.dp(36), 0, 0, 12, 0))
            }
        }
        fill()
        c.addView(row, m.lp(m.WC, m.WC))
    }

    private fun build() {
        col.removeAllViews()
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val back = m.tv("‹", 30f, m.onSurface, true)
        back.setPadding(m.dp(6), 0, m.dp(14), m.dp(4))
        back.setOnClickListener { finish() }
        top.addView(back)
        top.addView(m.tv("Isla dinámica", 24f, m.onSurface, true))
        col.addView(top, m.lp(m.MP, m.WC, 0, 4, 0, 10))

        prev = IslaPreview(this)
        col.addView(prev, m.lp(m.MP, m.WC, 0, 0, 0, 12))

        val a = sec("Estado")
        status = m.tv("Comprobando el módulo…", 13f, m.onSurfaceVar)
        a.addView(status, m.lp(m.MP, m.WC, 0, 0, 0, 4))
        sw(a, "Isla activada", "Apágala para dejar la barra como siempre", "en")
        val br = LinearLayout(this)
        br.orientation = LinearLayout.HORIZONTAL
        br.addView(m.filled("Probar isla") {
            val n = testN % 4; testN++
            Thread { Sx.su("am broadcast -a com.lancelot.island.TEST --ei tipo $n") }.start()
            say("Prueba enviada")
        }, m.lp(m.WC, m.WC, 0, 0, 8, 0))
        br.addView(m.tonal("Reiniciar SystemUI") {
            say("Reiniciando SystemUI…"); Thread { Sx.su("killall com.android.systemui") }.start()
        }, m.lp(m.WC, m.WC))
        a.addView(br, m.lp(m.WC, m.WC, 0, 10, 0, 0))

        val s1 = sec("Estilo")
        chips(s1, "st", listOf("Hyper", "Cristal", "Neón", "Xiaomi", "iPhone", "Liquid Glass"))
        slider(s1, "Intensidad del cristal (Liquid Glass)", "lq", 0, 100, "%")
        slider(s1, "Desenfoque (Cristal)", "br", 0, 60, " dp")
        sw(s1, "Luz interior", "Aura en Hyper, refracción en Cristal", "au")
        sw(s1, "Aro de luz al llegar un evento", "", "gl")
        sw(s1, "Luz de progreso en el borde", "Carga, descargas y volumen", "bp")
        swatches(s1, "Color de fondo", listOf("bg"), listOf(
            listOf("FF000000"), listOf("FF1C1C1E"), listOf("FF0A1A3A"), listOf("FF2A1B4A"), listOf("FF0E2A22"), listOf("FF3A0F1A")))
        swatches(s1, "Color del texto", listOf("tc", "bc"), listOf(
            listOf("FFFFFFFF", "FFCCCCCC"), listOf("FFFFF3E0", "FFD7C4A3"), listOf("FFB8F3FF", "FF7FC4D1"), listOf("FFD7FFD9", "FF9BD3A0")))
        sw(s1, "Icono de la app", "", "ic")

        val s2 = sec("Tamaño y posición")
        slider(s2, "Ancho de la isla expandida", "w", 200, 360, " dp")
        slider(s2, "Alto", "h", 48, 100, " dp")
        slider(s2, "Esquinas", "r", 0, 50, " dp")
        slider(s2, "Ancho de la cápsula compacta", "cm", 90, 200, " dp")
        slider(s2, "Ajuste vertical", "t", 0, 60, " dp")
        c0(s2, "Posición de la isla expandida")
        chips(s2, "ep", listOf("Bajo la barra", "Sobre la barra"), 2)

        val s3 = sec("Animación")
        chips(s3, "an", listOf("Suave", "Resorte", "Elástico"))
        slider(s3, "Velocidad", "vl", 50, 200, "%")
        slider(s3, "Tiempo en pantalla", "d", 1500, 12000, " ms")
        sw(s3, "Vibración suave", "Al expandirse", "hp")

        val s4 = sec("Eventos", "Qué muestra la isla")
        sw(s4, "Notificaciones", "", "xn")
        sw(s4, "Música", "", "xm")
        sw(s4, "Llamadas", "Con botones para contestar y colgar", "xk")
        sw(s4, "Cronómetros y temporizadores", "", "xr")
        sw(s4, "Descargas y progreso", "", "xg")
        sw(s4, "Carga", "", "xc")
        sw(s4, "Batería baja", "", "xa")
        sw(s4, "Linterna", "", "xl")
        sw(s4, "Auriculares y Bluetooth", "", "xb")
        sw(s4, "Timbre y sonido", "", "xt")
        sw(s4, "Cámara y micrófono en uso", "", "xy")
        sw(s4, "Ajustes rápidos (avión, No molestar…)", "", "xj")
        sw(s4, "Volumen", "", "xv")
        sw(s4, "Portapapeles", "", "xp")
        c0(s4, "Notificaciones")
        chips(s4, "nm", listOf("Expandidas", "Minimizadas", "Automático"))

        val s5 = sec("Comportamiento")
        sw(s5, "Cápsula permanente", "Una cápsula oscura siempre alrededor del notch", "pm")
        sw(s5, "Doble toque para descartar", "", "dt")
        sw(s5, "Ocultar en horizontal", "", "oh")
        sw(s5, "Ocultar con la barra de estado oculta", "Experimental", "fs")
        slider(s5, "Sensibilidad de gestos", "gs", 50, 160, "%")
        sw(s5, "Horario silencioso", "Solo llamadas, linterna y cronómetros", "sl")
        slider(s5, "Desde las", "sd", 0, 23, " h")
        slider(s5, "Hasta las", "sh", 0, 23, " h")
        sw(s5, "Ventana antigua (seguro)", "Solo si algo no responde bajo la isla", "wv")

        val s6 = sec("Mantenimiento")
        s6.addView(m.tonal("Restablecer ajustes de la isla") {
            IslaCfg.reset(); say("Ajustes restablecidos"); build()
        }, m.lp(m.WC, m.WC, 0, 4, 0, 0))
    }

    private fun c0(c: LinearLayout, t: String) {
        c.addView(m.tv(t, 14f, m.onSurface), m.lp(m.MP, m.WC, 0, 14, 0, 0))
    }
}

/** Tarjeta de la pestaña Bloqueo que abre los ajustes de la isla. */
object IslaCard {
    fun card(act: Activity, m: M3, say: (String) -> Unit): View {
        IslaCfg.load(act)
        val c = m.card()
        c.addView(m.tv("Isla dinámica", 18f, m.onSurface, true))
        c.addView(m.tv("Música, llamadas, cronómetros, carga y notificaciones en una cápsula junto a la cámara. Estilos Hyper, Cristal, Neón, Xiaomi, iPhone y Liquid Glass.", 13f, m.onSurfaceVar), m.lp(m.MP, m.WC, 0, 4, 0, 10))
        c.addView(m.switchRow("Isla activada", "Necesita el módulo de la isla activo en LSPosed", IslaCfg.b("en")) { v ->
            IslaCfg.setNow("en", if (v) "1" else "0")
            say(if (v) "Isla activada" else "Isla desactivada")
        })
        c.addView(m.filled("Ajustes de la isla") { act.startActivity(android.content.Intent(act, IslaActivity::class.java)) }, m.lp(m.WC, m.WC, 0, 12, 0, 0))
        return c
    }
}
