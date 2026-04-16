package com.github.k1rakishou.v2


/**
 * Never modify the values of this enum because they are used as keys in the database!!!
 * */
sealed class KurobaSettingKey(val raw: String) {
  sealed class Application(key: String) : KurobaSettingKey(key) {
    data object IsLowRamDeviceForced : Application("IsLowRamDeviceForced")
    data object WatchEnabled : Application("WatchEnabled")
    data object WatchBackground : Application("WatchBackground")
    data object WatchBackgroundInterval : Application("WatchBackgroundInterval")
    data object WatchForegroundInterval : Application("WatchForegroundInterval")
    data object ReplyNotifications : Application("ReplyNotifications")
    data object UseSoundForReplyNotifications : Application("UseSoundForReplyNotifications")
    data object WatchLastPageNotify : Application("WatchLastPageNotify")
    data object UseSoundForLastPageNotifications : Application("UseSoundForLastPageNotifications")
    data object FilterWatchEnabled : Application("FilterWatchEnabled")
    data object FilterWatchInterval : Application("FilterWatchInterval")
    data object FilterWatchUseFilterPatternForGroup : Application("FilterWatchUseFilterPatternForGroup")
    data object ThreadDownloaderUpdateInterval : Application("ThreadDownloaderUpdateInterval")
    data object ThreadDownloaderDownloadMediaOnMeteredNetwork : Application("ThreadDownloaderDownloadMediaOnMeteredNetwork")
    data object IsCurrentThemeDark : Application("IsCurrentThemeDark")

    data object LayoutMode : Application("LayoutMode")
    data object CatalogSpanCount : Application("CatalogSpanCount")
    data object AlbumSpanCount : Application("AlbumSpanCount")
    data object ShowThreadPage : Application("ShowThreadPage")

    data object FontSize : Application("FontSize")
    data object PostCellThumbnailSizePercents : Application("PostCellThumbnailSizePercents")
    data object PostFullDate : Application("PostFullDate")
    data object PostFullDateUseLocalLocale : Application("PostFullDateUseLocalLocale")
    data object PostFileInfo : Application("PostFileInfo")
    data object CatalogPostAlignmentMode : Application("CatalogPostAlignmentMode")
    data object ThreadPostAlignmentMode : Application("ThreadPostAlignmentMode")
    data object PostThumbnailScaling : Application("PostThumbnailScaling")
    data object DrawPostThumbnailBackground : Application("DrawPostThumbnailBackground")
    data object TextOnly : Application("TextOnly")
    data object RevealTextSpoilers : Application("RevealTextSpoilers")
    data object Anonymize : Application("Anonymize")
    data object ShowAnonymousName : Application("ShowAnonymousName")
    data object AnonymizeIds : Application("AnonymizeIds")
    data object ShiftPostComment : Application("ShiftPostComment")
    data object ForceShiftPostComment : Application("ForceShiftPostComment")
    data object PostMultipleImagesCompactMode : Application("PostMultipleImagesCompactMode")

    data object ParseYoutubeTitlesAndDuration : Application("ParseYoutubeTitlesAndDuration")
    data object ParseSoundCloudTitlesAndDuration : Application("ParseSoundCloudTitlesAndDuration")
    data object ParseStreamableTitlesAndDuration : Application("ParseStreamableTitlesAndDuration")
    data object ShowLinkAlongWithTitleAndDuration : Application("ShowLinkAlongWithTitleAndDuration")

    data object HideImages : Application("HideImages")
    data object PostThumbnailRemoveImageSpoilers : Application("PostThumbnailRemoveImageSpoilers")
    data object MediaViewerRevealImageSpoilers : Application("MediaViewerRevealImageSpoilers")
    data object TransparencyOn : Application("TransparencyOn")

    data object BoardPostViewMode : Application("BoardPostViewMode")
    data object BoardOrder : Application("BoardOrder")

