package io.ea.documentview.rendering

import android.content.Context
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import io.ea.documentview.AbsDocumentView
import io.ea.pdf.BuildConfig

/**
 * Created by nano on 18-3-22.
 */

abstract class BitmapDocumentView : AbsDocumentView {

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
            bitmapAdapter = value as? BitmapDocumentAdapter
        }

    /** Delegate [adapter] */
    var bitmapAdapter: BitmapDocumentAdapter? = null
        private set

    /**
     * Bitmap pool for adapter, default is a [BitmapDocumentAdapter.BitmapPool]. You may want to share one
     * pool between adapters. But you should notice that you can not change the pool of adapter once
     * document has been loaded, you must specify your own pool before loading.
     */
    var bitmapPool: BitmapDocumentAdapter.BitmapPool = BitmapDocumentAdapter.BitmapPool(
        DEFAULT_GRID_SIZE,
        DEFAULT_GRID_SIZE,
        false, 0.1f)

    /** State listener */
    var stateListener: StateListener? = null

    private var setupWhenGotSize = false

    /**
     * Setup [renderer]
     *
     * [renderingThread] and [renderingHandler] will be initialized when this function get called,
     * and all previous pending rendering tasks will be canceled.
     */
    protected fun setupRenderer() {
        if (renderingThread == null) renderingThread = HandlerThread("Bitmap Renderer").apply { start() }
        if (renderingHandler == null) renderingHandler = RenderingHandler(renderingThread!!.looper)

        renderingHandler?.cancelAllPendingTasks()
        renderer?.release()

        renderer = createRender()
        renderingHandler?.renderer = renderer

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
        renderingHandler ?: return
        renderer ?: return

        val adapter = createAdapter()
        adapter.checkCrop(crop)
        adapter.crop.set(crop)

        adapterConfig.update(width, height, adapter)
        adapter.config = adapterConfig
        adapter.setup()

        this.adapter = adapter
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val adapter = bitmapAdapter ?: return
        if (setupWhenGotSize) {
            setup()
            setupWhenGotSize = false
        } else {
            adapterConfig.update(w, h, adapter)
        }
        super.onSizeChanged(w, h, oldw, oldh)
    }

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
    }
}
