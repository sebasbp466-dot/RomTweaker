package dev.romtweaker.app

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

/** Estilos de reloj predefinidos (propios, inspirados en la galería de HyperOS). */
object StudioPresets {
    class P(val name: String, val cat: String, val cfg: StudioCfg)

    val cats = listOf("Clásicos", "Cristal", "Color", "Mezcla")

    private fun mk(name: String, cat: String, layout: String, w: Int, wd: Int, ro: Int, fx: String, tr: Int, color: Int, size: Int): P {
        val c = StudioCfg()
        c.layout = layout; c.wght = w; c.wdth = wd; c.rond = ro; c.fx = fx; c.tr = tr; c.color = color; c.size = size
        return P(name, cat, c)
    }

    private const val W = 0xFFFFFFFF.toInt()
    val all: List<P> = listOf(
        mk("Grueso", "Clásicos", "stack", 800, 100, 0, "solid", 0, W, 190),
        mk("Fino alto", "Clásicos", "stack", 200, 50, 0, "solid", 0, W, 215),
        mk("Condensado", "Clásicos", "stack", 500, 50, 0, "solid", 0, W, 200),
        mk("En línea", "Clásicos", "inline", 500, 90, 0, "solid", 0, W, 230),
        mk("Cristal", "Cristal", "stack", 600, 90, 0, "glass", 35, W, 190),
        mk("Cristal fino", "Cristal", "stack", 150, 70, 0, "glass", 20, W, 210),
        mk("Líquido", "Cristal", "stack", 700, 110, 100, "glass", 50, W, 180),
        mk("Naranja", "Color", "stack", 800, 100, 0, "solid", 0, 0xFFFF7A3D.toInt(), 190),
        mk("Neón", "Color", "stack", 400, 60, 100, "solid", 0, 0xFF4ADE80.toInt(), 200),
        mk("Rosa", "Color", "stack", 700, 90, 50, "solid", 0, 0xFFFF5C9E.toInt(), 190),
        mk("Mezcla", "Mezcla", "stack", 800, 100, 0, "blend", 20, W, 190),
        mk("Mezcla fina", "Mezcla", "stack", 300, 70, 0, "blend", 10, W, 210),
        mk("Superposición", "Mezcla", "stack", 700, 100, 0, "overlay", 20, W, 190)
    )
}

/** Fondos generados (propios) y utilidades para poner un fondo en la pantalla de bloqueo. */
object StudioBg {
    private val pal = arrayOf(
        intArrayOf(0xFF0F3B3B.toInt(), 0xFF1B6E6E.toInt(), 0xFF15262B.toInt()),
        intArrayOf(0xFF3A1C71.toInt(), 0xFFD76D77.toInt(), 0xFFFFAF7B.toInt()),
        intArrayOf(0xFF0F2027.toInt(), 0xFF2C5364.toInt(), 0xFF1CB5E0.toInt()),
        intArrayOf(0xFF0B3D2E.toInt(), 0xFF3DDC97.toInt(), 0xFF1A237E.toInt()),
        intArrayOf(0xFF42275A.toInt(), 0xFF734B6D.toInt(), 0xFFFF9A9E.toInt()),
        intArrayOf(0xFF141E30.toInt(), 0xFF243B55.toInt(), 0xFF0B0F18.toInt())
    )
    val count = pal.size

