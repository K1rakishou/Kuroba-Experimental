package com.github.k1rakishou.chan.features.view.media.element

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