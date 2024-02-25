package com.github.k1rakishou.model.repository

import androidx.room.withTransaction
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.model.KurobaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

abstract class AbstractRepository(
  private val database: KurobaDatabase
) {
  private val dbDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

  protected suspend fun <T> tryWithTransaction(func: suspend () -> T): ModularResult<T> {
    return Try { database.withTransaction(func) }
  }

  protected fun isInTransaction() = database.inTransaction()

  @Suppress("RedundantAsync")
  protected suspend fun <T> CoroutineScope.dbCall(
    func: suspend () -> T
  ): T {
    return withContext(dbDispatcher + NonCancellable) { func() }
  }

  @Suppress("RedundantAsync")
  protected suspend fun CoroutineScope.dbCallAsync(
    func: suspend () -> Unit
  ) {
    val scope = this
    scope.launch(dbDispatcher) { func() }
  }

}