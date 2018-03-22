package io.ea.documentview.pdf

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Created by nano on 18-3-22.
 */

interface PDFSource {

    fun createDocument(context: Context, core: PdfiumCore): PdfDocument
}


class ByteArraySource(val data: ByteArray, val pwd: String? = null) : PDFSource {

    override fun createDocument(context: Context, core: PdfiumCore): PdfDocument =
        core.newDocument(data, pwd)
}


class FileSource(val file: File, val pwd: String? = null) : PDFSource {

    override fun createDocument(context: Context, core: PdfiumCore): PdfDocument {
        val f = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return core.newDocument(f, pwd)
    }
}


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


class UriSource(val uri: Uri, val pwd: String? = null) : PDFSource {

    override fun createDocument(context: Context, core: PdfiumCore): PdfDocument {
        val f = context.contentResolver.openFileDescriptor(uri, "r")
        return core.newDocument(f, pwd)
    }
}