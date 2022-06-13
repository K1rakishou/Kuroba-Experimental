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
package com.github.k1rakishou;

import static com.github.k1rakishou.common.AndroidUtils.getActivityManager;
import static com.github.k1rakishou.common.AndroidUtils.getAppDir;
import static com.github.k1rakishou.common.AndroidUtils.getAppMainPreferences;
import static com.github.k1rakishou.common.AndroidUtils.isAndroidN;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;

import android.app.ActivityManager;

import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.prefs.BooleanSetting;
import com.github.k1rakishou.prefs.CounterSetting;
import com.github.k1rakishou.prefs.IntegerSetting;
import com.github.k1rakishou.prefs.OptionsSetting;
import com.github.k1rakishou.prefs.RangeSetting;
import com.github.k1rakishou.prefs.StringSetting;

import java.io.File;

import kotlin.Lazy;
import kotlin.LazyKt;

public class ChanSettings {
    private static final String TAG = "ChanSettings";
    public static final String EMPTY_JSON = "{}";
    public static final String NO_HASH_SET = "NO_HASH_SET";
    public static final String SHARED_PREFS_DIR_NAME = "shared_prefs";

    public static ChanSettingsInfo chanSettingsInfo;
    private static final Lazy<String> sharedPrefsFile = LazyKt.lazy(() ->
            SHARED_PREFS_DIR_NAME + "/"
            + chanSettingsInfo.getApplicationId()
            + "_preferences.xml");

    public static void init(ChanSettingsInfo info) {
        chanSettingsInfo = info;

        initInternal();
    }

    public enum PostThumbnailScaling implements OptionSettingItem {
        FitCenter("fit_center"),
        CenterCrop("center_crop");

        String key;

        PostThumbnailScaling(String key) {
            this.key = key;
        }

        @Override
        public String getKey() {
            return key;
        }
    }

    public enum PostAlignmentMode implements OptionSettingItem {
        AlignLeft("align_left"),
        AlignRight("align_right");

        String key;

        PostAlignmentMode(String key) {
            this.key = key;
        }

        @Override
        public String getKey() {
            return key;
        }
    }

    public enum FastScrollerType implements OptionSettingItem {
        Disabled("disabled"),
        ScrollByDraggingThumb("scroll_by_dragging_thumb"),
        ScrollByClickingAnyPointOfTrack("scroll_by_clicking_any_point_of_track");

        String key;

        FastScrollerType(String key) {
            this.key = key;
        }

        @Override
        public String getKey() {
            return key;
        }

        public boolean isEnabled() {
            return this != Disabled;
        }
    }

    public enum ImageGestureActionType implements OptionSettingItem {
        SaveImage("save_image"),
        CloseImage("close_image"),
        OpenAlbum("open_album"),
        Disabled("disabled");

        String key;

        ImageGestureActionType(String key) {
            this.key = key;
        }

        @Override
        public String getKey() {
            return key;
        }
    }

    public enum BookmarksSortOrder implements OptionSettingItem {
        CreatedOnAscending("creation_time_asc", true),
        CreatedOnDescending("creation_time_desc", false),
        UnreadRepliesAscending("replies_ascending", true),
        UnreadRepliesDescending("replies_descending", false),
        UnreadPostsAscending("unread_posts_ascending", true),
        UnreadPostsDescending("unread_posts_descending", false),
        CustomAscending("custom_ascending", true),
        CustomDescending("custom_descending", false);

        String key;
        boolean isAscending;

        BookmarksSortOrder(String key, boolean ascending) {
            this.key = key;
            this.isAscending = ascending;
        }

        @Override
        public String getKey() {
            return key;
        }

        public boolean isAscending() {
            return isAscending;
        }

        public static BookmarksSortOrder defaultOrder() {
            return BookmarksSortOrder.CustomAscending;
        }
    }

    public enum NetworkContentAutoLoadMode implements OptionSettingItem {
        // Always auto load, either wifi or mobile
        ALL("all"),
        // Only auto load if on wifi
        WIFI("wifi"),
        // Never auto load
        NONE("none");

        String name;

        NetworkContentAutoLoadMode(String name) {
            this.name = name;
        }

        @Override
        public String getKey() {
            return name;
        }
    }

    public enum BoardPostViewMode implements OptionSettingItem {
        LIST("list"),
        GRID("grid"),
        STAGGER("stagger");

        String name;

        BoardPostViewMode(String name) {
            this.name = name;
        }

