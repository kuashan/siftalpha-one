package com.siftalpha.studio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.siftalpha.studio.runtime.ResultWebHost

class ResultWebActivity : StudioActivity() {

    private lateinit var webView: WebView
    private lateinit var localUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val resultId = intent.getStringExtra(EXTRA_RESULT_ID).orEmpty()
        if (resultId.isBlank()) {
            finish()
            return
        }

        val url = runCatching { ResultWebHost.urlFor(this, resultId) }.getOrElse {
            Toast.makeText(this, R.string.runtime_result_web_load_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        localUri = Uri.parse(url)
        setContentView(buildUi(url))
        webView.loadUrl(url)
    }

    private fun buildUi(url: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 18, 23))
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        actions.addView(button(getString(R.string.runtime_result_web_back)) { finish() }, weight())
        actions.addView(
            button(getString(R.string.runtime_result_web_copy_link)) { copyLink(url) },
            weight().apply { marginStart = dp(6) },
        )
        actions.addView(
            button(getString(R.string.runtime_result_web_external)) { openExternal(localUri) },
            weight().apply { marginStart = dp(6) },
        )
        root.addView(
            actions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        root.addView(TextView(this).apply {
            text = url
            textSize = 11.5f
            setTextColor(Color.rgb(151, 177, 199))
            setTextIsSelectable(true)
            setPadding(dp(3), dp(7), dp(3), dp(7))
        })

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(15, 18, 23))
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val target = request?.url ?: return true
                    if (
                        target.scheme == "http" &&
                        target.host == localUri.host &&
                        target.port == localUri.port
                    ) {
                        return false
                    }
                    if (target.scheme == "http" || target.scheme == "https") {
                        openExternal(target)
                    }
                    return true
                }
            }
        }
        root.addView(
            webView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        return root
    }

    private fun copyLink(url: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SiftAlpha local result", url))
        Toast.makeText(this, R.string.runtime_result_web_link_copied, Toast.LENGTH_SHORT).show()
    }

    private fun openExternal(uri: Uri) {
        val target = StudioBrowser.selectedTarget(this, uri)
        if (target == null) {
            val browsers = StudioBrowser.discoverInstalled(this, uri)
            if (browsers.isEmpty()) {
                Toast.makeText(this, R.string.runtime_rich_result_no_browser, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, R.string.runtime_rich_result_browser_required, Toast.LENGTH_LONG).show()
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            return
        }

        val external = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(target.packageName)
        }
        runCatching { startActivity(external) }
            .onFailure {
                Toast.makeText(this, R.string.runtime_rich_result_browser_failed, Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webViewClient = null
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 11.5f
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener { action() }
        }

    private fun weight(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_RESULT_ID = "result_web_id"
        const val EXTRA_PROJECT_NAME = "result_web_project_name"
    }
}
