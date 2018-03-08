package io.ea.documentview

import android.graphics.Rect

/**
 * Created by nano on 17-12-2.
 */
class DefaultAdapterConfig(private val fullWidthPageMargin: Int) : AdapterConfig {

    override var initialScale = 1f

    override var minScale = 1f

    override var midScale = 1f

    override var maxScale = 1f

    override var pageMargin = 0

    override var onConfigChange: () -> Unit = {}

    private val prevCrop = Rect()

    override fun update(viewWidth: Int, viewHeight: Int, adapter: DocumentAdapter) {
        if (adapter.originalPagesSize.isEmpty()) return

        val maxPageWidth = with(adapter) {
            originalPagesSize.maxBy { it.width }!!.width - crop.left - crop.right
        }
        val maxPageHeight = with(adapter) {
            originalPagesSize.maxBy { it.height }!!.height - crop.top - crop.bottom
        }

        var widthScale = (viewWidth - fullWidthPageMargin * 2f) / maxPageWidth

        /**
         * If page margin has been calculated before or crop has not been changed,
         * we don't change it, just update scales
         */
        if (pageMargin == 0 || prevCrop != adapter.crop) pageMargin = (fullWidthPageMargin / widthScale).toInt()
        else widthScale = viewWidth / (maxPageWidth + pageMargin * 2f)

        prevCrop.set(adapter.crop)

        val heightScale = viewHeight / (maxPageHeight + pageMargin * 2f)

        minScale = minOf(widthScale, heightScale)
        midScale = maxOf(widthScale, heightScale)
        maxScale = viewWidth * 3f / maxPageWidth
        initialScale = widthScale

        super.update(viewWidth, viewHeight, adapter)
    }

    override fun toString(): String =
        "initial scale = $initialScale, max scale = $maxScale, min scale = $minScale, mid scale = $midScale"
}
