package io.ea.documentview.rendering

import android.graphics.*
import android.os.Build
import android.support.v4.view.MotionEventCompat
import android.view.MotionEvent
import io.ea.documentview.DocumentView

/**
 * Created by nano on 18-3-22.
 */
open class HandWriting(private val view: DocumentView) {

    private val visibleWindow = RectF()
    private val tmpRect = RectF()
    private val strokes = arrayListOf<Stroke>()
    private var currStroke: Stroke? = null
    private val writingMatrix = Matrix()
    private val paint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val tmpPath = Path()
    private val erasePath = Path()

    /** Eraser radius, default is 8dp */
    var eraserRadius = view.context.resources.displayMetrics.density * ERASER_RADIUS

    /** If support eraser */
    val isSupportErase = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

    /** Whether is in erase mode, default is `false` */
    var isEraseMode = false
        set(value) {
            field = if (!isSupportErase) false else value
        }

    /** Current writing color*/
    var writingColor = Color.BLACK

    /** Current writing thickness */
    var writingThickness = 2f

    /** On writing callback */
    var onWriting: () -> Unit = {}

    private var lastX = 0f
    private var lastY = 0f

    fun clearWriting() {
        strokes.clear()
        view.invalidate()
    }

    fun retreatWriting() {
        if (strokes.isEmpty()) return
        strokes.removeAt(strokes.size - 1)
        view.invalidate()
    }

    private fun writeStart(x: Float, y: Float) {
        val stroke = Stroke(Path(), writingColor, writingThickness)
        stroke.path.reset()
        stroke.path.moveTo(x, y)
        lastX = x
        lastY = y
        currStroke = stroke
        onWriting()
    }

    private fun writeTo(x: Float, y: Float) {
        val stroke = currStroke ?: return
        val dx = Math.abs(x - lastX)
        val dy = Math.abs(y - lastY)
        if (dx >= WRITING_TOLERANCE || dy >= WRITING_TOLERANCE) {
            stroke.path.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
            lastX = x
            lastY = y
            view.invalidate()
        }
        onWriting()
    }

    private fun writeDone() {
        val stroke = currStroke ?: return
        if (stroke.path.isEmpty) {
            currStroke = null
            return
        }
        stroke.apply { path.lineTo(lastX, lastY); path.computeBounds(bounds, false) }
        strokes.add(stroke)
        currStroke = null
        view.invalidate()
    }

    private fun writeCancel() {
        currStroke = null
        view.invalidate()
    }

    private fun erase(x: Float, y: Float) {
        if (!isSupportErase) return
        val it = strokes.iterator()
        while (it.hasNext()) {
            val stroke = it.next()
            if (!stroke.bounds.contains(x, y)) continue
            erasePath.apply { reset(); addCircle(x, y, eraserRadius, Path.Direction.CW); close() }
            tmpPath.apply { reset(); set(stroke.path) }
            /** Add check to avoid annoying lint warning */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                tmpPath.op(erasePath, Path.Op.INTERSECT)
            }
            tmpPath.computeBounds(tmpRect, true)
            if (!tmpRect.isEmpty) it.remove()
        }
        view.invalidate()
    }

    open fun onTouchEvent(event: MotionEvent): Boolean {
        val action = MotionEventCompat.getActionMasked(event)
        val index = MotionEventCompat.getActionIndex(event)

        val x = view.viewXToDocumentX(event.getX(index))
        val y = view.viewYToDocumentY(event.getY(index))

        if (isEraseMode) {
            erase(x, y)
            return true
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> writeStart(x, y)
            MotionEvent.ACTION_MOVE -> writeTo(x, y)
            MotionEvent.ACTION_UP -> writeDone()
            MotionEvent.ACTION_CANCEL -> writeCancel()
        }

        return true
    }

    open fun zoom() {
        val scale = view.scale
        writingMatrix.apply { reset(); setScale(scale, scale) }
        strokes.forEach { it.path.transform(writingMatrix); it.bounds.scale(scale); it.thickness *= scale }
    }

    open fun drawStrokes(canvas: Canvas) {
        with(view) {
            visibleWindow.set(
                leftEdge.toFloat(),
                topEdge.toFloat(),
                rightEdge.toFloat(),
                bottomEdge.toFloat())
        }

        if (currStroke != null) {
            paint.strokeWidth = currStroke!!.thickness
            paint.color = currStroke!!.color
            canvas.drawPath(currStroke!!.path, paint)
        }

        /* Draw only visible strokes */
        strokes.forEach {
            if (it.isVisible) {
                paint.strokeWidth = it.thickness
                paint.color = it.color
                canvas.drawPath(it.path, paint)
            }
        }
    }

    private fun toViewScale(r: RectF) = if (view.scale == 1f) r else tmpRect.apply {
        left = r.left * view.scale
        top = r.top * view.scale
        right = r.right * view.scale
        bottom = r.bottom * view.scale
    }

    private fun RectF.scale(scale: Float) = apply {
        left *= scale
        top *= scale
        right *= scale
        bottom *= scale
    }

    private val Stroke.isVisible get() = RectF.intersects(visibleWindow, toViewScale(bounds))

    class Stroke(
        val path: Path,
        val color: Int,
        var thickness: Float,
        val bounds: RectF = RectF())

    companion object {
        const val WRITING_TOLERANCE = 4f
        const val ERASER_RADIUS = 8f
    }
}