        @Override
        public String getKey() {
            return name;
        }
    }

    public enum LayoutMode implements OptionSettingItem {
        AUTO("auto"),
        SLIDE("slide"),
        PHONE("phone"),
        SPLIT("split");

        String name;

        LayoutMode(String name) {
            this.name = name;
        }

        @Override
        public String getKey() {
            return name;
        }
    }

    public enum ConcurrentFileDownloadingChunks implements OptionSettingItem {
        One("One chunk", 1),
        Two("Two chunks", 2),
        Four("Four chunks", 4);

        String name;
        int chunksCount;

        ConcurrentFileDownloadingChunks(String name, int chunksCount) {
            this.name = name;
            this.chunksCount = chunksCount;
        }

        @Override
        public String getKey() {
            return name;
        }

        public int chunksCount() {
            return chunksCount;
        }
    }

    //region Declarations
    //region THREAD WATCHER
    public static BooleanSetting watchEnabled;
    public static BooleanSetting watchBackground;
    public static IntegerSetting watchBackgroundInterval;
    public static IntegerSetting watchForegroundInterval;
    public static BooleanSetting watchForegroundAdaptiveInterval;
    public static BooleanSetting replyNotifications;
    public static BooleanSetting useSoundForReplyNotifications;
    public static BooleanSetting watchLastPageNotify;
    public static BooleanSetting useSoundForLastPageNotifications;
    //endregion

    //region FILTER WATCHER
    public static BooleanSetting filterWatchEnabled;
    public static IntegerSetting filterWatchInterval;
    public static BooleanSetting filterWatchUseFilterPatternForGroup;
    //endregion

    //region THREAD DOWNLOADER
    public static IntegerSetting threadDownloaderUpdateInterval;
    public static BooleanSetting threadDownloaderDownloadMediaOnMeteredNetwork;
    //endregion

    //region APPEARANCE
    // Theme
    public static BooleanSetting isCurrentThemeDark;

    // Layout
    public static BooleanSetting bottomNavigationViewEnabled;
    public static OptionsSetting<LayoutMode> layoutMode;
    public static IntegerSetting catalogSpanCount;
    public static IntegerSetting albumSpanCount;
    public static BooleanSetting neverHideToolbar;
    public static BooleanSetting enableReplyFab;
    public static BooleanSetting captchaOnBottom;
    public static BooleanSetting neverShowPages;
    public static OptionsSetting<FastScrollerType> draggableScrollbars;

    //Post
    public static StringSetting fontSize;
    public static RangeSetting postCellThumbnailSizePercents;
    public static BooleanSetting postFullDate;
    public static BooleanSetting postFullDateUseLocalLocale;
    public static BooleanSetting postFileInfo;
    public static OptionsSetting<PostAlignmentMode> catalogPostAlignmentMode;
    public static OptionsSetting<PostAlignmentMode> threadPostAlignmentMode;
    public static OptionsSetting<PostThumbnailScaling> postThumbnailScaling;
    public static BooleanSetting drawPostThumbnailBackground;
    public static BooleanSetting textOnly;
    public static BooleanSetting revealTextSpoilers;
    public static BooleanSetting anonymize;
    public static BooleanSetting showAnonymousName;
    public static BooleanSetting anonymizeIds;
    public static BooleanSetting shiftPostComment;
    public static BooleanSetting forceShiftPostComment;
    public static BooleanSetting postMultipleImagesCompactMode;

    // Post links parsing
    public static OptionsSetting<NetworkContentAutoLoadMode> parseYoutubeTitlesAndDuration;
    public static OptionsSetting<NetworkContentAutoLoadMode> parseSoundCloudTitlesAndDuration;
    public static OptionsSetting<NetworkContentAutoLoadMode> parseStreamableTitlesAndDuration;
    public static BooleanSetting showLinkAlongWithTitleAndDuration;

    // Images
    public static BooleanSetting hideImages;
    public static BooleanSetting postThumbnailRemoveImageSpoilers;
    public static BooleanSetting mediaViewerRevealImageSpoilers;
    public static BooleanSetting transparencyOn;

    // Set elsewhere in the application
    public static OptionsSetting<BoardPostViewMode> boardPostViewMode;
    public static StringSetting boardOrder;
    //endregion

