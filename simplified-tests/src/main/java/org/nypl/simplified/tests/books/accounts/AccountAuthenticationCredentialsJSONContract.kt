package org.nypl.simplified.tests.books.accounts

import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.nypl.drm.core.AdobeDeviceID
import org.nypl.drm.core.AdobeUserID
import org.nypl.drm.core.AdobeVendorID
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobeClientToken
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobePostActivationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobePreActivationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.accounts.json.AccountAuthenticationCredentialsJSON
import org.nypl.simplified.accounts.json.AccountAuthenticationCredentialsJSON.deserializeFromJSON
import org.nypl.simplified.accounts.json.AccountAuthenticationCredentialsJSON.serializeToJSON
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * @see AccountAuthenticationCredentialsJSON
 */

abstract class AccountAuthenticationCredentialsJSONContract {

  @JvmField
  @Rule
  var expected = ExpectedException.none()

  @Test
  @Throws(Exception::class)
  fun testRoundTrip0() {
    val creds0: AccountAuthenticationCredentials =
      AccountAuthenticationCredentials.Basic(
        userName = AccountUsername("1234"),
        password = AccountPassword("5678"),
        adobeCredentials = null,
        authenticationDescription = null
      )

    val creds1 = deserializeFromJSON(serializeToJSON(creds0))
    Assert.assertEquals(creds0, creds1)
  }

  @Test
  @Throws(Exception::class)
  fun testRoundTrip1() {
    val creds0: AccountAuthenticationCredentials =
      AccountAuthenticationCredentials.OAuthWithIntermediary(
        accessToken = "bm90IGFuIGFjY2VzcyB0b2tlbgo=",
        authenticationDescription = null,
        adobeCredentials = null
      )

    val creds1 = deserializeFromJSON(serializeToJSON(creds0))
    Assert.assertEquals(creds0, creds1)
  }

  @Test
  @Throws(Exception::class)
  fun testRoundTrip2() {
    val adobe =
      AccountAuthenticationAdobePreActivationCredentials(
        vendorID = AdobeVendorID("vendor"),
        clientToken = AccountAuthenticationAdobeClientToken.parse("NYNYPL|156|5e0cdf28-e3a2-11e7-ab18-0e26ed4612aa|LEcBeSV"),
        deviceManagerURI = URI.create("http://example.com"),
        postActivationCredentials = null
      )

    val creds0: AccountAuthenticationCredentials =
      AccountAuthenticationCredentials.Basic(
        userName = AccountUsername("1234"),
        password = AccountPassword("5678"),
        adobeCredentials = adobe,
        authenticationDescription = null
      )

    val creds1 = deserializeFromJSON(serializeToJSON(creds0))
    Assert.assertEquals(creds0, creds1)
  }

  @Test
  @Throws(Exception::class)
  fun testRoundTrip3() {
    val post =
      AccountAuthenticationAdobePostActivationCredentials(
        deviceID = AdobeDeviceID("device"),
        userID = AdobeUserID("user")
      )
    val adobe =
      AccountAuthenticationAdobePreActivationCredentials(
        vendorID = AdobeVendorID("vendor"),
        clientToken = AccountAuthenticationAdobeClientToken.parse("NYNYPL|156|5e0cdf28-e3a2-11e7-ab18-0e26ed4612aa|LEcBeSV"),
        deviceManagerURI = URI.create("http://example.com"),
        postActivationCredentials = post
      )

    val creds0: AccountAuthenticationCredentials =
      AccountAuthenticationCredentials.Basic(
        userName = AccountUsername("1234"),
        password = AccountPassword("5678"),
        adobeCredentials = adobe,
        authenticationDescription = "fake"
      )

    val creds1 = deserializeFromJSON(serializeToJSON(creds0))
    Assert.assertEquals(creds0, creds1)
  }

  companion object {
    private val LOG = LoggerFactory.getLogger(
      AccountAuthenticationCredentialsJSONContract::class.java
    )
  }
}
