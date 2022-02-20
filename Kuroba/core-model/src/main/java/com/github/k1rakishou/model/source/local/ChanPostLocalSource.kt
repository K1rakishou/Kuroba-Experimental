package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.common.flatMapIndexed
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.parcelable_spannable_string.ParcelableSpannableStringMapper
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.entity.chan.post.ChanPostFull
import com.github.k1rakishou.model.entity.chan.post.ChanPostHttpIconEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostIdEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostImageEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostReplyEntity
import com.github.k1rakishou.model.entity.chan.post.ChanTextSpanEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity
import com.github.k1rakishou.model.entity.view.ChanThreadsWithPosts
import com.github.k1rakishou.model.entity.view.OldChanPostThread
import com.github.k1rakishou.model.mapper.ChanPostEntityMapper
import com.github.k1rakishou.model.mapper.ChanPostHttpIconMapper
import com.github.k1rakishou.model.mapper.ChanPostImageMapper
import com.github.k1rakishou.model.mapper.ChanThreadMapper
import com.github.k1rakishou.model.mapper.TextSpanMapper
import java.util.concurrent.TimeUnit

class ChanPostLocalSource(
  database: KurobaDatabase
) : AbstractLocalSource(database) {
  private val TAG = "ChanPostLocalSource"
  private val chanBoardDao = database.chanBoardDao()
  private val chanThreadDao = database.chanThreadDao()
  private val chanPostDao = database.chanPostDao()
  private val chanPostImageDao = database.chanPostImageDao()
  private val chanPostHttpIconDao = database.chanPostHttpIconDao()
  private val chanTextSpanDao = database.chanTextSpanDao()
  private val chanPostReplyDao = database.chanPostReplyDao()

  suspend fun insertEmptyThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): Long? {
    ensureInTransaction()

    val chanBoardEntity = chanBoardDao.selectBoardId(
      threadDescriptor.siteName(),
      threadDescriptor.boardCode()
    )

    if (chanBoardEntity == null) {
      Logger.e(TAG, "Cannot insert empty thread (site ${threadDescriptor.siteName()} or " +
        "board ${threadDescriptor.boardCode()} does not exist)")
      return null
    }

    return chanThreadDao.insertDefaultOrIgnore(
      chanBoardEntity.boardId,
      threadDescriptor.threadNo
    )
  }

  suspend fun insertManyOriginalPosts(chanOriginalPostList: List<ChanOriginalPost>): List<Long> {
    ensureInTransaction()

    if (chanOriginalPostList.isEmpty()) {
      return emptyList()
    }

    val first = chanOriginalPostList.first()

    val chanBoardEntity = chanBoardDao.insertBoardId(
      first.postDescriptor.descriptor.siteName(),
      first.postDescriptor.descriptor.boardCode()
    )

    val chanThreadIds = chanOriginalPostList.map { chanPost ->
      val threadNo = chanPost.postDescriptor.getThreadNo()
      val chanThreadEntity = ChanThreadMapper.toEntity(threadNo, chanBoardEntity.boardId, chanPost)

      val chanThreadId = chanThreadDao.insertOrUpdate(
        chanBoardEntity.boardId,
        threadNo,
        chanThreadEntity
      )

      check(chanThreadId >= 0) {
        "insertManyOriginalPosts() Failed to insertOrUpdate thread $threadNo. " +
          "chanThreadEntity=$chanThreadEntity, chanPost=$chanPost, chanBoardEntity=$chanBoardEntity"
      }
      return@map chanThreadId
    }

    val chanPostIdEntities = chanThreadIds.mapIndexed { index, chanThreadId ->
      val chanOriginalPost = chanOriginalPostList[index]

      return@mapIndexed ChanPostIdEntity(
        postId = 0L,
        ownerThreadId = chanThreadId,
        postNo = chanOriginalPost.postDescriptor.postNo,
        postSubNo = chanOriginalPost.postDescriptor.postSubNo
      )
    }

    insertPostsInternal(chanPostIdEntities, chanOriginalPostList)

    return chanThreadIds
  }

  suspend fun insertPosts(chanPostList: List<ChanPost>) {
    ensureInTransaction()

    if (chanPostList.isEmpty()) {
      return
    }

    val originalPost = chanPostList
      .firstOrNull { chanPost -> chanPost is ChanOriginalPost }
      as? ChanOriginalPost
      ?: return

    val threadNo = originalPost.postDescriptor.getThreadNo()

    val chanBoardEntity = chanBoardDao.insertBoardId(
      originalPost.postDescriptor.descriptor.siteName(),
      originalPost.postDescriptor.descriptor.boardCode()
    )

    val chanThreadEntity = ChanThreadMapper.toEntity(threadNo, chanBoardEntity.boardId, originalPost)

    val chanThreadId = chanThreadDao.insertOrUpdate(
      chanBoardEntity.boardId,
      threadNo,
      chanThreadEntity
    )

    check(chanThreadId >= 0) {
      "insertPosts() Failed to insertOrUpdate thread $threadNo. chanThreadEntity=$chanThreadEntity, " +
        "originalPost=$originalPost, chanBoardEntity=$chanBoardEntity"
    }

    val chanPostIdEntities = chanPostList.map { chanPost ->
      ChanPostIdEntity(
        postId = 0L,
        ownerThreadId = chanThreadId,
        postNo = chanPost.postDescriptor.postNo,
        postSubNo = chanPost.postDescriptor.postSubNo
      )
    }

    insertPostsInternal(chanPostIdEntities, chanPostList)
  }

  suspend fun insertThreadPosts(ownerThreadId: Long, chanPostList: List<ChanPost>) {
    ensureInTransaction()

    val chanPostIdEntities = chanPostList.map { chanPost ->
      ChanPostIdEntity(
        postId = 0L,
        ownerThreadId = ownerThreadId,
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
        ChanPostEntityMapper.toEntity(chanPostIdEntity.postId, chanPostList[index])
      }
    )

    insertPostSpannables(chanPostIdEntities, chanPostList)

    chanPostImageDao.insertMany(
      chanPostIdEntities.flatMapIndexed { index, chanPostIdEntity ->
        val chanPost = chanPostList[index]

        return@flatMapIndexed chanPost.postImages.mapNotNull { postImage ->
          if (postImage.isInlined) {
            // Skip inlined images
            return@mapNotNull null
          }

          return@mapNotNull ChanPostImageMapper.toEntity(chanPostIdEntity.postId, postImage)
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
            replyNo = replyTo.postNo,
            replySubNo = replyTo.postSubNo,
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

      val parcelableSpannableString = ParcelableSpannableStringMapper
        .toParcelableSpannableString(chanPost.postComment.originalComment())
        ?: return@mapIndexedNotNull null

      return@mapIndexedNotNull TextSpanMapper.toEntity(
        ownerPostId = chanPostEntityId.postId,
        parcelableSpannableString = parcelableSpannableString,
        originalUnparsedComment = chanPost.postComment.originalUnparsedComment,
        chanTextType = ChanTextSpanEntity.TextType.PostComment
      )
    }

    if (postCommentWithSpansJsonList.isNotEmpty()) {
      chanTextSpanDao.insertMany(postCommentWithSpansJsonList)
    }

    val subjectWithSpansJsonList = chanPostEntityIdList.mapIndexedNotNull { index, chanPostEntityId ->
      val chanPost = chanPostList[index]

      val parcelableSpannableString = ParcelableSpannableStringMapper
        .toParcelableSpannableString(chanPost.subject)
        ?: return@mapIndexedNotNull null

      return@mapIndexedNotNull TextSpanMapper.toEntity(
        ownerPostId = chanPostEntityId.postId,
        parcelableSpannableString = parcelableSpannableString,
        originalUnparsedComment = null,
        chanTextType = ChanTextSpanEntity.TextType.Subject
      )
    }

    if (subjectWithSpansJsonList.isNotEmpty()) {
      chanTextSpanDao.insertMany(subjectWithSpansJsonList)
    }

    val tripcodeWithSpansJsonList = chanPostEntityIdList.mapIndexedNotNull { index, chanPostEntityId ->
      val chanPost = chanPostList[index]

      val parcelableSpannableString = ParcelableSpannableStringMapper
        .toParcelableSpannableString(chanPost.tripcode)
        ?: return@mapIndexedNotNull null

      return@mapIndexedNotNull TextSpanMapper.toEntity(
        ownerPostId = chanPostEntityId.postId,
        parcelableSpannableString = parcelableSpannableString,
        originalUnparsedComment = null,
        chanTextType = ChanTextSpanEntity.TextType.Tripcode
      )
    }

    if (tripcodeWithSpansJsonList.isNotEmpty()) {
      chanTextSpanDao.insertMany(tripcodeWithSpansJsonList)
    }
  }

  suspend fun updateThreadState(threadDatabaseId: Long, deleted: Boolean?, archived: Boolean?, closed: Boolean?) {
    ensureInTransaction()

    if (deleted == null && archived == null && closed == null) {
      return
    }

    chanThreadDao.updateThreadState(
      threadId = threadDatabaseId,
      closed = closed,
      archived = archived
    )

    if (deleted != null) {
      val postId = chanPostDao.selectOriginalPost(threadDatabaseId)?.chanPostIdEntity?.postId
      if (postId != null) {
        chanPostDao.updateOriginalPostDeleted(postId, deleted)
      }
    }
  }

  suspend fun getCatalogOriginalPosts(
    threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>
  ): Map<ChanDescriptor.ThreadDescriptor, ChanOriginalPost> {
    ensureInTransaction()

    val catalogDescriptors =
      mutableMapWithCap<ChanDescriptor.CatalogDescriptor, MutableSet<Long>>(threadDescriptors)

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

    val resultMap =
      mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ChanOriginalPost>(catalogDescriptors.size / 2)

    catalogDescriptors.forEach { (catalogDescriptor, postNoSet) ->
      // Load catalog descriptor's board
      val chanBoardEntity = chanBoardDao.selectBoardId(
        catalogDescriptor.siteName(),
        catalogDescriptor.boardCode()
      ) ?: return@forEach

      // Load catalog descriptor's latest threads
      val chanThreadEntityList = chanThreadDao.selectManyByThreadNos(
        chanBoardEntity.boardId,
        postNoSet
      )

      val posts = loadOriginalPostsInternal(chanThreadEntityList, catalogDescriptor)

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
    count: Int
  ): List<ChanOriginalPost> {
    ensureInTransaction()
    require(count > 0) { "Bad count param: $count" }

    // Load catalog descriptor's board
    val chanBoardEntity = chanBoardDao.selectBoardId(descriptor.siteName(), descriptor.boardCode())
      ?: return emptyList()

    // Load catalog descriptor's latest threads
    val chanThreadEntityList = chanThreadDao.selectLatestThreads(
      chanBoardEntity.boardId,
      count
    )

    if (chanThreadEntityList.isEmpty()) {
      return emptyList()
    }

    return loadOriginalPostsInternal(chanThreadEntityList, descriptor)
  }

  suspend fun loadOriginalPostsInternal(
    chanThreadEntityList: List<ChanThreadEntity>,
    catalogDescriptor: ChanDescriptor.CatalogDescriptor
  ): List<ChanOriginalPost> {
    // Load threads' original posts
    val chanPostFullMap = chanThreadEntityList
      .map { chanThreadEntity -> chanThreadEntity.threadId }
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> chanPostDao.selectManyOriginalPostsByThreadIdList(chunk) }
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

    val postAdditionalData = getPostsAdditionalData(postIdList)

    return chanThreadEntityList.mapNotNull { chanThreadEntity ->
      val chanPostEntity = chanPostFullMap[chanThreadEntity.threadId]
        ?: return@mapNotNull null

      val postTextSnapEntityList = textSpansGroupedByPostId[chanPostEntity.chanPostIdEntity.postId]

      val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
        catalogDescriptor,
        chanPostEntity.chanPostIdEntity.postNo
      )

      return@mapNotNull ChanThreadMapper.fromEntity(
        threadDescriptor = threadDescriptor,
        chanThreadEntity = chanThreadEntity,
        chanPostFull = chanPostEntity,
        chanTextSpanEntityList = postTextSnapEntityList,
        postAdditionalData = postAdditionalData
      )
    }
  }

  suspend fun countThreadPosts(threadDatabaseId: Long): Int {
    ensureInTransaction()

    return chanPostDao.countThreadPosts(threadDatabaseId)
  }

  suspend fun getThreadPosts(descriptor: ChanDescriptor.ThreadDescriptor): List<ChanPost> {
    ensureInTransaction()

    return getThreadPosts(descriptor, emptyList())
  }

  suspend fun getThreadPosts(
    descriptor: ChanDescriptor.ThreadDescriptor,
    postDatabaseIds: Collection<Long>
  ): List<ChanPost> {
    ensureInTransaction()

    // Load descriptor's thread
    val chanThreadEntity = getThreadByThreadDescriptor(descriptor)
      ?: return emptyList()

    val originalPost = chanPostDao.selectOriginalPost(chanThreadEntity.threadId)
      ?: return emptyList()

    val threadPosts = if (postDatabaseIds.isEmpty()) {
      chanPostDao.selectAllByThreadIdExceptOp(chanThreadEntity.threadId)
    } else {
      postDatabaseIds
        .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
        .flatMap { chunk -> chanPostDao.selectManyByThreadIdExceptOp(chanThreadEntity.threadId, chunk) }
    }

    // Load thread's posts. We need to sort them because we sort them right in the SQL query in
    // order to trim everything after [maxCount]
    val chanPostFullList = mutableListOf<ChanPostFull>()

    // Insert the original post at the beginning of the list
    chanPostFullList.add(originalPost)
    chanPostFullList.addAll(threadPosts)

    if (chanPostFullList.isEmpty()) {
      return emptyList()
    }

    val postIdList = chanPostFullList.map { it.chanPostIdEntity.postId }

    // Load posts' comments/subjects/tripcodes and other Spannables
    val textSpansGroupedByPostId = postIdList
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> chanTextSpanDao.selectManyByOwnerPostIdList(chunk) }
      .groupBy { chanTextSpanEntity -> chanTextSpanEntity.ownerPostId }

    val postAdditionalData = getPostsAdditionalData(postIdList)

    return chanPostFullList
      .mapNotNull { chanPostFull ->
        val postTextSnapEntityList =
          textSpansGroupedByPostId[chanPostFull.chanPostIdEntity.postId]

        return@mapNotNull ChanPostEntityMapper.fromEntity(
          descriptor,
          chanThreadEntity,
          chanPostFull.chanPostIdEntity,
          chanPostFull.chanPostEntity,
          postTextSnapEntityList,
          postAdditionalData
        )
      }
  }

  private suspend fun getPostsAdditionalData(postIdList: List<Long>): PostAdditionalData {
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

    return PostAdditionalData(
      postImageByPostIdMap = postImageByPostIdMap,
      postIconsByPostIdMap = postIconsByPostIdMap,
      postReplyToByPostIdMap = postReplyToByPostIdMap
    )
  }

  suspend fun getThreadIdByPostDescriptor(postDescriptor: PostDescriptor): Long? {
    ensureInTransaction()

    val chanBoardEntity = chanBoardDao.selectBoardId(
      postDescriptor.descriptor.siteName(),
      postDescriptor.descriptor.boardCode()
    ) ?: return null

    return chanThreadDao.select(
      chanBoardEntity.boardId,
      postDescriptor.getThreadNo()
    )?.threadId
  }

  private suspend fun getThreadByThreadDescriptor(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): ChanThreadEntity? {
    ensureInTransaction()

    val chanBoardEntity = chanBoardDao.selectBoardId(
      threadDescriptor.siteName(),
      threadDescriptor.boardCode()
    ) ?: return null

    return chanThreadDao.select(chanBoardEntity.boardId, threadDescriptor.threadNo)
  }

  suspend fun getThreadOriginalPostsByDatabaseId(threadDatabaseIds: Collection<Long>): List<ChanOriginalPost> {
    ensureInTransaction()

    val chanThreadMap = threadDatabaseIds.chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> chanThreadDao.selectManyByThreadIdList(chunk) }
      .associateBy { chanThreadEntity -> chanThreadEntity.threadId }

    val boardsIdSet = chanThreadMap.map { (_, chanThreadEntity) -> chanThreadEntity.ownerBoardId }
      .toSet()

    val chanBoardMap = chanBoardDao.selectMany(boardsIdSet)
      .associateBy { chanBoardIdEntity -> chanBoardIdEntity.boardId }

    val chanPostFullList = threadDatabaseIds
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> chanPostDao.selectOriginalPosts(chunk) }

    val postIdList = chanPostFullList.map { it.chanPostIdEntity.postId }

    // Load posts' comments/subjects/tripcodes and other Spannables
    val textSpansGroupedByPostId = postIdList
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> chanTextSpanDao.selectManyByOwnerPostIdList(chunk) }
      .groupBy { chanTextSpanEntity -> chanTextSpanEntity.ownerPostId }

    val postAdditionalData = getPostsAdditionalData(postIdList)

    return chanPostFullList
      .mapNotNull { chanPostFull ->
        val postTextSnapEntityList = textSpansGroupedByPostId[chanPostFull.chanPostIdEntity.postId]
        val chanThreadEntity = chanThreadMap[chanPostFull.chanPostIdEntity.ownerThreadId]
          ?: return@mapNotNull null
        val chanBoardIdEntity = chanBoardMap[chanThreadEntity.ownerBoardId]
          ?: return@mapNotNull null

        val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
          chanBoardIdEntity.ownerSiteName,
          chanBoardIdEntity.boardCode,
          chanThreadEntity.threadNo
        )

        require(chanPostFull.chanPostEntity.isOp) { "Must be original post" }

        return@mapNotNull ChanPostEntityMapper.fromEntity(
          threadDescriptor,
          chanThreadEntity,
          chanPostFull.chanPostIdEntity,
          chanPostFull.chanPostEntity,
          postTextSnapEntityList,
          postAdditionalData
        ) as ChanOriginalPost
      }
  }

  suspend fun countTotalAmountOfPosts(): Int {
    ensureInTransaction()

    return chanPostDao.totalPostsCount()
  }

  suspend fun countTotalAmountOfThreads(): Int {
    ensureInTransaction()

    return chanThreadDao.totalThreadsCount()
  }

  suspend fun deleteAll(): Int {
    ensureInTransaction()

    return chanPostDao.deleteAll()
  }

  suspend fun deleteThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    ensureInTransaction()

    val chanBoardEntity = chanBoardDao.selectBoardId(
      threadDescriptor.siteName(),
      threadDescriptor.boardCode()
    ) ?: return

    val threadId = chanThreadDao.select(chanBoardEntity.boardId, threadDescriptor.threadNo)?.threadId
      ?: return

    chanThreadDao.deleteAllPostsInThreadExceptOriginalPost(threadId)
  }

  // TODO(KurobaEx): this is slow because we are deleting threads one by one
  suspend fun deleteCatalog(catalogThreadDescriptors: List<ChanDescriptor.ThreadDescriptor>) {
    ensureInTransaction()

    val boardDescriptors = catalogThreadDescriptors
      .map { threadDescriptor -> threadDescriptor.boardDescriptor }
      .toSet()

    val boardIdMap = mutableMapOf<BoardDescriptor, Long>()

    boardDescriptors.forEach { boardDescriptor ->
      if (boardIdMap.containsKey(boardDescriptor)) {
        return@forEach
      }

      val chanBoardEntity = chanBoardDao.selectBoardId(
        boardDescriptor.siteName(),
        boardDescriptor.boardCode
      )

      if (chanBoardEntity != null) {
        boardIdMap[boardDescriptor] = chanBoardEntity.boardId
      }
    }

    catalogThreadDescriptors.forEach { threadDescriptor ->
      val boardId = boardIdMap[threadDescriptor.boardDescriptor]
        ?: return@forEach

      val threadId = chanThreadDao.select(boardId, threadDescriptor.threadNo)?.threadId
        ?: return

      chanThreadDao.deleteAllPostsInThreadExceptOriginalPost(threadId)
    }
  }

  suspend fun deletePost(postDescriptor: PostDescriptor) {
    ensureInTransaction()

    val threadDescriptor = postDescriptor.threadDescriptor()

    val chanBoardEntity = chanBoardDao.selectBoardId(
      threadDescriptor.siteName(),
      threadDescriptor.boardCode()
    ) ?: return

    val chanThreadEntity = chanThreadDao.select(chanBoardEntity.boardId, threadDescriptor.threadNo)
      ?: return

    chanPostDao.deletePost(chanThreadEntity.threadId, postDescriptor.postNo, postDescriptor.postSubNo)
  }

  suspend fun deletePosts(postDescriptors: Collection<PostDescriptor>) {
    ensureInTransaction()

    if (postDescriptors.isEmpty()) {
      return
    }

    val postsGroupedByThreads = postDescriptors
      .groupBy { postDescriptor -> postDescriptor.threadDescriptor() }

    if (postsGroupedByThreads.isEmpty()) {
      return
    }

    postsGroupedByThreads.forEach { (threadDescriptor, postDescriptors) ->
      val chanBoardEntity = chanBoardDao.selectBoardId(
        threadDescriptor.siteName(),
        threadDescriptor.boardCode()
      ) ?: return

      val chanThreadEntity = chanThreadDao.select(chanBoardEntity.boardId, threadDescriptor.threadNo)
        ?: return

      // TODO(KurobaEx): this may be kinda slow since we are deleting posts one by one instead of
      //  by batches. But to delete them in batches I need to figure out how SQLite WHERE operator
      //  works with multiple IN operators (e.g. WHERE x IN (1,2,3) AND y IN ("1", "2", "3") ).
      postDescriptors.forEach { postDescriptor ->
        chanPostDao.deletePost(
          chanThreadEntity.threadId,
          postDescriptor.postNo,
          postDescriptor.postSubNo
        )
      }
    }
  }

  suspend fun deleteOldPosts(toDeleteCount: Int): DeleteResult {
    ensureInTransaction()
    require(toDeleteCount > 0) { "Bad toDeleteCount: $toDeleteCount" }

    var deletedTotal = 0
    var skippedTotal = 0
    var offset = 0
    val startTime = System.currentTimeMillis()

    do {
      if (System.currentTimeMillis() - startTime > TEN_SECONDS) {
        Logger.d(TAG, "deleteOldPosts() execution took more than ${TEN_SECONDS} millis, exiting early")
        break
      }

      val threadBatch = chanThreadDao.selectThreadsWithPostsOtherThanOp(offset, ENTITIES_IN_BATCH)
      if (threadBatch.isEmpty()) {
        Logger.d(TAG, "deleteOldPosts() selectThreadsWithPostsOtherThanOp returned empty list")
        return DeleteResult(deletedTotal, skippedTotal)
      }

      val chanThreads = mutableSetOf<ChanThreadsWithPosts>()

      for (thread in threadBatch) {
        if (thread.threadBookmarkId != null) {
          skippedTotal += thread.postsCount

          Logger.d(TAG, "deleteOldPosts() skipping bookmarked thread " +
            "(threadNo = ${thread.threadNo}, posts count = ${thread.postsCount})")
          continue
        }

        if (thread.threadDownloadId != null) {
          skippedTotal += thread.postsCount

          Logger.d(TAG, "deleteOldPosts() skipping downloading thread " +
            "(threadNo = ${thread.threadNo}, posts count = ${thread.postsCount})")
          continue
        }

        if (deletedTotal >= toDeleteCount) {
          Logger.d(TAG, "deleteOldPosts() Deleted enough posts " +
            "(posts count = ${thread.postsCount}), exiting early")
          break
        }

        chanThreads += thread
        deletedTotal += thread.postsCount
      }

      if (chanThreads.isNotEmpty()) {
        val threadIdSet = chanThreads
          .map { chanThreadsWithPosts -> chanThreadsWithPosts.threadId }
          .toSet()

        val totalPosts = chanThreads
          .sumBy { chanThreadsWithPosts -> chanThreadsWithPosts.postsCount }

        Logger.d(TAG, "deleteOldPosts() deleting a batch of ${threadIdSet.size} threads with ${totalPosts} posts")
        chanPostDao.deletePostsByThreadIds(threadIdSet)
      }

      offset += threadBatch.size
    } while (deletedTotal < toDeleteCount)

    return DeleteResult(deletedTotal, skippedTotal)
  }

  suspend fun deleteOldThreads(toDeleteCount: Int): DeleteResult {
    ensureInTransaction()
    require(toDeleteCount > 0) { "Bad toDeleteCount: $toDeleteCount" }

    var deletedTotal = 0
    var skippedTotal = 0
    var offset = 0
    val startTime = System.currentTimeMillis()

    do {
      if (System.currentTimeMillis() - startTime > TEN_SECONDS) {
        Logger.d(TAG, "deleteOldPosts() execution took more than ${TEN_SECONDS} millis, exiting early")
        break
      }

      val threadBatch = chanThreadDao.selectOldThreads(offset, ENTITIES_IN_BATCH)
      if (threadBatch.isEmpty()) {
        Logger.d(TAG, "deleteOldThreads() selectOldThreads returned empty list")
        return DeleteResult(deletedTotal, skippedTotal)
      }

      val chanThreads = mutableSetOf<OldChanPostThread>()

      for (thread in threadBatch) {
        if (thread.threadBookmarkId != null) {
          ++skippedTotal
          Logger.d(TAG, "deleteOldThreads() skipping bookmarked thread (threadNo = ${thread.threadNo}, " +
            "deletedTotal = $deletedTotal, toDeleteCount = $toDeleteCount)")
          continue
        }

        if (thread.downloadThreadId != null) {
          ++skippedTotal
          Logger.d(TAG, "deleteOldThreads() skipping downloading thread (threadNo = ${thread.threadNo}, " +
            "deletedTotal = $deletedTotal, toDeleteCount = $toDeleteCount)")
          continue
        }

        if (deletedTotal >= toDeleteCount) {
          Logger.d(TAG, "deleteOldThreads() Deleted enough threads (deletedTotal = $deletedTotal, " +
            "toDeleteCount = $toDeleteCount), exiting early")
          break
        }

        chanThreads += thread
      }

      if (chanThreads.isNotEmpty()) {
        val threadIdSet = chanThreads
          .map { chanThreadsWithPosts -> chanThreadsWithPosts.threadId }
          .toSet()

        val totalPosts = chanThreads
          .sumBy { chanThreadsWithPosts -> chanThreadsWithPosts.postsCount }

        Logger.d(TAG, "deleteOldThreads() deleting a batch of ${threadIdSet.size} threads with $totalPosts posts")
        deletedTotal += chanThreadDao.deleteThreads(threadIdSet)
      }

      offset += threadBatch.size
    } while (deletedTotal < toDeleteCount)

    return DeleteResult(deletedTotal, skippedTotal)
  }

  class PostAdditionalData(
    val postImageByPostIdMap: Map<Long, List<ChanPostImageEntity>>,
    val postIconsByPostIdMap: Map<Long, List<ChanPostHttpIconEntity>>,
    val postReplyToByPostIdMap: Map<Long, List<ChanPostReplyEntity>>
  )

  data class DeleteResult(val deletedTotal: Int = 0, val skippedTotal: Int = 0)

  companion object {
    private const val ENTITIES_IN_BATCH = KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE

    private val TEN_SECONDS = TimeUnit.SECONDS.toMillis(10)
  }
}