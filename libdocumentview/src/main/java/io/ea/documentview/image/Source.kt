package io.ea.documentview.image

import android.content.Context
import android.graphics.BitmapRegionDecoder
import android.net.Uri
import java.io.File
import java.io.InputStream

/**
 * Created by nano on 18-3-22.
 *
 * Image source
 */


interface ImageSource {

    fun createDecoder(): BitmapRegionDecoder
}


class InputStreamSource(private val stream: InputStream) : ImageSource {

    override fun createDecoder(): BitmapRegionDecoder =
        BitmapRegionDecoder.newInstance(stream, true)
}


class FileSource : ImageSource {

    constructor(file: File) {
        path = file.absolutePath
    }

    constructor(path: String) {
        this.path = path
    }

    private val path: String

    override fun createDecoder(): BitmapRegionDecoder =
        BitmapRegionDecoder.newInstance(path, true)
}


class ByteArraySource(val bytes: ByteArray, val offset: Int = 0, val length: Int = bytes.size) : ImageSource {

    override fun createDecoder(): BitmapRegionDecoder =
        BitmapRegionDecoder.newInstance(bytes, offset, length, true)
}


class UriSource(val context: Context, val uri: Uri) : ImageSource {

    override fun createDecoder(): BitmapRegionDecoder {
        val f = context.contentResolver.openFileDescriptor(uri, "r").fileDescriptor
        return BitmapRegionDecoder.newInstance(f, true)
    }
}
