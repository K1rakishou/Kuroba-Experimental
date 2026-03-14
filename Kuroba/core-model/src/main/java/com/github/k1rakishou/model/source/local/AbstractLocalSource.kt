package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaMainDatabase

abstract class AbstractLocalSource(protected val database: KurobaMainDatabase) {

  protected suspend fun ensureInTransaction() {
    database.ensureInTransaction()
  }

  protected suspend fun ensureNotInTransaction() {
    database.ensureNotInTransaction()
  }

}