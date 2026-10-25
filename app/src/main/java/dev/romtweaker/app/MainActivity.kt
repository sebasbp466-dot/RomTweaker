package dev.romtweaker.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var log: TextView
    private val prefs get() = getSharedPreferences("backup", Context.MODE_PRIVATE)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private fun su(cmd: String): Pair<Int, String> = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        p.waitFor() to (out + err).trim()
    } catch (e: Exception) {
        -1 to (e.message ?: "error")
    }

    private fun prop(k: String): String = try {
        Runtime.getRuntime().exec(arrayOf("getprop", k)).inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) { "" }

    private fun run(cmd: String) {
        log.text = "Ejecutando..."
        Thread {
            val (code, out) = su(cmd)
            runOnUiThread { log.text = "[" + code + "] " + out.ifEmpty { "OK" } }
        }.start()
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 18f; setPadding(0, dp(22), 0, dp(6))
    }

    private fun edit(h: String) = EditText(this).apply {
        hint = h; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); setSingleLine()
    }

    private fun btn(t: String, a: () -> Unit) = Button(this).apply {
        text = t; setOnClickListener { a() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sv = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0F1115")) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(40))
        }
        sv.addView(col)
        setContentView(sv)

        val rom = prop("ro.lineage.version").ifEmpty { prop("ro.build.display.id") }
        col.addView(title("RomTweaker"))
        col.addView(TextView(this).apply {
            setTextColor(Color.LTGRAY)
            text = "ROM: " + rom + "\nAndroid " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n" +
                "Dispositivo: " + Build.MANUFACTURER + " " + Build.DEVICE
        })
        col.addView(btn("Comprobar root") { run("id") })

        log = TextView(this).apply {
            setTextColor(Color.parseColor("#7CFC9A")); setPadding(0, dp(10), 0, dp(4))
            text = "Salida de comandos aquí"
        }
        col.addView(log)

        col.addView(title("Densidad (DPI)"))
        val dpi = edit("DPI, ej. 380")
        col.addView(dpi)
        col.addView(btn("Aplicar DPI") {
            val n = dpi.text.toString().toIntOrNull()
            if (n != null && n in 120..800) run("wm density " + n) else log.text = "DPI inválido (120-800)"
        })
        col.addView(btn("Restablecer DPI") { run("wm density reset") })

        col.addView(title("Escala de animaciones"))
        val anim = edit("ej. 0.5 (0 = desactivar)")
        col.addView(anim)
        col.addView(btn("Aplicar animaciones") {
            val f = anim.text.toString().toFloatOrNull()
            if (f != null && f >= 0f && f <= 10f) {
                run("settings put global window_animation_scale " + f +
                    "; settings put global transition_animation_scale " + f +
                    "; settings put global animator_duration_scale " + f)
            } else log.text = "Valor inválido"
        })

        col.addView(title("Editor de propiedades"))
        val key = edit("propiedad, ej. persist.sys.example")
        val value = edit("valor")
        col.addView(key); col.addView(value)
        col.addView(btn("Leer") {
            val k = key.text.toString().trim()
            log.text = if (k.isEmpty()) "Escribe una propiedad" else k + " = " + prop(k)
        })
        col.addView(btn("Aplicar (con backup)") {
            val k = key.text.toString().trim()
            val v = value.text.toString()
            if (k.isEmpty()) { log.text = "Escribe una propiedad"; return@btn }
            if (!prefs.contains(k)) prefs.edit().putString(k, prop(k)).apply()
            run("(command -v resetprop >/dev/null && resetprop " + q(k) + " " + q(v) + ") || setprop " + q(k) + " " + q(v))
        })
        col.addView(btn("Restaurar todo lo cambiado") {
            val all = prefs.all
            if (all.isEmpty()) { log.text = "No hay nada que restaurar"; return@btn }
            val cmd = all.entries.joinToString("; ") {
                "(command -v resetprop >/dev/null && resetprop " + q(it.key) + " " + q(it.value.toString()) +
                    ") || setprop " + q(it.key) + " " + q(it.value.toString())
            }
            prefs.edit().clear().apply()
            run(cmd)
        })
    }
}
