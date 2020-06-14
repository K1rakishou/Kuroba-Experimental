package com.github.adamantcheese.model.source.remote

import com.github.adamantcheese.model.TestDatabaseModuleComponent
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class InlinedFileInfoRemoteSourceTest {
  lateinit var okHttpClient: OkHttpClient
  lateinit var remoteSource: InlinedFileInfoRemoteSource

  @Before
  fun setUp() {
    ShadowLog.stream = System.out
    val testDatabaseModuleComponent = TestDatabaseModuleComponent()

    okHttpClient = testDatabaseModuleComponent.provideOkHttpClient()
    remoteSource = testDatabaseModuleComponent.provideInlinedFileInfoRemoteSource()
  }

  @After
  fun tearDown() {
    okHttpClient.dispatcher.cancelAll()
  }

  @Test
  fun `test inlined file HEAD request success and failure`() {
    withServer { server ->
      val inlinedFileLink = "/123.jpg"
      val fileSize = 44003434L

      server.enqueue(
        MockResponse()
          .setResponseCode(200)
          .setHeader(InlinedFileInfoRemoteSourceHelper.CONTENT_LENGTH_HEADER, fileSize)
      )
      server.enqueue(
        MockResponse().setResponseCode(403)
      )

      server.start()

      kotlin.run {
        val url = server.url(inlinedFileLink).toString()

        val inlinedFileInfo = remoteSource.fetchFromNetwork(url).unwrap()
        assertEquals(url, inlinedFileInfo.fileUrl)
        assertEquals(fileSize, inlinedFileInfo.fileSize)
      }

      kotlin.run {
        val url = server.url(inlinedFileLink).toString()

        val inlinedFileInfo = remoteSource.fetchFromNetwork(url).unwrap()
        assertEquals(url, inlinedFileInfo.fileUrl)
        assertTrue(inlinedFileInfo.isEmpty())
      }
    }
  }
}