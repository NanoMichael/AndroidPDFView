package io.ea.documentview.rendering

import android.content.Context
import android.graphics.Rect
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import io.ea.documentview.AdapterConfig
import io.ea.documentview.DefaultAdapterConfig
import io.ea.documentview.DocumentView
import io.ea.pdf.BuildConfig

/**
 * Created by nano on 18-3-22.
 */

abstract class BitmapDocumentView : DocumentView {

    constructor(context: Context) :
        this(context, null)

    constructor(context: Context, attrs: AttributeSet?) :
        this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    /** Thread to handle render actions */
    private var renderingThread: HandlerThread? = null

    /** Rendering handler to render page async */
    var renderingHandler: RenderingHandler? = null

    var renderer: Renderer? = null
        private set

    /** MUST BE A [BitmapDocumentAdapter], or an [IllegalArgumentException] will be thrown */
    override var adapter: SliceAdapter?
        get() = super.adapter
        set(value) {
            if (value != null && value !is BitmapDocumentAdapter)
                throw IllegalArgumentException("Requires a BitmapDocumentAdapter")
            super.adapter = value
            documentAdapter = value as? BitmapDocumentAdapter
        }

    /** Delegate [adapter] */
    var documentAdapter: BitmapDocumentAdapter? = null
        private set

    /**
     * Configurator for adapter, default is a [DefaultAdapterConfig] with 24dp page margin when scaled
     * to filling the view width
     */
    var adapterConfig: AdapterConfig? = null
        set(value) {
            if (field === value) return
            field = value
            val pdfAdapter = documentAdapter ?: return
            if (value != null && width != 0 && height != 0) {
                value.update(width, height, pdfAdapter)
                pdfAdapter.config = value
            }
        }

    /**
     * Bitmap pool for adapter, default is a [BitmapDocumentAdapter.BitmapPool]. You may want to share one
     * pool between adapters. But you should notice that you can not change the pool of adapter once
     * document has been loaded, you must specify your own pool before loading.
     */
    var bitmapPool: BitmapDocumentAdapter.BitmapPool? = null

    /** State listener */
    var stateListener: StateListener? = null

    /** If use best quality bitmap, default is `false` */
    var bestQuality: Boolean = false

    /** Whether gestures is enabled, default is `true` */
    var isGestureEnabled = true

    /** First visible page in document, return -1 if no document opened */
    val firstVisiblePage get() = documentAdapter?.pageOf(firstVisibleRow) ?: -1

    /** Last visible page in document, return -1 if no document opened */
    val lastVisiblePage get() = documentAdapter?.pageOf(lastVisibleRow) ?: -1

    /** Total page count of document, return 0 if no document opened */
    val pageCount get() = documentAdapter?.pageCount ?: 0

    /**
     * Crop of pages, default is empty, changes on returned value has no side effects
     *
     * Notice that the crop actually represents an insets
     */
    var crop: Rect
        set(value) {
            if (internalCrop == value) return
            onCropChange(value)
        }
        get() = Rect().apply { set(internalCrop) }

    private val internalCrop = Rect()

    private var setupWhenGotSize = false

    /**
     * Setup [renderer]
     *
     * [renderingThread] and [renderingHandler] will be initialized when this function get called,
     * and all pending rendering tasks will be canceled.
     */
    protected fun setupRenderer() {
        if (renderingThread == null) renderingThread = HandlerThread("Bitmap Renderer").apply { start() }
        if (renderingHandler == null) renderingHandler = RenderingHandler(renderingThread!!.looper)

        renderingHandler?.cancelAllPendingTasks()
        renderer?.release()

        renderer = createRender()
        stateListener?.onLoading(this)
        renderingHandler?.post {
            val success = renderer?.open {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to open document", it)
                this@BitmapDocumentView.post { stateListener?.onLoadError(this, it) }
            } ?: false
            if (success) this@BitmapDocumentView.post { onLoaded() }
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

        if (bitmapPool == null) bitmapPool = BitmapDocumentAdapter.BitmapPool(
            DEFAULT_GRID_SIZE,
            DEFAULT_GRID_SIZE,
            bestQuality, 0.1f)

        val adapter = createAdapter()
        adapter.checkCrop(crop)
        adapter.crop.set(crop)

        val config = if (adapterConfig == null) {
            val m = DEFAULT_FULL_WIDTH_PAGE_MARGIN * context.resources.displayMetrics.density
            adapterConfig = DefaultAdapterConfig(m.toInt())
            adapterConfig!!
        } else adapterConfig!!

        config.update(width, height, adapter)
        adapter.config = config
        adapter.setup()

        this.adapter = adapter
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val adapter = documentAdapter ?: return
        if (setupWhenGotSize) {
            setup()
            setupWhenGotSize = false
        } else {
            adapterConfig?.update(w, h, adapter)
        }
        super.onSizeChanged(w, h, oldw, oldh)
    }

    private fun onCropChange(crop: Rect) {
        if (documentAdapter == null) {
            internalCrop.set(crop)
            return
        }

        val adapter = documentAdapter!!
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
        val adapter = documentAdapter ?: return
        val to = adapter.topPositionOf(page) + offset - adapter.currentPageMargin
        stopAllAnimations()
        if (smooth) smoothMoveTo(xOffset, to)
        else moveTo(xOffset, to)
    }

    override fun onTouchEvent(event: MotionEvent) =
        if (isGestureEnabled) super.onTouchEvent(event) else false

    override fun onDetachedFromWindow() {
        renderingThread?.quit()
        renderer?.release()
        super.onDetachedFromWindow()
    }

    /**
     * Create an [Renderer]
     *
     * [renderingThread], [renderingHandler] were initialized when this function get called
     */
    abstract fun createRender(): Renderer

    /**
     * Create a [BitmapDocumentAdapter]
     *
     * [renderingThread], [renderingHandler], [renderer], [bitmapPool] were initialized when this
     * function get called
     */
    abstract fun createAdapter(): BitmapDocumentAdapter

    /** Listener to listen document states */
    interface StateListener {

        fun onLoading(view: BitmapDocumentView)

        fun onLoaded(view: BitmapDocumentView)

        fun onLoadError(view: BitmapDocumentView, cause: Throwable)

        fun onRenderingError(page: Int, view: BitmapDocumentView, cause: Throwable)
    }

    companion object {
        const val TAG = "BitmapDocumentView"
        const val DEFAULT_GRID_SIZE = 320 // px
        const val DEFAULT_FULL_WIDTH_PAGE_MARGIN = 24f // dp
    }
}