package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.TestDatabaseModuleComponent
import com.github.adamantcheese.model.data.MediaServiceLinkExtraInfo
import com.github.adamantcheese.model.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.model.data.video_service.MediaServiceType
import com.github.adamantcheese.model.source.cache.GenericCacheSource
import com.github.adamantcheese.model.source.local.MediaServiceLinkExtraContentLocalSource
import com.github.adamantcheese.model.source.remote.MediaServiceLinkExtraContentRemoteSource
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import org.joda.time.Period
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class MediaServiceLinkExtraContentRepositoryTest {
  private val coroutineScope = TestCoroutineScope()

  lateinit var cache: GenericCacheSource<String, MediaServiceLinkExtraContent>
  lateinit var repository: MediaServiceLinkExtraContentRepository
  lateinit var localSource: MediaServiceLinkExtraContentLocalSource
  lateinit var remoteSource: MediaServiceLinkExtraContentRemoteSource

  @Before
  fun setUp() {
    val testDatabaseModuleComponent = TestDatabaseModuleComponent()
    cache = mock()
    localSource = mock()
    remoteSource = mock()

    repository = MediaServiceLinkExtraContentRepository(
      testDatabaseModuleComponent.provideInMemoryKurobaDatabase(),
      "",
      testDatabaseModuleComponent.provideLogger(),
      coroutineScope,
      cache,
      localSource,
      remoteSource
    )
  }

  @Test
  fun `test repository when cache hit should not get data from neither local source nor remote source`() {
    runBlocking(Dispatchers.Default) {
      val requestUrl = "youtube.com/test_url"
      val videoId = "testVideoId234234234"
      val serviceType = MediaServiceType.Youtube
      val duration = Period.parse("P1M")
      val title = null
      val content = MediaServiceLinkExtraContent(videoId, serviceType, title, duration)

      whenever(cache.get(anyString())).thenReturn(content)
      whenever(localSource.deleteOlderThan(any())).thenReturn(1)

      val linkExtraContent = repository.getLinkExtraContent(serviceType, requestUrl, videoId).unwrap()
      assertEquals(videoId, linkExtraContent.videoId)
      assertEquals(serviceType, linkExtraContent.mediaServiceType)
      assertEquals(title, linkExtraContent.videoTitle)
      assertEquals(duration, linkExtraContent.videoDuration)

      verify(localSource, times(1)).deleteOlderThan(any())
      verifyZeroInteractions(remoteSource)
    }
  }

  @Test
  fun `test repository when cache miss but local source hit should not get data from remote source`() {
    runBlocking(Dispatchers.Default) {
      val requestUrl = "youtube.com/test_url"
      val videoId = "testVideoId234234234"
      val serviceType = MediaServiceType.Youtube
      val duration = Period.parse("P1M")
      val title = null
      val content = MediaServiceLinkExtraContent(videoId, serviceType, title, duration)

      whenever(cache.get(anyString())).thenReturn(null)
      whenever(localSource.deleteOlderThan(any())).thenReturn(1)
      whenever(localSource.selectByVideoId(videoId)).thenReturn(content)

      val linkExtraContent = repository.getLinkExtraContent(serviceType, requestUrl, videoId).unwrap()
      assertEquals(videoId, linkExtraContent.videoId)
      assertEquals(serviceType, linkExtraContent.mediaServiceType)
      assertEquals(title, linkExtraContent.videoTitle)
      assertEquals(duration, linkExtraContent.videoDuration)

      verify(localSource, times(1)).deleteOlderThan(any())
      verify(localSource, times(1)).selectByVideoId(videoId)
      verifyZeroInteractions(remoteSource)
    }
  }

  @Test
  fun `test when both are empty get data from the remote source`() {
    runBlocking(Dispatchers.Default) {
      val requestUrl = "youtube.com/test_url"
      val videoId = "testVideoId234234234"
      val serviceType = MediaServiceType.Youtube
      val duration = Period.parse("P1M")
      val title = null
      val content = MediaServiceLinkExtraContent(videoId, serviceType, title, duration)
      val info = MediaServiceLinkExtraInfo(title, duration)

      whenever(cache.get(anyString())).thenReturn(null)
      whenever(localSource.deleteOlderThan(any())).thenReturn(1)
      whenever(localSource.selectByVideoId(videoId)).thenReturn(null)
      whenever(remoteSource.fetchFromNetwork(requestUrl, serviceType)).thenReturn(ModularResult.value(info))
      whenever(localSource.insert(content)).thenReturn(Unit)

      val linkExtraContent = repository.getLinkExtraContent(serviceType, requestUrl, videoId).unwrap()
      assertEquals(videoId, linkExtraContent.videoId)
      assertEquals(serviceType, linkExtraContent.mediaServiceType)
      assertEquals(title, linkExtraContent.videoTitle)
      assertEquals(duration, linkExtraContent.videoDuration)

      verify(localSource, times(1)).deleteOlderThan(any())
      verify(localSource, times(1)).selectByVideoId(videoId)
      verify(localSource, times(1)).insert(content)
      verify(remoteSource, times(1)).fetchFromNetwork(requestUrl, serviceType)
    }
  }
}