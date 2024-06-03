package com.github.k1rakishou.chan.core.di.component.application

import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.image.loader.KurobaImageLoader
import com.github.k1rakishou.chan.core.manager.DownloadedImagesManager
import com.github.k1rakishou.chan.core.manager.OnDemandContentLoaderManager
import com.github.k1rakishou.chan.core.manager.PrefetchStateManager
import com.github.k1rakishou.chan.core.manager.RevealedSpoilerImagesManager
import com.github.k1rakishou.chan.core.manager.RevealedTextSpoilersManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThirdEyeManager
import com.github.k1rakishou.chan.core.parser.repository.ParsedPostDataRepository
import com.github.k1rakishou.chan.ui.compose.snackbar.manager.SnackbarManagerFactory
import com.github.k1rakishou.chan.ui.config.UiConfiguration
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.KurobaDispatchers
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache

interface ApplicationDependencies {
  val application: Chan
  val appConstants: AppConstants
  val kurobaDispatchers: KurobaDispatchers
  val themeEngine: ThemeEngine
  val siteManager: SiteManager
  val globalUiStateHolder: GlobalUiStateHolder
  val appResources: AppResources
  val uiConfiguration: UiConfiguration
  val snackbarManagerFactory: SnackbarManagerFactory
  val onDemandContentLoaderManager: OnDemandContentLoaderManager
  val chanThreadsCache: ChanThreadsCache
  val kurobaImageLoader: KurobaImageLoader
  val thirdEyeManager: ThirdEyeManager
  val prefetchStateManager: PrefetchStateManager
  val downloadedImagesManager: DownloadedImagesManager
  val cacheHandler: CacheHandler
  val revealedSpoilerImagesManager: RevealedSpoilerImagesManager
  val revealedTextSpoilersManager: RevealedTextSpoilersManager
  val parsedPostDataRepository: ParsedPostDataRepository
}