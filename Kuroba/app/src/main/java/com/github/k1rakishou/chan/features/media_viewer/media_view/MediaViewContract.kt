package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.graphics.drawable.Drawable
import android.view.View
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia

interface MediaViewContract {
  fun changeMediaViewerBackgroundAlpha(newAlpha: Float)

  fun toggleSoundMuteState()
  fun isSoundCurrentlyMuted(): Boolean

  fun onTapped()
  fun closeMediaViewer()
  suspend fun onDownloadButtonClick(viewableMedia: ViewableMedia, longClick: Boolean): Boolean
  fun onOptionsButtonClick(viewableMedia: ViewableMedia)
  fun onMediaLongClick(view: View, viewableMedia: ViewableMedia)
  suspend fun defaultArtworkDrawable(): Drawable?
}