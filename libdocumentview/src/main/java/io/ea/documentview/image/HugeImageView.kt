package io.ea.documentview.image

import android.content.Context
import android.util.AttributeSet
import io.ea.documentview.Size
import io.ea.documentview.rendering.BitmapDocumentAdapter
import io.ea.documentview.rendering.BitmapDocumentView
import io.ea.documentview.rendering.Renderer

/**
 * Created by nano on 18-3-21.
 */

open class HugeImageView : BitmapDocumentView {

    constructor(context: Context) :
        this(context, null)

    constructor(context: Context, attrs: AttributeSet?) :
        this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    var imageRenderer: ImageRenderer? = null

    private var src: ImageSource? = null

    override fun createRender(): Renderer {
        val src = src ?: throw IllegalStateException("src was not initialized")
        imageRenderer = ImageRenderer(src, bitmapPool.bestQuality, Size(bitmapPool.width, bitmapPool.height))
        return imageRenderer!!
    }

    override fun createAdapter(): BitmapDocumentAdapter {
        val renderer = imageRenderer ?: throw IllegalStateException("renderer was not initialized")
        val handler = renderingHandler ?: throw IllegalStateException("renderingHandler was not initialized")
        return BitmapDocumentAdapter(this, renderer.pagesSize, bitmapPool, handler) { page, cause ->
            stateListener?.onRenderingError(page, this, cause)
        }
    }

    fun load(src: ImageSource) {
        this.src = src
        setupRenderer()
    }
}
