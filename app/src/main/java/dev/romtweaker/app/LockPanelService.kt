package dev.romtweaker.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** EXPERIMENTAL: panel flotante con controles del reloj encima de la pantalla de bloqueo real (si la ROM lo permite). */
class LockPanelService : Service() {
    private var wm: WindowManager? = null
    private var view: View? = null

    companion object {
        fun toggle(ctx: Context, on: Boolean) {
            val i = Intent(ctx, LockPanelService::class.java)
            if (on) ctx.startForegroundService(i) else ctx.stopService(i)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("lockpanel", "Panel del reloj", NotificationManager.IMPORTANCE_MIN))
            val n = Notification.Builder(this, "lockpanel").setSmallIcon(android.R.drawable.ic_menu_edit).setContentTitle("Panel del reloj de bloqueo activo").build()
            if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(7, n)
        } catch (e: Throwable) { }
        Thread {
            try { Sx.su("appops set " + packageName + " SYSTEM_ALERT_WINDOW allow") } catch (e: Throwable) { }
            Handler(Looper.getMainLooper()).post { show() }
        }.start()
        return START_STICKY
    }

    private fun seek(m: M3, cfg: ClockCfg, label: String, min: Int, max: Int, v0: Int, set: (Int) -> Unit, push: () -> Unit): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        val t = m.tv(label + ": " + v0, 12f, m.onSurfaceVar)
        l.addView(t)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = v0 - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, pr: Int, fromUser: Boolean) {
                t.text = label + ": " + (pr + min)
                if (fromUser) set(pr + min)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { push() }
        })
        l.addView(sb)
        return l
    }

    private fun show() {
        if (view != null) return
        try {
            val m = M3(this, false)
            val cfg = ClockCfg.load(this)
            val lp = getSharedPreferences("lock", Context.MODE_PRIVATE)
            fun push() { cfg.save(this); LockXp.apply(cfg, true) }
            val root = LinearLayout(this)
            root.orientation = LinearLayout.VERTICAL
            root.setPadding(m.dp(16), m.dp(12), m.dp(16), m.dp(12))
            root.background = m.shape(m.a(m.surfaceCont, 0.95f), 28)
            val top = LinearLayout(this)
            top.gravity = Gravity.CENTER_VERTICAL
            top.addView(m.tv("Reloj del bloqueo", 15f, m.onSurface, true), LinearLayout.LayoutParams(0, m.WC, 1f))
            val x = m.tv("✕", 18f, m.onSurfaceVar, true)
            x.setPadding(m.dp(12), 0, m.dp(4), 0)
            x.setOnClickListener { lp.edit().putBoolean("lk_panel", false).apply(); stopSelf() }
            top.addView(x)
            root.addView(top)
            val chips = LinearLayout(this)
            val st = listOf(Pair("glass", "Cristal"), Pair("color", "Degradado"), Pair("solid", "Sólido"))
            for (s in st) { val id = s.first; chips.addView(m.chip(s.second, cfg.style == id) { cfg.style = id; push() }, m.lp(m.WC, m.WC, 0, 6, 8, 4)) }
            root.addView(chips)
            root.addView(seek(m, cfg, "Grosor", 100, 900, cfg.weight, { cfg.weight = it }, { push() }))
            root.addView(seek(m, cfg, "Ancho", 50, 150, cfg.wdth, { cfg.wdth = it }, { push() }))
            root.addView(seek(m, cfg, "Redondez", 0, 100, cfg.rond, { cfg.rond = it }, { push() }))
            root.addView(seek(m, cfg, "Cristal", 0, 100, cfg.glass, { cfg.glass = it }, { push() }))
            val box = FrameLayout(this)
            val fl = FrameLayout.LayoutParams(-1, -2)
            fl.setMargins(m.dp(10), 0, m.dp(10), m.dp(10))
            box.addView(root, fl)
            @Suppress("DEPRECATION")
            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            val wp = WindowManager.LayoutParams(-1, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT)
            wp.gravity = Gravity.BOTTOM
            val w = getSystemService(WindowManager::class.java)
            w.addView(box, wp)
            wm = w
            view = box
        } catch (e: Throwable) {
            getSharedPreferences("lock", Context.MODE_PRIVATE).edit().putBoolean("lk_panel", false).apply()
            stopSelf()
        }
    }

    override fun onDestroy() {
        try { view?.let { wm?.removeView(it) } } catch (e: Throwable) { }
        view = null
        super.onDestroy()
    }
}
