package com.github.k1rakishou.chan.core.di.module.application

import android.content.Context
import coil.ImageLoader
import com.github.k1rakishou.chan.core.base.okhttp.CoilOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.downloader.ChunkedMediaDownloader
import com.github.k1rakishou.chan.core.helper.ImageLoaderFileManagerWrapper
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.image.loader.KurobaImageFromDiskLoader
import com.github.k1rakishou.chan.core.image.loader.KurobaImageFromDiskLoaderImpl
import com.github.k1rakishou.chan.core.image.loader.KurobaImageFromMemoryLoader
import com.github.k1rakishou.chan.core.image.loader.KurobaImageFromMemoryLoaderImpl
import com.github.k1rakishou.chan.core.image.loader.KurobaImageFromNetworkLoader
import com.github.k1rakishou.chan.core.image.loader.KurobaImageFromNetworkLoaderImpl
import com.github.k1rakishou.chan.core.image.loader.KurobaImageFromResourcesLoader
import com.github.k1rakishou.chan.core.image.loader.KurobaImageFromResourcesLoaderImpl
import com.github.k1rakishou.chan.core.image.loader.KurobaImageLoader
import com.github.k1rakishou.chan.core.image.loader.KurobaImageLoaderImpl
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.v2.KurobaSettings
import dagger.Lazy
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
class ImageLoaderModule {

  @Provides
  @Singleton
  fun provideImageLoaderDeprecated(
    appScope: CoroutineScope,
    appContext: Context,
    kurobaSettings: KurobaSettings,
    coilImageLoader: Lazy<ImageLoader>,
    replyManager: Lazy<ReplyManager>,
    themeEngine: Lazy<ThemeEngine>,
    cacheHandler: Lazy<CacheHandler>,
    chunkedMediaDownloader: Lazy<ChunkedMediaDownloader>,
    imageLoaderFileManagerWrapper: Lazy<ImageLoaderFileManagerWrapper>,
    siteResolver: Lazy<SiteResolver>,
    coilOkHttpClient: Lazy<CoilOkHttpClient>,
    threadDownloadManager: Lazy<ThreadDownloadManager>
  ): ImageLoaderDeprecated {
    Logger.deps("ImageLoaderDeprecated")

    return ImageLoaderDeprecated(
      appScope = appScope,
      appContext = appContext,
      kurobaSettings = kurobaSettings,
      imageLoaderLazy = coilImageLoader,
      replyManagerLazy = replyManager,
      themeEngineLazy = themeEngine,
      cacheHandlerLazy = cacheHandler,
      chunkedMediaDownloaderLazy = chunkedMediaDownloader,
      imageLoaderFileManagerWrapperLazy = imageLoaderFileManagerWrapper,
      siteResolverLazy = siteResolver,
      coilOkHttpClientLazy = coilOkHttpClient,
      threadDownloadManagerLazy = threadDownloadManager
    )
  }

  @Provides
  @Singleton
  fun provideKurobaImageFromMemoryLoader(
    coilImageLoaderLazy: Lazy<ImageLoader>
  ): KurobaImageFromMemoryLoader {
    Logger.deps("KurobaImageFromMemoryLoader")

    return KurobaImageFromMemoryLoaderImpl(
      coilImageLoaderLazy = coilImageLoaderLazy
    )
  }

  @Provides
  @Singleton
  fun provideKurobaImageFromDiskLoader(
    cacheHandlerLazy: Lazy<CacheHandler>,
    threadDownloadManagerLazy: Lazy<ThreadDownloadManager>,
    fileManagerLazy: Lazy<FileManager>,
    coilImageLoaderLazy: Lazy<ImageLoader>
  ): KurobaImageFromDiskLoader {
    Logger.deps("KurobaImageFromDiskLoader")

    return KurobaImageFromDiskLoaderImpl(
      cacheHandlerLazy = cacheHandlerLazy,
      threadDownloadManagerLazy = threadDownloadManagerLazy,
      fileManagerLazy = fileManagerLazy,
      coilImageLoaderLazy = coilImageLoaderLazy
    )
  }

  @Provides
  @Singleton
  fun provideKurobaImageFromNetworkLoader(
    kurobaSettings: KurobaSettings,
    cacheHandlerLazy: Lazy<CacheHandler>,
    chunkedMediaDownloaderLazy: Lazy<ChunkedMediaDownloader>,
    siteResolverLazy: Lazy<SiteResolver>,
    coilOkHttpClientLazy: Lazy<CoilOkHttpClient>,
    coilImageLoaderLazy: Lazy<ImageLoader>,
    fileManagerLazy: Lazy<FileManager>
  ): KurobaImageFromNetworkLoader {
    Logger.deps("KurobaImageFromNetworkLoader")

    return KurobaImageFromNetworkLoaderImpl(
      kurobaSettings = kurobaSettings,
      cacheHandlerLazy = cacheHandlerLazy,
      chunkedMediaDownloaderLazy = chunkedMediaDownloaderLazy,
      siteResolverLazy = siteResolverLazy,
      coilOkHttpClientLazy = coilOkHttpClientLazy,
      coilImageLoaderLazy = coilImageLoaderLazy,
      fileManagerLazy = fileManagerLazy
    )
  }

  @Provides
  @Singleton
  fun provideKurobaImageFromResourcesLoader(
    coilImageLoaderLazy: Lazy<ImageLoader>
  ): KurobaImageFromResourcesLoader {
    Logger.deps("KurobaImageFromResourcesLoader")

    return KurobaImageFromResourcesLoaderImpl(
      coilImageLoaderLazy = coilImageLoaderLazy
    )
  }

  @Provides
  @Singleton
  fun provideKurobaImageLoader(
    memoryLoaderLazy: Lazy<KurobaImageFromMemoryLoader>,
    resourcesLoaderLazy: Lazy<KurobaImageFromResourcesLoader>,
    diskLoaderLazy: Lazy<KurobaImageFromDiskLoader>,
    networkLoaderLazy: Lazy<KurobaImageFromNetworkLoader>,
  ): KurobaImageLoader {
    Logger.deps("KurobaImageLoader")

    return KurobaImageLoaderImpl(
      memoryLoaderLazy = memoryLoaderLazy,
      resourcesLoaderLazy = resourcesLoaderLazy,
      diskLoaderLazy = diskLoaderLazy,
      networkLoaderLazy = networkLoaderLazy
    )
  }

}