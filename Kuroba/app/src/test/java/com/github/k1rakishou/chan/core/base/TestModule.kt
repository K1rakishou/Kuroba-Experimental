package com.github.k1rakishou.chan.core.base

import com.github.k1rakishou.chan.core.base.okhttp.DownloaderOkHttpClient
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.downloader.*
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.RawFile
import com.github.k1rakishou.fsaf.manager.base_directory.DirectoryManager
import com.nhaarman.mockitokotlin2.mock
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.concurrent.TimeUnit

class TestModule {
  private var downloaderOkHttpClient: DownloaderOkHttpClient? = null
  private var proxiedOkHttpClient: ProxiedOkHttpClient? = null
  private var fileManager: FileManager? = null
  private var cacheHandler: CacheHandler? = null
  private var chunkDownloader: ChunkDownloader? = null
  private var activeDownloads: ActiveDownloads? = null
  private var concurrentChunkedFileDownloader: ConcurrentChunkedFileDownloader? = null
  private var partialContentSupportChecker: PartialContentSupportChecker? = null
  private var chunkPersister: ChunkPersister? = null
  private var chunkMerger: ChunkMerger? = null
  private var siteResolver: SiteResolver? = null
  private var appConstants: AppConstants? = null

  private var cacheDirFile: RawFile? = null
  private var chunksCacheDirFile: RawFile? = null

  fun provideApplication() = RuntimeEnvironment.application
  fun provideContext() = provideApplication().applicationContext

  internal fun provideAppConstants(): AppConstants {
    if (appConstants == null) {
      appConstants = mock()
    }

    return appConstants!!
  }

  internal fun provideChunkReader(): ChunkPersister {
    if (chunkPersister == null) {
      chunkPersister = ChunkPersister(
        provideFileManager(),
        provideCacheHandler(),
        provideActiveDownloads(),
        false
      )
    }

    return chunkPersister!!
  }

  internal fun provideSiteResolver(): SiteResolver {
    if (siteResolver == null) {
      siteResolver = mock()
    }

    return siteResolver!!
  }

  internal fun provideChunkPersister(): ChunkMerger {
    if (chunkMerger == null) {
      chunkMerger = ChunkMerger(
        provideFileManager(),
        provideCacheHandler(),
        provideSiteResolver(),
        provideActiveDownloads(),
        false
      )
    }

    return chunkMerger!!
  }

  internal fun providePartialContentSupportChecker(): PartialContentSupportChecker {
    if (partialContentSupportChecker == null) {
      partialContentSupportChecker = PartialContentSupportChecker(
        provideDownloaderOkHttpClient(),
        provideActiveDownloads(),
        provideSiteResolver(),
        250L,
        provideAppConstants()
      )
    }

    return partialContentSupportChecker!!
  }

  internal fun provideConcurrentChunkDownloader(): ConcurrentChunkedFileDownloader {
    if (concurrentChunkedFileDownloader == null) {
      concurrentChunkedFileDownloader = ConcurrentChunkedFileDownloader(
        provideFileManager(),
        provideChunkDownloader(),
        provideChunkReader(),
        provideChunkPersister(),
        Schedulers.single(),
        false,
        provideActiveDownloads(),
        provideCacheHandler()
      )
    }

    return concurrentChunkedFileDownloader!!
  }

  internal fun provideActiveDownloads(): ActiveDownloads {
    if (activeDownloads == null) {
      activeDownloads = ActiveDownloads()
    }

    return activeDownloads!!
  }

  internal fun provideChunkDownloader(): ChunkDownloader {
    if (chunkDownloader == null) {
      chunkDownloader = ChunkDownloader(
        provideDownloaderOkHttpClient(),
        provideActiveDownloads(),
        false,
        provideAppConstants()
      )
    }

    return chunkDownloader!!
  }

  fun provideFileManager(): FileManager {
    if (fileManager == null) {
      fileManager = FileManager(
        provideContext(),
        BadPathSymbolResolutionStrategy.ThrowAnException,
        DirectoryManager(provideContext())
      )
    }

    return fileManager!!
  }

  fun provideCacheHandler(): CacheHandler {
    if (cacheHandler == null) {
      cacheHandler = CacheHandler(
        provideFileManager(),
        provideCacheDirFile(),
        provideChunksCacheDirFile(),
        false
      )
    }

    return cacheHandler!!
  }

  fun provideCacheDirFile(): RawFile {
    if (cacheDirFile == null) {
      val fileMan = provideFileManager()

      cacheDirFile = fileMan.fromRawFile(File(provideContext().cacheDir, "cache_dir"))
      assertNotNull(fileMan.create(cacheDirFile!!))
      assertTrue(fileMan.deleteContent(cacheDirFile!!))
    }

    return cacheDirFile!!
  }

  fun provideChunksCacheDirFile(): RawFile {
    if (chunksCacheDirFile == null) {
      val fileMan = provideFileManager()

      chunksCacheDirFile = fileMan.fromRawFile(File(provideContext().cacheDir, "chunks_cache_dir"))
      assertNotNull(fileMan.create(chunksCacheDirFile!!))
      assertTrue(fileMan.deleteContent(chunksCacheDirFile!!))
    }

    return chunksCacheDirFile!!
  }

  fun provideDownloaderOkHttpClient(): DownloaderOkHttpClient {
    if (downloaderOkHttpClient == null) {
      downloaderOkHttpClient = TextDownloaderOkHttpClient()
    }

    return downloaderOkHttpClient!!
  }

  fun provideProxiedOkHttpClient(): ProxiedOkHttpClient {
    if (proxiedOkHttpClient == null) {
      proxiedOkHttpClient = TestProxiedOkHttpClient()
    }

    return proxiedOkHttpClient!!
  }

  class TextDownloaderOkHttpClient : DownloaderOkHttpClient {
    private var okHttpClient: OkHttpClient? = null

    override fun okHttpClient(): OkHttpClient {
      if (okHttpClient == null) {
        okHttpClient = OkHttpClient.Builder()
          .connectTimeout(5, TimeUnit.SECONDS)
          .readTimeout(5, TimeUnit.SECONDS)
          .writeTimeout(5, TimeUnit.SECONDS)
          .build()
      }

      return okHttpClient!!
    }
  }

  class TestProxiedOkHttpClient : ProxiedOkHttpClient {
    private var okHttpClient: OkHttpClient? = null

    override fun okHttpClient(): OkHttpClient {
      if (okHttpClient == null) {
        okHttpClient = OkHttpClient.Builder()
          .connectTimeout(5, TimeUnit.SECONDS)
          .readTimeout(5, TimeUnit.SECONDS)
          .writeTimeout(5, TimeUnit.SECONDS)
          .build()
      }

      return okHttpClient!!
    }

  }

}