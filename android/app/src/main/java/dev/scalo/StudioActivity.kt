package dev.scalo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.*

class StudioActivity : Activity() {
    private lateinit var web: WebView
    private var mic: PermissionRequest? = null
    private var chooser: ValueCallback<Array<Uri>>? = null
    private val origin = "https://scalo.local"
    private val types = mapOf("index.html" to "text/html","app.js" to "text/javascript",
        "style.css" to "text/css","demo.js" to "text/javascript","manifest.webmanifest" to "application/manifest+json",
        "icon-192.png" to "image/png","icon-512.png" to "image/png")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = true
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url
                if(u.scheme !in listOf("http","https")) return null
                val file = u.path?.removePrefix("/") ?: ""
                val mime = types[file]
                if(u.scheme == "https" && u.host == "scalo.local" && mime != null)
                    return WebResourceResponse(mime, if(mime.startsWith("image/")) null else "UTF-8", assets.open(file))
                return WebResourceResponse("text/plain","UTF-8",404,"Not found", emptyMap(),null)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                if(request.origin.toString().trimEnd('/') != origin ||
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE !in request.resources) { request.deny(); return }
                if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                else {
                    mic?.deny()
                    mic=request
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),1)
                }
            }
            override fun onPermissionRequestCanceled(request: PermissionRequest) { if(mic===request) mic=null }
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                chooser?.onReceiveValue(null)
                chooser=callback
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE);type="audio/*"
                },2)
                return true
            }
        }
        val container = android.widget.FrameLayout(this)
        container.addView(web)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            container.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        setContentView(container)
        web.loadUrl("$origin/index.html")
        if (savedInstanceState == null && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            val phrase = LaunchPhrase(this)
            container.addView(phrase)
            phrase.postOnAnimation { if (phrase.isAttachedToWindow) phrase.start() }
        }
    }
    override fun onRequestPermissionsResult(code:Int, permissions:Array<out String>, results:IntArray){
        super.onRequestPermissionsResult(code,permissions,results)
        if(code==1){ if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED)
            mic?.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) else mic?.deny()
            mic=null }
    }
    override fun onActivityResult(code:Int,result:Int,data:Intent?){
        super.onActivityResult(code,result,data)
        if(code==2){chooser?.onReceiveValue(if(result==RESULT_OK) data?.data?.let {arrayOf(it)} else null);chooser=null}
    }
    override fun onStop(){web.evaluateJavascript("if(live||micPending)stopMic();audio.pause();",null);super.onStop()}
    override fun onDestroy(){mic?.deny();chooser?.onReceiveValue(null);web.destroy();super.onDestroy()}
}


