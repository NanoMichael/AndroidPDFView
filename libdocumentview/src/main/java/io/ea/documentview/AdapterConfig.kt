package io.ea.documentview

import android.support.annotation.CallSuper

/**
 * Created by nano on 17-12-2.
 *
 * Adapter configurations
 */
interface AdapterConfig {

    /** Initial scale to render document */
    val initialScale: Float

    val minScale: Float

    val midScale: Float

    val maxScale: Float

    val pageMargin: Int

    var onConfigChange: () -> Unit

    fun onConfigChange(action: () -> Unit) {
        onConfigChange = action
    }

    /** Update configuration with current [viewWidth] and [viewHeight] */
    @CallSuper
    fun update(viewWidth: Int, viewHeight: Int, adapter: DocumentAdapter) {
        onConfigChange()
    }
}