    data object AutoRefreshThread : Application("AutoRefreshThread")
    data object ControllerSwipeable : Application("ControllerSwipeable")
    data object ViewThreadControllerSwipeable : Application("ViewThreadControllerSwipeable")
    data object OpenLinkConfirmation : Application("OpenLinkConfirmation")
    data object LoadLastOpenedBoardUponAppStart : Application("LoadLastOpenedBoardUponAppStart")
    data object LoadLastOpenedThreadUponAppStart : Application("LoadLastOpenedThreadUponAppStart")

    data object PostPinThread : Application("PostPinThread")
    data object PostDefaultName : Application("PostDefaultName")

    data object VolumeKeysScrolling : Application("VolumeKeysScrolling")
    data object TapNoReply : Application("TapNoReply")
    data object MarkUnseenPosts : Application("MarkUnseenPosts")
    data object MarkSeenThreads : Application("MarkSeenThreads")

    data object CatalogSearchMode : Application("CatalogSearchMode")
    data object ThreadSearchMode : Application("ThreadSearchMode")

    data object VideoAutoLoop : Application("VideoAutoLoop")
    data object VideoDefaultMuted : Application("VideoDefaultMuted")
    data object HeadsetDefaultMuted : Application("HeadsetDefaultMuted")
    data object VideoAlwaysResetToStart : Application("VideoAlwaysResetToStart")
    data object MediaViewerMaxOffscreenPages : Application("MediaViewerMaxOffscreenPages")
    data object MediaViewerAutoSwipeAfterDownload : Application("MediaViewerAutoSwipeAfterDownload")
    data object MediaViewerDrawBehindNotch : Application("MediaViewerDrawBehindNotch")
    data object MediaViewerSoundPostsEnabled : Application("MediaViewerSoundPostsEnabled")
    data object MediaViewerPausePlayersWhenInBackground : Application("MediaViewerPausePlayersWhenInBackground")

    data object ImageAutoLoadNetwork : Application("ImageAutoLoadNetwork")
    data object VideoAutoLoadNetwork : Application("VideoAutoLoadNetwork")
    data object DiskCacheSizeMegabytes : Application("DiskCacheSizeMegabytes")
    data object PrefetchDiskCacheSizeMegabytes : Application("PrefetchDiskCacheSizeMegabytes")
    data object DiskCacheCleanupRemovePercent : Application("DiskCacheCleanupRemovePercent")

    data object OkHttpAllowIpv6 : Application("OkHttpAllowIpv6")
    data object OkHttpUseDnsOverHttps : Application("OkHttpUseDnsOverHttps")
    data object PrefetchMedia : Application("PrefetchMedia")
    data object HighResCells : Application("HighResCells")
    data object UseMpvVideoPlayer : Application("UseMpvVideoPlayer")
    data object MpvUseConfigFile : Application("MpvUseConfigFile")
    data object ColorizeTextSelectionCursors : Application("ColorizeTextSelectionCursors")
    data object CustomUserAgent : Application("CustomUserAgent")

    data object CrashOnSafeThrow : Application("CrashOnSafeThrow")
    data object VerboseLogs : Application("VerboseLogs")
    data object CheckUpdateApkVersionCode : Application("CheckUpdateApkVersionCode")
    data object FunThingsAreFun : Application("FunThingsAreFun")
    data object Force4chanBirthdayMode : Application("Force4chanBirthdayMode")
    data object ForceHalloweenMode : Application("ForceHalloweenMode")
    data object ForceChristmasMode : Application("ForceChristmasMode")
    data object ForceNewYearMode : Application("ForceNewYearMode")

