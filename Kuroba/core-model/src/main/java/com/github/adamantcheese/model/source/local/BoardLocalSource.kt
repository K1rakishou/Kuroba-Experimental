package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.board.ChanBoard
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.github.adamantcheese.model.mapper.ChanBoardMapper
import com.github.adamantcheese.model.source.cache.ChanDescriptorCache

class BoardLocalSource(
  database: KurobaDatabase,
  loggerTag: String,
  private val isDevFlavor: Boolean,
  private val logger: Logger,
  private val chanDescriptorCache: ChanDescriptorCache
) : AbstractLocalSource(database) {
  private val TAG = "$loggerTag BoardLocalSource"
  private val chanBoardDao = database.chanBoardDao()

  suspend fun selectAllActiveBoards(): Map<SiteDescriptor, List<ChanBoard>> {
    ensureInTransaction()

    return chanBoardDao.selectAllActiveBoards()
      .mapNotNull { chanBoardFull -> ChanBoardMapper.fromChanBoardEntity(chanBoardFull) }
      .groupBy { it.boardDescriptor.siteDescriptor }
  }

}