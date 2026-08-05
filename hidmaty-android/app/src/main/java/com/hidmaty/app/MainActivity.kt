package com.hidmaty.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.hidmaty.app.databinding.ActivityMainBinding
import java.io.File

/**
 * غلاف WebView كامل لمنصة حِدمتي — يخدم لوحتَي العميل والمزوّد معاً
 * (نفس الموقع يفرّق بينهما حسب تسجيل الدخول، فلا حاجة لتطبيقين منفصلين).
 *
 * يتعامل مع أشهر مشاكل تطبيقات WebView الجاهزة:
 *  - رفع الصور (من المعرض أو التقاط كاميرا مباشرة) — onShowFileChooser
 *  - طلب الموقع الجغرافي من صفحات الخرائط — onGeolocationPermissionsShowPrompt
 *  - زر الرجوع بأندرويد يرجع بتاريخ التصفّح داخل الموقع، لا يغلق التطبيق فوراً
 *  - سحب-للتحديث (Pull to refresh)
 *  - شاشة خطأ عند انقطاع الإنترنت + زر إعادة المحاولة
 *  - الروابط الخارجية (واتساب، خرائط جوجل، إلخ) تُفتَح بتطبيقها الخاص لا داخل الويب-فيو
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null
    private var geoOrigin: String? = null
    private var geoCallback: GeolocationPermissions.Callback? = null

    // ── طلب صلاحية الموقع الجغرافي من نظام أندرويد نفسه ──
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        geoCallback?.invoke(geoOrigin, granted, false)
        geoOrigin = null
        geoCallback = null
    }

    // ── طلب صلاحية الكاميرا ──
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openFileChooser() else {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    // ── استلام نتيجة اختيار/التقاط الصورة ──
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        var results: Array<Uri>? = null
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data?.dataString != null) {
                // اختيار من المعرض
                results = arrayOf(data.data!!)
            } else if (data?.clipData != null) {
                // اختيار متعدد
                val clip = data.clipData!!
                results = Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
            } else if (cameraImageUri != null) {
                // التقاط كاميرا مباشر
                results = arrayOf(cameraImageUri!!)
            }
        }
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
        cameraImageUri = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupSwipeRefresh()
        setupBackPress()

        if (savedInstanceState == null) {
            binding.webView.loadUrl(BuildConfig.BASE_URL)
        } else {
            binding.webView.restoreState(savedInstanceState)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true          // إلزامي — الموقع يعتمد على localStorage/sessionStorage
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setGeolocationEnabled(true)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        // الكوكيز — لازم تُفعَّل يدوياً لتسجيل الدخول يستمر بين فتحات التطبيق
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webChromeClient = object : WebChromeClient() {

            // رفع الصور (input type="file" بأي صفحة — الشكاوى، الصور الشخصية، إلخ)
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback

                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    openFileChooser()
                }
                return true
            }

            // الموقع الجغرافي (صفحات الخرائط بالحجز)
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    callback?.invoke(origin, true, false)
                } else {
                    geoOrigin = origin
                    geoCallback = callback
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                val scheme = url.scheme ?: ""
                // روابط الموقع نفسه تبقى داخل التطبيق — أي رابط خارجي (واتساب،
                // خرائط، اتصال هاتفي) يُفتَح بتطبيقه الخاص خارج الويب-فيو
                return if (scheme == "http" || scheme == "https") {
                    if (url.host == Uri.parse(BuildConfig.BASE_URL).host) {
                        false // يبقى داخل الـWebView
                    } else {
                        openExternally(url); true
                    }
                } else {
                    openExternally(url); true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.swipeRefresh.isRefreshing = false
                binding.errorView.visibility = View.GONE
                binding.webView.visibility = View.VISIBLE
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    binding.webView.visibility = View.GONE
                    binding.errorView.visibility = View.VISIBLE
                    binding.swipeRefresh.isRefreshing = false
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                // لا تتجاهل أخطاء SSL أبداً — أهمّ سطر أمني بكل هذا الملف
                handler?.cancel()
            }
        }

        binding.retryButton.setOnClickListener {
            binding.errorView.visibility = View.GONE
            webView.reload()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { binding.webView.reload() }
        binding.swipeRefresh.setColorSchemeResources(R.color.brand_gold)
        // فعّل السحب-للتحديث فقط لما تكون الصفحة بأعلى تمرير (تجنّب تعارضه مع تمرير المحتوى الطبيعي)
        binding.webView.viewTreeObserver.addOnScrollChangedListener {
            binding.swipeRefresh.isEnabled = binding.webView.scrollY == 0
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this) {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                finish()
            }
        }
    }

    private fun openFileChooser() {
        val chooserIntent = Intent(Intent.ACTION_CHOOSER)
        val galleryIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }

        var cameraIntent: Intent? = null
        try {
            val imageFile = File.createTempFile(
                "capture_", ".jpg", File(cacheDir, "images").apply { mkdirs() }
            )
            cameraImageUri = FileProvider.getUriForFile(this, "com.hidmaty.app.fileprovider", imageFile)
            cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        } catch (_: Exception) {
            // لو فشل تجهيز ملف الكاميرا (نادر)، يكتفي باختيار من المعرض
        }

        chooserIntent.putExtra(Intent.EXTRA_INTENT, galleryIntent)
        chooserIntent.putExtra(Intent.EXTRA_TITLE, getString(R.string.choose_image))
        if (cameraIntent != null) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
        }
        fileChooserLauncher.launch(chooserIntent)
    }

    private fun openExternally(url: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.no_app_found, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
