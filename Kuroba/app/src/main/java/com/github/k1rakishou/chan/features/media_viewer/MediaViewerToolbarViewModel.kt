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

  fun updateToolbarVisibilityGlobal(nowVisible: Boolean) {
    toolbarStateStorage.values.forEach { toolbarState ->
      toolbarState.toolbarShown = nowVisible
    }
  }

  class ToolbarState(
    val goToPostButtonEnabled: Boolean,
    val reloadButtonEnabled: Boolean,
    val downloadButtonEnabled: Boolean,
    var toolbarShown: Boolean
  )
}