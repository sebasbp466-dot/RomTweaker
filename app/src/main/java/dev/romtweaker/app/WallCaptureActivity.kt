package dev.romtweaker.app

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import java.io.File

/**
 * Captura el fondo real (también los animados): se abre una ventana transparente sin barras
 * que deja ver el fondo detrás, se hace una captura con root y se guarda como fondo de vista previa.
 */
class WallCaptureActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val v = View(this)
        v.setBackgroundColor(Color.TRANSPARENT)
        setContentView(v)
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            Thread {
                val ok = capture()
                runOnUiThread {
                    setResult(if (ok) RESULT_OK else RESULT_CANCELED)
                    finish()
                    overridePendingTransition(0, 0)
                    Toast.makeText(applicationContext, if (ok) "Fondo capturado" else "No pude capturar el fondo (¿root?)", Toast.LENGTH_SHORT).show()
                }
            }.start()
        }, 1800)
    }

    private fun capture(): Boolean {
        val vs = listOf(arrayOf("su", "-c"), arrayOf("/data/adb/ksu/bin/su", "-c"), arrayOf("/system/xbin/su", "-c"))
        for (su in vs) {
            try {
                val p = ProcessBuilder(su[0], su[1], "screencap -p").start()
                val bytes = p.inputStream.readBytes()
                p.waitFor()
                if (bytes.size < 5000) continue
                val o = BitmapFactory.Options()
                o.inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                if (o.outWidth <= 0) continue
                var ss = 1
                while (maxOf(o.outWidth, o.outHeight) / ss > 2000) ss *= 2
                val o2 = BitmapFactory.Options()
                o2.inSampleSize = ss
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2) ?: continue
                File(filesDir, "prev_bg.jpg").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
                StudioWall.reset()
                return true
            } catch (e: Throwable) { }
        }
        return false
    }
}
