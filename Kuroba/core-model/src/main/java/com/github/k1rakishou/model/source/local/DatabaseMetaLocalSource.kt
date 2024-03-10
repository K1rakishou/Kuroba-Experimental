package com.github.k1rakishou.model.source.local

import androidx.sqlite.db.SimpleSQLiteQuery
import com.github.k1rakishou.model.KurobaDatabase

class DatabaseMetaLocalSource(
  database: KurobaDatabase,
) : AbstractLocalSource(database) {
  private val TAG = "DatabaseMetaLocalSource"
  private val dao = database.databaseMetaDao()

  suspend fun checkpoint(): Int {
    ensureNotInTransaction()

    return dao.checkpoint(SimpleSQLiteQuery("pragma wal_checkpoint(full)"))
  }

}