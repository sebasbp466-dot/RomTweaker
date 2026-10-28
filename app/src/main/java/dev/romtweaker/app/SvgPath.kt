package dev.romtweaker.app

import android.graphics.Path
import android.graphics.RectF

/** Mini parser de path SVG (M L H V C S Q A Z, absolutos y relativos) para previsualizar formas. */
object SvgPath {
    private val tokRe = Regex("[MmLlHhVvCcSsQqAaZz]|-?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?")

    fun parse(d: String): Path? {
        return try { run(d) } catch (e: Throwable) { null }
    }

    private fun run(d: String): Path? {
        val t = tokRe.findAll(d).map { it.value }.toList()
        val p = Path()
        var i = 0
        var cmd = ' '
        var cx = 0.0; var cy = 0.0; var sx = 0.0; var sy = 0.0
        var lx = 0.0; var ly = 0.0
        fun num(): Double { val v = t[i].toDouble(); i++; return v }
        fun isCmd(s: String): Boolean = s.length == 1 && s[0].isLetter()
        while (i < t.size) {
            if (isCmd(t[i])) { cmd = t[i][0]; i++ }
            val rel = cmd.isLowerCase()
            when (cmd.uppercaseChar()) {
                'M' -> {
                    var x = num(); var y = num()
                    if (rel) { x += cx; y += cy }
                    p.moveTo(x.toFloat(), y.toFloat()); cx = x; cy = y; sx = x; sy = y; lx = x; ly = y
                    cmd = if (rel) 'l' else 'L'
                }
                'L' -> {
                    var x = num(); var y = num()
                    if (rel) { x += cx; y += cy }
                    p.lineTo(x.toFloat(), y.toFloat()); cx = x; cy = y; lx = x; ly = y
                }
                'H' -> {
                    var x = num(); if (rel) x += cx
                    p.lineTo(x.toFloat(), cy.toFloat()); cx = x; lx = x; ly = cy
                }
                'V' -> {
                    var y = num(); if (rel) y += cy
                    p.lineTo(cx.toFloat(), y.toFloat()); cy = y; lx = cx; ly = y
                }
                'C' -> {
                    var x1 = num(); var y1 = num(); var x2 = num(); var y2 = num(); var x = num(); var y = num()
                    if (rel) { x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy }
                    p.cubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x.toFloat(), y.toFloat())
                    lx = x2; ly = y2; cx = x; cy = y
                }
                'S' -> {
                    var x2 = num(); var y2 = num(); var x = num(); var y = num()
                    if (rel) { x2 += cx; y2 += cy; x += cx; y += cy }
                    val x1 = 2 * cx - lx; val y1 = 2 * cy - ly
                    p.cubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x.toFloat(), y.toFloat())
                    lx = x2; ly = y2; cx = x; cy = y
                }
                'Q' -> {
                    var x1 = num(); var y1 = num(); var x = num(); var y = num()
                    if (rel) { x1 += cx; y1 += cy; x += cx; y += cy }
                    p.quadTo(x1.toFloat(), y1.toFloat(), x.toFloat(), y.toFloat())
                    lx = x1; ly = y1; cx = x; cy = y
                }
                'A' -> {
                    val rx = num(); val ry = num(); num()
                    val large = num() != 0.0; val sweep = num() != 0.0
                    var x = num(); var y = num()
                    if (rel) { x += cx; y += cy }
                    arc(p, cx, cy, rx, ry, large, sweep, x, y)
                    cx = x; cy = y; lx = x; ly = y
                }
                'Z' -> { p.close(); cx = sx; cy = sy; lx = sx; ly = sy }
                else -> return null
            }
        }
        return p
    }

    private fun arc(p: Path, x0: Double, y0: Double, rx0: Double, ry0: Double, large: Boolean, sweep: Boolean, x: Double, y: Double) {
        var rx = Math.abs(rx0)
        var ry = Math.abs(ry0)
        if (rx == 0.0 || ry == 0.0) { p.lineTo(x.toFloat(), y.toFloat()); return }
        val x1 = (x0 - x) / 2.0
        val y1 = (y0 - y) / 2.0
        val lam = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry)
        if (lam > 1.0) { val s = Math.sqrt(lam); rx *= s; ry *= s }
        val sign = if (large == sweep) -1.0 else 1.0
        val num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
        val den = rx * rx * y1 * y1 + ry * ry * x1 * x1
        val co = sign * Math.sqrt(Math.max(0.0, num / den))
        val cx1 = co * (rx * y1 / ry)
        val cy1 = co * -(ry * x1 / rx)
        val cx = cx1 + (x0 + x) / 2.0
        val cy = cy1 + (y0 + y) / 2.0
        val ux = (x1 - cx1) / rx
        val uy = (y1 - cy1) / ry
        val vx = (-x1 - cx1) / rx
        val vy = (-y1 - cy1) / ry
        val th1 = Math.toDegrees(Math.atan2(uy, ux))
        var dth = Math.toDegrees(Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy))
        if (!sweep && dth > 0) dth -= 360.0 else if (sweep && dth < 0) dth += 360.0
        p.arcTo(RectF((cx - rx).toFloat(), (cy - ry).toFloat(), (cx + rx).toFloat(), (cy + ry).toFloat()), th1.toFloat(), dth.toFloat())
    }
}
