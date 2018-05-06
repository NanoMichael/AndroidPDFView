package io.ea.documentview

import android.graphics.Canvas
import android.graphics.Rect
import java.util.*

/**
 * Created by nano on 17-11-24.
 *
 * To manage slices, fill slices into visible window, and recycle invisible
 * slices into pool
 *
 * Slices are arranged by following way
 *
 * ```
 *
 * +------------------------------------------------+
 * |        |    slice   |   slice  |               |
 * +-------------------------------------------     +
 * |  slice   |    slice   |   slice  | slice |     |
 * +------------------------------------------------+
 * |      | slice   |    slice   |   slice  | slice |
 * +      ------------------------------------------+
 * | visible window                                 |
 * +------------------------------------------------+
 *
 * ```
 *
 * Slices may have different width and height, but requires same height
 * in same row
 */
internal class SliceManager(val view: DocumentView) {

    var adapter: DocumentView.SliceAdapter? = null
        set(value) {
            val old = field
            field = value
            switchAdapter(old)
        }

    /** Slices in visible window */
    private val slices = LinkedList<LinkedList<DocumentView.Slice>>()
    private val recycledSlices = LinkedList<DocumentView.Slice>()
    /** Visible area */
    private val visibleWindow = Rect()
    private val tmpRect = Rect()

    /** Switch new adapter and clear slices if needed */
    private fun switchAdapter(old: DocumentView.SliceAdapter?) {
        if (old === adapter) return
        if (old != null) {
            recycleAll()
            val new = adapter?.newSlice()
            if (recycledSlices.isNotEmpty() && new?.javaClass != recycledSlices[0].javaClass) {
                recycledSlices.clear()
            }
        }
    }

    /** First visible row in document, if slices is empty return -1 */
    val firstVisibleRow get() = if (slices.isEmpty()) -1 else slices.first.first.row

    /** Last visible row in document, if slices is empty return -1 */
    val lastVisibleRow get() = if (slices.isEmpty()) -1 else slices.last.first.row

    /** Recycle all populated slices */
    fun recycleAll() {
        slices.forEach { recycleRow(it) }
        slices.clear()
    }

    /** Draw all slices */
    fun drawAll(canvas: Canvas) = slices.forEach { it.forEach { it.draw(canvas) } }

    /** Populate slice into visible window */
    fun populate() {
        adapter ?: return
        visibleWindow.set(view.leftEdge, view.topEdge, view.rightEdge, view.bottomEdge)
        /* Following invoke order is important */
        fillFirst(); fillAbove(); fillBelow(); fillLeft(); fillRight()
    }

    /** Rebind visible slices */
    fun rebind() {
        val adapter = adapter ?: return
        slices.forEach { it.forEach { adapter.bindSlice(it, view) } }
    }

    /** Scale slice bounds by view scale */
    private fun toViewScale(r: Rect) = if (view.scale == 1f) r else tmpRect.apply {
        left = (r.left * view.scale).toInt()
        top = (r.top * view.scale).toInt()
        right = (r.right * view.scale).toInt()
        bottom = (r.bottom * view.scale).toInt()
    }

    /** Check if slice is visible */
    private val DocumentView.Slice.isVisible get() = Rect.intersects(visibleWindow, toViewScale(bounds))

    /** Check if slice is outside of visible window in vertical direction */
    private val DocumentView.Slice.isOutsideVertical
        get() = with(toViewScale(bounds)) { top > visibleWindow.bottom || bottom < visibleWindow.top }

    /** Fill first slice into visible window */
    private fun fillFirst() {
        val adapter = adapter ?: return
        if (slices.isNotEmpty()) return

        val tmp = acquireSlice()

        for (i in 0 until adapter.rowCount) {
            adapter.getBounds(tmp.apply { row = i; col = 0 })
            if (!tmp.isOutsideVertical) {
                for (j in 0 until adapter.getColumnCount(tmp.row)) {
                    adapter.getBounds(tmp.apply { col = j })
                    if (tmp.isVisible) {
                        val row = LinkedList<DocumentView.Slice>().also { slices.add(it) }
                        acquireSlice().apply { set(tmp) }.also { row.add(it); adapter.bindSlice(it, view) }
                        break
                    }
                }
                break
            }
        }

        recycleSlice(tmp, false)
    }

    /** Check if [slice] is legal */
    private fun check(slice: DocumentView.Slice) {
        if (slice.bounds.isEmpty) throw IllegalStateException("Slice bounds can not be empty")
    }

    /** Check if [slice] is legal and if has same height in single row */
    private fun check(slice: DocumentView.Slice, rowHeight: Int) {
        check(slice)
        if (rowHeight != 0 && slice.bounds.height() != rowHeight)
            throw IllegalStateException("Requires same height in one row")
    }

