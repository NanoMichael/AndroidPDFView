package io.ea.documentview.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.NinePatchDrawable
import android.net.Uri
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import com.shockwave.pdfium.PdfDocument
import io.ea.documentview.AdapterConfig
import io.ea.documentview.DefaultAdapterConfig
import io.ea.documentview.DocumentView
import io.ea.documentview.Size
import io.ea.documentview.pdf.source.PDFSource
import io.ea.pdf.BuildConfig

/**
 * Created by nano on 17-11-30.
 */
open class PDFView : DocumentView {

    constructor(context: Context) :
        this(context, null)

    constructor(context: Context, attrs: AttributeSet?) :
        this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    private var renderingThread: HandlerThread? = null
    private var renderingHandler: RenderingHandler? = null
    private var renderer: PDFRenderer? = null

    /** MUST BE A [PDFDocumentAdapter], otherwise a [IllegalArgumentException] will be throw */
    override var adapter: SliceAdapter?
        get() = super.adapter
        set(value) {
            if (value != null && value !is PDFDocumentAdapter)
                throw IllegalArgumentException("Requires a PDFDocumentAdapter")
            super.adapter = value
            pdfAdapter = value as PDFDocumentAdapter?
        }

    /** Delegate [adapter] */
    var pdfAdapter: PDFDocumentAdapter? = null
        private set

    /**
     * Configurator for adapter, default is a [DefaultAdapterConfig] with 24dp page margin when scaled
     * to filling the view width
     */
    var adapterConfig: AdapterConfig? = null

    /** Bitmap pool for adapter, default is a [PDFDocumentAdapter.BitmapPool] */
    var bitmapPool: PDFDocumentAdapter.BitmapPool? = null

    /** Drawable to show press */
    var pressingDrawable: Drawable = ColorDrawable(Color.argb(112, 0, 0, 255))

    /** Page background, default is `null` */
    var pageBackground: Drawable? = null

    /** On link click event handler, default is [handleLinkClick] */
    var onLinkClick: (PdfDocument.Link) -> Unit = this::handleLinkClick

    /** On single tab callback */
    var onSingleTab: (PDFView) -> Unit = {}

    /** State listener */
    var stateListener: StateListener? = null

    /**
     * Create a new [PDFDocumentAdapter], default is a [PDFDocumentAdapter] with `320 * 320` grid size
     *
     * FIXME ugly way to expose create new adapter
     */
    var newAdapter: (
        PDFView,
        List<Size>,
        PDFDocumentAdapter.BitmapPool,
        RenderingHandler,
        (Int, Throwable) -> Unit) -> PDFDocumentAdapter = { view, sizes, pool, handler, callback ->
        PDFDocumentAdapter(view, sizes, pool, handler, callback)
    }

    /** Whether rendering annotation, default is `false` */
    var isRenderAnnotation = false
        set(value) {
            if (field != value) {
                renderer?.isRenderAnnotation = value
                invalidateVisible()
            }
            field = value
        }

    /** Whether gestures is enabled, default is `true` */
    var isGesturesEnabled = true

    /** First visible page in document, return -1 if no document opened */
    val firstVisiblePage get() = pdfAdapter?.pageOf(firstVisibleRow) ?: -1

    /** Last visible page in document, return -1 if no document opened */
    val lastVisiblePage get() = pdfAdapter?.pageOf(lastVisibleRow) ?: -1

    /** Get page count of document, return 0 if no document opened */
    val pageCount get() = renderer?.pageCount ?: 0

    /** Meta data of document, return null if no document opened */
    val metaData get() = if (renderer?.isOpened == true) renderer?.metaData else null

    /** Table of contents of document, return null if no document opened */
    val tableOfContents get() = if (renderer?.isOpened == true) renderer?.tableOfContents else null

    /** Crop of pages, default is empty */
    var crop: Rect
        set(value) {
            if (internalCrop == value) return
            onCropChange(value)
        }
        get() = internalCrop

    private val internalCrop = Rect()

    private var setupWhenGotSize = false
    private val pressedArea = Rect()
    private val tmpArea = Rect()

    /** Load PDF document from [src] */
    fun load(src: PDFSource) {
        if (renderingThread == null) renderingThread = HandlerThread("PDF Renderer").apply { start() }
        if (renderingHandler == null) renderingHandler = RenderingHandler(renderingThread!!.looper)

        renderingHandler?.cancelAllPendingTasks()
        renderer?.release()

        renderer = PDFRenderer(context, src).apply { isRenderAnnotation = this@PDFView.isRenderAnnotation }
        stateListener?.onLoading(this)
        renderingHandler?.post {
            val success = renderer?.open {
                if (BuildConfig.DEBUG) Log.e(TAG, "error when open $src", it)
                this@PDFView.post { stateListener?.onLoadError(src, this@PDFView, it) }
            } ?: false
            if (success) this@PDFView.post { onLoaded() }
        }
    }

    private fun onLoaded() {
        stateListener?.onLoaded(this)
        if (width != 0 && height != 0) setup()
        else setupWhenGotSize = true
    }