    data object LastImageOptions : Application("LastImageOptions")
    data object ScrollingTextForThreadTitles : Application("ScrollingTextForThreadTitles")
    data object BookmarksSortOrder : Application("BookmarksSortOrder")
    data object MoveNotActiveBookmarksToBottom : Application("MoveNotActiveBookmarksToBottom")
    data object MoveBookmarksWithUnreadRepliesToTop : Application("MoveBookmarksWithUnreadRepliesToTop")
    data object IgnoreDarkNightMode : Application("IgnoreDarkNightMode")
    data object BookmarkGridViewWidth : Application("BookmarkGridViewWidth")
    data object MediaViewerTopGestureAction : Application("MediaViewerTopGestureAction")
    data object MediaViewerBottomGestureAction : Application("MediaViewerBottomGestureAction")
    data object DrawerGridMode : Application("DrawerGridMode")
    data object DrawerMoveLastAccessedThreadToTop : Application("DrawerMoveLastAccessedThreadToTop")
    data object DrawerShowBookmarkedThreads : Application("DrawerShowBookmarkedThreads")
    data object DrawerShowNavigationHistory : Application("DrawerShowNavigationHistory")
    data object DrawerShowDeleteButtonShortcut : Application("DrawerShowDeleteButtonShortcut")
    data object DrawerDeleteBookmarksWhenDeletingNavHistory : Application("DrawerDeleteBookmarksWhenDeletingNavHistory")
    data object DrawerDeleteNavHistoryWhenBookmarkDeleted : Application("DrawerDeleteNavHistoryWhenBookmarkDeleted")
    data object MarkYourPostsOnScrollbar : Application("MarkYourPostsOnScrollbar")
    data object MarkRepliesToYourPostOnScrollbar : Application("MarkRepliesToYourPostOnScrollbar")
    data object MarkCrossThreadQuotesOnScrollbar : Application("MarkCrossThreadQuotesOnScrollbar")
    data object MarkDeletedPostsOnScrollbar : Application("MarkDeletedPostsOnScrollbar")
    data object MarkHotPostsOnScrollbar : Application("MarkHotPostsOnScrollbar")
    data object GlobalNsfwMode : Application("GlobalNsfwMode")
    data object AppUpdate : Application("AppUpdate")
  }

  sealed class Internal(key: String) : KurobaSettingKey(key) {
    data object HasNewApkUpdate : Internal("HasNewApkUpdate")
    data object PreviousVersion : Internal("PreviousVersion")
    data object UpdateCheckTime : Internal("UpdateCheckTime")
    data object PreviousBuildNumber : Internal("PreviousBuildNumber")
    data object ApkUpdateInfoJson : Internal("ApkUpdateInfoJson")
    data object ViewThreadBookmarksGridMode : Internal("ViewThreadBookmarksGridMode")
    data object AlbumLayoutGridMode : Internal("AlbumLayoutGridMode")
    data object ShittyPhonesBackgroundLimitationsExplanationDialogShown : Internal("ShittyPhonesBackgroundLimitationsExplanationDialogShown")
    data object BookmarksRecyclerIndexAndTop : Internal("BookmarksRecyclerIndexAndTop")
    data object ProxyEditingNotificationShown : Internal("ProxyEditingNotificationShown")
    data object LastRememberedFilePicker : Internal("LastRememberedFilePicker")
    data object ThemesIgnoreSystemDayNightModeMessageShown : Internal("ThemesIgnoreSystemDayNightModeMessageShown")
    data object ImageViewerImmersiveModeEnabled : Internal("ImageViewerImmersiveModeEnabled")
    data object ImageSaverV2PersistedOptions : Internal("ImageSaverV2PersistedOptions")
    data object ReorderableBottomNavViewButtons : Internal("ReorderableBottomNavViewButtons")
    data object ReorderableMediaViewerActions : Internal("ReorderableMediaViewerActions")
    data object ShowAlbumViewsImageDetails : Internal("ShowAlbumViewsImageDetails")
    data object ThreadDownloaderOptions : Internal("ThreadDownloaderOptions")
    data object ThreadDownloaderArchiveWarningShown : Internal("ThreadDownloaderArchiveWarningShown")
    data object DontKeepActivitiesWarningShown : Internal("DontKeepActivitiesWarningShown")
    data object RemoteImageSearchSettings : Internal("RemoteImageSearchSettings")
    data object NewReplyLayoutTutorialFinished : Internal("NewReplyLayoutTutorialFinished")
    data object AlwaysRandomizePickedFilesNames : Application("AlwaysRandomizePickedFilesNames")
  }

