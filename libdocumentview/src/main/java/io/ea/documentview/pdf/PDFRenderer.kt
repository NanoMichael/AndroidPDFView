package io.ea.documentview.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import io.ea.documentview.Size
import io.ea.documentview.rendering.Renderer

/**
 * Created by nano on 17-11-29.
 */
class PDFRenderer(private val context: Context, private val src: PDFSource) : Renderer {

    private val lock = Any()

    private val core = PdfiumCore(context)
    private var document: PdfDocument? = null

    /** Size of all pages with current DPI */
    override var pagesSize = listOf<Size>()
        private set

    /**
     * Any call of methods of this class will throw an [IllegalStateException]
     * if document was not opened, you should check this value before.
     */
    @Volatile
    var isOpened = false
        private set

    /** Specifies whether render annotation, default is `false` */
    @Volatile
    var isRenderAnnotation = false

    private val openedPages = hashSetOf<Int>()
    private val errPages = hashSetOf<Int>()
    private var pagesLinks = arrayOf<List<PdfDocument.Link>?>()

    override fun open(onErr: (Throwable) -> Unit) = try {
        val document = src.createDocument(context, core)

        val pagesSize = ArrayList<Size>(core.getPageCount(document))
        (0 until core.getPageCount(document))
            .map { core.getPageSize(document, it) }
            .mapTo(pagesSize) { Size(it.width, it.height) }

        this.document = document
        this.pagesSize = pagesSize
        this.pagesLinks = arrayOfNulls(core.getPageCount(document))

        openedPages.clear()
        errPages.clear()
        isOpened = true

        true
    } catch (t: Throwable) {
        onErr(t)
        false
    }

    /** Check if document is opened */
    private fun checkDocument() = check(document != null) { "Document not opened, open first" }

    /** Open page with callback [onErr] */
    private fun openPage(page: Int, onErr: (Int, Throwable) -> Unit) = synchronized(lock) {
        checkDocument()
        if (openedPages.contains(page)) return@synchronized true
        if (errPages.contains(page)) return@synchronized false
        try {
            core.openPage(document, page)
            openedPages.add(page)
            true
        } catch (t: Throwable) {
            errPages.add(page)
            onErr(page, t)
            false
        }
    }

    /** Render page fragment into [bitmap] with offset ([left], [top]) */
    fun renderPageClip(
        bitmap: Bitmap, page: Int, left: Int, top: Int, scale: Float,
        onErr: (Int, Throwable) -> Unit): Boolean {

        if (!openPage(page, onErr)) return false

        val w = (pagesSize[page].width * scale).toInt()
        val h = (pagesSize[page].height * scale).toInt()

        core.renderPageBitmap(document, bitmap, page, -left, -top, w, h, isRenderAnnotation)
        return true
    }

    override fun renderPageClip(
        bitmap: Bitmap, page: Int, scale: Float, region: Rect,
        onErr: (Int, Throwable) -> Unit) =
        renderPageClip(bitmap, page, region.left, region.top, scale, onErr)

    /** Get page count of document, return 0 if document not opened */
    val pageCount get() = pagesSize.size

    /** Meta data of document */
    val metaData: PdfDocument.Meta? by lazy { checkDocument(); core.getDocumentMeta(document) }

    /** Table of contents of document */
    val tableOfContents: MutableList<PdfDocument.Bookmark>? by lazy { checkDocument(); core.getTableOfContents(document) }

    /** Peek for page links, return null if not fetched */
    fun peekPageLinks(page: Int) = pagesLinks[page]

    /** Get links in [page] */
    fun getPageLinks(page: Int): List<PdfDocument.Link> {
        checkDocument()
        /** Sometimes [NullPointerException] may happen */
        return try {
            pagesLinks[page] ?: core.getPageLinks(document, page).also { pagesLinks[page] = it }
        } catch (e: Throwable) {
            listOf()
        }
    }

    /** Get scaled coordinates in page */
    fun getScaledPageCoords(page: Int, scale: Float, x: Float, y: Float): Point {
        checkDocument()
        val w = (pagesSize[page].width * scale).toInt()
        val h = (pagesSize[page].height * scale).toInt()
        return core.mapPageCoordsToDevice(document, page, 0, 0, w, h, 0, x.toDouble(), y.toDouble())
    }

    /** Close opened document */
    override fun release() {
        if (document != null) core.closeDocument(document)
        document = null
        isOpened = false
    }
}
