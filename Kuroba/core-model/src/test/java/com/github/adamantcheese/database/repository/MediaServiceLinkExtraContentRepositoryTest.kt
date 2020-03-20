package com.github.adamantcheese.database.repository

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.database.TestDatabaseModuleComponent
import com.github.adamantcheese.database.data.MediaServiceLinkExtraInfo
import com.github.adamantcheese.database.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.database.data.video_service.MediaServiceType
import com.github.adamantcheese.database.source.cache.GenericCacheSource
import com.github.adamantcheese.database.source.local.MediaServiceLinkExtraContentLocalSource
import com.github.adamantcheese.database.source.remote.MediaServiceLinkExtraContentRemoteSource
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.joda.time.Period
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaServiceLinkExtraContentRepositoryTest {
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
                testDatabaseModuleComponent.provideKurobaDatabase(),
                "",
                testDatabaseModuleComponent.provideLogger(),
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
            whenever(localSource.deleteOlderThan(any())).thenReturn(ModularResult.value(1))

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
            whenever(localSource.deleteOlderThan(any())).thenReturn(ModularResult.value(1))
            whenever(localSource.selectByVideoId(videoId)).thenReturn(ModularResult.value(content))

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
            whenever(localSource.deleteOlderThan(any())).thenReturn(ModularResult.value(1))
            whenever(localSource.selectByVideoId(videoId)).thenReturn(ModularResult.value(null))
            whenever(remoteSource.fetchFromNetwork(requestUrl, serviceType)).thenReturn(ModularResult.value(info))
            whenever(localSource.insert(content)).thenReturn(ModularResult.value(Unit))

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