    private fun setup() {
        val renderer = renderer ?: return
        val renderingHandler = renderingHandler ?: return

        renderingHandler.renderer = renderer
        val sizes = renderer.pagesSize

        val pool = if (bitmapPool == null) {
            bitmapPool = PDFDocumentAdapter.BitmapPool(DEFAULT_GRID_SIZE, DEFAULT_GRID_SIZE, false, 0.1f)
            bitmapPool!!
        } else bitmapPool!!

        val pdfAdapter = newAdapter(this, sizes, pool, renderingHandler) { page, cause ->
            stateListener?.onRenderingError(page, this, cause)
        }
        pdfAdapter.checkCrop(crop)
        pdfAdapter.crop.set(crop)

        val config = if (adapterConfig == null) {
            val m = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                DEFAULT_FULL_WIDTH_PAGE_MARGIN,
                context.resources.displayMetrics)
            adapterConfig = DefaultAdapterConfig(m.toInt())
            adapterConfig!!
        } else adapterConfig!!

        config.update(width, height, pdfAdapter)
        pdfAdapter.config = config
        pdfAdapter.setup()

        adapter = pdfAdapter
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val adapter = pdfAdapter ?: return
        if (setupWhenGotSize) {
            setup()
            setupWhenGotSize = false
        } else {
            adapterConfig?.update(w, h, adapter)
        }
        super.onSizeChanged(w, h, oldw, oldh)
    }

    private fun onCropChange(crop: Rect) {
        if (pdfAdapter == null) {
            internalCrop.set(crop)
            return
        }

        val adapter = pdfAdapter!!
        if (crop == adapter.crop) return

        adapter.checkCrop(crop)
        internalCrop.set(crop)

        adapter.crop.set(crop)
        adapterConfig?.update(width, height, adapter)

        adapter.recalculate()
        invalidateAll()
    }

    /** Scroll to [page] with [offset], [smooth] indicates if scroll smoothly, default is `false` */
    fun scrollToPage(page: Int, offset: Int = 0, smooth: Boolean = false) {
        val adapter = pdfAdapter ?: return
        val to = adapter.topPositionOf(page) + offset + adapter.currentPageMargin
        stopAllAnimations()
        if (smooth) smoothMoveTo(xOffset, to)
        else moveTo(xOffset, to)
    }

    /** Search link in position ([x], [y]), return false if not found */
    private fun searchLink(x: Int, y: Int, onFound: (PdfDocument.Link, Rect) -> Unit): Boolean {
        val renderer = renderer ?: return false
        val adapter = pdfAdapter ?: return false
        if (!renderer.isOpened) return false

        if (firstVisiblePage < 0 || lastVisiblePage < 0) return false

        for (i in firstVisiblePage..lastVisiblePage) {
            renderer.getPageLinks(i).forEach {
                val b = it.bounds
                val lt = renderer.getScaledPageCoords(i, adapter.rawScale, b.left, b.top)
                val rb = renderer.getScaledPageCoords(i, adapter.rawScale, b.right, b.bottom)
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
            scrollToPage(link.destPageIdx, 0, true)
        } else if (uri != null && uri.isNotEmpty()) {
            if (BuildConfig.DEBUG) Log.i(TAG, "got uri: $uri")
            val raw = Uri.parse(uri)
            val intent = Intent(Intent.ACTION_VIEW, raw)
            if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
            else if (BuildConfig.DEBUG) Log.w(TAG, "No activity found to open $uri")
        }
    }

    /** Draw pages background if has any */
    override fun beforeDrawSlices(canvas: Canvas) {
        val bg = pageBackground ?: return
        val adapter = pdfAdapter ?: return
        if (firstVisiblePage < 0 || lastVisiblePage < 0) return
        tmpArea.setEmpty()
        (bg as? NinePatchDrawable)?.getPadding(tmpArea)
        for (i in firstVisiblePage..lastVisiblePage) {
            val l = adapter.leftPositionOf(i)
            val t = adapter.topPositionOf(i)
            val r = l + adapter.widthOf(i)
            val b = t + adapter.heightOf(i)
            bg.setBounds(l - tmpArea.left, t - tmpArea.top, r + tmpArea.right, b + tmpArea.bottom)
            bg.draw(canvas)
        }
    }

    /** Draw pressed area if has any */
    override fun afterDrawSlices(canvas: Canvas) {
        if (pressedArea.isEmpty) return
        pressingDrawable.bounds.set(pressedArea)
        pressingDrawable.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent) =
        if (isGesturesEnabled) super.onTouchEvent(event) else false

    override fun onDetachedFromWindow() {
        renderingThread?.quit()
        renderer?.release()
        super.onDetachedFromWindow()
    }

    interface StateListener {

        fun onLoading(view: PDFView)

        fun onLoaded(view: PDFView)

        fun onLoadError(src: PDFSource, view: PDFView, cause: Throwable)

        fun onRenderingError(page: Int, view: PDFView, cause: Throwable)
    }

    companion object {
        const val TAG = "PDFView"
        const val DEFAULT_FULL_WIDTH_PAGE_MARGIN = 24f // dp
        const val DEFAULT_GRID_SIZE = 320 // px
    }
}
