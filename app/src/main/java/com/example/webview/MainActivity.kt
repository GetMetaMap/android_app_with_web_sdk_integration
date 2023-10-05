package com.example.webview

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private val MAX_PROGRESS = 100
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var permissionCallback: (() -> Unit)? = null

    /**
     * WebView file path result listener
     */
    private var filePathCallback: ValueCallback<Array<Uri?>?>? = null


    /**
     * Result listener for gallery image pick
     */
    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val value = if (result.resultCode == Activity.RESULT_OK) {
                WebChromeClient.FileChooserParams.parseResult(
                    Activity.RESULT_OK,
                    result.data
                )
            } else {
                null
            }
            filePathCallback?.onReceiveValue(value)
            filePathCallback = null
        }


    /**
     * Result listener for camera photos
     */
    private var latestTmpUri: Uri? = null
    private val takePhotoResultLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            val result = if (isSuccess) {
                arrayOf(latestTmpUri)
            } else {
                null
            }
            filePathCallback?.onReceiveValue(result)
            filePathCallback = null
        }


    /**
     * Handler for Permissions
     */
    private val permissionsResult = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val missingPermissions = ArrayList<String>()
        permissions.entries.forEach {
            if (!it.value) {
                missingPermissions.add(it.key)
            }
        }
        if (missingPermissions.isEmpty()) {
            permissionCallback?.invoke()
        } else {
            for (i in missingPermissions.indices) {
                val permission = missingPermissions[i]
                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        permission
                    )
                ) {
                    onPermissionPermanentlyDenied(permission)
                    return@registerForActivityResult
                }
            }
            onPermissionDenied(missingPermissions.toTypedArray())
        }
    }


    private val webChromeClient = object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            val permissions = mutableSetOf<String>()
            request.resources.forEach {
                if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                    permissions.add(Manifest.permission.CAMERA)
                }
                if (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                    permissions.add(Manifest.permission.RECORD_AUDIO)
                }
            }
            checkPermissionAndMakeAction(*permissions.toTypedArray()) {
                request.grant(request.resources)
            }
        }

        override fun onShowFileChooser(
            mWebView: WebView?,
            filePathCallback: ValueCallback<Array<Uri?>?>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            assignFilePathCallback(filePathCallback)
            return tryFetchFile(fileChooserParams.isCaptureEnabled, fileChooserParams.acceptTypes)

        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback?
        ) {
            checkPermissionAndMakeAction(Manifest.permission.ACCESS_FINE_LOCATION) {
                callback!!.invoke(origin, true, true)
            }
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            trackProgress(newProgress)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        initWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = false
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.proceed()
            }
        }
        webView.webChromeClient = webChromeClient
        webView.loadUrl("file:///android_asset/metamap.html")

    }

    private fun assignFilePathCallback(filePathCallback: ValueCallback<Array<Uri?>?>) {
        if (this.filePathCallback != null) {
            this.filePathCallback?.onReceiveValue(null)
            this.filePathCallback = null
        }
        this.filePathCallback = filePathCallback
    }

    private fun tryFetchFile(fromCamera: Boolean, mimeTypes: Array<String>) = try {
        if (fromCamera) {
            openCamera()
        } else {
            openPicker(mimeTypes)
        }
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private fun openCamera() {
        latestTmpUri = createTempFileUri()
        takePhotoResultLauncher.launch(latestTmpUri)
    }

    private fun openPicker(mimeTypes: Array<String>) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        resultLauncher.launch(intent)
    }

    private fun trackProgress(newProgress: Int) {
        progressBar.progress = newProgress
        if (newProgress < MAX_PROGRESS && progressBar.visibility == ProgressBar.GONE) {
            progressBar.visibility = ProgressBar.VISIBLE
        }
        if (newProgress == MAX_PROGRESS) {
            progressBar.visibility = ProgressBar.GONE
        }
    }

    private fun onPermissionDenied(permission: Array<String>) {
        showPermissionsRationale(permission)
    }

    private fun showPermissionsRationale(permission: Array<String>) {
        AlertDialog.Builder(this@MainActivity)
            .setMessage("Permission required for app: ${permission.joinToString()}")
            .setNeutralButton(
                android.R.string.ok
            ) { _, i ->
                permissionsResult.launch(permission)
            }
            .show()
    }

    private fun onPermissionPermanentlyDenied(permission: String) {
        Toast.makeText(this, "Permission permanently denied: $permission", Toast.LENGTH_SHORT)
            .show()
    }

    fun checkPermissionAndMakeAction(vararg requiredPermission: String, action: () -> Unit) {
        val missingPermissions = ArrayList<String>()
        requiredPermission.forEach {
            if (!hasPermission(it)) missingPermissions.add(it)
        }
        if (missingPermissions.isEmpty()) {
            action()
        } else {
            permissionCallback = action
            permissionsResult.launch(missingPermissions.toTypedArray())
        }
    }

    private fun Context.hasPermission(requiredPermission: String) =
        ActivityCompat.checkSelfPermission(
            this,
            requiredPermission
        ) == PackageManager.PERMISSION_GRANTED

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}