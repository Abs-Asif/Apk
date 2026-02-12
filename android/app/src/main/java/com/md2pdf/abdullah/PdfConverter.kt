package com.md2pdf.abdullah

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PrintAdapterHelper
import android.print.PrintAttributes
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

class PdfConverter(private val context: Context, private val fontSize: Int, private val pageSize: String, private val margin: String, private val textAlign: String) {

    interface Callback {
        fun onSuccess(file: File)
        fun onFailure(error: String)
    }

    fun convert(html: String, path: File, fileName: String, callback: Callback) {
        // WebView must be created on the main thread
        Handler(Looper.getMainLooper()).post {
            val webView = WebView(context)
            webView.settings.allowFileAccess = true
            webView.settings.javaScriptEnabled = true

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Give some time for KaTeX/MathJax to render
                    Handler(Looper.getMainLooper()).postDelayed({
                        startPrint(webView, fileName, path, callback)
                    }, 1500)
                }
            }
            webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
        }
    }

    private fun startPrint(webView: WebView, fileName: String, path: File, callback: Callback) {
        val jobName = fileName.replace(".pdf", "")
        val printAdapter = webView.createPrintDocumentAdapter(jobName)

        val attributes = PrintAttributes.Builder()
            .setMediaSize(when (pageSize) {
                "Letter" -> PrintAttributes.MediaSize.NA_LETTER
                "Legal" -> PrintAttributes.MediaSize.NA_LEGAL
                else -> PrintAttributes.MediaSize.ISO_A4
            })
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        val file = File(path, fileName)
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)

            PrintAdapterHelper.drawPdf(printAdapter, attributes, pfd, {
                try { pfd.close() } catch (e: Exception) {}
                callback.onSuccess(file)
            }, {
                try { pfd.close() } catch (e: Exception) {}
                callback.onFailure("Failed to generate PDF")
            })
        } catch (e: Exception) {
            callback.onFailure("Error: ${e.message}")
        }
    }
}
