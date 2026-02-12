package com.mdtopdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.text.HtmlCompat
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File
import java.io.FileOutputStream

class PdfConverter(private val context: Context, private val fontSize: Int, private val pageSize: String, private val margin: String, private val textAlign: String) {

    interface Callback {
        fun onSuccess(file: File)
        fun onFailure(error: String)
    }

    private val marginPx: Int
        get() = when (margin) {
            "Minimal" -> 38 // ~10mm
            "None" -> 0
            else -> 75 // ~20mm
        }

    private val pageWidth: Int
        get() = when (pageSize) {
            "Letter" -> 612
            "Legal" -> 612
            else -> 595 // A4
        }

    private val pageHeight: Int
        get() = when (pageSize) {
            "Letter" -> 792
            "Legal" -> 1008
            else -> 842 // A4
        }

    fun convert(text: String, path: File, fileName: String, callback: Callback) {
        try {
            val parser = Parser.builder().build()
            val node = parser.parse(text)
            val htmlRenderer = HtmlRenderer.builder().build()
            val html = htmlRenderer.render(node)

            val document = PdfDocument()
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            textPaint.textSize = fontSize.toFloat() * context.resources.displayMetrics.scaledDensity / 1.33f // Adjust for PDF points
            textPaint.color = Color.BLACK

            try {
                val typeface = Typeface.createFromAsset(context.assets, "fonts/Bornomala-Regular.ttf")
                textPaint.typeface = typeface
            } catch (e: Exception) {
                // Fallback to default
            }

            val contentWidth = pageWidth - 2 * marginPx
            if (contentWidth <= 0) {
                callback.onFailure("Margin too large for page size")
                return
            }

            val spannable = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)

            val alignment = when (textAlign) {
                "Center" -> Layout.Alignment.ALIGN_CENTER
                "Right" -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }

            val builder = StaticLayout.Builder.obtain(spannable, 0, spannable.length, textPaint, contentWidth)
                .setAlignment(alignment)
                .setLineSpacing(0f, 1.4f)
                .setIncludePad(false)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && textAlign == "Justify") {
                builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_CHARACTER)
            }

            val staticLayout = builder.build()

            var currentLine = 0
            val totalLines = staticLayout.lineCount
            var pageNumber = 1
            val maxContentHeight = pageHeight - 2 * marginPx

            while (currentLine < totalLines) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                canvas.save()
                canvas.translate(marginPx.toFloat(), marginPx.toFloat())

                val startLineTop = staticLayout.getLineTop(currentLine)
                canvas.translate(0f, -startLineTop.toFloat())

                // Clip the canvas to the current page's content area (in un-translated coordinates)
                // Actually easier to clip in translated coordinates
                canvas.clipRect(0f, startLineTop.toFloat(), contentWidth.toFloat(), (startLineTop + maxContentHeight).toFloat())

                staticLayout.draw(canvas)
                canvas.restore()

                // Calculate how many lines fit on this page
                val startLine = currentLine
                while (currentLine < totalLines && staticLayout.getLineBottom(currentLine) - staticLayout.getLineTop(startLine) <= maxContentHeight) {
                    currentLine++
                }

                if (currentLine == startLine) currentLine++ // Force move if one line is too tall

                document.finishPage(page)
                pageNumber++
            }

            val file = File(path, fileName)
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            document.close()
            callback.onSuccess(file)

        } catch (e: Exception) {
            callback.onFailure("Error: ${e.message}")
        }
    }
}
