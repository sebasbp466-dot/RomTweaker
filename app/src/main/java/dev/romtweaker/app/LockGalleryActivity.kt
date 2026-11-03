package dev.romtweaker.app

import android.app.Activity
import android.app.WallpaperManager
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Estilos de reloj predefinidos (propios, inspirados en la galería de HyperOS y en Iconify). */
object StudioPresets {
    class P(val name: String, val cat: String, val cfg: StudioCfg)

    private const val W = 0xFFFFFFFF.toInt()
    val icPages = 5
    val cats: List<String> = listOf("Diseños", "Centrados", "HyperOS 2", "Agujas", "Palabras", "Clásicos", "Cristal", "Revista", "Material You", "Neón", "Color", "Mezcla", "Vertical", "Contorno") + (0 until icPages).map { "Iconify ${it * 12 + 1}–${it * 12 + 12}" }

    private fun st(name: String, cat: String, f: StudioCfg.() -> Unit): P {
        val c = StudioCfg()
        c.f()
        return P(name, cat, c)
    }

    private fun mk(name: String, cat: String, layout: String, w: Int, wd: Int, ro: Int, fx: String, tr: Int, color: Int, size: Int, font: String = "flex", ol: Int = 0): P =
        st(name, cat) { this.layout = layout; wght = w; wdth = wd; rond = ro; this.fx = fx; this.tr = tr; this.color = color; this.size = size; this.font = font; this.ol = ol }

