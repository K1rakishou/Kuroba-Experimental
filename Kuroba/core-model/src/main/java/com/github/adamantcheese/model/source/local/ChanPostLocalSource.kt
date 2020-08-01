package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.common.flatMapIndexed
import com.github.adamantcheese.common.mutableMapWithCap
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.entity.chan.ChanPostIdEntity
import com.github.adamantcheese.model.entity.chan.ChanPostReplyEntity
import com.github.adamantcheese.model.entity.chan.ChanTextSpanEntity
import com.github.adamantcheese.model.entity.chan.ChanThreadEntity
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
  private val chanPostReplyDao = database.chanPostReplyDao()

  suspend fun insertEmptyThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): Long? {
    ensureInTransaction()

    val chanBoardEntity = chanBoardDao.select(
      threadDescriptor.siteName(),
      threadDescriptor.boardCode()
    )

    if (chanBoardEntity == null) {
      logger.logError(TAG, "Cannot insert empty thread (site ${threadDescriptor.siteName()} or " +
        "board ${threadDescriptor.boardCode()} does not exist)")
      return null
    }

    return chanThreadDao.insertDefaultOrIgnore(
      chanBoardEntity.boardId,
      threadDescriptor.threadNo
    )
  }

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

    val chanThreadIds = chanOriginalPostList.map { chanPost ->
      val threadNo = chanPost.postDescriptor.getThreadNo()

      return@map chanThreadDao.insertOrUpdate(
        chanBoardEntity.boardId,
        threadNo,
        ChanThreadMapper.toEntity(threadNo, chanBoardEntity.boardId, chanPost)
      )
    }

    val chanPostIdEntities = chanThreadIds.mapIndexed { index, chanThreadId ->
      val chanOriginalPost = chanOriginalPostList[index]

      return@mapIndexed ChanPostIdEntity(
        postId = 0L,
        ownerArchiveId = chanOriginalPost.archiveId,
        ownerThreadId = chanThreadId,
        postNo = chanOriginalPost.postDescriptor.postNo,
        postSubNo = chanOriginalPost.postDescriptor.postSubNo
      )
    }

    insertPostsInternal(chanPostIdEntities, chanOriginalPostList)
    return chanThreadIds
  }

  suspend fun insertPosts(chanThreadId: Long, chanPostList: List<ChanPost>) {
    ensureInTransaction()

    val originalPost = chanPostList.firstOrNull { chanPost -> chanPost.isOp }
    if (originalPost != null) {
      val chanBoardEntity = chanBoardDao.insert(
        originalPost.postDescriptor.descriptor.siteName(),
        originalPost.postDescriptor.descriptor.boardCode()
      )

      val threadNo = originalPost.postDescriptor.getThreadNo()

      chanThreadDao.insertOrUpdate(
        chanBoardEntity.boardId,
        threadNo,
        ChanThreadMapper.toEntity(threadNo, chanBoardEntity.boardId, originalPost)
      )
    }

    val chanPostIdEntities = chanPostList.map { chanPost ->
      ChanPostIdEntity(
        postId = 0L,
        ownerArchiveId = chanPost.archiveId,
        ownerThreadId = chanThreadId,
        postNo = chanPost.postDescriptor.postNo,
        postSubNo = chanPost.postDescriptor.postSubNo
      )
    }

    insertPostsInternal(chanPostIdEntities, chanPostList)
  }

  private suspend fun insertPostsInternal(
    chanPostIdEntities: List<ChanPostIdEntity>,
    chanPostList: List<ChanPost>
  ) {
    chanPostDao.insertOrReplaceManyIds(chanPostIdEntities).forEachIndexed { index, postDatabaseId ->
      chanPostIdEntities[index].postId = postDatabaseId
    }

    chanPostDao.insertOrReplaceManyPosts(
      chanPostIdEntities.mapIndexed { index, chanPostIdEntity ->
        ChanPostMapper.toEntity(chanPostIdEntity.postId, chanPostList[index])
      }
    )

    insertPostSpannables(chanPostIdEntities, chanPostList)

    chanPostImageDao.insertMany(
      chanPostIdEntities.flatMapIndexed { index, chanPostIdEntity ->
        val chanPost = chanPostList[index]

        return@flatMapIndexed chanPost.postImages.map { postImage ->
          ChanPostImageMapper.toEntity(chanPostIdEntity.postId, postImage)
        }
      }
    )

    chanPostHttpIconDao.insertMany(
      chanPostIdEntities.flatMapIndexed { index, chanPostIdEntity ->
        val chanPost = chanPostList[index]

        return@flatMapIndexed chanPost.postIcons.map { postIcon ->
          ChanPostHttpIconMapper.toEntity(chanPostIdEntity.postId, postIcon)
        }
      }
    )

    chanPostReplyDao.insertManyOrIgnore(
      chanPostIdEntities.flatMapIndexed { index, chanPostIdEntity ->
        val chanPost = chanPostList[index]

        return@flatMapIndexed chanPost.repliesTo.map { replyTo ->
          ChanPostReplyEntity(
            postReplyId = 0L,
            ownerPostId = chanPostIdEntity.postId,
            replyNo = replyTo,
            replyType = ChanPostReplyEntity.ReplyType.ReplyTo
          )
        }.toList()
      }
    )
  }

  private suspend fun insertPostSpannables(
    chanPostEntityIdList: List<ChanPostIdEntity>,
    chanPostList: List<ChanPost>
  ) {
    ensureInTransaction()

    val postCommentWithSpansJsonList = chanPostEntityIdList.mapIndexedNotNull { index, chanPostEntityId ->
      val chanPost = chanPostList[index]

      return@mapIndexedNotNull TextSpanMapper.toEntity(
        gson,
        chanPostEntityId.postId,
        chanPost.postComment,
        ChanTextSpanEntity.TextType.PostComment
      )
    }

    if (postCommentWithSpansJsonList.isNotEmpty()) {
      chanTextSpanDao.insertMany(postCommentWithSpansJsonList)
    }

    val subjectWithSpansJsonList = chanPostEntityIdList.mapIndexedNotNull { index, chanPostEntityId ->
      val chanPost = chanPostList[index]

      return@mapIndexedNotNull TextSpanMapper.toEntity(
        gson,
        chanPostEntityId.postId,
        chanPost.subject,
        ChanTextSpanEntity.TextType.Subject
      )
    }

    if (subjectWithSpansJsonList.isNotEmpty()) {
      chanTextSpanDao.insertMany(subjectWithSpansJsonList)
    }

    val tripcodeWithSpansJsonList = chanPostEntityIdList.mapIndexedNotNull { index, chanPostEntityId ->
      val chanPost = chanPostList[index]

      return@mapIndexedNotNull TextSpanMapper.toEntity(
        gson,
        chanPostEntityId.postId,
        chanPost.tripcode,
        ChanTextSpanEntity.TextType.Tripcode
      )
    }

    if (tripcodeWithSpansJsonList.isNotEmpty()) {
      chanTextSpanDao.insertMany(tripcodeWithSpansJsonList)
    }
  }

  suspend fun getCatalogOriginalPosts(
    archiveIds: Set<Long>,
    threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>
  ): Map<ChanDescriptor.ThreadDescriptor, ChanPost> {
    ensureInTransaction()

    val catalogDescriptors = mutableMapWithCap<ChanDescriptor.CatalogDescriptor, MutableSet<Long>>(threadDescriptors)

    threadDescriptors.forEach { threadDescriptor ->
      val catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(
        threadDescriptor.siteName(),
        threadDescriptor.boardCode()
      )

      if (!catalogDescriptors.containsKey(catalogDescriptor)) {
        catalogDescriptors[catalogDescriptor] = mutableSetOf()
      }

      catalogDescriptors[catalogDescriptor]!!.add(threadDescriptor.threadNo)
    }

    val resultMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ChanPost>(catalogDescriptors.size / 2)

    catalogDescriptors.forEach { (catalogDescriptor, postNoSet) ->
      // Load catalog descriptor's board
      val chanBoardEntity = chanBoardDao.select(catalogDescriptor.siteName(), catalogDescriptor.boardCode())
        ?: return@forEach

      // Load catalog descriptor's latest threads
      val chanThreadEntityList = chanThreadDao.selectManyByThreadNos(
        chanBoardEntity.boardId,
        postNoSet
      )

      val posts = loadOriginalPostsInternal(chanThreadEntityList, catalogDescriptor, archiveIds)

      posts.forEach { chanPost ->
        val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
          catalogDescriptor.siteName(),
          catalogDescriptor.boardCode(),
          chanPost.postDescriptor.postNo
        )

        resultMap[threadDescriptor] = chanPost
      }
    }

    return resultMap
  }

  suspend fun getCatalogOriginalPosts(
    descriptor: ChanDescriptor.CatalogDescriptor,
    archiveIds: Set<Long>,
    count: Int
  ): List<ChanPost> {
    ensureInTransaction()
    require(count > 0) { "Bad count param: $count" }

    // Load catalog descriptor's board
    val chanBoardEntity = chanBoardDao.select(descriptor.siteName(), descriptor.boardCode())
      ?: return emptyList()

    // Load catalog descriptor's latest threads
    val chanThreadEntityList = chanThreadDao.selectLatestThreads(
      chanBoardEntity.boardId,
      count
    )

    if (chanThreadEntityList.isEmpty()) {
      return emptyList()
    }

    return loadOriginalPostsInternal(chanThreadEntityList, descriptor, archiveIds)
  }

  suspend fun getCatalogOriginalPosts(
    descriptor: ChanDescriptor.CatalogDescriptor,
    archiveIds: Set<Long>,
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
        chanThreadDao.selectManyByThreadNos(chanBoardEntity.boardId, chunk)
      }

    if (chanThreadEntityList.isEmpty()) {
      return emptyList()
    }

    return loadOriginalPostsInternal(chanThreadEntityList, descriptor, archiveIds)
  }

  private suspend fun loadOriginalPostsInternal(
    chanThreadEntityList: List<ChanThreadEntity>,
    descriptor: ChanDescriptor.CatalogDescriptor,
    archiveIds: Set<Long>
  ): List<ChanPost> {
    // Load threads' original posts
    val chanPostFullMap = chanThreadEntityList
      .map { chanThreadEntity -> chanThreadEntity.threadId }
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> chanPostDao.selectManyOriginalPostsByThreadIdList(chunk, archiveIds) }
      .associateBy { chanPostEntity -> chanPostEntity.chanPostIdEntity.ownerThreadId }

    if (chanPostFullMap.isEmpty()) {
      return emptyList()
    }

    val postIdList = chanPostFullMap.values.map { chanPostFull ->
      chanPostFull.chanPostIdEntity.postId
    }

    // Load posts' comments/subjects/tripcodes and other Spannables
    val textSpansGroupedByPostId = postIdList
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> chanTextSpanDao.selectManyByOwnerPostIdList(chunk) }
      .groupBy { chanTextSpanEntity -> chanTextSpanEntity.ownerPostId }

    val posts = chanThreadEntityList.map { chanThreadEntity ->
      val chanPostEntity = checkNotNull(chanPostFullMap[chanThreadEntity.threadId]) {
        "Couldn't find post info for original post with id (${chanThreadEntity.threadId})"
      }

      val postTextSnapEntityList = textSpansGroupedByPostId[chanPostEntity.chanPostIdEntity.postId]

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

  suspend fun getThreadPosts(
    descriptor: ChanDescriptor.ThreadDescriptor,
    archiveIds: Set<Long>,
    postNoCollection: Collection<Long>
  ): List<ChanPost> {
    ensureInTransaction()

    // Load descriptor's thread
    val chanThreadEntity = getThreadByThreadDescriptor(descriptor)
      ?: return emptyList()

    val chanPostFullList = chanPostDao.selectMany(
      chanThreadEntity.threadId,
      archiveIds,
      postNoCollection
    )

    if (chanPostFullList.isEmpty()) {
      return emptyList()
    }

    val postIdList = chanPostFullList.map { it.chanPostIdEntity.postId }

    // Load posts' comments/subjects/tripcodes and other Spannables
    val textSpansGroupedByPostId = postIdList
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> chanTextSpanDao.selectManyByOwnerPostIdList(chunk) }
      .groupBy { chanTextSpanEntity -> chanTextSpanEntity.ownerPostId }

    val posts = chanPostFullList
      .mapNotNull { chanPostFull ->
        val postTextSnapEntityList =
          textSpansGroupedByPostId[chanPostFull.chanPostIdEntity.postId]

        return@mapNotNull ChanPostMapper.fromEntity(
          gson,
          descriptor,
          chanThreadEntity,
          chanPostFull.chanPostIdEntity,
          chanPostFull.chanPostEntity,
          postTextSnapEntityList
        )
      }

    return getPostsAdditionalData(postIdList, posts)
  }

  suspend fun getThreadPosts(
    descriptor: ChanDescriptor.ThreadDescriptor,
    archiveIds: Set<Long>,
    postsNoToIgnore: Set<Long>,
    maxCount: Int
  ): List<ChanPost> {
    ensureInTransaction()
    require(maxCount > 0) { "Bad maxCount: $maxCount" }

    // Load descriptor's thread
    val chanThreadEntity = getThreadByThreadDescriptor(descriptor)
      ?: return emptyList()

    val originalPost = chanPostDao.selectOriginalPost(chanThreadEntity.threadId, archiveIds)
      ?: return emptyList()

    // Load thread's posts. We need to sort them because we sort them right in the SQL query in
    // order to trim everything after [maxCount]
    val chanPostFullList = if (postsNoToIgnore.isNotEmpty()) {
      postsNoToIgnore
        .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
        .flatMap { chunk ->
          return@flatMap chanPostDao.selectAllByThreadId(
            chanThreadEntity.threadId,
            archiveIds,
            chunk,
            maxCount
          )
        }.toMutableList()
    } else {
      chanPostDao.selectAllByThreadId(
        chanThreadEntity.threadId,
        archiveIds,
        emptyList(),
        maxCount
      ).toMutableList()
    }

    if (!postsNoToIgnore.contains(originalPost.chanPostIdEntity.postNo)) {
      // Insert the original post at the beginning of the list but only if we don't already
      // have it in the postsNoToIgnore
      chanPostFullList.add(0, originalPost)
    }

    if (chanPostFullList.isEmpty()) {
      return emptyList()
    }

    val postIdList = chanPostFullList.map { it.chanPostIdEntity.postId }

    // Load posts' comments/subjects/tripcodes and other Spannables
    val textSpansGroupedByPostId = postIdList
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> chanTextSpanDao.selectManyByOwnerPostIdList(chunk) }
      .groupBy { chanTextSpanEntity -> chanTextSpanEntity.ownerPostId }

    val posts = chanPostFullList
      .mapNotNull { chanPostFull ->
        val postTextSnapEntityList =
          textSpansGroupedByPostId[chanPostFull.chanPostIdEntity.postId]

        return@mapNotNull ChanPostMapper.fromEntity(
          gson,
          descriptor,
          chanThreadEntity,
          chanPostFull.chanPostIdEntity,
          chanPostFull.chanPostEntity,
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

    // Load posts' replies to other posts
    val postReplyToByPostIdMap = postIdList
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk ->
        return@flatMap chanPostReplyDao.selectByOwnerPostIdList(
          chunk,
          ChanPostReplyEntity.ReplyType.ReplyTo
        )
      }
      .groupBy { chanPostReplyEntity -> chanPostReplyEntity.ownerPostId }

    posts.forEach { post ->
      val postImages = postImageByPostIdMap[post.chanPostId]
      if (postImages != null && postImages.isNotEmpty()) {
        postImages.forEach { postImage ->
          post.postImages.add(ChanPostImageMapper.fromEntity(postImage))
        }
      }

      val postIcons = postIconsByPostIdMap[post.chanPostId]
      if (postIcons != null && postIcons.isNotEmpty()) {
        postIcons.forEach { postIcon ->
          post.postIcons.add(ChanPostHttpIconMapper.fromEntity(postIcon))
        }
      }

      val replyToList = postReplyToByPostIdMap[post.chanPostId]
      if (replyToList != null && replyToList.isNotEmpty()) {
        post.repliesTo.addAll(replyToList.map { it.replyNo })
      }
    }

    return posts
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

    return chanThreadDao.select(
      chanBoardEntity.boardId,
      postDescriptor.getThreadNo()
    )?.threadId
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

    return chanThreadDao.select(chanBoardEntity.boardId, threadDescriptor.threadNo)
  }

  suspend fun countTotalAmountOfPosts(): Int {
    ensureInTransaction()

    return chanPostDao.count()
  }

  suspend fun deleteAll(): Int {
    ensureInTransaction()

    return chanPostDao.deleteAll()
  }

  suspend fun deleteThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    ensureInTransaction()

    val chanBoardEntity = chanBoardDao.select(
      threadDescriptor.siteName(),
      threadDescriptor.boardCode()
    )

    if (chanBoardEntity == null) {
      return
    }

    chanThreadDao.deleteThread(chanBoardEntity.boardId, threadDescriptor.threadNo)
  }

  suspend fun deleteOldPosts(toDeleteCount: Int): Int {
    ensureInTransaction()
    require(toDeleteCount > 0) { "Bad toDeleteCount: $toDeleteCount" }

    var deletedTotal = 0
    var offset = 0

    do {
      val threadBatch = chanThreadDao.selectThreadsWithPostsOtherThanOp(offset, THREADS_IN_BATCH)
      if (threadBatch.isEmpty()) {
        logger.log(TAG, "selectThreadsWithPostsOtherThanOp returned empty list")
        return deletedTotal
      }

      for (thread in threadBatch) {
        if (deletedTotal >= toDeleteCount) {
          logger.log(TAG, "Deleted enough posts (deletedTotal = $deletedTotal, " +
            "toDeleteCount = $toDeleteCount), exiting early")
          break
        }

        val deletedPosts = chanPostDao.deletePostsByThreadId(thread.threadId)
        deletedTotal += thread.postsCount

        logger.log(TAG, "Deleting posts in " +
          "(threadId=${thread.threadId}, threadNo=${thread.threadNo}, " +
          "lastModified=${thread.lastModified}) thread, deleted $deletedPosts posts," +
          "deletedTotal = $deletedTotal")
      }

      offset += threadBatch.size
    } while (deletedTotal < toDeleteCount)

    return deletedTotal
  }

  companion object {
    private const val THREADS_IN_BATCH = 128
  }
}