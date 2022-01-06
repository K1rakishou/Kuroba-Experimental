package com.github.k1rakishou.chan.features.media_viewer.strip

import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

interface MediaViewerBottomActionStripCallbacks {
  suspend fun reloadMedia()
  suspend fun downloadMedia(isLongClick: Boolean): Boolean
  fun onOptionsButtonClick()
  fun onShowRepliesButtonClick(postDescriptor: PostDescriptor)
  fun onGoToPostMediaClick(viewableMedia: ViewableMedia, postDescriptor: PostDescriptor)
}