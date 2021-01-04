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

import android.net.Uri;

import com.github.k1rakishou.base_dir.SavedFilesBaseDirSetting;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.prefs.BooleanSetting;
import com.github.k1rakishou.prefs.CounterSetting;
import com.github.k1rakishou.prefs.IntegerSetting;
import com.github.k1rakishou.prefs.OptionsSetting;
import com.github.k1rakishou.prefs.RangeSetting;
import com.github.k1rakishou.prefs.StringSetting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import kotlin.Lazy;
import kotlin.LazyKt;

import static com.github.k1rakishou.common.AndroidUtils.getAppDir;
import static com.github.k1rakishou.common.AndroidUtils.getAppMainPreferences;
import static java.util.concurrent.TimeUnit.MINUTES;

public class ChanSettings {
    private static final String TAG = "ChanSettings";
    public static final String EMPTY_JSON = "{}";
    public static final String NO_HASH_SET = "NO_HASH_SET";

    private static ChanSettingsInfo chanSettingsInfo;
    private static Lazy<String> sharedPrefsFile = LazyKt.lazy(() ->
            "shared_prefs/"
            + chanSettingsInfo.getApplicationId()
            + "_preferences.xml");

    public static void init(ChanSettingsInfo info) {
        chanSettingsInfo = info;

        initInternal();
    }

    public enum ImageGestureActionType implements OptionSettingItem {
        SaveImage("save_image"),
        CloseImage("close_image"),
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

    public enum PostViewMode implements OptionSettingItem {
        LIST("list"),
        CARD("grid");

        String name;

