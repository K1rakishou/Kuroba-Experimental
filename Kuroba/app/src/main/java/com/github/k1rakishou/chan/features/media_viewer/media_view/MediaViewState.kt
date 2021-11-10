package com.github.k1rakishou.chan.features.media_viewer.media_view

import androidx.annotation.CallSuper

abstract class MediaViewState(
  val audioPlayerViewState: AudioPlayerView.AudioPlayerViewState? = null
) {

  @CallSuper
  open fun resetPosition() {

  }

  abstract fun clone(): MediaViewState
  abstract fun updateFrom(other: MediaViewState?)
}