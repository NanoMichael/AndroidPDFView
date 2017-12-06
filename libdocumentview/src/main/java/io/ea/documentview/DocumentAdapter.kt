package io.ea.documentview

import android.graphics.Canvas
import android.graphics.Rect
import java.util.*

/**
 * Created by nano on 17-12-1.
 */
abstract class DocumentAdapter(
    /** Original pages size with current DPI */
    private val originalPagesSize: List<Size>,
    private val gridWidth: Int,
    private val gridHeight: Int,
    private val config: AdapterConfig) :
    DocumentView.SliceAdapter {

    private val baseDocumentWidth = originalPagesSize.maxBy { it.width }?.width ?: 0
    private val baseDocumentHeight = originalPagesSize.sumBy { it.height }

    override var documentWidth = baseDocumentWidth
    override var documentHeight = baseDocumentHeight
    override var minScale = config.minScale
    override var midScale = config.midScale
    override var maxScale = config.maxScale

    override val offScreenWidth get() = gridWidth
    override val offScreenHeight get() = gridHeight

    private val attrs = Array(originalPagesSize.size) { PageAttr() }
    private val gridPool = LinkedList<Grid>()

    override var rawScale = 1f
        set(value) {
            field = value
            calculateParams()
        }

    override var rowCount = 0

    init {
        setup()
    }

    private fun setup() {
        rawScale = config.initialScale
        calculatePageAttr()
    }

    /** Calculate parameters with [rawScale] */
    private fun calculateParams() {
        documentWidth = ((baseDocumentWidth + config.pageMargin * 2) * rawScale + 0.5f).toInt()
        documentHeight = ((baseDocumentHeight + (originalPagesSize.size + 1) * config.pageMargin) * rawScale).toInt()
        minScale = config.minScale / rawScale
        midScale = config.midScale / rawScale
        maxScale = config.maxScale / rawScale
    }

    override fun onScale(relativeScale: Float) {
        rawScale *= relativeScale
        calculatePageAttr()
    }

    /** Calculate page attributes with [rawScale] */
    private fun calculatePageAttr() {
        val margin = config.pageMargin * rawScale
        var t = margin
        var row = 0

        originalPagesSize.forEachIndexed { i, (w, h) ->
            attrs[i].apply {
                width = (w * rawScale).toInt()
                height = (h * rawScale).toInt()

                documentTop = (t + 0.5f).toInt()
                startRow = row

                val rowMod = height % gridHeight
                val rows = if (rowMod != 0) height / gridHeight + 1 else height / gridHeight
                endRow = startRow + rows

                val colMod = width % gridWidth
                colCount = if (colMod != 0) width / gridWidth + 1 else width / gridWidth

                lastClip.apply {
                    left = if (colMod != 0) width - colMod else width - gridWidth
                    top = if (rowMod != 0) height - rowMod else height - gridHeight
                    right = width
                    bottom = height
                }

                row = endRow
                /** To avoid round problem */
                t += margin + h * rawScale
            }
        }

        rowCount = row
    }

    /** Binary search page by [row], if not found return -1 */
    fun pageOf(row: Int): Int {
        var low = 0
        var high = attrs.size - 1

        while (low <= high) {
            val mid = (low + high) / 2
            val midVal = attrs[mid]

            if (row >= midVal.startRow && row < midVal.endRow) return mid

            if (row < midVal.startRow) high = mid - 1
            else low = mid + 1
        }
        return -1
    }

    /** Current page margin with [rawScale] */
    val currentPageMargin get() = config.pageMargin * rawScale

    /** Left position of [page] in document */
    fun leftPositionOf(page: Int) = (documentWidth - attrs[page].width) / 2

    /** Top position of [page] in document */
    fun topPositionOf(page: Int) = attrs[page].documentTop

    /** Width of [page] */
    fun widthOf(page: Int) = attrs[page].width

    /** Height of [page] */
    fun heightOf(page: Int) = attrs[page].height

    /** Retrieve grid from pool, create a new if pool is empty */
    private fun acquireGrid() = if (gridPool.isEmpty()) newGrid() else gridPool.remove()

    /** Recycle grid into pool */
    private fun recycleGrid(grid: Grid) = gridPool.add(grid)

    override fun newSlice() = GridSlice()

    override fun getColumnCount(row: Int): Int {
        val page = pageOf(row)
        return attrs[page].colCount
    }

    override fun getBounds(outSlice: DocumentView.Slice) {
        val attr = attrs[pageOf(outSlice.row)]
        val clip = attr.lastClip
        val bounds = outSlice.bounds

        if (outSlice.col == attr.colCount - 1) bounds.apply { left = clip.left; right = clip.right }
        else bounds.apply { left = outSlice.col * gridWidth; right = left + gridWidth }

        if (outSlice.row == attr.endRow - 1) bounds.apply { top = clip.top; bottom = clip.bottom }
        else bounds.apply { top = (outSlice.row - attr.startRow) * gridHeight; bottom = top + gridHeight }

        bounds.offset((documentWidth - attr.width) / 2, attr.documentTop)
    }

    override fun bindSlice(slice: DocumentView.Slice, view: DocumentView) {
        val p = pageOf(slice.row)
        val attr = attrs[p]
        val documentLeft = (documentWidth - attr.width) / 2

        val grid = acquireGrid().apply {
            page = p
            clip.apply { set(slice.bounds); offset(-documentLeft, -attr.documentTop) }
            onBind(view)
        }
        (slice as GridSlice).grid = grid
    }

    override fun onSliceRecycled(slice: DocumentView.Slice) {
        with(slice as GridSlice) {
            grid?.onRecycle()
            if (grid != null) recycleGrid(grid!!)
            grid = null
        }
    }

    /** Create a new grid */
    abstract fun newGrid(): Grid

    /** Delegate [draw] operation to [Grid] */
    class GridSlice : DocumentView.Slice() {

        /** Attached grid */
        var grid: Grid? = null

        override fun draw(canvas: Canvas) {
            grid?.draw(canvas, this)
        }
    }

    /** Delegate class to do the [onBind], [draw], [onRecycle] job */
    abstract class Grid {

        var page = 0
            internal set

        val clip = Rect()

        abstract fun onBind(view: DocumentView)

        abstract fun draw(canvas: Canvas, slice: DocumentView.Slice)

        abstract fun onRecycle()
    }

    /** Page attributes */
    private class PageAttr {

        /** Current page width with scale */
        var width: Int = 0

        /** Current page height with scale */
        var height: Int = 0

        /** Start row in document */
        var startRow: Int = 0

        /** End row in document */
        var endRow: Int = 0

        /** Column count when cut into slices */
        var colCount: Int = 0

        /** Top position in document */
        var documentTop: Int = 0

        /** Last clip of this page */
        val lastClip: Rect = Rect()
    }
}
