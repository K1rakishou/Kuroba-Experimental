package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.SeenPost
import com.github.k1rakishou.model.mapper.SeenPostMapper
import org.joda.time.DateTime

open class SeenPostLocalSource(
  database: KurobaDatabase
) : AbstractLocalSource(database) {
  private val seenPostDao = database.seenPostDao()
  private val chanBoardDao = database.chanBoardDao()
  private val chanThreadDao = database.chanThreadDao()

  open suspend fun insertMany(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    seenPosts: Collection<SeenPost>
  ) {
    ensureInTransaction()

    val chanBoardEntity = chanBoardDao.insertBoardId(
      threadDescriptor.siteName(),
      threadDescriptor.boardCode()
    )

    val chanThreadEntityId = chanThreadDao.insertDefaultOrIgnore(
      chanBoardEntity.boardId,
      threadDescriptor.threadNo
    )

    val seenPostEntities = seenPosts
      .map { seenPost -> SeenPostMapper.toEntity(chanThreadEntityId, seenPost) }

    seenPostDao.insertMany(seenPostEntities)
  }

  open suspend fun selectAllByThreadDescriptor(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): List<SeenPost> {
    ensureInTransaction()

    val chanBoardEntity = chanBoardDao.selectBoardId(
      threadDescriptor.siteName(),
      threadDescriptor.boardCode()
    ) ?: return emptyList()

    val chanThreadEntity = chanThreadDao.select(
      chanBoardEntity.boardId,
      threadDescriptor.threadNo
    ) ?: return emptyList()

    return seenPostDao.selectAllByThreadId(chanThreadEntity.threadId)
      .mapNotNull { seenPostEntity ->
        return@mapNotNull SeenPostMapper.fromEntity(threadDescriptor, seenPostEntity)
      }
  }

  suspend fun selectAllByThreadDescriptors(
    boardDescriptor: BoardDescriptor,
    threadDescriptors: List<ChanDescriptor.ThreadDescriptor>
  ): List<SeenPost> {
    ensureInTransaction()

    val chanBoardEntity = chanBoardDao.selectBoardId(
      boardDescriptor.siteName(),
      boardDescriptor.boardCode
    ) ?: return emptyList()

    return threadDescriptors
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { threadDescriptorBatch ->
        val threadNos = threadDescriptorBatch.map { threadDescriptor -> threadDescriptor.threadNo }

        val threadIds = chanThreadDao.selectManyThreadIdsByThreadNos(
          chanBoardEntity.boardId,
          threadNos
        )

        return@flatMap seenPostDao.selectManyOriginalPostsByThreadId(threadIds)
          .mapNotNull { seenPostEntity ->
            val threadDescriptor = threadDescriptorBatch
              .firstOrNull { threadDescriptor -> threadDescriptor.threadNo == seenPostEntity.postNo }
              ?: return@mapNotNull null

            return@mapNotNull SeenPostMapper.fromEntity(threadDescriptor, seenPostEntity)
          }
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