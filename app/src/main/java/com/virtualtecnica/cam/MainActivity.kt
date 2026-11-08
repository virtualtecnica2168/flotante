package com.virtualtecnica.cam

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var info: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (40 * d).toInt(), (20 * d).toInt(), (20 * d).toInt())
        }
        fun add(t: String, f: () -> Unit) = col.addView(Button(this).apply {
            text = t; isAllCaps = false; setOnClickListener { f() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(TextView(this).apply { text = "VirtualTécnica Cam"; textSize = 22f })
        info = TextView(this).apply {
            text = "Da los permisos 1 y 2, pulsa «Iniciar» y graba con la cámara de Samsung. La burbuja detecta el video nuevo y te deja subirlo, enviarlo por WhatsApp o mostrar el QR."
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
        }
        col.addView(info)
        add("1. Permitir mostrar sobre otras apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        add("2. Permitir videos y notificaciones") {
            val p = if (Build.VERSION.SDK_INT >= 33)
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS)
            else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            requestPermissions(p, 1)
        }
        add("3. Iniciar burbuja y abrir cámara") {
            if (!Settings.canDrawOverlays(this)) info.text = "Falta el permiso 1."
            else {
                ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
                val i = packageManager.getLaunchIntentForPackage("com.sec.android.app.camera")
                    ?: Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
                startActivity(i)
            }
        }
        add("Detener burbuja") { stopService(Intent(this, OverlayService::class.java)) }
        setContentView(col)
    }
}
