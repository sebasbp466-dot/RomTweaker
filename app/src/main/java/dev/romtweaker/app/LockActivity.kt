package dev.romtweaker.app

import android.app.Activity
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.TextView

/** Pantalla de bloqueo propia: se muestra sobre el keyguard; al deslizar hacia arriba pide desbloquear. */
class LockActivity : Activity() {
    private var userPresent: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            val ic = window.insetsController
            if (ic != null) {
                ic.hide(WindowInsets.Type.systemBars())
                ic.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        val cfg = ClockCfg.load(this)
        val root = FrameLayout(this)
        val dim = View(this)
        dim.setBackgroundColor(0x40000000)
        root.addView(dim, FrameLayout.LayoutParams(-1, -1))
        val face = ClockFace(this)
        face.cfg = cfg
        val fl = FrameLayout.LayoutParams(-1, -2)
        val dm = resources.displayMetrics
        fl.gravity = if (cfg.pos == 0) Gravity.TOP else if (cfg.pos == 2) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        fl.topMargin = if (cfg.pos == 0) (dm.heightPixels * 0.12f).toInt() else 0
        fl.bottomMargin = if (cfg.pos == 2) (dm.heightPixels * 0.18f).toInt() else 0
        root.addView(face, fl)

        val bat = TextView(this)
        bat.setTextColor(Color.WHITE)
        bat.textSize = 13f
        val st = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (st != null) {
            val lv = st.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val sc = st.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (lv >= 0) bat.text = "${lv * 100 / sc}%"
        }
        val bl = FrameLayout.LayoutParams(-2, -2)
        bl.gravity = Gravity.TOP or Gravity.END
        bl.topMargin = (dm.density * 36).toInt()
        bl.rightMargin = (dm.density * 24).toInt()
        root.addView(bat, bl)

        val hint = TextView(this)
        hint.text = "Desliza hacia arriba para desbloquear"
        hint.setTextColor(Color.WHITE)
        hint.alpha = 0.8f
        hint.textSize = 14f
        val hl = FrameLayout.LayoutParams(-2, -2)
        hl.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        hl.bottomMargin = (dm.density * 48).toInt()
        root.addView(hint, hl)

        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (vy < -600f) { unlock(); return true }
                return false
            }
            override fun onSingleTapUp(e: MotionEvent): Boolean { hint.alpha = 1f; return true }
        })
        root.setOnTouchListener { _, ev -> gd.onTouchEvent(ev) }
        setContentView(root)

        userPresent = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { finish() }
        }
        val f = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(userPresent, f, Context.RECEIVER_EXPORTED) else registerReceiver(userPresent, f)
    }

    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java)
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { finish() }
            override fun onDismissCancelled() {}
            override fun onDismissError() {}
        })
    }

    override fun onBackPressed() {}

    override fun onDestroy() {
        try { userPresent?.let { unregisterReceiver(it) } } catch (e: Throwable) { }
        super.onDestroy()
    }
}

/** Servicio que lanza la pantalla de bloqueo propia cuando se enciende la pantalla. */
class LockService : Service() {
    private var rcv: BroadcastReceiver? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("rt_lock", "Pantalla de bloqueo", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "rt_lock")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("RomTweaker")
            .setContentText("Pantalla de bloqueo propia activa")
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(7001, n)
        rcv = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { show() }
        }
        val f = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(rcv, f, Context.RECEIVER_EXPORTED) else registerReceiver(rcv, f)
    }

    private fun show() {
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) return
        val i = Intent(this, LockActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(i)
    }

    override fun onDestroy() {
        try { rcv?.let { unregisterReceiver(it) } } catch (e: Throwable) { }
        super.onDestroy()
    }
}

class LockBootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (c.getSharedPreferences("lock", Context.MODE_PRIVATE).getBoolean("lock_on", false)) {
            try { c.startForegroundService(Intent(c, LockService::class.java)) } catch (e: Throwable) { }
        }
    }
}
