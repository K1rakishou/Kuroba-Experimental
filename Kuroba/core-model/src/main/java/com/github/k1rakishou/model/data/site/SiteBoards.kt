package com.github.k1rakishou.model.data.site

import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

sealed interface SiteBoards {
  data class Progress(
    val current: Int,
    val total: Int
  ) : SiteBoards

  sealed interface Result : SiteBoards {
    data class Error(val error: Throwable) : Result

    data class Success(
      val siteDescriptor: SiteDescriptor,
      val boards: List<ChanBoard>
    ) : Result
  }
}