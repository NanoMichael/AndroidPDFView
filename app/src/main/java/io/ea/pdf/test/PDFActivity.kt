package io.ea.pdf.test

import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import io.ea.documentview.DefaultAdapterConfig
import io.ea.documentview.DocumentView
import io.ea.documentview.pdf.FileSource
import io.ea.documentview.pdf.WritablePDFView
import io.ea.documentview.rendering.BitmapDocumentView
import java.io.File

/**
 * Created by nano on 17-11-30.
 */
class PDFActivity : AppCompatActivity() {

    private lateinit var pdf: WritablePDFView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_pdf)

        pdf = findViewById<WritablePDFView>(R.id.pdf).apply {
            adapterConfig = DefaultAdapterConfig(18)
            pageBackground = resources.getDrawable(R.drawable.bg_page)
            writingColor = Color.RED
            onSingleTab = { Log.i(TAG, "onSingleTab") }
            scrollListener = object : DocumentView.ScrollListener {

                override fun onScrolled(view: DocumentView, dx: Int, dy: Int) {
                    Log.i(TAG, "onScrolled, [$dx, $dy]")
                }

                override fun onScrollSateChanged(view: DocumentView, oldState: Int, newState: Int) {
                    Log.i(TAG, "onScrollStateChanged, $oldState -> $newState")
                }
            }
            zoomListener = object : DocumentView.ZoomListener {

                override fun onZoomStart(view: DocumentView) {
                    val elements = Thread.currentThread().stackTrace
                    elements.take(10).forEachIndexed { i, e ->
                        Log.d(TAG, "|" + "-".repeat(i) + " $e")
                    }
                    Log.d(TAG, "onZoomStart")
                }

                override fun onZoomed(view: DocumentView, deltaScale: Float, px: Float, py: Float) {
                    Log.i(TAG, "onZoomed, to [$deltaScale, $px, $py]")
                }

                override fun onZoomEnd(view: DocumentView) {
                    val elements = Thread.currentThread().stackTrace
                    elements.take(10).forEachIndexed { i, e ->
                        Log.e(TAG, "|" + "-".repeat(i) + " $e")
                    }
                    Log.e(TAG, "onZoomEnd")
                }
            }
            stateListener = object : BitmapDocumentView.StateListener {

                override fun onLoading(view: BitmapDocumentView) {
                    Log.i(TAG, "onLoading")
                }

                override fun onLoaded(view: BitmapDocumentView) {
                    Log.i(TAG, "onLoaded")
                }

                override fun onLoadError(view: BitmapDocumentView, cause: Throwable) {
                    Log.e(TAG, "onLoadError", cause)
                }

                override fun onRenderingError(page: Int, view: BitmapDocumentView, cause: Throwable) {
                    Log.e(TAG, "onRenderingError", cause)
                }
            }
        }
        pdf.load(FileSource(File("/sdcard/test_pdf.pdf")))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.pdf, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.to_first -> pdf.scrollToPage(0)
            R.id.to_last -> pdf.scrollToPage(pdf.pageCount - 1)
            R.id.enable_writing -> {
                item.title = if (pdf.isWritingEnabled) "ENABLE WRITING" else "DISABLE WRITING"
                pdf.isWritingEnabled = !pdf.isWritingEnabled
            }
            R.id.retreat_writing -> pdf.retreatWriting()
            R.id.erase -> {
                item.title = if (pdf.isEraseMode) "ERASER" else "PEN"
                pdf.isEraseMode = !pdf.isEraseMode
            }
            R.id.show_side -> {
                findViewById<View>(R.id.side).apply {
                    visibility = if (visibility == View.GONE) View.VISIBLE else View.GONE
                    item.title = if (visibility == View.GONE) "SHOW SIDE" else "HIDE SIDE"
                }
            }
            R.id.crop -> {
                pdf.crop = if (pdf.crop.left == 0) Rect(200, 200, 200, 200) else Rect()
                item.title = if (pdf.crop.left == 0) "CROP" else "CANCEL CROP"
            }
        }
        return true
    }

    companion object {
        const val TAG = "PDFActivity"
    }
}