    private val base: List<P> = listOf(
        // Centrados al estilo Xiaomi: alto y fino, pesado condensado, Didone, escalonado...
        st("Alto fino", "Centrados") { layout = "stack"; wght = 160; wdth = 35; fx = "solid"; size = 235 },
        st("Pesado condensado", "Centrados") { layout = "stack"; wght = 900; wdth = 35; fx = "solid"; size = 225 },
        st("Alto cristal", "Centrados") { layout = "stack"; wght = 200; wdth = 35; fx = "glass"; tr = 30; size = 235 },
        st("Línea alta", "Centrados") { layout = "inline"; wght = 300; wdth = 35; fx = "solid"; size = 420; gap = 6 },
        st("Línea cálida", "Centrados") { layout = "inline"; font = "antonio"; wght = 500; fx = "solid"; size = 360; color = 0xFFFFD9A0.toInt(); c2 = 0xFFFF7A3D.toInt() },
        st("Didone", "Centrados") { layout = "stack"; font = "bodoni"; wght = 500; fx = "overlay"; tr = 20; size = 200 },
        st("Didone cristal", "Centrados") { layout = "stack"; font = "bodoni"; wght = 400; fx = "glass"; tr = 35; size = 200 },
        st("Escalonado", "Centrados") { layout = "grid"; font = "italiana"; fx = "solid"; size = 220; gap = 14; color = 0xFFFFE6E6.toInt() },
        st("Diagonal", "Centrados") { layout = "diag"; wght = 300; wdth = 60; fx = "solid"; size = 190 },
        st("Anton", "Centrados") { layout = "stack"; font = "anton"; fx = "solid"; size = 205; gap = 6 },
        st("Estrecho", "Centrados") { layout = "stack"; font = "bigshoulders"; wght = 800; fx = "solid"; size = 225 },
        st("Bebas", "Centrados") { layout = "inline"; font = "bebas"; fx = "solid"; size = 400 },

        // HyperOS 2 / populares
        st("Bauhaus", "HyperOS 2") { layout = "stack"; wght = 900; wdth = 130; rond = 100; fx = "solid"; size = 170; color = 0xFFFFC857.toInt() },
        st("Urbano", "HyperOS 2") { layout = "stack"; font = "anton"; xs = 85; fx = "solid"; size = 215; gap = 4; color = 0xFFFFE14D.toInt() },
        st("Póster", "HyperOS 2") { layout = "stack"; font = "bebas"; fx = "solid"; size = 260; gap = 0; color = 0xFFFF5C5C.toInt() },
        st("Retro", "HyperOS 2") { layout = "inline"; font = "syne"; wght = 800; fx = "solid"; size = 380 },
        st("Europeo", "HyperOS 2") { layout = "stack"; font = "antonio"; wght = 100; xs = 120; gap = 22; fx = "solid"; size = 230 },
        st("Periódico", "HyperOS 2") { layout = "col"; font = "dmserif"; fx = "solid"; size = 190 },
        st("Oriental", "HyperOS 2") { layout = "grid"; wght = 300; wdth = 60; fx = "solid"; size = 230; gap = 12; color = 0xFFFFE6E6.toInt(); c2 = 0xFFFF4D4D.toInt() },
        st("Gigante fino", "HyperOS 2") { layout = "stack"; wght = 100; wdth = 150; fx = "solid"; size = 220 },
        st("Compacto", "HyperOS 2") { layout = "stack"; wght = 900; wdth = 60; gap = 0; fx = "solid"; size = 230 },
        st("Cristal oriental", "HyperOS 2") { layout = "grid"; wght = 200; wdth = 50; fx = "glass"; tr = 35; size = 230 },
        st("Mezcla bold", "HyperOS 2") { layout = "stack"; font = "anton"; fx = "blend"; tr = 10; size = 205 },
        st("Aurora", "HyperOS 2") { layout = "inline"; font = "bodoni"; fx = "solid"; size = 360; color = 0xFFA0F0FF.toInt(); c2 = 0xFFFF9AD5.toInt() },

        // Relojes de agujas
        st("Minimal", "Agujas") { layout = "analog"; an = 1; fx = "solid"; size = 170 },
        st("Bauhaus aguja", "Agujas") { layout = "analog"; an = 2; fx = "solid"; size = 170; color = 0xFFFF6B3D.toInt(); c2 = 0xFFFFFFFF.toInt() },
        st("Puntos", "Agujas") { layout = "analog"; an = 3; fx = "solid"; size = 170; c2 = 0xFFFFD60A.toInt() },
        st("Romano", "Agujas") { layout = "analog"; an = 4; fx = "solid"; size = 170; c2 = 0xFFFFD9A0.toInt() },
        st("Sector", "Agujas") { layout = "analog"; an = 5; fx = "solid"; size = 170; c2 = 0xFF4DA3FF.toInt() },
        st("Anillo", "Agujas") { layout = "analog"; an = 6; fx = "solid"; size = 170; c2 = 0xFFFF5C9E.toInt() },
        st("Piruleta", "Agujas") { layout = "analog"; an = 7; fx = "solid"; size = 170; color = 0xFFFF9AD5.toInt(); c2 = 0xFFB388FF.toInt() },
        st("Números", "Agujas") { layout = "analog"; an = 8; fx = "solid"; size = 170; c2 = 0xFFFF7A3D.toInt() },
        st("Barras", "Agujas") { layout = "analog"; an = 9; fx = "solid"; size = 170; c2 = 0xFF4ADE80.toInt() },
        st("Degradado", "Agujas") { layout = "analog"; an = 10; fx = "solid"; size = 170; color = 0xFFFFD36B.toInt(); c2 = 0xFFFF4E8A.toInt() },
        st("Órbita", "Agujas") { layout = "analog"; an = 11; fx = "solid"; size = 170; c2 = 0xFFFFD60A.toInt() },
        st("Aguja larga", "Agujas") { layout = "analog"; an = 12; fx = "solid"; size = 170; c2 = 0xFFFF5C5C.toInt() },
        st("Cristal aguja", "Agujas") { layout = "analog"; an = 1; fx = "glass"; tr = 25; size = 170 },
        st("Material aguja", "Agujas") { layout = "analog"; an = 2; fx = "solid"; size = 170; cmode = 3 },

        // Reloj con palabras
        st("Texto", "Palabras") { layout = "words"; wght = 600; fx = "solid"; size = 150 },
        st("Texto serif", "Palabras") { layout = "words"; font = "bodoni"; fx = "solid"; size = 150 },
        st("Texto fino", "Palabras") { layout = "words"; wght = 200; wdth = 80; fx = "solid"; size = 160 },
        st("Texto cursivo", "Palabras") { layout = "words"; font = "caveat"; wght = 600; fx = "solid"; size = 170; color = 0xFFFFF3A3.toInt() },

        mk("Grueso", "Clásicos", "stack", 800, 100, 0, "solid", 0, W, 190),
        mk("Fino alto", "Clásicos", "stack", 200, 50, 0, "solid", 0, W, 215),
        mk("Condensado", "Clásicos", "stack", 500, 50, 0, "solid", 0, W, 200),
        mk("En línea", "Clásicos", "inline", 500, 90, 0, "solid", 0, W, 230),
        mk("Ancho", "Clásicos", "stack", 500, 140, 0, "solid", 0, W, 170),
        mk("Redondo", "Clásicos", "stack", 600, 100, 100, "solid", 0, W, 190),
        mk("Cuadrícula", "Clásicos", "grid", 600, 80, 40, "solid", 0, W, 200),

        mk("Cristal", "Cristal", "stack", 600, 90, 0, "glass", 35, W, 190),
        mk("Cristal fino", "Cristal", "stack", 150, 70, 0, "glass", 20, W, 210),
        mk("Líquido", "Cristal", "stack", 700, 110, 100, "glass", 50, W, 180),
        mk("Cristal ancho", "Cristal", "stack", 500, 130, 40, "glass", 40, W, 175),
        mk("Hielo", "Cristal", "stack", 300, 80, 0, "glass", 60, 0xFFBFE9FF.toInt(), 200),
        mk("Cristal línea", "Cristal", "inline", 400, 70, 60, "glass", 40, W, 330),

        st("Serif", "Revista") { layout = "stack"; font = "serif"; wght = 400; fx = "solid"; size = 190 },
        st("Serif fino", "Revista") { layout = "stack"; font = "serif"; wght = 300; fx = "solid"; size = 205 },
        st("Serif en línea", "Revista") { layout = "inline"; font = "serif"; wght = 500; fx = "solid"; size = 230 },
        st("Doodle", "Revista") { layout = "stack"; font = "caveat"; wght = 700; fx = "solid"; size = 220; color = 0xFFFFF3A3.toInt() },
        st("Editorial", "Revista") { layout = "stack"; font = "dmserif"; fx = "solid"; size = 190 },
        st("Syne", "Revista") { layout = "stack"; font = "syne"; wght = 800; fx = "solid"; size = 175 },
        mk("Mono", "Revista", "stack", 500, 100, 0, "solid", 0, W, 170, "mono"),

        st("Material", "Material You") { layout = "stack"; wght = 650; rond = 100; fx = "solid"; size = 190; cmode = 2 },
        st("Material dúo", "Material You") { layout = "stack"; wght = 800; rond = 60; fx = "solid"; size = 190; cmode = 3 },
        st("Material línea", "Material You") { layout = "inline"; wght = 500; rond = 100; fx = "solid"; size = 240; cmode = 2 },
        st("Material cristal", "Material You") { layout = "stack"; wght = 600; rond = 100; fx = "glass"; tr = 30; size = 190; cmode = 2 },
        st("Adaptado", "Material You") { layout = "stack"; wght = 700; fx = "solid"; size = 190; cmode = 1 },
        st("Adaptado fino", "Material You") { layout = "stack"; wght = 200; wdth = 60; fx = "solid"; size = 215; cmode = 1; c2 = 1 },

        st("Neón verde", "Neón") { layout = "stack"; wght = 400; wdth = 60; rond = 100; fx = "solid"; size = 200; color = 0xFF4ADE80.toInt(); glow = 60 },
        st("Neón rosa", "Neón") { layout = "stack"; wght = 600; rond = 60; fx = "solid"; size = 190; color = 0xFFFF5C9E.toInt(); glow = 70 },
        st("Neón azul", "Neón") { layout = "inline"; wght = 400; fx = "solid"; size = 240; color = 0xFF4DA3FF.toInt(); glow = 60 },
        st("Resplandor", "Neón") { layout = "stack"; font = "antonio"; wght = 400; fx = "solid"; size = 220; color = 0xFFFFE08A.toInt(); glow = 45 },
        st("Sombra", "Neón") { layout = "stack"; wght = 800; fx = "solid"; size = 190; shadow = 70 },

        mk("Naranja", "Color", "stack", 800, 100, 0, "solid", 0, 0xFFFF7A3D.toInt(), 190),
        mk("Neón", "Color", "stack", 400, 60, 100, "solid", 0, 0xFF4ADE80.toInt(), 200),
        mk("Rosa", "Color", "stack", 700, 90, 50, "solid", 0, 0xFFFF5C9E.toInt(), 190),
        mk("Violeta", "Color", "stack", 700, 100, 60, "solid", 0, 0xFFB388FF.toInt(), 190),
        mk("Dorado", "Color", "stack", 300, 70, 0, "solid", 0, 0xFFFFD60A.toInt(), 205),
        mk("Menta", "Color", "inline", 600, 90, 100, "solid", 0, 0xFF4ADE80.toInt(), 230),
        st("Atardecer", "Color") { layout = "stack"; wght = 800; fx = "solid"; size = 190; color = 0xFFFFD36B.toInt(); c2 = 0xFFFF4E8A.toInt() },

        mk("Mezcla", "Mezcla", "stack", 800, 100, 0, "blend", 20, W, 190),
        mk("Mezcla fina", "Mezcla", "stack", 300, 70, 0, "blend", 10, W, 210),
        mk("Superposición", "Mezcla", "stack", 700, 100, 0, "overlay", 20, W, 190),
        mk("Superpos. fina", "Mezcla", "stack", 300, 70, 0, "overlay", 30, W, 210),
        st("Mezcla alta", "Mezcla") { layout = "stack"; wght = 400; wdth = 35; fx = "blend"; tr = 10; size = 235 },

        mk("Vertical fino", "Vertical", "col", 200, 60, 0, "solid", 0, W, 200),
        mk("Vertical grueso", "Vertical", "col", 800, 90, 0, "solid", 0, W, 200),
        mk("Vertical cristal", "Vertical", "col", 500, 80, 0, "glass", 35, W, 200),
        mk("Vertical color", "Vertical", "col", 700, 70, 60, "solid", 0, 0xFFFF7A3D.toInt(), 200),
        st("Vertical serif", "Vertical") { layout = "col"; font = "bodoni"; fx = "solid"; size = 190 },

        mk("Contorno", "Contorno", "stack", 500, 90, 0, "solid", 0, W, 190, "flex", 1),
        mk("Contorno fino", "Contorno", "stack", 300, 70, 0, "solid", 0, W, 210, "flex", 1),
        mk("Contorno vertical", "Contorno", "col", 400, 70, 0, "solid", 0, W, 200, "flex", 1),
        st("Contorno serif", "Contorno") { layout = "stack"; font = "bodoni"; fx = "solid"; ol = 1; size = 200 },
        st("Contorno línea", "Contorno") { layout = "inline"; wght = 300; wdth = 50; fx = "solid"; ol = 1; size = 360 }
    )

