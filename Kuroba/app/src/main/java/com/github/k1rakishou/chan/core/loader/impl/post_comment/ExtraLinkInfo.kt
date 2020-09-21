package com.github.k1rakishou.chan.core.loader.impl.post_comment

import org.joda.time.Period

internal sealed class ExtraLinkInfo {
  class Success(val title: String?, val duration: Period?) : ExtraLinkInfo() {
    fun isEmpty() = title.isNullOrEmpty() && duration == null
  }

  object Error : ExtraLinkInfo()
  object NotAvailable : ExtraLinkInfo()
}