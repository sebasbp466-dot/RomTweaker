package dev.romtweaker.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * Salud y reportes: comprueba cada pieza de la app, y si algo falla genera un informe completo
 * (equipo, ROM, estado, ajustes, registros de la app y del módulo, instantánea del bloqueo) listo para compartir.
 */
class HealthActivity : Activity() {
    private lateinit var m: M3
    private lateinit var box: LinearLayout
    private lateinit var snack: TextView
    private val ui = Handler(Looper.getMainLooper())
    private val hideSnack = Runnable { snack.visibility = View.GONE }
    private var checks: List<Health.Check> = emptyList()
    private var busy = false
    private var auto = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        RtLog.init(this)
        m = M3(this, getSharedPreferences("hub", MODE_PRIVATE).getBoolean("glass", true))
        auto = intent?.getBooleanExtra("auto", false) ?: false
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setBackgroundDrawable(ColorDrawable(m.surface))

        val root = FrameLayout(this)
        val sv = ScrollView(this)
        sv.clipToPadding = false
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(m.dp(16), m.dp(36), m.dp(16), m.dp(24))
        sv.addView(box)
        root.addView(sv, FrameLayout.LayoutParams(m.MP, m.MP))

        snack = m.tv("", 14f, m.onPrimary, true)
        snack.setPadding(m.dp(18), m.dp(12), m.dp(18), m.dp(12))
        snack.background = m.shape(m.a(m.primary, 0.96f), 16)
        snack.visibility = View.GONE
        val sl = FrameLayout.LayoutParams(m.WC, m.WC)
        sl.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        sl.setMargins(m.dp(16), 0, m.dp(16), m.dp(40))
        root.addView(snack, sl)
        setContentView(root)

