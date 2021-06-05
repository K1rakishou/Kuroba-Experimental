package com.github.k1rakishou.chan.features.media_viewer

import androidx.lifecycle.ViewModel

class MediaViewerToolbarViewModel : ViewModel() {
  private val toolbarStateStorage = mutableMapOf<MediaLocation, ToolbarState>()

  fun store(mediaLocation: MediaLocation, toolbarState: ToolbarState) {
    toolbarStateStorage[mediaLocation] = toolbarState
  }

  fun restore(mediaLocation: MediaLocation): ToolbarState? {
    return toolbarStateStorage[mediaLocation]
  }

  data class ToolbarState(
    val goToPostButtonEnabled: Boolean,
    val reloadButtonEnabled: Boolean,
    val downloadButtonEnabled: Boolean,
    val toolbarShown: Boolean
  )
}