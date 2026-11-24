package dev.romtweaker.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Recibe los eventos que el módulo envía desde SystemUI (arranque, avisos, errores, modo seguro).
 * Guarda los últimos 40 en las preferencias "modlog" y cada uno también en el registro de la app.
 * El contenido ya viene recortado y nunca incluye textos personales.
 */
class ModuleEventReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try {
            RtLog.init(c)
            val type = (i.getStringExtra("type") ?: return).take(12)
            val tag = (i.getStringExtra("tag") ?: "").take(24)
            val msg = RtLog.redact((i.getStringExtra("msg") ?: "").take(800))
            val now = System.currentTimeMillis()
            val p = c.getSharedPreferences("modlog", Context.MODE_PRIVATE)
            val e = p.edit()
            when (type) {
                "hello" -> {
                    e.putLong("hello_t", now)
                    e.putInt("mv", i.getIntExtra("mv", 0))
                    e.putInt("sdk", i.getIntExtra("sdk", 0))
                    e.putString("hello_msg", msg.take(300))
                }
                "safe" -> {
                    e.putLong("safe_t", now)
                    e.putString("safe_msg", msg.take(300))
                }
                "warn" -> e.putInt("warns", p.getInt("warns", 0) + 1)
                "error" -> e.putInt("errs", p.getInt("errs", 0) + 1)
            }
            val old = try { JSONArray(p.getString("ev", "[]")) } catch (x: Throwable) { JSONArray() }
            val out = JSONArray()
            for (n in maxOf(0, old.length() - 39) until old.length()) out.put(old.get(n))
            out.put(JSONObject().put("t", now).put("k", type).put("g", tag).put("m", msg))
            e.putString("ev", out.toString())
            e.apply()
            val line = "SystemUI/" + tag + ": " + msg
            when (type) {
                "error" -> RtLog.e("módulo", line)
                "warn", "safe" -> RtLog.w("módulo", line)
                else -> RtLog.i("módulo", type + " · " + line)
            }
        } catch (x: Throwable) { RtLog.swallow(x) }
    }

    companion object {
        /** Eventos guardados (más antiguo primero): hora, tipo, etiqueta y texto. */
        fun events(ctx: Context): List<Array<String>> {
            val l = ArrayList<Array<String>>()
            try {
                val a = JSONArray(ctx.getSharedPreferences("modlog", Context.MODE_PRIVATE).getString("ev", "[]"))
                for (n in 0 until a.length()) {
                    val o = a.getJSONObject(n)
                    l.add(arrayOf(o.optLong("t").toString(), o.optString("k"), o.optString("g"), o.optString("m")))
                }
            } catch (e: Throwable) { RtLog.swallow(e) }
            return l
        }
    }
}
