package io.ea.documentview.image

import android.graphics.*
import android.util.Log
import io.ea.documentview.Size
import io.ea.documentview.rendering.Renderer
import io.ea.pdf.BuildConfig

/**
 * Created by nano on 18-3-21.
 */
class ImageRenderer(private val src: ImageSource, val bestQuality: Boolean, val gridSize: Size) :
    Renderer {

    private var decoder: BitmapRegionDecoder? = null
    private val canvas = Canvas()
    private val drawingMatrix = Matrix()

    private val bitmapOptions = BitmapFactory.Options()
    private var tmpBitmap: Bitmap? = null

    override val pagesSize = ArrayList<Size>(1)

    @Volatile
    var isOpened = false
        private set

    override fun open(onErr: (cause: Throwable) -> Unit) = try {
        val decoder = src.createDecoder()
        isOpened = true
        this.decoder = decoder
        pagesSize.add(Size(decoder.width, decoder.height))
        Log.e("abc", "size = [${decoder.width} ${decoder.height}]")
        val config = if (bestQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        tmpBitmap = Bitmap.createBitmap(gridSize.width, gridSize.height, config)
        bitmapOptions.apply {
            inBitmap = tmpBitmap
            inPreferredConfig = config
        }
        true
    } catch (t: Throwable) {
        onErr(t)
        false
    }

    private val tmpRegion = Rect()

    private fun Rect.scale(scale: Float): Rect {
        tmpRegion.set(
            (left * scale).toInt(),
            (top * scale).toInt(),
            (right * scale).toInt(),
            (bottom * scale).toInt())
        return tmpRegion
    }

    override fun renderPageClip(bitmap: Bitmap, page: Int, scale: Float, region: Rect,
        onErr: (Int, Throwable) -> Unit): Boolean {
        val decoder = decoder ?: return false
        Log.e("abc", "region = $region, scale = $scale")
        if (scale < 1f) {
            val revert = 1 / scale
            bitmapOptions.inSampleSize = revert.toInt()
            Log.i("abc", "sample size = ${bitmapOptions.inSampleSize}")
            bitmapOptions.inBitmap = bitmap
            val result = decoder.decodeRegion(region.scale(revert), bitmapOptions)
            Log.e("abc", "reuse: ${result === bitmap}")
            return true
        }
        val result = decoder.decodeRegion(region, bitmapOptions)
        if (BuildConfig.DEBUG) Log.d(TAG, "reuse tmpBitmap: ${tmpBitmap === result}")
        drawingMatrix.apply { reset(); setScale(1 / scale, 1 / scale) }
        canvas.setBitmap(bitmap)
        canvas.drawBitmap(result, drawingMatrix, null)
        return true
    }

    override fun release() {
        decoder?.recycle()
        tmpBitmap?.recycle()
    }

    companion object {
        const val TAG = "ImageRenderer"
    }
}