    private val icons: List<P> = (1..IcClocks.COUNT).map { n ->
        st("Iconify $n", "Iconify ${((n - 1) / 12) * 12 + 1}–${((n - 1) / 12) * 12 + 12}") { ic = n; cmode = 5; fx = "solid"; top = 100f; size = 170 }
    }

    private class Pal(val name: String, val c1: Int, val c2: Int)
    private val pals = listOf(
        Pal("Coral", 0xFFFF8A6B.toInt(), 0xFFFFD2B8.toInt()), Pal("Océano", 0xFF6EC8FF.toInt(), 0xFF2F6BFF.toInt()),
        Pal("Bosque", 0xFFA6F0B2.toInt(), 0xFF2FAE6A.toInt()), Pal("Lavanda", 0xFFD8C2FF.toInt(), 0xFF8E6BFF.toInt()),
        Pal("Dorado", 0xFFFFE6A3.toInt(), 0xFFF0A400.toInt()), Pal("Hielo", 0xFFEAF8FF.toInt(), 0xFF9AD8FF.toInt()),
        Pal("Fuego", 0xFFFFD36B.toInt(), 0xFFFF3D2E.toInt()), Pal("Menta", 0xFFC9FFE9.toInt(), 0xFF34D8A0.toInt()),
        Pal("Rosa", 0xFFFFC2DD.toInt(), 0xFFFF4D9A.toInt()), Pal("Noche", 0xFFB9C4FF.toInt(), 0xFF3B3FA8.toInt()),
        Pal("Arena", 0xFFFFF1DC.toInt(), 0xFFD9A86C.toInt()), Pal("Cereza", 0xFFFFB3C0.toInt(), 0xFFC21F4B.toInt()),
        Pal("Neón", 0xFFB6FF3D.toInt(), 0xFF00E5FF.toInt()), Pal("Atardecer", 0xFFFFE08A.toInt(), 0xFFFF5E8A.toInt()),
        Pal("Aurora", 0xFF9AFFD1.toInt(), 0xFF7A5CFF.toInt()), Pal("Mármol", 0xFFFFFFFF.toInt(), 0xFFB7BDC6.toInt()),
        Pal("Tinta", 0xFF16181D.toInt(), 0xFF4B5563.toInt()), Pal("Mandarina", 0xFFFFC46B.toInt(), 0xFFFF6A00.toInt()),
        Pal("Uva", 0xFFE0B3FF.toInt(), 0xFF7B1FA2.toInt()), Pal("Turquesa", 0xFFB3FFF5.toInt(), 0xFF00A5A5.toInt())
    )

    private fun recipe(i: Int, c: StudioCfg) {
        c.fx = "solid"
        when (i) {
            0 -> { c.layout = "stack"; c.wght = 800; c.wdth = 100; c.size = 190 }
            1 -> { c.layout = "stack"; c.wght = 200; c.wdth = 50; c.size = 215 }
            2 -> { c.layout = "inline"; c.wght = 500; c.wdth = 90; c.size = 230 }
            3 -> { c.layout = "stack"; c.font = "bodoni"; c.size = 200 }
            4 -> { c.layout = "grid"; c.wght = 400; c.wdth = 70; c.rond = 40; c.size = 200 }
            5 -> { c.layout = "col"; c.font = "antonio"; c.wght = 400; c.size = 195 }
            6 -> { c.layout = "stack"; c.font = "anton"; c.gap = 6; c.size = 205 }
            7 -> { c.layout = "diag"; c.wght = 300; c.wdth = 60; c.size = 190 }
            8 -> { c.layout = "stack"; c.wght = 700; c.rond = 100; c.glow = 40; c.size = 190 }
            9 -> { c.layout = "inline"; c.font = "syne"; c.wght = 800; c.size = 360 }
            10 -> { c.layout = "stack"; c.font = "bigshoulders"; c.wght = 800; c.size = 225 }
            else -> { c.layout = "words"; c.wght = 600; c.size = 150 }
        }
    }

    private val collections: List<P> = pals.flatMap { pl ->
        val dig = (0 until 12).map { i ->
            val c = StudioCfg()
            recipe(i, c)
            c.color = pl.c1
            if (i % 2 == 0) c.c2 = pl.c2
            P("${pl.name} ${i + 1}", "Paleta · ${pl.name}", c)
        }
        val ana = (0 until 12).map { i ->
            val c = StudioCfg()
            c.layout = "analog"; c.an = i + 1; c.size = 170; c.fx = "solid"; c.color = pl.c1; c.c2 = pl.c2
            P("${pl.name} ${i + 1}", "Agujas · ${pl.name}", c)
        }
        dig + ana
    }

    private val designs: List<P> = StudioDesigns.all.mapIndexed { i, d ->
        val c = StudioCfg()
        c.layout = "custom"; c.cs = d.second; c.fx = if (i % 3 == 1) "glass" else "solid"; c.tr = 30; c.size = 170; c.date = false
        c.color = when (i % 4) { 0 -> 0xFFFFFFFF.toInt(); 1 -> 0xFFFFE6B8.toInt(); 2 -> 0xFFBFE3FF.toInt(); else -> 0xFFFFC9E3.toInt() }
        c.c2 = when (i % 4) { 0 -> 0xFFFFB36B.toInt(); 1 -> 0xFFFF7A7A.toInt(); 2 -> 0xFF7AE0C3.toInt(); else -> 0xFFB68CFF.toInt() }
        P(d.first, "Diseños", c)
    }

    val all: List<P> = designs + base + icons + collections
    val catsAll: List<String> = cats + pals.map { "Paleta · ${it.name}" } + pals.map { "Agujas · ${it.name}" }
    val total: Int get() = all.size

    fun same(a: StudioCfg, b: StudioCfg) = a.layout == b.layout && a.wght == b.wght && a.wdth == b.wdth && a.rond == b.rond &&
        a.fx == b.fx && a.font == b.font && a.ol == b.ol && a.color == b.color && a.ic == b.ic && a.cmode == b.cmode && a.c2 == b.c2 && a.xs == b.xs && a.cs == b.cs
}