    fun gen(i: Int, w: Int, h: Int): Bitmap {
        val c = pal[i.coerceIn(0, pal.size - 1)]
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w * 0.8f, h.toFloat(), c, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = RadialGradient(w * 0.2f, h * 0.25f, w * 0.7f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = RadialGradient(w * 0.85f, h * 0.72f, w * 0.8f, (c[2] and 0x00FFFFFF) or 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return b
    }

    fun setLock(ctx: Activity, bmp: Bitmap): Boolean = try {
        WallpaperManager.getInstance(ctx).setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
        true
    } catch (e: Throwable) { false }
}

/** Galería de estilos de la pantalla de bloqueo (se abre con pulsación larga en el bloqueo o desde la app). */
class LockGalleryActivity : Activity() {
    private lateinit var m: M3
    private lateinit var carousel: LinearLayout
    private lateinit var chips: LinearLayout
    private lateinit var bgRow: LinearLayout
    private var cat = "Clásicos"
    private var sel = 0
    private var bg = -1            // -1 fondo actual, 0..n generado, 100 galería
    private var custom: Bitmap? = null

    private fun list() = StudioPresets.all.filter { it.cat == cat }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try { setShowWhenLocked(true) } catch (e: Throwable) { }
        try {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(WindowInsets.Type.systemBars())
            window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (e: Throwable) { }
        m = M3(this, true)
        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF0B0F12.toInt())
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, m.dp(30), 0, m.dp(18))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(m.tonal("Salir") { finish() }, m.lp(m.WC, m.WC, 16, 0, 0, 0))
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(m.filled("Aplicar") { apply(false) }, m.lp(m.WC, m.WC, 0, 0, 16, 0))
        col.addView(top, m.lp(m.MP, m.WC))

        col.addView(m.tv("Pantalla de bloqueo", 26f, 0xFFFFFFFF.toInt(), true), m.lp(m.WC, m.WC, 22, 14, 0, 6))
        chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        val cs = HorizontalScrollView(this)
        cs.isHorizontalScrollBarEnabled = false
        cs.addView(chips)
        col.addView(cs, m.lp(m.MP, m.WC, 16, 0, 16, 10))

        carousel = LinearLayout(this)
        carousel.orientation = LinearLayout.HORIZONTAL
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.clipToPadding = false
        hs.setPadding(m.dp(16), 0, m.dp(16), 0)
        hs.addView(carousel)
        col.addView(hs, LinearLayout.LayoutParams(-1, 0, 1f))

        col.addView(m.tv("Fondo", 13f, 0xCCFFFFFF.toInt()), m.lp(m.WC, m.WC, 22, 10, 0, 6))
        bgRow = LinearLayout(this)
        bgRow.orientation = LinearLayout.HORIZONTAL
        val bs = HorizontalScrollView(this)
        bs.isHorizontalScrollBarEnabled = false
        bs.addView(bgRow)
        col.addView(bs, m.lp(m.MP, m.WC, 16, 0, 16, 8))

        col.addView(m.tonal("Personalizar este estilo") { customize() }, m.lp(m.WC, m.WC, 0, 4, 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        rebuild()
    }

    private fun bgBitmap(w: Int, h: Int): Bitmap? = when {
        bg in 0 until StudioBg.count -> StudioBg.gen(bg, w, h)
        bg == 100 -> custom
        else -> null
    }

    private fun rebuild() {
        chips.removeAllViews()
        for (c in StudioPresets.cats) {
            val x = c
            chips.addView(m.chip(x, cat == x) { cat = x; sel = 0; rebuild() }, m.lp(m.WC, m.WC, 0, 0, 8, 0))
        }
        carousel.removeAllViews()
        val dm = resources.displayMetrics
        val sw = dm.widthPixels
        val sh = dm.heightPixels
        val pw = (sw * 0.58f).toInt()
        val ph = (pw * sh / sw.toFloat()).toInt()
        val k = pw / sw.toFloat()
        val items = list()
        for (i in items.indices) {
            val pr = items[i]
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            val card = FrameLayout(this)
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            val pb = bgBitmap(sw / 4, sh / 4)
            if (pb != null) iv.setImageBitmap(pb)
            else {
                val wd = LockStudio.wallpaper(this)
                if (wd != null) iv.setImageDrawable(wd) else iv.setImageDrawable(BitmapDrawable(resources, StudioBg.gen(0, sw / 4, sh / 4)))
            }
            card.addView(iv, FrameLayout.LayoutParams(-1, -1))
            val dim = View(this)
            dim.setBackgroundColor(0x22000000)
            card.addView(dim, FrameLayout.LayoutParams(-1, -1))
            val screen = FrameLayout(this)
            val clk = StudioClock(this)
            clk.applyConfig(pr.cfg)
            val clp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            clp.topMargin = (pr.cfg.top * dm.density).toInt()
            screen.addView(clk, clp)
            screen.pivotX = 0f
            screen.pivotY = 0f
            screen.scaleX = k
            screen.scaleY = k
            card.addView(screen, FrameLayout.LayoutParams(sw, sh))
            card.clipToOutline = true
            card.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, m.dp(26).toFloat()) }
            }
            val ring = GradientDrawable()
            ring.cornerRadius = m.dp(26).toFloat()
            ring.setColor(Color.TRANSPARENT)
            ring.setStroke(m.dp(3), if (i == sel) 0xFF4DA3FF.toInt() else Color.TRANSPARENT)
            card.foreground = ring
            card.setOnClickListener { sel = i; rebuild() }
            cell.addView(card, LinearLayout.LayoutParams(pw, ph))
            cell.addView(m.tv(pr.name, 14f, if (i == sel) 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt(), i == sel), m.lp(m.WC, m.WC, 0, 8, 0, 0))
            carousel.addView(cell, m.lp(m.WC, m.WC, 0, 0, 14, 0))
        }
        bgRow.removeAllViews()
        fun dot(idx: Int, b: Bitmap?, label: String?) {
            val v = FrameLayout(this)
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            if (b != null) iv.setImageBitmap(b) else iv.setBackgroundColor(0xFF2B3238.toInt())
            v.addView(iv, FrameLayout.LayoutParams(-1, -1))
            if (label != null) v.addView(m.tv(label, 11f, 0xFFFFFFFF.toInt(), true), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            v.clipToOutline = true
            v.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v2: View, o: Outline) { o.setRoundRect(0, 0, v2.width, v2.height, m.dp(14).toFloat()) }
            }
            val ring = GradientDrawable()
            ring.cornerRadius = m.dp(14).toFloat()
            ring.setColor(Color.TRANSPARENT)
            ring.setStroke(m.dp(3), if (bg == idx) 0xFF4DA3FF.toInt() else Color.TRANSPARENT)
            v.foreground = ring
            v.setOnClickListener {
                if (idx == 100) pickImage() else { bg = idx; rebuild() }
            }
            bgRow.addView(v, m.lp(m.dp(44), m.dp(64), 0, 0, 8, 0))
        }
        dot(-1, null, "Actual")
        for (i in 0 until StudioBg.count) dot(i, StudioBg.gen(i, 60, 100), null)
        dot(100, custom, "Galería")
    }

    private fun pickImage() {
        try {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "image/*"
            startActivityForResult(i, 77)
        } catch (e: Throwable) { Toast.makeText(this, "No se pudo abrir la galería", Toast.LENGTH_SHORT).show() }
    }

    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        if (rc != 77 || res != RESULT_OK) return
        val u = data?.data ?: return
        try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
            var ss = 1
            while (maxOf(o.outWidth, o.outHeight) / ss > 2400) ss *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = ss
            custom = contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o2) }
            if (custom != null) { bg = 100; rebuild() }
        } catch (e: Throwable) { Toast.makeText(this, "No se pudo leer la imagen", Toast.LENGTH_SHORT).show() }
    }

    /** Aplica el fondo elegido (si no es el actual) y guarda el reloj elegido. */
    private fun apply(thenEdit: Boolean) {
        val pr = list().getOrNull(sel) ?: return
        val dm = resources.displayMetrics
        val bmp = bgBitmap(dm.widthPixels, dm.heightPixels)
        Thread {
            var ok = true
            if (bmp != null) ok = StudioBg.setLock(this, bmp)
            val p = getSharedPreferences("lock", MODE_PRIVATE)
            p.edit().putString("sc_cfg", pr.cfg.enc()).putBoolean("sc_on", true).putBoolean("ic_on", false).apply()
            val pushed = StudioPush.push(p)
            runOnUiThread {
                if (thenEdit) {
                    val i = Intent(this, ClockEditActivity::class.java)
                    startActivity(i)
                    finish()
                } else {
                    Toast.makeText(this, if (!ok) "No pude cambiar el fondo" else if (pushed) "Aplicado" else "Guardado, pero el módulo LSPosed no está activo", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }.start()
    }

    private fun customize() { apply(true) }
}
