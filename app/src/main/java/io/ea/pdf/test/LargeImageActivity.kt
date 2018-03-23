package io.ea.pdf.test

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import io.ea.documentview.image.FileSource
import io.ea.documentview.image.HugeImageView

/**
 * Created by nano on 18-3-21.
 */

class LargeImageActivity : AppCompatActivity() {

    private lateinit var image: HugeImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_large_image)

        image = findViewById<HugeImageView>(R.id.image).apply {
        }
        image.load(FileSource("/sdcard/large_image.jpg"))

        /*val iv1 = findViewById<ImageView>(R.id.image1)
        val iv2 = findViewById<ImageView>(R.id.image2)

        val decoder = BitmapRegionDecoder.newInstance("/sdcard/large_image.jpg", true)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 6
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        val b1 = decoder.decodeRegion(Rect(0, 0, 2000, 2000), options)
        val b2 = decoder.decodeRegion(Rect(0, 2000, 2000, 4000), options)

        Log.e("abc", "size = ${b1.width}")

        iv1.setImageBitmap(b1)
        iv2.setImageBitmap(b2)*/
    }
}