/** Últimos estilos aplicados (se muestran en la página "Para ti"). */
object StudioRecents {
    fun list(ctx: Context): List<String> = (ctx.getSharedPreferences("lock", Context.MODE_PRIVATE).getString("recents", "") ?: "").split("\n").filter { it.isNotEmpty() }
    fun add(ctx: Context, enc: String) {
        val l = ArrayList(list(ctx))
        l.remove(enc)
        l.add(0, enc)
        ctx.getSharedPreferences("lock", Context.MODE_PRIVATE).edit().putString("recents", l.take(11).joinToString("\n")).apply()
    }
}

/** Fondos generados (propios) y utilidades para poner un fondo en bloqueo, inicio o ambos. */
object StudioBg {
    private val pal = arrayOf(
        intArrayOf(0xFF0F3B3B.toInt(), 0xFF1B6E6E.toInt(), 0xFF15262B.toInt()),
        intArrayOf(0xFF3A1C71.toInt(), 0xFFD76D77.toInt(), 0xFFFFAF7B.toInt()),
        intArrayOf(0xFF0F2027.toInt(), 0xFF2C5364.toInt(), 0xFF1CB5E0.toInt()),
        intArrayOf(0xFF0B3D2E.toInt(), 0xFF3DDC97.toInt(), 0xFF1A237E.toInt()),
        intArrayOf(0xFF42275A.toInt(), 0xFF734B6D.toInt(), 0xFFFF9A9E.toInt()),
        intArrayOf(0xFF141E30.toInt(), 0xFF243B55.toInt(), 0xFF0B0F18.toInt())
    )
    private val styleNames = listOf("Degradado", "Malla", "Aurora", "Dunas", "Ondas", "Esferas")
    val count = pal.size * styleNames.size

    fun label(i: Int): String = styleNames[(i / pal.size) % styleNames.size]