    //region BEHAVIOUR
    // General
    public static BooleanSetting autoRefreshThread;
    public static BooleanSetting controllerSwipeable;
    public static BooleanSetting viewThreadControllerSwipeable;
    public static BooleanSetting replyLayoutOpenCloseGestures;
    public static BooleanSetting openLinkConfirmation;
    public static StringSetting jsCaptchaCookies;
    public static BooleanSetting loadLastOpenedBoardUponAppStart;
    public static BooleanSetting loadLastOpenedThreadUponAppStart;

    // Reply
    public static BooleanSetting postPinThread;
    public static StringSetting postDefaultName;

    // Post
    public static BooleanSetting volumeKeysScrolling;
    public static BooleanSetting tapNoReply;
    public static BooleanSetting postLinksTakeWholeHorizSpace;
    public static BooleanSetting markUnseenPosts;
    public static BooleanSetting markSeenThreads;

    // Other options
    public static BooleanSetting fullUserRotationEnable;
    public static BooleanSetting showCopyApkUpdateDialog;
    public static StringSetting androidTenGestureZones;
    //endregion

    //region MEDIA

    // Video settings
    public static BooleanSetting videoAutoLoop;
    public static BooleanSetting videoDefaultMuted;
    public static BooleanSetting headsetDefaultMuted;
    public static BooleanSetting videoAlwaysResetToStart;
    public static IntegerSetting mediaViewerMaxOffscreenPages;
    public static BooleanSetting mediaViewerAutoSwipeAfterDownload;
    public static BooleanSetting mediaViewerDrawBehindNotch;
    public static BooleanSetting mediaViewerSoundPostsEnabled;
    public static BooleanSetting mediaViewerPausePlayersWhenInBackground;

    // Media loading
    public static OptionsSetting<NetworkContentAutoLoadMode> imageAutoLoadNetwork;
    public static OptionsSetting<NetworkContentAutoLoadMode> videoAutoLoadNetwork;

    // Misc
    public static BooleanSetting alwaysRandomizePickedFilesNames;
    //endregion

    //region SECURITY
    public static BooleanSetting forceHttpsUrlScheme;
    //endregion

    //region CACHING
    public static RangeSetting diskCacheSizeMegabytes;
    public static RangeSetting prefetchDiskCacheSizeMegabytes;
    public static RangeSetting diskCacheCleanupRemovePercent;
    //endregion

    // region Captcha-solvers
    public static BooleanSetting twoCaptchaSolverEnabled;
    public static StringSetting twoCaptchaSolverUrl;
    public static StringSetting twoCaptchaSolverApiKey;
    //endregion

    //region EXPERIMENTAL
    public static BooleanSetting okHttpAllowHttp2;
    public static BooleanSetting okHttpAllowIpv6;
    public static BooleanSetting okHttpUseDnsOverHttps;
    public static BooleanSetting cloudflareForcePreload;
    public static BooleanSetting prefetchMedia;
    public static BooleanSetting showPrefetchLoadingIndicator;
    public static BooleanSetting highResCells;
    public static BooleanSetting useMpvVideoPlayer;
    public static BooleanSetting colorizeTextSelectionCursors;
    //endregion

    //region OTHER
    public static BooleanSetting historyEnabled;
    public static BooleanSetting collectCrashLogs;
    public static BooleanSetting collectANRs;
    //endregion

    //region DEVELOPER
    public static BooleanSetting crashOnSafeThrow;
    public static BooleanSetting verboseLogs;
    public static BooleanSetting checkUpdateApkVersionCode;
    public static BooleanSetting showMpvInternalLogs;

    public static BooleanSetting funThingsAreFun;
    public static BooleanSetting force4chanBirthdayMode;
    public static BooleanSetting forceHalloweenMode;
    public static BooleanSetting forceChristmasMode;
    public static BooleanSetting forceNewYearMode;
    //endregion

    //region DATA
    // While not a setting, the last image options selected should be persisted even after import.
    public static StringSetting lastImageOptions;

