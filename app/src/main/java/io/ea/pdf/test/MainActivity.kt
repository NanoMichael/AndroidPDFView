package io.ea.pdf.test

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View

/**
 * Created by nano on 18-3-21.
 */

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.to_document).setOnClickListener {
            startActivity(Intent(this, DocumentActivity::class.java))
        }

        findViewById<View>(R.id.to_pdf).setOnClickListener {
            startActivity(Intent(this, PDFActivity::class.java))
        }

        findViewById<View>(R.id.to_large_image).setOnClickListener {
            startActivity(Intent(this, RectInvalidateActivity::class.java))
        }
    }
}
