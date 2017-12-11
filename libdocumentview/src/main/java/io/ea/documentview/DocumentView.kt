package io.ea.documentview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.support.annotation.CallSuper
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Created by nano on 17-11-23.
 *
 * To rendering big document. It does not care about how the document rendered, but just do the create
 * and recycle work through [SliceManager], and handle gestures through [DragScaleDetector].
 * A document can be a very large bitmap or something can be cut into parts and render.
 */
open class DocumentView : View {

    constructor(context: Context) :
        this(context, null)

    constructor(context: Context, attrs: AttributeSet?) :
        this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    private val sliceManager = SliceManager(this)
    private val animator = DocumentAnimator(context, this)
    private val gestureDetector = DragScaleDetector(this, animator)

    open var adapter: SliceAdapter? = null
        @CallSuper
        set(value) {
            if (value === field) return
            field = value
            sliceManager.adapter = field
            if (width != 0 && height != 0) {
                checkOffsetBoundary()
                sliceManager.populate()
                ViewCompat.postInvalidateOnAnimation(this)
            }
        }

    /** Current scroll state, see [ScrollListener] */
    var scrollState = SCROLL_STATE_IDLE
        internal set(value) {
            if (field == value) return
            val old = field
            field = value
            scrollListener?.onScrollSateChanged(this, old, value)
        }

    /** Callback to listen scroll events, default is `null` */
    var scrollListener: ScrollListener? = null

    /** Callback to listen zoom events, default is `null` */
    var zoomListener: ZoomListener? = null

    /** Temporary scale, when zoom end, this value will return to 1 */
    var scale = 1f
        private set(value) {
            if (field == value) return
            field = value
            checkOffsetBoundary()
        }

    /** If document is zooming */
    var isZooming = false
        private set

    /** Current minimal scale of the document */
    val minScale get() = adapter?.minScale ?: 1f

    /** Current middle scale of the document */
    val midScale get() = adapter?.midScale ?: 1f

    /** Current maximal scale of the document */
    val maxScale get() = adapter?.maxScale ?: 1f

    /** First visible row in document */
    val firstVisibleRow get() = sliceManager.firstVisibleRow

    /** Last visible row in document */
    val lastVisibleRow get() = sliceManager.lastVisibleRow

    /**
     * Minimal offset in horizontal direction
     *
     * If document width greater than view width, the value is 0, else the value may smaller than 0
     */
    var minXOffset = 0
        private set
    /** Maximal offset in horizontal direction */
    var maxXOffset = 0
        private set

    /**
     * Minimal offset in vertical direction
     *
     * If document height greater than view height, the value is 0, else the value may smaller than 0
     */
    var minYOffset = 0
        private set
    /** Maximal offset in vertical direction*/
    var maxYOffset = 0
        private set

    /** Current offset in horizontal direction */
    var xOffset = 0
        private set
    /** Current offset in vertical direction */
    var yOffset = 0
        private set

    /** Width outside of the view bounds with current [scale] to preload */
    val offScreenWidth get() = ((adapter?.offScreenWidth ?: 0) * scale + 0.5f).toInt()

    /** Height outside of the view bounds with current [scale] to preload */
    val offScreenHeight get() = ((adapter?.offScreenHeight ?: 0) * scale + 0.5f).toInt()

    /** Current left edge of visible window */
    val leftEdge get() = xOffset - offScreenWidth

    /** Current top edge of visible window */
    val topEdge get() = yOffset - offScreenHeight

    /** Current right edge of visible window */
    val rightEdge get() = xOffset + width + offScreenWidth

    /** Current bottom edge of visible window */
    val bottomEdge get() = yOffset + height + offScreenHeight

    /** Document width with current [scale] */
    private val documentWidth get() = ((adapter?.documentWidth ?: 0) * scale).toInt()

    /** Document height with current [scale] */
    private val documentHeight get() = ((adapter?.documentHeight ?: 0) * scale).toInt()

