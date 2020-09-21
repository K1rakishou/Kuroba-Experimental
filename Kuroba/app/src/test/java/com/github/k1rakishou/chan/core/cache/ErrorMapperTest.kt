package com.github.k1rakishou.chan.core.cache

import com.github.k1rakishou.chan.core.cache.downloader.ActiveDownloads
import com.github.k1rakishou.chan.core.cache.downloader.FileCacheException
import com.github.k1rakishou.chan.core.cache.downloader.FileDownloadResult
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMapperTest {

  @Test
  fun `FileNotFoundOnTheServerException must be mapped into FileDownloadResult KnownException`() {
    val url = "test.com"
    val activeDownloads = ActiveDownloads()

    val result = ErrorMapper.mapError(
      url,
      FileCacheException.FileNotFoundOnTheServerException(),
      activeDownloads
    )

    assertTrue(result is FileDownloadResult.KnownException)
    result as FileDownloadResult.KnownException

    assertTrue(result.fileCacheException is FileCacheException.FileNotFoundOnTheServerException)
  }

}