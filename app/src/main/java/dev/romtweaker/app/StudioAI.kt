package dev.romtweaker.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Efecto de profundidad: segmenta el sujeto del fondo con ML Kit (por reflexión, para no romper la compilación si falta). */
object StudioDepth {
    private const val P = "com.google.mlkit.vision.segmentation.subject."

    /** Devuelve el sujeto recortado (ARGB, fondo transparente) o un texto de error. Llamar fuera del hilo principal. */
    fun segment(src: Bitmap): Pair<Bitmap?, String?> {
        return try {
            val ob = Class.forName(P + "SubjectSegmenterOptions\$Builder").getConstructor().newInstance()
            ob.javaClass.getMethod("enableForegroundBitmap").invoke(ob)
            val opts = ob.javaClass.getMethod("build").invoke(ob)
            val seg = Class.forName(P + "SubjectSegmentation").getMethod("getClient", Class.forName(P + "SubjectSegmenterOptions")).invoke(null, opts)
            val ii = Class.forName("com.google.mlkit.vision.common.InputImage")
            val img = ii.getMethod("fromBitmap", Bitmap::class.java, Int::class.javaPrimitiveType).invoke(null, src, 0)
            val task = Class.forName(P + "SubjectSegmenter").getMethod("process", ii).invoke(seg, img)
            val res = Class.forName("com.google.android.gms.tasks.Tasks").getMethod("await", Class.forName("com.google.android.gms.tasks.Task"), Long::class.javaPrimitiveType, java.util.concurrent.TimeUnit::class.java)
                .invoke(null, task, 90L, java.util.concurrent.TimeUnit.SECONDS)
            val fg = res!!.javaClass.getMethod("getForegroundBitmap").invoke(res) as? Bitmap
            if (fg == null) Pair(null, "ML Kit no devolvió sujeto") else Pair(fg, null)
        } catch (e: Throwable) {
            val c = e.cause ?: e
            Pair(null, c.javaClass.simpleName + ": " + (c.message ?: "").take(120))
        }
    }

    /** Solo el canal alfa del recorte, reducido (PNG en base64) para enviarlo al módulo. */
    fun encode(fg: Bitmap): String {
        val w = 320
        val h = (fg.height * w / fg.width.toFloat()).toInt().coerceAtLeast(1)
        val s = Bitmap.createScaledBitmap(fg, w, h, true)
        val o = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = Paint()
        p.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        Canvas(o).drawBitmap(s, 0f, 0f, p)
        val bo = ByteArrayOutputStream()
        o.compress(Bitmap.CompressFormat.PNG, 100, bo)
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
    }

    /** Fondo + máscara = sujeto recortado a la resolución del fondo. */
    fun compose(wall: Bitmap, mask: Bitmap): Bitmap {
        val o = Bitmap.createBitmap(wall.width, wall.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(o)
        c.drawBitmap(wall, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        val mp = Paint(Paint.FILTER_BITMAP_FLAG)
        mp.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        mp.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 2.2f, -153f)))
        c.scale(wall.width / mask.width.toFloat(), wall.height / mask.height.toFloat())
        c.drawBitmap(mask, 0f, 0f, mp)
        return o
    }

    fun decode(b64: String?): Bitmap? = try {
        if (b64.isNullOrEmpty()) null else Base64.decode(b64, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    } catch (e: Throwable) { null }
}

/** IA con Gemini (clave de la API que pone el usuario): estilo de reloj a partir de texto y fondos generados. */
object StudioAI {
    private fun call(key: String, model: String, body: JSONObject): JSONObject {
        val u = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val c = u.openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 20000
        c.readTimeout = 120000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", key)
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        if (code !in 200..299) throw RuntimeException(when (code) {
            400 -> "Petición no válida (revisa la clave)"
            401, 403 -> "Clave rechazada: revisa que sea una clave de Gemini válida"
            429 -> "Límite de uso alcanzado, espera un minuto"
            404 -> "404 " + txt.take(160)
            else -> "Error $code " + txt.take(120)
        })
        return JSONObject(txt)
    }

    private val TEXT_MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.5-flash", "gemini-3.1-flash-lite", "gemini-2.5-flash")
    private val IMAGE_MODELS = listOf("gemini-3.1-flash-lite-image", "gemini-3.1-flash-image", "gemini-nano-banana-2.1", "gemini-2.5-flash-image")

    /** Prueba los modelos en orden: si uno no existe o ya no está disponible, pasa al siguiente. */
    private fun callAny(key: String, models: List<String>, body: JSONObject): JSONObject {
        var last: Throwable? = null
        for (m in models) {
            try { return call(key, m, body) } catch (e: Throwable) {
                last = e
                val msg = e.message ?: ""
                if (!msg.startsWith("404")) throw e
            }
        }
        throw RuntimeException("Ningún modelo de Gemini disponible con tu clave: " + (last?.message ?: "").take(120))
    }

    private const val SYS = "Eres un diseñador de relojes para pantalla de bloqueo. Responde SOLO con un objeto JSON (sin markdown) con estas claves opcionales: " +
        "layout (stack|inline|col|grid|diag|words|analog), an (1-12, solo si layout=analog), font (flex|serif|mono|anton|antonio|bigshoulders|bodoni|italiana|caveat|dmserif|syne|bebas), " +
        "wght (100-900), wdth (35-150, solo flex), rond (0-100, solo flex), fx (solid|glass|blend|overlay), tr (0-100), color (#RRGGBB), c2 (#RRGGBB para degradado o null), " +
        "glow (0-100), shadow (0-100), size (150-260), xs (60-160), gap (0-40), ol (0|1 contorno), datef (0-3), sig (texto corto de firma). Elige lo que mejor encaje con la descripción."

