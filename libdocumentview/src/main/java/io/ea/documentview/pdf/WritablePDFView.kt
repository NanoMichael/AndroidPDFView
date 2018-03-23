package io.ea.documentview.pdf

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import io.ea.documentview.rendering.HandWriting

/**
 * Created by nano on 17-12-5.
 */
open class WritablePDFView : PDFView {

    constructor(context: Context) :
        this(context, null)

    constructor(context: Context, attrs: AttributeSet?) :
        this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    /** Whether writing is enabled, scroll and scale are disabled when is `true`, default is `false` */
    var isWritingEnabled = false
        set(value) {
            field = value
            isGesturesEnabled = !value
        }

    var handWriting: HandWriting = HandWriting(this)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isWritingEnabled) return super.onTouchEvent(event)
        return handWriting.onTouchEvent(event)
    }

    override fun canScrollHorizontally(direction: Int) =
        isWritingEnabled || super.canScrollHorizontally(direction)

    override fun canScrollVertically(direction: Int) =
        isWritingEnabled || super.canScrollVertically(direction)

    override fun afterDrawSlices(canvas: Canvas) {
        handWriting.drawStrokes(canvas)
        super.afterDrawSlices(canvas)
    }

    override fun onZoomEnd() {
        handWriting.zoom()
        super.onZoomEnd()
    }
}
