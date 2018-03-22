package io.ea.documentview.rendering

import android.graphics.Bitmap
import android.graphics.Rect
import io.ea.documentview.Size

/**
 * Created by nano on 18-3-21.
 *
 * Renderer to render parts of document
 */

interface Renderer {

    /** Original pages size of document, return empty list if no document opened */
    val pagesSize: List<Size>

    /** Open renderer with error callback [onErr], return false if open failed */
    fun open(onErr: (cause: Throwable) -> Unit): Boolean

    /** Render page fragment into [bitmap] with specified [scale] and [region], return false if render failed */
    fun renderPageClip(
        bitmap: Bitmap,
        page: Int,
        scale: Float,
        region: Rect,
        onErr: (page: Int, cause: Throwable) -> Unit): Boolean

    fun release()
}
