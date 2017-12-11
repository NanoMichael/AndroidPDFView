package io.ea.documentview.pdf.source

import android.content.Context
import android.os.ParcelFileDescriptor
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.File

/**
 * Created by nano on 17-12-4.
 */
class FileSource(val file: File, val pwd: String? = null) : PDFSource {

    override fun createDocument(context: Context, core: PdfiumCore): PdfDocument {
        val f = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return core.newDocument(f, pwd)
    }
}
