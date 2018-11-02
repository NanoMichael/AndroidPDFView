package io.ea.documentview.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import com.shockwave.pdfium.PdfDocument
import io.ea.documentview.Size
import io.ea.documentview.rendering.BitmapDocumentAdapter
import io.ea.documentview.rendering.BitmapDocumentView
import io.ea.documentview.rendering.Renderer
import io.ea.documentview.rendering.RenderingHandler
import io.ea.pdf.BuildConfig

/**
 * Created by nano on 17-11-30.
 */
open class PDFView : BitmapDocumentView {

    constructor(context: Context) :
        this(context, null)

    constructor(context: Context, attrs: AttributeSet?) :
        this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    var pdfRenderer: PDFRenderer? = null

    /** Drawable to show press */
    var pressingDrawable: Drawable = ColorDrawable(Color.argb(112, 0, 0, 255))

    /** On link click event handler, default is [handleLinkClick] */
    var onLinkClick: (PdfDocument.Link) -> Unit = {
        // Manually invoke to avoid annoying preview problem
        handleLinkClick(it)
    }

    /** On single tab callback */
    var onSingleTab: (PDFView) -> Unit = {}

    /**
     * Create a new [BitmapDocumentAdapter], default is a [BitmapDocumentAdapter] with `320 * 320` grid size
     *
     * FIXME Ugly way to expose create new adapter
     */
    var newAdapter: (
        PDFView,
        List<Size>,
        BitmapDocumentAdapter.BitmapPool,
        RenderingHandler,
        (Int, Throwable) -> Unit) -> BitmapDocumentAdapter = { view, sizes, pool, handler, callback ->
        BitmapDocumentAdapter(view, sizes, pool, handler, callback)
    }

    /** Whether rendering annotation, default is `false` */
    var isRenderAnnotation = false
        set(value) {
            if (field != value) {
                pdfRenderer?.isRenderAnnotation = value
                invalidateVisible()
            }
            field = value
        }

    /** Meta data of document, return null if no document opened */
    val metaData get() = if (pdfRenderer?.isOpened == true) pdfRenderer?.metaData else null

    /** Table of contents of document, return null if no document opened */
    val tableOfContents get() = if (pdfRenderer?.isOpened == true) pdfRenderer?.tableOfContents else null

    private var src: PDFSource? = null

    /** Get page thumbnail with [scale] */
    fun getPageThumbnail(outBitmap: Bitmap, page: Int, scale: Float, left: Int = 0, top: Int = 0) {
        val pdfRenderer = pdfRenderer ?: return
        pdfRenderer.renderPageClip(outBitmap, page, left, top, scale) { p, cause ->
            stateListener?.onRenderingError(p, this, cause)
        }
    }

    private val pressedArea = Rect()

    override fun createRender(): Renderer {
        val src = src ?: throw IllegalStateException("src was not initialized")
        pdfRenderer = PDFRenderer(context, src).apply {
            isRenderAnnotation = this@PDFView.isRenderAnnotation
        }
        return pdfRenderer!!
    }

    override fun createAdapter(): BitmapDocumentAdapter {
        val renderer = pdfRenderer ?: throw IllegalStateException("renderer was not initialized")
        val handler = renderingHandler ?: throw IllegalStateException("renderingHandler was not initialized")
        return newAdapter(this, renderer.pagesSize, bitmapPool, handler) { page, cause ->
            stateListener?.onRenderingError(page, this, cause)
        }
    }

    /** Load PDF document from [src] */
    fun load(src: PDFSource) {
        this.src = src
        setupRenderer()
    }

    /** Search link in position ([x], [y]), return false if not found */
    private fun searchLink(x: Int, y: Int, onFound: (PdfDocument.Link, Rect) -> Unit): Boolean {
        val pdfRenderer = pdfRenderer ?: return false
        val adapter = bitmapAdapter ?: return false
        val handler = renderingHandler ?: return false
        if (!pdfRenderer.isOpened) return false

        if (firstVisiblePage < 0 || lastVisiblePage < 0) return false

        for (i in firstVisiblePage..lastVisiblePage) {
            val links = pdfRenderer.peekPageLinks(i)
            if (links == null) {
                handler.post { pdfRenderer.getPageLinks(i) }
                continue
            }
            links.forEach {
                val b = it.bounds
                val lt = pdfRenderer.getScaledPageCoords(i, adapter.rawScale, b.left, b.top)
                val rb = pdfRenderer.getScaledPageCoords(i, adapter.rawScale, b.right, b.bottom)
                tmpArea.set(lt.x, lt.y, rb.x, rb.y)
                adapter.pageAreaToDocArea(i, tmpArea)
                if (tmpArea.contains(x, y)) {
                    onFound(it, tmpArea)
                    return true
                }
            }
        }
        return false
    }

    override fun onPressed(x: Int, y: Int) {
        searchLink(x, y) { _, area -> pressedArea.set(area); invalidate() }
    }

    override fun onCancelPress() {
        if (!pressedArea.isEmpty) {
            pressedArea.setEmpty()
            invalidate()
        }
    }

    override fun onClicked(x: Int, y: Int) {
        val inLink = searchLink(x, y) { link, _ -> onLinkClick(link) }
        if (!inLink) onSingleTab(this)
    }

    private fun handleLinkClick(link: PdfDocument.Link) {
        val uri = link.uri
        if (link.destPageIdx != null) {
            if (BuildConfig.DEBUG) Log.i(TAG, "jump to ${link.destPageIdx}")
            scrollToPage(link.destPageIdx)
        } else if (uri != null && uri.isNotEmpty()) {
            if (BuildConfig.DEBUG) Log.i(TAG, "got uri: $uri")
            val raw = Uri.parse(uri)
            val intent = Intent(Intent.ACTION_VIEW, raw)
            if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
            else if (BuildConfig.DEBUG) Log.w(TAG, "No activity found to open $uri")
        }
    }

    /** Draw pressed area if has any */
    override fun afterDrawSlices(canvas: Canvas) {
        if (pressedArea.isEmpty) return
        pressingDrawable.bounds.set(pressedArea)
        pressingDrawable.draw(canvas)
    }

    companion object {
        const val TAG = "PDFView"
    }
}
