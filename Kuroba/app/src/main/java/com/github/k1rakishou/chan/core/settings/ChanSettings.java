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
package com.github.k1rakishou.chan.core.settings;

import android.net.ConnectivityManager;
import android.net.Uri;

import com.github.k1rakishou.SettingProvider;
import com.github.k1rakishou.chan.BuildConfig;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.settings.base_dir.SavedFilesBaseDirSetting;
import com.github.k1rakishou.chan.core.settings.state.PersistableChanState;
import com.github.k1rakishou.chan.ui.adapter.PostsFilter;
import com.github.k1rakishou.chan.ui.controller.settings.captcha.JsCaptchaCookiesJar;
import com.github.k1rakishou.chan.utils.AndroidUtils;
import com.github.k1rakishou.chan.utils.Logger;
import com.github.k1rakishou.common.DoNotStrip;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.github.k1rakishou.chan.utils.AndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getAppContext;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getAppDir;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getPreferences;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getRes;
import static com.github.k1rakishou.chan.utils.AndroidUtils.isConnected;
import static com.github.k1rakishou.chan.utils.AndroidUtils.postToEventBus;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * This settings class is for all persistable settings that should be saved as preferences. Note that all settings in here
 * will be exported when a backup is exported; for persistable application data that SHOULDN'T be exported, use
 * {@link PersistableChanState} to store that data.
 */

public class ChanSettings {
    private static final String TAG = "ChanSettings";
    public static final String EMPTY_JSON = "{}";
    public static final String NO_HASH_SET = "NO_HASH_SET";
    public static final int HI_RES_THUMBNAIL_SIZE = dp(160);

    @DoNotStrip
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

    @DoNotStrip
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

    @DoNotStrip
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

