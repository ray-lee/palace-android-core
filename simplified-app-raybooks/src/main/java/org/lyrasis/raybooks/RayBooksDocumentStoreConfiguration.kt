package org.lyrasis.raybooks

import org.librarysimplified.documents.DocumentConfiguration
import org.librarysimplified.documents.DocumentConfigurationServiceType
import java.net.URI

class RayBooksDocumentStoreConfiguration : DocumentConfigurationServiceType {

  override val privacyPolicy: DocumentConfiguration? =
    null

  override val about: DocumentConfiguration? =
    DocumentConfiguration(
      name = "about.html",
      remoteURI = URI.create("http://localhost/about.html")
    )

  override val acknowledgements: DocumentConfiguration? =
    null

  override val eula: DocumentConfiguration? =
    DocumentConfiguration(
      "eula.html",
      URI.create("http://localhost/eula.html")
    )

  override val licenses: DocumentConfiguration? =
    DocumentConfiguration(
      "software-licenses.html",
      URI.create("http://localhost/software-licenses.html")
    )

  override val faq: DocumentConfiguration? =
    null
}
