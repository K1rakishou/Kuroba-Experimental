package com.github.k1rakishou.chan.ui.compose.image

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.ChanSettings

@Immutable
enum class KurobaThumbnailScaling {
  FitCenter,
  CenterCrop;

  companion object {
    fun from(postThumbnailScaling: ChanSettings.PostThumbnailScaling?): KurobaThumbnailScaling? {
      return when (postThumbnailScaling) {
        ChanSettings.PostThumbnailScaling.FitCenter -> FitCenter
        ChanSettings.PostThumbnailScaling.CenterCrop -> CenterCrop
        null -> null
      }
    }
  }
}