    private fun shift(c: Int, deg: Float): Int {
        val h = FloatArray(3)
        Color.colorToHSV(c, h)
        h[0] = (h[0] + deg) % 360f
        return Color.HSVToColor(h)
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(), (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(), (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())

    fun gen(i: Int, w: Int, h: Int): Bitmap {
        val st = (i / pal.size) % styleNames.size
        val base = pal[i % pal.size]
        val c = IntArray(3) { shift(base[it], st * 23f) }
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rnd = java.util.Random(i * 7919L + 13)
        when (st) {
            0 -> {
                p.shader = LinearGradient(0f, 0f, w * 0.8f, h.toFloat(), c, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.shader = RadialGradient(w * 0.2f, h * 0.25f, w * 0.7f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.shader = RadialGradient(w * 0.85f, h * 0.72f, w * 0.8f, (c[2] and 0x00FFFFFF) or 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            }
            1 -> {
                cv.drawColor(mix(c[0], Color.BLACK, 0.35f))
                for (k in 0 until 5) {
                    val x = w * (0.1f + 0.8f * rnd.nextFloat())
                    val y = h * (0.1f + 0.8f * rnd.nextFloat())
                    val col = if (k % 2 == 0) c[1] else c[2]
                    p.shader = RadialGradient(x, y, w * (0.55f + 0.3f * rnd.nextFloat()), (col and 0x00FFFFFF) or 0xCC000000.toInt(), col and 0x00FFFFFF, Shader.TileMode.CLAMP)
                    cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                }
            }
            2 -> {
                p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), mix(c[2], Color.BLACK, 0.7f), mix(c[0], Color.BLACK, 0.5f), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                for (k in 0 until 7) {
                    val path = Path()
                    val y0 = h * (0.25f + 0.1f * k)
                    val amp = h * (0.03f + 0.04f * rnd.nextFloat())
                    val ph = rnd.nextFloat() * 6.28f
                    path.moveTo(0f, y0)
                    var x = 0f
                    while (x <= w) { path.lineTo(x, y0 + amp * Math.sin(x / w * 5.5 + ph).toFloat()); x += w / 40f }
                    p.shader = null
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = h * 0.07f
                    p.color = ((if (k % 2 == 0) c[1] else c[2]) and 0x00FFFFFF) or 0x38000000
                    cv.drawPath(path, p)
                }
                p.style = Paint.Style.FILL
            }
            3 -> {
                p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), c[2], c[0], Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.shader = null
                for (k in 0 until 5) {
                    val path = Path()
                    val y0 = h * (0.45f + 0.11f * k)
                    val ph = rnd.nextFloat() * 6.28f
                    path.moveTo(0f, h.toFloat())
                    var x = 0f
                    while (x <= w) { path.lineTo(x, y0 + h * 0.05f * Math.sin(x / w * 3.2 + ph).toFloat()); x += w / 40f }
                    path.lineTo(w.toFloat(), h.toFloat()); path.close()
                    p.color = mix(c[1], Color.BLACK, 0.12f + 0.16f * k)
                    cv.drawPath(path, p)
                }
            }
            4 -> {
                p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), c[0], c[2], Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.shader = null
                p.style = Paint.Style.STROKE
                p.strokeWidth = w * 0.004f
                for (k in 0 until 34) {
                    val path = Path()
                    val y0 = h * (0.08f + 0.03f * k)
                    path.moveTo(0f, y0)
                    var x = 0f
                    while (x <= w) { path.lineTo(x, y0 + h * 0.05f * Math.sin(x / w * 6.0 + k * 0.3).toFloat() * (0.4f + k / 40f)); x += w / 50f }
                    p.color = ((if (k % 3 == 0) c[1] else Color.WHITE) and 0x00FFFFFF) or 0x40000000
                    cv.drawPath(path, p)
                }
                p.style = Paint.Style.FILL
            }
            else -> {
                p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), c[0], mix(c[2], Color.BLACK, 0.4f), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                for (k in 0 until 7) {
                    val x = w * rnd.nextFloat()
                    val y = h * (0.1f + 0.8f * rnd.nextFloat())
                    val r = w * (0.12f + 0.28f * rnd.nextFloat())
                    val col = if (k % 2 == 0) c[1] else c[2]
                    p.shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.2f, intArrayOf(mix(col, Color.WHITE, 0.45f), col, mix(col, Color.BLACK, 0.5f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                    cv.drawCircle(x, y, r, p)
                }
            }
        }
        return b
    }

    /** Aplica brillo (-50..50), saturación (0..200 %) y desenfoque (0..100) a un fondo. */
    fun filter(src: Bitmap, blur: Int, bright: Int, sat: Int): Bitmap {
        if (blur == 0 && bright == 0 && sat == 100) return src
        var bmp = src
        if (blur > 0) {
            val k = 1f + blur / 100f * 26f
            val sm = Bitmap.createScaledBitmap(src, maxOf(2, (src.width / k).toInt()), maxOf(2, (src.height / k).toInt()), true)
            bmp = Bitmap.createScaledBitmap(sm, src.width, src.height, true)
        }
        val o = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix()
        cm.setSaturation(sat / 100f)
        val f = 1f + bright / 100f
        val bm = ColorMatrix(floatArrayOf(f, 0f, 0f, 0f, 0f, 0f, f, 0f, 0f, 0f, 0f, 0f, f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        cm.postConcat(bm)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        p.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(o).drawBitmap(bmp, 0f, 0f, p)
        return o
    }

    /** target: 0 = bloqueo, 1 = inicio, 2 = ambas. */
    fun setWall(ctx: Context, bmp: Bitmap, target: Int): Boolean = try {
        val flags = when (target) { 1 -> WallpaperManager.FLAG_SYSTEM; 2 -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK; else -> WallpaperManager.FLAG_LOCK }
        WallpaperManager.getInstance(ctx).setBitmap(bmp, null, true, flags)
        true
    } catch (e: Throwable) { false }

    fun setLock(ctx: Activity, bmp: Bitmap): Boolean = setWall(ctx, bmp, 0)
}

/** Utilidades visuales comunes del estilo HyperOS (botones tipo píldora, animaciones de pulsación). */
object Hx {
    const val BLUE = 0xFF3D8BFF.toInt()
    const val CARD = 0.72f

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun press(v: View) {
        v.setOnTouchListener { x, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> x.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    x.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(2.4f)).start()
            }
            false
        }
    }

    fun pill(ctx: Context, text: String, bg: Int, fg: Int, click: () -> Unit): TextView {
        val t = TextView(ctx)
        t.text = text
        t.setTextColor(fg)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.setPadding(dp(ctx, 18f), dp(ctx, 9f), dp(ctx, 18f), dp(ctx, 9f))
        val g = GradientDrawable()
        g.cornerRadius = dp(ctx, 40f).toFloat()
        g.setColor(bg)
        t.background = g
        t.setOnClickListener { click() }
        press(t)
        return t
    }

    fun bgBitmap(ctx: Context): Bitmap = StudioWall.get(ctx) ?: StudioBg.gen(0, 240, 420)
}

/** Iconos simples dibujados (Fondo, IA, Profundidad, Efectos) para la barra inferior del editor. */
class HxIcon(ctx: Context, private val kind: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val r = 11f * d
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.8f * d
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = Color.WHITE
        when (kind) {
            0 -> {
                c.drawRoundRect(RectF(cx - r, cy - r, cx + r, cy + r), 4f * d, 4f * d, p)
                val pa = Path()
                pa.moveTo(cx - r, cy + r * 0.55f)
                pa.lineTo(cx - r * 0.3f, cy - r * 0.05f)
                pa.lineTo(cx + r * 0.25f, cy + r * 0.45f)
                pa.lineTo(cx + r * 0.55f, cy + r * 0.15f)
                pa.lineTo(cx + r, cy + r * 0.6f)
                c.drawPath(pa, p)
                c.drawCircle(cx + r * 0.4f, cy - r * 0.45f, 2.2f * d, p)
            }
            1 -> {
                val pa = Path()
                pa.moveTo(cx, cy - r)
                pa.quadTo(cx + r * 0.12f, cy - r * 0.12f, cx + r, cy)
                pa.quadTo(cx + r * 0.12f, cy + r * 0.12f, cx, cy + r)
                pa.quadTo(cx - r * 0.12f, cy + r * 0.12f, cx - r, cy)
                pa.quadTo(cx - r * 0.12f, cy - r * 0.12f, cx, cy - r)
                c.drawPath(pa, p)
            }
            2 -> {
                c.drawRoundRect(RectF(cx - r, cy - r * 0.55f, cx + r * 0.35f, cy + r), 4f * d, 4f * d, p)
                c.drawRoundRect(RectF(cx - r * 0.35f, cy - r, cx + r, cy + r * 0.55f), 4f * d, 4f * d, p)
            }
            4 -> {
                c.drawRoundRect(RectF(cx - r, cy - r, cx + r, cy + r), 5f * d, 5f * d, p)
                p.style = Paint.Style.FILL
                for (q in listOf(floatArrayOf(-0.45f, -0.45f), floatArrayOf(0f, 0f), floatArrayOf(0.45f, 0.45f), floatArrayOf(0.45f, -0.45f), floatArrayOf(-0.45f, 0.45f))) c.drawCircle(cx + q[0] * r, cy + q[1] * r, 1.9f * d, p)
            }
            5 -> {
                val rr = RectF(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f)
                c.drawArc(rr, 200f, 250f, false, p)
                val pa = Path()
                pa.moveTo(cx - r * 0.85f, cy - r * 0.75f)
                pa.lineTo(cx - r * 0.5f, cy - r * 0.7f)
                pa.lineTo(cx - r * 0.7f, cy - r * 0.35f)
                c.drawPath(pa, p)
            }
            else -> {
                c.drawCircle(cx - r * 0.35f, cy, r * 0.62f, p)
                c.drawCircle(cx + r * 0.35f, cy, r * 0.62f, p)
            }
        }
    }
}

/** Miniatura de pantalla de bloqueo con el reloj escalado (galería y panel del editor). */
object StudioTile {
    fun make(ctx: Context, cfg: StudioCfg, pw: Int, ph: Int, radiusDp: Float, bg: Bitmap): FrameLayout {
        val dm = ctx.resources.displayMetrics
        val sw = dm.widthPixels
        val sh = dm.heightPixels
        val k = pw / sw.toFloat()
        val card = FrameLayout(ctx)
        val iv = ImageView(ctx)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.setImageBitmap(bg)
        card.addView(iv, FrameLayout.LayoutParams(-1, -1))
        val dim = View(ctx)
        dim.setBackgroundColor(0x26000000)
        card.addView(dim, FrameLayout.LayoutParams(-1, -1))
        val screen = FrameLayout(ctx)
        val clk = StudioClock(ctx)
        clk.applyConfig(cfg)
        val clp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        clp.topMargin = (cfg.top * dm.density).toInt()
        screen.addView(clk, clp)
        screen.pivotX = 0f
        screen.pivotY = 0f
        screen.scaleX = k
        screen.scaleY = k
        card.addView(screen, FrameLayout.LayoutParams(sw, sh))
        val shine = View(ctx)
        shine.tag = "shine"
        val shg = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF))
        shine.background = shg
        shine.translationX = -pw.toFloat()
        card.addView(shine, FrameLayout.LayoutParams((pw * 0.45f).toInt(), -1))
        val rad = radiusDp * dm.density
        val bord = android.graphics.drawable.GradientDrawable()
        bord.cornerRadius = radiusDp * dm.density
        bord.setColor(0)
        bord.setStroke(maxOf(1, (1.2f * dm.density).toInt()), 0x38FFFFFF)
        card.foreground = bord
        card.clipToOutline = true
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, rad) }
        }
        return card
    }
}

/**
 * Galería de estilos de la pantalla de bloqueo, estilo HyperOS: categorías en vertical, carrusel de tarjetas
 * en horizontal, la tarjeta se expande hasta el editor. Se abre con pulsación larga en el bloqueo o desde la app.
 */
