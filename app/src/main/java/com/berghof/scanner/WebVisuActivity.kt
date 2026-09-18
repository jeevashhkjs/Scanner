package com.berghof.scanner

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.berghof.scanner.databinding.ActivityWebvisuBinding
import org.json.JSONObject

/**
 * Hosts the Berghof WebVisu web page in a WebView. After scanning, the value
 * is injected straight into the configured input field via JavaScript - this
 * is more reliable than a system paste because it works even though a
 * WebView's internal DOM elements are not always exposed as standard
 * Android Accessibility nodes.
 */
class WebVisuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebvisuBinding
    private lateinit var prefs: SharedPreferences

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val value = result.data?.getStringExtra(MainActivity.EXTRA_SCANNED_VALUE)
            if (!value.isNullOrEmpty()) fillField(value)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebvisuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("webvisu_prefs", MODE_PRIVATE)
        binding.etUrl.setText(prefs.getString("url", ""))
        binding.etSelector.setText(prefs.getString("selector", ""))

        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.domStorageEnabled = true
        binding.webView.webViewClient = WebViewClient()

        binding.btnLoad.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            val selector = binding.etSelector.text.toString().trim()
            if (url.isEmpty()) {
                toast("Enter the WebVisu page URL first")
                return@setOnClickListener
            }
            prefs.edit().putString("url", url).putString("selector", selector).apply()
            binding.webView.loadUrl(url)
        }

        binding.fabScan.setOnClickListener {
            if (binding.etSelector.text.toString().trim().isEmpty()) {
                toast("Enter the CSS selector of the target field first")
                return@setOnClickListener
            }
            scanLauncher.launch(
                Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCAN_FOR_RESULT, true)
            )
        }

        // If we arrived here with a value already scanned from the main screen, offer to fill immediately.
        intent.getStringExtra(EXTRA_SCANNED_VALUE)?.let { value ->
            if (binding.etSelector.text.toString().trim().isNotEmpty()) fillField(value)
        }
    }

    private fun fillField(value: String) {
        val selector = binding.etSelector.text.toString().trim()
        if (selector.isEmpty()) {
            toast("No target field selector set")
            return
        }
        // JSON-encode the value so quotes/newlines in the scanned data can't break the JS.
        val safeValue = JSONObject.quote(value)
        val js = """
            (function() {
                var el = document.querySelector('$selector');
                if (!el) { return 'not_found'; }
                el.focus();
                el.value = $safeValue;
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return 'ok';
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(js) { resultJson ->
            val ok = resultJson?.contains("ok") == true
            toast(if (ok) "Field filled" else "Could not find that field on the page")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_SCANNED_VALUE = "scanned_value"
    }
}
