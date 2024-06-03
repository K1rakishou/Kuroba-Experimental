package com.github.k1rakishou.chan.ui.compose.post.state

import androidx.compose.ui.unit.Dp
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.ui.compose.image.KurobaThumbnailScaling
import com.github.k1rakishou.chan.ui.controller.popup.PostRepliesPopupController

data class ThreadInitializationState(
  val initialWindowLoaded: Boolean,
  val thumbnailSize: Dp?,
  val thumbnailScaling: KurobaThumbnailScaling?,
  val postMultipleImagesCompactMode: Boolean?,
  val postTitleFontSize: Int?,
  val postCommentFontSize: Int?,
  val catalogThumbnailAlignment: PostThumbnailAlignmentUi?,
  val threadThumbnailAlignment: PostThumbnailAlignmentUi?
) {

  fun isInitialized(localDisplayingData: AsyncData<PostRepliesPopupController.PostRepliesPopupData>): Boolean {
    if (localDisplayingData is AsyncData.NotInitialized || localDisplayingData is AsyncData.Loading) {
      return false
    }

    if (!initialWindowLoaded) {
      return false
    }

    if (thumbnailSize == null || postMultipleImagesCompactMode == null || thumbnailScaling == null) {
      return false
    }

    if (catalogThumbnailAlignment == null || threadThumbnailAlignment == null) {
      return false
    }

    if (postTitleFontSize == null || postCommentFontSize == null) {
      return false
    }

    return true
  }

  companion object {
    fun fromArray(array: Array<Comparable<*>?>): ThreadInitializationState {
      val initialWindowLoaded: Boolean = array[0] as Boolean
      val thumbnailSize: Dp? = array[1] as Dp?
      val thumbnailScaling: KurobaThumbnailScaling? = array[2] as KurobaThumbnailScaling?
      val postMultipleImagesCompactMode: Boolean? = array[3] as  Boolean?
      val postTitleFontSize: Int? = array[4] as Int?
      val postCommentFontSize: Int? = array[5] as Int?
      val catalogThumbnailAlignment: PostThumbnailAlignmentUi? = array[6] as PostThumbnailAlignmentUi?
      val threadThumbnailAlignment: PostThumbnailAlignmentUi? = array[7] as PostThumbnailAlignmentUi?

      return ThreadInitializationState(
        initialWindowLoaded = initialWindowLoaded,
        thumbnailSize = thumbnailSize,
        thumbnailScaling = thumbnailScaling,
        postMultipleImagesCompactMode = postMultipleImagesCompactMode,
        postTitleFontSize = postTitleFontSize,
        postCommentFontSize = postCommentFontSize,
        catalogThumbnailAlignment = catalogThumbnailAlignment,
        threadThumbnailAlignment = threadThumbnailAlignment
      )
    }
  }

}