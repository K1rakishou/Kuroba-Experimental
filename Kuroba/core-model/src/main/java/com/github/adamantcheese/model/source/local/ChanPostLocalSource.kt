package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.post.ChanPostUnparsed
import com.github.adamantcheese.model.entity.ChanThreadEntity
import com.github.adamantcheese.model.mapper.ChanPostHttpIconMapper
import com.github.adamantcheese.model.mapper.ChanPostImageMapper
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
    private val chanPostImageDao = database.chanPostImageDao()
    private val chanPostHttpIconDao = database.chanPostHttpIconDao()

    suspend fun insert(chanPostUnparsed: ChanPostUnparsed) {
        ensureInTransaction()

        val chanBoardEntity = chanBoardDao.insert(
                chanPostUnparsed.postDescriptor.threadDescriptor.siteName(),
                chanPostUnparsed.postDescriptor.threadDescriptor.boardCode()
        )

        val chanThreadEntity = chanThreadDao.insert(
                chanBoardEntity.boardId,
                chanPostUnparsed.postDescriptor.threadDescriptor.opNo
        )

        val chanPostEntityId = chanPostDao.insertOrUpdate(
                chanThreadEntity.threadId,
                chanPostUnparsed.postDescriptor.postNo,
                ChanPostMapper.toEntity(chanThreadEntity.threadId, chanPostUnparsed)
        )

        chanPostUnparsed.postImages.forEach { postImage ->
            chanPostImageDao.insertOrUpdate(
                    ChanPostImageMapper.toEntity(chanPostEntityId, postImage)
            )
        }

        chanPostUnparsed.postIcons.forEach { postIcon ->
            chanPostHttpIconDao.insertOrUpdate(
                    ChanPostHttpIconMapper.toEntity(chanPostEntityId, postIcon)
            )
        }
    }

    suspend fun containsPostBlocking(descriptor: ChanDescriptor, postNo: Long): Boolean {
        ensureInTransaction()

        return when (descriptor) {
            is ChanDescriptor.ThreadDescriptor -> threadContainsPost(descriptor, postNo)
            is ChanDescriptor.CatalogDescriptor -> catalogContainsThread(descriptor, postNo)
        }
    }

    suspend fun catalogContainsThread(
            catalogDescriptor: ChanDescriptor.CatalogDescriptor,
            threadNo: Long
    ): Boolean {
        ensureInTransaction()

        val chanBoardEntity = chanBoardDao.select(
                catalogDescriptor.siteName(),
                catalogDescriptor.boardCode()
        )

        if (chanBoardEntity == null) {
            return false
        }

        return chanThreadDao.select(chanBoardEntity.boardId, threadNo) != null
    }

    suspend fun threadContainsPost(
            threadDescriptor: ChanDescriptor.ThreadDescriptor,
            postNo: Long
    ): Boolean {
        ensureInTransaction()

        val chanThreadEntity = getThreadByThreadDescriptor(threadDescriptor)
                ?: return false

        return chanPostDao.select(chanThreadEntity.threadId, postNo) != null
    }

    private suspend fun getThreadByThreadDescriptor(
            threadDescriptor: ChanDescriptor.ThreadDescriptor
    ): ChanThreadEntity? {
        ensureInTransaction()

        val chanBoardEntity = chanBoardDao.select(
                threadDescriptor.siteName(),
                threadDescriptor.boardCode()
        )

        if (chanBoardEntity == null) {
            return null
        }

        return chanThreadDao.select(chanBoardEntity.boardId, threadDescriptor.opNo)
    }

    open suspend fun deleteAll(): Int {
        ensureInTransaction()

        return chanPostDao.deleteAll()
    }

}