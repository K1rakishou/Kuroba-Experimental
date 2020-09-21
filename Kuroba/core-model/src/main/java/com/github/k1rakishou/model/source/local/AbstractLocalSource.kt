package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase

abstract class AbstractLocalSource(protected val database: KurobaDatabase) {

  protected suspend fun ensureInTransaction() {
    database.ensureInTransaction()
  }

}