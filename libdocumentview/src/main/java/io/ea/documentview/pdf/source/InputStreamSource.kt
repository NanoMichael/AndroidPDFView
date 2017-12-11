package io.ea.documentview.pdf.source

import android.content.Context
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Created by nano on 17-12-4.
 */
class InputStreamSource(val input: InputStream, val pwd: String? = null) : PDFSource {

    override fun createDocument(context: Context, core: PdfiumCore): PdfDocument {
        val os = ByteArrayOutputStream()
        val buffer = ByteArray(1024 * 4)
        var len: Int
        do {
            len = input.read(buffer)
            if (len != -1) os.write(buffer, 0, len)
        } while (len != -1)
        return core.newDocument(os.toByteArray(), pwd)
    }
}
