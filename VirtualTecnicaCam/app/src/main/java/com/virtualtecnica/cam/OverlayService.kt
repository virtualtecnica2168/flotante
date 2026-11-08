package com.virtualtecnica.cam

import android.app.*
import android.content.*
import android.database.ContentObserver
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.core.app.ServiceCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Cambia esta URL si pones upload.php en otra ruta
const val UPLOAD_URL = "https://www.virtualtecnica.com/upload.php"

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var lp: WindowManager.LayoutParams
    private var qrView: View? = null
    private val h = Handler(Looper.getMainLooper())
    private var videoUri: Uri? = null
    private var videoName = ""
    private var lastId = -1L
    private var link = ""

    private val scan = Runnable { findLatest() }
    private val observer = object : ContentObserver(h) {
        override fun onChange(selfChange: Boolean) { h.removeCallbacks(scan); h.postDelayed(scan, 2000) }
    }

    override fun onBind(i: Intent?): IBinder? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun say(t: String) { status.text = t }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("vt", "Burbuja", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "vt").setContentTitle("VirtualTécnica Cam activa")
            .setSmallIcon(android.R.drawable.ic_menu_camera).build()
        ServiceCompat.startForeground(this, 1, n,
            if (Build.VERSION.SDK_INT >= 34) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildBubble()
        contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    private fun btn(t: String, c: Int, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 12f; minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        setPadding(dp(6), dp(8), dp(6), dp(8)); setTextColor(Color.WHITE)
        background = GradientDrawable().apply { setColor(c); cornerRadius = dp(10).toFloat() }
        setOnClickListener { f() }
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }
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
        val row = LinearLayout(this).apply {
            setPadding(0, dp(6), 0, 0)
            addView(btn("Subir", 0xFF3B82F6.toInt()) { upload { } })
            addView(btn("WhatsApp", 0xFF1FA855.toInt()) { upload { u -> whatsapp(u) } })
            addView(btn("QR", 0xFF7C5CFF.toInt()) { upload { u -> showQR(u) } })
        }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(status); addView(row) }
        root.addView(header); root.addView(body)

        lp = WindowManager.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT,
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

    // Busca el video más reciente en DCIM (donde guarda la cámara de Samsung, incluida cámara lenta)
    private fun findLatest() {
        var sel = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE 'DCIM/%'"
        if (Build.VERSION.SDK_INT >= 29) sel += " AND ${MediaStore.MediaColumns.IS_PENDING}=0"
        try {
            contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME),
                sel, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    if (id != lastId) {
                        lastId = id; videoName = c.getString(1); link = ""
                        videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        say("Nuevo video: $videoName")
                    }
                }
            }
        } catch (e: Exception) { say("Sin permiso para leer videos. Ábrela y da el permiso 2.") }
    }

    private fun upload(then: (String) -> Unit) {
        if (link.isNotEmpty()) { then(link); return }
        val uri = videoUri ?: run { say("Aún no hay video nuevo."); return }
        val name = videoName
        say("Subiendo $name…")
        Thread {
            try {
                val b = "----vt" + System.currentTimeMillis()
                val c = URL(UPLOAD_URL).openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; c.setChunkedStreamingMode(64 * 1024)
                c.connectTimeout = 15000; c.readTimeout = 120000
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
                c.outputStream.use { o ->
                    o.write("--$b\r\nContent-Disposition: form-data; name=\"video\"; filename=\"$name\"\r\nContent-Type: video/mp4\r\n\r\n".toByteArray())
                    contentResolver.openInputStream(uri)!!.use { it.copyTo(o) }
                    o.write("\r\n--$b--\r\n".toByteArray())
                }
                val url = JSONObject(c.inputStream.bufferedReader().readText()).getString("url")
                h.post { link = url; say("Subido: $url"); then(url) }
            } catch (e: Exception) { h.post { say("Error al subir: ${e.message}") } }
        }.start()
    }

    private fun whatsapp(u: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=" + Uri.encode("Tu video está aquí, míralo y descárgalo: $u")))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    private fun showQR(u: String) {
        qrView?.let { wm.removeView(it) }
        val m = QRCodeWriter().encode(u, BarcodeFormat.QR_CODE, 512, 512)
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
        for (x in 0 until 512) for (y in 0 until 512) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
            addView(ImageView(this@OverlayService).apply { setImageBitmap(bmp); layoutParams = LinearLayout.LayoutParams(dp(260), dp(260)) })
            addView(TextView(this@OverlayService).apply { text = "Escanea para ver y descargar\n(toca para cerrar)"; setTextColor(Color.BLACK); gravity = Gravity.CENTER })
            setOnClickListener { wm.removeView(this); qrView = null }
        }
        val p = WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        wm.addView(v, p); qrView = v
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        runCatching { wm.removeView(root) }; qrView?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }
}
