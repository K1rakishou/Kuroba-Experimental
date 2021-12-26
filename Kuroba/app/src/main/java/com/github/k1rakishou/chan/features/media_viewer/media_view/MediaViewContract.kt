package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.graphics.drawable.Drawable
import android.view.View
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

interface MediaViewContract {
  val viewerChanDescriptor: ChanDescriptor?

  fun changeMediaViewerBackgroundAlpha(newAlpha: Float)

  fun toggleSoundMuteState()
  fun isSoundCurrentlyMuted(): Boolean

  fun isSystemUiHidden(): Boolean
  fun onTapped()
  fun closeMediaViewer()
  suspend fun onDownloadButtonClick(viewableMedia: ViewableMedia, longClick: Boolean): Boolean
  fun onOptionsButtonClick(viewableMedia: ViewableMedia)
  fun onMediaLongClick(view: View, viewableMedia: ViewableMedia)
  fun onGoToPostMediaClick(viewableMedia: ViewableMedia, postDescriptor: PostDescriptor)
  fun showReplyChain(postDescriptor: PostDescriptor)
  suspend fun defaultArtworkDrawable(): Drawable?
  fun openAlbum(viewableMedia: ViewableMedia)
  fun reloadAs(pagerPosition: Int, viewableMedia: ViewableMedia)

  fun presentController(controller: Controller, animated: Boolean)
}