        root.setOnApplyWindowInsetsListener { v, ins ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bb = ins.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(0, bb.top, 0, bb.bottom)
            }
            ins
        }
        if (Build.VERSION.SDK_INT >= 30 && !m.dark) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(mask, mask)
        }
        render()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        RtLog.watch(true)
    }

    override fun onPause() {
        super.onPause()
        RtLog.watch(false)
    }

    private fun say(s: String) {
        snack.text = s
        snack.visibility = View.VISIBLE
        ui.removeCallbacks(hideSnack)
        ui.postDelayed(hideSnack, 7000)
    }

    private fun dot(state: Int): View {
        val v = View(this)
        val c = when (state) { 0 -> 0xFF2E9E5B.toInt(); 1 -> 0xFFE0A100.toInt(); else -> 0xFFD14343.toInt() }
        v.background = m.shape(c, 6)
        return v
    }

    private fun row(k: Health.Check): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.addView(dot(k.state), m.lp(m.dp(12), m.dp(12), 0, 5, 12, 0))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(m.tv(k.title, 15f, m.onSurface, true))
        col.addView(m.tv(k.detail, 13f, m.onSurfaceVar))
        if (k.state != 0 && k.fix.isNotEmpty()) col.addView(m.tv("→ " + k.fix, 13f, m.primary))
        r.addView(col, LinearLayout.LayoutParams(0, m.WC, 1f))
        return r
    }

    private fun render() {
        box.removeAllViews()
        box.addView(m.tv("Salud y reportes", 34f, m.onSurface), m.lp(m.MP, m.WC, 8, 0, 8, 2))
        box.addView(m.tv("Comprueba que todo esté bien y, si algo falla, envía un informe completo con un toque.", 14f, m.onSurfaceVar), m.lp(m.MP, m.WC, 8, 0, 8, 18))

        val c = m.card()
        c.addView(m.tv("Estado", 18f, m.onSurface, true), m.lp(m.MP, m.WC, 0, 0, 0, 6))
        if (busy && checks.isEmpty()) c.addView(m.tv("Comprobando…", 14f, m.onSurfaceVar))
        for (k in checks) c.addView(row(k), m.lp(m.MP, m.WC, 0, 8, 0, 0))
        box.addView(c, m.lp(m.MP, m.WC, 0, 0, 0, 12))

        val c2 = m.card()
        c2.addView(m.tv("Enviar un informe", 18f, m.onSurface, true), m.lp(m.MP, m.WC, 0, 0, 0, 4))
        c2.addView(
            m.tv("El informe reúne todo lo necesario para resolver el problema sin pedirte nada más: tu equipo y ROM, el estado de cada pieza, los registros de la app y del módulo y la estructura de la pantalla de bloqueo. Añade además una captura o un texto contando qué viste.", 13f, m.onSurfaceVar),
            m.lp(m.MP, m.WC, 0, 0, 0, 12)
        )
        val f = RtFlow(this, m.dp(8))
        f.addView(m.filled("Enviar informe") { sendReport() })
        if (AppInfo.repoOk) f.addView(m.tonal("Informe en GitHub") { openIssue() })
        f.addView(m.tonal("Copiar informe") { copyReport() })
        c2.addView(f, m.lp(m.MP, m.WC))
        box.addView(c2, m.lp(m.MP, m.WC, 0, 0, 0, 12))

        val c3 = m.card()
        c3.addView(m.tv("Herramientas", 18f, m.onSurface, true), m.lp(m.MP, m.WC, 0, 0, 0, 10))
        val f3 = RtFlow(this, m.dp(8))
        f3.addView(m.tonal("Volver a comprobar") { refresh() })
        f3.addView(m.tonal("Capturar estado del bloqueo") { captureLock() })
        f3.addView(m.tonal("Salir del modo seguro") { exitSafe() })
        f3.addView(m.tonal("Abrir LSPosed") { Lsp.open(this) { s -> say(s) } })
        f3.addView(m.tonal("Reiniciar SystemUI") { say("Reiniciando SystemUI…"); Thread { Sx.su("killall com.android.systemui") }.start() })
        f3.addView(m.tonal("Borrar registros") { askClear() })
        c3.addView(f3, m.lp(m.MP, m.WC))
        box.addView(c3, m.lp(m.MP, m.WC, 0, 0, 0, 12))

        val c4 = m.card()
        c4.addView(m.tv("Cómo funcionan los registros", 18f, m.onSurface, true), m.lp(m.MP, m.WC, 0, 0, 0, 6))
        c4.addView(
            m.tv(
                "RomTweaker anota lo que hace y cada fallo, con fecha y detalle: errores de la app, avisos del módulo dentro de SystemUI, resultado de los comandos con root, cierres inesperados y la estructura de la pantalla de bloqueo.\n\n" +
                    "• Pesan como máximo unos 5 MB y se renuevan solos.\n" +
                    "• No se guardan textos de notificaciones, mensajes, claves ni datos de cuentas.\n" +
                    "• Nada sale del teléfono hasta que pulses «Enviar informe»: se crea un .zip que compartes por donde prefieras (Telegram, correo…) o adjuntas en GitHub.\n" +
                    "• Si SystemUI se reinicia en bucle, el módulo entra solo en modo seguro 10 minutos para que no te quedes sin pantalla.",
                13f, m.onSurfaceVar
            )
        )
        box.addView(c4, m.lp(m.MP, m.WC, 0, 0, 0, 12))
    }

    private fun refresh() {
        if (busy) return
        busy = true
        render()
        Thread {
            val r = try {
                Health.run(this)
            } catch (e: Throwable) {
                RtLog.w("salud", "falló la comprobación", e)
                listOf(Health.Check("Comprobación", 2, "Falló: " + e))
            }
            runOnUiThread {
                checks = r
                busy = false
                render()
                if (auto) { auto = false; sendReport() }
            }
        }.start()
    }

    private fun sendReport() {
        if (busy) { say("Espera un momento: estoy comprobando…"); return }
        busy = true
        say("Preparando el informe (unos segundos)…")
        Thread {
            val f = try { Health.zip(this, Health.report(this, checks, true)) } catch (e: Throwable) { RtLog.w("salud", "informe", e); null }
            runOnUiThread {
                busy = false
                if (f == null) say("No pude crear el informe. Prueba «Copiar informe».") else share(f)
            }
        }.start()
    }

    private fun share(f: File) {
        try {
            val uri = Uri.parse("content://" + RtShare.AUTH + "/" + Uri.encode(f.name))
            val i = Intent(Intent.ACTION_SEND)
            i.type = "application/zip"
            i.putExtra(Intent.EXTRA_STREAM, uri)
            i.putExtra(Intent.EXTRA_SUBJECT, "Informe de RomTweaker")
            i.putExtra(Intent.EXTRA_TEXT, Health.summary(this, checks))
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            i.clipData = ClipData.newRawUri("informe", uri)
            startActivity(Intent.createChooser(i, "Enviar informe"))
            RtLog.i("salud", "informe enviado al selector de compartir")
        } catch (e: Throwable) {
            RtLog.w("salud", "no pude abrir el selector de compartir", e)
            say("No pude abrir el selector de compartir.")
        }
    }

    private fun copyReport() {
        if (busy) { say("Espera un momento: estoy comprobando…"); return }
        busy = true
        say("Preparando el informe…")
        Thread {
            val t = try { Health.report(this, checks, false) } catch (e: Throwable) { RtLog.w("salud", "informe corto", e); "" }
            runOnUiThread {
                busy = false
                if (t.isEmpty()) { say("No pude crear el informe"); return@runOnUiThread }
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Informe de RomTweaker", t))
                say("Informe copiado (" + t.length / 1024 + " KB). Pégalo donde lo necesites.")
            }
        }.start()
    }

    private fun openIssue() {
        if (busy) { say("Espera un momento: estoy comprobando…"); return }
        busy = true
        say("Preparando el informe para GitHub…")
        Thread {
            val t = try { Health.report(this, checks, false) } catch (e: Throwable) { RtLog.w("salud", "informe corto", e); "" }
            val url = Health.issueUrl(this, checks)
            runOnUiThread {
                busy = false
                try {
                    if (t.isNotEmpty()) {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Informe de RomTweaker", t))
                    }
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    say("Informe copiado: en GitHub pégalo en el cuadro del Issue (o adjunta el .zip de «Enviar informe»).")
                } catch (e: Throwable) {
                    RtLog.w("salud", "no pude abrir GitHub", e)
                    say("No pude abrir el navegador.")
                }
            }
        }.start()
    }

    private fun captureLock() {
        val ok = Xp.putString("snap_req", System.currentTimeMillis().toString())
        RtLog.i("salud", "instantánea del bloqueo pedida al módulo (conectado=" + ok + ")")
        say(if (ok) "Pedí la instantánea. Si el reloj de bloqueo aún no se ha mostrado, bloquea y desbloquea la pantalla una vez." else "El módulo no está conectado: activa RomTweaker en LSPosed y reinicia SystemUI.")
    }

    private fun exitSafe() {
        Thread {
            val r = Sx.su("rm -f " + Health.DE + "/rt_boot.txt; killall com.android.systemui")
            RtLog.i("salud", "salida del modo seguro → " + r.first)
            runOnUiThread { say("Modo seguro reiniciado: SystemUI se está reiniciando."); refresh() }
        }.start()
    }

    private fun askClear() {
        AlertDialog.Builder(this)
            .setTitle("Borrar registros")
            .setMessage("Se borrarán los registros de la app y los cierres guardados. Los del módulo en SystemUI se renuevan solos. ¿Seguro?")
            .setPositiveButton("Borrar") { _, _ ->
                Thread {
                    RtLog.clear(this)
                    runOnUiThread { say("Registros borrados"); refresh() }
                }.start()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
