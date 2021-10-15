package com.github.k1rakishou.model.data.post

data class PostFilter(
  val ownerFilterId: Long?,
  var enabled: Boolean = false,
  var filterHighlightedColor: Int = 0,
  var filterStub: Boolean = false,
  var filterRemove: Boolean = false,
  var filterWatch: Boolean = false,
  var filterReplies: Boolean = false,
  var filterOnlyOP: Boolean = false,
  var filterSaved: Boolean = false
) {

  val highlightedColor: Int
    get() {
      if (!enabled) {
        return 0
      }

      return filterHighlightedColor
    }

  val stub: Boolean
    get() {
      if (!enabled) {
        return false
      }

      return filterStub
    }

  val remove: Boolean
    get() {
      if (!enabled) {
        return false
      }

      return filterRemove
    }

  val watch: Boolean
    get() {
      if (!enabled) {
        return false
      }

      return filterWatch
    }

  val replies: Boolean
    get() {
      if (!enabled) {
        return false
      }

      return filterReplies
    }

  val onlyOP: Boolean
    get() {
      if (!enabled) {
        return false
      }

      return filterOnlyOP
    }

  val saved: Boolean
    get() {
      if (!enabled) {
        return false
      }

      return filterSaved
    }


}