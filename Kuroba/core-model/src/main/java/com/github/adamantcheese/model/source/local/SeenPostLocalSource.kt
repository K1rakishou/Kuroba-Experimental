package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.post.SeenPost
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

    val chanThreadEntityId = chanThreadDao.insertDefaultOrIgnore(
      chanBoardEntity.boardId,
      seenPost.threadDescriptor.opNo
    )

    seenPostDao.insert(
      SeenPostMapper.toEntity(chanThreadEntityId, seenPost)
    )

  }

  open suspend fun selectAllByThreadDescriptor(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): List<SeenPost> {
    ensureInTransaction()

    val chanBoardEntity = chanBoardDao.select(
      threadDescriptor.siteName(),
      threadDescriptor.boardCode()
    ) ?: return emptyList()

    val chanThreadEntity = chanThreadDao.select(
      chanBoardEntity.boardId,
      threadDescriptor.opNo
    ) ?: return emptyList()

    return seenPostDao.selectAllByThreadId(chanThreadEntity.threadId)
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

  suspend fun count(): Int {
    ensureInTransaction()

    return seenPostDao.count()
  }

  companion object {
    val ONE_MONTH_AGO = DateTime.now().minusMonths(1)
  }
}