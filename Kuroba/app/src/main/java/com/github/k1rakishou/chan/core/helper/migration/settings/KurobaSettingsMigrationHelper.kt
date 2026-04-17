package com.github.k1rakishou.chan.core.helper.migration.settings

import android.content.Context
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4CaptchaSettings
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4SiteSettings
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach
import com.github.k1rakishou.chan.core.site.sites.dvach.DvachPasscodeInfo
import com.github.k1rakishou.chan.core.site.sites.dvach.DvachSiteSettings
import com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8.Chan8Moe
import com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8.Chan8MoeSiteSettings
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.BaseLynxchanSite
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanSiteSettings
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.common.StringUtils.asFormattedToken
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.deprecated.ChanSettingsDeprecated
import com.github.k1rakishou.deprecated.OptionSettingItem
import com.github.k1rakishou.deprecated.SharedPreferencesSettingProvider
import com.github.k1rakishou.deprecated.persist_state.ReplyModeDeprecated
import com.github.k1rakishou.deprecated.prefs.CookieSetting
import com.github.k1rakishou.deprecated.prefs.MapSetting
import com.github.k1rakishou.deprecated.prefs.OptionsSetting
import com.github.k1rakishou.v2.ApplicationSettings
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.ApkUpdateInfoJson
import com.github.k1rakishou.v2.parameters.BoardPostViewMode
import com.github.k1rakishou.v2.parameters.BookmarksSortOrder
import com.github.k1rakishou.v2.parameters.CatalogOrThreadSearchMode
import com.github.k1rakishou.v2.parameters.ConcurrentFileDownloadingChunks
import com.github.k1rakishou.v2.parameters.ImageGestureActionType
import com.github.k1rakishou.v2.parameters.ImageSaverV2Options
import com.github.k1rakishou.v2.parameters.LayoutMode
import com.github.k1rakishou.v2.parameters.NetworkContentAutoLoadMode
import com.github.k1rakishou.v2.parameters.PostAlignmentMode
import com.github.k1rakishou.v2.parameters.PostThumbnailScaling
import com.github.k1rakishou.v2.parameters.RecyclerIndexAndTopInfo
import com.github.k1rakishou.v2.parameters.RemoteImageSearchSettings
import com.github.k1rakishou.v2.parameters.ReorderableBottomNavViewButtons
import com.github.k1rakishou.v2.parameters.ReorderableMediaViewerActions
import com.github.k1rakishou.v2.parameters.ReplyMode
import com.github.k1rakishou.v2.parameters.ThreadDownloaderOptions
import com.github.k1rakishou.v2.settings.KurobaBooleanSetting
import com.github.k1rakishou.v2.settings.KurobaCookieSetting
import com.github.k1rakishou.v2.settings.KurobaEnumSetting
import com.github.k1rakishou.v2.settings.KurobaIntSetting
import com.github.k1rakishou.v2.settings.KurobaLongSetting
import com.github.k1rakishou.v2.settings.KurobaMapSetting
import com.github.k1rakishou.v2.settings.KurobaMoshiSetting
import com.github.k1rakishou.v2.settings.KurobaStringSetting
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.TimeUnit