    private fun hex(s: String?): Int? = try { if (s.isNullOrEmpty() || s == "null") null else android.graphics.Color.parseColor(s) } catch (e: Throwable) { null }

    private const val SYS4 = "Eres un diseñador de relojes para pantalla de bloqueo. Responde SOLO con JSON (sin markdown): {\"variants\":[obj,obj,obj,obj]} con 4 variantes MUY distintas entre sí; cada obj usa estas claves opcionales: " +
        "layout (stack|inline|col|grid|diag|words|analog), an (1-12, solo si layout=analog), font (flex|serif|mono|anton|antonio|bigshoulders|bodoni|italiana|caveat|dmserif|syne|bebas), " +
        "wght (100-900), wdth (35-150, solo flex), rond (0-100, solo flex), fx (solid|glass|blend|overlay), tr (0-100), color (#RRGGBB), c2 (#RRGGBB para degradado o null), " +
        "glow (0-100), shadow (0-100), size (150-260), xs (60-160), gap (0-40), ol (0|1 contorno), datef (0-3), sig (texto corto de firma). Si se adjunta una imagen es el fondo de pantalla: elige colores que combinen y contrasten con ella."

    /** 4 variantes de estilo a partir de un texto y, opcionalmente, del fondo (JPEG en base64). */
    fun clockVariants(key: String, prompt: String, imgB64: String?): Pair<List<JSONObject>, String?> = try {
        val parts = org.json.JSONArray()
        if (imgB64 != null) parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", imgB64)))
        parts.put(JSONObject().put("text", if (prompt.isEmpty()) "Diseña relojes que combinen con este fondo." else prompt))
        val b = JSONObject()
        b.put("systemInstruction", JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", SYS4))))
        b.put("contents", org.json.JSONArray().put(JSONObject().put("parts", parts)))
        b.put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        val r = callAny(key, TEXT_MODELS, b)
        var t = r.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
        if (t.startsWith("```")) t = t.substringAfter('\n').substringBeforeLast("```")
        val arr = if (t.startsWith("[")) org.json.JSONArray(t) else JSONObject(t).optJSONArray("variants") ?: org.json.JSONArray().put(JSONObject(t))
        val out = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) out.add(arr.getJSONObject(i))
        Pair(out, null)
    } catch (e: Throwable) { Pair(emptyList<JSONObject>(), (e.message ?: e.toString()).take(160)) }

    fun jpegB64(b: Bitmap): String {
        val s = Bitmap.createScaledBitmap(b, 384, (b.height * 384 / b.width.toFloat()).toInt().coerceAtLeast(1), true)
        val bo = ByteArrayOutputStream()
        s.compress(Bitmap.CompressFormat.JPEG, 80, bo)
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
    }

    fun applyJson(c: StudioCfg, j: JSONObject) {
        j.optString("layout").takeIf { it in listOf("stack", "inline", "col", "grid", "diag", "words", "analog") }?.let { c.layout = it; c.ic = 0 }
        if (j.has("an")) c.an = j.optInt("an", c.an).coerceIn(1, 12)
        j.optString("font").takeIf { it in listOf("flex", "serif", "mono") + StudioFont.assetFonts }?.let { c.font = it }
        if (j.has("wght")) c.wght = j.optInt("wght", c.wght).coerceIn(100, 900)
        if (j.has("wdth")) c.wdth = j.optInt("wdth", c.wdth).coerceIn(35, 150)
        if (j.has("rond")) c.rond = j.optInt("rond", c.rond).coerceIn(0, 100)
        j.optString("fx").takeIf { it in listOf("solid", "glass", "blend", "overlay") }?.let { c.fx = it }
        if (j.has("tr")) c.tr = j.optInt("tr", c.tr).coerceIn(0, 100)
        hex(j.optString("color", ""))?.let { c.color = it; c.cmode = 0 }
        if (j.has("c2")) c.c2 = hex(j.optString("c2", "")) ?: 0
        if (j.has("glow")) c.glow = j.optInt("glow", 0).coerceIn(0, 100)
        if (j.has("shadow")) c.shadow = j.optInt("shadow", 0).coerceIn(0, 100)
        if (j.has("size")) c.size = j.optInt("size", c.size).coerceIn(100, 300)
        if (j.has("xs")) c.xs = j.optInt("xs", 100).coerceIn(60, 160)
        if (j.has("gap")) c.gap = j.optInt("gap", 10).coerceIn(0, 40)
        if (j.has("ol")) c.ol = j.optInt("ol", 0).coerceIn(0, 1)
        if (j.has("datef")) c.datef = j.optInt("datef", 0).coerceIn(0, 3)
        if (j.has("sig")) c.sig = j.optString("sig", "").take(40)
    }

    fun wallpaper(key: String, prompt: String): Pair<Bitmap?, String?> = try {
        val b = JSONObject()
        b.put("contents", org.json.JSONArray().put(JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", "Fondo de pantalla de móvil, formato vertical 9:16, sin texto ni marcas. $prompt")))))
        b.put("generationConfig", JSONObject().put("responseModalities", org.json.JSONArray().put("TEXT").put("IMAGE")))
        val r = callAny(key, IMAGE_MODELS, b)
        val parts = r.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        var out: Bitmap? = null
        for (i in 0 until parts.length()) {
            val pt = parts.getJSONObject(i)
            val d = pt.optJSONObject("inlineData") ?: pt.optJSONObject("inline_data")
            if (d != null) { val by = Base64.decode(d.getString("data"), Base64.DEFAULT); out = BitmapFactory.decodeByteArray(by, 0, by.size); break }
        }
        if (out == null) Pair(null, "Gemini no devolvió imagen") else Pair(out, null)
    } catch (e: Throwable) { Pair(null, (e.message ?: e.toString()).take(160)) }
}
