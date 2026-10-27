package dev.romtweaker.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.content.res.ColorStateList
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

object Controls {
    class Tile(val key: String, val label: String)

    val tiles = listOf(
        Tile("wifi", "Wi-Fi"), Tile("bt", "Bluetooth"), Tile("data", "Datos"), Tile("air", "Avión"),
        Tile("torch", "Linterna"), Tile("rot", "Rotación"), Tile("dnd", "No molestar"), Tile("loc", "Ubicación")
    )

    @Volatile
    var torchOn = false

    private fun get(ns: String, key: String): String = Root.su("settings get " + ns + " " + key).second.trim()

    private fun truthy(v: String): Boolean = v.isNotEmpty() && v != "null" && v != "0"

    fun read(key: String): Boolean {
        return when (key) {
            "wifi" -> truthy(get("global", "wifi_on"))
            "bt" -> truthy(get("global", "bluetooth_on"))
            "data" -> get("global", "mobile_data") == "1"
            "air" -> get("global", "airplane_mode_on") == "1"
            "torch" -> torchOn
            "rot" -> get("system", "accelerometer_rotation") == "1"
            "dnd" -> truthy(get("global", "zen_mode"))
            "loc" -> truthy(get("secure", "location_mode"))
            else -> false
        }
    }

    fun torch(ctx: Context, on: Boolean): String {
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var id: String? = null
            for (c in cm.cameraIdList) {
                val ch = cm.getCameraCharacteristics(c)
                if (ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    id = c
                    break
                }
            }
            if (id == null) {
                "✗ Este dispositivo no tiene flash"
            } else {
                cm.setTorchMode(id, on)
                torchOn = on
                if (on) "✓ Linterna encendida" else "✓ Linterna apagada"
            }
        } catch (e: Exception) {
            "✗ Linterna: " + e.message
        }
    }

    fun set(ctx: Context, key: String, on: Boolean): String {
        val v = if (on) "1" else "0"
        val e = if (on) "enable" else "disable"
        val steps: List<Adapt.Step> = when (key) {
            "wifi" -> listOf(
                Adapt.Step("svc wifi", "svc wifi " + e),
                Adapt.Step("cmd wifi", "cmd wifi set-wifi-enabled " + (if (on) "enabled" else "disabled")),
                Adapt.Step("ajuste wifi_on", "settings put global wifi_on " + v)
            )
            "bt" -> listOf(
                Adapt.Step("svc bluetooth", "svc bluetooth " + e),
                Adapt.Step("cmd bluetooth_manager", "cmd bluetooth_manager " + e),
                Adapt.Step("ajuste bluetooth_on", "settings put global bluetooth_on " + v)
            )
            "data" -> listOf(
                Adapt.Step("svc data", "svc data " + e),
                Adapt.Step("cmd phone data", "cmd phone data " + e),
                Adapt.Step("ajuste mobile_data", "settings put global mobile_data " + v)
            )
            "air" -> listOf(
                Adapt.Step("cmd connectivity", "cmd connectivity airplane-mode " + e),
                Adapt.Step("ajuste + broadcast", "settings put global airplane_mode_on " + v + "; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state " + on)
            )
            "rot" -> listOf(
                Adapt.Step("settings system", "settings put system accelerometer_rotation " + v),
                Adapt.Step("cmd settings", "cmd settings put system accelerometer_rotation " + v)
            )
            "dnd" -> listOf(
                Adapt.Step("cmd notification", "cmd notification set_dnd " + (if (on) "priority" else "off")),
                Adapt.Step("ajuste zen_mode", "settings put global zen_mode " + (if (on) "1" else "0"))
            )
            "loc" -> listOf(
                Adapt.Step("cmd location", "cmd location set-location-enabled " + on),
                Adapt.Step("ajuste location_mode", "settings put secure location_mode " + (if (on) "3" else "0"))
            )
            else -> emptyList()
        }
        if (steps.isEmpty()) return "?"
        return Adapt.attempt(ctx, "cc-" + key, steps) {
            Thread.sleep(400)
            read(key) == on
        }.lines().first()
    }

    fun brightness(ctx: Context, v: Int): String {
        val steps = listOf(
            Adapt.Step("settings put", "settings put system screen_brightness_mode 0; settings put system screen_brightness " + v),
            Adapt.Step("cmd settings", "cmd settings put system screen_brightness_mode 0; cmd settings put system screen_brightness " + v),
            Adapt.Step("cmd display", "cmd display set-brightness " + (v / 255f))
        )
        return Adapt.attempt(ctx, "cc-brillo", steps) {
            Math.abs((get("system", "screen_brightness").toIntOrNull() ?: -999) - v) <= 2
        }.lines().first()
    }
}