class LockGalleryActivity : Activity() {
    private class Page(val cat: String, val items: List<StudioPresets.P>, val view: FrameLayout, val title: TextView) {
        val cards = ArrayList<FrameLayout>()
        var pos = 0f
        var built = false
    }

    private lateinit var root: FrameLayout
    private lateinit var stage: FrameLayout
    private lateinit var nameTv: TextView
    private lateinit var custBtn: TextView
    private lateinit var topBar: FrameLayout
    private val pages = ArrayList<Page>()
    private var vpos = 0f
    private var sw = 0
    private var sh = 0
    private var pw = 0
    private var ph = 0
    private var cy = 0f
    private var vstep = 0f
    private var hstep = 0f
    private var busy = false
    private var anim: ValueAnimator? = null
    private var vt: VelocityTracker? = null
    private var dX = 0f
    private var dY = 0f
    private var axis = 0
    private var startV = 0f
    private var startC = 0f
    private var lastIdx = -1

    private fun cur(): Int = vpos.roundToInt().coerceIn(0, pages.size - 1)
    private fun idx(pg: Page): Int = Math.floorMod(pg.pos.roundToInt(), pg.items.size)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try { setShowWhenLocked(true) } catch (e: Throwable) { }
        try {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(WindowInsets.Type.systemBars())
            window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (e: Throwable) { }
        val dm = resources.displayMetrics
        sw = dm.widthPixels
        sh = dm.heightPixels
        pw = (sw * 0.54f).toInt()
        ph = (pw * sh / sw.toFloat()).toInt()
        cy = sh * 0.52f
        vstep = sh * 0.64f
        hstep = pw * 0.93f

        root = FrameLayout(this)
        root.setBackgroundColor(0xFF0A0B0F.toInt())
        // el fondo del bloqueo se oscurece mientras la pantalla se "minimiza" en la tarjeta
        val wallBg = ImageView(this)
        wallBg.scaleType = ImageView.ScaleType.CENTER_CROP
        wallBg.setImageBitmap(Hx.bgBitmap(this))
        wallBg.scaleX = 1.12f; wallBg.scaleY = 1.12f
        wallBgV = wallBg
        root.addView(wallBg, FrameLayout.LayoutParams(-1, -1))
        val darkBg = View(this)
        darkBg.setBackgroundColor(0xFF0A0B0F.toInt())
        darkBg.alpha = 0f
        root.addView(darkBg, FrameLayout.LayoutParams(-1, -1))
        // resplandor ambiental con los colores de tu fondo, que "respira"
        glowV = View(this)
        val ac = avgColor(Hx.bgBitmap(this))
        val gd = android.graphics.drawable.GradientDrawable()
        gd.gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
        gd.gradientRadius = sw * 0.95f
        gd.setGradientCenter(0.5f, 0.52f)
        gd.colors = intArrayOf((ac and 0xFFFFFF) or 0x77000000, (ac and 0xFFFFFF) or 0x22000000, 0x00000000)
        glowV.background = gd
        glowV.alpha = 0f
        root.addView(glowV, FrameLayout.LayoutParams(-1, -1))
        stage = FrameLayout(this)
        root.addView(stage, FrameLayout.LayoutParams(-1, -1))

        val bg = Hx.bgBitmap(this)
        val byCat = StudioPresets.all.groupBy { it.cat }
        val mine = ArrayList<StudioPresets.P>()
        val cur = getSharedPreferences("lock", MODE_PRIVATE)
        if (!cur.getString("sc_cfg", "").isNullOrEmpty()) mine.add(StudioPresets.P("Actual", "Para ti", StudioCfg.load(this)))
        for ((i, e) in StudioRecents.list(this).withIndex()) mine.add(StudioPresets.P("Reciente ${i + 1}", "Para ti", StudioCfg.parse(e)))
        val order = ArrayList<Pair<String, List<StudioPresets.P>>>()
        if (mine.isNotEmpty()) order.add(Pair("Para ti", mine))
        for (c0 in StudioPresets.catsAll) byCat[c0]?.let { order.add(Pair(c0, it)) }
        for ((c, items) in order) {
            val v = FrameLayout(this)
            v.pivotX = sw / 2f
            v.pivotY = cy
            val t = TextView(this)
            t.text = c
            t.setTextColor(Color.WHITE)
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            t.typeface = Typeface.DEFAULT_BOLD
            t.gravity = Gravity.CENTER
            val tlp = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
            tlp.topMargin = (sh * 0.165f).toInt()
            v.addView(t, tlp)
            stage.addView(v, FrameLayout.LayoutParams(-1, -1))
            pages.add(Page(c, items, v, t))
        }
        bgRef = bg

        nameTv = TextView(this)
        nameTv.setTextColor(0xE6FFFFFF.toInt())
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        nameTv.letterSpacing = 0.04f
        nameTv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        nameTv.gravity = Gravity.CENTER
        val nlp = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        nlp.topMargin = (cy + ph / 2f + Hx.dp(this, 6f)).toInt()
        root.addView(nameTv, nlp)

        custBtn = Hx.pill(this, "Personalizar", 0x33FFFFFF, Color.WHITE) { openEditor() }
        custBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        custBtn.setPadding(Hx.dp(this, 14f), Hx.dp(this, 6f), Hx.dp(this, 14f), Hx.dp(this, 6f))
        val clp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        clp.topMargin = (cy + ph / 2f - Hx.dp(this, 46f)).toInt()
        root.addView(custBtn, clp)

        topBar = FrameLayout(this)
        topBar.addView(Hx.pill(this, "Salir", 0x2EFFFFFF, Color.WHITE) { leave() }, FrameLayout.LayoutParams(-2, -2, Gravity.START))
        topBar.addView(Hx.pill(this, "Aplicar", Hx.BLUE, Color.WHITE) { applyOnly() }, FrameLayout.LayoutParams(-2, -2, Gravity.END))
        val tl = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        tl.setMargins(Hx.dp(this, 16f), Hx.dp(this, 30f), Hx.dp(this, 16f), 0)
        root.addView(topBar, tl)

        stage.setOnTouchListener { _, e -> touchTilt(e); touch(e); true }
        setContentView(root)

        // Entrada: la pantalla de bloqueo "se aleja" y aparece la galería
        stage.pivotX = sw / 2f
        stage.pivotY = cy
        stage.scaleX = 1f / 0.54f
        stage.scaleY = 1f / 0.54f
        topBar.alpha = 0f
        nameTv.alpha = 0f
        layoutAll()
        val spring = android.view.animation.Interpolator { t -> (1.0 - Math.exp(-6.5 * t) * Math.cos(7.0 * t)).toFloat() }
        stage.animate().scaleX(1f).scaleY(1f).setDuration(700).setInterpolator(spring).start()
        darkBg.animate().alpha(0.96f).setDuration(520).setInterpolator(DecelerateInterpolator(1.5f)).start()
        topBar.animate().alpha(1f).setStartDelay(300).setDuration(320).start()
        // entrada escalonada de las tarjetas, brillo y respiración del resplandor
        val d0 = resources.displayMetrics.density
        pages.getOrNull(0)?.let { p0 ->
            for ((j, c) in p0.cards.withIndex()) {
                val dj = Math.abs(j - p0.pos)
                c.translationY = 110f * d0
                c.animate().translationY(0f).setStartDelay((120 + dj * 90).toLong()).setDuration(620).setInterpolator(android.view.animation.OvershootInterpolator(1.6f)).start()
            }
            p0.cards.getOrNull(idx(p0))?.let { c -> c.postDelayed({ shine(c) }, 650) }
        }
        glowV.animate().alpha(1f).setDuration(900).withEndAction(object : Runnable {
            var up = false
            override fun run() {
                if (!glowV.isAttachedToWindow) return
                up = !up
                glowV.animate().alpha(if (up) 0.62f else 1f).setDuration(3200).setInterpolator(android.view.animation.AccelerateDecelerateInterpolator()).withEndAction(this).start()
            }
        }).start()
        nameTv.translationY = 24f * d0
        nameTv.animate().translationY(0f).setStartDelay(420).setDuration(500).setInterpolator(DecelerateInterpolator(2f)).start()
    }

