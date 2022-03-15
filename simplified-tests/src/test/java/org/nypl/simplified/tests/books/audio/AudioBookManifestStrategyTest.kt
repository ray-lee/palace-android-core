package org.nypl.simplified.tests.books.audio

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest_fulfill.api.ManifestFulfillmentStrategyRegistryType
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicParameters
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicType
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentErrorType
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyType
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsersType
import org.mockito.Mockito
import org.nypl.simplified.books.audio.AudioBookManifestRequest
import org.nypl.simplified.books.audio.UnpackagedAudioBookManifestStrategy
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.tests.MutableServiceDirectory
import org.nypl.simplified.tests.TestDirectories
import org.slf4j.LoggerFactory
import rx.Observable
import java.io.File
import java.net.URI

class AudioBookManifestStrategyTest {

  private val logger =
    LoggerFactory.getLogger(AudioBookManifestStrategyTest::class.java)

  private lateinit var basicStrategies: ManifestFulfillmentBasicType
  private lateinit var basicStrategy: ManifestFulfillmentStrategyType
  private lateinit var fulfillError: ManifestFulfillmentErrorType
  private lateinit var manifestParsers: ManifestParsersType
  private lateinit var services: MutableServiceDirectory
  private lateinit var strategies: ManifestFulfillmentStrategyRegistryType
  private lateinit var tempFolder: File

  @BeforeEach
  fun testSetup() {
    this.basicStrategy =
      Mockito.mock(ManifestFulfillmentStrategyType::class.java)
    this.basicStrategies =
      Mockito.mock(ManifestFulfillmentBasicType::class.java)
    this.strategies =
      Mockito.mock(ManifestFulfillmentStrategyRegistryType::class.java)
    this.manifestParsers =
      Mockito.mock(ManifestParsersType::class.java)

    this.tempFolder =
      TestDirectories.temporaryDirectory()

    this.fulfillError =
      object : ManifestFulfillmentErrorType {
        override val message: String = "Download failed!"
        override val serverData: ManifestFulfillmentErrorType.ServerData? = null
      }

    this.services = MutableServiceDirectory()
    this.services.putService(ManifestFulfillmentStrategyRegistryType::class.java, this.strategies)
  }

  @Test
  fun testNoBasicStrategyAvailable() {
    val strategy =
      UnpackagedAudioBookManifestStrategy(
        AudioBookManifestRequest(
          targetURI = URI.create("http://www.example.com"),
          contentType = BookFormats.audioBookGenericMimeTypes().first(),
          userAgent = PlayerUserAgent("test"),
          credentials = null,
          services = this.services,
          isNetworkAvailable = { true },
          strategyRegistry = this.strategies,
          cacheDirectory = File(tempFolder, "cache")
        )
      )

    val failure = strategy.execute() as TaskResult.Failure
    Assertions.assertEquals(UnsupportedOperationException::class.java, failure.resolutionOf(0).exception?.javaClass)
  }

  @Test
  fun testNoBasicStrategyFails() {
    Mockito.`when`(
      this.strategies.findStrategy(
        this.any((ManifestFulfillmentBasicType::class.java)::class.java)
      )
    ).thenReturn(this.basicStrategies)

    Mockito.`when`(
      this.basicStrategies.create(
        this.any(ManifestFulfillmentBasicParameters::class.java)
      )
    ).thenReturn(this.basicStrategy)

    Mockito.`when`(this.basicStrategy.events)
      .thenReturn(Observable.never())

    val fulfillmentResult =
      PlayerResult.Failure<ManifestFulfilled, ManifestFulfillmentErrorType>(this.fulfillError)

    Mockito.`when`(this.basicStrategy.execute())
      .thenReturn(fulfillmentResult)

    val strategy =
      UnpackagedAudioBookManifestStrategy(
        AudioBookManifestRequest(
          targetURI = URI.create("http://www.example.com"),
          contentType = BookFormats.audioBookGenericMimeTypes().first(),
          userAgent = PlayerUserAgent("test"),
          credentials = null,
          services = this.services,
          isNetworkAvailable = { true },
          strategyRegistry = this.strategies,
          cacheDirectory = File(tempFolder, "cache")
        )
      )

    val failure = strategy.execute() as TaskResult.Failure

    Assertions.assertEquals("Download failed!", failure.resolutionOf(0).message)
  }

  @Test
  fun testNoBasicStrategyParseFails() {
    Mockito.`when`(
      this.strategies.findStrategy(
        this.any((ManifestFulfillmentBasicType::class.java)::class.java)
      )
    ).thenReturn(this.basicStrategies)

    Mockito.`when`(
      this.basicStrategies.create(
        this.any(ManifestFulfillmentBasicParameters::class.java)
      )
    ).thenReturn(this.basicStrategy)

    Mockito.`when`(this.basicStrategy.events)
      .thenReturn(Observable.never())

    val fulfillmentResult =
      PlayerResult.Success<ManifestFulfilled, ManifestFulfillmentErrorType>(
        ManifestFulfilled(
          BookFormats.audioBookGenericMimeTypes().first(),
          ByteArray(23)
        )
      )

    Mockito.`when`(this.basicStrategy.execute())
      .thenReturn(fulfillmentResult)

    val strategy =
      UnpackagedAudioBookManifestStrategy(
        AudioBookManifestRequest(
          targetURI = URI.create("http://www.example.com"),
          contentType = BookFormats.audioBookGenericMimeTypes().first(),
          userAgent = PlayerUserAgent("test"),
          credentials = null,
          services = this.services,
          isNetworkAvailable = { true },
          strategyRegistry = this.strategies,
          manifestParsers = AudioBookFailingParsers,
          extensions = emptyList(),
          cacheDirectory = File(tempFolder, "cache")
        )
      )

    val failure = strategy.execute() as TaskResult.Failure
    Assertions.assertTrue(
      failure.resolutionOf(1).message.startsWith("Manifest parsing failed")
    )
  }

