package io.ea.documentview.pdf

import android.graphics.Bitmap
import io.ea.documentview.DocumentAdapter

/**
 * Created by nano on 17-11-30.
 */
abstract class RenderGrid : DocumentAdapter.Grid() {

    @Volatile
    var task: RenderingHandler.RenderingTask? = null

    abstract val render: Bitmap?

    abstract fun invalidate()
}
