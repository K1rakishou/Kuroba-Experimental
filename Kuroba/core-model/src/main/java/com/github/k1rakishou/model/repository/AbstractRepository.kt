package com.github.k1rakishou.model.repository

import androidx.room.withTransaction
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.model.KurobaMainDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class AbstractRepository(
  protected val database: KurobaMainDatabase,
  protected val coroutineScope: CoroutineScope
) {
  protected suspend fun <T> DatabaseScope.tryWithTransaction(func: suspend () -> T): ModularResult<T> {
    return Try { database.withTransaction(func) }
  }

  @Suppress("RedundantAsync")
  protected suspend fun <T> KurobaMainDatabase.call(
    func: suspend DatabaseScope.() -> T
  ): T {
    return withContext(Dispatchers.IO + NonCancellable) {
      with(DatabaseScopeImpl()) {
        func()
      }
    }
  }

  @Suppress("RedundantAsync")
  protected fun KurobaMainDatabase.callAsync(
    func: suspend DatabaseScope.() -> Unit
  ) {
    coroutineScope.launch(Dispatchers.IO) {
      with(DatabaseScopeImpl()) {
        func()
      }
    }
  }

  interface DatabaseScope
  class DatabaseScopeImpl : DatabaseScope

}