  sealed class NonBackupable(key: String) : KurobaSettingKey(key) {
    data object ApplicationMigrationVersion : NonBackupable("ApplicationMigrationVersion")
    data object SettingMigrationPerformed : NonBackupable("SettingMigrationPerformed")
  }

  sealed class Mpv(key: String) : KurobaSettingKey(key) {
    data object HardwareDecoding : Mpv("HardwareDecoding")
    data object VideoFastCode : Mpv("VideoFastCode")
    data object GpuNextVO : Mpv("GpuNextVO")
    data object LastMpvLibsUpdateCheckTime : Mpv("LastMpvLibsUpdateCheckTime")
    data object MpvLibsUpdate : Mpv("MpvLibsUpdate")
  }

  sealed class Site(siteName: String, key: String) : KurobaSettingKey("${siteName}_${key}") {
    data class SiteDomainSetting(val siteName: String) : Site(siteName, "SiteDomainSetting")
    data class ConcurrentFileDownloadingChunks(val siteName: String) : Site(siteName, "ConcurrentFileDownloadingChunks")
    data class CloudFlareClearanceCookieMap(val siteName: String) : Site(siteName, "CloudFlareClearanceCookieMap")
    data class LastUsedReplyMode(val siteName: String) : Site(siteName, "LastUsedReplyMode")
    data class IgnoreReplyCooldowns(val siteName: String) : Site(siteName, "IgnoreReplyCooldowns")
    data class LastSiteBoardsRefreshTime(val siteName: String) : Site(siteName, "LastSiteBoardsRefreshTime")

    sealed class Chan4(siteName: String, key: String) : Site(siteName, key) {
      data class PassToken(val siteName: String) : Chan4(siteName, "PassToken")
      data class PassPin(val siteName: String) : Chan4(siteName, "PassPin")
      data class PassId(val siteName: String) : Chan4(siteName, "PassId")
      data class Flag(val siteName: String) : Chan4(siteName, "Flag")
      data class PostingCookie(val siteName: String) : Chan4(siteName, "PostingCookie")
      data class CheckPostAcknowledged(val siteName: String) : Chan4(siteName, "CheckPostAcknowledged")
      data class CaptchaType(val siteName: String) : Chan4(siteName, "CaptchaType")
      data class CaptchaSettings(val siteName: String) : Chan4(siteName, "CaptchaSettings")
      data class EmailVerificationCookie(val siteName: String) : Chan4(siteName, "EmailVerificationCookie")
    }

    sealed class Dvach(siteName: String, key: String) : Site(siteName, key) {
      data class CaptchaType(val siteName: String) : Dvach(siteName, "CaptchaType")
      data class PassCodeInfo(val siteName: String) : Dvach(siteName, "PassCodeInfo")
      data class Passcode(val siteName: String) : Dvach(siteName, "Passcode")
      data class PasscodeCookie(val siteName: String) : Dvach(siteName, "PasscodeCookie")
      data class UserCodeCookie(val siteName: String) : Dvach(siteName, "UserCodeCookie")
      data class DvachAntiSpamCookie(val siteName: String) : Dvach(siteName, "DvachAntiSpamCookie")
    }

    sealed class Lynxchan(siteName: String, key: String) : Site(siteName, key) {
      data class CaptchaId(val siteName: String) : Lynxchan(siteName, "CaptchaId")
      data class BypassCookie(val siteName: String) : Lynxchan(siteName, "BypassCookie")
      data class ExtraCookie(val siteName: String) : Lynxchan(siteName, "ExtraCookie")
    }

    sealed class Chan8(siteName: String, key: String) : Site(siteName, key) {
      data class PowToken(val siteName: String) : Chan8(siteName, "PowToken")
      data class PowId(val siteName: String) : Chan8(siteName, "PowId")
    }
  }
}