package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.TestDatabaseModuleComponent
import com.github.k1rakishou.model.data.media.GenericVideoId
import com.github.k1rakishou.model.data.media.MediaServiceLinkExtraInfo
import com.github.k1rakishou.model.data.video_service.MediaServiceLinkExtraContent
import com.github.k1rakishou.model.data.video_service.MediaServiceType
import com.github.k1rakishou.model.source.cache.GenericSuspendableCacheSource
import com.github.k1rakishou.model.source.local.MediaServiceLinkExtraContentLocalSource
import com.github.k1rakishou.model.source.remote.MediaServiceLinkExtraContentRemoteSource
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import org.joda.time.Period
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class MediaServiceLinkExtraContentRepositoryTest {
  private val coroutineScope = TestCoroutineScope()

  lateinit var cache: GenericSuspendableCacheSource<MediaServiceLinkExtraContentRepository.MediaServiceKey, MediaServiceLinkExtraContent>
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
      val videoId = GenericVideoId("testVideoId234234234")
      val serviceType = MediaServiceType.Youtube
      val duration = Period.parse("P1M")
      val title = null
      val content = MediaServiceLinkExtraContent(videoId, serviceType, title, duration)

      whenever(cache.get(any())).thenReturn(content)
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
      val videoId = GenericVideoId("testVideoId234234234")
      val serviceType = MediaServiceType.Youtube
      val mediaServiceKey = MediaServiceLinkExtraContentRepository.MediaServiceKey(videoId, serviceType)
      val duration = Period.parse("P1M")
      val title = null
      val content = MediaServiceLinkExtraContent(videoId, serviceType, title, duration)

      whenever(cache.get(any())).thenReturn(null)
      whenever(localSource.deleteOlderThan(any())).thenReturn(1)
      whenever(localSource.selectByMediaServiceKey(videoId, mediaServiceKey)).thenReturn(content)

      val linkExtraContent = repository.getLinkExtraContent(serviceType, requestUrl, videoId).unwrap()
      assertEquals(videoId, linkExtraContent.videoId)
      assertEquals(serviceType, linkExtraContent.mediaServiceType)
      assertEquals(title, linkExtraContent.videoTitle)
      assertEquals(duration, linkExtraContent.videoDuration)

      verify(localSource, times(1)).deleteOlderThan(any())
      verify(localSource, times(1)).selectByMediaServiceKey(videoId, mediaServiceKey)
      verifyZeroInteractions(remoteSource)
    }
  }

  @Test
  fun `test when both are empty get data from the remote source`() {
    runBlocking(Dispatchers.Default) {
      val requestUrl = "youtube.com/test_url"
      val videoId = GenericVideoId("testVideoId234234234")
      val serviceType = MediaServiceType.Youtube
      val mediaServiceKey = MediaServiceLinkExtraContentRepository.MediaServiceKey(videoId, serviceType)
      val duration = Period.parse("P1M")
      val title = null
      val content = MediaServiceLinkExtraContent(videoId, serviceType, title, duration)
      val info = MediaServiceLinkExtraInfo(title, duration)

      whenever(cache.get(any())).thenReturn(null)
      whenever(localSource.deleteOlderThan(any())).thenReturn(1)
      whenever(localSource.selectByMediaServiceKey(videoId, mediaServiceKey)).thenReturn(null)
      whenever(remoteSource.fetchFromNetwork(requestUrl, videoId, serviceType)).thenReturn(ModularResult.value(info))
      whenever(localSource.insert(content)).thenReturn(Unit)

      val linkExtraContent = repository.getLinkExtraContent(serviceType, requestUrl, videoId).unwrap()
      assertEquals(videoId, linkExtraContent.videoId)
      assertEquals(serviceType, linkExtraContent.mediaServiceType)
      assertEquals(title, linkExtraContent.videoTitle)
      assertEquals(duration, linkExtraContent.videoDuration)

      verify(localSource, times(1)).deleteOlderThan(any())
      verify(localSource, times(1)).selectByMediaServiceKey(videoId, mediaServiceKey)
      verify(localSource, times(1)).insert(content)
      verify(remoteSource, times(1)).fetchFromNetwork(requestUrl, videoId, serviceType)
    }
  }
}