package dev.romtweaker.app

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.ZipFile

class ShapePreview(ctx: Context, private val path: Path, private val color: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        p.color = color
        val s = Math.min(width, height) / 100f
        c.save()
        c.scale(s, s)
        c.drawPath(path, p)
        c.restore()
    }
}

/** Vista previa real: carga los recursos de cada overlay compilado (sin instalarlo) y dibuja sus iconos. */
object IcoPreview {
    private val pool = Executors.newFixedThreadPool(2)
    private val ui = Handler(Looper.getMainLooper())
    private val resCache = HashMap<String, Resources?>()

    private val prefer = mapOf(
        "Paquetes de iconos" to listOf("ic_signal_cellular_4_5_bar", "ic_wifi_signal_2", "ic_lock", "ic_notifications_alerted", "perm_group_microphone"),
        "Iconos de señal" to listOf("ic_mobile_4_4_bar", "ic_mobile_2_4_bar", "ic_mobile_4_4_bar_error", "ic_mobile_0_4_bar"),
        "Iconos de Wi-Fi" to listOf("ic_wifi_3", "ic_wifi_2", "ic_wifi_1", "ic_wifi_0", "ic_wifi_3_error"),
        "Fondo de notificaciones" to listOf("notification_material_bg", "global_actions_lite_background"),
        "Marco de toasts" to listOf("toast_frame"),
        "Chip de privacidad" to listOf("privacy_chip_bg", "statusbar_privacy_chip_bg"),
        "Botones y diálogos" to listOf("qs_dialog_btn_filled", "bottom_sheet_button_background", "settingslib_progress_horizontal")
    )

    private fun apkFile(ctx: Context, id: String): File {
        val f = File(ctx.cacheDir, "pv_$id.apk")
        if (!f.exists()) {
            ctx.assets.open("iconify/$id.apk").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            f.setReadOnly()
        }
        return f
    }

    private fun res(ctx: Context, part: IcoPart): Resources? {
        synchronized(resCache) {
            if (resCache.containsKey(part.id)) return resCache[part.id]
        }
        var r: Resources? = null
        try {
            val f = apkFile(ctx, part.id)
            val pm = ctx.packageManager
            val info = pm.getPackageArchiveInfo(f.absolutePath, 0)
            val ai = info?.applicationInfo
            if (ai != null) {
                ai.sourceDir = f.absolutePath
                ai.publicSourceDir = f.absolutePath
                r = pm.getResourcesForApplication(ai)
            }
        } catch (e: Throwable) {
        }
        synchronized(resCache) { resCache[part.id] = r }
        return r
    }

    private fun listNames(ctx: Context, part: IcoPart): List<String> {
        val out = ArrayList<String>()
        try {
            ZipFile(apkFile(ctx, part.id)).use { z ->
                val e = z.entries()
                while (e.hasMoreElements()) {
                    val n = e.nextElement().name
                    if (n.startsWith("res/drawable") && n.endsWith(".xml") && !n.contains("night")) {
                        out.add(n.substringAfterLast('/').removeSuffix(".xml"))
                    }
                }
            }
        } catch (e: Throwable) {
        }
        return out.distinct().sorted()
    }

    private class Pv(val shape: String?, val icons: List<Drawable>)

    private fun load(ctx: Context, item: IcoItem, tint: Int): Pv {
        var shape: String? = null
        val out = ArrayList<Drawable>()
        for (part in item.parts) {
            val r = res(ctx, part) ?: continue
            val sid = r.getIdentifier("config_icon_mask", "string", part.pkg)
            if (sid != 0) shape = r.getString(sid)
            val th = r.newTheme()
            th.applyStyle(android.R.style.Theme_DeviceDefault, true)
            val names = LinkedHashSet<String>()
            names.addAll(prefer[item.group] ?: emptyList())
            names.addAll(listNames(ctx, part))
            var n = 0
            for (nm in names) {
                if (out.size >= 6 || n >= 4) break
                val id = r.getIdentifier(nm, "drawable", part.pkg)
                if (id == 0) continue
                try {
                    val d = r.getDrawable(id, th)
                    if (d is VectorDrawable) d.setTint(tint)
                    out.add(d)
                    n++
                } catch (e: Throwable) {
                }
            }
        }
        return Pv(shape, out)
    }

    /** Tarjeta de opción con vista previa; al tocar, aplica la opción. */
    fun tile(ctx: Context, m: M3, item: IcoItem, sel: Boolean, click: () -> Unit): View {
        val t = LinearLayout(ctx)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER_HORIZONTAL
        t.setPadding(m.dp(12), m.dp(10), m.dp(12), m.dp(8))
        val base = if (sel) m.shape(m.secContainer, 14) else m.shape(Color.TRANSPARENT, 14, 1, m.a(m.outline, 0.8f))
        t.background = m.ripple(base, 14)
        val box = LinearLayout(ctx)
        box.gravity = Gravity.CENTER
        box.minimumHeight = m.dp(40)
        t.addView(box, m.lp(m.WC, m.WC))
        t.addView(m.tv(item.label, 12f, if (sel) m.onSecContainer else m.onSurfaceVar, true), m.lp(m.WC, m.WC, 0, 6, 0, 0))
        t.setOnClickListener { click() }
        val tint = if (sel) m.onSecContainer else m.onSurface
        pool.execute {
            val pv = try { load(ctx, item, tint) } catch (e: Throwable) { null }
            if (pv != null) {
                ui.post {
                    val sp = pv.shape
                    if (sp != null) {
                        val path = SvgPath.parse(sp)
                        if (path != null) box.addView(ShapePreview(ctx, path, tint), m.lp(m.dp(40), m.dp(40)))
                    }
                    for (d in pv.icons) {
                        val iv = ImageView(ctx)
                        iv.scaleType = ImageView.ScaleType.FIT_CENTER
                        iv.setImageDrawable(d)
                        box.addView(iv, m.lp(m.dp(30), m.dp(30), 2, 0, 2, 0))
                    }
                }
            }
        }
        return t
    }
}