    public static CounterSetting historyOpenCounter;
    public static CounterSetting threadOpenCounter;
    public static IntegerSetting drawerAutoOpenCount;
    public static BooleanSetting reencodeHintShown;
    public static BooleanSetting scrollingTextForThreadTitles;
    public static OptionsSetting<BookmarksSortOrder> bookmarksSortOrder;
    public static BooleanSetting moveNotActiveBookmarksToBottom;
    public static BooleanSetting moveBookmarksWithUnreadRepliesToTop;
    public static BooleanSetting ignoreDarkNightMode;
    public static RangeSetting bookmarkGridViewWidth;
    public static OptionsSetting<ImageGestureActionType> mediaViewerTopGestureAction;
    public static OptionsSetting<ImageGestureActionType> mediaViewerBottomGestureAction;
    public static BooleanSetting drawerGridMode;
    public static BooleanSetting drawerMoveLastAccessedThreadToTop;
    public static BooleanSetting drawerShowBookmarkedThreads;
    public static BooleanSetting drawerShowNavigationHistory;
    public static BooleanSetting drawerShowDeleteButtonShortcut;
    public static BooleanSetting drawerDeleteBookmarksWhenDeletingNavHistory;
    public static BooleanSetting drawerDeleteNavHistoryWhenBookmarkDeleted;
    public static BooleanSetting isLowRamDeviceForced;
    public static BooleanSetting markYourPostsOnScrollbar;
    public static BooleanSetting markRepliesToYourPostOnScrollbar;
    public static BooleanSetting markCrossThreadQuotesOnScrollbar;
    public static BooleanSetting markDeletedPostsOnScrollbar;
    public static BooleanSetting markHotPostsOnScrollbar;
    public static BooleanSetting globalNsfwMode;
    //endregion
    //endregion

