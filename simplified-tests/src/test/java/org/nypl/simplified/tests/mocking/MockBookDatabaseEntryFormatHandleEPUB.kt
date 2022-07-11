package org.nypl.simplified.tests.mocking

import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.book_database.api.BookDRMInformationHandle
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.books.formats.api.StandardFormatNames
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class MockBookDatabaseEntryFormatHandleEPUB(
  val bookID: BookID,
  val directory: File? = null
) : BookDatabaseEntryFormatHandleEPUB() {

  var bookData: String? = null
  private var bookFile: File? = null

  var formatField: BookFormat.BookFormatEPUB =
    BookFormat.BookFormatEPUB(
      drmInformation = BookDRMInformation.None,
      file = this.bookFile,
      lastReadLocation = null,
      bookmarks = listOf(),
      contentType = StandardFormatNames.genericEPUBFiles
    )

  var drmInformationHandleField: BookDRMInformationHandle =
    object : BookDRMInformationHandle.NoneHandle() {
      override val info: BookDRMInformation.None =
        BookDRMInformation.None
    }

  override val format: BookFormat.BookFormatEPUB
    get() = this.formatField

  override fun copyInBook(file: File) {
    this.bookData = file.readText()
    this.bookFile = File(this.directory, "book.epub")

    Files.copy(file.toPath(), this.bookFile!!.toPath(), StandardCopyOption.REPLACE_EXISTING)

    this.formatField = this.formatField.copy(file = this.bookFile)
    check(this.formatField.isDownloaded)
  }

  override fun setLastReadLocation(bookmark: Bookmark.ReaderBookmark?) {
    this.formatField = this.formatField.copy(lastReadLocation = bookmark)
  }

  override fun setBookmarks(bookmarks: List<Bookmark.ReaderBookmark>) {
    this.formatField = this.formatField.copy(bookmarks = bookmarks)
  }

  override val drmInformationHandle: BookDRMInformationHandle
    get() = this.drmInformationHandleField

  override fun setDRMKind(kind: BookDRMKind) {
  }

  override fun deleteBookData() {
    this.bookData = null
    this.bookFile = null
    this.formatField = this.formatField.copy(file = this.bookFile)
  }
}
