package org.thepalaceproject.palace

import org.joda.time.DateTime
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountProviderFallbackType
import org.nypl.simplified.accounts.api.AccountProviderType
import java.net.URI

/**
 * The fallback account for DPLA.
 */

class PalaceAccountFallback : AccountProviderFallbackType {

  override fun get(): AccountProviderType {
    return AccountProvider(
      addAutomatically = false,
      authenticationDocumentURI = URI.create("https://openbookshelf.dp.la/OB/authentication_document"),
      authentication = AccountProviderAuthenticationDescription.Anonymous,
      authenticationAlternatives = listOf(),
      cardCreatorURI = null,
      catalogURI = URI.create("https://openbookshelf.dp.la/OB/groups/3"),
      displayName = "Digital Public Library of America",
      eula = null,
      // This id is intentionally different than the id for the "real" DPLA library retrieved from
      // the registry, so that it doesn't prevent that library from being added.
      id = URI.create("urn:uuid:e0c621fa-424c-49e6-a6bb-0601def66cf8"),
      idNumeric = -1,
      isProduction = true,
      license = null,
      loansURI = URI.create("https://openbookshelf.dp.la/OB/loans/"),
      logo = URI.create("data:image/png;base64,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"),
      mainColor = "cyan",
      patronSettingsURI = URI.create("https://openbookshelf.dp.la/OB/patrons/me/"),
      privacyPolicy = null,
      subtitle = "Popular books free to download and keep, handpicked by librarians across the US.",
      supportEmail = "mailto:ebooks@dp.la",
      supportsReservations = false,
      updated = DateTime.parse("2021-07-07T23:10:18.238-04:00"),
      location = null
    )
  }
}
