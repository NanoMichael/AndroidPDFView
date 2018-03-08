package io.ea.pdf.test

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.ViewTreeObserver
import io.ea.documentview.DefaultAdapterConfig
import io.ea.documentview.DocumentAdapter
import io.ea.documentview.DocumentView
import io.ea.documentview.Size

/**
 * Created by nano on 17-11-24.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val view = findViewById<DocumentView>(R.id.slice_view)
        view.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {

            override fun onGlobalLayout() {
                view.viewTreeObserver.removeGlobalOnLayoutListener(this)
                val sizes = List(10) { if (it % 2 == 0) Size(520, 720) else Size(480, 640) }

                val config = DefaultAdapterConfig(24)
                val adapter = Adapter(sizes)

                config.update(view.width, view.height, adapter)
                adapter.config = config
                adapter.setup()

                view.adapter = adapter
            }
        })

        findViewById<View>(R.id.to_pdf).setOnClickListener {
            startActivity(Intent(this, PDFActivity::class.java))
        }
    }

    private val paint = Paint().apply { textSize = 24f; isAntiAlias = true }

    inner class TestGrid : DocumentAdapter.Grid() {

        override fun onBind(view: DocumentView) {
        }

        override fun draw(canvas: Canvas, slice: DocumentView.Slice) {
            val bounds = slice.bounds
            paint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            canvas.drawRect(slice.bounds, paint)
            paint.apply { color = Color.RED }
            canvas.drawText("${slice.row}, ${slice.col}", bounds.left + 32f, bounds.top + 32f, paint)
            paint.apply { color = Color.RED; style = Paint.Style.STROKE }
            canvas.drawRect(bounds, paint)
        }

        override fun onRecycle() {
        }
    }

    inner class Adapter(pagesSize: List<Size>) :
        DocumentAdapter(pagesSize, 480, 480) {

        override fun newGrid() = TestGrid()
    }
}