  @Test
  fun testNoBasicStrategyLicenseCheckFails() {
    Mockito.`when`(
      this.strategies.findStrategy(
        this.any((ManifestFulfillmentBasicType::class.java)::class.java)
      )
    ).thenReturn(this.basicStrategies)

    Mockito.`when`(
      this.basicStrategies.create(
        this.any(ManifestFulfillmentBasicParameters::class.java)
      )
    ).thenReturn(this.basicStrategy)

    Mockito.`when`(this.basicStrategy.events)
      .thenReturn(Observable.never())

    val fulfillmentResult =
      PlayerResult.Success<ManifestFulfilled, ManifestFulfillmentErrorType>(
        ManifestFulfilled(
          BookFormats.audioBookGenericMimeTypes().first(),
          ByteArray(23)
        )
      )

    Mockito.`when`(this.basicStrategy.execute())
      .thenReturn(fulfillmentResult)

    val strategy =
      UnpackagedAudioBookManifestStrategy(
        AudioBookManifestRequest(
          targetURI = URI.create("http://www.example.com"),
          contentType = BookFormats.audioBookGenericMimeTypes().first(),
          userAgent = PlayerUserAgent("test"),
          credentials = null,
          services = this.services,
          isNetworkAvailable = { true },
          strategyRegistry = this.strategies,
          manifestParsers = AudioBookSucceedingParsers,
          extensions = emptyList(),
          licenseChecks = listOf(AudioBookFailingLicenseChecks),
          cacheDirectory = File(tempFolder, "cache")
        )
      )

    val failure = strategy.execute() as TaskResult.Failure
    Assertions.assertTrue(
      failure.resolutionOf(2).message.startsWith("One or more license checks failed")
    )
  }

  @Test
  fun testNoBasicStrategySucceeds() {
    Mockito.`when`(
      this.strategies.findStrategy(
        this.any((ManifestFulfillmentBasicType::class.java)::class.java)
      )
    ).thenReturn(this.basicStrategies)

    Mockito.`when`(
      this.basicStrategies.create(
        this.any(ManifestFulfillmentBasicParameters::class.java)
      )
    ).thenReturn(this.basicStrategy)

    Mockito.`when`(this.basicStrategy.events)
      .thenReturn(Observable.never())

    val fulfillmentResult =
      PlayerResult.Success<ManifestFulfilled, ManifestFulfillmentErrorType>(
        ManifestFulfilled(
          BookFormats.audioBookGenericMimeTypes().first(),
          ByteArray(23)
        )
      )

    Mockito.`when`(this.basicStrategy.execute())
      .thenReturn(fulfillmentResult)

    val strategy =
      UnpackagedAudioBookManifestStrategy(
        AudioBookManifestRequest(
          targetURI = URI.create("http://www.example.com"),
          contentType = BookFormats.audioBookGenericMimeTypes().first(),
          userAgent = PlayerUserAgent("test"),
          credentials = null,
          services = this.services,
          isNetworkAvailable = { true },
          strategyRegistry = this.strategies,
          manifestParsers = AudioBookSucceedingParsers,
          extensions = emptyList(),
          licenseChecks = listOf(),
          cacheDirectory = File(tempFolder, "cache")
        )
      )

    val success = strategy.execute() as TaskResult.Success
    Assertions.assertEquals(AudioBookSucceedingParsers.playerManifest, success.result.manifest)
  }

  @Test
  fun testNoNetworkLoadFails() {
    val strategy =
      UnpackagedAudioBookManifestStrategy(
        AudioBookManifestRequest(
          targetURI = URI.create("http://www.example.com"),
          contentType = BookFormats.audioBookGenericMimeTypes().first(),
          userAgent = PlayerUserAgent("test"),
          credentials = null,
          services = this.services,
          isNetworkAvailable = { false },
          cacheDirectory = File(tempFolder, "cache")
        )
      )

    val failure = strategy.execute() as TaskResult.Failure

    Assertions.assertEquals("No fallback manifest data is provided", failure.resolutionOf(0).message)
  }

  @Test
  fun testNoNetworkLoadSucceeds() {
    val strategy =
      UnpackagedAudioBookManifestStrategy(
        AudioBookManifestRequest(
          targetURI = URI.create("http://www.example.com"),
          contentType = BookFormats.audioBookGenericMimeTypes().first(),
          userAgent = PlayerUserAgent("test"),
          credentials = null,
          loadFallbackData = {
            ManifestFulfilled(
              BookFormats.audioBookGenericMimeTypes().first(),
              ByteArray(23)
            )
          },
          services = this.services,
          manifestParsers = AudioBookSucceedingParsers,
          isNetworkAvailable = { false },
          strategyRegistry = this.strategies,
          licenseChecks = listOf(),
          cacheDirectory = File(tempFolder, "cache")
        )
      )

    val success = strategy.execute() as TaskResult.Success
    Assertions.assertEquals(AudioBookSucceedingParsers.playerManifest, success.result.manifest)
  }

  /**
   * Some magic needed to mock calls via Kotlin.
   *
   * See: "https://stackoverflow.com/questions/49148801/mock-object-in-android-unit-test-with-kotlin-any-gives-null"
   */

  private fun <T> any(
    type: Class<T>
  ): T {
    Mockito.any(type)
    return this.uninitialized()
  }

  private fun <T> uninitialized(): T =
    null as T
}
