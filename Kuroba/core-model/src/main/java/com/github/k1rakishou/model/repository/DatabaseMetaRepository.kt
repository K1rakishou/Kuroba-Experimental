package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.source.local.DatabaseMetaLocalSource
import kotlinx.coroutines.CoroutineScope

class DatabaseMetaRepository(
  database: KurobaDatabase,
  private val applicationScope: CoroutineScope,
  private val localSource: DatabaseMetaLocalSource
) : AbstractRepository(database) {
  private val TAG = "DatabaseMetaRepository"

  suspend fun checkpoint(): ModularResult<Int> {
    return applicationScope.dbCall {
      return@dbCall ModularResult.Try { localSource.checkpoint() }
    }
  }

}