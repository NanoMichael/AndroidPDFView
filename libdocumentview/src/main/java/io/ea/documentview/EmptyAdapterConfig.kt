package io.ea.documentview

/**
 * Created by nano on 17-12-8.
 */
class EmptyAdapterConfig : AdapterConfig {

    override val initialScale = 1f
    override val minScale = 1f
    override val midScale = 1f
    override val maxScale = 1f
    override val pageMargin = 0
    override var onConfigChange: () -> Unit = {}
}