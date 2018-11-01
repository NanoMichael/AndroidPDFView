package io.ea.documentview

import android.graphics.Canvas
import android.graphics.Rect
import java.util.*

/**
 * Created by nano on 17-12-1.
 */
abstract class DocumentAdapter(
    /** Original pages size with current DPI */
    val originalPagesSize: List<Size>,
    val gridWidth: Int,
    val gridHeight: Int) :
    DocumentView.SliceAdapter {

    val originalDocumentWidth = originalPagesSize.maxBy { it.width }?.width ?: 0
    val originalDocumentHeight = originalPagesSize.sumBy { it.height }

    val minPageWidth = originalPagesSize.minBy { it.width }?.width ?: 0
    val minPageHeight = originalPagesSize.minBy { it.height }?.height ?: 0

    /** Config to get scales and page margin */
    var config: AdapterConfig = EmptyAdapterConfig()
        set(value) {
            if (field === value) return
            field = value
            field.onConfigChange { updateParams() }
            updateParams()
        }

    override var documentWidth = originalDocumentWidth
    override var documentHeight = originalDocumentHeight
    override var minScale = config.minScale
    override var midScale = config.midScale
    override var maxScale = config.maxScale

    override val offScreenWidth get() = gridWidth
    override val offScreenHeight get() = gridHeight

    private val attrs = Array(originalPagesSize.size) { PageAttr() }
    private val gridPool = LinkedList<Grid>()

    /**
     * Crop on original pages of document, default is empty.
     * If cropped width/height greater than the [minPageWidth]/[minPageHeight],
     * it will be reset to empty, you should check it before set.
     */
    val crop = Rect()
    private var scaledCrop = Rect()

    override var rawScale = 1f
        set(value) {
            field = value
            updateParams()
        }

    override var rowCount = 0

    /** Setup adapter, this method will be called before attach to a [DocumentView] */
    fun setup() {
        rawScale = config.initialScale
        updatePageAttr()
    }

    /** Recalculate params and slices with [scale] */
    fun recalculate(scale: Float = config.initialScale) {
        rawScale = scale
        updatePageAttr()
    }

    fun checkCrop(crop: Rect) {
        with(crop) {
            if (left + right >= minPageWidth || top + bottom >= minPageHeight) {
                crop.setEmpty()
                return
            }
            if (left < 0) left = 0
            if (right < 0) right = 0
            if (top < 0) top = 0
            if (bottom < 0) bottom = 0
        }
    }

    /** Calculate parameters with [rawScale] */
    private fun updateParams() {
        checkCrop(crop)
        scaledCrop.apply {
            left = (crop.left * rawScale).toInt()
            top = (crop.top * rawScale).toInt()
            right = (crop.right * rawScale).toInt()
            bottom = (crop.bottom * rawScale).toInt()
        }

        documentWidth = ((originalDocumentWidth + config.pageMargin * 2) * rawScale + 0.5f).toInt() -
            scaledCrop.left - scaledCrop.right

        documentHeight = ((originalDocumentHeight + (originalPagesSize.size + 1) * config.pageMargin) * rawScale).toInt() -
            originalPagesSize.size * (scaledCrop.top + scaledCrop.bottom)

        minScale = config.minScale / rawScale
        midScale = config.midScale / rawScale
        maxScale = config.maxScale / rawScale
    }

    override fun onScale(relativeScale: Float) {
        rawScale *= relativeScale
        updatePageAttr()
    }

    /** Calculate page attributes with [rawScale] */
    private fun updatePageAttr() {
        if (originalPagesSize.isEmpty()) return

        val margin = config.pageMargin * rawScale
        var t = margin
        var row = 0
        val c = scaledCrop

        originalPagesSize.forEachIndexed { i, (w, h) ->
            attrs[i].apply {
                val fh = h * rawScale - c.top - c.bottom
                width = (w * rawScale - c.left - c.right).toInt()
                height = fh.toInt()

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
                t += margin + fh
            }
        }

        rowCount = row
    }

    /** Binary search page by [row], return -1 if not found */
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

    /** Page count of document */
    val pageCount get() = originalPagesSize.size

    /** Current page margin with [rawScale] */
    val currentPageMargin get() = (config.pageMargin * rawScale).toInt()

    /** Left position of [page] in document */
    fun leftPositionOf(page: Int) = (documentWidth - attrs[page].width) / 2

    /** Top position of [page] in document */
    fun topPositionOf(page: Int) = attrs[page].documentTop

    /** Width of [page] */
    fun widthOf(page: Int) = attrs[page].width

    /** Height of [page] */
    fun heightOf(page: Int) = attrs[page].height

    /** Convert area in page to area in document */
    fun pageAreaToDocArea(page: Int, outArea: Rect) {
        outArea.offset(leftPositionOf(page), topPositionOf(page))
        outArea.offset(-scaledCrop.left, -scaledCrop.top)
        outArea.apply {
            left = maxOf(left, leftPositionOf(page))
            top = maxOf(top, topPositionOf(page))
            right = minOf(right, widthOf(page) + left)
            bottom = minOf(bottom, heightOf(page) + top)
        }
    }

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
            clip.apply {
                set(slice.bounds)
                offset(-documentLeft, -attr.documentTop)
                offset(scaledCrop.left, scaledCrop.top)
            }
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
