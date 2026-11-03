package dev.romtweaker.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Hoja inferior de IA (Gemini): 4 variantes de reloj por texto, reloj para tu fondo, y fondos generados. */
class AiSheet(private val act: Activity, private val host: Host) : FrameLayout(act) {
    interface Host {
        fun style(c: StudioCfg)
        fun curCfg(): StudioCfg
        fun wallBmp(): Bitmap?
        fun useWall(b: Bitmap)
        fun closeSheet()
    }

    private val ink = 0xFF14171B.toInt()
    private val inkDim = 0x99141719.toInt()
    private var mode = 0
    private val prefs = act.getSharedPreferences("lock", Activity.MODE_PRIVATE)
    private val sheet = LinearLayout(act)
    private val modeTv = ArrayList<TextView>()
    private val result = FrameLayout(act)
    private val input = EditText(act)
    private val chips = LinearLayout(act)
    private val keyRow = LinearLayout(act)
    private var busy = false
    private var picked = -1

    private fun dp(v: Float) = Hx.dp(act, v)

    private fun rounded(c: Int, r: Float, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = dp(r).toFloat()
        g.setColor(c)
        if (stroke != 0) g.setStroke(dp(1f), stroke)
        return g
    }

    private fun txt(t: String, sp: Float, c: Int, bold: Boolean = false): TextView {
        val v = TextView(act)
        v.text = t
        v.setTextColor(c)
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) v.typeface = Typeface.DEFAULT_BOLD
        return v
    }

    private fun chip(label: String, sel: Boolean, click: () -> Unit): TextView {
        val t = txt(label, 12.5f, if (sel) Color.WHITE else ink)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        t.background = rounded(if (sel) Hx.BLUE else 0x14000000, 40f)
        t.setOnClickListener { click() }
        Hx.press(t)
        return t
    }

    init {
        setBackgroundColor(0x66000000)
        isClickable = true
        setOnClickListener { close() }
        sheet.orientation = LinearLayout.VERTICAL
        sheet.isClickable = true
        sheet.setPadding(dp(16f), dp(10f), dp(16f), dp(18f))
        val bg = GradientDrawable()
        bg.setColor(0xF4F4F6FA.toInt())
        val r = dp(30f).toFloat()
        bg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        sheet.background = bg

        val grab = View(act)
        grab.background = rounded(0x33000000, 3f)
        sheet.addView(grab, LinearLayout.LayoutParams(dp(38f), dp(4f)).also { it.gravity = Gravity.CENTER_HORIZONTAL })

        val head = LinearLayout(act)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(txt("✦  IA · Gemini", 17f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        val keyBtn = chip("Clave", false) { keyRow.visibility = if (keyRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        head.addView(keyBtn)
        val x = txt("✕", 15f, ink)
        x.gravity = Gravity.CENTER
        x.background = rounded(0x14000000, 20f)
        x.setOnClickListener { close() }
        val xl = LinearLayout.LayoutParams(dp(36f), dp(36f))
        xl.leftMargin = dp(8f)
        head.addView(x, xl)
        sheet.addView(head, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(8f) })

        // modos
        val seg = LinearLayout(act)
        seg.orientation = LinearLayout.HORIZONTAL
        seg.background = rounded(0x14000000, 40f)
        seg.setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
        for ((i, n) in listOf("Reloj", "Para mi fondo", "Fondo").withIndex()) {
            val t = txt(n, 13f, ink)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(8f), 0, dp(8f))
            t.setOnClickListener { mode = i; refreshMode() }
            seg.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
            modeTv.add(t)
        }
        sheet.addView(seg, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(12f) })

        // clave
        keyRow.orientation = LinearLayout.VERTICAL
        val note = txt("Pega tu clave de la API de Gemini (gratis en aistudio.google.com → Get API key). Se guarda solo en tu móvil.", 12f, inkDim)
        keyRow.addView(note)
        val kr = LinearLayout(act)
        kr.orientation = LinearLayout.HORIZONTAL
        kr.gravity = Gravity.CENTER_VERTICAL
        val ke = EditText(act)
        ke.hint = "Clave API"
        ke.setSingleLine(true)
        ke.setText(prefs.getString("gem_key", ""))
        ke.setTextColor(ink)
        ke.setHintTextColor(inkDim)
        ke.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        ke.background = rounded(0x14000000, 14f)
        ke.setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        kr.addView(ke, LinearLayout.LayoutParams(0, -2, 1f))
        val ok = chip("Guardar", true) {
            prefs.edit().putString("gem_key", ke.text.toString().trim()).apply()
            keyRow.visibility = View.GONE
            show("Clave guardada. Escribe qué quieres y pulsa ↑", false)
        }
        kr.addView(ok, LinearLayout.LayoutParams(-2, -2).also { it.leftMargin = dp(8f) })
        keyRow.addView(kr, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(6f) })
        keyRow.visibility = if (key().isEmpty()) View.VISIBLE else View.GONE
        sheet.addView(keyRow, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(10f) })

        // resultado
        sheet.addView(result, LinearLayout.LayoutParams(-1, dp(196f)).also { it.topMargin = dp(10f) })

        // sugerencias
        val hs = HorizontalScrollView(act)
        hs.isHorizontalScrollBarEnabled = false
        chips.orientation = LinearLayout.HORIZONTAL
        hs.addView(chips)
        sheet.addView(hs, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(4f) })

        // entrada
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        input.setTextColor(ink)
        input.setHintTextColor(inkDim)
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        input.maxLines = 3
        input.background = rounded(0x14000000, 26f)
        input.setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        val send = txt("↑", 20f, Color.WHITE, true)
        send.gravity = Gravity.CENTER
        val sg = GradientDrawable(); sg.shape = GradientDrawable.OVAL; sg.setColor(Hx.BLUE)
        send.background = sg
        send.setOnClickListener { run() }
        Hx.press(send)
        row.addView(send, LinearLayout.LayoutParams(dp(46f), dp(46f)).also { it.leftMargin = dp(8f) })
        sheet.addView(row, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(8f) })

        addView(sheet, LayoutParams(-1, -2, Gravity.BOTTOM))
        refreshMode()
        sheet.post {
            sheet.translationY = sheet.height.toFloat()
            sheet.animate().translationY(0f).setDuration(420).setInterpolator(DecelerateInterpolator(1.8f)).start()
        }
        alpha = 0f
        animate().alpha(1f).setDuration(220).start()
    }

    private fun key(): String = prefs.getString("gem_key", "") ?: ""

    fun close() {
        sheet.animate().translationY(sheet.height.toFloat()).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
        animate().alpha(0f).setDuration(260).withEndAction { host.closeSheet() }.start()
    }

    private fun refreshMode() {
        for ((i, t) in modeTv.withIndex()) {
            t.background = rounded(if (i == mode) Color.WHITE else Color.TRANSPARENT, 40f)
            t.typeface = if (i == mode) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        input.hint = when (mode) { 0 -> "Describe tu reloj ideal…"; 1 -> "Opcional: estilo que buscas para tu fondo"; else -> "Describe el fondo que quieres…" }
        chips.removeAllViews()
        val sug = when (mode) {
            0 -> listOf("Elegante dorado con serif", "Minimal neón oscuro", "Retro ochentero", "Grueso y divertido", "De aguja moderno")
            1 -> listOf("Que contraste bien", "Cristal elegante", "Colorido", "Muy minimal")
            else -> listOf("Atardecer en la montaña", "Ciudad cyberpunk de noche", "Bosque con niebla", "Océano abstracto", "Galaxia morada")
        }
        val hist = (prefs.getString("ai_hist", "") ?: "").split("\n").filter { it.isNotEmpty() }.take(4)
        for (h in hist) addChip("↺ $h", h)
        for (s in sug) addChip(s, s)
        picked = -1
        show(when (mode) {
            0 -> "Escribe cómo quieres el reloj y te propongo 4 estilos para elegir."
            1 -> "Miro tu fondo y diseño relojes que combinen con sus colores."
            else -> "Genera un fondo nuevo con IA y úsalo en tu pantalla."
        }, false)
    }

    private fun addChip(label: String, text: String) {
        val c = chip(label, false) { input.setText(text); input.setSelection(text.length) }
        chips.addView(c, LinearLayout.LayoutParams(-2, -2).also { it.rightMargin = dp(8f) })
    }

    private fun show(msg: String, err: Boolean) {
        result.removeAllViews()
        val t = txt(msg, 14f, if (err) 0xFFC62828.toInt() else inkDim)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(20f), 0, dp(20f), 0)
        result.addView(t, LayoutParams(-1, -1))
    }

    private fun loading() {
        result.removeAllViews()
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        val sp = txt("✦", 40f, Hx.BLUE, true)
        sp.gravity = Gravity.CENTER
        col.addView(sp)
        col.addView(txt("Gemini está pensando…", 14f, inkDim))
        result.addView(col, LayoutParams(-1, -1))
        sp.animate().scaleX(1.35f).scaleY(1.35f).rotation(180f).setDuration(700).withEndAction(object : Runnable {
            var up = false
            override fun run() {
                if (!sp.isAttachedToWindow || !busy) return
                up = !up
                sp.animate().scaleX(if (up) 1f else 1.35f).scaleY(if (up) 1f else 1.35f).rotationBy(180f).setDuration(700).withEndAction(this).start()
            }
        }).start()
    }

    private fun run() {
        if (busy) return
        val k = key()
        if (k.isEmpty()) { keyRow.visibility = View.VISIBLE; show("Primero pega tu clave de Gemini (botón Clave).", true); return }
        val p = input.text.toString().trim()
        if (p.isEmpty() && mode != 1) { show("Escribe una descripción.", true); return }
        if (p.isNotEmpty()) {
            val h = (listOf(p) + (prefs.getString("ai_hist", "") ?: "").split("\n").filter { it.isNotEmpty() && it != p }).take(6)
            prefs.edit().putString("ai_hist", h.joinToString("\n")).apply()
        }
        busy = true
        loading()
        val wall = host.wallBmp()
        val cur = host.curCfg()
        Thread {
            if (mode == 2) {
                val r = StudioAI.wallpaper(k, p)
                post {
                    busy = false
                    if (r.first == null) show("No pude: " + r.second, true) else showWall(r.first!!)
                }
            } else {
                val img = if (mode == 1 && wall != null) StudioAI.jpegB64(wall) else null
                val r = StudioAI.clockVariants(k, p, img)
                post {
                    busy = false
                    if (r.first.isEmpty()) show("No pude: " + (r.second ?: "respuesta vacía"), true) else showVariants(r.first, cur, wall)
                }
            }
        }.start()
    }

    private fun showVariants(list: List<org.json.JSONObject>, base: StudioCfg, wall: Bitmap?) {
        result.removeAllViews()
        val hs = HorizontalScrollView(act)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(4f), 0, 0)
        val tw = dp(104f)
        val th = (tw * act.resources.displayMetrics.heightPixels / act.resources.displayMetrics.widthPixels.toFloat()).toInt()
        val bg = wall ?: Hx.bgBitmap(act)
        val tiles = ArrayList<View>()
        for ((i, j) in list.take(4).withIndex()) {
            val c = base.copy()
            StudioAI.applyJson(c, j)
            val t = StudioTile.make(act, c, tw, th, 16f, bg)
            t.alpha = 0f
            t.scaleX = 0.8f; t.scaleY = 0.8f
            t.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(i * 90L).setDuration(380).setInterpolator(OvershootInterpolator(2f)).start()
            t.setOnClickListener {
                picked = i
                host.style(c)
                for ((k, v) in tiles.withIndex()) {
                    val g = GradientDrawable(); g.cornerRadius = dp(16f).toFloat(); g.setColor(Color.TRANSPARENT)
                    g.setStroke(dp(3f), if (k == i) Hx.BLUE else Color.TRANSPARENT)
                    (v as FrameLayout).foreground = g
                }
            }
            Hx.press(t)
            tiles.add(t)
            row.addView(t, LinearLayout.LayoutParams(tw, th).also { it.rightMargin = dp(10f) })
        }
        hs.addView(row)
        result.addView(hs, LayoutParams(-1, -1))
    }

    private fun showWall(b: Bitmap) {
        result.removeAllViews()
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val iv = ImageView(act)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.setImageBitmap(b)
        iv.clipToOutline = true
        iv.outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(16f).toFloat()) } }
        val w = dp(104f)
        row.addView(iv, LinearLayout.LayoutParams(w, (w * b.height / b.width.toFloat()).toInt().coerceAtMost(dp(190f))))
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16f), 0, 0, 0)
        col.addView(txt("Fondo generado", 15f, ink, true))
        col.addView(txt("Úsalo y elige después si va en bloqueo, inicio o ambas (pestaña Fondo).", 12f, inkDim))
        col.addView(chip("Usar este fondo", true) { host.useWall(b); close() }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = dp(10f) })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        result.addView(row, LayoutParams(-1, -1))
        iv.alpha = 0f
        iv.animate().alpha(1f).setDuration(400).start()
    }
}
