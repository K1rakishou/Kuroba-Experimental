package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.ChanPostUnparsed
import com.github.adamantcheese.model.mapper.ChanPostMapper

class ChanPostLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag ChanPostLocalSource"
    private val chanBoardDao = database.chanBoardDao()
    private val chanThreadDao = database.chanThreadDao()
    private val chanPostDao = database.chanPostDao()

    suspend fun insert(chanPostUnparsed: ChanPostUnparsed) {
        require(chanPostUnparsed.postDescriptor.threadDescriptor.opId >= 0)
        ensureInTransaction()

        val chanBoardEntity = chanBoardDao.insert(
                chanPostUnparsed.postDescriptor.threadDescriptor.siteName(),
                chanPostUnparsed.postDescriptor.threadDescriptor.boardCode()
        )

        val chanThreadEntity = chanThreadDao.insert(
                chanPostUnparsed.postDescriptor.threadDescriptor.opId,
                chanBoardEntity.boardId
        )

        chanPostDao.insertOrUpdate(
                chanThreadEntity.threadId,
                chanPostUnparsed.postDescriptor.postId,
                ChanPostMapper.toEntity(chanPostUnparsed)
        )
    }

    open suspend fun deleteAll(): Int {
        ensureInTransaction()

        return chanPostDao.deleteAll()
    }
}