    /** Check offset boundary to ensure not outside the document */
    private fun checkOffsetBoundary() {
        minXOffset = if (width > documentWidth) -(width - documentWidth) / 2 else 0
        maxXOffset = if (width > documentWidth) minXOffset else documentWidth - width

        minYOffset = if (height > documentHeight) -(height - documentHeight) / 2 else 0
        maxYOffset = if (height > documentHeight) minYOffset else documentHeight - height

        xOffset = adjustXOffset(xOffset)
        yOffset = adjustYOffset(yOffset)
    }

    /** Adjust offset in x direction */
    private fun adjustXOffset(x: Int) =
        if (x < minXOffset) minXOffset else if (x > maxXOffset) maxXOffset else x

    /** Adjust offset in y direction */
    private fun adjustYOffset(y: Int) =
        if (y < minYOffset) minYOffset else if (y > maxYOffset) maxYOffset else y

    /** Recycle and populate */
    private fun repopulate() {
        sliceManager.recycle()
        sliceManager.populate()
        ViewCompat.postInvalidateOnAnimation(this)
    }

    /** Convert x in view to x in document */
    fun viewXToDocumentX(x: Float) = x + xOffset

    /** Convert y in view to y in document */
    fun viewYToDocumentY(y: Float) = y + yOffset

    /** Invalidate visible slices */
    fun invalidateVisible() {
        sliceManager.rebind()
        ViewCompat.postInvalidateOnAnimation(this)
    }

    /** Invalidate all slices. It will recycle all visible slices and repopulate. */
    fun invalidateAll() {
        checkOffsetBoundary()
        sliceManager.recycleAll()
        sliceManager.populate()
        ViewCompat.postInvalidateOnAnimation(this)
    }

    /** Stop all running animations */
    fun stopAllAnimations() = animator.stopAll()

    /** Smooth move document to specified [x], [y] position */
    fun smoothMoveTo(x: Int, y: Int) {
        adapter ?: return
        animator.moveTo(x, y)
    }

    /** Move document to specified [x], [y] position, return false if not moved */
    fun moveTo(x: Int, y: Int): Boolean {
        adapter ?: return false

        val targetX = adjustXOffset(x)
        val targetY = adjustYOffset(y)

        if (targetY != yOffset || targetX != xOffset) {
            scrollListener?.onScrolled(this, targetX - xOffset, targetY - yOffset)
            xOffset = targetX
            yOffset = targetY
            repopulate()
            return true
        }
        return false
    }

    /** Move document by [dx], [dy], return false if not moved */
    fun moveBy(dx: Int, dy: Int) = moveTo(xOffset + dx, yOffset + dy)

    /** Zoom document to [scale] with pivot ([px], [py]), return false if scale not changed */
    fun zoomTo(scale: Float, px: Float, py: Float): Boolean {
        val targetScale = if (scale < minScale) minScale else if (scale > maxScale) maxScale else scale
        if (targetScale == this.scale) return false

        val ds = targetScale / this.scale

        val x = xOffset * ds - px + px * ds
        val y = yOffset * ds - py + py * ds

        this.scale = targetScale

        if (!moveTo(x.toInt(), y.toInt())) repopulate()

        isZooming = true
        onZoomed(ds, px, py)
        return true
    }

    /** Zoom document by [factor] with pivot ([px], [py]), return false if scale not changed */
    fun zoomBy(factor: Float, px: Float, py: Float): Boolean {
        if (Math.abs(factor - 1f) < 0.001) return false
        return zoomTo(factor * scale, px, py)
    }

    /** Callback when zoom start */
    @CallSuper
    open fun onZoomStart() {
        zoomListener?.onZoomStart(this)
    }

    /** Callback when zooming */
    @CallSuper
    open fun onZoomed(deltaScale: Float, px: Float, py: Float) {
        zoomListener?.onZoomed(this, deltaScale, px, py)
    }

    /** Callback when zoom end */
    @CallSuper
    open fun onZoomEnd() {
        val adapter = adapter ?: return

        if (!isZooming) {
            zoomListener?.onZoomEnd(this)
            return
        }
        isZooming = false

        adapter.onScale(scale)
        scale = 1f

        sliceManager.recycleAll()
        sliceManager.populate()
        ViewCompat.postInvalidateOnAnimation(this)

        zoomListener?.onZoomEnd(this)
    }

