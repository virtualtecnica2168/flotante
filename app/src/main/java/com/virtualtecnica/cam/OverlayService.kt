package com.virtualtecnica.cam

import android.app.*
import android.content.*
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.core.app.ServiceCompat

/** Burbuja sobre la cámara: avisa del video nuevo y abre la app para ponerle el marco. */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var lp: WindowManager.LayoutParams
    private val h = Handler(Looper.getMainLooper())
    private var lastId = -1L

    private val scan = Runnable {
        Media.latest(this)?.let { if (it.first != lastId) { lastId = it.first; status.text = "Nuevo video: ${it.second}" } }
    }
    private val observer = object : ContentObserver(h) {
        override fun onChange(selfChange: Boolean) { h.removeCallbacks(scan); h.postDelayed(scan, 2000) }
    }

    override fun onBind(i: Intent?): IBinder? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("vt", "Burbuja", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "vt").setContentTitle("VirtualTécnica Cam activa")
            .setSmallIcon(android.R.drawable.ic_menu_camera).build()
        ServiceCompat.startForeground(this, 1, n,
            if (Build.VERSION.SDK_INT >= 34) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildBubble()
        contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("autoload", true))
    }

    private fun buildBubble() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(8))
            background = GradientDrawable().apply { setColor(0xEE171C24.toInt()); cornerRadius = dp(16).toFloat() }
        }
        val header = TextView(this).apply {
            text = "☰ VirtualTécnica  (arrastra · toca para ocultar)"; setTextColor(0xFF8B95A5.toInt()); textSize = 11f
            setPadding(0, 0, 0, dp(4))
        }
        status = TextView(this).apply { text = "Graba un video con la cámara…"; setTextColor(Color.WHITE); textSize = 12f; maxLines = 2 }
        val go = Button(this).apply {
            text = "Procesar con marco"; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE)
            minHeight = 0; minimumHeight = 0; setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply { setColor(0xFFFF4F81.toInt()); cornerRadius = dp(10).toFloat() }
            setOnClickListener { openApp() }
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status); addView(go, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        root.addView(header); root.addView(body)

        lp = WindowManager.LayoutParams(dp(250), ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(70)
        }
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX - tx) > 10 || Math.abs(e.rawY - ty) > 10) moved = true
                    lp.x = sx + (e.rawX - tx).toInt(); lp.y = sy + (e.rawY - ty).toInt(); wm.updateViewLayout(root, lp)
                }
                MotionEvent.ACTION_UP -> if (!moved) body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
            true
        }
        wm.addView(root, lp)
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        runCatching { wm.removeView(root) }
        super.onDestroy()
    }
}
