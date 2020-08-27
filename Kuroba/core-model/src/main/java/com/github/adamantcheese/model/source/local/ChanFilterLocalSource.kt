package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.filter.ChanFilter
import com.github.adamantcheese.model.mapper.ChanFilterMapper

class ChanFilterLocalSource(
  database: KurobaDatabase,
  loggerTag: String,
  private val isDevFlavor: Boolean,
  private val logger: Logger
) : AbstractLocalSource(database) {
  private val TAG = "$loggerTag ChanFilterLocalSource"
  private val chanFilterDao = database.chanFilterDao()

  suspend fun selectAll(): List<ChanFilter> {
    ensureInTransaction()

    return chanFilterDao.selectAll()
      .sortedBy { chanFilterFull -> chanFilterFull.chanFilterEntity.order }
      .map { chanFilterFull -> ChanFilterMapper.fromEntity(chanFilterFull) }
  }

  suspend fun createFilter(chanFilter: ChanFilter, order: Int): Long {
    ensureInTransaction()

    return chanFilterDao.insertOrIgnore(ChanFilterMapper.toEntity(chanFilter, order))
  }

  suspend fun updateAllFilters(filters: List<ChanFilter>) {
    ensureInTransaction()

    val chanFilterFullList = filters
      .mapIndexed { index, chanFilter -> ChanFilterMapper.toEntity(chanFilter, index) }

    chanFilterDao.updateManyOrIgnore(chanFilterFullList)
  }

  suspend fun deleteFilter(filter: ChanFilter) {
    ensureInTransaction()
    require(filter.hasDatabaseId()) { "filter has not databaseId, filter = ${filter}" }

    chanFilterDao.deleteById(filter.getDatabaseId())
  }

}