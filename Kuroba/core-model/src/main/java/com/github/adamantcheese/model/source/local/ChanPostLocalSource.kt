package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.post.ChanPostUnparsed
import com.github.adamantcheese.model.entity.ChanPostEntity
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
                chanPostUnparsed.postDescriptor.descriptor.siteName(),
                chanPostUnparsed.postDescriptor.descriptor.boardCode()
        )

        val chanThreadEntity = chanThreadDao.insert(
                chanBoardEntity.boardId,
                chanPostUnparsed.postDescriptor.getThreadNo()
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

    suspend fun getCatalogOriginalPosts(
            descriptor: ChanDescriptor.CatalogDescriptor,
            threadIds: List<Long>
    ): List<ChanPostUnparsed> {
        if (threadIds.isEmpty()) {
            return emptyList()
        }

        val chanPostEntityList = threadIds
                .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chunk -> chanPostDao.selectManyByPostNoList(chunk) }

        if (chanPostEntityList.isEmpty()) {
            return emptyList()
        }

        return loadChanPostEntitiesFully(chanPostEntityList, descriptor)
    }

    suspend fun getThreadPosts(
            descriptor: ChanDescriptor.ThreadDescriptor
    ): List<ChanPostUnparsed> {
        val chanThreadEntity = getThreadByThreadDescriptor(descriptor)
                ?: return emptyList()

        val chanPostEntityList = chanPostDao.selectAllPostsByThreadId(chanThreadEntity.threadId)
        if (chanPostEntityList.isEmpty()) {
            return emptyList()
        }

        return loadChanPostEntitiesFully(chanPostEntityList, descriptor)
    }

    private suspend fun loadChanPostEntitiesFully(
            chanPostEntityList: List<ChanPostEntity>,
            descriptor: ChanDescriptor
    ): List<ChanPostUnparsed> {
        val postIdList = chanPostEntityList.map { it.postId }

        val postImageByPostIdMap = postIdList.chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chunk -> chanPostImageDao.selectByOwnerPostIdList(chunk) }
                .groupBy { chanPostImageEntity -> chanPostImageEntity.ownerPostId }

        val postIconsByPostIdMap = postIdList.chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chunk -> chanPostHttpIconDao.selectByOwnerPostIdList(chunk) }
                .groupBy { chanPostHttpIconEntity -> chanPostHttpIconEntity.ownerPostId }

        val unparsedPosts = chanPostEntityList
                .mapNotNull { chanPostEntity -> ChanPostMapper.fromEntity(descriptor, chanPostEntity) }

        unparsedPosts.forEach { unparsedPost ->
            val postImages = postImageByPostIdMap[unparsedPost.databasePostId]
            if (postImages != null && postImages.isNotEmpty()) {
                postImages.forEach { postImage ->
                    unparsedPost.postImages.add(ChanPostImageMapper.fromEntity(postImage))
                }
            }

            val postIcons = postIconsByPostIdMap[unparsedPost.databasePostId]
            if (postIcons != null && postIcons.isNotEmpty()) {
                postIcons.forEach { postIcon ->
                    unparsedPost.postIcons.add(ChanPostHttpIconMapper.fromEntity(postIcon))
                }
            }
        }

        return unparsedPosts
    }

    suspend fun containsPostBlocking(descriptor: ChanDescriptor, postNo: Long): Boolean {
        ensureInTransaction()

        when (descriptor) {
            is ChanDescriptor.ThreadDescriptor -> {
                val chanThreadEntity = getThreadByThreadDescriptor(descriptor)
                        ?: return false

                return chanPostDao.select(chanThreadEntity.threadId, postNo) != null
            }
            is ChanDescriptor.CatalogDescriptor -> {
                val chanBoardEntity = chanBoardDao.select(
                        descriptor.siteName(),
                        descriptor.boardCode()
                )

                if (chanBoardEntity == null) {
                    return false
                }

                return chanThreadDao.select(chanBoardEntity.boardId, postNo) != null
            }
        }
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