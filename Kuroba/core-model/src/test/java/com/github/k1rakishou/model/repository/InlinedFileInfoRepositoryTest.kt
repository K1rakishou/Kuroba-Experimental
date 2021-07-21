package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.TestDatabaseModuleComponent
import com.github.k1rakishou.model.data.InlinedFileInfo
import com.github.k1rakishou.model.source.cache.GenericSuspendableCacheSource
import com.github.k1rakishou.model.source.local.InlinedFileInfoLocalSource
import com.github.k1rakishou.model.source.remote.InlinedFileInfoRemoteSource
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class InlinedFileInfoRepositoryTest {
  private val coroutineScope = TestCoroutineScope()

  lateinit var cache: GenericSuspendableCacheSource<String, InlinedFileInfo>
  lateinit var repository: InlinedFileInfoRepository
  lateinit var localSource: InlinedFileInfoLocalSource
  lateinit var remoteSource: InlinedFileInfoRemoteSource

  @Before
  fun setUp() {
    val testDatabaseModuleComponent = TestDatabaseModuleComponent()
    cache = mock()
    localSource = mock()
    remoteSource = mock()

    repository = InlinedFileInfoRepository(
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
      val url = "test.com/123.jpg"
      val fileSize = 10000L

      whenever(cache.get(anyString())).thenReturn(InlinedFileInfo(url, fileSize))
      whenever(localSource.deleteOlderThan(any())).thenReturn(1)

      val inlinedFileInfo = repository.getInlinedFileInfo(url).unwrap()
      assertEquals(url, inlinedFileInfo.fileUrl)
      assertEquals(fileSize, inlinedFileInfo.fileSize)

      verify(localSource, times(1)).deleteOlderThan(any())
      verifyZeroInteractions(remoteSource)
    }
  }

  @Test
  fun `test repository when cache miss but local source hit should not get data from remote source`() {
    runBlocking(Dispatchers.Default) {
      val url = "test.com/123.jpg"
      val fileSize = 10000L

      whenever(cache.get(anyString())).thenReturn(null)
      whenever(localSource.deleteOlderThan(any())).thenReturn(1)
      whenever(localSource.selectByFileUrl(url)).thenReturn(InlinedFileInfo(url, fileSize))

      val inlinedFileInfo = repository.getInlinedFileInfo(url).unwrap()
      assertEquals(url, inlinedFileInfo.fileUrl)
      assertEquals(fileSize, inlinedFileInfo.fileSize)

      verify(localSource, times(1)).deleteOlderThan(any())
      verify(localSource, times(1)).selectByFileUrl(url)
      verifyZeroInteractions(remoteSource)
    }
  }

  @Test
  fun `test when both are empty get data from the remote source`() {
    runBlocking(Dispatchers.Default) {
      val url = "test.com/123.jpg"
      val fileSize = 10000L
      val inlinedFileInfo = InlinedFileInfo(url, fileSize)

      whenever(cache.get(anyString())).thenReturn(null)
      whenever(localSource.deleteOlderThan(any())).thenReturn(1)
      whenever(localSource.selectByFileUrl(url)).thenReturn(null)
      whenever(remoteSource.fetchFromNetwork(url)).thenReturn(ModularResult.value(inlinedFileInfo))
      whenever(localSource.insert(inlinedFileInfo)).thenReturn(Unit)

      val resultInlinedFileInfo = repository.getInlinedFileInfo(url).unwrap()
      assertEquals(url, resultInlinedFileInfo.fileUrl)
      assertEquals(fileSize, resultInlinedFileInfo.fileSize)

      verify(localSource, times(1)).deleteOlderThan(any())
      verify(localSource, times(1)).selectByFileUrl(url)
      verify(localSource, times(1)).insert(inlinedFileInfo)
      verify(remoteSource, times(1)).fetchFromNetwork(url)
    }
  }

}