object ControlCenter {
    private const val PKG = "dev.romtweaker.app"

    fun grant(act: Activity, report: (String) -> Unit) {
        Adapt.async(act, report) {
            val sb = StringBuilder()
            val ov = Adapt.attempt(
                act, "perm-overlay",
                listOf(
                    Adapt.Step("appops", "appops set " + PKG + " SYSTEM_ALERT_WINDOW allow"),
                    Adapt.Step("cmd appops", "cmd appops set " + PKG + " SYSTEM_ALERT_WINDOW allow"),
                    Adapt.Step("pm grant", "pm grant " + PKG + " android.permission.SYSTEM_ALERT_WINDOW")
                )
            ) { Settings.canDrawOverlays(act) }
            sb.append(ov.lines().first()).append('\n')
            val nt = Adapt.attempt(
                act, "perm-notif",
                listOf(Adapt.Step("pm grant", "pm grant " + PKG + " android.permission.POST_NOTIFICATIONS")),
                null
            )
            sb.append(nt.lines().first()).append('\n')
            Root.su("cmd deviceidle whitelist +" + PKG + "; appops set " + PKG + " RUN_ANY_IN_BACKGROUND allow; appops set " + PKG + " START_FOREGROUND allow")
            sb.append("Batería: sin restricciones para la app")
            sb.toString()
        }
    }

    fun enable(act: Activity, on: Boolean, report: (String) -> Unit) {
        act.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("cc", on).apply()
        if (on) {
            if (!Settings.canDrawOverlays(act)) {
                report("Primero pulsa «Conceder permisos con root».")
                return
            }
            act.startForegroundService(Intent(act, ControlCenterService::class.java))
            report("Centro de control activado. Desliza hacia abajo desde la esquina superior derecha.")
        } else {
            act.stopService(Intent(act, ControlCenterService::class.java))
            report("Centro de control desactivado.")
        }
    }

    fun open(act: Activity) {
        if (!Settings.canDrawOverlays(act)) return
        act.startForegroundService(Intent(act, ControlCenterService::class.java).setAction(ControlCenterService.ACTION_OPEN))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("cc", false)) {
            try {
                ctx.startForegroundService(Intent(ctx, ControlCenterService::class.java))
            } catch (e: Exception) {
            }
        }
    }
}

class ControlCenterService : Service() {
    companion object {
        const val ACTION_OPEN = "dev.romtweaker.app.OPEN"
        const val ACTION_STOP = "dev.romtweaker.app.STOP"
        private const val CHANNEL = "rt_cc"
    }

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var handle: View? = null
    private var panel: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (handle == null) addHandle()
        if (intent?.action == ACTION_OPEN) openPanel()
        return START_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Centro de control", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getService(
            this, 1, Intent(this, ControlCenterService::class.java).setAction(ACTION_OPEN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, ControlCenterService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Centro de control")
            .setContentText("Desliza desde la esquina superior derecha")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_view, "Abrir panel", open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun addHandle() {
        if (!Settings.canDrawOverlays(this)) return
        val dens = resources.displayMetrics.density
        val v = View(this)
        val lp = WindowManager.LayoutParams(
            (120 * dens).toInt(), (22 * dens).toInt(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.END
        var y0 = 0f
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    y0 = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (e.rawY - y0 > 40 * dens) {
                        y0 = Float.MAX_VALUE
                        openPanel()
                    }
                    true
                }
                else -> true
            }
        }
        try {
            wm.addView(v, lp)
            handle = v
        } catch (e: Exception) {
        }
    }

