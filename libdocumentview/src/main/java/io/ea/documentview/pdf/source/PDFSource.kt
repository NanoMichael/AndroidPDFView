package io.ea.documentview.pdf.source

import android.content.Context
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore

/**
 * Created by nano on 17-12-4.
 */
interface PDFSource {

    fun createDocument(context: Context, core: PdfiumCore): PdfDocument
}