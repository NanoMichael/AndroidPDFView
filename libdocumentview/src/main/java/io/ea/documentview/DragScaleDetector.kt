package io.ea.documentview

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration

/**
 * Created by nano on 17-11-24.
 */
internal class DragScaleDetector(val view: DocumentView, private val animator: DocumentAnimator) :
    GestureDetector.SimpleOnGestureListener(),
    ScaleGestureDetector.OnScaleGestureListener {

    private val gestureDetector = GestureDetector(view.context, this).apply { setIsLongpressEnabled(false) }
    private val scaleDetector = ScaleGestureDetector(view.context, this)
    private val clickOffsetTolerance = ViewConfiguration.get(view.context).scaledTouchSlop / 2

    private var scrolling = false
    private var pressing = false
    private var scaling = false
    private var pressedX = 0
    private var pressedY = 0

    override fun onShowPress(e: MotionEvent) {
        cancelPress()
        pressing = true
        pressedX = view.viewXToDocumentX(e.x).toInt()
        pressedY = view.viewYToDocumentY(e.y).toInt()
        view.onPressed(pressedX, pressedY)
    }

    private fun cancelPress() {
        if (pressing) view.onCancelPress()
        pressing = false
    }

    override fun onDown(e: MotionEvent): Boolean {
        animator.stopFling()
        animator.stopMove()
        return true
    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float): Boolean {
        if (scaling) return false
        cancelPress()
        if (!scrolling) view.onScrollStart()
        scrolling = true
        return view.moveBy(dx.toInt(), dy.toInt())
    }

    private fun onScrollEnd() {
        scrolling = false
        view.onScrollEnd()
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, vx: Float, vy: Float): Boolean {
        if (scaling) return false
        cancelPress()
        return animator.fling(vx.toInt(), vy.toInt())
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        cancelPress()
        when {
            Math.abs(view.scale - view.midScale) < 0.00001 -> animator.zoom(view.scale, view.minScale, e.x, e.y)
            view.scale < view.midScale -> animator.zoom(view.scale, view.midScale, e.x, e.y)
            else -> animator.zoom(view.scale, view.midScale, e.x, e.y)
        }
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        cancelPress()
        val x = view.viewXToDocumentX(e.x)
        val y = view.viewYToDocumentY(e.y)
        if (Math.abs(x - pressedX) < clickOffsetTolerance &&
            Math.abs(y - pressedY) < clickOffsetTolerance) {
            view.onClicked(pressedX, pressedY)
        }
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        cancelPress()
        scaling = true
        view.onZoomStart()
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        view.zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        scaling = false
        view.onZoomEnd()
    }

    fun onTouch(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            if (scrolling) onScrollEnd()
            if (pressing) cancelPress()
            /**
             * A hack way to solve the bug that the
             * [android.view.ScaleGestureDetector.OnScaleGestureListener.onScaleEnd]
             * may not get called after scaling has end
             */
            if (scaling) {
                scaling = false
                view.clearZoomFlag()
            }
        }
        var handled = scaleDetector.onTouchEvent(event)
        handled = handled or gestureDetector.onTouchEvent(event)
        return handled
    }
}
