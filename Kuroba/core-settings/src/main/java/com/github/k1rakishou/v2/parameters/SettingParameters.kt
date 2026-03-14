package com.github.k1rakishou.v2.parameters

enum class PostThumbnailScaling {
  FitCenter,
  CenterCrop
}

enum class PostAlignmentMode {
  AlignLeft,
  AlignRight
}

enum class ImageGestureActionType {
  SaveImage,
  CloseImage,
  OpenAlbum,
  Disabled;
}

enum class BookmarksSortOrder(val ascending: Boolean) {
  CreatedOnAscending(true),
  CreatedOnDescending(false),
  ThreadIdAscending(true),
  ThreadIdDescending(false),
  UnreadRepliesAscending(true),
  UnreadRepliesDescending(false),
  UnreadPostsAscending(true),
  UnreadPostsDescending(false),
  CustomAscending(true),
  CustomDescending(false);

  companion object {
    fun defaultOrder(): BookmarksSortOrder {
      return BookmarksSortOrder.CustomAscending
    }
  }
}

enum class NetworkContentAutoLoadMode {
  // Always autoload, either Wi-Fi or mobile
  All,

  // Only autoload if on unmetered network (the setting name is still the same
  // for backward compatibility)
  Unmetered,

  // Never auto load
  None
}

enum class CatalogOrThreadSearchMode {
  Filter,
  Highlight
}

enum class BoardPostViewMode {
  List,
  Grid,
  Stagger
}

enum class LayoutMode {
  Auto,
  Slide,
  Phone,
  Split
}

enum class ConcurrentFileDownloadingChunks(val chunksCount: Int) {
  One(1),
  Two(2),
  Four(4)
}