package com.github.k1rakishou.chan.ui.compose.post.state

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.ChanSettings

@Immutable
enum class PostThumbnailAlignmentUi {
  Left,
  Right;

  companion object {
    fun from(postAlignmentMode: ChanSettings.PostAlignmentMode?): PostThumbnailAlignmentUi? {
      return when (postAlignmentMode) {
        ChanSettings.PostAlignmentMode.AlignLeft -> PostThumbnailAlignmentUi.Right
        ChanSettings.PostAlignmentMode.AlignRight -> PostThumbnailAlignmentUi.Left
        null -> null
      }
    }
  }
}