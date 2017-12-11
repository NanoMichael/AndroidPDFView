package io.ea.documentview

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.support.v4.view.ViewCompat
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller

/**
 * Created by nano on 17-11-26.
 */
internal class DocumentAnimator(val context: Context, val view: DocumentView) {

    private val interpolator = DecelerateInterpolator()

    private val flingAnim by lazy { FlingAnimator() }
    private val zoomAnim by lazy { ZoomAnimator() }
    private val moveAnim by lazy { MoveAnimator() }

    fun fling(vx: Int, vy: Int) = flingAnim.start(vx, vy)

    fun zoom(fromScale: Float, toScale: Float, px: Float, py: Float) =
        zoomAnim.start(fromScale, toScale, px, py)

    fun moveTo(toX: Int, toY: Int) = moveAnim.start(toX, toY)

    fun stopFling() = flingAnim.stop()

    fun stopMove() = moveAnim.stop()

    fun stopAll() {
        flingAnim.stop()
        zoomAnim.stop()
        moveAnim.stop()
    }

    inner class MoveAnimator :
        ValueAnimator.AnimatorUpdateListener,
        EmptyAnimatorListener() {

        private var xAnim: ValueAnimator? = null
        private var yAnim: ValueAnimator? = null

        fun start(toX: Int, toY: Int) {
            var hasAnim = false
            if (toX != view.xOffset && toX > view.minXOffset && toX < view.maxXOffset) {
                xAnim = ValueAnimator.ofInt(view.xOffset, toX).apply { setup() }
                hasAnim = true
            }
            if (toY != view.yOffset && toY > view.minYOffset && toY < view.maxYOffset) {
                yAnim = ValueAnimator.ofInt(view.yOffset, toY).apply { setup() }
                hasAnim = true
            }
            if (hasAnim) {
                stopAll()
                xAnim?.start()
                yAnim?.start()
                view.scrollState = DocumentView.SCROLL_STATE_FLING
            }
        }

        fun stop() {
            xAnim?.cancel()
            yAnim?.cancel()
        }

        private fun ValueAnimator.setup() {
            interpolator = this@DocumentAnimator.interpolator
            duration = ANIM_DURATION
            addUpdateListener(this@MoveAnimator)
            addListener(this@MoveAnimator)
        }

        override fun onAnimationUpdate(animation: ValueAnimator) {
            if (animation === xAnim) view.moveTo(animation.animatedValue as Int, view.yOffset)
            if (animation === yAnim) view.moveTo(view.xOffset, animation.animatedValue as Int)
        }

        override fun onAnimationEnd(animation: Animator) {
            view.scrollState = DocumentView.SCROLL_STATE_IDLE
        }
    }

    inner class ZoomAnimator :
        ValueAnimator.AnimatorUpdateListener,
        EmptyAnimatorListener() {

        private var pivotX = 0f
        private var pivotY = 0f
        private var anim: ValueAnimator? = null

        fun start(fromScale: Float, toScale: Float, px: Float, py: Float) {
            if (fromScale == toScale) return
            stopAll()
            pivotX = px
            pivotY = py
            anim = ValueAnimator.ofFloat(fromScale, toScale).apply {
                interpolator = this@DocumentAnimator.interpolator
                duration = ANIM_DURATION
                addUpdateListener(this@ZoomAnimator)
                addListener(this@ZoomAnimator)
            }
            anim?.start()
            view.onZoomStart()
        }

        fun stop() = anim?.cancel()

        override fun onAnimationUpdate(animation: ValueAnimator) {
            val scale = animation.animatedValue as Float
            view.zoomTo(scale, pivotX, pivotY)
        }

        override fun onAnimationEnd(animation: Animator) {
            view.onZoomEnd()
        }
    }

    inner class FlingAnimator : Runnable {

        private val scroller = OverScroller(context)

        fun start(vx: Int, vy: Int): Boolean {
            val flingX = if (vx < 0) view.xOffset < view.maxXOffset else view.xOffset > view.minXOffset
            val flingY = if (vy < 0) view.yOffset < view.maxYOffset else view.yOffset > view.minYOffset

            if (!flingX && !flingY) return false

            stopAll()

            scroller.fling(view.xOffset, view.yOffset, -vx, -vy,
                view.minXOffset, view.maxXOffset,
                view.minYOffset, view.maxYOffset, 0, 0)
            view.scrollState = DocumentView.SCROLL_STATE_FLING
            view.post(this)
            return true
        }

        fun stop() = scroller.forceFinished(true)

        override fun run() {
            if (scroller.isFinished || !scroller.computeScrollOffset()) {
                view.scrollState = DocumentView.SCROLL_STATE_IDLE
                return
            }
            view.moveTo(scroller.currX, scroller.currY)
            ViewCompat.postOnAnimation(view, this)
        }
    }

    open class EmptyAnimatorListener : Animator.AnimatorListener {

        override fun onAnimationRepeat(animation: Animator) {
        }

        override fun onAnimationEnd(animation: Animator) {
        }

        override fun onAnimationCancel(animation: Animator) {
        }

        override fun onAnimationStart(animation: Animator) {
        }
    }

    companion object {
        const val ANIM_DURATION = 500L
    }
}