        public static boolean shouldLoadForNetworkType(NetworkContentAutoLoadMode networkType) {
            if (networkType == NetworkContentAutoLoadMode.NONE) {
                return false;
            } else if (networkType == NetworkContentAutoLoadMode.WIFI) {
                return isConnected(ConnectivityManager.TYPE_WIFI);
            } else {
                return networkType == NetworkContentAutoLoadMode.ALL;
            }
        }
    }

    @DoNotStrip
    public enum PostViewMode
            implements OptionSettingItem {
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

    @DoNotStrip
    public enum LayoutMode
            implements OptionSettingItem {
        AUTO("auto"),
        PHONE("phone"),
        SLIDE("slide"),
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

    @DoNotStrip
    public enum ConcurrentFileDownloadingChunks
            implements OptionSettingItem {
        One("One chunk"),
        Two("Two chunks"),
        Four("Four chunks");

        String name;

        ConcurrentFileDownloadingChunks(String name) {
            this.name = name;
        }

        @Override
        public String getKey() {
            return name;
        }

        public int toInt() {
            return (int) Math.pow(2, ordinal());
        }
    }

    @DoNotStrip
    public enum ImageClickPreloadStrategy
            implements OptionSettingItem {
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

    private static final String sharedPrefsFile = "shared_prefs/" + BuildConfig.APPLICATION_ID + "_preferences.xml";

    //region Declarations
    //region THREAD WATCHER
    public static final BooleanSetting watchEnabled;
    public static final BooleanSetting watchBackground;
    public static final IntegerSetting watchBackgroundInterval;
    public static final BooleanSetting replyNotifications;
    public static final BooleanSetting useSoundForReplyNotifications;
    public static final BooleanSetting watchLastPageNotify;
    public static final BooleanSetting useSoundForLastPageNotifications;
    //endregion

    //region APPEARANCE
    // Theme
    public static final BooleanSetting imageViewerFullscreenMode;
    public static final BooleanSetting isCurrentThemeDark;

    // Layout
    public static final OptionsSetting<LayoutMode> layoutMode;
    public static final IntegerSetting boardGridSpanCount;
    public static final BooleanSetting neverHideToolbar;
    public static final BooleanSetting enableReplyFab;
    public static final BooleanSetting captchaOnBottom;
    public static final BooleanSetting neverShowPages;

    //Post
    public static final StringSetting fontSize;
    public static final BooleanSetting postFullDate;
    public static final BooleanSetting postFileInfo;
    public static final BooleanSetting postFilename;
    public static final BooleanSetting textOnly;
    public static final BooleanSetting revealTextSpoilers;
    public static final BooleanSetting anonymize;
    public static final BooleanSetting showAnonymousName;
    public static final BooleanSetting anonymizeIds;
    public static final BooleanSetting markYourPostsOnScrollbar;
    public static final BooleanSetting markRepliesToYourPostOnScrollbar;
    public static final BooleanSetting markCrossThreadQuotesOnScrollbar;

    // Post links parsing
    public static final OptionsSetting<NetworkContentAutoLoadMode> parseYoutubeTitlesAndDuration;
    public static final OptionsSetting<NetworkContentAutoLoadMode> parseSoundCloudTitlesAndDuration;
    public static final OptionsSetting<NetworkContentAutoLoadMode> parseStreamableTitlesAndDuration;
    public static final BooleanSetting showLinkAlongWithTitleAndDuration;

    // Images
    public static final BooleanSetting hideImages;
    public static final BooleanSetting removeImageSpoilers;
    public static final BooleanSetting revealImageSpoilers;
    public static final BooleanSetting highResCells;
    public static final BooleanSetting parsePostImageLinks;
    public static final BooleanSetting fetchInlinedFileSizes;
    public static final BooleanSetting transparencyOn;

    // Set elsewhere in the application
    public static final OptionsSetting<PostViewMode> boardViewMode;
    public static final StringSetting boardOrder;
    //endregion

    //region BEHAVIOUR
    // General
    public static final BooleanSetting autoRefreshThread;
    public static final BooleanSetting controllerSwipeable;
    public static final BooleanSetting openLinkConfirmation;
    public static final BooleanSetting openLinkBrowser;
    public static final StringSetting jsCaptchaCookies;
    public static final BooleanSetting loadLastOpenedBoardUponAppStart;
    public static final BooleanSetting loadLastOpenedThreadUponAppStart;

    // Reply
    public static final BooleanSetting postPinThread;
    public static final StringSetting postDefaultName;

    // Post
    public static final BooleanSetting volumeKeysScrolling;
    public static final BooleanSetting tapNoReply;
    public static final BooleanSetting markUnseenPosts;

    // Other options
    public static final BooleanSetting fullUserRotationEnable;
    public static final BooleanSetting allowFilePickChooser;
    public static final BooleanSetting showCopyApkUpdateDialog;
    //endregion

    //region MEDIA
    // Saving
    public static final SavedFilesBaseDirSetting saveLocation;
    public static final BooleanSetting saveBoardFolder;
    public static final BooleanSetting saveThreadFolder;
    public static final BooleanSetting saveAlbumBoardFolder;
    public static final BooleanSetting saveAlbumThreadFolder;
    public static final BooleanSetting saveServerFilename;

    // Video settings
    public static final BooleanSetting videoAutoLoop;
    public static final BooleanSetting videoDefaultMuted;
    public static final BooleanSetting headsetDefaultMuted;
    public static final BooleanSetting videoOpenExternal;
    public static final BooleanSetting videoStream;

    // Media loading
    public static final OptionsSetting<NetworkContentAutoLoadMode> imageAutoLoadNetwork;
    public static final OptionsSetting<NetworkContentAutoLoadMode> videoAutoLoadNetwork;
    public static final OptionsSetting<ImageClickPreloadStrategy> imageClickPreloadStrategy;
    public static final BooleanSetting autoLoadThreadImages;
    public static final BooleanSetting showPrefetchLoadingIndicator;
    //endregion

    //region EXPERIMENTAL
    public static final OptionsSetting<ConcurrentFileDownloadingChunks> concurrentDownloadChunkCount;
    public static final StringSetting androidTenGestureZones;
    public static final BooleanSetting okHttpAllowHttp2;
    public static final BooleanSetting okHttpAllowIpv6;
    public static final BooleanSetting cloudflareForcePreload;
    //endregion

    //region OTHER
    public static final BooleanSetting historyEnabled;
    public static final BooleanSetting collectCrashLogs;
    //endregion

    //region DEVELOPER
    public static final BooleanSetting crashOnSafeThrow;
    public static final BooleanSetting verboseLogs;
    //endregion

    //region DATA
    // While not a setting, the last image options selected should be persisted even after import.
    public static final StringSetting lastImageOptions;

    // While these are not "settings", they are here instead of in PersistableChanState because they control the appearance of hints.
    // Hints should not be shown if re-imported.
    public static final CounterSetting historyOpenCounter;
    public static final CounterSetting threadOpenCounter;
    public static final IntegerSetting drawerAutoOpenCount;
    public static final BooleanSetting reencodeHintShown;
    public static final CounterSetting replyOpenCounter;
    public static final BooleanSetting scrollingTextForThreadTitles;
    public static final OptionsSetting<BookmarksSortOrder> bookmarksSortOrder;
    public static final BooleanSetting moveNotActiveBookmarksToBottom;
    public static final BooleanSetting moveBookmarksWithUnreadRepliesToTop;
    public static final BooleanSetting ignoreDarkNightMode;
    public static final RangeSetting bookmarkGridViewWidth;
    public static final OptionsSetting<ImageGestureActionType> imageSwipeUpGesture;
    public static final OptionsSetting<ImageGestureActionType> imageSwipeDownGesture;
    //endregion
    //endregion


    static {
        try {
            SettingProvider p = new SharedPreferencesSettingProvider(getPreferences());

            //region THREAD WATCHER
            watchEnabled = new BooleanSetting(p, "preference_watch_enabled", false);
            watchEnabled.addCallback((setting, value) -> postToEventBus(new SettingChanged<>(watchEnabled)));
            watchBackground = new BooleanSetting(p, "preference_watch_background_enabled", false);
            watchBackground.addCallback((setting, value) -> postToEventBus(new SettingChanged<>(watchBackground)));
            watchBackgroundInterval = new IntegerSetting(p, "preference_watch_background_interval", (int) MINUTES.toMillis(30));
            watchBackgroundInterval.addCallback((setting, value) -> postToEventBus(new SettingChanged<>(watchBackgroundInterval)));
            replyNotifications = new BooleanSetting(p, "reply_notifications", true);
            useSoundForReplyNotifications = new BooleanSetting(p, "use_sound_for_reply_notifications", false);
            watchLastPageNotify = new BooleanSetting(p, "preference_watch_last_page_notify", false);
            useSoundForLastPageNotifications = new BooleanSetting(p, "use_sound_for_last_page_notifications", false);
            //endregion

            //region APPEARANCE
            // Theme
            imageViewerFullscreenMode = new BooleanSetting(p, "image_viewer_fullscreen_mode", true);
            isCurrentThemeDark = new BooleanSetting(p, "is_current_theme_dark", true);

            //Layout
            layoutMode = new OptionsSetting<>(p, "preference_layout_mode", LayoutMode.class, LayoutMode.AUTO);
            boardGridSpanCount = new IntegerSetting(p, "preference_board_grid_span_count", 0);
            neverHideToolbar = new BooleanSetting(p, "preference_never_hide_toolbar", false);
            enableReplyFab = new BooleanSetting(p, "preference_enable_reply_fab", true);
            captchaOnBottom = new BooleanSetting(p, "captcha_on_bottom", true);
            neverShowPages = new BooleanSetting(p, "never_show_page_number", false);

            // Post
            fontSize = new StringSetting(p, "preference_font", getRes().getBoolean(R.bool.is_tablet) ? "16" : "14");
            postFullDate = new BooleanSetting(p, "preference_post_full_date", false);
            postFileInfo = new BooleanSetting(p, "preference_post_file_info", true);
            postFilename = new BooleanSetting(p, "preference_post_filename", true);
            textOnly = new BooleanSetting(p, "preference_text_only", false);
            revealTextSpoilers = new BooleanSetting(p, "preference_reveal_text_spoilers", false);
            anonymize = new BooleanSetting(p, "preference_anonymize", false);
            showAnonymousName = new BooleanSetting(p, "preference_show_anonymous_name", false);
            anonymizeIds = new BooleanSetting(p, "preference_anonymize_ids", false);
            markYourPostsOnScrollbar = new BooleanSetting(p, "mark_your_posts_on_scrollbar", true);
            markRepliesToYourPostOnScrollbar = new BooleanSetting(p, "mark_replies_to_your_posts_on_scrollbar", true);
            markCrossThreadQuotesOnScrollbar = new BooleanSetting(p, "mark_cross_thread_quotes_on_scrollbar", false);

            // Post links parsing
            parseYoutubeTitlesAndDuration = new OptionsSetting<>(
                    p,
                    "parse_youtube_titles_and_duration_v2",
                    NetworkContentAutoLoadMode.class,
                    NetworkContentAutoLoadMode.WIFI
            );
            parseSoundCloudTitlesAndDuration = new OptionsSetting<>(
                    p,
                    "parse_soundcloud_titles_and_duration",
                    NetworkContentAutoLoadMode.class,
                    NetworkContentAutoLoadMode.WIFI
            );
            parseStreamableTitlesAndDuration = new OptionsSetting<>(
                    p,
                    "parse_streamable_titles_and_duration",
                    NetworkContentAutoLoadMode.class,
                    NetworkContentAutoLoadMode.WIFI
            );
            showLinkAlongWithTitleAndDuration = new BooleanSetting(p, "show_link_along_with_title_and_duration", true);

            // Images
            hideImages = new BooleanSetting(p, "preference_hide_images", false);
            removeImageSpoilers = new BooleanSetting(p, "preference_reveal_image_spoilers", false);
            revealImageSpoilers = new BooleanSetting(p, "preference_auto_unspoil_images", true);
            highResCells = new BooleanSetting(p, "high_res_cells", false);
            parsePostImageLinks = new BooleanSetting(p, "parse_post_image_links", true);
            fetchInlinedFileSizes = new BooleanSetting(p, "fetch_inlined_file_size", false);
            transparencyOn = new BooleanSetting(p, "image_transparency_on", false);

            //Elsewhere
            boardViewMode =
                    new OptionsSetting<>(p, "preference_board_view_mode", PostViewMode.class, PostViewMode.LIST);
            boardOrder = new StringSetting(p, "preference_board_order", PostsFilter.Order.BUMP.getOrderName());
            //endregion

            //region BEHAVIOUR
            // General
            autoRefreshThread = new BooleanSetting(p, "preference_auto_refresh_thread", true);
            controllerSwipeable = new BooleanSetting(p, "preference_controller_swipeable", true);
            openLinkConfirmation = new BooleanSetting(p, "preference_open_link_confirmation", false);
            openLinkBrowser = new BooleanSetting(p, "preference_open_link_browser", false);
            jsCaptchaCookies = new StringSetting(p, "js_captcha_cookies", EMPTY_JSON);
            loadLastOpenedBoardUponAppStart = new BooleanSetting(p, "load_last_opened_board_upon_app_start", true);
            loadLastOpenedThreadUponAppStart = new BooleanSetting(p, "load_last_opened_thread_upon_app_start", true);

            // Reply
            postPinThread = new BooleanSetting(p, "preference_pin_on_post", false);
            postDefaultName = new StringSetting(p, "preference_default_name", "");

            // Post
            volumeKeysScrolling = new BooleanSetting(p, "preference_volume_key_scrolling", false);
            tapNoReply = new BooleanSetting(p, "preference_tap_no_reply", false);
            markUnseenPosts = new BooleanSetting(p, "preference_mark_unseen_posts", true);

            // Other options
            fullUserRotationEnable = new BooleanSetting(p, "full_user_rotation_enable", true);
            allowFilePickChooser = new BooleanSetting(p, "allow_file_picker_chooser", false);
            showCopyApkUpdateDialog = new BooleanSetting(p, "show_copy_apk_update_dialog", true);
            //endregion

            //region MEDIA
            // Saving
            saveLocation = new SavedFilesBaseDirSetting(p);
            saveBoardFolder = new BooleanSetting(p, "preference_save_subboard", false);
            saveThreadFolder = new BooleanSetting(p, "preference_save_subthread", false);
            saveAlbumBoardFolder = new BooleanSetting(p, "preference_save_album_subboard", false);
            saveAlbumThreadFolder = new BooleanSetting(p, "preference_save_album_subthread", false);
            saveServerFilename = new BooleanSetting(p, "preference_image_save_original", false);

            // Video Settings
            videoAutoLoop = new BooleanSetting(p, "preference_video_loop", true);
            videoDefaultMuted = new BooleanSetting(p, "preference_video_default_muted", true);
            headsetDefaultMuted = new BooleanSetting(p, "preference_headset_default_muted", true);
            videoOpenExternal = new BooleanSetting(p, "preference_video_external", false);
            videoStream = new BooleanSetting(p, "preference_video_stream", false);

            // Media loading
            imageAutoLoadNetwork = new OptionsSetting<>(p,
                    "preference_image_auto_load_network",
                    NetworkContentAutoLoadMode.class,
                    NetworkContentAutoLoadMode.WIFI
            );
            videoAutoLoadNetwork = new OptionsSetting<>(p,
                    "preference_video_auto_load_network",
                    NetworkContentAutoLoadMode.class,
                    NetworkContentAutoLoadMode.WIFI
            );
            imageClickPreloadStrategy = new OptionsSetting<>(p,
                    "image_click_preload_strategy",
                    ImageClickPreloadStrategy.class,
                    ImageClickPreloadStrategy.PreloadNext
            );
            autoLoadThreadImages = new BooleanSetting(p, "preference_auto_load_thread", false);
            showPrefetchLoadingIndicator = new BooleanSetting(p, "show_prefetch_loading_indicator", false);
            cloudflareForcePreload = new BooleanSetting(p, "cloudflare_force_preload", false);
            //endregion

            //region EXPERIMENTAL
            concurrentDownloadChunkCount = new OptionsSetting<>(p,
                    "concurrent_file_downloading_chunks_count",
                    ConcurrentFileDownloadingChunks.class,
                    ConcurrentFileDownloadingChunks.Two
            );
            androidTenGestureZones = new StringSetting(p, "android_ten_gesture_zones", EMPTY_JSON);
            okHttpAllowHttp2 = new BooleanSetting(p, "ok_http_allow_http_2", true);
            okHttpAllowIpv6 = new BooleanSetting(p, "ok_http_allow_ipv6", false);
            //endregion

            //region OTHER
            historyEnabled = new BooleanSetting(p, "preference_history_enabled", true);
            collectCrashLogs = new BooleanSetting(p, "collect_crash_logs", true);
            //endregion

            //region DEVELOPER
            crashOnSafeThrow = new BooleanSetting(p, "crash_on_safe_throw", true);
            verboseLogs = new BooleanSetting(
                    p,
                    "verbose_logs",
                    // Always true by default for dev flavor
                    AndroidUtils.isDevBuild()
            );
            //endregion

            //region DATA
            lastImageOptions = new StringSetting(p, "last_image_options", "");
            historyOpenCounter = new CounterSetting(p, "counter_history_open");
            threadOpenCounter = new CounterSetting(p, "counter_thread_open");
            drawerAutoOpenCount = new IntegerSetting(p, "drawer_auto_open_count", 0);
            reencodeHintShown = new BooleanSetting(p, "preference_reencode_hint_already_shown", false);
            replyOpenCounter = new CounterSetting(p, "reply_open_counter");
            ignoreDarkNightMode = new BooleanSetting(p, "ignore_dark_night_mode", false);

            bookmarksSortOrder = new OptionsSetting<>(p,
                    "bookmarks_comparator",
                    BookmarksSortOrder.class,
                    BookmarksSortOrder.defaultOrder()
            );

            moveNotActiveBookmarksToBottom = new BooleanSetting(p, "move_not_active_bookmarks_to_bottom", false);
            moveBookmarksWithUnreadRepliesToTop = new BooleanSetting(p, "move_bookmarks_with_unread_replies_to_top", false);
            //endregion

            scrollingTextForThreadTitles = new BooleanSetting(p, "scrolling_text_for_thread_titles", true);

            bookmarkGridViewWidth = new RangeSetting(
                    p,
                    "bookmark_grid_view_width",
                    (int) getAppContext().getResources().getDimension(R.dimen.thread_grid_bookmark_view_default_width),
                    (int) getAppContext().getResources().getDimension(R.dimen.thread_grid_bookmark_view_min_width),
                    (int) getAppContext().getResources().getDimension(R.dimen.thread_grid_bookmark_view_max_width)
            );

            imageSwipeUpGesture = new OptionsSetting<>(
                    p,
                    "image_swipe_up_gesture",
                    ImageGestureActionType.class,
                    ImageGestureActionType.CloseImage
            );
            imageSwipeDownGesture = new OptionsSetting<>(
                    p,
                    "image_swipe_down_gesture",
                    ImageGestureActionType.class,
                    ImageGestureActionType.SaveImage
            );
        } catch (Throwable error) {
            // If something crashes while the settings are initializing we at least will have the
            // stacktrace. Otherwise we won't because of Feather.
            Logger.e(TAG, "Error while initializing the settings", error);
            throw error;
        }
    }

    public static JsCaptchaCookiesJar getJsCaptchaCookieJar(Gson gson) {
        try {
            return gson.fromJson(ChanSettings.jsCaptchaCookies.get(), JsCaptchaCookiesJar.class);
        } catch (Throwable error) {
            Logger.e(TAG, "Error while trying to deserialize JsCaptchaCookiesJar", error);
            return JsCaptchaCookiesJar.empty();
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

        File file = new File(getAppDir(), sharedPrefsFile);

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
        File file = new File(getAppDir(), sharedPrefsFile);

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
            if (AndroidUtils.isTablet()) {
                layoutMode = ChanSettings.LayoutMode.SPLIT;
            } else {
                layoutMode = ChanSettings.LayoutMode.SLIDE;
            }
        }

        return layoutMode;
    }

    public static class ThemeColor {
        public String theme;
        public String color;
        public String accentColor;

        public ThemeColor(String theme, String color, String accentColor) {
            this.theme = theme;
            this.color = color;
            this.accentColor = accentColor;
        }
    }

    public static class SettingChanged<T> {
        public final Setting<T> setting;

        public SettingChanged(Setting<T> setting) {
            this.setting = setting;
        }
    }
}
