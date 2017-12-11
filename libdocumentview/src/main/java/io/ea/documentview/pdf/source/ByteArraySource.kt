package io.ea.documentview.pdf.source

import android.content.Context
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore

/**
 * Created by nano on 17-12-4.
 */
class ByteArraySource(val data: ByteArray, val pwd: String? = null) : PDFSource {

    override fun createDocument(context: Context, core: PdfiumCore): PdfDocument =
        core.newDocument(data, pwd)
}
