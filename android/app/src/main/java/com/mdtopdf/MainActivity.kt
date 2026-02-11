package com.mdtopdf

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

class MainActivity : AppCompatActivity() {

    private var sharedText: String? = null
    private var pageSize = "A4"
    private var margin = "Standard"
    private var fontSize = 12
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                showSettingsDialog()
            }
        }
    }

    private fun showSettingsDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        dialog.setContentView(view)

        val fontSizeLabel = view.findViewById<TextView>(R.id.fontSizeLabel)
        val btnMinus = view.findViewById<Button>(R.id.btnMinus)
        val btnPlus = view.findViewById<Button>(R.id.btnPlus)
        val btnSave = view.findViewById<Button>(R.id.btnSave)
        val btnPrint = view.findViewById<Button>(R.id.btnPrint)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val pageSizeGroup = view.findViewById<RadioGroup>(R.id.pageSizeGroup)
        val marginsGroup = view.findViewById<RadioGroup>(R.id.marginsGroup)

        fontSizeLabel.text = getString(R.string.font_size_label, fontSize)

        btnMinus.setOnClickListener {
            fontSize = maxOf(8, fontSize - 1)
            fontSizeLabel.text = getString(R.string.font_size_label, fontSize)
        }

        btnPlus.setOnClickListener {
            fontSize = minOf(30, fontSize + 1)
            fontSizeLabel.text = getString(R.string.font_size_label, fontSize)
        }

        pageSizeGroup.setOnCheckedChangeListener { _, checkedId ->
            pageSize = when (checkedId) {
                R.id.radioA4 -> "A4"
                R.id.radioLetter -> "Letter"
                R.id.radioLegal -> "Legal"
                else -> "A4"
            }
        }

        marginsGroup.setOnCheckedChangeListener { _, checkedId ->
            margin = when (checkedId) {
                R.id.radioStandard -> "Standard"
                R.id.radioMinimal -> "Minimal"
                R.id.radioNone -> "None"
                else -> "Standard"
            }
        }

        btnSave.setOnClickListener {
            if (!isProcessing) {
                handleSave(dialog)
            }
        }

        btnPrint.setOnClickListener {
            if (!isProcessing) {
                handlePrint(dialog)
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
            sharedText = null
        }

        dialog.setOnDismissListener {
            if (!isProcessing) {
                sharedText = null
            }
        }

        dialog.show()
    }

    private fun handleSave(dialog: BottomSheetDialog) {
        val text = sharedText ?: return
        val html = markdownToHtml(text, pageSize, margin, fontSize)
        dialog.dismiss()
        isProcessing = true
        // Both buttons now use the system print/save dialog which is more reliable and native
        doPrint(html, "Save")
    }

    private fun handlePrint(dialog: BottomSheetDialog) {
        val text = sharedText ?: return
        val html = markdownToHtml(text, pageSize, margin, fontSize)
        dialog.dismiss()
        isProcessing = true
        doPrint(html, "Print")
    }

    private fun doPrint(html: String, title: String) {
        val webView = WebView(this)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "MD_Export_${System.currentTimeMillis()}"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)

                val attributes = PrintAttributes.Builder()
                    .setMediaSize(when(pageSize) {
                        "Letter" -> PrintAttributes.MediaSize.NA_LETTER
                        "Legal" -> PrintAttributes.MediaSize.NA_LEGAL
                        else -> PrintAttributes.MediaSize.ISO_A4
                    })
                    .build()

                printManager.print(jobName, printAdapter, attributes)
                isProcessing = false
                sharedText = null
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun markdownToHtml(text: String, pSize: String, mType: String, fSize: Int): String {
        val parser = Parser.builder().build()
        val document = parser.parse(text)
        val renderer = HtmlRenderer.builder().build()
        val content = renderer.render(document)

        val marginMap = mapOf(
            "Standard" to "20mm",
            "Minimal" to "10mm",
            "None" to "0mm"
        )

        val sizeMap = mapOf(
            "A4" to "A4",
            "Letter" to "letter",
            "Legal" to "legal"
        )

        val selectedSize = sizeMap[pSize] ?: "A4"
        val selectedMargin = marginMap[mType] ?: "20mm"

        return """
            <!DOCTYPE html>
            <html>
              <head>
                <meta charset="utf-8">
                <style>
                  @page {
                    size: $selectedSize;
                    margin: $selectedMargin;
                  }
                  body {
                    font-size: ${fSize}pt;
                    font-family: sans-serif;
                    line-height: 1.5;
                    color: #333;
                  }
                  img { max-width: 100%; height: auto; }
                  pre {
                    background: #f4f4f4;
                    padding: 10px;
                    border-radius: 5px;
                    overflow-x: auto;
                    white-space: pre-wrap;
                    word-wrap: break-word;
                  }
                  code {
                    font-family: monospace;
                    background: #f4f4f4;
                    padding: 2px 4px;
                    border-radius: 3px;
                  }
                  blockquote {
                    border-left: 4px solid #ddd;
                    padding-left: 15px;
                    color: #777;
                    font-style: italic;
                  }
                  table {
                    border-collapse: collapse;
                    width: 100%;
                    margin-bottom: 20px;
                  }
                  th, td {
                    border: 1px solid #ddd;
                    padding: 8px;
                    text-align: left;
                  }
                  th {
                    background-color: #f2f2f2;
                  }
                </style>
              </head>
              <body>
                $content
              </body>
            </html>
        """.trimIndent()
    }
}
