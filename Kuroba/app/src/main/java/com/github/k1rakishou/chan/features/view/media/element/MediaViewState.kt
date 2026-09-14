package com.github.k1rakishou.chan.features.view.media.element

import androidx.annotation.CallSuper
import com.github.k1rakishou.chan.features.view.media.soundpost.SoundPostPlayer

abstract class MediaViewState {
  val soundPostState: SoundPostPlayer.State = SoundPostPlayer.State()

  @CallSuper
  open fun resetPosition() {
    soundPostState.resetPosition()
  }

  abstract fun clone(): MediaViewState
  abstract fun updateFrom(other: MediaViewState?)
}
