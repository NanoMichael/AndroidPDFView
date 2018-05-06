package io.ea.pdf.test

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View

/**
 * Created by nano on 18-3-23.
 */

class RectInvalidateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val v = V(this)
        setContentView(v)
        v.postDelayed({
            v.isInvalidateRect = true
            v.invalidate(500, 500, 800, 800)
        }, 1000L)
    }

    class V(context: Context) : View(context) {

        var isInvalidateRect = false

        private val paint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.RED
        }

        override fun onDraw(canvas: Canvas) {
            if (!isInvalidateRect) canvas.drawRect(20f, 20f, width - 20f, height - 20f, paint.apply { color = Color.RED })
            else canvas.drawRect(500f, 500f, 800f, 800f, paint.apply { color = Color.BLUE })
            isInvalidateRect = false
        }
    }
}