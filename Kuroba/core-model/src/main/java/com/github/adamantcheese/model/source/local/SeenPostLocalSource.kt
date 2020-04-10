package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.SeenPost
import com.github.adamantcheese.model.data.descriptor.ThreadDescriptor
import com.github.adamantcheese.model.mapper.SeenPostMapper
import org.joda.time.DateTime

open class SeenPostLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag SeenPostLocalSource"
    private val seenPostDao = database.seenPostDao()
    private val chanBoardDao = database.chanBoardDao()
    private val chanThreadDao = database.chanThreadDao()

    open suspend fun insert(seenPost: SeenPost) {
        ensureInTransaction()

        val chanBoardEntity = chanBoardDao.insert(
                seenPost.threadDescriptor.siteName(),
                seenPost.threadDescriptor.boardCode()
        )

        val chanThreadEntity = chanThreadDao.insert(
                seenPost.threadDescriptor.opId,
                chanBoardEntity.boardId
        )

        seenPostDao.insert(
                SeenPostMapper.toEntity(chanThreadEntity.threadId, seenPost)
        )

    }

    open suspend fun selectAllByThreadDescriptor(threadDescriptor: ThreadDescriptor): List<SeenPost> {
        ensureInTransaction()

        val chanBoardEntity = chanThreadDao.select(threadDescriptor.opId)
                ?: return emptyList()

        return seenPostDao.selectAllByThreadId(chanBoardEntity.threadId)
                .mapNotNull { seenPostEntity ->
                    return@mapNotNull SeenPostMapper.fromEntity(threadDescriptor, seenPostEntity)
                }
    }

    open suspend fun deleteOlderThan(dateTime: DateTime = ONE_MONTH_AGO): Int {
        ensureInTransaction()

        return seenPostDao.deleteOlderThan(dateTime)

    }

    open suspend fun deleteAll(): Int {
        ensureInTransaction()

        return seenPostDao.deleteAll()
    }

    companion object {
        val ONE_MONTH_AGO = DateTime.now().minusMonths(1)
    }
}