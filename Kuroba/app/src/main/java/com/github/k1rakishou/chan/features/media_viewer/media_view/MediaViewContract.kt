package com.github.k1rakishou.chan.features.media_viewer.media_view

interface MediaViewContract {
  fun changeMediaViewerBackgroundAlpha(newAlpha: Float)

  fun onTapped()
  fun closeMediaViewer()
}