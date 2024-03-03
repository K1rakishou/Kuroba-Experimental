package com.github.k1rakishou.chan.core.di.module.activity

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.di.scope.PerActivity
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.helper.ThumbnailLongtapOptionsHelper
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.BottomNavBarVisibilityStateManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.ControllerNavigationManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.GlobalViewStateManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.PostHighlightManager
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadFollowHistoryManager
import com.github.k1rakishou.chan.core.manager.UpdateManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.chan.features.reply.data.PostFormattingButtonsFactory
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import dagger.Lazy
import dagger.Module
import dagger.Provides

@Module
class ActivityModule {
  @PerActivity
  @Provides
  fun provideUpdateManager(
    activity: AppCompatActivity,
    cacheHandler: Lazy<CacheHandler>,
    fileManager: Lazy<FileManager>,
    settingsNotificationManager: SettingsNotificationManager,
    fileChooser: Lazy<FileChooser>,
    proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>,
    dialogFactory: Lazy<DialogFactory>
  ): UpdateManager {
    Logger.deps("UpdateManager")
    return UpdateManager(
      activity,
      cacheHandler,
      fileManager,
      settingsNotificationManager,
      fileChooser,
      proxiedOkHttpClient,
      dialogFactory
    )
  }

  @PerActivity
  @Provides
  fun provideRuntimePermissionHelper(
    activity: AppCompatActivity,
    dialogFactory: DialogFactory
  ): RuntimePermissionsHelper {
    Logger.deps("RuntimePermissionsHelper")
    return RuntimePermissionsHelper(
      activity,
      dialogFactory
    )
  }

  @PerActivity
  @Provides
  fun provideStartActivityStartupHandlerHelper(
    historyNavigationManager: Lazy<HistoryNavigationManager>,
    siteManager: Lazy<SiteManager>,
    boardManager: Lazy<BoardManager>,
    bookmarksManager: Lazy<BookmarksManager>,
    chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>,
    siteResolver: Lazy<SiteResolver>,
    compositeCatalogManager: Lazy<CompositeCatalogManager>,
    notificationManagerCompat: NotificationManagerCompat
  ): StartActivityStartupHandlerHelper {
    Logger.deps("StartActivityStartupHandlerHelper")
    return StartActivityStartupHandlerHelper(
      historyNavigationManager,
      siteManager,
      boardManager,
      bookmarksManager,
      chanThreadViewableInfoManager,
      siteResolver,
      compositeCatalogManager,
      notificationManagerCompat
    )
  }

  @PerActivity
  @Provides
  fun provideGlobalWindowInsetsManager(): GlobalWindowInsetsManager {
    Logger.deps("GlobalWindowInsetsManager")
    return GlobalWindowInsetsManager()
  }

  @PerActivity
  @Provides
  fun provideControllerNavigationManager(): ControllerNavigationManager {
    Logger.deps("ControllerNavigationManager")
    return ControllerNavigationManager()
  }

  @PerActivity
  @Provides
  fun provideThreadFollowHistoryManager(): ThreadFollowHistoryManager {
    Logger.deps("ThreadFollowHistoryManager")
    return ThreadFollowHistoryManager()
  }

  @PerActivity
  @Provides
  fun provideReplyViewStateManager(): BottomNavBarVisibilityStateManager {
    Logger.deps("BottomNavBarVisibilityStateManager")
    return BottomNavBarVisibilityStateManager()
  }

  @PerActivity
  @Provides
  fun provideDialogFactory(
    applicationVisibilityManager: Lazy<ApplicationVisibilityManager>,
    themeEngine: Lazy<ThemeEngine>
  ): DialogFactory {
    Logger.deps("DialogFactory")
    return DialogFactory(
      applicationVisibilityManager,
      themeEngine
    )
  }

  @PerActivity
  @Provides
  fun provideGlobalViewStateManager(): GlobalViewStateManager {
    Logger.deps("GlobalViewStateManager")
    return GlobalViewStateManager()
  }

  @PerActivity
  @Provides
  fun provideThumbnailLongtapOptionsHelper(
    globalWindowInsetsManager: GlobalWindowInsetsManager,
    imageSaverV2: Lazy<ImageSaverV2>
  ): ThumbnailLongtapOptionsHelper {
    Logger.deps("ThumbnailLongtapOptionsHelper")
    return ThumbnailLongtapOptionsHelper(
      globalWindowInsetsManager,
      imageSaverV2
    )
  }

  @PerActivity
  @Provides
  fun provideFileChooser(activity: AppCompatActivity): FileChooser {
    Logger.deps("FileChooser")
    return FileChooser(activity)
  }

  @PerActivity
  @Provides
  fun providePostHighlightManager(
    currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
  ): PostHighlightManager {
    Logger.deps("PostHighlightManager")
    return PostHighlightManager(currentOpenedDescriptorStateManager)
  }

  @PerActivity
  @Provides
  fun providePostFormattingButtonsFactory(
    boardManagerLazy: Lazy<BoardManager>,
    themeEngineLazy: Lazy<ThemeEngine>
  ): PostFormattingButtonsFactory {
    return PostFormattingButtonsFactory(boardManagerLazy, themeEngineLazy)
  }

}
