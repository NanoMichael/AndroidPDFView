package io.ea.documentview.pdf.source

import android.content.Context
import android.net.Uri
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore

/**
 * Created by nano on 17-12-4.
 */
class UriSource(private val uri: Uri, private val pwd: String? = null) : PDFSource {

    override fun createDocument(context: Context, core: PdfiumCore): PdfDocument {
        val f = context.contentResolver.openFileDescriptor(uri, "r")
        return core.newDocument(f, pwd)
    }
}
