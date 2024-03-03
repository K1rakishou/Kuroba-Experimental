package com.github.k1rakishou.chan.core.loader.impl.post_comment

import com.github.k1rakishou.model.data.video_service.MediaServiceType
import org.joda.time.Period

sealed class ExtraLinkInfo {
  class Success(
    val mediaServiceType: MediaServiceType,
    val title: String?,
    val duration: Period?
  ) : ExtraLinkInfo() {
    fun isEmpty() = title.isNullOrEmpty() && duration == null
  }

  data object Error : ExtraLinkInfo()
  data object NotAvailable : ExtraLinkInfo()
}