    private static void initInternal() {
        try {
            SettingProvider provider = new SharedPreferencesSettingProvider(getAppMainPreferences());

            // Must be initialized first to avoid NPEs
            isLowRamDeviceForced = new BooleanSetting(
                    provider,
                    "is_low_ram_device_forced",
                    false
            );

            //region THREAD WATCHER
            watchEnabled = new BooleanSetting(provider, "preference_watch_enabled", false);
            watchBackground = new BooleanSetting(provider, "preference_watch_background_enabled", false);
            watchBackgroundInterval = new IntegerSetting(provider, "preference_watch_background_interval", (int) MINUTES.toMillis(30));
            watchForegroundInterval = new IntegerSetting(provider, "preference_watch_foreground_interval", (int) MINUTES.toMillis(1));
            watchForegroundAdaptiveInterval = new BooleanSetting(provider, "preference_watch_foreground_adaptive_interval", true);
            replyNotifications = new BooleanSetting(provider, "reply_notifications", true);
            useSoundForReplyNotifications = new BooleanSetting(provider, "use_sound_for_reply_notifications", false);
            watchLastPageNotify = new BooleanSetting(provider, "preference_watch_last_page_notify", false);
            useSoundForLastPageNotifications = new BooleanSetting(provider, "use_sound_for_last_page_notifications", false);
            //endregion

            //region FILTER WATCHER
            filterWatchEnabled = new BooleanSetting(provider, "preference_filter_watch_enabled", false);
            filterWatchInterval = new IntegerSetting(provider, "preference_filter_watch_interval", (int) HOURS.toMillis(12));
            filterWatchUseFilterPatternForGroup = new BooleanSetting(provider, "preference_filter_use_filter_pattern_for_group", true);
            //endregion

            // region THREAD DOWNLOADER
            threadDownloaderUpdateInterval = new IntegerSetting(provider, "preference_thread_downloader_update_interval", (int) HOURS.toMillis(1));
            threadDownloaderDownloadMediaOnMeteredNetwork = new BooleanSetting(provider, "preference_thread_downloader_download_media_on_metered_network", false);
            //endregion

            //region APPEARANCE
            // Theme
            isCurrentThemeDark = new BooleanSetting(provider, "is_current_theme_dark", true);

            //Layout
            bottomNavigationViewEnabled = new BooleanSetting(provider, "bottom_navigation_mode", true);
            layoutMode = new OptionsSetting<>(provider, "preference_layout_mode", LayoutMode.class, LayoutMode.AUTO);
            catalogSpanCount = new IntegerSetting(provider, "preference_board_grid_span_count", 0);
            albumSpanCount = new IntegerSetting(provider, "preference_album_span_count", 0);
            neverHideToolbar = new BooleanSetting(provider, "preference_never_hide_toolbar", false);
            enableReplyFab = new BooleanSetting(provider, "preference_enable_reply_fab", true);
            captchaOnBottom = new BooleanSetting(provider, "captcha_on_bottom", true);
            neverShowPages = new BooleanSetting(provider, "never_show_page_number", false);

            draggableScrollbars = new OptionsSetting<>(
                    provider,
                    "draggable_scrollbars",
                    FastScrollerType.class,
                    FastScrollerType.ScrollByClickingAnyPointOfTrack
            );

            // Post
            fontSize = new StringSetting(provider, "preference_font", String.valueOf(defaultFontSize()));
            postCellThumbnailSizePercents = new RangeSetting(provider, "post_cell_thumbnail_size_percents", 75, 50, 125);
            postFullDate = new BooleanSetting(provider, "preference_post_full_date", false);
            postFullDateUseLocalLocale = new BooleanSetting(provider, "preference_post_full_date_use_local_locale", false);
            postFileInfo = new BooleanSetting(provider, "preference_post_file_name", true);
            catalogPostAlignmentMode = new OptionsSetting<>(provider, "catalog_post_alignment_mode", PostAlignmentMode.class, PostAlignmentMode.AlignLeft);
            threadPostAlignmentMode = new OptionsSetting<>(provider, "thread_post_alignment_mode", PostAlignmentMode.class, PostAlignmentMode.AlignLeft);
            postThumbnailScaling = new OptionsSetting<>(provider, "post_thumbnail_scaling", PostThumbnailScaling.class, PostThumbnailScaling.FitCenter);
            drawPostThumbnailBackground = new BooleanSetting(provider, "draw_post_thumbnail_background", true);
            textOnly = new BooleanSetting(provider, "preference_text_only", false);
            revealTextSpoilers = new BooleanSetting(provider, "preference_reveal_text_spoilers", false);
            anonymize = new BooleanSetting(provider, "preference_anonymize", false);
            showAnonymousName = new BooleanSetting(provider, "preference_show_anonymous_name", false);
            anonymizeIds = new BooleanSetting(provider, "preference_anonymize_ids", false);
            markYourPostsOnScrollbar = new BooleanSetting(provider, "mark_your_posts_on_scrollbar", true);
            markRepliesToYourPostOnScrollbar = new BooleanSetting(provider, "mark_replies_to_your_posts_on_scrollbar", true);
            markDeletedPostsOnScrollbar = new BooleanSetting(provider, "mark_deleted_on_scrollbar", true);
            markHotPostsOnScrollbar = new BooleanSetting(provider, "mark_hot_posts_on_scrollbar", false);
            markCrossThreadQuotesOnScrollbar = new BooleanSetting(provider, "mark_cross_thread_quotes_on_scrollbar", false);
            shiftPostComment = new BooleanSetting(provider, "shift_post_comment", true);
            forceShiftPostComment = new BooleanSetting(provider, "force_shift_post_comment", false);
            postMultipleImagesCompactMode = new BooleanSetting(provider, "post_multiple_images_compact_mode", true);

            // Post links parsing
            parseYoutubeTitlesAndDuration = new OptionsSetting<>(
                    provider,
                    "parse_youtube_titles_and_duration_v2",
                    NetworkContentAutoLoadMode.class,
                    NetworkContentAutoLoadMode.WIFI
            );
            parseSoundCloudTitlesAndDuration = new OptionsSetting<>(
                    provider,
                    "parse_soundcloud_titles_and_duration",
                    NetworkContentAutoLoadMode.class,
                    NetworkContentAutoLoadMode.WIFI
            );
            parseStreamableTitlesAndDuration = new OptionsSetting<>(
                    provider,
                    "parse_streamable_titles_and_duration",
                    NetworkContentAutoLoadMode.class,
                    NetworkContentAutoLoadMode.WIFI
            );
            showLinkAlongWithTitleAndDuration = new BooleanSetting(provider, "show_link_along_with_title_and_duration", true);

            // Images
            hideImages = new BooleanSetting(provider, "preference_hide_images", false);
            postThumbnailRemoveImageSpoilers = new BooleanSetting(provider, "preference_reveal_image_spoilers", false);
            mediaViewerRevealImageSpoilers = new BooleanSetting(provider, "preference_auto_unspoil_images", true);
            transparencyOn = new BooleanSetting(provider, "image_transparency_on", false);

            //Elsewhere
            boardPostViewMode = new OptionsSetting<>(provider, "preference_board_view_mode", BoardPostViewMode.class, BoardPostViewMode.LIST);
            boardOrder = new StringSetting(provider, "preference_board_order", chanSettingsInfo.getDefaultFilterOrderName());
            //endregion

            //region BEHAVIOUR
            // General
            autoRefreshThread = new BooleanSetting(provider, "preference_auto_refresh_thread", true);
            controllerSwipeable = new BooleanSetting(provider, "preference_controller_swipeable", true);
            viewThreadControllerSwipeable = new BooleanSetting(provider, "preference_view_thread_controller_swipeable", true);
            replyLayoutOpenCloseGestures = new BooleanSetting(provider, "reply_layout_open_close_gestures", true);
            openLinkConfirmation = new BooleanSetting(provider, "preference_open_link_confirmation", false);
            jsCaptchaCookies = new StringSetting(provider, "js_captcha_cookies", EMPTY_JSON);
            loadLastOpenedBoardUponAppStart = new BooleanSetting(provider, "load_last_opened_board_upon_app_start", true);
            loadLastOpenedThreadUponAppStart = new BooleanSetting(provider, "load_last_opened_thread_upon_app_start", true);

            // Reply
            postPinThread = new BooleanSetting(provider, "preference_pin_on_post", false);
            postDefaultName = new StringSetting(provider, "preference_default_name", "");

            // Post
            volumeKeysScrolling = new BooleanSetting(provider, "preference_volume_key_scrolling", false);
            tapNoReply = new BooleanSetting(provider, "preference_tap_no_reply", false);
            postLinksTakeWholeHorizSpace = new BooleanSetting(provider, "post_links_take_whole_horiz_space", true);
            markUnseenPosts = new BooleanSetting(provider, "preference_mark_unseen_posts", true);
            markSeenThreads = new BooleanSetting(provider, "preference_mark_seen_threads", true);

            // Other options
            fullUserRotationEnable = new BooleanSetting(provider, "full_user_rotation_enable", true);
            showCopyApkUpdateDialog = new BooleanSetting(provider, "show_copy_apk_update_dialog", true);
            androidTenGestureZones = new StringSetting(provider, "android_ten_gesture_zones", EMPTY_JSON);
            //endregion

            //region MEDIA

            // Video Settings
            videoAutoLoop = new BooleanSetting(provider, "preference_video_loop", true);
            videoDefaultMuted = new BooleanSetting(provider, "preference_video_default_muted", true);
            headsetDefaultMuted = new BooleanSetting(provider, "preference_headset_default_muted", true);
            videoAlwaysResetToStart = new BooleanSetting(provider, "preference_video_always_reset_to_start", false);
            mediaViewerMaxOffscreenPages = new IntegerSetting(provider, "preference_media_viewer_max_offscreen_pages", 1);
            mediaViewerAutoSwipeAfterDownload = new BooleanSetting(provider, "preference_media_viewer_auto_swipe_after_download", false);
            mediaViewerDrawBehindNotch = new BooleanSetting(provider, "preference_media_viewer_draw_behind_notch", true);
            mediaViewerSoundPostsEnabled = new BooleanSetting(provider, "preference_media_viewer_sound_posts_enabled", false);
            mediaViewerPausePlayersWhenInBackground = new BooleanSetting(provider, "preference_media_viewer_pause_players_when_in_background", true);

            // Media loading
            imageAutoLoadNetwork = new OptionsSetting<>(provider,
                    "preference_image_auto_load_network",
                    NetworkContentAutoLoadMode.class,
                    NetworkContentAutoLoadMode.WIFI
            );
            videoAutoLoadNetwork = new OptionsSetting<>(provider,
                    "preference_video_auto_load_network",
                    NetworkContentAutoLoadMode.class,
                    NetworkContentAutoLoadMode.WIFI
            );

            // Misc
            alwaysRandomizePickedFilesNames = new BooleanSetting(provider, "always_randomized_picked_files_names", false);
            //endregion

            forceHttpsUrlScheme = new BooleanSetting(provider, "force_https_url_scheme", true);

            //region CACHING
            diskCacheSizeMegabytes = new RangeSetting(provider, "disk_cache_size", 256, diskCacheSizeGetMin(), 1024);
            prefetchDiskCacheSizeMegabytes = new RangeSetting(provider, "prefetch_disk_cache_size", 512, diskCacheSizePrefetchGetMin(), 2048);
            diskCacheCleanupRemovePercent = new RangeSetting(provider, "disk_cache_cleanup_remove_files_percent", 25, cleanupPercentsGetMin(), 75);
            //endregion

            // region Captcha-solvers
            twoCaptchaSolverEnabled = new BooleanSetting(provider, "two_captcha_solver_enabled", false);
            twoCaptchaSolverUrl = new StringSetting(provider, "two_captcha_solver_url", "https://2captcha.com");
            twoCaptchaSolverApiKey = new StringSetting(provider, "two_captcha_solver_api_key", "");
            //endregion

            //region EXPERIMENTAL
            okHttpAllowHttp2 = new BooleanSetting(provider, "ok_http_allow_http_2", true);
            okHttpAllowIpv6 = new BooleanSetting(provider, "ok_http_allow_ipv6", false);
            okHttpUseDnsOverHttps = new BooleanSetting(provider, "ok_http_use_dns_over_https", false);
            prefetchMedia = new BooleanSetting(provider, "preference_auto_load_thread", false);
            showPrefetchLoadingIndicator = new BooleanSetting(provider, "show_prefetch_loading_indicator", false);
            cloudflareForcePreload = new BooleanSetting(provider, "cloudflare_force_preload", false);
            highResCells = new BooleanSetting(provider, "high_res_cells", false);
            useMpvVideoPlayer = new BooleanSetting(provider, "use_mpv_video_player", false);
            colorizeTextSelectionCursors = new BooleanSetting(provider, "colorize_text_selection_cursors", true);
            //endregion

            //region OTHER
            historyEnabled = new BooleanSetting(provider, "preference_history_enabled", true);
            collectCrashLogs = new BooleanSetting(provider, "collect_crash_logs", true);
            collectANRs = new BooleanSetting(provider, "collect_anrs", true);
            //endregion

            //region DEVELOPER
            crashOnSafeThrow = new BooleanSetting(
                    provider,
                    "crash_on_safe_throw",
                    // Always true by default for dev/beta flavors
                    chanSettingsInfo.isDevOrBetaBuild()
            );
            verboseLogs = new BooleanSetting(
                    provider,
                    "verbose_logs",
                    // Always true by default for dev/beta flavors
                    chanSettingsInfo.isDevOrBetaBuild()
            );
            checkUpdateApkVersionCode = new BooleanSetting(provider, "check_update_apk_version_code", true);
            showMpvInternalLogs = new BooleanSetting(provider, "show_mpv_internal_logs", chanSettingsInfo.isDevBuild());

            funThingsAreFun = new BooleanSetting(provider, "fun_things_are_fun", true);
            force4chanBirthdayMode = new BooleanSetting(provider, "force_4chan_birthday_mode", false);
            forceHalloweenMode = new BooleanSetting(provider, "force_halloween_mode", false);
            forceChristmasMode = new BooleanSetting(provider, "force_christmas_mode", false);
            forceNewYearMode = new BooleanSetting(provider, "force_new_year_mode", false);
            //endregion

            //region DATA
            lastImageOptions = new StringSetting(provider, "last_image_options", "");
            historyOpenCounter = new CounterSetting(provider, "counter_history_open");
            threadOpenCounter = new CounterSetting(provider, "counter_thread_open");
            drawerAutoOpenCount = new IntegerSetting(provider, "drawer_auto_open_count", 0);
            reencodeHintShown = new BooleanSetting(provider, "preference_reencode_hint_already_shown", false);
            ignoreDarkNightMode = new BooleanSetting(provider, "ignore_dark_night_mode", true);

            bookmarksSortOrder = new OptionsSetting<>(provider,
                    "bookmarks_comparator",
                    BookmarksSortOrder.class,
                    BookmarksSortOrder.defaultOrder()
            );

            moveNotActiveBookmarksToBottom = new BooleanSetting(provider, "move_not_active_bookmarks_to_bottom", false);
            moveBookmarksWithUnreadRepliesToTop = new BooleanSetting(provider, "move_bookmarks_with_unread_replies_to_top", false);
            //endregion

            scrollingTextForThreadTitles = new BooleanSetting(provider, "scrolling_text_for_thread_titles", true);

            bookmarkGridViewWidth = new RangeSetting(
                    provider,
                    "bookmark_grid_view_width",
                    chanSettingsInfo.getBookmarkGridViewInfo().getDefaultWidth(),
                    chanSettingsInfo.getBookmarkGridViewInfo().getMinWidth(),
                    chanSettingsInfo.getBookmarkGridViewInfo().getMaxWidth()
            );

            mediaViewerTopGestureAction = new OptionsSetting<>(
                    provider,
                    "media_viewer_top_gesture_action",
                    ImageGestureActionType.class,
                    ImageGestureActionType.CloseImage
            );
            mediaViewerBottomGestureAction = new OptionsSetting<>(
                    provider,
                    "media_viewer_bottom_gesture_action",
                    ImageGestureActionType.class,
                    ImageGestureActionType.SaveImage
            );
            drawerMoveLastAccessedThreadToTop = new BooleanSetting(
                    provider,
                    "drawer_move_last_accessed_thread_to_top",
                    true
            );
            drawerGridMode = new BooleanSetting(
                    provider,
                    "drawer_grid_mode",
                    true
            );
            drawerShowBookmarkedThreads = new BooleanSetting(
                    provider,
                    "drawer_show_bookmarked_threads",
                    true
            );
            drawerShowNavigationHistory = new BooleanSetting(
                    provider,
                    "drawer_show_navigation_history",
                    true
            );
            drawerShowDeleteButtonShortcut = new BooleanSetting(
                    provider,
                    "drawer_show_delete_button_shortcut",
                    true
            );
            drawerDeleteBookmarksWhenDeletingNavHistory = new BooleanSetting(provider, "drawer_delete_bookmarks_when_deleting_nav_history", false);
            drawerDeleteNavHistoryWhenBookmarkDeleted = new BooleanSetting(provider, "drawer_delete_nav_history_when_bookmark_deleted", false);
            globalNsfwMode = new BooleanSetting(provider, "global_nsfw_mode", false);
        } catch (Throwable error) {
            // If something crashes while the settings are initializing we at least will have the
            // stacktrace. Otherwise we won't because of Feather.
            Logger.e(TAG, "Error while initializing the settings", error);
            throw error;
        }
    }

