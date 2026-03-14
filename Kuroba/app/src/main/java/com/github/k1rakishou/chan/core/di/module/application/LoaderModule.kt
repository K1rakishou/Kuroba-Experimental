package com.github.k1rakishou.chan.core.di.module.application

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.downloader.ChunkedMediaDownloader
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.loader.impl.Chan4CloudFlareImagePreloader
import com.github.k1rakishou.chan.core.loader.impl.PostExtraContentLoader
import com.github.k1rakishou.chan.core.loader.impl.PostHighlightFilterLoader
import com.github.k1rakishou.chan.core.loader.impl.PrefetchLoader
import com.github.k1rakishou.chan.core.loader.impl.ThirdEyeLoader
import com.github.k1rakishou.chan.core.loader.impl.external_media_service.SoundCloudMediaServiceExtraInfoFetcher
import com.github.k1rakishou.chan.core.loader.impl.external_media_service.StreamableMediaServiceExtraInfoFetcher
import com.github.k1rakishou.chan.core.loader.impl.external_media_service.YoutubeMediaServiceExtraInfoFetcher
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.Chan4CloudFlareImagePreloaderManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.PostFilterHighlightManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PrefetchStateManager
import com.github.k1rakishou.chan.core.manager.ThirdEyeManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger.deps
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository
import com.github.k1rakishou.v2.KurobaSettings
import dagger.Lazy
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class LoaderModule {
  @Provides
  @Singleton
  fun providePrefetchLoader(
    kurobaSettings: KurobaSettings,
    chunkedMediaDownloader: Lazy<ChunkedMediaDownloader>,
    cacheHandler: Lazy<CacheHandler>,
    prefetchStateManager: Lazy<PrefetchStateManager>,
    chanThreadManager: Lazy<ChanThreadManager>,
    archivesManager: Lazy<ArchivesManager>,
    threadDownloadManager: Lazy<ThreadDownloadManager>
  ): PrefetchLoader {
    deps("PrefetchLoader")

    return PrefetchLoader(
      kurobaSettings,
      chunkedMediaDownloader,
      cacheHandler,
      chanThreadManager,
      archivesManager,
      prefetchStateManager,
      threadDownloadManager
    )
  }

  @Provides
  @Singleton
  fun provideThirdEyeLoader(
    appConstants: AppConstants,
    thirdEyeManager: Lazy<ThirdEyeManager>,
    chanThreadManager: Lazy<ChanThreadManager>,
    proxiedOkHttpClient: Lazy<ProxiedOkHttpClient>
  ): ThirdEyeLoader {
    deps("ThirdEyeLoader")

    return ThirdEyeLoader(
      appConstants,
      thirdEyeManager,
      chanThreadManager,
      proxiedOkHttpClient
    )
  }

  @Provides
  @Singleton
  fun provideChan4CloudFlareImagePreloader(
    chan4CloudFlareImagePreloaderManager: Chan4CloudFlareImagePreloaderManager
  ): Chan4CloudFlareImagePreloader {
    deps("Chan4CloudFlareImagePreloader")

    return Chan4CloudFlareImagePreloader(
      chan4CloudFlareImagePreloaderManager
    )
  }

  @Provides
  @Singleton
  fun providePostHighlightFilterLoader(
    chanFilterManager: ChanFilterManager,
    filterEngine: FilterEngine,
    postFilterManager: PostFilterManager,
    chanThreadManager: ChanThreadManager,
    postFilterHighlightManager: PostFilterHighlightManager
  ): PostHighlightFilterLoader {
    deps("PostHighlightFilterLoader")

    return PostHighlightFilterLoader(
      chanFilterManager,
      filterEngine,
      postFilterManager,
      chanThreadManager,
      postFilterHighlightManager
    )
  }

  @Provides
  @Singleton
  fun provideYoutubeMediaServiceExtraInfoFetcher(
    kurobaSettings: KurobaSettings,
    mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository
  ): YoutubeMediaServiceExtraInfoFetcher {
    deps("YoutubeMediaServiceExtraInfoFetcher")

    return YoutubeMediaServiceExtraInfoFetcher(
      kurobaSettings,
      mediaServiceLinkExtraContentRepository
    )
  }

  @Provides
  @Singleton
  fun provideSoundCloudMediaServiceExtraInfoFetcher(
    kurobaSettings: KurobaSettings,
    mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository
  ): SoundCloudMediaServiceExtraInfoFetcher {
    deps("SoundCloudMediaServiceExtraInfoFetcher")

    return SoundCloudMediaServiceExtraInfoFetcher(
      kurobaSettings,
      mediaServiceLinkExtraContentRepository
    )
  }

  @Provides
  @Singleton
  fun provideStreamableMediaServiceExtraInfoFetcher(
    kurobaSettings: KurobaSettings,
    mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository
  ): StreamableMediaServiceExtraInfoFetcher {
    deps("StreamableMediaServiceExtraInfoFetcher")

    return StreamableMediaServiceExtraInfoFetcher(
      kurobaSettings,
      mediaServiceLinkExtraContentRepository
    )
  }

  @Provides
  @Singleton
  fun providePostExtraContentLoader(
    kurobaSettings: KurobaSettings,
    youtubeMediaServiceExtraInfoFetcher: YoutubeMediaServiceExtraInfoFetcher,
    soundCloudMediaServiceExtraInfoFetcher: SoundCloudMediaServiceExtraInfoFetcher,
    streamableMediaServiceExtraInfoFetcher: StreamableMediaServiceExtraInfoFetcher,
    chanThreadManager: ChanThreadManager
  ): PostExtraContentLoader {
    deps("PostExtraContentLoader")

    val fetchers = buildList {
      add(youtubeMediaServiceExtraInfoFetcher)
      add(soundCloudMediaServiceExtraInfoFetcher)
      add(streamableMediaServiceExtraInfoFetcher)
    }

    return PostExtraContentLoader(
      kurobaSettings,
      chanThreadManager,
      fetchers
    )
  }
}
