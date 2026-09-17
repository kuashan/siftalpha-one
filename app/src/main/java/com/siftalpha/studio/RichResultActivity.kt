package com.siftalpha.studio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.siftalpha.studio.runtime.RichResultItem
import com.siftalpha.studio.runtime.RichResultUrlPolicy

/** Native, text-only viewer for a one-shot CLI Rich Result. */
class RichResultActivity : StudioActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val labels = intent.getStringArrayListExtra(EXTRA_LABELS).orEmpty()
        val urls = intent.getStringArrayListExtra(EXTRA_URLS).orEmpty()
        val items = labels.zip(urls)
            .mapNotNull { (label, url) ->
                val safeUrl = RichResultUrlPolicy.validate(url) ?: return@mapNotNull null
                label.trim().takeIf { it.isNotBlank() }?.let {
                    RichResultItem(label = it, url = safeUrl)
                }
            }

        if (items.isEmpty()) {
            Toast.makeText(this, R.string.runtime_rich_result_empty, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(buildUi(items))
    }

    private fun buildUi(items: List<RichResultItem>): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(16, 19, 24))
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = getString(R.string.runtime_rich_result_title)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.runtime_rich_result_count, items.size)
            textSize = 14f
            setTextColor(Color.rgb(170, 204, 235))
            setPadding(0, dp(6), 0, dp(10))
        })
        root.addView(Button(this).apply {
            text = getString(R.string.runtime_rich_result_copy)
            textSize = 13f
            isAllCaps = false
            setOnClickListener { copyResult(items) }
        }, fullWidthParams(top = 0, bottom = 10))

        items.forEach { item ->
            root.addView(resultCard(item), fullWidthParams(top = 0, bottom = 10))
        }

        return scroll
    }

    private fun resultCard(item: RichResultItem): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(24, 28, 35))
                cornerRadius = dp(9).toFloat()
                setStroke(dp(1), Color.rgb(48, 54, 64))
            }
        }
        card.addView(TextView(this).apply {
            text = item.label
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        card.addView(TextView(this).apply {
            text = item.url
            textSize = 13f
            setTextColor(Color.rgb(170, 204, 235))
            setTextIsSelectable(true)
            setHorizontallyScrolling(false)
            setPadding(0, dp(6), 0, dp(8))
        })
        card.addView(Button(this).apply {
            text = getString(R.string.runtime_rich_result_open)
            textSize = 13f
            isAllCaps = false
            setOnClickListener { openUrl(item.url) }
        })
        return card
    }

    private fun copyResult(items: List<RichResultItem>) {
        val text = items.joinToString("\n\n") { item ->
            item.label + "\n" + item.url
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SiftAlpha Rich Result", text))
        Toast.makeText(this, R.string.runtime_rich_result_copied, Toast.LENGTH_SHORT).show()
    }

    private fun openUrl(rawUrl: String) {
        val validatedUrl = RichResultUrlPolicy.validate(rawUrl)
        if (validatedUrl == null) {
            Toast.makeText(this, R.string.runtime_rich_result_invalid_url, Toast.LENGTH_LONG).show()
            return
        }

        val uri = Uri.parse(validatedUrl)
        val target = StudioBrowser.selectedTarget(this, uri)
        if (target == null) {
            val browsers = StudioBrowser.discoverInstalled(this, uri)
            if (browsers.isEmpty()) {
                Toast.makeText(this, R.string.runtime_rich_result_no_browser, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, R.string.runtime_rich_result_browser_required, Toast.LENGTH_LONG).show()
                runCatching { startActivity(Intent(this, SettingsActivity::class.java)) }
            }
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(target.packageName)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, R.string.runtime_rich_result_browser_failed, Toast.LENGTH_LONG).show()
            }
    }

    private fun fullWidthParams(top: Int, bottom: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PROJECT_NAME = "rich_result_project_name"
        const val EXTRA_LABELS = "rich_result_labels"
        const val EXTRA_URLS = "rich_result_urls"
    }
}
