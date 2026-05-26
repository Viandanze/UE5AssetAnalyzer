package com.example.ue5analyzer.ui.screens

import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.BufferedReader
import java.io.InputStreamReader

private const val TAG = "ObjPreview"

/**
 * Bridge class for passing OBJ data from Kotlin to JavaScript.
 */
class ObjDataBridge {
    @Volatile
    private var objData: String = ""

    fun setData(data: String) {
        objData = data
    }

    @JavascriptInterface
    fun getObjData(): String {
        return objData
    }
}

/**
 * OBJ 3D Preview Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjPreviewScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var objBridge by remember { mutableStateOf<ObjDataBridge?>(null) }
    var fileName by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Tap the button to import an OBJ file") }
    var isPageLoaded by remember { mutableStateOf(false) }
    var pendingObjFile by remember { mutableStateOf<String?>(null) }

    fun loadObjIntoWebView(objText: String) {
        objBridge?.setData(objText)
        if (isPageLoaded && objBridge != null) {
            webView?.evaluateJavascript(
                "try { loadObjFromBridge(); } catch(e) { console.error('bridge error:', e); }",
                null
            )
            statusMessage = "Rendering..."
        } else {
            pendingObjFile = objText
            statusMessage = "Waiting for 3D engine..."
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                statusMessage = "Reading file..."
                fileName = it.lastPathSegment?.split("/")?.last() ?: "model.obj"
                val objText = readObjFile(context, it)
                loadObjIntoWebView(objText)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load file", e)
                statusMessage = "Error: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (fileName.isNotEmpty()) fileName else "3D Model Preview") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    filePickerLauncher.launch(arrayOf("*/*"))
                }
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = "Import OBJ")
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                factory = { ctx ->
                    val bridge = ObjDataBridge()
                    objBridge = bridge

                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowFileAccessFromFileURLs = true
                        settings.allowUniversalAccessFromFileURLs = true

                        addJavascriptInterface(bridge, "AndroidBridge")

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageLoaded = true
                                pendingObjFile?.let { data ->
                                    loadObjIntoWebView(data)
                                    pendingObjFile = null
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                Log.d(
                                    TAG,
                                    "JS [${consoleMessage.messageLevel()}]: ${consoleMessage.message()}"
                                )
                                return true
                            }
                        }

                        loadUrl("file:///android_asset/obj_viewer.html")
                        webView = this
                    }
                },
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private const val MAX_OBJ_SIZE = 10 * 1024 * 1024

private fun readObjFile(context: android.content.Context, uri: Uri): String {
    val inputStream = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Cannot open file")
    val reader = BufferedReader(InputStreamReader(inputStream))
    val sb = StringBuilder()
    var totalSize = 0
    reader.use {
        var line: String? = it.readLine()
        while (line != null) {
            totalSize += line.length
            if (totalSize > MAX_OBJ_SIZE) {
                throw IllegalArgumentException("OBJ file too large (max 10MB)")
            }
            sb.appendLine(line)
            line = it.readLine()
        }
    }
    return sb.toString()
}