    public static int mediaViewerOffscreenPagesCount() {
        if (isLowRamDevice()) {
            return 1;
        }

        int count = ChanSettings.mediaViewerMaxOffscreenPages.get();
        if (count < 1) {
            count = 1;
        }

        if (count > 2) {
            count = 2;
        }

        return count;
    }

    public static boolean isLowRamDevice() {
        if (isLowRamDeviceForced.get()) {
            return true;
        }

        if (!isAndroidN()) {
            // Consider all devices lover than N as low ram devices
            return true;
        }

        ActivityManager activityManager = getActivityManager();
        return activityManager != null && activityManager.isLowRamDevice();
    }

    public static int defaultFontSize() {
        if (chanSettingsInfo.isTablet()) {
            return 16;
        } else {
            return 14;
        }
    }

    private static int cleanupPercentsGetMin() {
        if (chanSettingsInfo.isDevBuild()) {
            return 1;
        }

        return 25;
    }

    private static int diskCacheSizePrefetchGetMin() {
        if (chanSettingsInfo.isDevBuild()) {
            return 32;
        }

        return 512;
    }

    private static int diskCacheSizeGetMin() {
        if (chanSettingsInfo.isDevBuild()) {
            return 32;
        }

        return 128;
    }

    public static ChanSettings.LayoutMode getCurrentLayoutMode() {
        ChanSettings.LayoutMode layoutMode = ChanSettings.layoutMode.get();

        if (layoutMode == ChanSettings.LayoutMode.AUTO) {
            if (chanSettingsInfo.isTablet()) {
                layoutMode = ChanSettings.LayoutMode.SPLIT;
            } else {
                layoutMode = ChanSettings.LayoutMode.SLIDE;
            }
        }

        return layoutMode;
    }

