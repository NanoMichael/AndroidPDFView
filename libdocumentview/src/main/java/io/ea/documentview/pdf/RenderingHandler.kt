package io.ea.documentview.pdf

import android.os.Handler
import android.os.Looper
import android.os.Message
import java.util.*

/**
 * Created by nano on 17-11-29.
 */
class RenderingHandler(looper: Looper) :
    Handler(looper) {

    var renderer: PDFRenderer? = null
    private val taskPool = LinkedList<RenderingTask>()

    /**
     * Render grid
     *
     * [scale] specifies the document scale, ([left], [top]) specifies the start position of the page
     * to render. When task done, [RenderingCallback.onRendered] will be called whether the rendering
     * is successful or not.
     */
    fun renderGrid(grid: RenderGrid, scale: Float, left: Int, top: Int, callback: RenderingCallback) {
        val task = acquireTask().apply { set(grid, scale, left, top, callback) }
        val msg = obtainMessage(MSG_RENDERING, task)
        sendMessage(msg)
    }

    /** Cancel rendering [task] */
    fun cancelTask(task: RenderingTask) {
        removeCallbacksAndMessages(task)
        recycleTask(task)
    }

    fun cancelAllPendingTasks() = removeCallbacksAndMessages(null)

    override fun handleMessage(msg: Message) {
        if (msg.what != MSG_RENDERING) return
        val renderer = renderer ?: return

        val task = msg.obj as RenderingTask
        if (!task.isInUse) return

        val callback = task.callback ?: return
        val grid = task.grid ?: return
        val render = grid.render ?: return

        val success = renderer.renderPageClip(render, grid.page,
            task.left, task.top, task.scale, callback::onRenderingError)

        if (task.isInUse) callback.onRendered(grid, success)
        recycleTask(task)
    }

    private fun acquireTask() = synchronized(taskPool) {
        if (taskPool.isEmpty()) RenderingTask() else taskPool.remove()
    }

    private fun recycleTask(task: RenderingTask) = synchronized(taskPool) {
        task.recycle()
        if (!taskPool.contains(task)) taskPool.add(task)
    }

    class RenderingTask {

        var scale: Float = 1f
            private set
        var left: Int = 0
            private set
        var top: Int = 0
            private set
        var grid: RenderGrid? = null
            private set
        var callback: RenderingCallback? = null
            private set
        @Volatile
        var isInUse = false
            private set

        fun set(grid: RenderGrid, scale: Float, left: Int, top: Int, callback: RenderingCallback) {
            isInUse = true
            this.grid = grid
            this.scale = scale
            this.left = left
            this.top = top
            this.callback = callback
            grid.task = this
        }

        fun recycle() {
            isInUse = false
            callback = null
            grid?.task = null
            grid = null
        }
    }

    interface RenderingCallback {

        /** Callback when rendering done, [success] specifies whether rendering is successful */
        fun onRendered(grid: RenderGrid, success: Boolean)

        /** Callback when rendering error */
        fun onRenderingError(page: Int, cause: Throwable)
    }

    companion object {
        const val MSG_RENDERING = 1
    }
}