    /**
     * Fill slices into visible window in vertical direction
     *
     * [toBelow] specifies whether fill from top to bottom
     */
    private fun fillVertical(toBelow: Boolean) {
        val adapter = adapter ?: return
        if (adapter.rowCount == 0 || slices.isEmpty()) return

        val start = if (toBelow) slices.last.first else slices.first.first
        val range = if (toBelow) start.row + 1 until adapter.rowCount else start.row - 1 downTo 0
        val tmp = acquireSlice()

        for (r in range) {
            adapter.getBounds(tmp.apply { row = r; col = 0 })
            /**
             * We just need to check one slice,
             * because slices in same row have same height
             */
            if (tmp.isOutsideVertical) break

            var firstFound = false
            val nextRow = LinkedList<DocumentView.Slice>()
            val rowHeight = tmp.bounds.height()

            for (i in 0 until adapter.getColumnCount(tmp.row)) {
                adapter.getBounds(tmp.apply { col = i })
                check(tmp, rowHeight)

                if (tmp.isVisible) {
                    firstFound = true
                    acquireSlice().apply { set(tmp) }.also { nextRow.add(it); adapter.bindSlice(it, view) }
                } else if (firstFound) {
                    break
                }
            }

            if (nextRow.isNotEmpty()) if (toBelow) slices.add(nextRow) else slices.addFirst(nextRow)
        }

        recycleSlice(tmp, false)
    }

    private fun fillAbove() = fillVertical(false)

    private fun fillBelow() = fillVertical(true)

    /**
     * Fill slices into visible window in horizontal direction
     *
     * [toRight] specifies if fill from left to right
     */
    private fun fillHorizontal(toRight: Boolean) {
        val adapter = adapter ?: return
        if (adapter.rowCount == 0 || slices.isEmpty()) return

        val tmp = acquireSlice()

        for (i in 0 until slices.size) {
            val row = slices[i]
            val start = if (toRight) row.last else row.first
            val rowHeight = start.bounds.height()

            tmp.row = start.row

            val range = if (toRight) start.col + 1 until adapter.getColumnCount(tmp.row) else start.col - 1 downTo 0
            for (j in range) {
                adapter.getBounds(tmp.apply { col = j })
                check(tmp, rowHeight)

                if (tmp.isVisible) acquireSlice().apply { set(tmp) }.also {
                    if (toRight) row.add(it) else row.addFirst(it)
                    adapter.bindSlice(it, view)
                } else break
            }
        }

        recycleSlice(tmp, false)
    }

    private fun fillLeft() = fillHorizontal(false)

    private fun fillRight() = fillHorizontal(true)

    /** Recycle invisible slices */
    fun recycle() {
        visibleWindow.set(view.leftEdge, view.topEdge, view.rightEdge, view.bottomEdge)
        /* Following invoke order is important */
        recycleLeft(); recycleRight(); recycleAbove(); recycleBelow()
    }

    /**
     * Recycle invisible slices in horizontal direction
     *
     * [fromRight] specifies if recycle from right to left
     */
    private fun recycleHorizontal(fromRight: Boolean) {
        val rowIt = slices.iterator()
        while (rowIt.hasNext()) {
            val row = rowIt.next()
            val colIt = if (fromRight) row.descendingIterator() else row.iterator()
            while (colIt.hasNext()) {
                val slice = colIt.next()
                if (!slice.isVisible) {
                    recycleSlice(slice)
                    colIt.remove()
                } else {
                    break
                }
            }
            if (row.isEmpty()) rowIt.remove()
        }
    }

    private fun recycleLeft() = recycleHorizontal(false)

    private fun recycleRight() = recycleHorizontal(true)

    /**
     * Recycle invisible slices in vertical direction
     *
     * [fromBelow] specifies if recycle from bottom to top
     */
    private fun recycleVertical(fromBelow: Boolean) {
        val rowIt = if (fromBelow) slices.descendingIterator() else slices.iterator()
        while (rowIt.hasNext()) {
            val row = rowIt.next()
            if (row.isEmpty()) {
                rowIt.remove()
            } else {
                /*
                 * We just need to test first slice of the row,
                 * because the invisible slice of left side was recycled before
                 */
                if (!row.first.isVisible) {
                    recycleRow(row)
                    rowIt.remove()
                } else {
                    break
                }
            }
        }
    }

    private fun recycleAbove() = recycleVertical(false)

    private fun recycleBelow() = recycleVertical(true)

    /**
     * Recycle slice
     *
     * [notify] specifies if notify [adapter] that slice has been recycled
     */
    private fun recycleSlice(slice: DocumentView.Slice, notify: Boolean = true) {
        if (notify) adapter!!.onSliceRecycled(slice)
        recycledSlices.add(slice)
    }

    private fun recycleRow(slices: Collection<DocumentView.Slice>) {
        slices.forEach { adapter!!.onSliceRecycled(it) }
        recycledSlices.addAll(slices)
    }

    private fun acquireSlice() = if (recycledSlices.isEmpty()) adapter!!.newSlice() else recycledSlices.remove()
}