    private fun style(tv: TextView, ui: Ui, on: Boolean) {
        val p = ui.p
        tv.tag = on
        tv.setTextColor(if (on) p.onPrimary else p.onContainer)
        if (p.glass) {
            if (on) {
                val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.parseColor("#4FC3FF"), Color.parseColor("#9B7BFF")))
                g.cornerRadius = ui.dp(20).toFloat()
                tv.background = g
            } else {
                tv.background = ui.glassBg(20, true)
            }
        } else {
            tv.background = ui.round(if (on) p.primary else p.container, 20)
        }
    }

    private fun openPanel() {
        if (panel != null || !Settings.canDrawOverlays(this)) return
        val glass = getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("glass", false)
        val ui = Ui(this, makePalette(this, glass))
        val p = ui.p

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#66000000"))
        root.isFocusableInTouchMode = true
        root.setOnClickListener { closePanel() }
        root.setOnKeyListener { _, code, ev ->
            if (code == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) {
                closePanel()
                true
            } else {
                false
            }
        }

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14))
        card.background = if (glass) ui.glassBg(32, true) else ui.round(p.surface, 32)
        card.isClickable = true

        val status = TextView(this)
        status.textSize = 11f
        status.setTextColor(p.onVariant)
        status.text = "Centro de control"

        val tileViews = HashMap<String, TextView>()
        for (rowTiles in Controls.tiles.chunked(4)) {
            val row = ui.row()
            for (t in rowTiles) {
                val tv = TextView(this)
                tv.text = t.label
                tv.textSize = 12f
                tv.gravity = Gravity.CENTER
                tv.setPadding(ui.dp(4), ui.dp(18), ui.dp(4), ui.dp(18))
                val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
                lp.setMargins(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
                tv.layoutParams = lp
                style(tv, ui, false)
                tv.setOnClickListener {
                    val want = tv.tag != true
                    style(tv, ui, want)
                    Thread {
                        val msg = if (t.key == "torch") Controls.torch(this, want) else Controls.set(this, t.key, want)
                        val real = Controls.read(t.key)
                        main.post {
                            style(tv, ui, real)
                            status.text = msg
                        }
                    }.start()
                }
                tileViews[t.key] = tv
                row.addView(tv)
            }
            card.addView(row)
        }

        fun label(t: String): TextView {
            val l = TextView(this)
            l.text = t
            l.textSize = 12f
            l.setTextColor(p.onVariant)
            l.setPadding(ui.dp(4), ui.dp(10), 0, 0)
            return l
        }

        val tint = ColorStateList.valueOf(p.primary)
        val bright = SeekBar(this)
        bright.max = 255
        bright.progress = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        bright.progressTintList = tint
        bright.thumbTintList = tint
        bright.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, pr: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                val v = (s?.progress ?: 128).coerceAtLeast(1)
                Thread {
                    val m = Controls.brightness(this@ControlCenterService, v)
                    main.post { status.text = m }
                }.start()
            }
        })
        card.addView(label("Brillo"))
        card.addView(bright)

        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val vol = SeekBar(this)
        vol.max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        vol.progress = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        vol.progressTintList = tint
        vol.thumbTintList = tint
        vol.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, pr: Int, fromUser: Boolean) {
                if (fromUser) am.setStreamVolume(AudioManager.STREAM_MUSIC, pr, 0)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        card.addView(label("Volumen multimedia"))
        card.addView(vol)

        val actions = ui.row()
        actions.setPadding(0, ui.dp(10), 0, 0)
        actions.addView(ui.chip("Ajustes", false) {
            closePanel()
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        actions.addView(ui.chip("RomTweaker", false) {
            closePanel()
            val li = packageManager.getLaunchIntentForPackage(packageName)
            if (li != null) startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        actions.addView(ui.chip("Cerrar", true) { closePanel() })
        card.addView(actions)
        card.addView(status)

        val clp = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP)
        clp.setMargins(ui.dp(10), ui.dp(34), ui.dp(10), 0)
        root.addView(card, clp)

        val lp = WindowManager.LayoutParams(
            MATCH, MATCH, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= 31 && wm.isCrossWindowBlurEnabled) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = 45
        }
        try {
            wm.addView(root, lp)
        } catch (e: Exception) {
            return
        }
        panel = root
        root.requestFocus()
        root.alpha = 0f
        card.translationY = -ui.dp(40).toFloat()
        root.animate().alpha(1f).setDuration(160).start()
        card.animate().translationY(0f).setDuration(200).start()

        Thread {
            val states = HashMap<String, Boolean>()
            for (t in Controls.tiles) states[t.key] = Controls.read(t.key)
            main.post {
                for ((k, tv) in tileViews) style(tv, ui, states[k] == true)
            }
        }.start()
    }

    private fun closePanel() {
        val pn = panel
        if (pn != null) {
            try {
                wm.removeView(pn)
            } catch (e: Exception) {
            }
        }
        panel = null
    }

    override fun onDestroy() {
        closePanel()
        val h = handle
        if (h != null) {
            try {
                wm.removeView(h)
            } catch (e: Exception) {
            }
        }
        handle = null
        super.onDestroy()
    }
}
