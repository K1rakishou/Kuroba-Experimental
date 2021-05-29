package com.github.k1rakishou.chan.features.media_viewer.media_view

interface MediaViewState {
  fun clone(): MediaViewState
  fun updateFrom(other: MediaViewState?)
}