package io.ea.documentview

import android.os.Handler
import android.os.Looper
import android.os.Message
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
    private val tapHandler = SingleTapHandler(Looper.getMainLooper())
    private val singleTabDelay = ViewConfiguration.getDoubleTapTimeout().toLong()
    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop

    private var scrolling = false
    private var scaling = false
    private var doubleTaping = false

    override fun onDown(e: MotionEvent): Boolean {
        animator.stopFling()
        animator.stopMove()
        return true
    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float): Boolean {
        cancelPress()
        if (scaling) return false
        var x = dx
        var y = dy
        if (!scrolling) {
            view.scrollState = DocumentView.SCROLL_STATE_DRAGGING
            /*
             * FIXME There should be a better way
             * Hack way to remove the touch slop distance when first scrolling event happened
             */
            if (e2.pointerCount < 2) {
                val r = Math.sqrt((touchSlop * touchSlop / (dx * dx + dy * dy)).toDouble()).toFloat()
                x -= dx * r
                y -= dy * r
            }
        }
        scrolling = true
        return view.moveBy(x.toInt(), y.toInt())
    }

    private fun onScrollEnd() {
        scrolling = false
        view.scrollState = DocumentView.SCROLL_STATE_IDLE
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, vx: Float, vy: Float): Boolean {
        cancelPress()
        if (scaling) return false
        return animator.fling(vx.toInt(), vy.toInt())
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        cancelPress()
        doubleTaping = true
        when {
            Math.abs(view.scale - view.midScale) < 0.00001 -> animator.zoom(view.scale, view.minScale, e.x, e.y)
            view.scale < view.midScale -> animator.zoom(view.scale, view.midScale, e.x, e.y)
            else -> animator.zoom(view.scale, view.midScale, e.x, e.y)
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
        if (scaling) {
            scaling = false
            view.onZoomEnd()
        }
    }

    fun onTouch(event: MotionEvent): Boolean {
        val action = event.action

        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_POINTER_DOWN -> cancelPress()
            MotionEvent.ACTION_DOWN -> onPressed(event)
            MotionEvent.ACTION_UP -> {
                if (doubleTaping) {
                    doubleTaping = false
                    cancelPress()
                }
                if (scrolling) {
                    onScrollEnd()
                    cancelPress()
                }
                /*
                 * FIXME There should be a better way
                 * A hack way to solve the bug that the
                 * [android.view.ScaleGestureDetector.OnScaleGestureListener.onScaleEnd]
                 * may not get called after scaling has end
                 */
                if (scaling) {
                    scaling = false
                    view.onZoomEnd()
                    cancelPress()
                }
                stillDown = false
                if (pressed && !stillDown && !tapHandler.hasMessages(TAP)) onSingleTab()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelPress()
                stillDown = false
            }
        }

        var handled = scaleDetector.onTouchEvent(event)
        handled = handled or gestureDetector.onTouchEvent(event)
        return handled
    }

    private var stillDown = false
    private var pressed = false
    private var pressedX = 0
    private var pressedY = 0

    private fun onPressed(e: MotionEvent) {
        cancelPress()
        stillDown = true
        pressed = true
        pressedX = view.viewXToDocumentX(e.x).toInt()
        pressedY = view.viewYToDocumentY(e.y).toInt()
        view.onPressed(pressedX, pressedY)
        tapHandler.sendEmptyMessageDelayed(TAP, singleTabDelay)
    }

    private fun cancelPress() {
        if (pressed) view.onCancelPress()
        tapHandler.removeMessages(TAP)
        pressed = false
    }

    private fun onSingleTab() {
        cancelPress()
        view.onClicked(pressedX, pressedY)
    }

    private inner class SingleTapHandler(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            if (msg.what != TAP) return
            if (pressed && !stillDown) onSingleTab()
        }
    }

    companion object {
        const val TAP = 1
    }
}