        PostViewMode(String name) {
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

    public enum ImageClickPreloadStrategy implements OptionSettingItem {
        PreloadNext("Preload next image"),
        PreloadPrevious("Preload previous image"),
        PreloadBoth("Preload next and previous images"),
        PreloadNeither("Do not preload any images");

        String name;

        ImageClickPreloadStrategy(String name) {
            this.name = name;
        }

        @Override
        public String getKey() {
            return name;
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

    //region APPEARANCE
    // Theme
    public static BooleanSetting imageViewerFullscreenMode;
    public static BooleanSetting isCurrentThemeDark;

    // Layout
    public static OptionsSetting<LayoutMode> layoutMode;
    public static IntegerSetting boardGridSpanCount;
    public static BooleanSetting neverHideToolbar;
    public static BooleanSetting enableReplyFab;
    public static BooleanSetting captchaOnBottom;
    public static BooleanSetting neverShowPages;

    //Post
    public static StringSetting fontSize;
    public static BooleanSetting postFullDate;
    public static BooleanSetting postFileInfo;
    public static BooleanSetting postFilename;
    public static BooleanSetting textOnly;
    public static BooleanSetting revealTextSpoilers;
    public static BooleanSetting anonymize;
    public static BooleanSetting showAnonymousName;
    public static BooleanSetting anonymizeIds;
    public static BooleanSetting markYourPostsOnScrollbar;
    public static BooleanSetting markRepliesToYourPostOnScrollbar;
    public static BooleanSetting markCrossThreadQuotesOnScrollbar;

    // Post links parsing
    public static OptionsSetting<NetworkContentAutoLoadMode> parseYoutubeTitlesAndDuration;
    public static OptionsSetting<NetworkContentAutoLoadMode> parseSoundCloudTitlesAndDuration;
    public static OptionsSetting<NetworkContentAutoLoadMode> parseStreamableTitlesAndDuration;
    public static BooleanSetting showLinkAlongWithTitleAndDuration;

    // Images
    public static BooleanSetting hideImages;
    public static BooleanSetting removeImageSpoilers;
    public static BooleanSetting revealImageSpoilers;
    public static BooleanSetting highResCells;
    public static BooleanSetting parsePostImageLinks;
    public static BooleanSetting fetchInlinedFileSizes;
    public static BooleanSetting transparencyOn;

    // Set elsewhere in the application
    public static OptionsSetting<PostViewMode> boardViewMode;
    public static StringSetting boardOrder;
    //endregion

    //region BEHAVIOUR
    // General
    public static BooleanSetting autoRefreshThread;
    public static BooleanSetting controllerSwipeable;
    public static BooleanSetting openLinkConfirmation;
    public static BooleanSetting openLinkBrowser;
    public static StringSetting jsCaptchaCookies;
    public static BooleanSetting loadLastOpenedBoardUponAppStart;
    public static BooleanSetting loadLastOpenedThreadUponAppStart;

    // Reply
    public static BooleanSetting postPinThread;
    public static StringSetting postDefaultName;

    // Post
    public static BooleanSetting volumeKeysScrolling;
    public static BooleanSetting tapNoReply;
    public static BooleanSetting markUnseenPosts;

    // Other options
    public static BooleanSetting fullUserRotationEnable;
    public static BooleanSetting showCopyApkUpdateDialog;
    //endregion

    //region MEDIA
    // Saving
    public static SavedFilesBaseDirSetting saveLocation;
    public static BooleanSetting saveBoardFolder;
    public static BooleanSetting saveThreadFolder;
    public static BooleanSetting saveAlbumBoardFolder;
    public static BooleanSetting saveAlbumThreadFolder;
    public static BooleanSetting saveServerFilename;

    // Video settings
    public static BooleanSetting videoAutoLoop;
    public static BooleanSetting videoDefaultMuted;
    public static BooleanSetting headsetDefaultMuted;
    public static BooleanSetting videoOpenExternal;
    public static BooleanSetting videoStream;

    // Media loading
    public static OptionsSetting<NetworkContentAutoLoadMode> imageAutoLoadNetwork;
    public static OptionsSetting<NetworkContentAutoLoadMode> videoAutoLoadNetwork;
    public static OptionsSetting<ImageClickPreloadStrategy> imageClickPreloadStrategy;
    public static BooleanSetting autoLoadThreadImages;
    public static BooleanSetting showPrefetchLoadingIndicator;
    //endregion

    //region EXPERIMENTAL
    public static StringSetting androidTenGestureZones;
    public static BooleanSetting okHttpAllowHttp2;
    public static BooleanSetting okHttpAllowIpv6;
    public static BooleanSetting cloudflareForcePreload;
    //endregion

    //region OTHER
    public static BooleanSetting historyEnabled;
    public static BooleanSetting collectCrashLogs;
    //endregion

    //region DEVELOPER
    public static BooleanSetting crashOnSafeThrow;
    public static BooleanSetting verboseLogs;
    //endregion

    //region DATA
    // While not a setting, the last image options selected should be persisted even after import.
    public static StringSetting lastImageOptions;

    // While these are not "settings", they are here instead of in PersistableChanState because they
    // control the appearance of hints. Hints should not be shown if re-imported.
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
    public static OptionsSetting<ImageGestureActionType> imageSwipeUpGesture;
    public static OptionsSetting<ImageGestureActionType> imageSwipeDownGesture;
    public static RangeSetting threadMaxPostCapacity;
    public static BooleanSetting drawerMoveLastAccessedThreadToTop;
    public static BooleanSetting drawerShowBookmarkedThreads;
    public static BooleanSetting drawerShowNavigationHistory;
    //endregion
    //endregion

    private static void initInternal() {
        try {
            SettingProvider provider = new SharedPreferencesSettingProvider(getAppMainPreferences());

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

            //region APPEARANCE
            // Theme
            imageViewerFullscreenMode = new BooleanSetting(provider, "image_viewer_fullscreen_mode", true);
            isCurrentThemeDark = new BooleanSetting(provider, "is_current_theme_dark", true);

            //Layout
            layoutMode = new OptionsSetting<>(provider, "preference_layout_mode", LayoutMode.class, LayoutMode.AUTO);
            boardGridSpanCount = new IntegerSetting(provider, "preference_board_grid_span_count", 0);
            neverHideToolbar = new BooleanSetting(provider, "preference_never_hide_toolbar", false);
            enableReplyFab = new BooleanSetting(provider, "preference_enable_reply_fab", true);
            captchaOnBottom = new BooleanSetting(provider, "captcha_on_bottom", true);
            neverShowPages = new BooleanSetting(provider, "never_show_page_number", false);

            // Post
            fontSize = new StringSetting(provider, "preference_font", chanSettingsInfo.isTablet() ? "16" : "14");
            postFullDate = new BooleanSetting(provider, "preference_post_full_date", false);
            postFileInfo = new BooleanSetting(provider, "preference_post_file_info", true);
            postFilename = new BooleanSetting(provider, "preference_post_filename", true);
            textOnly = new BooleanSetting(provider, "preference_text_only", false);
            revealTextSpoilers = new BooleanSetting(provider, "preference_reveal_text_spoilers", false);
            anonymize = new BooleanSetting(provider, "preference_anonymize", false);
            showAnonymousName = new BooleanSetting(provider, "preference_show_anonymous_name", false);
            anonymizeIds = new BooleanSetting(provider, "preference_anonymize_ids", false);
            markYourPostsOnScrollbar = new BooleanSetting(provider, "mark_your_posts_on_scrollbar", true);
            markRepliesToYourPostOnScrollbar = new BooleanSetting(provider, "mark_replies_to_your_posts_on_scrollbar", true);
            markCrossThreadQuotesOnScrollbar = new BooleanSetting(provider, "mark_cross_thread_quotes_on_scrollbar", false);

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
            removeImageSpoilers = new BooleanSetting(provider, "preference_reveal_image_spoilers", false);
            revealImageSpoilers = new BooleanSetting(provider, "preference_auto_unspoil_images", true);
            highResCells = new BooleanSetting(provider, "high_res_cells", false);
            parsePostImageLinks = new BooleanSetting(provider, "parse_post_image_links", true);
            fetchInlinedFileSizes = new BooleanSetting(provider, "fetch_inlined_file_size", false);
            transparencyOn = new BooleanSetting(provider, "image_transparency_on", false);

            //Elsewhere
            boardViewMode = new OptionsSetting<>(provider, "preference_board_view_mode", PostViewMode.class, PostViewMode.LIST);
            boardOrder = new StringSetting(provider, "preference_board_order", chanSettingsInfo.getDefaultFilterOrderName());
            //endregion

            //region BEHAVIOUR
            // General
            autoRefreshThread = new BooleanSetting(provider, "preference_auto_refresh_thread", true);
            controllerSwipeable = new BooleanSetting(provider, "preference_controller_swipeable", true);
            openLinkConfirmation = new BooleanSetting(provider, "preference_open_link_confirmation", false);
            openLinkBrowser = new BooleanSetting(provider, "preference_open_link_browser", false);
            jsCaptchaCookies = new StringSetting(provider, "js_captcha_cookies", EMPTY_JSON);
            loadLastOpenedBoardUponAppStart = new BooleanSetting(provider, "load_last_opened_board_upon_app_start", true);
            loadLastOpenedThreadUponAppStart = new BooleanSetting(provider, "load_last_opened_thread_upon_app_start", true);

            // Reply
            postPinThread = new BooleanSetting(provider, "preference_pin_on_post", false);
            postDefaultName = new StringSetting(provider, "preference_default_name", "");

            // Post
            volumeKeysScrolling = new BooleanSetting(provider, "preference_volume_key_scrolling", false);
            tapNoReply = new BooleanSetting(provider, "preference_tap_no_reply", false);
            markUnseenPosts = new BooleanSetting(provider, "preference_mark_unseen_posts", true);

            // Other options
            fullUserRotationEnable = new BooleanSetting(provider, "full_user_rotation_enable", true);
            showCopyApkUpdateDialog = new BooleanSetting(provider, "show_copy_apk_update_dialog", true);
            //endregion

            //region MEDIA
            // Saving
            saveLocation = new SavedFilesBaseDirSetting(provider);
            saveBoardFolder = new BooleanSetting(provider, "preference_save_subboard", false);
            saveThreadFolder = new BooleanSetting(provider, "preference_save_subthread", false);
            saveAlbumBoardFolder = new BooleanSetting(provider, "preference_save_album_subboard", false);
            saveAlbumThreadFolder = new BooleanSetting(provider, "preference_save_album_subthread", false);
            saveServerFilename = new BooleanSetting(provider, "preference_image_save_original", false);

            // Video Settings
            videoAutoLoop = new BooleanSetting(provider, "preference_video_loop", true);
            videoDefaultMuted = new BooleanSetting(provider, "preference_video_default_muted", true);
            headsetDefaultMuted = new BooleanSetting(provider, "preference_headset_default_muted", true);
            videoOpenExternal = new BooleanSetting(provider, "preference_video_external", false);
            videoStream = new BooleanSetting(provider, "preference_video_stream", false);

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
            imageClickPreloadStrategy = new OptionsSetting<>(provider,
                    "image_click_preload_strategy",
                    ImageClickPreloadStrategy.class,
                    ImageClickPreloadStrategy.PreloadNext
            );
            autoLoadThreadImages = new BooleanSetting(provider, "preference_auto_load_thread", false);
            showPrefetchLoadingIndicator = new BooleanSetting(provider, "show_prefetch_loading_indicator", false);
            cloudflareForcePreload = new BooleanSetting(provider, "cloudflare_force_preload", false);
            //endregion

            //region EXPERIMENTAL
            androidTenGestureZones = new StringSetting(provider, "android_ten_gesture_zones", EMPTY_JSON);
            okHttpAllowHttp2 = new BooleanSetting(provider, "ok_http_allow_http_2", true);
            okHttpAllowIpv6 = new BooleanSetting(provider, "ok_http_allow_ipv6", false);
            //endregion

            //region OTHER
            historyEnabled = new BooleanSetting(provider, "preference_history_enabled", true);
            collectCrashLogs = new BooleanSetting(provider, "collect_crash_logs", true);
            //endregion

            //region DEVELOPER
            crashOnSafeThrow = new BooleanSetting(provider, "crash_on_safe_throw", true);
            verboseLogs = new BooleanSetting(
                    provider,
                    "verbose_logs",
                    // Always true by default for dev flavor
                    chanSettingsInfo.isDevBuild()
            );
            //endregion

            //region DATA
            lastImageOptions = new StringSetting(provider, "last_image_options", "");
            historyOpenCounter = new CounterSetting(provider, "counter_history_open");
            threadOpenCounter = new CounterSetting(provider, "counter_thread_open");
            drawerAutoOpenCount = new IntegerSetting(provider, "drawer_auto_open_count", 0);
            reencodeHintShown = new BooleanSetting(provider, "preference_reencode_hint_already_shown", false);
            ignoreDarkNightMode = new BooleanSetting(provider, "ignore_dark_night_mode", false);

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

            imageSwipeUpGesture = new OptionsSetting<>(
                    provider,
                    "image_swipe_up_gesture",
                    ImageGestureActionType.class,
                    ImageGestureActionType.CloseImage
            );
            imageSwipeDownGesture = new OptionsSetting<>(
                    provider,
                    "image_swipe_down_gesture",
                    ImageGestureActionType.class,
                    ImageGestureActionType.SaveImage
            );
            threadMaxPostCapacity = new RangeSetting(
                    provider,
                    "thread_max_post_capacity",
                    0,
                    0,
                    1000
            );
            drawerMoveLastAccessedThreadToTop = new BooleanSetting(
                    provider,
                    "drawer_move_last_accessed_thread_to_top",
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
        } catch (Throwable error) {
            // If something crashes while the settings are initializing we at least will have the
            // stacktrace. Otherwise we won't because of Feather.
            Logger.e(TAG, "Error while initializing the settings", error);
            throw error;
        }
    }

    /**
     * Reads setting from the shared preferences file to a string.
     * Called on the Database thread.
     */
    public synchronized static String serializeToString() throws IOException {
        String prevSaveLocationUri = null;

        /*
         We need to check if the user has any of the location settings set to a SAF directory.
         We can't export them because if the user reinstalls the app and then imports a location
         setting that point to a SAF directory that directory won't be valid for the app because
         after clearing settings all permissions for that directory will be lost. So in case the
         user tries to export SAF directory paths we don't export them and instead export default
         locations. But we also don't wont to change the paths for the current app so we need to
         save the previous paths, patch the sharedPrefs file read it to string and then restore
         the current paths back to what they were before exporting.

         We also need to reset the active dir setting in case of resetting the base dir (and then
         restore back) so that the user won't see empty paths to files when importing settings
         back.
        */
        if (saveLocation.isSafDirActive()) {
            // Save the saveLocationUri
            prevSaveLocationUri = saveLocation.getSafBaseDir().get();

            saveLocation.getSafBaseDir().remove();
            saveLocation.resetFileDir();
            saveLocation.resetActiveDir();
        }

        File file = new File(getAppDir(), sharedPrefsFile.getValue());

        if (!file.exists()) {
            throw new IOException("Shared preferences file does not exist! " +
                    "(" + file.getAbsolutePath() + ")");
        }

        if (!file.canRead()) {
            throw new IOException("Cannot read from shared preferences file! " +
                    "(" + file.getAbsolutePath() + ")");
        }

        byte[] buffer = new byte[(int) file.length()];

        try (FileInputStream inputStream = new FileInputStream(file)) {
            int readAmount = inputStream.read(buffer);

            if (readAmount != file.length()) {
                throw new IOException("Could not read shared prefs file " +
                        "readAmount != fileLength " + readAmount + ", "
                        + file.length());
            }
        }

        // Restore back the previous paths
        if (prevSaveLocationUri != null) {
            ChanSettings.saveLocation.resetFileDir();
            ChanSettings.saveLocation.setSafBaseDir(Uri.parse(prevSaveLocationUri));
        }

        return new String(buffer);
    }

    /**
     * Reads settings from string and writes them to the shared preferences file.
     * Called on the Database thread.
     */
    public static void deserializeFromString(String settings)
            throws IOException {
        File file = new File(getAppDir(), sharedPrefsFile.getValue());

        if (!file.exists()) {
            // Hack to create the shared_prefs file when it does not exist so that we don't cancel
            // settings importing because shared_prefs file does not exist
            String fontSize = ChanSettings.fontSize.get();
            ChanSettings.fontSize.setSyncNoCheck(fontSize);
        }

        if (!file.canWrite()) {
            throw new IOException("Cannot write to shared preferences file! (" + file.getAbsolutePath() + ")");
        }

        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(settings.getBytes());
            outputStream.flush();
        }
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

    public static boolean isSlideLayoutMode() {
        return getCurrentLayoutMode() == LayoutMode.SLIDE;
    }

    public static boolean isSplitLayoutMode() {
        return getCurrentLayoutMode() == LayoutMode.SPLIT;
    }
}