@Suppress("all")
class KurobaSettingsMigrationHelper(
  private val context: Context,
  private val kurobaSettings: KurobaSettings,
  private val moshi: Moshi,
  private val gson: Gson,
  private val siteManager: SiteManager
) {
  fun perform(forced: Boolean): Boolean {
    if (!forced) {
      val settingMigrationPerformed = kurobaSettings.nonBackupable.settingMigrationPerformed.readBlocking()
      if (settingMigrationPerformed) {
        // Everything is already migrated/nothing to migrate
        Logger.debug(TAG) { "All settings were already migrated, nothing to do." }
        return false
      }
    }

    try {
      try {
        Logger.debug(TAG) { "Migrating ChanSettings..." }
        migrateChanSettings(context, kurobaSettings)
        Logger.debug(TAG) { "Migrating ChanSettings... success!" }
      } catch (error: Throwable) {
        Logger.error(TAG, error) { "Migrating ChanSettings... error!" }
      }

      try {
        Logger.debug(TAG) { "Migrating PersistableChanState..." }
        migratePersistableChanState(context, kurobaSettings)
        Logger.debug(TAG) { "Migrating PersistableChanState... success!" }
      } catch (error: Throwable) {
        Logger.error(TAG, error) { "Migrating PersistableChanState... error!" }
      }

      runBlocking(Dispatchers.IO) { siteManager.awaitUntilInitialized() }

      siteManager.viewSitesOrdered { _, site ->
        val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/site_preferences_${site.descriptor.siteName}.xml")

        try {
          if (!prefsFile.exists()) {
            Logger.debug(TAG) { "SharedPrefs file for site ${site.descriptor} doesn't exist, exiting." }
            return@viewSitesOrdered true
          }

          Logger.debug(TAG) { "Migrating sharedprefs file '${prefsFile.path}' for site ${site.descriptor}..." }

          val prefs = SharedPreferencesSettingProvider(AppModuleAndroidUtils.getPreferencesForSite(site.descriptor))
          migrateCommonSiteSettings(site, prefs)

          when (site) {
            is Chan4 -> migrate4chanSiteSettings(site, prefs)
            is Dvach -> migrateDvachSiteSettings(site, prefs)
            is BaseLynxchanSite -> {
              migrateLynxchanSiteSettings(site, prefs)

              if (site is Chan8Moe) {
                migrateChan8MoeSiteSettings(site, prefs)
              }
            }
          }

          Logger.debug(TAG) { "Migrating sharedprefs file '${prefsFile.path}' for site ${site.descriptor}... success!" }
        } catch (error: Throwable) {
          Logger.error(TAG, error) { "Migrating sharedprefs file '${prefsFile.path}' for site ${site.descriptor}... error!" }
        }

        return@viewSitesOrdered true
      }

      siteManager.viewSitesOrdered { _, site ->
        val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/site_preferences_${site.descriptor.siteName}.xml")
        if (prefsFile.exists()) {
          Logger.debug(TAG) { "SharedPrefs deleting sharedprefs file '${prefsFile.path}' for site ${site.descriptor}" }
          prefsFile.delete()
        }

        return@viewSitesOrdered true
      }

      // Kill any remaining shared prefs. There might be some left for sites that are not supported anymore.
      // We don't want to keep them.
      run {
        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        (sharedPrefsDir.listFiles() ?: emptyArray()).forEach { file ->
          if (file.isFile && file.name.startsWith("site_preferences_") && file.name.endsWith(".xml")) {
            Logger.debug(TAG) { "Deleting remaining sharedprefs file '${file.path}'" }
            file.delete()
          }
        }
      }
    } finally {
      kurobaSettings.nonBackupable.settingMigrationPerformed.writeBlocking(true)
    }

    Logger.debug(TAG) { "Successfully migrated settings!" }

    // Some stuff was migrated, just to be safe we need to restart the app
    return true
  }

  private fun migrateChan8MoeSiteSettings(
    site: BaseLynxchanSite,
    prefs: SharedPreferencesSettingProvider
  ) {
    val settings = site.requireSiteSettings(Chan8MoeSiteSettings::class.java)

    run {
      val oldSetting = CookieSetting(moshi, prefs, "pow_token")
      migrateCookieSetting(oldSetting, settings.powToken)
    }

    run {
      val oldSetting = CookieSetting(moshi, prefs, "pow_id")
      migrateCookieSetting(oldSetting, settings.powId)
    }
  }

  private fun migrateLynxchanSiteSettings(
    site: BaseLynxchanSite,
    prefs: SharedPreferencesSettingProvider
  ) {
    val settings = site.requireSiteSettings(LynxchanSiteSettings::class.java)

    run {
      val oldSetting = CookieSetting(moshi, prefs, "captcha_id")
      migrateCookieSetting(oldSetting, settings.captchaIdCookie)
    }

    run {
      val oldSetting = CookieSetting(moshi, prefs, "bypass_cookie")
      migrateCookieSetting(oldSetting, settings.bypassCookie)
    }

    run {
      val oldSetting = CookieSetting(moshi, prefs, "extra_cookie")
      migrateCookieSetting(oldSetting, settings.extraCookie)
    }
  }

  private fun migrateDvachSiteSettings(
    site: Dvach,
    prefs: SharedPreferencesSettingProvider
  ) {
    val settings = site.requireSiteSettings(DvachSiteSettings::class.java)

    settings.captchaType.writeBlocking(Dvach.CaptchaType.DVACH_CAPTCHA_EMOJI)

    migrateGsonSetting(
      prefs = prefs,
      name = "preference_pass_code_info",
      clazz = DvachPasscodeInfoDeprecated::class.java,
      newSetting = settings.passCodeInfo,
      oldToNewMapper = { old ->
        DvachPasscodeInfo(
          files = old.files,
          filesSize = old.filesSize
        )
      }
    )

    migrateStringSetting(prefs, "preference_pass_code", settings.passCode)
    migrateStringSetting(prefs, "preference_pass_cookie", settings.passCookie)
    migrateStringSetting(prefs, "user_code_cookie", settings.userCodeCookie)
    migrateStringSetting(prefs, "dvach_anti_spam_cookie", settings.antiSpamCookie)
  }

  private fun migrate4chanSiteSettings(
    site: Chan4,
    prefs: SharedPreferencesSettingProvider
  ) {
    val settings = site.requireSiteSettings(Chan4SiteSettings::class.java)

    tryMigrateSetting(settings.postingCookie.key) {
      val value = prefs.getString("preference_4chan_captcha_cookie", "")
      if (value.isBlank() || value == settings.postingCookie.default?.value) {
        return@tryMigrateSetting
      }

      Logger.debug(TAG) {
        "migrateStringSetting() preference_4chan_captcha_cookie -> ${settings.postingCookie.key.raw}, " +
          "value: ${value.asFormattedToken()}"
      }

      settings.postingCookie.writeBlocking(
        KurobaCookie(
          value = value,
          expiration = KurobaCookie.Expiration.Time(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(180))
        )
      )
    }

    migrateStringSetting(prefs, "preference_pass_token", settings.passToken)
    migrateStringSetting(prefs, "preference_pass_pin", settings.passPin)
    migrateStringSetting(prefs, "preference_pass_id", settings.passId)
    migrateStringSetting(prefs, "preference_flag_chan4", settings.lastUsedFlagPerBoard)

    migrateBooleanSetting(prefs, "chan_4chan_post_acknowledged", settings.checkPostAcknowledged)

    settings.captchaType.writeBlocking(Chan4.CaptchaType.CHAN4_CAPTCHA)

    migrateGsonSetting(
      prefs = prefs,
      name = "chan4_captcha_settings",
      clazz = Chan4CaptchaSettingsDeprecated::class.java,
      newSetting = settings.captchaSettings,
      oldToNewMapper = { old ->
        Chan4CaptchaSettings(
          rememberCaptchaCookies = old.rememberCaptchaCookies,
          captchaTicket = old.captchaTicket,
          lastRefreshTime = old.lastRefreshTime
        )
      }
    )
  }

  private fun migrateCommonSiteSettings(
    site: Site,
    prefs: SharedPreferencesSettingProvider
  ) {
    migrateStringSetting(prefs, "site_domain", site.commonSettings.siteDomainSetting)
    migrateBooleanSetting(prefs, "ignore_reply_cooldowns", site.commonSettings.ignoreReplyCooldowns)
    migrateLongSetting(prefs, "last_site_boards_refresh_time", site.commonSettings.lastSiteBoardsRefreshTime)

    run {
      val oldSetting = MapSetting(
        moshi = moshi,
        mapperFrom = { mapSettingEntry ->
          return@MapSetting MapSetting.KeyValue(
            key = mapSettingEntry.key,
            value = mapSettingEntry.value
          )
        },
        mapperTo = { keyValue ->
          return@MapSetting MapSetting.MapSettingEntry(
            key = keyValue.key,
            value = keyValue.value
          )
        },
        settingProvider = prefs,
        key = "cloud_flare_clearance_cookie_map",
        def = emptyMap()
      )

      migrateMapSetting(
        oldSetting = oldSetting,
        newSetting = site.commonSettings.cloudFlareClearanceCookieMap,
        entryMapper = { mapEntry -> mapEntry }
      )
    }

    run {
      val oldSetting = OptionsSetting(
        prefs,
        "concurrent_download_chunk_count",
        ChanSettingsDeprecated.ConcurrentFileDownloadingChunks::class.java,
        ChanSettingsDeprecated.ConcurrentFileDownloadingChunks.Two
      )

      migrateEnumSetting(
        newSetting = site.commonSettings.concurrentFileDownloadingChunks,
        oldSetting = oldSetting,
        oldToNewMapper = { old ->
          when (old) {
            ChanSettingsDeprecated.ConcurrentFileDownloadingChunks.One -> ConcurrentFileDownloadingChunks.One
            ChanSettingsDeprecated.ConcurrentFileDownloadingChunks.Two -> ConcurrentFileDownloadingChunks.Two
            ChanSettingsDeprecated.ConcurrentFileDownloadingChunks.Four -> ConcurrentFileDownloadingChunks.Four
          }
        }
      )
    }

    run {
      val oldSetting = OptionsSetting(
        prefs,
        "last_used_reply_mode",
        ReplyModeDeprecated::class.java,
        ReplyModeDeprecated.Unknown
      )

      migrateEnumSetting(
        newSetting = site.commonSettings.lastUsedReplyMode,
        oldSetting = oldSetting,
        oldToNewMapper = { old ->
          when (old) {
            ReplyModeDeprecated.Unknown -> ReplyMode.Unknown
            ReplyModeDeprecated.ReplyModeSolveCaptchaManually -> ReplyMode.ReplyModeSolveCaptchaManually
            ReplyModeDeprecated.ReplyModeSendWithoutCaptcha -> ReplyMode.ReplyModeSendWithoutCaptcha
            ReplyModeDeprecated.ReplyModeSolveCaptchaAuto -> ReplyMode.ReplyModeSolveCaptchaAuto
            ReplyModeDeprecated.ReplyModeUsePasscode -> ReplyMode.ReplyModeUsePasscode
          }
        }
      )
    }
  }

  private fun migratePersistableChanState(
    context: Context,
    kurobaSettings: KurobaSettings
  ) {
    val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/chan_state.xml")
    if (!prefsFile.exists()) {
      Logger.debug(TAG) { "SharedPrefs file '${prefsFile.path}' doesn't exist, exiting." }
      return
    }

    try {
      val prefs = SharedPreferencesSettingProvider(AndroidUtils.appState)
      val internal = kurobaSettings.internal

      migrateBooleanSetting(prefs, "has_new_apk_update", internal.hasNewApkUpdate)
      migrateBooleanSetting(prefs, "view_thread_bookmarks_grid_mode", internal.viewThreadBookmarksGridMode)
      migrateBooleanSetting(prefs, "album_layout_grid_mode", internal.albumLayoutGridMode)
      migrateBooleanSetting(prefs, "shitty_phones_background_limitations_explanation_dialog_shown", internal.shittyPhonesBackgroundLimitationsExplanationDialogShown)
      migrateBooleanSetting(prefs, "proxy_editing_notification_shown", internal.proxyEditingNotificationShown)
      migrateBooleanSetting(prefs, "themes_ignore_system_day_night_mode_message_shown", internal.themesIgnoreSystemDayNightModeMessageShown)
      migrateBooleanSetting(prefs, "image_viewer_immersive_mode_enabled", internal.imageViewerImmersiveModeEnabled)
      migrateBooleanSetting(prefs, "show_album_views_image_details", internal.showAlbumViewsImageDetails)
      migrateBooleanSetting(prefs, "thread_downloader_archive_warning_shown", internal.threadDownloaderArchiveWarningShown)
      migrateBooleanSetting(prefs, "dont_keep_activities_warning_shown", internal.dontKeepActivitiesWarningShown)
      migrateBooleanSetting(prefs, "new_reply_layout_tutorial_finished", internal.newReplyLayoutTutorialFinished)

      migrateIntSetting(prefs, "previous_version", internal.previousVersion)

      migrateLongSetting(prefs, "update_check_time", internal.updateCheckTime)
      migrateLongSetting(prefs, "previous_build_number", internal.previousBuildNumber)

      migrateStringSetting(prefs, "last_remembered_file_picker", internal.lastRememberedFilePicker)

      migrateMoshiSetting(prefs, "bookmarks_recycler_index_and_top", RecyclerIndexAndTopInfo::class.java, internal.bookmarksRecyclerIndexAndTop)
      migrateMoshiSetting(prefs, "image_saver_options", ImageSaverV2Options::class.java, internal.imageSaverV2PersistedOptions)
      migrateMoshiSetting(prefs, "apk_update_info", ApkUpdateInfoJson::class.java, internal.apkUpdateInfoJson)
      migrateMoshiSetting(prefs, "bottom_nav_view_buttons_ordered", ReorderableBottomNavViewButtons::class.java, internal.reorderableBottomNavViewButtons)
      migrateMoshiSetting(prefs, "media_viewer_action_buttons_ordered", ReorderableMediaViewerActions::class.java, internal.reorderableMediaViewerActions)
      migrateMoshiSetting(prefs, "thread_downloader_options", ThreadDownloaderOptions::class.java, internal.threadDownloaderOptions)
      migrateMoshiSetting(prefs, "remote_image_search_settings", RemoteImageSearchSettings::class.java, internal.remoteImageSearchSettings)
      migrateBooleanSetting(prefs, "always_randomized_picked_files_names", internal.alwaysRandomizePickedFilesNames)
    } finally {
      prefsFile.delete()
    }
  }

  private fun migrateChanSettings(
    context: Context,
    kurobaSettings: KurobaSettings
  ) {
    val applicationId = kurobaSettings.application.applicationSettingsParameters.applicationId
    val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/${applicationId}_preferences.xml")

    if (!prefsFile.exists()) {
      Logger.debug(TAG) { "SharedPrefs file '${prefsFile.path}' doesn't exist, exiting." }
      return
    }

    try {
      ChanSettingsDeprecated.init()

      val prefs = SharedPreferencesSettingProvider(AndroidUtils.appMainPreferences)
      val application = kurobaSettings.application

      run {
        val default = application.detailsSizeSp().toString()
        application.fontSize.writeBlocking(prefs.getString("preference_font", default) ?: default)
      }

      migrateStringSetting(prefs, "preference_board_order", application.boardOrder)
      migrateStringSetting(prefs, "preference_default_name", application.postDefaultName)
      migrateStringSetting(prefs, "custom_user_agent", application.customUserAgent)
      migrateStringSetting(prefs, "last_image_options", application.lastImageOptions)

      migrateLongSetting(prefs, "preference_watch_background_interval", application.watchBackgroundInterval)
      migrateLongSetting(prefs, "preference_watch_foreground_interval", application.watchForegroundInterval)
      migrateLongSetting(prefs, "preference_filter_watch_interval", application.filterWatchInterval)
      migrateLongSetting(prefs, "preference_thread_downloader_update_interval", application.threadDownloaderUpdateInterval)

      migrateIntSetting(prefs, "preference_board_grid_span_count", application.catalogSpanCount)
      migrateIntSetting(prefs, "preference_album_span_count", application.albumSpanCount)
      migrateIntSetting(prefs, "post_cell_thumbnail_size_percents", application.postCellThumbnailSizePercents)
      migrateIntSetting(prefs, "preference_media_viewer_max_offscreen_pages", application.mediaViewerMaxOffscreenPages)
      migrateIntSetting(prefs, "disk_cache_size", application.diskCacheSizeMegabytes)
      migrateIntSetting(prefs, "prefetch_disk_cache_size", application.prefetchDiskCacheSizeMegabytes)
      migrateIntSetting(prefs, "disk_cache_cleanup_remove_files_percent", application.diskCacheCleanupRemovePercent)
      migrateIntSetting(prefs, "bookmark_grid_view_width", application.bookmarkGridViewWidth)

      migrateBooleanSetting(prefs, "is_low_ram_device_forced", application.isLowRamDeviceForced)
      migrateBooleanSetting(prefs, "preference_watch_enabled", application.watchEnabled)
      migrateBooleanSetting(prefs, "preference_watch_background_enabled", application.watchBackground)
      migrateBooleanSetting(prefs, "reply_notifications", application.replyNotifications)
      migrateBooleanSetting(prefs, "use_sound_for_reply_notifications", application.useSoundForReplyNotifications)
      migrateBooleanSetting(prefs, "preference_watch_last_page_notify", application.watchLastPageNotify)
      migrateBooleanSetting(prefs, "use_sound_for_last_page_notifications", application.useSoundForLastPageNotifications)
      migrateBooleanSetting(prefs, "preference_filter_watch_enabled", application.filterWatchEnabled)
      migrateBooleanSetting(prefs, "preference_filter_use_filter_pattern_for_group", application.filterWatchUseFilterPatternForGroup)
      migrateBooleanSetting(prefs, "preference_thread_downloader_download_media_on_metered_network", application.threadDownloaderDownloadMediaOnMeteredNetwork)
      migrateBooleanSetting(prefs, "is_current_theme_dark", application.isCurrentThemeDark)
      migrateBooleanSetting(prefs, "never_show_page_number", application.showThreadPage)
      migrateBooleanSetting(prefs, "preference_post_full_date", application.postFullDate)
      migrateBooleanSetting(prefs, "preference_post_full_date_use_local_locale", application.postFullDateUseLocalLocale)
      migrateBooleanSetting(prefs, "preference_post_file_name", application.postFileInfo)
      migrateBooleanSetting(prefs, "draw_post_thumbnail_background", application.drawPostThumbnailBackground)
      migrateBooleanSetting(prefs, "preference_text_only", application.textOnly)
      migrateBooleanSetting(prefs, "preference_reveal_text_spoilers", application.revealTextSpoilers)
      migrateBooleanSetting(prefs, "preference_anonymize", application.anonymize)
      migrateBooleanSetting(prefs, "preference_show_anonymous_name", application.showAnonymousName)
      migrateBooleanSetting(prefs, "preference_anonymize_ids", application.anonymizeIds)
      migrateBooleanSetting(prefs, "mark_your_posts_on_scrollbar", application.markYourPostsOnScrollbar)
      migrateBooleanSetting(prefs, "mark_replies_to_your_posts_on_scrollbar", application.markRepliesToYourPostOnScrollbar)
      migrateBooleanSetting(prefs, "mark_deleted_on_scrollbar", application.markDeletedPostsOnScrollbar)
      migrateBooleanSetting(prefs, "mark_hot_posts_on_scrollbar", application.markHotPostsOnScrollbar)
      migrateBooleanSetting(prefs, "mark_cross_thread_quotes_on_scrollbar", application.markCrossThreadQuotesOnScrollbar)
      migrateBooleanSetting(prefs, "shift_post_comment", application.shiftPostComment)
      migrateBooleanSetting(prefs, "force_shift_post_comment", application.forceShiftPostComment)
      migrateBooleanSetting(prefs, "post_multiple_images_compact_mode", application.postMultipleImagesCompactMode)
      migrateBooleanSetting(prefs, "show_link_along_with_title_and_duration", application.showLinkAlongWithTitleAndDuration)
      migrateBooleanSetting(prefs, "preference_hide_images", application.hideImages)
      migrateBooleanSetting(prefs, "preference_reveal_image_spoilers", application.postThumbnailRemoveImageSpoilers)
      migrateBooleanSetting(prefs, "preference_auto_unspoil_images", application.mediaViewerRevealImageSpoilers)
      migrateBooleanSetting(prefs, "image_transparency_on", application.transparencyOn)
      migrateBooleanSetting(prefs, "preference_auto_refresh_thread", application.autoRefreshThread)
      migrateBooleanSetting(prefs, "preference_controller_swipeable", application.controllerSwipeable)
      migrateBooleanSetting(prefs, "preference_view_thread_controller_swipeable", application.viewThreadControllerSwipeable)
      migrateBooleanSetting(prefs, "preference_open_link_confirmation", application.openLinkConfirmation)
      migrateBooleanSetting(prefs, "load_last_opened_board_upon_app_start", application.loadLastOpenedBoardUponAppStart)
      migrateBooleanSetting(prefs, "load_last_opened_thread_upon_app_start", application.loadLastOpenedThreadUponAppStart)
      migrateBooleanSetting(prefs, "preference_pin_on_post", application.postPinThread)
      migrateBooleanSetting(prefs, "preference_volume_key_scrolling", application.volumeKeysScrolling)
      migrateBooleanSetting(prefs, "preference_tap_no_reply", application.tapNoReply)
      migrateBooleanSetting(prefs, "preference_mark_unseen_posts", application.markUnseenPosts)
      migrateBooleanSetting(prefs, "preference_mark_seen_threads", application.markSeenThreads)
      migrateBooleanSetting(prefs, "preference_video_loop", application.videoAutoLoop)
      migrateBooleanSetting(prefs, "preference_video_default_muted", application.videoDefaultMuted)
      migrateBooleanSetting(prefs, "preference_headset_default_muted", application.headsetDefaultMuted)
      migrateBooleanSetting(prefs, "preference_video_always_reset_to_start", application.videoAlwaysResetToStart)
      migrateBooleanSetting(prefs, "preference_media_viewer_auto_swipe_after_download", application.mediaViewerAutoSwipeAfterDownload)
      migrateBooleanSetting(prefs, "preference_media_viewer_draw_behind_notch", application.mediaViewerDrawBehindNotch)
      migrateBooleanSetting(prefs, "preference_media_viewer_sound_posts_enabled", application.mediaViewerSoundPostsEnabled)
      migrateBooleanSetting(prefs, "preference_media_viewer_pause_players_when_in_background", application.mediaViewerPausePlayersWhenInBackground)
      migrateBooleanSetting(prefs, "ok_http_allow_ipv6", application.okHttpAllowIpv6)
      migrateBooleanSetting(prefs, "ok_http_use_dns_over_https", application.okHttpUseDnsOverHttps)
      migrateBooleanSetting(prefs, "preference_auto_load_thread", application.prefetchMedia)
      migrateBooleanSetting(prefs, "high_res_cells", application.highResCells)
      migrateBooleanSetting(prefs, "use_mpv_video_player", application.useMpvVideoPlayer)
      migrateBooleanSetting(prefs, "mpv_use_config_file", application.mpvUseConfigFile)
      migrateBooleanSetting(prefs, "colorize_text_selection_cursors", application.colorizeTextSelectionCursors)
      migrateBooleanSetting(prefs, "crash_on_safe_throw", application.crashOnSafeThrow)
      migrateBooleanSetting(prefs, "verbose_logs", application.verboseLogs)
      migrateBooleanSetting(prefs, "check_update_apk_version_code", application.checkUpdateApkVersionCode)
      migrateBooleanSetting(prefs, "fun_things_are_fun", application.funThingsAreFun)
      migrateBooleanSetting(prefs, "force_4chan_birthday_mode", application.force4chanBirthdayMode)
      migrateBooleanSetting(prefs, "force_halloween_mode", application.forceHalloweenMode)
      migrateBooleanSetting(prefs, "force_christmas_mode", application.forceChristmasMode)
      migrateBooleanSetting(prefs, "force_new_year_mode", application.forceNewYearMode)
      migrateBooleanSetting(prefs, "ignore_dark_night_mode", application.ignoreDarkNightMode)
      migrateBooleanSetting(prefs, "move_not_active_bookmarks_to_bottom", application.moveNotActiveBookmarksToBottom)
      migrateBooleanSetting(prefs, "move_bookmarks_with_unread_replies_to_top", application.moveBookmarksWithUnreadRepliesToTop)
      migrateBooleanSetting(prefs, "scrolling_text_for_thread_titles", application.scrollingTextForThreadTitles)
      migrateBooleanSetting(prefs, "drawer_move_last_accessed_thread_to_top", application.drawerMoveLastAccessedThreadToTop)
      migrateBooleanSetting(prefs, "drawer_grid_mode", application.drawerGridMode)
      migrateBooleanSetting(prefs, "drawer_show_bookmarked_threads", application.drawerShowBookmarkedThreads)
      migrateBooleanSetting(prefs, "drawer_show_navigation_history", application.drawerShowNavigationHistory)
      migrateBooleanSetting(prefs, "drawer_delete_bookmarks_when_deleting_nav_history", application.drawerDeleteBookmarksWhenDeletingNavHistory)
      migrateBooleanSetting(prefs, "drawer_delete_nav_history_when_bookmark_deleted", application.drawerDeleteNavHistoryWhenBookmarkDeleted)
      migrateBooleanSetting(prefs, "global_nsfw_mode", application.globalNsfwMode)
      migrateEnumSettings(application)
    } finally {
      prefsFile.delete()
    }
  }

  private fun migrateBooleanSetting(
    prefs: SharedPreferencesSettingProvider,
    name: String,
    newSetting: KurobaBooleanSetting
  ) {
    tryMigrateSetting(newSetting.key) {
      val value = prefs.getBoolean(name, newSetting.default)
      if (value == newSetting.default) {
        return@tryMigrateSetting
      }

      Logger.debug(TAG) { "migrateBooleanSetting() ${name} -> ${newSetting.key.raw}, value: ${value}" }
      newSetting.writeBlocking(value)
    }
  }

  private fun migrateIntSetting(
    prefs: SharedPreferencesSettingProvider,
    name: String,
    newSetting: KurobaIntSetting
  ) {
    tryMigrateSetting(newSetting.key) {
      val value = prefs.getInt(name, newSetting.default)
      if (value == newSetting.default) {
        return@tryMigrateSetting
      }

      Logger.debug(TAG) { "migrateIntSetting() ${name} -> ${newSetting.key.raw}, value: ${value}" }
      newSetting.writeBlocking(value)
    }
  }

  private fun migrateLongSetting(
    prefs: SharedPreferencesSettingProvider,
    name: String,
    newSetting: KurobaLongSetting
  ) {
    tryMigrateSetting(newSetting.key) {
      val value = prefs.getLong(name, newSetting.default)
      if (value == newSetting.default) {
        return@tryMigrateSetting
      }

      Logger.debug(TAG) { "migrateLongSetting() ${name} -> ${newSetting.key.raw}, value: ${value}" }
      newSetting.writeBlocking(value)
    }
  }

  private fun migrateStringSetting(
    prefs: SharedPreferencesSettingProvider,
    name: String,
    newSetting: KurobaStringSetting
  ) {
    tryMigrateSetting(newSetting.key) {
      val value = prefs.getString(name, newSetting.default)
      if (value == newSetting.default) {
        return@tryMigrateSetting
      }

      Logger.debug(TAG) { "migrateStringSetting() ${name} -> ${newSetting.key.raw}, value: ${value.asFormattedToken()}" }
      newSetting.writeBlocking(value)
    }
  }

  private fun <T> migrateMoshiSetting(
    prefs: SharedPreferencesSettingProvider,
    name: String,
    clazz: Class<T>,
    newSetting: KurobaMoshiSetting<T>
  ) {
    tryMigrateSetting(newSetting.key) {
      val json = prefs.getString(name, "{}")

      val value = moshi
        .adapter<T>(clazz)
        .fromJson(json)

      if (value == newSetting.default) {
        return@tryMigrateSetting
      }

      if (value == null) {
        Logger.error(TAG) { "migrateMoshiSetting(${name}) failed to deserialize json '${json}' into ${clazz.name}" }
        return@tryMigrateSetting
      }

      Logger.debug(TAG) { "migrateMoshiSetting() ${name} -> ${newSetting.key.raw}, value: ${value.toString().asFormattedToken()}" }
      newSetting.writeBlocking(value)
    }
  }

  private fun <OldEnum, NewEnum> migrateEnumSetting(
    oldSetting: OptionsSetting<OldEnum>,
    newSetting: KurobaEnumSetting<NewEnum>,
    oldToNewMapper: (OldEnum) -> NewEnum
  ) where OldEnum : Enum<OldEnum>,
          OldEnum : OptionSettingItem,
          NewEnum : Enum<NewEnum>
  {
    tryMigrateSetting(newSetting.key) {
      val value = oldToNewMapper(oldSetting.get())
      if (value == newSetting.default) {
        // Do not store the default value
        return@tryMigrateSetting
      }

      Logger.debug(TAG) { "migrateEnumSetting() ${oldSetting.key} -> ${newSetting.key.raw}, value: ${value}" }
      newSetting.writeBlocking(value)
    }
  }

  private fun <K, V> migrateMapSetting(
    oldSetting: MapSetting,
    newSetting: KurobaMapSetting<K, V>,
    entryMapper: (Map.Entry<String, String>) -> Map.Entry<K, V>
  ) {
    tryMigrateSetting(newSetting.key) {
      val oldMap = oldSetting.get()
      val newMap = mutableMapOf<K, V>()

      oldMap.entries.forEach { mapEntry ->
        val newEntry = entryMapper(mapEntry)
        newMap[newEntry.key] = newEntry.value
      }

      newMap.entries.forEach { (key, value) ->
        Logger.debug(TAG) {
          "migrateMapSetting() ${oldSetting.key} -> ${newSetting.key.raw}, " +
            "(key: ${key}, value: ${value.toString().asFormattedToken()})"
        }
      }
      newSetting.writeBlocking(newMap)
    }
  }

  private fun <Old, New> migrateGsonSetting(
    prefs: SharedPreferencesSettingProvider,
    name: String,
    clazz: Class<Old>,
    newSetting: KurobaMoshiSetting<New>,
    oldToNewMapper: (Old) -> New
  ) {
    tryMigrateSetting(newSetting.key) {
      val json = prefs.getString(name, "{}")
      val oldObject = gson.fromJson(json, clazz)

      Logger.debug(TAG) { "migrateGsonSetting() ${name} -> ${newSetting.key.raw}" }
      newSetting.writeBlocking(oldToNewMapper(oldObject))
    }
  }

  private fun migrateCookieSetting(
    oldSetting: CookieSetting,
    newSetting: KurobaCookieSetting
  ) {
    tryMigrateSetting(newSetting.key) {
      val kurobaCookie = oldSetting.get()
      Logger.debug(TAG) { "migrateCookieSetting() ${oldSetting.key} -> ${newSetting.key.raw}" }
      newSetting.writeBlocking(kurobaCookie)
    }
  }

  private fun migrateEnumSettings(application: ApplicationSettings) {
    migrateEnumSetting<ChanSettingsDeprecated.LayoutMode, LayoutMode>(
      oldSetting = ChanSettingsDeprecated.layoutMode,
      newSetting = application.layoutMode,
      oldToNewMapper = { oldEnum ->
        when (oldEnum) {
          ChanSettingsDeprecated.LayoutMode.AUTO -> LayoutMode.Auto
          ChanSettingsDeprecated.LayoutMode.SLIDE -> LayoutMode.Slide
          ChanSettingsDeprecated.LayoutMode.PHONE -> LayoutMode.Phone
          ChanSettingsDeprecated.LayoutMode.SPLIT -> LayoutMode.Split
        }
      }
    )

    migrateEnumSetting<ChanSettingsDeprecated.PostAlignmentMode, PostAlignmentMode>(
      oldSetting = ChanSettingsDeprecated.catalogPostAlignmentMode,
      newSetting = application.catalogPostAlignmentMode,
      oldToNewMapper = { oldEnum ->
        when (oldEnum) {
          ChanSettingsDeprecated.PostAlignmentMode.AlignLeft -> PostAlignmentMode.AlignLeft
          ChanSettingsDeprecated.PostAlignmentMode.AlignRight -> PostAlignmentMode.AlignRight
        }
      }
    )

    migrateEnumSetting<ChanSettingsDeprecated.PostAlignmentMode, PostAlignmentMode>(
      oldSetting = ChanSettingsDeprecated.threadPostAlignmentMode,
      newSetting = application.threadPostAlignmentMode,
      oldToNewMapper = { oldEnum ->
        when (oldEnum) {
          ChanSettingsDeprecated.PostAlignmentMode.AlignLeft -> PostAlignmentMode.AlignLeft
          ChanSettingsDeprecated.PostAlignmentMode.AlignRight -> PostAlignmentMode.AlignRight
        }
      }
    )

    migrateEnumSetting<ChanSettingsDeprecated.PostThumbnailScaling, PostThumbnailScaling>(
      oldSetting = ChanSettingsDeprecated.postThumbnailScaling,
      newSetting = application.postThumbnailScaling,
      oldToNewMapper = { oldEnum ->
        when (oldEnum) {
          ChanSettingsDeprecated.PostThumbnailScaling.FitCenter -> PostThumbnailScaling.FitCenter
          ChanSettingsDeprecated.PostThumbnailScaling.CenterCrop -> PostThumbnailScaling.CenterCrop
        }
      }
    )

    migrateEnumSetting<ChanSettingsDeprecated.NetworkContentAutoLoadMode, NetworkContentAutoLoadMode>(
      oldSetting = ChanSettingsDeprecated.parseYoutubeTitlesAndDuration,
      newSetting = application.parseYoutubeTitlesAndDuration,
      oldToNewMapper = { oldEnum ->
        when (oldEnum) {
          ChanSettingsDeprecated.NetworkContentAutoLoadMode.ALL -> NetworkContentAutoLoadMode.All
          ChanSettingsDeprecated.NetworkContentAutoLoadMode.UNMETERED -> NetworkContentAutoLoadMode.Unmetered
          ChanSettingsDeprecated.NetworkContentAutoLoadMode.NONE -> NetworkContentAutoLoadMode.None
        }
      }
    )

    migrateEnumSetting<ChanSettingsDeprecated.NetworkContentAutoLoadMode, NetworkContentAutoLoadMode>(
      oldSetting = ChanSettingsDeprecated.parseSoundCloudTitlesAndDuration,
      newSetting = application.parseSoundCloudTitlesAndDuration,
      oldToNewMapper = { oldEnum ->
        when (oldEnum) {
          ChanSettingsDeprecated.NetworkContentAutoLoadMode.ALL -> NetworkContentAutoLoadMode.All
          ChanSettingsDeprecated.NetworkContentAutoLoadMode.UNMETERED -> NetworkContentAutoLoadMode.Unmetered
          ChanSettingsDeprecated.NetworkContentAutoLoadMode.NONE -> NetworkContentAutoLoadMode.None
        }
      }
    )

    migrateEnumSetting<ChanSettingsDeprecated.NetworkContentAutoLoadMode, NetworkContentAutoLoadMode>(
      oldSetting = ChanSettingsDeprecated.parseStreamableTitlesAndDuration,
      newSetting = application.parseStreamableTitlesAndDuration,
      oldToNewMapper = { oldEnum ->
        when (oldEnum) {
          ChanSettingsDeprecated.NetworkContentAutoLoadMode.ALL -> NetworkContentAutoLoadMode.All
          ChanSettingsDeprecated.NetworkContentAutoLoadMode.UNMETERED -> NetworkContentAutoLoadMode.Unmetered
          ChanSettingsDeprecated.NetworkContentAutoLoadMode.NONE -> NetworkContentAutoLoadMode.None
        }
      }
    )

    migrateEnumSetting<ChanSettingsDeprecated.BoardPostViewMode, BoardPostViewMode>(
      oldSetting = ChanSettingsDeprecated.boardPostViewMode,
      newSetting = application.boardPostViewMode,
      oldToNewMapper = { oldEnum ->
        when (oldEnum) {
          ChanSettingsDeprecated.BoardPostViewMode.LIST -> BoardPostViewMode.List
          ChanSettingsDeprecated.BoardPostViewMode.GRID -> BoardPostViewMode.Grid
          ChanSettingsDeprecated.BoardPostViewMode.STAGGER -> BoardPostViewMode.Stagger
        }
      }
    )

    migrateEnumSetting<ChanSettingsDeprecated.CatalogOrThreadSearchMode, CatalogOrThreadSearchMode>(
      oldSetting = ChanSettingsDeprecated.catalogSearchMode,
      newSetting = application.catalogSearchMode,
      oldToNewMapper = { oldEnum ->
        when (oldEnum) {
          ChanSettingsDeprecated.CatalogOrThreadSearchMode.Filter -> CatalogOrThreadSearchMode.Filter
          ChanSettingsDeprecated.CatalogOrThreadSearchMode.Highlight -> CatalogOrThreadSearchMode.Highlight
        }
      }
    )

    migrateEnumSetting<ChanSettingsDeprecated.CatalogOrThreadSearchMode, CatalogOrThreadSearchMode>(
      oldSetting = ChanSettingsDeprecated.threadSearchMode,
      newSetting = application.threadSearchMode,
      oldToNewMapper = { oldEnum ->
        when (oldEnum) {
          ChanSettingsDeprecated.CatalogOrThreadSearchMode.Filter -> CatalogOrThreadSearchMode.Filter
          ChanSettingsDeprecated.CatalogOrThreadSearchMode.Highlight -> CatalogOrThreadSearchMode.Highlight
        }
      }
    )

    migrateEnumSetting<ChanSettingsDeprecated.NetworkContentAutoLoadMode, NetworkContentAutoLoadMode>(
      oldSetting = ChanSettingsDeprecated.imageAutoLoadNetwork,
      newSetting = application.imageAutoLoadNetwork,
      oldToNewMapper = { oldEnum ->
        when (oldEnum) {
          ChanSettingsDeprecated.NetworkContentAutoLoadMode.ALL -> NetworkContentAutoLoadMode.All
          ChanSettingsDeprecated.NetworkContentAutoLoadMode.UNMETERED -> NetworkContentAutoLoadMode.Unmetered
          ChanSettingsDeprecated.NetworkContentAutoLoadMode.NONE -> NetworkContentAutoLoadMode.None
        }
      }
    )

    migrateEnumSetting<ChanSettingsDeprecated.NetworkContentAutoLoadMode, NetworkContentAutoLoadMode>(
      oldSetting = ChanSettingsDeprecated.videoAutoLoadNetwork,
      newSetting = application.videoAutoLoadNetwork,
      oldToNewMapper = { oldEnum ->
        when (oldEnum) {
          ChanSettingsDeprecated.NetworkContentAutoLoadMode.ALL -> NetworkContentAutoLoadMode.All
          ChanSettingsDeprecated.NetworkContentAutoLoadMode.UNMETERED -> NetworkContentAutoLoadMode.Unmetered
          ChanSettingsDeprecated.NetworkContentAutoLoadMode.NONE -> NetworkContentAutoLoadMode.None
        }
      }
    )

    migrateEnumSetting<ChanSettingsDeprecated.ImageGestureActionType, ImageGestureActionType>(
      oldSetting = ChanSettingsDeprecated.mediaViewerTopGestureAction,
      newSetting = application.mediaViewerTopGestureAction,
      oldToNewMapper = { oldEnum ->
        when (oldEnum) {
          ChanSettingsDeprecated.ImageGestureActionType.SaveImage -> ImageGestureActionType.SaveImage
          ChanSettingsDeprecated.ImageGestureActionType.CloseImage -> ImageGestureActionType.CloseImage
          ChanSettingsDeprecated.ImageGestureActionType.OpenAlbum -> ImageGestureActionType.OpenAlbum
          ChanSettingsDeprecated.ImageGestureActionType.Disabled -> ImageGestureActionType.Disabled
        }
      }
    )

    migrateEnumSetting<ChanSettingsDeprecated.ImageGestureActionType, ImageGestureActionType>(
      oldSetting = ChanSettingsDeprecated.mediaViewerBottomGestureAction,
      newSetting = application.mediaViewerBottomGestureAction,
      oldToNewMapper = { oldEnum ->
        when (oldEnum) {
          ChanSettingsDeprecated.ImageGestureActionType.SaveImage -> ImageGestureActionType.SaveImage
          ChanSettingsDeprecated.ImageGestureActionType.CloseImage -> ImageGestureActionType.CloseImage
          ChanSettingsDeprecated.ImageGestureActionType.OpenAlbum -> ImageGestureActionType.OpenAlbum
          ChanSettingsDeprecated.ImageGestureActionType.Disabled -> ImageGestureActionType.Disabled
        }
      }
    )

    migrateEnumSetting<ChanSettingsDeprecated.BookmarksSortOrder, BookmarksSortOrder>(
      oldSetting = ChanSettingsDeprecated.bookmarksSortOrder,
      newSetting = application.bookmarksSortOrder,
      oldToNewMapper = { oldEnum ->
        when (oldEnum) {
          ChanSettingsDeprecated.BookmarksSortOrder.CreatedOnAscending -> BookmarksSortOrder.CreatedOnAscending
          ChanSettingsDeprecated.BookmarksSortOrder.CreatedOnDescending -> BookmarksSortOrder.CreatedOnDescending
          ChanSettingsDeprecated.BookmarksSortOrder.ThreadIdAscending -> BookmarksSortOrder.ThreadIdAscending
          ChanSettingsDeprecated.BookmarksSortOrder.ThreadIdDescending -> BookmarksSortOrder.ThreadIdDescending
          ChanSettingsDeprecated.BookmarksSortOrder.UnreadRepliesAscending -> BookmarksSortOrder.UnreadRepliesAscending
          ChanSettingsDeprecated.BookmarksSortOrder.UnreadRepliesDescending -> BookmarksSortOrder.UnreadRepliesDescending
          ChanSettingsDeprecated.BookmarksSortOrder.UnreadPostsAscending -> BookmarksSortOrder.UnreadPostsAscending
          ChanSettingsDeprecated.BookmarksSortOrder.UnreadPostsDescending -> BookmarksSortOrder.UnreadPostsDescending
          ChanSettingsDeprecated.BookmarksSortOrder.CustomAscending -> BookmarksSortOrder.CustomAscending
          ChanSettingsDeprecated.BookmarksSortOrder.CustomDescending -> BookmarksSortOrder.CustomDescending
        }
      }
    )
  }

  private fun tryMigrateSetting(settingKey: KurobaSettingKey, func: () -> Unit) {
    try {
      Logger.debug(TAG) { "Migrating ${settingKey.raw} setting..." }
      func()
      Logger.debug(TAG) { "Migrating ${settingKey.raw} setting... success!" }
    } catch (error: Throwable) {
      Logger.error(TAG, error) { "Migrating ${settingKey.raw} setting... error!" }
    }
  }

  private data class DvachPasscodeInfoDeprecated(
    @SerializedName("files")
    val files: Int? = null,
    @SerializedName("files_size")
    val filesSize: Long? = null,
  )

  private data class Chan4CaptchaSettingsDeprecated(
    @SerializedName("remember_captcha_cookies")
    val rememberCaptchaCookies: Boolean = true,
    @SerializedName("captcha_ticket")
    val captchaTicket: String? = null,
    @SerializedName("last_refresh_time")
    val lastRefreshTime: Long = 0L
  )

  companion object {
    private const val TAG = "KurobaSettingsMigration"
  }
}