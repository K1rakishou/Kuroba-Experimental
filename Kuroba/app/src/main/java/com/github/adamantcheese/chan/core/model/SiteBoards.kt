package com.github.adamantcheese.chan.core.model

import com.github.adamantcheese.model.data.board.ChanBoard
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor

data class SiteBoards(
  val siteDescriptor: SiteDescriptor,
  val boards: List<ChanBoard>
)