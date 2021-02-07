package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
abstract class DatabaseMetaDao {

  @RawQuery
  abstract suspend fun checkpoint(supportSQLiteQuery: SupportSQLiteQuery): Int

}