    private var bgRef: Bitmap? = null
    private lateinit var wallBgV: ImageView
    private lateinit var glowV: View
    private var tiltX = 0f
    private var tiltY = 0f
    private var baseY = Float.NaN
    private var sensors: android.hardware.SensorManager? = null
    private val sl = object : android.hardware.SensorEventListener {
        override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        override fun onSensorChanged(e: android.hardware.SensorEvent) {
            val gx = (-e.values[0] / 9.8f).coerceIn(-1f, 1f)
            val gy = (e.values[1] / 9.8f).coerceIn(-1f, 1f)
            if (baseY.isNaN()) { baseY = gy; try { android.util.Log.i("RomTweaker", "gal: sensor activo (" + e.sensor.name + ")") } catch (x: Throwable) { } }
            tiltX += (gx - tiltX) * 0.12f
            tiltY += ((gy - baseY) - tiltY) * 0.12f
            applyTilt()
        }
    }

    private var touchX = 0f
    private var touchY = 0f

    private fun applyTilt() {
        if (!::stage.isInitialized) return
        val d = resources.displayMetrics.density
        val tx = (tiltX * 2.2f + touchX).coerceIn(-1.2f, 1.2f)
        val ty = (tiltY * 2.2f + touchY).coerceIn(-1.2f, 1.2f)
        stage.cameraDistance = 12000f * d
        stage.rotationY = tx * 7f
        stage.rotationX = ty * 7f
        wallBgV.translationX = -tx * 22f * d
        wallBgV.translationY = ty * 22f * d
        glowV.translationX = tx * 40f * d
    }

