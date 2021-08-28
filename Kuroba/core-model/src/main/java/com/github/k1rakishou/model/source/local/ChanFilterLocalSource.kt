package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.mapper.ChanFilterMapper

class ChanFilterLocalSource(
  database: KurobaDatabase,
  private val isDevFlavor: Boolean,
) : AbstractLocalSource(database) {
  private val TAG = "ChanFilterLocalSource"
  private val chanFilterDao = database.chanFilterDao()

  suspend fun selectAll(): List<ChanFilter> {
    ensureInTransaction()

    return chanFilterDao.selectAll()
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

  suspend fun deleteAll() {
    ensureInTransaction()

    chanFilterDao.deleteAll()
  }

}