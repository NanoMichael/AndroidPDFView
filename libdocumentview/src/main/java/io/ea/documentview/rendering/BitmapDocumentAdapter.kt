package io.ea.documentview.rendering

import android.graphics.*
import android.support.v4.view.ViewCompat
import android.util.Log
import io.ea.documentview.DocumentAdapter
import io.ea.documentview.DocumentView
import io.ea.documentview.Size
import io.ea.pdf.BuildConfig
import java.util.*

/**
 * Created by nano on 17-11-30.
 *
 * To render document that use bitmap to render.
 */
open class BitmapDocumentAdapter(
    val view: DocumentView,
    originalPagesSize: List<Size>,
    val bitmapPool: BitmapPool,
    val renderingHandler: RenderingHandler,
    val onRenderingError: (Int, Throwable) -> Unit) :
    DocumentAdapter(originalPagesSize, bitmapPool.width, bitmapPool.height) {

    private val src = Rect()

    override fun newGrid() = BitmapGrid()

    inner class BitmapGrid : RenderGrid() {

        private var thumbnail: Bitmap? = null
        private var content: Bitmap? = null
        private var tmpRegion = Rect()
        private val debugPaint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE }

        override val render get() = if (content != null) content else thumbnail

        override fun onBind(view: DocumentView) {
            if (view.isZooming) {
                thumbnail = bitmapPool.acquireThumbnail()
                val scale = bitmapPool.thumbnailScale * rawScale
                tmpRegion.apply {
                    val s = bitmapPool.thumbnailScale
                    left = (clip.left * s).toInt()
                    top = (clip.top * s).toInt()
                    right = (clip.right * s).toInt()
                    bottom = (clip.bottom * s).toInt()
                }
                renderingHandler.renderGrid(this, scale, tmpRegion, renderingCallback)
            } else {
                content = bitmapPool.acquireRender()
                renderingHandler.renderGrid(this, rawScale, clip, renderingCallback)
            }
        }

        override fun draw(canvas: Canvas, slice: DocumentView.Slice) {
            val bounds = slice.bounds
            if (content != null) {
                src.set(0, 0, bounds.width(), bounds.height())
                canvas.drawBitmap(content, src, bounds, null)
            } else if (thumbnail != null) {
                val w = (bounds.width() * bitmapPool.thumbnailScale).toInt()
                val h = (bounds.height() * bitmapPool.thumbnailScale).toInt()
                src.set(0, 0, w, h)
                if (!src.isEmpty) canvas.drawBitmap(thumbnail, src, bounds, null)
            }
            canvas.drawRect(bounds, debugPaint)
        }

        override fun onRecycle() {
            val t = task
            if (t != null) renderingHandler.cancelTask(t)
            if (thumbnail != null) bitmapPool.recycleThumbnail(thumbnail!!.apply { eraseColor(Color.WHITE) })
            if (content != null) bitmapPool.recycleRender(content!!.apply { eraseColor(Color.WHITE) })
            thumbnail = null
            content = null
        }

        override fun invalidate() {
            ViewCompat.postInvalidateOnAnimation(view)
        }
    }

    private val renderingCallback = object : RenderingHandler.RenderingCallback {

        override fun onRendered(grid: RenderGrid, success: Boolean) {
            if (success) view.post { grid.invalidate() }
        }

        override fun onRenderingError(page: Int, cause: Throwable) {
            this@BitmapDocumentAdapter.onRenderingError(page, cause)
        }
    }

    /** Simple pool to create and recycle bitmap, you may want to share one pool between adapters */
    open class BitmapPool(val width: Int, val height: Int, val bestQuality: Boolean, val thumbnailScale: Float) {

        private val renderPool = LinkedList<Bitmap>()
        private val thumbnailPool = LinkedList<Bitmap>()

        private val thumbnailWidth = (width * thumbnailScale).toInt()
        private val thumbnailHeight = (height * thumbnailScale).toInt()

        private var createdCount = 0

        open fun acquireRender() = if (renderPool.isEmpty()) {
            val config = if (bestQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            createdCount++
            if (BuildConfig.DEBUG) Log.i(TAG, "create new bitmap, count = $createdCount")
            Bitmap.createBitmap(width, height, config)!!.apply { eraseColor(Color.WHITE) }
        } else renderPool.remove()!!

        open fun recycleRender(render: Bitmap) {
            if (!renderPool.contains(render)) renderPool.add(render)
        }

        open fun acquireThumbnail() = if (thumbnailPool.isEmpty()) {
            Bitmap.createBitmap(thumbnailWidth, thumbnailHeight, Bitmap.Config.RGB_565)!!.apply {
                eraseColor(Color.WHITE)
            }
        } else thumbnailPool.remove()!!

        open fun recycleThumbnail(thumbnail: Bitmap) {
            if (!thumbnailPool.contains(thumbnail)) thumbnailPool.add(thumbnail)
        }

        companion object {
            const val TAG = "BitmapPool"
        }
    }
}
