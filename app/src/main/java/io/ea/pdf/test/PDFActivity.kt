package io.ea.pdf.test

import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.TextView
import io.ea.documentview.DocumentView
import io.ea.documentview.pdf.PDFView
import io.ea.documentview.pdf.WritablePDFView
import io.ea.documentview.pdf.source.FileSource
import io.ea.documentview.pdf.source.PDFSource
import java.io.File

/**
 * Created by nano on 17-11-30.
 */
class PDFActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_pdf)
        val decor = window.decorView
        decor.systemUiVisibility = fullScreenFlags

        var showingSide = true
        val side = findViewById(R.id.placeholder)

        val pdf = (findViewById(R.id.pdf) as WritablePDFView).apply {
            pageBackground = resources.getDrawable(R.drawable.bg_page)
            writingColor = Color.RED
            onSingleTab = {
                if (showingSide) side.visibility = View.GONE
                else side.visibility = View.VISIBLE
                showingSide = !showingSide
            }
            scrollListener = object : DocumentView.ScrollListener {

                override fun onScrollStart(view: DocumentView) {
                    Log.i(TAG, "onScrollStart")
                }

                override fun onScroll(view: DocumentView, toX: Int, toY: Int) {
                    Log.i(TAG, "onScroll, to [$toX, $toY]")
                }

                override fun onScrollEnd(view: DocumentView) {
                    Log.i(TAG, "onScrollEnd")
                }
            }
            zoomListener = object : DocumentView.ZoomListener {

                override fun onZoomStart(view: DocumentView) {
                    Log.e(TAG, "onZoomStart")
                }

                override fun onZoom(view: DocumentView, scaleTo: Float, px: Float, py: Float) {
                    Log.i(TAG, "onZoom, to [$scaleTo, $px, $py]")
                }

                override fun onZoomEnd(view: DocumentView) {
                    Log.i(TAG, "onZoomEnd")
                }
            }
            stateListener = object : PDFView.StateListener {

                override fun onLoading(view: PDFView) {
                    Log.i(TAG, "onLoading")
                }

                override fun onLoaded(view: PDFView) {
                    Log.i(TAG, "onLoaded")
                }

                override fun onLoadError(src: PDFSource, view: PDFView, cause: Throwable) {
                    Log.e(TAG, "onLoadError", cause)
                }

                override fun onRenderingError(page: Int, view: PDFView, cause: Throwable) {
                    Log.e(TAG, "onRenderingError", cause)
                }
            }
        }
        pdf.load(FileSource(File("/sdcard/long.pdf")))

        findViewById(R.id.to_first).setOnClickListener { pdf.scrollToPage(0) }
        findViewById(R.id.to_last).setOnClickListener { pdf.scrollToPage(pdf.pageCount - 1) }
        findViewById(R.id.enable_writing).setOnClickListener {
            (it as TextView).text = if (pdf.isWritingEnabled) "enable writing" else "disable writing"
            pdf.isWritingEnabled = !pdf.isWritingEnabled
        }
        findViewById(R.id.retreat_writing).setOnClickListener { pdf.retreatWriting() }
        findViewById(R.id.erase).setOnClickListener {
            (it as TextView).text = if (pdf.isEraseMode) "eraser" else "pen"
            pdf.isEraseMode = !pdf.isEraseMode
        }

        findViewById(R.id.hide_side).setOnClickListener {
            it as TextView
            if (it.isSelected) {
                side.visibility = View.VISIBLE
                it.text = "hide side"
            } else {
                side.visibility = View.GONE
                it.text = "show side"
            }
            it.isSelected = !it.isSelected
        }
    }

    private val fullScreenFlags = View.SYSTEM_UI_FLAG_LOW_PROFILE or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_IMMERSIVE

    companion object {
        const val TAG = "PDFActivity"
    }
}
