package com.github.adamantcheese.database.repository

import androidx.room.withTransaction
import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.database.KurobaDatabase

abstract class AbstractRepository(
        private val database: KurobaDatabase
) {

    suspend fun <T> runInTransaction(func: suspend () -> ModularResult<T>): ModularResult<T> {
        return ModularResult.safeRun { database.withTransaction(func).unwrap() }
    }

    protected fun isInTransaction() = database.inTransaction()

}