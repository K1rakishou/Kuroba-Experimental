package com.github.k1rakishou.model.data.post

data class PostFilter(
  val ownerFilterId: Long?,
  private var filterEnabled: Boolean = false,
  private var filterHighlightedColor: Int = 0,
  private var filterStub: Boolean = false,
  private var filterRemove: Boolean = false,
  private var filterWatch: Boolean = false,
  private var filterReplies: Boolean = false,
  private var filterOnlyOP: Boolean = false,
  private var filterSaved: Boolean = false
) {

  fun update(
    enable: Boolean? = null,
    highlightColor: Int? = null,
    stub: Boolean? = null,
    remove: Boolean? = null,
    watch: Boolean? = null,
    replies: Boolean? = null,
    onlyOP: Boolean? = null,
    saved: Boolean? = null
  ) {
    if (stub != null && remove != null && stub && remove) {
      error("Cannot both stub and remove post at the same time!")
    }

    if (enable != null) {
      filterEnabled = enable
    }

    if (highlightColor != null) {
      filterHighlightedColor = highlightColor
    }

    if (stub != null) {
      filterStub = stub

      if (stub) {
        filterRemove = false
      }
    }

    if (remove != null) {
      filterRemove = remove

      if (remove) {
        filterStub = false
      }
    }

    if (watch != null) {
      filterWatch = watch
    }

    if (replies != null) {
      filterReplies = replies
    }

    if (onlyOP != null) {
      filterOnlyOP = onlyOP
    }

    if (saved != null) {
      filterSaved = saved
    }
  }

  fun hasFilterParameters(): Boolean {
    return filterHighlightedColor != 0
      || filterStub
      || filterSaved
      || filterReplies
  }

  val enabled: Boolean
    get() = filterEnabled

  val highlightedColor: Int
    get() {
      if (!filterEnabled) {
        return 0
      }

      return filterHighlightedColor
    }

  val stub: Boolean
    get() {
      if (!filterEnabled) {
        return false
      }

      return filterStub
    }

  val remove: Boolean
    get() {
      if (!filterEnabled) {
        return false
      }

      return filterRemove
    }

  val watch: Boolean
    get() {
      if (!filterEnabled) {
        return false
      }

      return filterWatch
    }

  val replies: Boolean
    get() {
      if (!filterEnabled) {
        return false
      }

      return filterReplies
    }

  val onlyOP: Boolean
    get() {
      if (!filterEnabled) {
        return false
      }

      return filterOnlyOP
    }

  val saved: Boolean
    get() {
      if (!filterEnabled) {
        return false
      }

      return filterSaved
    }


}