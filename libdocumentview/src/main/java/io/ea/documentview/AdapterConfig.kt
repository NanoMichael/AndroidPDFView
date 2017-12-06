package io.ea.documentview

/**
 * Created by nano on 17-12-2.
 */
interface AdapterConfig {

    /** Initial scale to render document */
    val initialScale: Float

    val minScale: Float

    val midScale: Float

    val maxScale: Float

    val pageMargin: Int

    /** Update configuration with current [viewWidth] and [viewHeight] */
    fun update(viewWidth: Int, viewHeight: Int, originalPagesSize: List<Size>)
}
