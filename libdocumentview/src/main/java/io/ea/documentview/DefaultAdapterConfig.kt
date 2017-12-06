package io.ea.documentview

/**
 * Created by nano on 17-12-2.
 */
class DefaultAdapterConfig(private val fullWidthPageMargin: Int) : AdapterConfig {

    override var initialScale = 1f

    override var minScale = 1f

    override var midScale = 1f

    override var maxScale = 1f

    override var pageMargin = 0

    override fun update(viewWidth: Int, viewHeight: Int, originalPagesSize: List<Size>) {
        val maxPageWidth = originalPagesSize.maxBy { it.width }?.width ?: 0
        val maxPageHeight = originalPagesSize.maxBy { it.height }?.height ?: 0

        var widthScale = (viewWidth - fullWidthPageMargin * 2f) / maxPageWidth

        /** If page margin has been calculated before, we don't change it, just update scales */
        if (pageMargin == 0) pageMargin = (fullWidthPageMargin / widthScale).toInt()
        else widthScale = viewWidth / (maxPageWidth + pageMargin * 2f)

        val heightScale = viewHeight / (maxPageHeight + pageMargin * 2f)

        minScale = minOf(widthScale, heightScale)
        midScale = maxOf(widthScale, heightScale)
        maxScale = viewWidth * 3f / maxPageWidth
        initialScale = widthScale
    }
}