    public static boolean isNavigationViewEnabled() {
        if (isSplitLayoutMode()) {
            // The left side navigation view is always enabled when in SPLIT mode
            return true;
        }

        return bottomNavigationViewEnabled.get();
    }

    public static boolean isBottomNavigationPresent() {
        if (isSplitLayoutMode()) {
            // The bottom navigation is moved to the left side in SPLIT mode
            return false;
        }

        return bottomNavigationViewEnabled.get();
    }

    public static File getMainSharedPrefsFileForThisFlavor() {
        return new File(getAppDir(), sharedPrefsFile.getValue());
    }

    public static int detailsSizeSp() {
        return Integer.parseInt(ChanSettings.fontSize.get()) - 2;
    }

    public static int codeTagFontSizePx() {
        return Integer.parseInt(ChanSettings.fontSize.get()) - 2;
    }

    public static int sjisTagFontSizePx() {
        return 8;
    }

    public static int redTextFontSizePx() {
        return Integer.parseInt(ChanSettings.fontSize.get()) + 2;
    }

    public static boolean isSlideLayoutMode() {
        return getCurrentLayoutMode() == LayoutMode.SLIDE;
    }

    public static boolean isSplitLayoutMode() {
        return getCurrentLayoutMode() == LayoutMode.SPLIT;
    }
}
