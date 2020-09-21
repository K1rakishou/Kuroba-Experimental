package com.github.k1rakishou.chan.core.model

data class PostFilter(
  var enabled: Boolean = false,
  var filterHighlightedColor: Int = 0,
  var filterStub: Boolean = false,
  var filterRemove: Boolean = false,
  var filterWatch: Boolean = false,
  var filterReplies: Boolean = false,
  var filterOnlyOP: Boolean = false,
  var filterSaved: Boolean = false
)