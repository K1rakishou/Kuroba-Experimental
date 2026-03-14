package com.github.k1rakishou.v2

import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import com.github.k1rakishou.v2.parameters.BoardPostViewMode
import com.github.k1rakishou.v2.parameters.BookmarksSortOrder
import com.github.k1rakishou.v2.parameters.CatalogOrThreadSearchMode
import com.github.k1rakishou.v2.parameters.ImageGestureActionType
import com.github.k1rakishou.v2.parameters.LayoutMode
import com.github.k1rakishou.v2.parameters.NetworkContentAutoLoadMode
import com.github.k1rakishou.v2.parameters.PostAlignmentMode
import com.github.k1rakishou.v2.parameters.PostThumbnailScaling
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class ApplicationSettings(
  database: KurobaSettingsDatabase,
  val applicationSettingsParameters: ApplicationSettingsParameters,
  override val initialSettingsState: KurobaInitialSettingsState
) : BaseSettings(database) {
  override val backupable: Boolean = true

  val isLowRamDeviceForced by lazy {
    createBooleanSetting(KurobaSettingKey.Application.IsLowRamDeviceForced, false)
  }

  val watchEnabled by lazy {
    createBooleanSetting(KurobaSettingKey.Application.WatchEnabled, false)
  }
  val watchBackground by lazy {
    createBooleanSetting(KurobaSettingKey.Application.WatchBackground, false)
  }
  val watchBackgroundInterval by lazy {
    createLongSetting(KurobaSettingKey.Application.WatchBackgroundInterval, TimeUnit.MINUTES.toMillis(30))
  }
  val watchForegroundInterval by lazy {
    createLongSetting(KurobaSettingKey.Application.WatchForegroundInterval, TimeUnit.MINUTES.toMillis(1))
  }
  val replyNotifications by lazy {
    createBooleanSetting(KurobaSettingKey.Application.ReplyNotifications, false)
  }
  val useSoundForReplyNotifications by lazy {
    createBooleanSetting(KurobaSettingKey.Application.UseSoundForReplyNotifications, false)
  }
  val watchLastPageNotify by lazy {
    createBooleanSetting(KurobaSettingKey.Application.WatchLastPageNotify, false)
  }
  val useSoundForLastPageNotifications by lazy {
    createBooleanSetting(KurobaSettingKey.Application.UseSoundForLastPageNotifications, false)
  }

  val filterWatchEnabled by lazy {
    createBooleanSetting(KurobaSettingKey.Application.FilterWatchEnabled, false)
  }
  val filterWatchInterval by lazy {
    createLongSetting(KurobaSettingKey.Application.FilterWatchInterval, TimeUnit.HOURS.toMillis(12))
  }
  val filterWatchUseFilterPatternForGroup by lazy {
    createBooleanSetting(KurobaSettingKey.Application.FilterWatchUseFilterPatternForGroup, true)
  }

  val threadDownloaderUpdateInterval by lazy {
    createLongSetting(KurobaSettingKey.Application.ThreadDownloaderUpdateInterval, TimeUnit.HOURS.toMillis(1))
  }
  val threadDownloaderDownloadMediaOnMeteredNetwork by lazy {
    createBooleanSetting(KurobaSettingKey.Application.ThreadDownloaderDownloadMediaOnMeteredNetwork, false)
  }

  val isCurrentThemeDark by lazy {
    createBooleanSetting(KurobaSettingKey.Application.IsCurrentThemeDark, true)
  }
  val layoutMode by lazy {
    createEnumSetting<LayoutMode>(LayoutMode::class.java, KurobaSettingKey.Application.LayoutMode, LayoutMode.Auto)
  }
  val catalogSpanCount by lazy {
    createIntSetting(KurobaSettingKey.Application.CatalogSpanCount, 0)
  }
  val albumSpanCount by lazy {
    createIntSetting(KurobaSettingKey.Application.AlbumSpanCount, 0)
  }
  val showThreadPage by lazy {
    createBooleanSetting(KurobaSettingKey.Application.ShowThreadPage, true)
  }

  val fontSize by lazy {
    createStringSetting(KurobaSettingKey.Application.FontSize, defaultFontSize().toString())
  }
  val postCellThumbnailSizePercents by lazy {
    createRangeSetting(
      key = KurobaSettingKey.Application.PostCellThumbnailSizePercents,
      default = 75,
      min = 50,
      max = 125
    )
  }
  val postFullDate by lazy {
    createBooleanSetting(KurobaSettingKey.Application.PostFullDate, false)
  }
  val postFullDateUseLocalLocale by lazy {
    createBooleanSetting(KurobaSettingKey.Application.PostFullDateUseLocalLocale, false)
  }
  val postFileInfo by lazy {
    createBooleanSetting(KurobaSettingKey.Application.PostFileInfo, true)
  }
  val catalogPostAlignmentMode by lazy {
    createEnumSetting<PostAlignmentMode>(
      clazz = PostAlignmentMode::class.java,
      key = KurobaSettingKey.Application.CatalogPostAlignmentMode,
      default = PostAlignmentMode.AlignRight
    )
  }
  val threadPostAlignmentMode by lazy {
    createEnumSetting<PostAlignmentMode>(
      clazz = PostAlignmentMode::class.java,
      key = KurobaSettingKey.Application.ThreadPostAlignmentMode,
      default = PostAlignmentMode.AlignRight
    )
  }
  val postThumbnailScaling by lazy {
    createEnumSetting<PostThumbnailScaling>(
      clazz = PostThumbnailScaling::class.java,
      key = KurobaSettingKey.Application.PostThumbnailScaling,
      default = PostThumbnailScaling.FitCenter
    )
  }
  val drawPostThumbnailBackground by lazy {
    createBooleanSetting(KurobaSettingKey.Application.DrawPostThumbnailBackground, true)
  }
  val textOnly by lazy {
    createBooleanSetting(KurobaSettingKey.Application.TextOnly, false)
  }
  val revealTextSpoilers by lazy {
    createBooleanSetting(KurobaSettingKey.Application.RevealTextSpoilers, false)
  }
  val anonymize by lazy {
    createBooleanSetting(KurobaSettingKey.Application.Anonymize, false)
  }
  val showAnonymousName by lazy {
    createBooleanSetting(KurobaSettingKey.Application.ShowAnonymousName, false)
  }
  val anonymizeIds by lazy {
    createBooleanSetting(KurobaSettingKey.Application.AnonymizeIds, false)
  }
  val markYourPostsOnScrollbar by lazy {
    createBooleanSetting(KurobaSettingKey.Application.MarkYourPostsOnScrollbar, true)
  }
  val markRepliesToYourPostOnScrollbar by lazy {
    createBooleanSetting(KurobaSettingKey.Application.MarkRepliesToYourPostOnScrollbar, true)
  }
  val markCrossThreadQuotesOnScrollbar by lazy {
    createBooleanSetting(KurobaSettingKey.Application.MarkCrossThreadQuotesOnScrollbar, false)
  }
  val markDeletedPostsOnScrollbar by lazy {
    createBooleanSetting(KurobaSettingKey.Application.MarkDeletedPostsOnScrollbar, true)
  }
  val markHotPostsOnScrollbar by lazy {
    createBooleanSetting(KurobaSettingKey.Application.MarkHotPostsOnScrollbar, false)
  }
  val shiftPostComment by lazy {
    createBooleanSetting(KurobaSettingKey.Application.ShiftPostComment, true)
  }
  val forceShiftPostComment by lazy {
    createBooleanSetting(KurobaSettingKey.Application.ForceShiftPostComment, false)
  }
  val postMultipleImagesCompactMode by lazy {
    createBooleanSetting(KurobaSettingKey.Application.PostMultipleImagesCompactMode, true)
  }

  val parseYoutubeTitlesAndDuration by lazy {
    createEnumSetting<NetworkContentAutoLoadMode>(
      clazz = NetworkContentAutoLoadMode::class.java,
      key = KurobaSettingKey.Application.ParseYoutubeTitlesAndDuration,
      default = NetworkContentAutoLoadMode.Unmetered
    )
  }
  val parseSoundCloudTitlesAndDuration by lazy {
    createEnumSetting<NetworkContentAutoLoadMode>(
      clazz = NetworkContentAutoLoadMode::class.java,
      key = KurobaSettingKey.Application.ParseSoundCloudTitlesAndDuration,
      default = NetworkContentAutoLoadMode.Unmetered
    )
  }
  val parseStreamableTitlesAndDuration by lazy {
    createEnumSetting<NetworkContentAutoLoadMode>(
      clazz = NetworkContentAutoLoadMode::class.java,
      key = KurobaSettingKey.Application.ParseStreamableTitlesAndDuration,
      default = NetworkContentAutoLoadMode.Unmetered
    )
  }
  val showLinkAlongWithTitleAndDuration by lazy {
    createBooleanSetting(KurobaSettingKey.Application.ShowLinkAlongWithTitleAndDuration, true)
  }

  val hideImages by lazy {
    createBooleanSetting(KurobaSettingKey.Application.HideImages, false)
  }
  val postThumbnailRemoveImageSpoilers by lazy {
    createBooleanSetting(KurobaSettingKey.Application.PostThumbnailRemoveImageSpoilers, false)
  }
  val mediaViewerRevealImageSpoilers by lazy {
    createBooleanSetting(KurobaSettingKey.Application.MediaViewerRevealImageSpoilers, true)
  }
  val transparencyOn by lazy {
    createBooleanSetting(KurobaSettingKey.Application.TransparencyOn, false)
  }

  val boardPostViewMode by lazy {
    createEnumSetting<BoardPostViewMode>(
      clazz = BoardPostViewMode::class.java,
      key = KurobaSettingKey.Application.BoardPostViewMode,
      default = BoardPostViewMode.List
    )
  }
  val boardOrder by lazy {
    createStringSetting(KurobaSettingKey.Application.BoardOrder, applicationSettingsParameters.defaultFilterOrderName)
  }

  val autoRefreshThread by lazy {
    createBooleanSetting(KurobaSettingKey.Application.AutoRefreshThread, true)
  }
  val controllerSwipeable by lazy {
    createBooleanSetting(KurobaSettingKey.Application.ControllerSwipeable, true)
  }
  val viewThreadControllerSwipeable by lazy {
    createBooleanSetting(KurobaSettingKey.Application.ViewThreadControllerSwipeable, true)
  }
  val openLinkConfirmation by lazy {
    createBooleanSetting(KurobaSettingKey.Application.OpenLinkConfirmation, false)
  }
  val loadLastOpenedBoardUponAppStart by lazy {
    createBooleanSetting(KurobaSettingKey.Application.LoadLastOpenedBoardUponAppStart, true)
  }
  val loadLastOpenedThreadUponAppStart by lazy {
    createBooleanSetting(KurobaSettingKey.Application.LoadLastOpenedThreadUponAppStart, true)
  }

  val postPinThread by lazy {
    createBooleanSetting(KurobaSettingKey.Application.PostPinThread, true)
  }
  val postDefaultName by lazy {
    createStringSetting(KurobaSettingKey.Application.PostDefaultName, "")
  }

  val volumeKeysScrolling by lazy {
    createBooleanSetting(KurobaSettingKey.Application.VolumeKeysScrolling, false)
  }
  val tapNoReply by lazy {
    createBooleanSetting(KurobaSettingKey.Application.TapNoReply, true)
  }
  val markUnseenPosts by lazy {
    createBooleanSetting(KurobaSettingKey.Application.MarkUnseenPosts, true)
  }
  val markSeenThreads by lazy {
    createBooleanSetting(KurobaSettingKey.Application.MarkSeenThreads, true)
  }

  val catalogSearchMode by lazy {
    createEnumSetting<CatalogOrThreadSearchMode>(
      clazz = CatalogOrThreadSearchMode::class.java,
      key = KurobaSettingKey.Application.CatalogSearchMode,
      default = CatalogOrThreadSearchMode.Highlight
    )
  }
  val threadSearchMode by lazy {
    createEnumSetting<CatalogOrThreadSearchMode>(
      clazz = CatalogOrThreadSearchMode::class.java,
      key = KurobaSettingKey.Application.ThreadSearchMode,
      default = CatalogOrThreadSearchMode.Highlight
    )
  }

  val videoAutoLoop by lazy {
    createBooleanSetting(KurobaSettingKey.Application.VideoAutoLoop, true)
  }
  val videoDefaultMuted by lazy {
    createBooleanSetting(KurobaSettingKey.Application.VideoDefaultMuted, true)
  }
  val headsetDefaultMuted by lazy {
    createBooleanSetting(KurobaSettingKey.Application.HeadsetDefaultMuted, true)
  }
  val videoAlwaysResetToStart by lazy {
    createBooleanSetting(KurobaSettingKey.Application.VideoAlwaysResetToStart, false)
  }
  val mediaViewerMaxOffscreenPages by lazy {
    createIntSetting(KurobaSettingKey.Application.MediaViewerMaxOffscreenPages, 1)
  }
  val mediaViewerAutoSwipeAfterDownload by lazy {
    createBooleanSetting(KurobaSettingKey.Application.MediaViewerAutoSwipeAfterDownload, false)
  }
  val mediaViewerDrawBehindNotch by lazy {
    createBooleanSetting(KurobaSettingKey.Application.MediaViewerDrawBehindNotch, true)
  }
  val mediaViewerSoundPostsEnabled by lazy {
    createBooleanSetting(KurobaSettingKey.Application.MediaViewerSoundPostsEnabled, false)
  }
  val mediaViewerPausePlayersWhenInBackground by lazy {
    createBooleanSetting(KurobaSettingKey.Application.MediaViewerPausePlayersWhenInBackground, true)
  }

  val imageAutoLoadNetwork by lazy {
    createEnumSetting<NetworkContentAutoLoadMode>(
      clazz = NetworkContentAutoLoadMode::class.java,
      key = KurobaSettingKey.Application.ImageAutoLoadNetwork,
      default = NetworkContentAutoLoadMode.Unmetered
    )
  }
  val videoAutoLoadNetwork by lazy {
    createEnumSetting<NetworkContentAutoLoadMode>(
      clazz = NetworkContentAutoLoadMode::class.java,
      key = KurobaSettingKey.Application.VideoAutoLoadNetwork,
      default = NetworkContentAutoLoadMode.Unmetered
    )
  }

  val diskCacheSizeMegabytes by lazy {
    createRangeSetting(
      key = KurobaSettingKey.Application.DiskCacheSizeMegabytes,
      default = 256,
      min = diskCacheSizeGetMin(),
      max = 1024
    )
  }
  val prefetchDiskCacheSizeMegabytes by lazy {
    createRangeSetting(
      key = KurobaSettingKey.Application.PrefetchDiskCacheSizeMegabytes,
      default = 512,
      min = diskCacheSizePrefetchGetMin(),
      max = 2048
    )
  }
  val diskCacheCleanupRemovePercent by lazy {
    createRangeSetting(
      key = KurobaSettingKey.Application.DiskCacheCleanupRemovePercent,
      default = 25,
      min = cleanupPercentsGetMin(),
      max = 75
    )
  }

  val okHttpAllowIpv6 by lazy {
    createBooleanSetting(KurobaSettingKey.Application.OkHttpAllowIpv6, false)
  }
  val okHttpUseDnsOverHttps by lazy {
    createBooleanSetting(KurobaSettingKey.Application.OkHttpUseDnsOverHttps, false)
  }
  val prefetchMedia by lazy {
    createBooleanSetting(KurobaSettingKey.Application.PrefetchMedia, false)
  }
  val highResCells by lazy {
    createBooleanSetting(KurobaSettingKey.Application.HighResCells, false)
  }
  val useMpvVideoPlayer by lazy {
    createBooleanSetting(KurobaSettingKey.Application.UseMpvVideoPlayer, false)
  }
  val mpvUseConfigFile by lazy {
    createBooleanSetting(KurobaSettingKey.Application.MpvUseConfigFile, false)
  }
  val colorizeTextSelectionCursors by lazy {
    createBooleanSetting(KurobaSettingKey.Application.ColorizeTextSelectionCursors, true)
  }
  val customUserAgent by lazy {
    createStringSetting(KurobaSettingKey.Application.CustomUserAgent, "")
  }

  val crashOnSafeThrow by lazy {
    createBooleanSetting(
      key = KurobaSettingKey.Application.CrashOnSafeThrow,
      default = applicationSettingsParameters.isDevOrBetaBuild()
    )
  }
  val verboseLogs by lazy {
    createBooleanSetting(KurobaSettingKey.Application.VerboseLogs, applicationSettingsParameters.isDevOrBetaBuild())
  }
  val checkUpdateApkVersionCode by lazy {
    createBooleanSetting(KurobaSettingKey.Application.CheckUpdateApkVersionCode, true)
  }
  val funThingsAreFun by lazy {
    createBooleanSetting(KurobaSettingKey.Application.FunThingsAreFun, true)
  }
  val force4chanBirthdayMode by lazy {
    createBooleanSetting(KurobaSettingKey.Application.Force4chanBirthdayMode, false)
  }
  val forceHalloweenMode by lazy {
    createBooleanSetting(KurobaSettingKey.Application.ForceHalloweenMode, false)
  }
  val forceChristmasMode by lazy {
    createBooleanSetting(KurobaSettingKey.Application.ForceChristmasMode, false)
  }
  val forceNewYearMode by lazy {
    createBooleanSetting(KurobaSettingKey.Application.ForceNewYearMode, false)
  }

  val lastImageOptions by lazy {
    createStringSetting(KurobaSettingKey.Application.LastImageOptions, "")
  }
  val scrollingTextForThreadTitles by lazy {
    createBooleanSetting(KurobaSettingKey.Application.ScrollingTextForThreadTitles, true)
  }
  val bookmarksSortOrder by lazy {
    createEnumSetting<BookmarksSortOrder>(
      clazz = BookmarksSortOrder::class.java,
      key = KurobaSettingKey.Application.BookmarksSortOrder,
      default = BookmarksSortOrder.defaultOrder()
    )
  }
  val moveNotActiveBookmarksToBottom by lazy {
    createBooleanSetting(KurobaSettingKey.Application.MoveNotActiveBookmarksToBottom, true)
  }
  val moveBookmarksWithUnreadRepliesToTop by lazy {
    createBooleanSetting(KurobaSettingKey.Application.MoveBookmarksWithUnreadRepliesToTop, true)
  }
  val ignoreDarkNightMode by lazy {
    createBooleanSetting(KurobaSettingKey.Application.IgnoreDarkNightMode, true)
  }
  val bookmarkGridViewWidth by lazy {
    createRangeSetting(
      key = KurobaSettingKey.Application.BookmarkGridViewWidth,
      default = applicationSettingsParameters.bookmarkGridViewInfo.defaultWidth,
      min = applicationSettingsParameters.bookmarkGridViewInfo.minWidth,
      max = applicationSettingsParameters.bookmarkGridViewInfo.maxWidth
    )
  }
  val mediaViewerTopGestureAction by lazy {
    createEnumSetting<ImageGestureActionType>(
      clazz = ImageGestureActionType::class.java,
      key = KurobaSettingKey.Application.MediaViewerTopGestureAction,
      default = ImageGestureActionType.CloseImage
    )
  }
  val mediaViewerBottomGestureAction by lazy {
    createEnumSetting<ImageGestureActionType>(
      clazz = ImageGestureActionType::class.java,
      key = KurobaSettingKey.Application.MediaViewerBottomGestureAction,
      default = ImageGestureActionType.SaveImage
    )
  }
  val drawerGridMode by lazy {
    createBooleanSetting(KurobaSettingKey.Application.DrawerGridMode, true)
  }
  val drawerMoveLastAccessedThreadToTop by lazy {
    createBooleanSetting(KurobaSettingKey.Application.DrawerMoveLastAccessedThreadToTop, true)
  }
  val drawerShowBookmarkedThreads by lazy {
    createBooleanSetting(KurobaSettingKey.Application.DrawerShowBookmarkedThreads, true)
  }
  val drawerShowNavigationHistory by lazy {
    createBooleanSetting(KurobaSettingKey.Application.DrawerShowNavigationHistory, true)
  }
  val drawerShowDeleteButtonShortcut by lazy {
    createBooleanSetting(KurobaSettingKey.Application.DrawerShowDeleteButtonShortcut, true)
  }
  val drawerDeleteBookmarksWhenDeletingNavHistory by lazy {
    createBooleanSetting(KurobaSettingKey.Application.DrawerDeleteBookmarksWhenDeletingNavHistory, false)
  }
  val drawerDeleteNavHistoryWhenBookmarkDeleted by lazy {
    createBooleanSetting(KurobaSettingKey.Application.DrawerDeleteNavHistoryWhenBookmarkDeleted, false)
  }
  val globalNsfwMode by lazy {
    createBooleanSetting(KurobaSettingKey.Application.GlobalNsfwMode, false)
  }

  fun mediaViewerOffscreenPagesCount(): Int {
    return runBlocking {
      if (isLowRamDevice()) {
        return@runBlocking 1
      }

      var count: Int = mediaViewerMaxOffscreenPages.read()
      if (count < 1) {
        count = 1
      }

      if (count > 2) {
        count = 2
      }

      return@runBlocking count
    }
  }

  fun isLowRamDeviceBlocking(): Boolean {
    return runBlocking { isLowRamDevice() }
  }

  suspend fun isLowRamDevice(): Boolean {
    if (isLowRamDeviceForced.read()) {
      return true
    }

    if (!AndroidUtils.isAndroidN) {
      // Consider all devices lover than N as low ram devices
      return true
    }

    val activityManager = AndroidUtils.activityManager
    return activityManager.isLowRamDevice
  }

  fun defaultFontSize(): Int {
    return 16
  }

  fun cleanupPercentsGetMin(): Int {
    if (applicationSettingsParameters.isDevBuild) {
      return 1
    }

    return 25
  }

  fun diskCacheSizePrefetchGetMin(): Int {
    if (applicationSettingsParameters.isDevBuild) {
      return 32
    }

    return 512
  }

  fun diskCacheSizeGetMin(): Int {
    if (applicationSettingsParameters.isDevBuild) {
      return 32
    }

    return 128
  }

  fun getCurrentLayoutModeBlocking(): LayoutMode {
    return runBlocking { getCurrentLayoutMode() }
  }

  suspend fun getCurrentLayoutMode(): LayoutMode {
    var layoutMode = layoutMode.read()

    if (layoutMode == LayoutMode.Auto) {
      if (applicationSettingsParameters.isTablet) {
        layoutMode = LayoutMode.Split
      } else {
        layoutMode = LayoutMode.Slide
      }
    }

    return layoutMode
  }

  fun detailsSizeSp(): Int {
    return fontSize.readBlocking().toInt() - 2
  }

  fun codeTagFontSizePx(): Int {
    return fontSize.readBlocking().toInt() - 2
  }

  fun sjisTagFontSizePx(): Int {
    return 8
  }

  fun redTextFontSizePx(): Int {
    return fontSize.readBlocking().toInt() + 2
  }

  fun isSlideLayoutModeBlocking(): Boolean {
    return runBlocking { isSlideLayoutMode() }
  }

  suspend fun isSlideLayoutMode(): Boolean {
    return getCurrentLayoutMode() == LayoutMode.Slide
  }

  fun isSplitLayoutModeBlocking(): Boolean {
    return runBlocking { isSplitLayoutMode() }
  }

  suspend fun isSplitLayoutMode(): Boolean {
    return getCurrentLayoutMode() == LayoutMode.Split
  }

  fun canCollapseToolbar(): Boolean {
    return !isSplitLayoutModeBlocking()
  }

  fun supportedFontSizes(): IntRange {
    return IntRange(10, 20)
  }

  companion object {
    const val EMPTY_JSON: String = "{}"
  }
}