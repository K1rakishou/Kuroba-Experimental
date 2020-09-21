package com.github.k1rakishou.model.source.remote

import com.github.k1rakishou.model.TestDatabaseModuleComponent
import com.github.k1rakishou.model.data.video_service.MediaServiceType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.joda.time.Period
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class MediaServiceLinkExtraContentRemoteSourceTest {
  lateinit var okHttpClient: OkHttpClient
  lateinit var remoteSource: MediaServiceLinkExtraContentRemoteSource

  @Before
  fun setUp() {
    ShadowLog.stream = System.out
    val testDatabaseModuleComponent = TestDatabaseModuleComponent()

    okHttpClient = testDatabaseModuleComponent.provideOkHttpClient()
    remoteSource = testDatabaseModuleComponent.provideMediaServiceLinkExtraContentRemoteSource()
  }

  @After
  fun tearDown() {
    okHttpClient.dispatcher.cancelAll()
  }

  @Test
  fun `test with good response`() {
    withServer { server ->
      val youtubeLink = "/test_video_id"
      val testTitle = "test title"
      val testDuration = "P1M"

      server.enqueue(
        MockResponse()
          .setResponseCode(200)
          .setBody("""
    {
        "items": [
            {
                "snippet": {
                    "title": "$testTitle"
                },
                "contentDetails": {
                    "duration": "$testDuration"
                }
            }
        ]
    }
""".trimIndent())
      )

      server.start()

      kotlin.run {
        val url = server.url(youtubeLink).toString()

        val info = remoteSource.fetchFromNetwork(url, MediaServiceType.Youtube).unwrap()
        assertEquals(info.videoTitle, testTitle)
        assertEquals(info.videoDuration, Period.parse(testDuration))
      }
    }
  }

  @Test
  fun `test with bad response`() {
    withServer { server ->
      val youtubeLink = "/test_video_id"
      val testTitle = "test title"
      val testDuration = "P1M"

      server.enqueue(
        MockResponse()
          .setResponseCode(200)
          .setBody("""
    {
        "items": [
            {
                "snippet": {
                    "corrupted title": "$testTitle"
                },
                "contentDetails": {
                    "corrupted duration": "$testDuration"
                }
            }
        ]
    }
""".trimIndent())
      )

      server.start()

      kotlin.run {
        val url = server.url(youtubeLink).toString()

        val info = remoteSource.fetchFromNetwork(url, MediaServiceType.Youtube).unwrap()
        assertEquals(info.videoTitle, null)
        assertEquals(info.videoDuration, null)
      }
    }
  }
}