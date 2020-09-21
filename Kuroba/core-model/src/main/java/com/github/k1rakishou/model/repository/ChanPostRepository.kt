package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.*
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.common.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.source.cache.PostsCache
import com.github.k1rakishou.model.source.local.ChanPostLocalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class ChanPostRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val isDevFlavor: Boolean,
  private val applicationScope: CoroutineScope,
  private val localSource: ChanPostLocalSource,
  private val appConstants: AppConstants
) : AbstractRepository(database, logger) {
  private val TAG = "$loggerTag ChanPostRepository"
  private val suspendableInitializer = SuspendableInitializer<Unit>("ChanPostRepository")
  private val postCache = PostsCache(appConstants.maxPostsCountInPostsCache, loggerTag, logger,)

  init {
    applicationScope.launch(Dispatchers.Default) {
      // We need to first delete the posts, so that the threads are only left with the OP
      val postDeleteResult = deleteOldPostsIfNeeded().mapValue { Unit }
      if (postDeleteResult is ModularResult.Error) {
        suspendableInitializer.initWithModularResult(postDeleteResult)
        return@launch
      }

      // Then we can delete the threads themselves
      val threadDeleteResult = deleteOldThreadsIfNeeded().mapValue { Unit }
      suspendableInitializer.initWithModularResult(threadDeleteResult)
    }
  }

  suspend fun awaitUntilInitialized() = suspendableInitializer.awaitUntilInitialized()

  suspend fun getTotalCachedPostsCount(): Int {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync postCache.getTotalCachedPostsCount()
    }
  }

  suspend fun createEmptyThreadIfNotExists(descriptor: ChanDescriptor.ThreadDescriptor): ModularResult<Long?> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.insertEmptyThread(descriptor)
      }
    }
  }

  /**
   * Returns a list of posts that differ from the cached ones and which we want to parse again and
   * show the user (otherwise show cached posts)
   * */
  suspend fun insertOrUpdateMany(
    posts: MutableList<ChanPost>,
    isCatalog: Boolean
  ): ModularResult<List<Long>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        if (isCatalog) {
          return@tryWithTransaction insertOrUpdateCatalogOriginalPosts(posts)
        } else {
          return@tryWithTransaction insertOrUpdateThreadPosts(posts)
        }
      }
    }
  }

  suspend fun getCachedThreadPostsNos(threadDescriptor: ChanDescriptor.ThreadDescriptor): Set<Long> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return postCache.getAllPostNoSet(threadDescriptor, Int.MAX_VALUE)
  }

  suspend fun getCachedPost(postDescriptor: PostDescriptor, isOP: Boolean): ChanPost? {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return postCache.getPostFromCache(postDescriptor, isOP)
  }

  suspend fun deleteThreadsFromCache(threadDescriptors: List<ChanDescriptor.ThreadDescriptor>) {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    postCache.deleteThreads(threadDescriptors)
  }

  suspend fun deletePostFromCache(postDescriptor: PostDescriptor) {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    postCache.deletePost(postDescriptor)
  }

  suspend fun putPostHash(postDescriptor: PostDescriptor, hash: MurmurHashUtils.Murmur3Hash) {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    postCache.putPostHash(postDescriptor, hash)
  }

  suspend fun getPostHash(postDescriptor: PostDescriptor): MurmurHashUtils.Murmur3Hash? {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return postCache.getPostHash(postDescriptor)
  }

  suspend fun getCatalogOriginalPosts(
    descriptor: ChanDescriptor.CatalogDescriptor,
    count: Int
  ): ModularResult<List<ChanPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }
    require(count > 0) { "Bad count param: $count" }

    logger.log(TAG, "getCatalogOriginalPosts(descriptor=$descriptor, count=$count)")

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val originalPostsFromDatabase = localSource.getCatalogOriginalPosts(
          descriptor,
          count
        )

        if (originalPostsFromDatabase.isNotEmpty()) {
          postCache.putManyIntoCache(originalPostsFromDatabase)
        }

        return@tryWithTransaction originalPostsFromDatabase
          // Sort in descending order by threads' lastModified value because that's the BUMP ordering
          .sortedByDescending { chanPost -> chanPost.lastModified }
      }
    }
  }

  suspend fun getCatalogOriginalPosts(
    descriptor: ChanDescriptor.CatalogDescriptor,
    threadNoList: Collection<Long>
  ): ModularResult<List<ChanPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val originalPostsFromCache = threadNoList.mapNotNull { threadNo ->
          postCache.getOriginalPostFromCache(descriptor.toThreadDescriptor(threadNo))
        }

        val originalPostNoFromCacheSet = originalPostsFromCache.map { post ->
          post.postDescriptor.postNo
        }.toSet()

        val originalPostNoListToGetFromDatabase = threadNoList.filter { threadNo ->
          threadNo !in originalPostNoFromCacheSet
        }

        if (originalPostNoListToGetFromDatabase.isEmpty()) {
          // All posts were found in the cache
          logger.log(TAG, "getCatalogOriginalPosts() found all posts in the cache " +
            "(count=${originalPostsFromCache.size})")
          return@tryWithTransaction originalPostsFromCache
        }

        val originalPostsFromDatabase = localSource.getCatalogOriginalPosts(
          descriptor,
          originalPostNoListToGetFromDatabase
        )

        if (originalPostsFromDatabase.isNotEmpty()) {
          postCache.putManyIntoCache(originalPostsFromDatabase)
        }

        logger.log(TAG, "getCatalogOriginalPosts() found ${originalPostsFromCache.size} posts in " +
          "the cache and the rest (${originalPostsFromDatabase.size}) taken from the database")
        return@tryWithTransaction originalPostsFromCache + originalPostsFromDatabase
      }
    }
  }

  suspend fun getCatalogOriginalPosts(
    threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>
  ): ModularResult<Map<ChanDescriptor.ThreadDescriptor, ChanPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val originalPostsFromCache = postCache.getOriginalPostsFromCache(threadDescriptors)

        val notCachedOriginalPostThreadDescriptors = threadDescriptors.filter { threadDescriptor ->
          !originalPostsFromCache.containsKey(threadDescriptor)
        }

        if (notCachedOriginalPostThreadDescriptors.isEmpty()) {
          // All posts were found in the cache
          logger.log(TAG, "getCatalogOriginalPosts() found all posts in the cache " +
            "(count=${originalPostsFromCache.size})")
          return@tryWithTransaction originalPostsFromCache
        }

        val originalPostsFromDatabase = localSource.getCatalogOriginalPosts(
          notCachedOriginalPostThreadDescriptors
        )

        if (originalPostsFromDatabase.isNotEmpty()) {
          postCache.putManyIntoCache(originalPostsFromDatabase.values)
        }

        val resultMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ChanPost>(
          originalPostsFromCache.size + originalPostsFromDatabase.size
        )

        logger.log(TAG, "getCatalogOriginalPosts() found ${originalPostsFromCache.size} posts in " +
          "the cache and the rest (${originalPostsFromDatabase.size}) taken from the database")

        resultMap.putAll(originalPostsFromCache)
        resultMap.putAll(originalPostsFromDatabase)

        if (isDevFlavor) {
          resultMap.values.forEach { chanPost ->
            check(chanPost.isOp) { "getCatalogOriginalPosts() is returning a non-OP post!" }
          }
        }

        return@tryWithTransaction resultMap
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ModularResult<Unit> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        logger.log(TAG, "preloadForThread($threadDescriptor) begin")

        val time = measureTime {
          val postsFromDatabase = localSource.getThreadPosts(
            threadDescriptor,
            emptySet(),
            Int.MAX_VALUE
          )

          logger.log(TAG, "preloadForThread($threadDescriptor) got ${postsFromDatabase.size} from DB")

          if (postsFromDatabase.isNotEmpty()) {
            postCache.putManyIntoCache(postsFromDatabase)
          }
        }

        logger.log(TAG, "preloadForThread($threadDescriptor) end, took $time")
      }
    }
  }

  suspend fun getThreadPosts(
    descriptor: ChanDescriptor.ThreadDescriptor,
    maxCount: Int
  ): List<ChanPost> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }
    logger.log(TAG, "getThreadPosts(descriptor=$descriptor, maxCount=$maxCount)")

    return applicationScope.myAsync {
      return@myAsync postCache.getAll(descriptor, maxCount)
    }
  }

  suspend fun deleteAll(): ModularResult<Int> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val result = localSource.deleteAll()
        postCache.deleteAll()

        return@tryWithTransaction result
      }
    }
  }

  suspend fun deleteThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ModularResult<Unit> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val result = localSource.deleteThread(threadDescriptor)
        postCache.deleteThread(threadDescriptor)

        return@tryWithTransaction result
      }
    }
  }

  suspend fun deletePost(postDescriptor: PostDescriptor): ModularResult<Unit> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        localSource.deletePost(postDescriptor)
        postCache.deletePost(postDescriptor)

        return@tryWithTransaction
      }
    }
  }

  suspend fun totalPostsCount(): ModularResult<Int> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.countTotalAmountOfPosts()
      }
    }
  }

  suspend fun totalThreadsCount(): ModularResult<Int> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.countTotalAmountOfThreads()
      }
    }
  }

  private suspend fun insertOrUpdateCatalogOriginalPosts(posts: MutableList<ChanPost>): List<Long> {
    if (posts.isEmpty()) {
      return emptyList()
    }

    require(posts.all { post -> post.isOp }) { "Not all posts are original posts" }
    localSource.insertManyOriginalPosts(posts)

    if (posts.isNotEmpty()) {
      postCache.putManyIntoCache(posts)
    }

    return posts.map { it.postDescriptor.postNo }
  }

  private suspend fun insertOrUpdateThreadPosts(posts: MutableList<ChanPost>): List<Long> {
    var originalPost: ChanPost? = null
    val postsThatDifferWithCache = ArrayList<ChanPost>()

    // Figure out what posts differ from the cache that we want to update in the
    // database
    posts.forEach { chanPost ->
      val differsFromCached = postDiffersFromCached(chanPost)
      if (differsFromCached) {
        if (chanPost.isOp) {
          if (originalPost != null) {
            throw IllegalStateException("More than one OP found!")
          }

          originalPost = chanPost
        } else {
          postsThatDifferWithCache += chanPost
        }
      }
    }

    logger.log(TAG, "insertOrUpdateThreadPosts() ${postsThatDifferWithCache.size} posts differ from " +
      "the cache (total posts=${posts.size})")

    val chanThreadId = if (originalPost != null) {
      val chanThreadId = localSource.insertOriginalPost(originalPost!!)
      postCache.putIntoCache(originalPost!!)

      chanThreadId
    } else {
      if (postsThatDifferWithCache.isNotEmpty()) {
        localSource.getThreadIdByPostDescriptor(
          postsThatDifferWithCache.first().postDescriptor
        )
      } else {
        null
      }
    }

    if (chanThreadId == null) {
      return if (originalPost == null) {
        emptyList()
      } else {
        listOf(originalPost!!.postDescriptor.postNo)
      }
    }

    if (postsThatDifferWithCache.isNotEmpty()) {
      localSource.insertPosts(chanThreadId, postsThatDifferWithCache)
      postCache.putManyIntoCache(postsThatDifferWithCache)
    }

    return postsThatDifferWithCache.map { it.postDescriptor.postNo }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun deleteOldPostsIfNeeded(forced: Boolean = false): ModularResult<ChanPostLocalSource.DeleteResult> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val totalAmountOfPostsInDatabase = localSource.countTotalAmountOfPosts()
        if (totalAmountOfPostsInDatabase <= 0) {
          logger.log(TAG, "deleteOldPostsIfNeeded database is empty")
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        val maxPostsAmount = appConstants.maxAmountOfPostsInDatabase

        if (!forced && totalAmountOfPostsInDatabase < maxPostsAmount) {
          logger.log(TAG, "Not enough posts to start deleting, " +
            "posts in database amount: $totalAmountOfPostsInDatabase, " +
            "max allowed posts amount: $maxPostsAmount")
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        val toDeleteCount = if (forced) {
          totalAmountOfPostsInDatabase / 2
        } else {
          // Delete half of the posts in the database
          max(totalAmountOfPostsInDatabase, maxPostsAmount) / 2
        }

        if (toDeleteCount <= 0) {
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        logger.log(TAG, "Starting deleting $toDeleteCount posts " +
          "(totalAmountOfPostsInDatabase = $totalAmountOfPostsInDatabase, " +
          "maxPostsAmount = $maxPostsAmount)")

        val (deleteMRResult, time) = measureTimedValue { Try { localSource.deleteOldPosts(toDeleteCount) } }
        val deleteResult = if (deleteMRResult is ModularResult.Error) {
          logger.logError(TAG, "Error while trying to delete old posts", deleteMRResult.error)
          throw deleteMRResult.error
        } else {
          (deleteMRResult as ModularResult.Value).value
        }

        val newAmount = localSource.countTotalAmountOfPosts()
        logger.log(TAG, "Deleted ${deleteResult.deletedTotal} posts, " +
          "skipped ${deleteResult.skippedTotal} posts, $newAmount posts left, took $time")

        return@tryWithTransaction deleteResult
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun deleteOldThreadsIfNeeded(forced: Boolean = false): ModularResult<ChanPostLocalSource.DeleteResult> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val totalAmountOfThreadsInDatabase = localSource.countTotalAmountOfThreads()
        if (totalAmountOfThreadsInDatabase <= 0) {
          logger.log(TAG, "deleteOldThreadsIfNeeded database is empty")
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        val maxThreadsAmount = appConstants.maxAmountOfThreadsInDatabase

        if (!forced && totalAmountOfThreadsInDatabase < maxThreadsAmount) {
          logger.log(TAG, "Not enough threads to start deleting, " +
            "threads in database amount: $totalAmountOfThreadsInDatabase, " +
            "max allowed threads amount: $maxThreadsAmount")
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        val toDeleteCount = if (forced) {
          totalAmountOfThreadsInDatabase / 2
        } else {
          // Delete half of the posts in the database
          max(totalAmountOfThreadsInDatabase, maxThreadsAmount) / 2
        }

        if (toDeleteCount <= 0) {
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        logger.log(TAG, "Starting deleting $toDeleteCount threads " +
          "(totalAmountOfThreadsInDatabase = $totalAmountOfThreadsInDatabase, " +
          "maxThreadsAmount = $maxThreadsAmount)")

        val (deleteMRResult, time) = measureTimedValue { Try { localSource.deleteOldThreads(toDeleteCount) } }
        val deleteResult = if (deleteMRResult is ModularResult.Error) {
          logger.logError(TAG, "Error while trying to delete old threads", deleteMRResult.error)
          throw deleteMRResult.error
        } else {
          (deleteMRResult as ModularResult.Value).value
        }

        val newAmount = localSource.countTotalAmountOfThreads()
        logger.log(TAG, "Deleted ${deleteResult.deletedTotal} threads, " +
          "skipped ${deleteResult.skippedTotal} threads, $newAmount threads left, took $time")

        return@tryWithTransaction deleteResult
      }
    }
  }

  private suspend fun postDiffersFromCached(chanPost: ChanPost): Boolean {
    val fromCache = postCache.getPostFromCache(
      chanPost.postDescriptor,
      chanPost.isOp
    )

    if (fromCache == null) {
      // Post is not cached yet - update
      return true
    }

    if (fromCache.isOp) {
      // Cached post is an original post - always update
      return true
    }

    if (fromCache != chanPost) {
      // Cached post is not the same as the fresh post - update
      return true
    }

    return false
  }

}