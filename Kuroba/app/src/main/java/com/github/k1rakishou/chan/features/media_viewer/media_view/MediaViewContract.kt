package com.github.k1rakishou.chan.features.media_viewer.media_view

import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia

interface MediaViewContract {
  fun changeMediaViewerBackgroundAlpha(newAlpha: Float)

  fun toggleSoundMuteState()
  fun isSoundCurrentlyMuted(): Boolean

  fun onTapped()
  fun closeMediaViewer()
  fun onDownloadButtonClick(viewableMedia: ViewableMedia, longClick: Boolean)
  fun onOptionsButtonClick(viewableMedia: ViewableMedia)
}