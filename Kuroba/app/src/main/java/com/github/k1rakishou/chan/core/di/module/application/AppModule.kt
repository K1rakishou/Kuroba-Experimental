/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.di.module.application

import android.content.Context
import android.net.ConnectivityManager
import coil.ImageLoader
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.AppDependenciesInitializer
import com.github.k1rakishou.chan.core.base.okhttp.CoilOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.downloader.ChunkedMediaDownloader
import com.github.k1rakishou.chan.core.helper.ImageLoaderFileManagerWrapper
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.watcher.BookmarkWatcherCoordinator
import com.github.k1rakishou.chan.core.watcher.FilterWatcherCoordinator
import com.github.k1rakishou.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2ServiceDelegate
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadingCoordinator
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.core_logger.Logger.deps
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository
import com.google.gson.Gson
import dagger.Lazy
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
class AppModule {

  private val defaultAvailableMemoryPercentage: Double
    get() {
      var defaultMemoryPercentage = 0.1
      if (ChanSettings.isLowRamDevice()) {
        defaultMemoryPercentage /= 2.0
      }

      return defaultMemoryPercentage
    }

  @Provides
  @Singleton
  fun provideAppDependenciesInitializer(
    siteManager: SiteManager,
    boardManager: BoardManager,
    bookmarksManager: BookmarksManager,
    threadBookmarkGroupManager: ThreadBookmarkGroupManager,
    historyNavigationManager: HistoryNavigationManager,
    bookmarkWatcherCoordinator: BookmarkWatcherCoordinator,
    filterWatcherCoordinator: FilterWatcherCoordinator,
    archivesManager: ArchivesManager,
    chanFilterManager: ChanFilterManager,
    threadDownloadingCoordinator: ThreadDownloadingCoordinator
  ): AppDependenciesInitializer {
    deps("AppDependenciesInitializer")

    return AppDependenciesInitializer(
      siteManager,
      boardManager,
      bookmarksManager,
      threadBookmarkGroupManager,
      historyNavigationManager,
      bookmarkWatcherCoordinator,
      filterWatcherCoordinator,
      archivesManager,
      chanFilterManager,
      threadDownloadingCoordinator
    )
  }

  @Provides
  @Singleton
  fun provideConnectivityManager(appContext: Context): ConnectivityManager {
    val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (connectivityManager != null) {
      return connectivityManager
    }

    throw NullPointerException("What works in this ROM: You tell me ;)\nWhat doesn't work: Connectivity fucking manager")
  }

  @Provides
  @Singleton
  fun provideCoilImageLoader(
    applicationContext: Context,
    coilOkHttpClient: CoilOkHttpClient
  ): ImageLoader {
    val isLowRamDevice = ChanSettings.isLowRamDevice()
    val allowHardware = !isLowRamDevice
    val availableMemoryPercentage = defaultAvailableMemoryPercentage

    deps(
      "ImageLoader(), availableMemoryPercentage=" + availableMemoryPercentage +
        ", isLowRamDevice=" + isLowRamDevice + ", allowHardware=" + allowHardware +
        ", allowRgb565=" + isLowRamDevice
    )

    return ImageLoader.Builder(applicationContext)
      .allowHardware(allowHardware)
      .allowRgb565(isLowRamDevice)
      .memoryCachePolicy(CachePolicy.ENABLED)
      .networkCachePolicy(CachePolicy.ENABLED)
      // Coil's caching system relies on OkHttp's caching system which is not suitable for
      // us (It doesn't handle 404 responses how we want). So we have to use our own disk
      // caching system.
      .diskCachePolicy(CachePolicy.DISABLED)
      .callFactory(coilOkHttpClient.okHttpClient())
      .memoryCache {
        MemoryCache.Builder(applicationContext)
          .maxSizePercent(availableMemoryPercentage)
          .build()
      }
      .build()
  }

  @Provides
  @Singleton
  fun provideImageLoaderV2(
    appScope: CoroutineScope,
    coilImageLoader: Lazy<ImageLoader>,
    replyManager: Lazy<ReplyManager>,
    themeEngine: Lazy<ThemeEngine>,
    cacheHandler: Lazy<CacheHandler>,
    chunkedMediaDownloader: Lazy<ChunkedMediaDownloader>,
    imageLoaderFileManagerWrapper: Lazy<ImageLoaderFileManagerWrapper>,
    siteResolver: Lazy<SiteResolver>,
    coilOkHttpClient: Lazy<CoilOkHttpClient>,
    threadDownloadManager: Lazy<ThreadDownloadManager>
  ): ImageLoaderV2 {
    deps("ImageLoaderV2")

    return ImageLoaderV2(
      ChanSettings.verboseLogs.get(),
      appScope,
      coilImageLoader,
      replyManager,
      themeEngine,
      cacheHandler,
      chunkedMediaDownloader,
      imageLoaderFileManagerWrapper,
      siteResolver,
      coilOkHttpClient,
      threadDownloadManager
    )
  }

  @Provides
  @Singleton
  fun provideImageSaverV2(
    appContext: Context,
    appScope: CoroutineScope,
    gson: Gson,
    fileManager: FileManager,
    imageDownloadRequestRepository: ImageDownloadRequestRepository,
    imageSaverV2ServiceDelegate: ImageSaverV2ServiceDelegate
  ): ImageSaverV2 {
    deps("ImageSaverV2")

    return ImageSaverV2(
      ChanSettings.verboseLogs.get(),
      appContext,
      appScope,
      gson,
      fileManager,
      imageDownloadRequestRepository,
      imageSaverV2ServiceDelegate
    )
  }

  @Provides
  @Singleton
  fun provideCaptchaHolder(appScope: CoroutineScope): CaptchaHolder {
    deps("CaptchaHolder")

    return CaptchaHolder(appScope)
  }

  @Provides
  @Singleton
  fun provideAndroid10GesturesHolder(
    gson: Gson
  ): Android10GesturesExclusionZonesHolder {
    deps("Android10GesturesExclusionZonesHolder")

    return Android10GesturesExclusionZonesHolder(gson)
  }
}
