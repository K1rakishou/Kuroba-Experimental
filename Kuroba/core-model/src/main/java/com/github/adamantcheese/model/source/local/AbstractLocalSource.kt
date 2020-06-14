package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase

abstract class AbstractLocalSource(protected val database: KurobaDatabase) {

  protected suspend fun ensureInTransaction() {
    require(database.inTransaction()) { "Must be executed in a transaction!" }
  }

}