package org.nypl.simplified.viewer.pdf

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import edu.umn.minitex.pdf.android.api.PdfFragmentListenerType
import edu.umn.minitex.pdf.android.api.TableOfContentsFragmentListenerType
import edu.umn.minitex.pdf.android.api.TableOfContentsItem
import kotlinx.coroutines.runBlocking
import org.librarysimplified.services.api.Services
import org.nypl.drm.core.ContentProtectionProvider
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.BookContentProtections
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryType
import org.nypl.simplified.books.book_database.api.BookDatabaseType
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.nypl.simplified.viewer.pdf.server.PdfServer
import org.readium.r2.shared.fetcher.Resource
import org.readium.r2.shared.fetcher.ResourceInputStream
import org.readium.r2.shared.publication.asset.FileAsset
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.publication.services.protectionError
import org.readium.r2.shared.util.getOrDefault
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.logging.ConsoleWarningLogger
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.Streamer
import org.readium.r2.streamer.parser.readium.ReadiumWebPubParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.*


class PdfReaderActivity :
  AppCompatActivity(),
  PdfFragmentListenerType,
  TableOfContentsFragmentListenerType {

  companion object {
    const val TABLE_OF_CONTENTS = "table_of_contents"

    private const val PARAMS_ID = "edu.umn.minitex.pdf.android.pdfreader.PdfReaderActivity.params"

    /**
     * Factory method to start a [PdfReaderActivity]
     */
    fun startActivity(
      from: Activity,
      parameters: PdfReaderParameters
    ) {
      val b = Bundle()
      b.putSerializable(PARAMS_ID, parameters)
      val i = Intent(from, PdfReaderActivity::class.java)
      i.putExtras(b)
      from.startActivity(i)
    }
  }

  private val log: Logger = LoggerFactory.getLogger(PdfReaderActivity::class.java)

  // vars assigned in onCreate and passed with the intent
  private lateinit var documentTitle: String
  private lateinit var drmInfo: BookDRMInformation
  private lateinit var pdfFile: File
  private lateinit var accountId: AccountID
  private lateinit var id: BookID
  private lateinit var currentProfile: ProfileReadableType
  private lateinit var account: AccountType
  private lateinit var books: BookDatabaseType
  private lateinit var entry: BookDatabaseEntryType
  private lateinit var handle: BookDatabaseEntryFormatHandlePDF
  private lateinit var uiThread: UIThreadServiceType
  private lateinit var pdfReaderContainer: FrameLayout
  private lateinit var webView: WebView

  private var server: PdfServer? = null

  // vars for the activity to pass back to the reader or table of contents fragment
  private var documentPageIndex: Int = 0
  private var tableOfContentsList: ArrayList<TableOfContentsItem> = arrayListOf()

  private val contentProtectionProviders =
    ServiceLoader.load(ContentProtectionProvider::class.java).toList()

  override fun onCreate(savedInstanceState: Bundle?) {
    log.debug("onCreate")
    super.onCreate(savedInstanceState)
    setContentView(R.layout.pdf_reader)

    val intentParams = intent?.getSerializableExtra(PARAMS_ID) as PdfReaderParameters
    this.documentTitle = intentParams.documentTile
    this.drmInfo = intentParams.drmInfo
    this.pdfFile = intentParams.pdfFile
    this.accountId = intentParams.accountId
    this.id = intentParams.id

    val services =
      Services.serviceDirectory()

    this.uiThread =
      services.requireService(UIThreadServiceType::class.java)

    this.currentProfile =
      services.requireService(ProfilesControllerType::class.java).profileCurrent()
    this.account = currentProfile.account(accountId)
    this.books = account.bookDatabase

    val toolbar = this.findViewById(R.id.pdf_toolbar) as Toolbar
    this.setSupportActionBar(toolbar)
    this.supportActionBar?.setDisplayHomeAsUpEnabled(true)
    this.supportActionBar?.setDisplayShowHomeEnabled(true)
    this.supportActionBar?.title = this.documentTitle

    try {
      this.entry = books.entry(id)
      this.handle = entry.findFormatHandle(BookDatabaseEntryFormatHandlePDF::class.java)!!
      this.documentPageIndex = handle.format.lastReadLocation!!
    } catch (e: Exception) {
      log.error("Could not get lastReadLocation, defaulting to the 1st page", e)
    }

    if (savedInstanceState == null) {
      // Get the new instance of the reader you want to load here.
//      val readerFragment = PdfViewerFragment.newInstance()
//
//      this.supportFragmentManager
//        .beginTransaction()
//        .replace(R.id.pdf_reader_fragment_holder, readerFragment, "READER")
//        .commit()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        WebView.setWebContentsDebuggingEnabled(true);
      }

      this.pdfReaderContainer = findViewById(R.id.pdf_reader_container)
      this.webView = WebView(this)

//      val webView: WebView = findViewById(R.id.readerWebView)
      val webSettings = webView.settings

      webSettings.javaScriptEnabled = true

      this.pdfReaderContainer.addView(this.webView)

//      val pdfPathHandler = PDFPathHandler(
//        context = this,
//        contentProtectionProviders = this.contentProtectionProviders,
//        drmInfo = this.drmInfo,
//        pdfFile = this.pdfFile
//      )
//
//      val assetLoader = WebViewAssetLoader.Builder()
//        .addPathHandler(
//          "/pdf/",
//          pdfPathHandler
//        )
//        .addPathHandler(
//          "/assets/",
//          WebViewAssetLoader.AssetsPathHandler(this)
//        )
//        .build()
//
//      webView.webViewClient = LocalContentWebViewClient(assetLoader, pdfPathHandler)

      try {
        this.server = PdfServer(
          port = 7671,
          context = this,
          contentProtectionProviders = this.contentProtectionProviders,
          drmInfo = this.drmInfo,
          pdfFile = this.pdfFile
        )
      } catch (exception: Exception) {
        showErrorWithRunnable(
          context = this,
          title = exception.message ?: "",
          failure = exception,
          execute = this::finish
        )
      }

      this.server?.let {
        it.start()

        webView.loadUrl("http://localhost:7671/assets/viewer.html")
      }
    } else {
      this.tableOfContentsList =
        savedInstanceState.getParcelableArrayList(TABLE_OF_CONTENTS) ?: arrayListOf()
    }
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    val inflater: MenuInflater = menuInflater
    inflater.inflate(R.menu.pdf_reader_menu, menu)

    menu?.findItem(R.id.readerMenuTOC)?.setOnMenuItemClickListener {
      this.onReaderMenuTOCSelected()
    }

    menu?.findItem(R.id.readerMenuSettings)?.setOnMenuItemClickListener {
      this.onReaderMenuSettingsSelected()
    }
    return true
  }

  private fun onReaderMenuTOCSelected(): Boolean {
    this.webView.evaluateJavascript("toggleSidebar()", null)

    return true
  }

  private fun onReaderMenuSettingsSelected(): Boolean {
    this.webView.evaluateJavascript("toggleSecondaryToolbar()", null)

    return true
  }

  override fun onDestroy() {
    super.onDestroy()

    this.server?.stop()
    this.pdfReaderContainer.removeAllViews()
    this.webView.destroy()
  }

  private class LocalContentWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val pdfPathHandler: PDFPathHandler
  ) : WebViewClientCompat() {
    @RequiresApi(21)
    override fun shouldInterceptRequest(
      view: WebView,
      request: WebResourceRequest
    ): WebResourceResponse? {
      // TODO: Check path
      val range = request.requestHeaders.get("range")
      val url = request.url

      return if (range == null) {
        assetLoader.shouldInterceptRequest(request.url)
      } else {
        pdfPathHandler.handle(request.url.toString(), range)
      }
    }

    // to support API < 21
    override fun shouldInterceptRequest(
      view: WebView,
      url: String
    ): WebResourceResponse? {
      return assetLoader.shouldInterceptRequest(Uri.parse(url))
    }
  }

  private class PDFPathHandler(
    context: Context,
    contentProtectionProviders: List<ContentProtectionProvider>,
    drmInfo: BookDRMInformation,
    pdfFile: File
  ) : WebViewAssetLoader.PathHandler {
    private lateinit var resource: Resource

    init {
      val streamer = Streamer(
        context = context,
        parsers = listOf(
          ReadiumWebPubParser(
            httpClient = DefaultHttpClient(),
            pdfFactory = null
          )
        ),
        contentProtections = BookContentProtections.create(
          context = context,
          contentProtectionProviders = contentProtectionProviders,
          drmInfo = drmInfo
        ),
        ignoreDefaultParsers = true
      )

      val publication = runBlocking {
        streamer.open(
          asset = FileAsset(pdfFile, MediaType.LCP_PROTECTED_PDF),
          allowUserInteraction = false,
          warnings = ConsoleWarningLogger()
        )
      }.getOrElse {
        throw IOException("Failed to open PDF", it)
      }

      if (publication.isRestricted) {
        throw IOException("Failed to unlock PDF", publication.protectionError)
      }

      // We only support a single PDF file in the archive.
      val link = publication.readingOrder.first()

      this.resource = publication.get(link)
    }

    override fun handle(path: String): WebResourceResponse? {
      val total = runBlocking {
        resource.length()
      }.getOrDefault(0L)

      return WebResourceResponse(
        "application/pdf",
        "",
        200,
        "OK",
        mapOf(
          "Accept-Ranges" to "bytes"
        ),
        ResourceInputStream(this.resource).buffered(256 * 1024)
      )
    }

    fun handle(path: String, range: String): WebResourceResponse {
      val resource = this.resource
      val longRange = parseRange(range)

      val bytes: ByteArray = runBlocking {
        resource.read(longRange)
      }.getOrDefault(ByteArray(0))

      val start = longRange.start
      val end = longRange.endInclusive

      val total = runBlocking {
        resource.length()
      }.getOrDefault(0L)

      return WebResourceResponse(
        "application/pdf",
        "",
        206,
        "Partial Content",
        mapOf(
          "Accept-Ranges" to "bytes",
          "Content-Range" to "bytes $start-$end/$total"
        ),
        ByteArrayInputStream(bytes)
      )
    }

    fun parseRange(range: String): LongRange {
      val (start, end) = range.trim().substringAfter("bytes=").split("-").map { it.toLong() }

      return LongRange(start, end)
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      onBackPressed()
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    log.debug("onSaveInstanceState")
    outState.putParcelableArrayList(TABLE_OF_CONTENTS, tableOfContentsList)
    super.onSaveInstanceState(outState)
  }

  //region [PdfFragmentListenerType]
  override fun onReaderWantsInputStream(): InputStream {
    log.debug("onReaderWantsInputStream")

    return when (this.drmInfo.kind) {
      BookDRMKind.LCP ->
        try {
          this.lcpInputStream()
        } catch (exception: Exception) {
          showErrorWithRunnable(
            context = this,
            title = exception.message ?: "",
            failure = exception,
            execute = this::finish
          )

          // Return a dummy stream in case the PDF couldn't be opened or decrypted.
          ByteArrayInputStream(ByteArray(0))
        }
      else ->
        pdfFile.inputStream()
    }
  }

  private fun lcpInputStream(): InputStream {
    val streamer = Streamer(
      context = this,
      parsers = listOf(
        ReadiumWebPubParser(
          httpClient = DefaultHttpClient(),
          pdfFactory = null
        )
      ),
      contentProtections = BookContentProtections.create(
        context = this,
        contentProtectionProviders = this.contentProtectionProviders,
        drmInfo = this.drmInfo
      ),
      ignoreDefaultParsers = true
    )

    val publication = runBlocking {
      streamer.open(
        asset = FileAsset(pdfFile, MediaType.LCP_PROTECTED_PDF),
        allowUserInteraction = false,
        warnings = ConsoleWarningLogger()
      )
    }.getOrElse {
      throw IOException("Failed to open PDF", it)
    }

    if (publication.isRestricted) {
      throw IOException("Failed to unlock PDF", publication.protectionError)
    }

    // We only support a single PDF file in the archive.
    val link = publication.readingOrder.first()

    return ResourceInputStream(publication.get(link)).buffered(256 * 1024)
  }

  override fun onReaderWantsTitle(): String {
    log.debug("onReaderWantsTitle")
    return this.documentTitle
  }

  override fun onReaderWantsCurrentPage(): Int {
    log.debug("onReaderWantsCurrentPage")
    return this.documentPageIndex
  }

  override fun onReaderPageChanged(pageIndex: Int) {
    log.debug("onReaderPageChanged")
    this.documentPageIndex = pageIndex
    handle.setLastReadLocation(pageIndex)
  }

  override fun onReaderLoadedTableOfContents(tableOfContentsList: ArrayList<TableOfContentsItem>) {
    log.debug("onReaderLoadedTableOfContents. tableOfContentsList: $tableOfContentsList")
    this.tableOfContentsList = tableOfContentsList
  }

  override fun onReaderWantsTableOfContentsFragment() {
    log.debug("onReaderWantsTableOfContentsFragment")

    // Get the new instance of the [TableOfContentsFragment] you want to load here.
//    val readerFragment = TableOfContentsFragment.newInstance()

//    this.supportFragmentManager
//      .beginTransaction()
//      .replace(R.id.pdf_reader_fragment_holder, readerFragment, "READER")
//      .addToBackStack(null)
//      .commit()
  }
  //endregion

  //region [TableOfContentsFragmentListenerType]
  override fun onTableOfContentsWantsItems(): ArrayList<TableOfContentsItem> {
    log.debug("onTableOfContentsWantsItems")
    return this.tableOfContentsList
  }

  override fun onTableOfContentsWantsTitle(): String {
    log.debug("onTableOfContentsWantsTitle")
    return getString(R.string.table_of_contents_title)
  }

  override fun onTableOfContentsWantsEmptyDataText(): String {
    log.debug("onTableOfContentsWantsEmptyDataText")
    return getString(R.string.table_of_contents_empty_message)
  }

  override fun onTableOfContentsItemSelected(pageSelected: Int) {
    log.debug("onTableOfContentsItemSelected. pageSelected: $pageSelected")

    // the reader fragment should be on the backstack and will ask for the page index when `onResume` is called
    this.documentPageIndex = pageSelected
    onBackPressed()
  }
  //endregion

  private fun showErrorWithRunnable(
    context: Context,
    title: String,
    failure: Exception,
    execute: () -> Unit
  ) {
    this.log.error("error: {}: ", title, failure)

    this.uiThread.runOnUIThread {
      AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(failure.localizedMessage)
        .setOnDismissListener {
          execute.invoke()
        }
        .show()
    }
  }
}
