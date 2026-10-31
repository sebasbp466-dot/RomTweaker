package dev.romtweaker.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

class MDigit(val t: String, val cx: Float, val base: Float, val size: Float)

/** Medidas REALES del reloj de SystemUI (las envía el módulo LSPosed): posición, tamaño y dígitos. */
class ClockMetrics(val sw: Int, val sh: Int, val gx: Int, val gy: Int, val gw: Int, val gh: Int, val digits: List<MDigit>) {
    companion object {
        fun load(ctx: Context, compact: Boolean): ClockMetrics? {
            try {
                val s = ctx.getSharedPreferences("lockmetrics", Context.MODE_PRIVATE).getString(if (compact) "compact" else "stack", null) ?: return null
                val j = JSONObject(s)
                val a = j.getJSONArray("d")
                val l = ArrayList<MDigit>()
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    l.add(MDigit(o.getString("t"), o.getDouble("cx").toFloat(), o.getDouble("base").toFloat(), o.getDouble("size").toFloat()))
                }
                val r = ClockMetrics(j.getInt("sw"), j.getInt("sh"), j.getInt("gx"), j.getInt("gy"), j.getInt("gw"), j.getInt("gh"), l)
                return if (r.sw > 0 && r.gw > 0 && r.gh > 0 && l.isNotEmpty()) r else null
            } catch (e: Throwable) { return null }
        }

        fun status(ctx: Context): String {
            val p = ctx.getSharedPreferences("lockmetrics", Context.MODE_PRIVATE)
            val t = p.getLong("t_stack", 0L)
            if (t == 0L) return "Medidas reales: aún no recibidas. Reinicia SystemUI y pulsa «Ver bloqueo ahora»; la vista previa usará las medidas del reloj verdadero."
            val min = (System.currentTimeMillis() - t) / 60000L
            return "Medidas reales del reloj recibidas (hace " + min + " min). La vista previa usa su posición, tamaño y dígitos."
        }
    }
}

class ClockMetricsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val k = i.getStringExtra("kind") ?: return
        val j = i.getStringExtra("json") ?: return
        c.getSharedPreferences("lockmetrics", Context.MODE_PRIVATE).edit().putString(k, j).putLong("t_" + k, System.currentTimeMillis()).apply()
    }
}