    private fun touchTilt(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> { touchX = ((e.x / sw) - 0.5f) * 1.4f; touchY = -((e.y / sh) - 0.5f) * 0.8f; applyTilt() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val x0 = touchX; val y0 = touchY
                val a = ValueAnimator.ofFloat(1f, 0f)
                a.duration = 600
                a.interpolator = android.view.animation.OvershootInterpolator(1.8f)
                a.addUpdateListener { val k = it.animatedValue as Float; touchX = x0 * k; touchY = y0 * k; applyTilt() }
                a.start()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            sensors = getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager
            val g = sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) ?: sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)
            if (g != null) sensors?.registerListener(sl, g, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        } catch (e: Throwable) { }
    }

    override fun onPause() {
        try { sensors?.unregisterListener(sl) } catch (e: Throwable) { }
        super.onPause()
    }

    private fun shine(c: FrameLayout) {
        val sv = c.findViewWithTag<View>("shine") ?: return
        sv.animate().cancel()
        sv.translationX = -pw * 0.5f
        sv.animate().translationX(pw * 1.05f).setStartDelay(120).setDuration(950).setInterpolator(android.view.animation.AccelerateDecelerateInterpolator()).start()
    }

    private fun avgColor(b: Bitmap): Int {
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        val st = maxOf(1, b.width / 20)
        var y = 0
        while (y < b.height) { var x = 0; while (x < b.width) { val p = b.getPixel(x, y); r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++; x += st }; y += st }
        if (n == 0L) return 0xFF4D7CFF.toInt()
        val hsv = FloatArray(3)
        Color.colorToHSV(Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt()), hsv)
        hsv[1] = minOf(1f, hsv[1] * 1.3f + 0.15f); hsv[2] = maxOf(0.55f, hsv[2])
        return Color.HSVToColor(hsv)
    }

    private fun build(pg: Page) {
        if (pg.built) return
        pg.built = true
        val bg = bgRef ?: Hx.bgBitmap(this)
        for (pr in pg.items) {
            val card = StudioTile.make(this, pr.cfg, pw, ph, 28f, bg)
            val lp = FrameLayout.LayoutParams(pw, ph, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            lp.topMargin = (cy - ph / 2f).toInt()
            pg.view.addView(card, lp)
            pg.cards.add(card)
        }
        layoutAll()
    }

    /** Coloca todas las páginas y tarjetas según la posición (vertical vpos, horizontal pos de cada página, circular). */
    private fun layoutAll() {
        for ((i, pg) in pages.withIndex()) {
            val d = i - vpos
            val ad = abs(d)
            if (d > 1.7f || d < -1.5f) { pg.view.visibility = View.INVISIBLE; continue }
            pg.view.visibility = View.VISIBLE
            if (!pg.built) build(pg)
            if (d >= 0f) {
                // las categorías de abajo asoman con su tamaño real
                pg.view.translationY = d * vstep
                pg.view.scaleX = 1f
                pg.view.scaleY = 1f
                pg.view.alpha = 1f
            } else {
                // la de arriba se encoge, sube y se desvanece
                val k = min(1f, ad)
                pg.view.translationY = d * vstep * 0.85f
                pg.view.scaleX = 1f - 0.22f * k
                pg.view.scaleY = 1f - 0.22f * k
                pg.view.alpha = 1f - k
            }
            pg.title.alpha = (1f - ad * 1.7f).coerceIn(0f, 1f)
            val n = pg.items.size
            for ((j, c) in pg.cards.withIndex()) {
                var dj = j - pg.pos
                dj -= n * Math.floor(((dj + n / 2f) / n).toDouble()).toFloat()
                val a2 = abs(dj)
                if (a2 > 2.3f) { c.visibility = View.INVISIBLE; continue }
                c.visibility = View.VISIBLE
                val sg = if (dj < 0) -1f else 1f
                c.translationX = if (a2 <= 1f) dj * hstep else sg * (hstep + (a2 - 1f) * hstep * 0.7f)
                val sc = if (a2 <= 1f) 1f - 0.40f * a2 else 0.60f - 0.08f * (a2 - 1f)
                c.scaleX = sc
                c.scaleY = sc
                c.alpha = 1f - 0.45f * min(1f, a2)
                c.cameraDistance = 9000f * resources.displayMetrics.density
                c.rotationY = (-dj * 22f).coerceIn(-34f, 34f)
                c.translationZ = 10f * (1f - min(1f, a2))
            }
        }
        val pg = pages[cur()]
        val near = (1f - abs(vpos - vpos.roundToInt()) * 3f).coerceIn(0f, 1f)
        val nearH = (1f - abs(pg.pos - pg.pos.roundToInt()) * 3f).coerceIn(0f, 1f)
        val a = min(near, nearH)
        custBtn.alpha = a
        nameTv.alpha = a
        val i = idx(pg)
        pg.items.getOrNull(i)?.let { nameTv.text = it.name }
        val key = cur() * 1000 + i
        if (key != lastIdx) {
            if (lastIdx != -1) {
                root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                pg.cards.getOrNull(i)?.let { shine(it) }
            }
            lastIdx = key
        }
    }

    private fun animTo(from: Float, to: Float, set: (Float) -> Unit) {
        anim?.cancel()
        val a = ValueAnimator.ofFloat(from, to)
        a.duration = 360
        a.interpolator = DecelerateInterpolator(1.8f)
        a.addUpdateListener { set(it.animatedValue as Float); layoutAll() }
        anim = a
        a.start()
    }

    private fun touch(e: MotionEvent) {
        if (busy) return
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anim?.cancel()
                vt?.recycle()
                vt = VelocityTracker.obtain()
                vt?.addMovement(e)
                dX = e.x; dY = e.y; axis = 0
                startV = vpos
                startC = pages[cur()].pos
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                val dx = e.x - dX
                val dy = e.y - dY
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (axis == 0 && max(abs(dx), abs(dy)) > slop) {
                    axis = if (abs(dx) > abs(dy)) 1 else 2
                    if (axis == 1) startC = pages[cur()].pos else startV = vpos
                }
                if (axis == 1) {
                    val pg = pages[cur()]
                    pg.pos = startC - dx / hstep
                    layoutAll()
                } else if (axis == 2) {
                    vpos = (startV - dy / vstep).coerceIn(-0.25f, pages.size - 1 + 0.25f)
                    layoutAll()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt?.addMovement(e)
                vt?.computeCurrentVelocity(1000)
                val vx = vt?.xVelocity ?: 0f
                val vy = vt?.yVelocity ?: 0f
                vt?.recycle()
                vt = null
                if (e.actionMasked == MotionEvent.ACTION_CANCEL) { snap(); return }
                when (axis) {
                    1 -> {
                        val pg = pages[cur()]
                        val t = (pg.pos - vx / hstep * 0.22f).roundToInt()
                        animTo(pg.pos, t.toFloat()) { pg.pos = it }
                    }
                    2 -> {
                        val t = (vpos - vy / vstep * 0.22f).roundToInt().coerceIn(0, pages.size - 1)
                        animTo(vpos, t.toFloat()) { vpos = it }
                    }
                    else -> tap(e.x, e.y)
                }
            }
        }
    }

    private fun snap() {
        val pg = pages[cur()]
        animTo(vpos, cur().toFloat()) { vpos = it }
        pg.pos = pg.pos.roundToInt().toFloat()
        layoutAll()
    }

    private fun tap(x: Float, y: Float) {
        val pg = pages[cur()]
        if (abs(y - cy) > ph / 2f) return
        val half = pw / 2f
        val dx = x - sw / 2f
        if (abs(dx) <= half) openEditor()
        else if (dx < 0) { val t = pg.pos.roundToInt() - 1; animTo(pg.pos, t.toFloat()) { pg.pos = it } }
        else { val t = pg.pos.roundToInt() + 1; animTo(pg.pos, t.toFloat()) { pg.pos = it } }
    }

    /** La tarjeta central crece hasta el 72 % de la pantalla (como HyperOS) y se abre el editor sobre ella. */
    private fun openEditor() {
        if (busy) return
        val pg = pages[cur()]
        val j = idx(pg)
        val card = pg.cards.getOrNull(j) ?: return
        val pr = pg.items[j]
        busy = true
        anim?.cancel()
        for (p in pages) if (p !== pg) p.view.animate().alpha(0f).setDuration(220).start()
        for ((k, c) in pg.cards.withIndex()) if (k != j) c.animate().alpha(0f).translationXBy(if (k < j) -pw * 0.3f else pw * 0.3f).setDuration(260).start()
        pg.title.animate().alpha(0f).setDuration(180).start()
        nameTv.animate().alpha(0f).setDuration(150).start()
        custBtn.animate().alpha(0f).setDuration(150).start()
        topBar.animate().alpha(0f).setDuration(180).start()
        card.translationZ = 30f
        val k = Hx.CARD * sw / pw.toFloat()
        card.animate().scaleX(k).scaleY(k).translationY(sh / 2f - cy)
            .setDuration(420).setInterpolator(DecelerateInterpolator(1.8f))
            .withEndAction {
                val i = Intent(this, ClockEditActivity::class.java)
                i.putExtra("cfg", pr.cfg.enc())
                try { startActivityForResult(i, 91) } catch (e: Throwable) { busy = false }
                overridePendingTransition(0, 0)
            }.start()
    }

    private fun resetAfterEdit() {
        busy = false
        layoutAll()
        val pg = pages[cur()]
        val j = idx(pg)
        for (p in pages) p.view.animate().alpha(1f).setDuration(260).start()
        for ((k, c) in pg.cards.withIndex()) {
            c.animate().alpha(1f).translationY(0f).setDuration(300).setInterpolator(DecelerateInterpolator(1.5f)).start()
            if (k == j) c.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(380).setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
        layoutAll()
        pg.title.animate().alpha(1f).setDuration(260).start()
        nameTv.animate().alpha(1f).setDuration(260).start()
        custBtn.animate().alpha(1f).setDuration(260).start()
        topBar.animate().alpha(1f).setDuration(260).start()
    }

    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        if (rc != 91) return
        if (res == RESULT_OK) { finish(); overridePendingTransition(0, 0) } else resetAfterEdit()
    }

    private fun leave() {
        if (busy) return
        busy = true
        root.animate().alpha(0f).scaleX(1.08f).scaleY(1.08f).setDuration(240).setInterpolator(DecelerateInterpolator())
            .withEndAction { finish(); overridePendingTransition(0, 0) }.start()
    }

    /** Aplica el estilo elegido sin pasar por el editor. */
    private fun applyOnly() {
        if (busy) return
        val pg = pages[cur()]
        val pr = pg.items.getOrNull(idx(pg)) ?: return
        busy = true
        val p = getSharedPreferences("lock", MODE_PRIVATE)
        p.edit().putString("sc_cfg", pr.cfg.enc()).putBoolean("sc_on", true).putBoolean("ic_on", false).apply()
        StudioRecents.add(this, pr.cfg.enc())
        val pushed = StudioPush.push(p)
        Toast.makeText(this, if (pushed) "Aplicado: ${pr.name}" else "Guardado, pero el módulo LSPosed no está activo", Toast.LENGTH_LONG).show()
        root.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(260).setInterpolator(DecelerateInterpolator())
            .withEndAction { finish(); overridePendingTransition(0, 0) }.start()
    }

    override fun onBackPressed() { leave() }
}
