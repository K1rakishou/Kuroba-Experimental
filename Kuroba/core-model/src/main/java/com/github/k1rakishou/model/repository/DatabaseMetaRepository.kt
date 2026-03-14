package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaMainDatabase
import com.github.k1rakishou.model.source.local.DatabaseMetaLocalSource
import kotlinx.coroutines.CoroutineScope

class DatabaseMetaRepository(
  database: KurobaMainDatabase,
  applicationScope: CoroutineScope,
  private val localSource: DatabaseMetaLocalSource
) : AbstractRepository(database, applicationScope) {
  private val TAG = "DatabaseMetaRepository"

  suspend fun checkpoint(): ModularResult<Int> {
    return database.call {
      return@call ModularResult.Try { localSource.checkpoint() }
    }
  }

}