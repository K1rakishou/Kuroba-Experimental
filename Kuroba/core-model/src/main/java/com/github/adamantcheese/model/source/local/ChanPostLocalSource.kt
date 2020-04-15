package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPostUnparsed
import com.github.adamantcheese.model.entity.ChanThreadEntity
import com.github.adamantcheese.model.mapper.ChanPostHttpIconMapper
import com.github.adamantcheese.model.mapper.ChanPostImageMapper
import com.github.adamantcheese.model.mapper.ChanPostMapper
import com.github.adamantcheese.model.mapper.ChanThreadMapper

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

    suspend fun insertOriginalPost(chanPostUnparsed: ChanPostUnparsed): Long {
        ensureInTransaction()

        val chanBoardEntity = chanBoardDao.insert(
                chanPostUnparsed.postDescriptor.descriptor.siteName(),
                chanPostUnparsed.postDescriptor.descriptor.boardCode()
        )

        val threadNo = chanPostUnparsed.postDescriptor.getThreadNo()

        return chanThreadDao.insertOrUpdate(
                chanBoardEntity.boardId,
                threadNo,
                ChanThreadMapper.toEntity(threadNo, chanBoardEntity.boardId, chanPostUnparsed)
        )
    }

    suspend fun insertManyOriginalPosts(chanOriginalPostUnparsedList: List<ChanPostUnparsed>) {
        ensureInTransaction()

        if (chanOriginalPostUnparsedList.isEmpty()) {
            return
        }

        val first = chanOriginalPostUnparsedList.first()

        val chanBoardEntity = chanBoardDao.insert(
                first.postDescriptor.descriptor.siteName(),
                first.postDescriptor.descriptor.boardCode()
        )

        chanOriginalPostUnparsedList.forEach { chanPostUnparsed ->
            val threadNo = chanPostUnparsed.postDescriptor.getThreadNo()

            val chanThreadId = chanThreadDao.insertOrUpdate(
                    chanBoardEntity.boardId,
                    threadNo,
                    ChanThreadMapper.toEntity(threadNo, chanBoardEntity.boardId, chanPostUnparsed)
            )

            val chanPostEntityId = chanPostDao.insertOrUpdate(
                    chanThreadId,
                    chanPostUnparsed.postDescriptor.postNo,
                    ChanPostMapper.toEntity(chanThreadId, chanPostUnparsed)
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
    }

    suspend fun insertPosts(chanThreadEntityId: Long, chanPostUnparsedList: List<ChanPostUnparsed>) {
        ensureInTransaction()

        chanPostUnparsedList.forEach { chanPostUnparsed ->
            val chanPostEntityId = chanPostDao.insertOrUpdate(
                    chanThreadEntityId,
                    chanPostUnparsed.postDescriptor.postNo,
                    ChanPostMapper.toEntity(chanThreadEntityId, chanPostUnparsed)
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
    }

    suspend fun getCatalogOriginalPosts(
            descriptor: ChanDescriptor.CatalogDescriptor,
            originalPostNoList: List<Long>
    ): List<ChanPostUnparsed> {
        ensureInTransaction()

        if (originalPostNoList.isEmpty()) {
            return emptyList()
        }

        val chanBoardEntity = chanBoardDao.select(descriptor.siteName(), descriptor.boardCode())
                ?: return emptyList()

        val chanThreadEntityList = originalPostNoList
                .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chunk -> chanThreadDao.selectManyByThreadNoList(chanBoardEntity.boardId, chunk) }

        if (chanThreadEntityList.isEmpty()) {
            return emptyList()
        }

        val chanPostEntityMap = chanThreadEntityList
                .map { chanThreadEntity -> chanThreadEntity.threadId to chanThreadEntity.threadNo }
                // FIXME: Slow! We iterate catalog original posts one by one instead of a single
                //  query. But to fix this I will have to come up with a query to select by
                //  ids from two separate lists:
                //
                //  SELECT *
                //  FROM ...
                //  WHERE
                //      OWNER_THREAD_COLUMN_NAME IN (:ownerThreadIdList)
                //  AND
                //      POST_NO_COLUMN_NAME IN (:postNoList)
                //
                //  Which I have no idea how to do and whether would it even work or not
                .mapNotNull { (threadId, threadNo) -> chanPostDao.selectByThreadIdAndPostNo(threadId, threadNo) }
                .associateBy { chanPostEntity -> chanPostEntity.ownerThreadId }

        val unparsedPosts = chanThreadEntityList.map { chanThreadEntity ->
            val chanPostEntity = checkNotNull(chanPostEntityMap[chanThreadEntity.threadId]) {
                "Couldn't find post info for original post with id (${chanThreadEntity.threadId})"
            }

            return@map ChanThreadMapper.fromEntity(descriptor, chanThreadEntity, chanPostEntity)
        }

        val postIdList = unparsedPosts.map { it.databasePostId }

        val postImageByPostIdMap = postIdList
                .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chunk -> chanPostImageDao.selectByOwnerPostIdList(chunk) }
                .groupBy { chanPostImageEntity -> chanPostImageEntity.ownerPostId }

        val postIconsByPostIdMap = postIdList
                .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chunk -> chanPostHttpIconDao.selectByOwnerPostIdList(chunk) }
                .groupBy { chanPostHttpIconEntity -> chanPostHttpIconEntity.ownerPostId }

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

    suspend fun getThreadPosts(
            descriptor: ChanDescriptor.ThreadDescriptor,
            postNoList: List<Long>
    ): List<ChanPostUnparsed> {
        ensureInTransaction()

        if (postNoList.isEmpty()) {
            return emptyList()
        }

        val chanThreadEntity = getThreadByThreadDescriptor(descriptor)
                ?: return emptyList()

        val chanPostEntityList = chanPostDao.selectAllPostsByThreadId(chanThreadEntity.threadId)
        if (chanPostEntityList.isEmpty()) {
            return emptyList()
        }

        val postIdList = chanPostEntityList.map { it.postId }

        val postImageByPostIdMap = postIdList
                .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chunk -> chanPostImageDao.selectByOwnerPostIdList(chunk) }
                .groupBy { chanPostImageEntity -> chanPostImageEntity.ownerPostId }

        val postIconsByPostIdMap = postIdList
                .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chunk -> chanPostHttpIconDao.selectByOwnerPostIdList(chunk) }
                .groupBy { chanPostHttpIconEntity -> chanPostHttpIconEntity.ownerPostId }

        val unparsedPosts = chanPostEntityList
                .mapNotNull { chanPostEntity -> ChanPostMapper.fromEntity(descriptor, null, chanPostEntity) }

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

    suspend fun getThreadIdByPostDescriptor(postDescriptor: PostDescriptor): Long? {
        ensureInTransaction()

        val chanBoardEntity = chanBoardDao.select(
                postDescriptor.descriptor.siteName(),
                postDescriptor.descriptor.boardCode()
        )

        if (chanBoardEntity == null) {
            return null
        }

        return chanThreadDao.select(chanBoardEntity.boardId, postDescriptor.getThreadNo())?.threadId
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