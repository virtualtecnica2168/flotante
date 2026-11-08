package com.virtualtecnica.cam

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.webkit.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.OutputStream

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var loader: WebViewAssetLoader
    private var chooser: ValueCallback<Array<Uri>>? = null
    private var loaded = false
    private var pending = false
    private var seen = -1L
    private var saveOut: OutputStream? = null
    private var saveUri: Uri? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) { p += Manifest.permission.READ_MEDIA_VIDEO; p += Manifest.permission.POST_NOTIFICATIONS }
        else p += Manifest.permission.READ_EXTERNAL_STORAGE
        requestPermissions(p.toTypedArray(), 1)

        seen = Media.latest(this)?.first ?: -1L
        loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web = WebView(this)
        setContentView(web)
        WebView.setWebContentsDebuggingEnabled(true)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.addJavascriptInterface(Bridge(), "Android")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url
                if (u.host == "appassets.androidplatform.net" && u.path == "/latest.mp4") {
                    val id = u.getQueryParameter("id")?.toLongOrNull() ?: return null
                    return try {
                        WebResourceResponse("video/mp4", null, 200, "OK",
                            mapOf("Access-Control-Allow-Origin" to "*"), contentResolver.openInputStream(Media.uri(id)))
                    } catch (e: Exception) { null }
                }
                return loader.shouldInterceptRequest(u)
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == "appassets.androidplatform.net") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (e: Exception) { }
                return true
            }
            override fun onPageFinished(view: WebView, url: String) {
                loaded = true
                if (pending) { pending = false; js("cargarUltimo()") }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) { runOnUiThread { request.grant(request.resources) } }
            override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                chooser?.onReceiveValue(null); chooser = cb
                return try { startActivityForResult(params.createIntent(), 7); true } catch (e: Exception) { chooser = null; false }
            }
        }
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        handle(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 7) {
            chooser?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data)); chooser = null
        } else super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    private fun handle(i: Intent?) {
        if (i?.getBooleanExtra("autoload", false) == true) {
            seen = Media.latest(this)?.first ?: seen
            if (loaded) js("cargarUltimo()") else pending = true
        }
    }

    // Al volver de la cámara, si hay un video nuevo se carga solo
    override fun onResume() {
        super.onResume()
        if (!loaded) return
        val l = Media.latest(this) ?: return
        if (l.first != seen) { seen = l.first; js("window.onNuevoVideo&&onNuevoVideo()") }
    }

    private fun js(code: String) { web.post { web.evaluateJavascript(code, null) } }
    private fun toast(t: String) = runOnUiThread { Toast.makeText(this, t, Toast.LENGTH_LONG).show() }

    inner class Bridge {
        @JavascriptInterface fun latestVideo(): String =
            Media.latest(this@MainActivity)?.let {
                JSONObject().put("id", it.first).put("name", it.second).put("size", it.third).toString()
            } ?: ""

        @JavascriptInterface fun openCamera() = runOnUiThread {
            val i = packageManager.getLaunchIntentForPackage("com.sec.android.app.camera")
                ?: Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
            startActivity(i)
        }

        @JavascriptInterface fun startBubble() = runOnUiThread {
            if (!Settings.canDrawOverlays(this@MainActivity)) {
                toast("Activa «Mostrar sobre otras apps» para VirtualTécnica Cam y vuelve a pulsar el botón.")
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, OverlayService::class.java))
                openCamera()
            }
        }

        @JavascriptInterface fun openUrl(u: String) = runOnUiThread {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } catch (e: Exception) { toast("No se pudo abrir el enlace") }
        }

        // Guardado por partes del video procesado en Películas/VirtualTecnica
        @JavascriptInterface fun saveBegin(name: String) {
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, if (name.endsWith("webm")) "video/webm" else "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/VirtualTecnica")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            saveUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)
            saveOut = contentResolver.openOutputStream(saveUri!!)
        }
        @JavascriptInterface fun saveChunk(b64: String) { saveOut?.write(Base64.decode(b64, Base64.DEFAULT)) }
        @JavascriptInterface fun saveEnd() {
            try { saveOut?.close() } catch (e: Exception) { }
            saveOut = null
            saveUri?.let { contentResolver.update(it, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) }
            saveUri = null
        }
    }
}
