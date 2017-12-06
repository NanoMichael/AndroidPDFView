package io.ea.documentview.pdf

import android.content.Context
import android.graphics.*
import android.os.Build
import android.support.v4.view.MotionEventCompat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent

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

    private val eraserRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
        ERASER_RADIUS, context.resources.displayMetrics)
    private val tmpPath = Path()
    private val erasePath = Path()

    /** If support eraser */
    val isSupportEraser = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

    /** Whether writing is enabled, when is true, scroll and scale are disabled */
    var isWritingEnabled = false
        set(value) {
            field = value
            isGesturesEnabled = !value
        }

    /** Whether is in erase mode, default is `false` */
    var isEraseMode = false
        set(value) {
            field = if (!isSupportEraser) false else value
        }

    /** Current writing color */
    var writingColor = Color.BLACK

    /** Current writing thickness */
    var writingThickness = 2f

    /** On writing callback */
    var onWriting: () -> Unit = {}

    private var lastX = 0f
    private var lastY = 0f

    /** Clear writing */
    fun clearWriting() {
        strokes.clear()
        invalidate()
    }

    /** Retreat writing, back to previous */
    fun retreatWriting() {
        if (strokes.isEmpty()) return
        strokes.removeAt(strokes.size - 1)
        invalidate()
    }

    private fun writingStart(x: Float, y: Float) {
        val stroke = Stroke(Path(), writingColor, writingThickness)
        stroke.path.reset()
        stroke.path.moveTo(x, y)
        lastX = x
        lastY = y
        currStroke = stroke
        onWriting()
    }

    private fun writingTo(x: Float, y: Float) {
        val stroke = currStroke ?: return
        val dx = Math.abs(x - lastX)
        val dy = Math.abs(y - lastY)
        if (dx >= WRITING_TOLERANCE || dy >= WRITING_TOLERANCE) {
            stroke.path.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
            lastX = x
            lastY = y
            invalidate()
        }
        onWriting()
    }

    private fun writingDone() {
        val stroke = currStroke ?: return
        if (stroke.path.isEmpty) {
            currStroke = null
            return
        }
        stroke.apply { path.lineTo(lastX, lastY); path.computeBounds(bounds, false) }
        strokes.add(stroke)
        currStroke = null
        invalidate()
    }

    private fun writingCancel() {
        currStroke = null
        invalidate()
    }

    private fun erase(x: Float, y: Float) {
        if (!isSupportEraser) return
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
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isWritingEnabled) return super.onTouchEvent(event)

        val action = MotionEventCompat.getActionMasked(event)
        val index = MotionEventCompat.getActionIndex(event)

        val x = viewXToDocumentX(event.getX(index))
        val y = viewYToDocumentY(event.getY(index))

        if (isEraseMode) {
            erase(x, y)
            return true
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> writingStart(x, y)
            MotionEvent.ACTION_MOVE -> writingTo(x, y)
            MotionEvent.ACTION_UP -> writingDone()
            MotionEvent.ACTION_CANCEL -> writingCancel()
        }

        return true
    }

    override fun afterDrawSlices(canvas: Canvas) {
        drawStrokes(canvas)
        super.afterDrawSlices(canvas)
    }

    override fun onZoomEnd() {
        writingMatrix.apply { reset(); setScale(scale, scale) }
        strokes.forEach { it.path.transform(writingMatrix); it.bounds.scale(scale); it.thickness *= scale }
        super.onZoomEnd()
    }

    /** Draw only visible strokes */
    private fun drawStrokes(canvas: Canvas) {
        visibleWindow.set(
            leftEdge.toFloat(),
            topEdge.toFloat(),
            rightEdge.toFloat(),
            bottomEdge.toFloat())

        if (currStroke != null) {
            paint.strokeWidth = currStroke!!.thickness
            paint.color = currStroke!!.color
            canvas.drawPath(currStroke!!.path, paint)
        }

        strokes.forEach {
            if (it.isVisible) {
                paint.strokeWidth = it.thickness
                paint.color = it.color
                canvas.drawPath(it.path, paint)
            }
        }
    }

    private fun toViewScale(r: RectF) = if (scale == 1f) r else tmpRect.apply {
        left = r.left * scale
        top = r.top * scale
        right = r.right * scale
        bottom = r.bottom * scale
    }

    private fun RectF.scale(scale: Float) = apply {
        left *= scale
        top *= scale
        right *= scale
        bottom *= scale
    }

    private val Stroke.isVisible get() = RectF.intersects(visibleWindow, toViewScale(bounds))

    private class Stroke(
        val path: Path,
        val color: Int,
        var thickness: Float,
        val bounds: RectF = RectF())

    companion object {
        const val WRITING_TOLERANCE = 4f
        const val ERASER_RADIUS = 4f
    }
}
