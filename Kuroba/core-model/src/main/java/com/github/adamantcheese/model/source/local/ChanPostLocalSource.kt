package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.entity.ChanTextSpanEntity
import com.github.adamantcheese.model.entity.ChanThreadEntity
import com.github.adamantcheese.model.mapper.*
import com.google.gson.Gson

class ChanPostLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger,
        private val gson: Gson
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag ChanPostLocalSource"
    private val chanBoardDao = database.chanBoardDao()
    private val chanThreadDao = database.chanThreadDao()
    private val chanPostDao = database.chanPostDao()
    private val chanPostImageDao = database.chanPostImageDao()
    private val chanPostHttpIconDao = database.chanPostHttpIconDao()
    private val chanTextSpanDao = database.chanTextSpanDao()

    suspend fun insertOriginalPost(chanPost: ChanPost): Long {
        ensureInTransaction()

        return insertManyOriginalPosts(listOf(chanPost)).first()
    }

    suspend fun insertManyOriginalPosts(chanOriginalPostList: List<ChanPost>): List<Long> {
        ensureInTransaction()

        if (chanOriginalPostList.isEmpty()) {
            return emptyList()
        }

        val first = chanOriginalPostList.first()

        val chanBoardEntity = chanBoardDao.insert(
                first.postDescriptor.descriptor.siteName(),
                first.postDescriptor.descriptor.boardCode()
        )

        // TODO(archives): BATCHING!!!!!!! Too many separate insert queries!
        return chanOriginalPostList.map { chanPost ->
            val threadNo = chanPost.postDescriptor.getThreadNo()

            val chanThreadId = chanThreadDao.insertOrUpdate(
                    chanBoardEntity.boardId,
                    threadNo,
                    ChanThreadMapper.toEntity(threadNo, chanBoardEntity.boardId, chanPost)
            )

            return@map insertPostFullyInternal(chanThreadId, chanPost)
        }
    }

    suspend fun insertPosts(chanThreadId: Long, chanPostList: List<ChanPost>) {
        ensureInTransaction()

        // TODO(archives): BATCHING!!!!!!! Too many separate insert queries!
        chanPostList.forEach { chanPost ->
            insertPostFullyInternal(chanThreadId, chanPost)
        }
    }

    private suspend fun insertPostFullyInternal(chanThreadId: Long, chanPost: ChanPost): Long {
        ensureInTransaction()

        val chanPostEntityId = chanPostDao.insertOrUpdate(
                chanThreadId,
                chanPost.postDescriptor.postNo,
                ChanPostMapper.toEntity(chanThreadId, chanPost)
        )

        insertPostSpannables(chanPostEntityId, chanPost)

        chanPost.postImages.forEach { postImage ->
            chanPostImageDao.insertOrUpdate(
                    ChanPostImageMapper.toEntity(chanPostEntityId, postImage)
            )
        }

        chanPost.postIcons.forEach { postIcon ->
            chanPostHttpIconDao.insertOrUpdate(
                    ChanPostHttpIconMapper.toEntity(chanPostEntityId, postIcon)
            )
        }

        return chanPostEntityId
    }

    private suspend fun insertPostSpannables(chanPostEntityId: Long, chanPost: ChanPost) {
        ensureInTransaction()
        require(chanPostEntityId > 0) { "Bad chanPostEntityId: ${chanPostEntityId}" }

        val postCommentWithSpansJson = TextSpanMapper.toEntity(
                gson,
                chanPostEntityId,
                chanPost.postComment,
                ChanTextSpanEntity.TextType.PostComment
        )

        if (postCommentWithSpansJson != null) {
            chanTextSpanDao.insertOrUpdate(chanPostEntityId, postCommentWithSpansJson)
        }

        val subjectWithSpansJson = TextSpanMapper.toEntity(
                gson,
                chanPostEntityId,
                chanPost.subject,
                ChanTextSpanEntity.TextType.Subject
        )

        if (subjectWithSpansJson != null) {
            chanTextSpanDao.insertOrUpdate(chanPostEntityId, subjectWithSpansJson)
        }

        val tripcodeWithSpansJson = TextSpanMapper.toEntity(
                gson,
                chanPostEntityId,
                chanPost.tripcode,
                ChanTextSpanEntity.TextType.Tripcode
        )

        if (tripcodeWithSpansJson != null) {
            chanTextSpanDao.insertOrUpdate(chanPostEntityId, tripcodeWithSpansJson)
        }
    }

    suspend fun getCatalogOriginalPosts(
            descriptor: ChanDescriptor.CatalogDescriptor,
            originalPostNoList: List<Long>
    ): List<ChanPost> {
        ensureInTransaction()

        if (originalPostNoList.isEmpty()) {
            return emptyList()
        }

        // Load catalog descriptor's board
        val chanBoardEntity = chanBoardDao.select(descriptor.siteName(), descriptor.boardCode())
                ?: return emptyList()

        // Load catalog descriptor's threads
        val chanThreadEntityList = originalPostNoList
                .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chunk ->
                    chanThreadDao.selectManyByThreadNoList(chanBoardEntity.boardId, chunk)
                }

        if (chanThreadEntityList.isEmpty()) {
            return emptyList()
        }

        // Load threads' original posts
        val chanPostEntityMap = chanThreadEntityList
                .map { chanThreadEntity -> chanThreadEntity.threadId }
                .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chunk -> chanPostDao.selectManyOriginalPostsByThreadIdList(chunk) }
                .associateBy { chanPostEntity -> chanPostEntity.ownerThreadId }

        if (chanPostEntityMap.isEmpty()) {
            return emptyList()
        }

        val postIdList = chanPostEntityMap.values.map { chanPostEntity ->
            chanPostEntity.postId
        }

        // Load posts' comments/subjects/tripcodes and other Spannables
        val textSpansGroupedByPostId = postIdList
                .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chunk -> chanTextSpanDao.selectManyByOwnerPostIdList(chunk) }
                .groupBy { chanTextSpanEntity -> chanTextSpanEntity.ownerPostId }

        val posts = chanThreadEntityList.map { chanThreadEntity ->
            val chanPostEntity = checkNotNull(chanPostEntityMap[chanThreadEntity.threadId]) {
                "Couldn't find post info for original post with id (${chanThreadEntity.threadId})"
            }

            val postTextSnapEntityList = textSpansGroupedByPostId[chanPostEntity.postId]

            return@map ChanThreadMapper.fromEntity(
                    gson,
                    descriptor,
                    chanThreadEntity,
                    chanPostEntity,
                    postTextSnapEntityList
            )
        }


        return getPostsAdditionalData(postIdList, posts)
    }

    suspend fun getThreadPostNoList(descriptor: ChanDescriptor.ThreadDescriptor): List<Long> {
        ensureInTransaction()

        val chanThreadEntity = getThreadByThreadDescriptor(descriptor)
                ?: return emptyList()

        return chanPostDao.selectManyPostNoByThreadId(chanThreadEntity.threadId)
    }

    suspend fun getThreadPosts(
            descriptor: ChanDescriptor.ThreadDescriptor,
            postNoList: List<Long>
    ): List<ChanPost> {
        ensureInTransaction()

        // Load descriptor's thread
        val chanThreadEntity = getThreadByThreadDescriptor(descriptor)
                ?: return emptyList()

        // Load thread's posts
        val chanPostEntityList = postNoList
                .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chunk ->
                    return@flatMap chanPostDao.selectManyByThreadIdAndPostNoList(
                            chanThreadEntity.threadId,
                            chunk
                    )
                }

        if (chanPostEntityList.isEmpty()) {
            return emptyList()
        }

        val postIdList = chanPostEntityList.map { it.postId }

        // Load posts' comments/subjects/tripcodes and other Spannables
        val textSpansGroupedByPostId = postIdList
                .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chunk -> chanTextSpanDao.selectManyByOwnerPostIdList(chunk) }
                .groupBy { chanTextSpanEntity -> chanTextSpanEntity.ownerPostId }

        val posts = chanPostEntityList
                .mapNotNull { chanPostEntity ->
                    val postTextSnapEntityList = textSpansGroupedByPostId[chanPostEntity.postId]

                    return@mapNotNull ChanPostMapper.fromEntity(
                            gson,
                            descriptor,
                            null,
                            chanPostEntity,
                            postTextSnapEntityList
                    )
                }

        return getPostsAdditionalData(postIdList, posts)
    }

    private suspend fun getPostsAdditionalData(
            postIdList: List<Long>,
            posts: List<ChanPost>
    ): List<ChanPost> {
        ensureInTransaction()

        // Load posts' images
        val postImageByPostIdMap = postIdList
                .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chunk -> chanPostImageDao.selectByOwnerPostIdList(chunk) }
                .groupBy { chanPostImageEntity -> chanPostImageEntity.ownerPostId }

        // Load posts' icons
        val postIconsByPostIdMap = postIdList
                .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chunk -> chanPostHttpIconDao.selectByOwnerPostIdList(chunk) }
                .groupBy { chanPostHttpIconEntity -> chanPostHttpIconEntity.ownerPostId }

        posts.forEach { post ->
            val postImages = postImageByPostIdMap[post.databasePostId]
            if (postImages != null && postImages.isNotEmpty()) {
                postImages.forEach { postImage ->
                    post.postImages.add(ChanPostImageMapper.fromEntity(postImage))
                }
            }

            val postIcons = postIconsByPostIdMap[post.databasePostId]
            if (postIcons != null && postIcons.isNotEmpty()) {
                postIcons.forEach { postIcon ->
                    post.postIcons.add(ChanPostHttpIconMapper.fromEntity(postIcon))
                }
            }
        }

        return posts
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