    /** Callback when pressed on position ([x], [y]) of document */
    open fun onPressed(x: Int, y: Int) {}

    /** Callback when cancel press */
    open fun onCancelPress() {}

    /** Callback when clicked on position ([x], [y]) of document */
    open fun onClicked(x: Int, y: Int) {}

    /** Draw decorations before slices were painted */
    open fun beforeDrawSlices(canvas: Canvas) {}

    /** Draw decorations after slices were painted */
    open fun afterDrawSlices(canvas: Canvas) {}

    @CallSuper
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        adapter ?: return
        checkOffsetBoundary()
        repopulate()
    }

    @CallSuper
    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(-xOffset.toFloat(), -yOffset.toFloat())
        canvas.scale(scale, scale)
        beforeDrawSlices(canvas)
        sliceManager.drawAll(canvas)
        afterDrawSlices(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent) = gestureDetector.onTouch(event)

    override fun canScrollVertically(direction: Int) = when {
        direction < 0 -> yOffset < maxYOffset
        direction > 0 -> yOffset > minYOffset
        else -> false
    }

    override fun canScrollHorizontally(direction: Int) = when {
        direction < 0 -> xOffset < maxXOffset
        direction > 0 -> xOffset > minXOffset
        else -> false
    }

    /** Adapter to retrieve slices */
    interface SliceAdapter {

        /** Row count of document after cut into slices */
        val rowCount: Int

        /** Width outside of the view to preload, the average slice width is good */
        val offScreenWidth: Int

        /** Height outside of the view to preload, the average slice height is good */
        val offScreenHeight: Int

        /** Total width of document */
        val documentWidth: Int

        /** Total height of document */
        val documentHeight: Int

        /** Minimal scale relative to original document scale */
        val minScale: Float

        /** Middle scale relative to original document scale */
        val midScale: Float

        /** Maximal scale relative to original document scale */
        val maxScale: Float

        /** Raw scale of the document */
        val rawScale: Float

        /**
         * Scale document with [relativeScale], when this function get called, the [scale] will
         * return to 1, you should recalculate the slices bounds here
         *
         * Notice that [relativeScale] is not the document scale, if you has a scale
         * on original document, you should handle it
         */
        fun onScale(relativeScale: Float)

        /** Column count of [row] */
        fun getColumnCount(row: Int): Int

        /** Create a new slice */
        fun newSlice(): Slice

        /** Get slice bounds in document */
        fun getBounds(outSlice: Slice)

        fun bindSlice(slice: Slice, view: DocumentView)

        fun onSliceRecycled(slice: Slice)
    }

    /** Represents slice in document */
    abstract class Slice {

        var row = 0
            internal set
        var col = 0
            internal set

        val bounds = Rect()

        internal fun set(src: Slice) {
            row = src.row
            col = src.col
            bounds.set(src.bounds)
        }

        abstract fun draw(canvas: Canvas)

        override fun toString() = "position: [$row, $col], bounds: $bounds"
    }

    interface ScrollListener {

        /**
         * Callback method to be called when document scrolled. It's normal that [onScrollSateChanged]
         * not get called while this method has been invoked, for example when jump to a position.
         */
        fun onScrolled(view: DocumentView, dx: Int, dy: Int)

        /**
         * Callback method to be called when scroll state changes. [oldState] and [newState] is one of
         * [SCROLL_STATE_IDLE], [SCROLL_STATE_DRAGGING] or [SCROLL_STATE_FLING]
         */
        fun onScrollSateChanged(view: DocumentView, oldState: Int, newState: Int)
    }

    interface ZoomListener {

        fun onZoomStart(view: DocumentView)

        fun onZoomed(view: DocumentView, deltaScale: Float, px: Float, py: Float)

        fun onZoomEnd(view: DocumentView)
    }

    companion object {
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_FLING = 2
    }
}
