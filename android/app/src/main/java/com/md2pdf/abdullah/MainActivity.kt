package com.md2pdf.abdullah

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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
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
        val sdf = SimpleDateFormat("dd MMMM yyyy hh_mm_ss a", Locale.US)
        val dateStr = sdf.format(Date())
        return "PDF $dateStr.pdf"
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
        if (intent == null) {
            finishAndRemoveTask()
            return
        }

        val action = intent.action
        val type = intent.type

        when (action) {
            Intent.ACTION_SEND -> {
                if (type == "text/plain" && intent.hasExtra(Intent.EXTRA_TEXT)) {
                    sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (sharedText != null) setupUI() else finishAndRemoveTask()
                } else {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    }
                    if (uri != null) readTextFromUri(uri) else finishAndRemoveTask()
                }
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) readTextFromUri(uri) else finishAndRemoveTask()
            }
            else -> finishAndRemoveTask()
        }
    }

    private fun readTextFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val text = inputStream.bufferedReader().use { it.readText() }
                val fileName = getFileName(uri)
                if (fileName?.endsWith(".csv", ignoreCase = true) == true) {
                    sharedText = csvToHtmlTable(text)
                } else {
                    sharedText = text
                }
                setupUI()
            } ?: finishAndRemoveTask()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            finishAndRemoveTask()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (index != -1) name = it.getString(index)
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/') ?: -1
            if (cut != -1) name = name?.substring(cut + 1)
        }
        return name
    }

    private fun csvToHtmlTable(csvText: String): String {
        val rows = csvText.lines().filter { it.isNotBlank() }
        if (rows.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("<table>\n")
        rows.forEachIndexed { index, row ->
            sb.append("  <tr>\n")
            val columns = parseCsvRow(row)
            columns.forEach { col ->
                val tag = if (index == 0) "th" else "td"
                sb.append("    <$tag>${escapeHtml(col)}</$tag>\n")
            }
            sb.append("  </tr>\n")
        }
        sb.append("</table>")
        return sb.toString()
    }

    private fun parseCsvRow(row: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < row.length) {
            val char = row[i]
            if (char == '\"') {
                if (inQuotes && i + 1 < row.length && row[i + 1] == '\"') {
                    current.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (char == ',' && !inQuotes) {
                result.add(current.toString().trim())
                current.setLength(0)
            } else {
                current.append(char)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun setupUI() {
        val fontSizeValue = findViewById<TextView>(R.id.fontSizeValue)
        val btnMinus = findViewById<View>(R.id.btnMinus)
        val btnPlus = findViewById<View>(R.id.btnPlus)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)
        val btnCancelTop = findViewById<ImageButton>(R.id.btnCancelTop)
        val btnInfo = findViewById<ImageButton>(R.id.btnInfo)
        val pageSizeToggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.pageSizeToggleGroup)
        val marginsToggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.marginsToggleGroup)
        val alignmentToggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.alignmentToggleGroup)

        fontSizeValue.text = fontSize.toString()

        btnMinus.setOnClickListener {
            fontSize = maxOf(8, fontSize - 1)
            fontSizeValue.text = fontSize.toString()
            updatePreview()
        }

        btnPlus.setOnClickListener {
            fontSize = minOf(30, fontSize + 1)
            fontSizeValue.text = fontSize.toString()
            updatePreview()
        }

        pageSizeToggleGroup.check(when(pageSize) {
            "A4" -> R.id.btnA4
            "Letter" -> R.id.btnLetter
            "Legal" -> R.id.btnLegal
            else -> R.id.btnA4
        })

        pageSizeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                pageSize = when (checkedId) {
                    R.id.btnA4 -> "A4"
                    R.id.btnLetter -> "Letter"
                    R.id.btnLegal -> "Legal"
                    else -> "A4"
                }
                updatePreview()
            }
        }

        marginsToggleGroup.check(when(margin) {
            "Standard" -> R.id.btnStandard
            "Minimal" -> R.id.btnMinimal
            else -> R.id.btnStandard
        })

        marginsToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                margin = when (checkedId) {
                    R.id.btnStandard -> "Standard"
                    R.id.btnMinimal -> "Minimal"
                    else -> "Standard"
                }
                updatePreview()
            }
        }

        alignmentToggleGroup.check(when(alignment) {
            "Left" -> R.id.btnLeft
            "Center" -> R.id.btnCenter
            "Right" -> R.id.btnRight
            "Justify" -> R.id.btnJustify
            else -> R.id.btnJustify
        })

        alignmentToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                alignment = when (checkedId) {
                    R.id.btnLeft -> "Left"
                    R.id.btnCenter -> "Center"
                    R.id.btnRight -> "Right"
                    R.id.btnJustify -> "Justify"
                    else -> "Justify"
                }
                updatePreview()
            }
        }

        updatePreview()

        btnSave.setOnClickListener {
            if (!isProcessing) {
                handleSave()
            }
        }

        btnCancelTop.setOnClickListener {
            finishAndRemoveTask()
        }

        btnInfo?.setOnClickListener {
            showCreditsDialog()
        }

        findViewById<View>(R.id.background_dim)?.setOnClickListener {
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


    private fun updatePreview() {
        val text = sharedText ?: ""
        val html = markdownToHtml(text, pageSize, margin, fontSize, alignment, isPreview = true)
        val previewWebView = findViewById<WebView>(R.id.previewWebView)
        previewWebView?.settings?.allowFileAccess = true
        previewWebView?.settings?.javaScriptEnabled = true
        previewWebView?.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
    }

    private fun updateLoadingState(loading: Boolean) {
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)
        if (loading) {
            btnSave.isEnabled = false
            btnSave.text = getString(R.string.processing)
        } else {
            btnSave.isEnabled = true
            btnSave.text = getString(R.string.save_button)
        }
    }

    private fun savePdfInBackground(html: String, onComplete: () -> Unit) {
        val fileName = getFormattedFileName()
        val converter = PdfConverter(this, fontSize, pageSize, margin, alignment)

        converter.convert(html, cacheDir, fileName, object : PdfConverter.Callback {
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
    }


    private fun showCreditsDialog() {
        val message = "Developed by Abdullah Bari\n" +
                "Portfolio: abdullh.ami.bd\n" +
                "Call: 01738745285, 01538310838 (WhatsApp)"

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Credits")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
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


    private fun markdownToHtml(text: String, pSize: String, mType: String, fSize: Int, textAlign: String, isPreview: Boolean = false): String {
        val extensions = listOf(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            TaskListItemsExtension.create(),
            AutolinkExtension.create(),
            HeadingAnchorExtension.create(),
            FootnotesExtension.create()
        )
        val parser = Parser.builder().extensions(extensions).build()
        val document = parser.parse(text)
        val renderer = HtmlRenderer.builder().extensions(extensions).build()
        val content = renderer.render(document)

        val marginMap = mapOf(
            "Standard" to "20mm",
            "Minimal" to "10mm"
        )

        val sizeMap = mapOf(
            "A4" to "A4",
            "Letter" to "letter",
            "Legal" to "legal"
        )

        val selectedSize = sizeMap[pSize] ?: "A4"
        val selectedMargin = marginMap[mType] ?: "20mm"

        val pageHeight = when(pSize) {
            "Letter" -> "279.4mm"
            "Legal" -> "355.6mm"
            else -> "297mm"
        }
        val pageWidth = when(pSize) {
            "Letter" -> "215.9mm"
            "Legal" -> "215.9mm"
            else -> "210mm"
        }

        // 96 DPI: 1mm = 3.7795px
        val viewportWidth = when(pSize) {
            "Letter", "Legal" -> 816
            else -> 794
        }

        val viewportContent = "width=$viewportWidth"

        val previewStyles = if (isPreview) """
            html {
                background-color: #f0f0f0;
                display: flex;
                justify-content: center;
            }
            body {
                width: $pageWidth !important;
                margin: 20px 0 !important;
                padding: $selectedMargin !important;
                box-sizing: border-box !important;
                background-color: white;
                box-shadow: 0 0 10px rgba(0,0,0,0.1);
                background-image: linear-gradient(to bottom, transparent calc($pageHeight - 2px), #ddd calc($pageHeight - 2px), #ddd $pageHeight, transparent $pageHeight);
                background-size: 100% $pageHeight;
                min-height: $pageHeight;
            }
        """.trimIndent() else """
            body {
                width: 100% !important;
                margin: 0 !important;
                padding: 0 !important;
                box-sizing: border-box !important;
            }
        """.trimIndent()

        return """
            <!DOCTYPE html>
            <html>
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="$viewportContent">
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
                <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
                <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js" onload="renderMathInElement(document.body);"></script>
                <style>
                  @font-face {
                    font-family: 'Bornomala';
                    src: url('fonts/Bornomala-Regular.ttf');
                  }
                  @page {
                    size: $selectedSize;
                    margin: ${if (isPreview) "0" else selectedMargin};
                  }
                  body {
                    font-size: ${fSize}pt;
                    font-family: 'Bornomala', sans-serif;
                    line-height: 1.6;
                    color: #1a1a1a;
                    background-color: white;
                    margin: ${if (isPreview) selectedMargin else "0"};
                    text-align: ${textAlign.lowercase()};
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                  }
                  $previewStyles
                  h1, h2, h3, h4, h5, h6 {
                    color: #333;
                    margin-top: 1.5em;
                    margin-bottom: 0.5em;
                    page-break-after: avoid;
                    page-break-inside: avoid;
                  }
                  img { max-width: 100%; height: auto; page-break-inside: avoid; display: block; margin: 1em auto; }
                  pre {
                    background: #f6f8fa;
                    padding: 16px;
                    border-radius: 8px;
                    overflow-x: auto;
                    white-space: pre-wrap;
                    word-wrap: break-word;
                    border: 1px solid #dfe1e4;
                    page-break-inside: avoid;
                  }
                  code {
                    font-family: 'Courier New', Courier, monospace;
                    background: #f6f8fa;
                    padding: 0.2em 0.4em;
                    border-radius: 3px;
                    font-size: 90%;
                  }
                  pre code {
                    background: transparent;
                    padding: 0;
                  }
                  blockquote {
                    border-left: 4px solid #d0d7de;
                    padding: 0 1em;
                    color: #57606a;
                    margin: 0 0 16px 0;
                    page-break-inside: avoid;
                  }
                  table {
                    border-collapse: collapse;
                    width: 100%;
                    margin-bottom: 16px;
                    page-break-inside: avoid;
                  }
                  th, td {
                    border: 1px solid #d0d7de;
                    padding: 6px 13px;
                  }
                  tr:nth-child(even) { background-color: #f6f8fa; }
                  th {
                    background-color: #f6f8fa;
                    font-weight: 600;
                  }
                  hr {
                    height: 0.25em;
                    padding: 0;
                    margin: 24px 0;
                    background-color: #d0d7de;
                    border: 0;
                  }
                  a { color: #0969da; text-decoration: none; }
                  a:hover { text-decoration: underline; }
                  .task-list-item { list-style-type: none; }
                  .task-list-item input { margin-right: 0.5em; }
                  .footnotes { font-size: 80%; color: #57606a; border-top: 1px solid #d0d7de; margin-top: 40px; }
                  .katex-display { page-break-inside: avoid; }
                </style>
              </head>
              <body>
                $content
              </body>
            </html>
        """.trimIndent()
    }
}
