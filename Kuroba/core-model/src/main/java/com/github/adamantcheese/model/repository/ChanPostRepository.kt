package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.post.ChanPostUnparsed
import com.github.adamantcheese.model.source.local.ChanPostLocalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ChanPostRepository(
        database: KurobaDatabase,
        loggerTag: String,
        logger: Logger,
        private val localSource: ChanPostLocalSource
) : AbstractRepository(database, logger) {

    suspend fun insert(chanPostUnparsed: ChanPostUnparsed): ModularResult<Unit> {
        return withTransactionSafe { localSource.insert(chanPostUnparsed) }
    }

    fun insertManyBlocking(unparsedPosts: MutableList<ChanPostUnparsed>): ModularResult<Unit> {
        return runBlocking {
            withTransactionSafe {
                // TODO: insert all in one big batch
                unparsedPosts.forEach { localSource.insert(it) }
            }
        }
    }

    fun deleteAllSync(): ModularResult<Int> {
        return runBlocking(Dispatchers.Default) { deleteAll() }
    }

    suspend fun deleteAll(): ModularResult<Int> {
        return withTransactionSafe {
            return@withTransactionSafe localSource.deleteAll()
        }
    }
}