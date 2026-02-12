package com.mdtopdf

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import android.print.PrintAttributes
import android.print.PrintManager
import android.print.PageRange
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var sharedText: String? = null
    private var pageSize = "A4"
    private var margin = "Standard"
    private var alignment = "Justify"
    private var fontSize = 12
    private var isProcessing = false

    private fun getFormattedFileName(): String {
        val sdf = SimpleDateFormat("dd-MM-yyyy_hh:mma", Locale.US)
        val dateStr = sdf.format(Date()).lowercase()
        return "PDF_$dateStr.pdf"
    }

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
                setupUI()
            } else {
                finishAndRemoveTask()
            }
        } else {
            finishAndRemoveTask()
        }
    }

    private fun setupUI() {
        val fontSizeLabel = findViewById<TextView>(R.id.fontSizeLabel)
        val btnMinus = findViewById<View>(R.id.btnMinus)
        val btnPlus = findViewById<View>(R.id.btnPlus)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)
        val btnPrint = findViewById<MaterialButton>(R.id.btnPrint)
        val btnCancelTop = findViewById<ImageButton>(R.id.btnCancelTop)
        val pageSizeGroup = findViewById<RadioGroup>(R.id.pageSizeGroup)
        val marginsGroup = findViewById<RadioGroup>(R.id.marginsGroup)
        val alignmentGroup = findViewById<RadioGroup>(R.id.alignmentGroup)

        fontSizeLabel.text = getString(R.string.font_size_label, fontSize)

        btnMinus.setOnClickListener {
            fontSize = maxOf(8, fontSize - 1)
            fontSizeLabel.text = getString(R.string.font_size_label, fontSize)
            updatePreview()
        }

        btnPlus.setOnClickListener {
            fontSize = minOf(30, fontSize + 1)
            fontSizeLabel.text = getString(R.string.font_size_label, fontSize)
            updatePreview()
        }

        pageSizeGroup.setOnCheckedChangeListener { _, checkedId ->
            pageSize = when (checkedId) {
                R.id.radioA4 -> "A4"
                R.id.radioLetter -> "Letter"
                R.id.radioLegal -> "Legal"
                else -> "A4"
            }
            updatePreview()
        }

        marginsGroup.setOnCheckedChangeListener { _, checkedId ->
            margin = when (checkedId) {
                R.id.radioStandard -> "Standard"
                R.id.radioMinimal -> "Minimal"
                R.id.radioNone -> "None"
                else -> "Standard"
            }
            updatePreview()
        }

        alignmentGroup.setOnCheckedChangeListener { _, checkedId ->
            alignment = when (checkedId) {
                R.id.radioLeft -> "Left"
                R.id.radioCenter -> "Center"
                R.id.radioRight -> "Right"
                R.id.radioJustify -> "Justify"
                else -> "Justify"
            }
            updatePreview()
        }

        updatePreview()

        btnSave.setOnClickListener {
            if (!isProcessing) {
                handleSave()
            }
        }

        btnPrint.setOnClickListener {
            if (!isProcessing) {
                handlePrint()
            }
        }

        btnCancelTop.setOnClickListener {
            finishAndRemoveTask()
        }

        findViewById<View>(R.id.activity_main_root)?.setOnClickListener {
            finishAndRemoveTask()
        }
        findViewById<View>(R.id.cardView)?.setOnClickListener {
            // Consume
        }
    }

    private fun handleSave() {
        val text = sharedText ?: return
        val html = markdownToHtml(text, pageSize, margin, fontSize, alignment)
        isProcessing = true
        updateLoadingState(true)
        savePdfInBackground(html) {
            finishAndRemoveTask()
        }
    }

    private fun handlePrint() {
        val text = sharedText ?: return
        val html = markdownToHtml(text, pageSize, margin, fontSize, alignment)
        isProcessing = true
        updateLoadingState(true)
        savePdfInBackground(html) {
            doPrint(html)
        }
    }

    private fun updatePreview() {
        val text = sharedText ?: ""
        val html = markdownToHtml(text, pageSize, margin, fontSize, alignment, isPreview = true)
        val previewWebView = findViewById<WebView>(R.id.previewWebView)
        previewWebView?.settings?.allowFileAccess = true
        previewWebView?.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
    }

    private fun updateLoadingState(loading: Boolean) {
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)
        val btnPrint = findViewById<MaterialButton>(R.id.btnPrint)
        if (loading) {
            btnSave.isEnabled = false
            btnPrint.isEnabled = false
            btnSave.text = getString(R.string.processing)
        } else {
            btnSave.isEnabled = true
            btnPrint.isEnabled = true
            btnSave.text = getString(R.string.save_button)
        }
    }

    private fun savePdfInBackground(html: String, onComplete: () -> Unit) {
        val fileName = getFormattedFileName()
        val converter = PdfConverter(this, fontSize, pageSize, margin, alignment)

        // We can run this in a background thread for even better performance
        Thread {
            converter.convert(sharedText ?: "", cacheDir, fileName, object : PdfConverter.Callback {
                override fun onSuccess(file: File) {
                    val finalUri = saveToDownloads(file, fileName)
                    runOnUiThread {
                        if (finalUri != null) {
                            Toast.makeText(this@MainActivity, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                        }
                        onComplete()
                    }
                }

                override fun onFailure(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                        updateLoadingState(false)
                        isProcessing = false
                    }
                }
            })
        }.start()
    }


    private fun saveToDownloads(file: File, fileName: String): Uri? {
        val resolver = contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        FileInputStream(file).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    uri
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            } else null
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, fileName)
            try {
                FileInputStream(file).use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Uri.fromFile(destFile)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun doPrint(html: String) {
        val webView = WebView(this)
        webView.settings.allowFileAccess = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val fileName = getFormattedFileName()
                val jobName = fileName.replace(".pdf", "")
                val printAdapter = webView.createPrintDocumentAdapter(jobName)

                val attributes = PrintAttributes.Builder()
                    .setMediaSize(when(pageSize) {
                        "Letter" -> PrintAttributes.MediaSize.NA_LETTER
                        "Legal" -> PrintAttributes.MediaSize.NA_LEGAL
                        else -> PrintAttributes.MediaSize.ISO_A4
                    })
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()

                printManager.print(jobName, printAdapter, attributes)
                isProcessing = false
                finishAndRemoveTask()
            }
        }
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
    }

    private fun markdownToHtml(text: String, pSize: String, mType: String, fSize: Int, textAlign: String, isPreview: Boolean = false): String {
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

        val previewStyles = if (isPreview) """
            body {
                width: 100%;
                min-height: 100vh;
            }
        """.trimIndent() else ""

        return """
            <!DOCTYPE html>
            <html>
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                  @font-face {
                    font-family: 'Bornomala';
                    src: url('fonts/Bornomala-Regular.ttf');
                  }
                  @page {
                    size: $selectedSize;
                    margin: $selectedMargin;
                  }
                  body {
                    font-size: ${fSize}pt;
                    font-family: 'Bornomala', sans-serif;
                    line-height: 1.6;
                    color: #1a1a1a;
                    background-color: white;
                    margin: $selectedMargin;
                    text-align: ${textAlign.lowercase()};
                  }
                  $previewStyles
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
