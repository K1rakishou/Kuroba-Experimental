package com.github.k1rakishou.deprecated;

import static com.github.k1rakishou.common.AndroidUtils.getAppMainPreferences;

import com.github.k1rakishou.deprecated.prefs.OptionsSetting;

// TODO: remove me in 1 year
@Deprecated
public class ChanSettingsDeprecated {
  public static void init() {
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
    ThreadIdAscending("thread_id_asc", true),
    ThreadIdDescending("thread_id_desc", false),
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
    // Only auto load if on unmetered network (the setting name is still the same
    // for backward compatibility)
    UNMETERED("wifi"),
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

  public enum CatalogOrThreadSearchMode implements OptionSettingItem {
    Filter("Filter"),
    Highlight("Highlight");

    String name;

    CatalogOrThreadSearchMode(String name) {
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

  public enum Tralse implements OptionSettingItem {
    True("True"),
    False("False"),
    Undefined("Undefined");

    String name;

    Tralse(String name) {
      this.name = name;
    }

    @Override
    public String getKey() {
      return name;
    }
  }

  public static OptionsSetting<LayoutMode> layoutMode;
  public static OptionsSetting<PostAlignmentMode> catalogPostAlignmentMode;
  public static OptionsSetting<PostAlignmentMode> threadPostAlignmentMode;
  public static OptionsSetting<PostThumbnailScaling> postThumbnailScaling;
  public static OptionsSetting<NetworkContentAutoLoadMode> parseYoutubeTitlesAndDuration;
  public static OptionsSetting<NetworkContentAutoLoadMode> parseSoundCloudTitlesAndDuration;
  public static OptionsSetting<NetworkContentAutoLoadMode> parseStreamableTitlesAndDuration;
  public static OptionsSetting<BoardPostViewMode> boardPostViewMode;
  public static OptionsSetting<CatalogOrThreadSearchMode> catalogSearchMode;
  public static OptionsSetting<CatalogOrThreadSearchMode> threadSearchMode;
  public static OptionsSetting<NetworkContentAutoLoadMode> imageAutoLoadNetwork;
  public static OptionsSetting<NetworkContentAutoLoadMode> videoAutoLoadNetwork;
  public static OptionsSetting<BookmarksSortOrder> bookmarksSortOrder;
  public static OptionsSetting<ImageGestureActionType> mediaViewerTopGestureAction;
  public static OptionsSetting<ImageGestureActionType> mediaViewerBottomGestureAction;

  private static void initInternal() {
    SettingProvider provider = new SharedPreferencesSettingProvider(getAppMainPreferences());

    layoutMode = new OptionsSetting<>(provider, "preference_layout_mode", LayoutMode.class, LayoutMode.AUTO);
    catalogPostAlignmentMode = new OptionsSetting<>(provider, "catalog_post_alignment_mode", PostAlignmentMode.class, PostAlignmentMode.AlignRight);
    threadPostAlignmentMode = new OptionsSetting<>(provider, "thread_post_alignment_mode", PostAlignmentMode.class, PostAlignmentMode.AlignRight);
    postThumbnailScaling = new OptionsSetting<>(provider, "post_thumbnail_scaling", PostThumbnailScaling.class, PostThumbnailScaling.FitCenter);
    parseYoutubeTitlesAndDuration = new OptionsSetting<>(provider, "parse_youtube_titles_and_duration_v2", NetworkContentAutoLoadMode.class, NetworkContentAutoLoadMode.UNMETERED);
    parseSoundCloudTitlesAndDuration = new OptionsSetting<>(provider, "parse_soundcloud_titles_and_duration", NetworkContentAutoLoadMode.class, NetworkContentAutoLoadMode.UNMETERED);
    parseStreamableTitlesAndDuration = new OptionsSetting<>(provider, "parse_streamable_titles_and_duration", NetworkContentAutoLoadMode.class, NetworkContentAutoLoadMode.UNMETERED);
    boardPostViewMode = new OptionsSetting<>(provider, "preference_board_view_mode", BoardPostViewMode.class, BoardPostViewMode.LIST);
    catalogSearchMode = new OptionsSetting<>(provider, "catalog_search_mode", CatalogOrThreadSearchMode.class, CatalogOrThreadSearchMode.Highlight);
    threadSearchMode = new OptionsSetting<>(provider, "thread_search_mode", CatalogOrThreadSearchMode.class, CatalogOrThreadSearchMode.Highlight);
    imageAutoLoadNetwork = new OptionsSetting<>(provider, "preference_image_auto_load_network", NetworkContentAutoLoadMode.class, NetworkContentAutoLoadMode.UNMETERED);
    videoAutoLoadNetwork = new OptionsSetting<>(provider, "preference_video_auto_load_network", NetworkContentAutoLoadMode.class, NetworkContentAutoLoadMode.UNMETERED);
    bookmarksSortOrder = new OptionsSetting<>(provider, "bookmarks_comparator", BookmarksSortOrder.class, BookmarksSortOrder.defaultOrder());
    mediaViewerTopGestureAction = new OptionsSetting<>(provider, "media_viewer_top_gesture_action", ImageGestureActionType.class, ImageGestureActionType.CloseImage);
    mediaViewerBottomGestureAction = new OptionsSetting<>(provider, "media_viewer_bottom_gesture_action", ImageGestureActionType.class, ImageGestureActionType.SaveImage);
  }

}
