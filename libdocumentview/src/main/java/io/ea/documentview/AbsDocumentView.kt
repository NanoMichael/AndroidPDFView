package io.ea.documentview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.NinePatchDrawable
import android.util.AttributeSet
import android.view.MotionEvent

/**
 * Created by nano on 18-3-22.
 */

abstract class AbsDocumentView : DocumentView {

    constructor(context: Context) :
        this(context, null)

    constructor(context: Context, attrs: AttributeSet?) :
        this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    /** MUST BE A [DocumentAdapter], or an [IllegalArgumentException] will be thrown */
    override var adapter: SliceAdapter?
        get() = super.adapter
        set(value) {
            if (value != null && value !is DocumentAdapter)
                throw IllegalArgumentException("Requires a BitmapDocumentAdapter")
            super.adapter = value
            documentAdapter = value as? DocumentAdapter
        }

    private var documentAdapter: DocumentAdapter? = null

    /** Page background, default is `null` */
    var pageBackground: Drawable? = null

    protected var tmpArea = Rect()

    /**
     * Configurator for adapter, default is a [DefaultAdapterConfig] with 24dp page margin when scaled
     * to filling the view width
     */
    var adapterConfig: AdapterConfig = newDefaultAdapterConfig()
        set(value) {
            if (field === value) return
            field = value
            val pdfAdapter = documentAdapter ?: return
            if (width != 0 && height != 0) {
                value.update(width, height, pdfAdapter)
                pdfAdapter.config = value
            }
        }

    /** Whether gestures is enabled, default is `true` */
    var isGesturesEnabled = true

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
        adapterConfig.update(width, height, adapter)

        adapter.recalculate()
        invalidateAll()
    }

    private fun newDefaultAdapterConfig(): AdapterConfig {
        val m = DEFAULT_FULL_WIDTH_PAGE_MARGIN * context.resources.displayMetrics.density
        return DefaultAdapterConfig(m.toInt())
    }

    /** Scroll to [page] with [offset], [smooth] indicates if scroll smoothly, default is `false` */
    fun scrollToPage(page: Int, offset: Int = 0, smooth: Boolean = false) {
        val adapter = documentAdapter ?: return
        val to = adapter.topPositionOf(page) + offset - adapter.currentPageMargin
        stopAllAnimations()
        if (smooth) smoothMoveTo(xOffset, to)
        else moveTo(xOffset, to)
    }

    /** Draw pages background if has any */
    override fun beforeDrawSlices(canvas: Canvas) {
        val bg = pageBackground ?: return
        val adapter = documentAdapter ?: return
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

    override fun onTouchEvent(event: MotionEvent) =
        if (isGesturesEnabled) super.onTouchEvent(event) else false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (documentAdapter != null) adapterConfig.update(w, h, documentAdapter!!)
        super.onSizeChanged(w, h, oldw, oldh)
    }

    companion object {
        const val DEFAULT_FULL_WIDTH_PAGE_MARGIN = 24f // dp
    }
}
