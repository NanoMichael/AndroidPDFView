package io.ea.documentview.image

import android.content.Context
import android.os.HandlerThread
import android.util.AttributeSet
import io.ea.documentview.DocumentView
import io.ea.documentview.rendering.BitmapDocumentAdapter
import io.ea.documentview.rendering.RenderingHandler

/**
 * Created by nano on 18-3-21.
 */

open class HugeImageView : DocumentView {

    constructor(context: Context) :
        this(context, null)

    constructor(context: Context, attrs: AttributeSet?) :
        this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    private var renderingThread: HandlerThread? = null

    var renderingHandler: RenderingHandler? = null
        private set

    var renderer: ImageRenderer? = null
        private set

    /** MUST BE A [BitmapDocumentAdapter], or a [IllegalArgumentException] will be thrown */
    override var adapter: SliceAdapter?
        get() = super.adapter
        set(value) {
            if (value != null && value !is BitmapDocumentAdapter)
                throw IllegalArgumentException("Requires a BitmapDocumentAdapter")
            super.adapter = value
            imageAdapter = value as? BitmapDocumentAdapter
        }

    var imageAdapter: BitmapDocumentAdapter? = null
        private set

    fun load(src: ImageSource) {
        if (renderingHandler == null) renderingThread = HandlerThread("Image Renderer").apply { start() }
        if (renderingHandler == null) renderingHandler = RenderingHandler(renderingThread!!.looper)

        renderingHandler?.cancelAllPendingTasks()
        renderer?.release()
    }
}