package com.github.k1rakishou.chan.core.cache

import com.github.k1rakishou.chan.core.base.TestModule
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.fsaf.FileManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CacheHandlerTest {
  private val testModule = TestModule()

  private lateinit var cacheHandler: CacheHandler
  private lateinit var fileManager: FileManager
  private lateinit var cacheDirFile: File

  @Before
  fun init() {
    AndroidUtils.init(testModule.provideApplication())
    ShadowLog.stream = System.out

    fileManager = testModule.provideFileManager()
    cacheHandler = testModule.provideCacheHandler()
    cacheDirFile = testModule.provideCacheDirFile()
  }

  @After
  fun tearDown() {
    cacheHandler.clearCache()
  }

  @Test
  fun `simple test create new cache file and mark it as downloaded`() {
    val url = "http://4chan.org/image.jpg"
    val cacheFile = checkNotNull(cacheHandler.getOrCreateCacheFile(url))
    assertFalse(cacheHandler.isAlreadyDownloaded(cacheFile))

    assertTrue(cacheHandler.markFileDownloaded(cacheFile))
    assertTrue(cacheHandler.isAlreadyDownloaded(cacheFile))
  }

  @Test
  fun `test create new cache file and malform cache file meta should delete both files`() {
    val url = "http://4chan.org/image.jpg"
    val cacheFile = checkNotNull(cacheHandler.getOrCreateCacheFile(url))
    val cacheFileMeta = cacheHandler.getCacheFileMetaInternal(url)
    val fileLength = cacheFileMeta.length()

    checkNotNull(cacheFileMeta.inputStream()).use { inputStream ->
      val array = ByteArray(fileLength.toInt())
      inputStream.read(array)

      checkNotNull(cacheFileMeta.outputStream()).use { outputStream ->
        // Malform the "True/False" boolean parameter by replacing it's last character with
        // a comma
        array[array.lastIndex] = ','.toByte()

        outputStream.write(array)
        outputStream.flush()
      }
    }

    assertFalse(cacheHandler.markFileDownloaded(cacheFile))
    assertFalse(cacheHandler.isAlreadyDownloaded(cacheFile))
    assertTrue(cacheDirFile.listFiles().isNullOrEmpty())
  }

  @Test
  fun `clearCache method should delete all cache files with their meta from the cache dir`() {
    repeat(10) { index ->
      val url = "http://4chan.org/image$index.jpg"
      val cacheFile = checkNotNull(cacheHandler.getOrCreateCacheFile(url))
      assertFalse(cacheHandler.isAlreadyDownloaded(cacheFile))

      assertTrue(cacheHandler.markFileDownloaded(cacheFile))
      assertTrue(cacheHandler.isAlreadyDownloaded(cacheFile))
    }

    cacheHandler.clearCache()

    assertTrue(cacheDirFile.listFiles().isNullOrEmpty())
  }
}