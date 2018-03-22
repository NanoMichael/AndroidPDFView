package io.ea.pdf.test

import android.graphics.*
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.ImageView

/**
 * Created by nano on 18-3-21.
 */

class LargeImageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_large_image)

        val decoder = BitmapRegionDecoder.newInstance("/sdcard/large_image.jpg", false)
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.RGB_565)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val b = decoder.decodeRegion(Rect(0, 0, 500, 500), options)
        Log.e("abc", "${bitmap === b}")
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(b, Matrix().apply { setScale(2f, 2f) }, Paint())
        findViewById<ImageView>(R.id.image).setImageBitmap(bitmap)
    }
}