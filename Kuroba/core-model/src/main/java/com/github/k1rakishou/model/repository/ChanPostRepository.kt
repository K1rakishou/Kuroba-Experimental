package com.github.k1rakishou.model.repository

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.ModularResult.Companion.value
import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.linkedMapWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.myAsync
import com.github.k1rakishou.common.options.ChanCacheOptions
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.id.ThreadDBId
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.source.cache.ChanDescriptorCache
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import com.github.k1rakishou.model.source.local.ChanPostLocalSource
import com.github.k1rakishou.model.util.ensureBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class ChanPostRepository(
  database: KurobaDatabase,
  private val isDevFlavor: Boolean,
  private val applicationScope: CoroutineScope,
  private val appConstants: AppConstants,
  private val localSource: ChanPostLocalSource,
  private val chanThreadsCache: ChanThreadsCache,
  private val chanDescriptorCache: ChanDescriptorCache
) : AbstractRepository(database) {
  private val TAG = "ChanPostRepository"
  private val suspendableInitializer = SuspendableInitializer<Unit>("ChanPostRepository")

  fun initialize() {
    Logger.d(TAG, "ChanPostRepository.initialize()")

    applicationScope.launch(Dispatchers.IO) {
      // We need to first delete the posts, so that the threads are only left with the OP
      val postDeleteResult = deleteOldPostsIfNeeded()
      if (postDeleteResult is ModularResult.Error) {
        Logger.e(TAG, "deleteOldPostsIfNeeded() error", postDeleteResult.error)
        return@launch
      }

      // Then we can delete the threads themselves
      val threadDeleteResult = deleteOldThreadsIfNeeded()
      if (threadDeleteResult is ModularResult.Error) {
        Logger.e(TAG, "deleteOldThreadsIfNeeded() error", threadDeleteResult.error)
        return@launch
      }

      suspendableInitializer.initWithValue(Unit)
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun awaitUntilInitialized() {
    if (isReady()) {
      return
    }

    Logger.d(TAG, "ChanPostRepository is not ready yet, waiting...")
    val duration = measureTime { suspendableInitializer.awaitUntilInitialized() }
    Logger.d(TAG, "ChanPostRepository initialization completed, took $duration")
  }

  fun isReady() = suspendableInitializer.isInitialized()

  fun updateThreadLastAccessTime(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    chanThreadsCache.updateLastAccessTime(threadDescriptor)
  }

  suspend fun getTotalCachedPostsCount(): Int {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync chanThreadsCache.getTotalCachedPostsCount()
    }
  }

  suspend fun getTotalCachedThreadCount(): Int {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync chanThreadsCache.getCachedThreadsCount()
    }
  }

  suspend fun getThreadsWithMoreThanOnePostCount(): Int {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync chanThreadsCache.getThreadsWithMoreThanOnePostCount()
    }
  }

  suspend fun getThreadCachedPostsCount(threadDescriptor: ChanDescriptor.ThreadDescriptor): Int? {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync chanThreadsCache.getThreadCachedPostsCount(threadDescriptor)
    }
  }

  suspend fun createEmptyThreadIfNotExists(descriptor: ChanDescriptor.ThreadDescriptor): ModularResult<Boolean> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    val threadDatabaseIdFromCache = chanDescriptorCache.getThreadIdByThreadDescriptorFromCache(descriptor)?.id
    if (threadDatabaseIdFromCache != null) {
      return value(threadDatabaseIdFromCache >= 0L)
    }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val createdThreadDatabaseId = localSource.insertEmptyThread(descriptor) ?: -1L
        if (createdThreadDatabaseId >= 0L) {
          chanDescriptorCache.putThreadDescriptor(
            ThreadDBId(createdThreadDatabaseId),
            descriptor
          )
        }

        return@tryWithTransaction createdThreadDatabaseId >= 0
      }
    }
  }

  /**
   * Returns a list of posts that differ from the cached ones and which we want to parse again and
   * show the user (otherwise show cached posts)
   * */
  @Suppress("UNCHECKED_CAST")
  suspend fun insertOrUpdateMany(
    posts: List<ChanPost>,
    cacheOptions: ChanCacheOptions,
    isCatalog: Boolean
  ): ModularResult<Int> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }
    ensureBackgroundThread()

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        if (isCatalog) {
          val allPostsAreOriginal = posts.all { post -> post is ChanOriginalPost }
          require(allPostsAreOriginal) { "Not all posts are original posts" }

          val postsCount = insertOrUpdateCatalogOriginalPosts(
            posts as List<ChanOriginalPost>,
            cacheOptions
          )

          Logger.d(TAG, "insertOrUpdateMany(isCatalog=$isCatalog) -> $postsCount")
          return@tryWithTransaction postsCount
        } else {
          val newPostsCount = insertOrUpdateThreadPosts(
            posts,
            cacheOptions
          )

          Logger.d(TAG, "insertOrUpdateMany(isCatalog=$isCatalog) -> $newPostsCount")
          return@tryWithTransaction newPostsCount
        }
      }
    }
  }

  fun getCachedThreadPostsNos(threadDescriptor: ChanDescriptor.ThreadDescriptor): Set<Long> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return chanThreadsCache.getThreadPostNoSet(threadDescriptor)
  }

  fun getCachedPost(postDescriptor: PostDescriptor): ChanPost? {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    if (postDescriptor.isOP()) {
      return chanThreadsCache.getOriginalPostFromCache(postDescriptor)
    } else {
      return chanThreadsCache.getPostFromCache(postDescriptor)
    }
  }

  fun putPostHash(postDescriptor: PostDescriptor, hash: MurmurHashUtils.Murmur3Hash) {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    chanThreadsCache.putPostHash(postDescriptor, hash)
  }

  fun getPostHash(postDescriptor: PostDescriptor): MurmurHashUtils.Murmur3Hash? {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return chanThreadsCache.getPostHash(postDescriptor)
  }

  fun clearPostHashes() {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    chanThreadsCache.clearPostHashes()
  }

  fun markThreadAsDeleted(threadDescriptor: ChanDescriptor.ThreadDescriptor, deleted: Boolean) {
    chanThreadsCache.markThreadAsDeleted(threadDescriptor, deleted)
  }

  suspend fun getCatalogOriginalPosts(
    descriptor: ChanDescriptor.CatalogDescriptor,
    count: Int
  ): ModularResult<List<ChanPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }
    require(count > 0) { "Bad count param: $count" }

    Logger.d(TAG, "getCatalogOriginalPosts(descriptor=$descriptor, count=$count)")

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val catalogPosts = localSource.getCatalogOriginalPosts(
          descriptor,
          count
        )

        if (catalogPosts.isNotEmpty()) {
          chanThreadsCache.putManyCatalogPostsIntoCache(
            originalPosts = catalogPosts,
            cacheOptions = ChanCacheOptions.default()
          )
        }

        return@tryWithTransaction catalogPosts
          // Sort in descending order by threads' lastModified value because that's the BUMP ordering
          .sortedByDescending { chanPost -> chanPost.lastModified }
      }
    }
  }

  suspend fun getCatalogOriginalPosts(
    descriptor: ChanDescriptor.CatalogDescriptor,
    originalPostDescriptorList: Collection<PostDescriptor>
  ): ModularResult<List<ChanPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val originalPostsFromCache = originalPostDescriptorList.mapNotNull { postDescriptor ->
          chanThreadsCache.getOriginalPostFromCache(postDescriptor.threadDescriptor())
        }

        val originalPostsFromCacheSet = originalPostsFromCache
          .map { post -> post.postDescriptor }
          .toSet()

        val originalPostNoListToGetFromDatabase = originalPostDescriptorList
          .filter { postDescriptor -> postDescriptor !in originalPostsFromCacheSet }
          .map { postDescriptor -> postDescriptor.postNo }

        if (originalPostNoListToGetFromDatabase.isEmpty()) {
          // All posts were found in the cache
          Logger.d(TAG, "getCatalogOriginalPosts() found all posts in the cache " +
            "(count=${originalPostsFromCache.size})")
          return@tryWithTransaction originalPostsFromCache
        }

        val catalogPostsFromDatabase = localSource.getCatalogOriginalPosts(
          descriptor,
          originalPostNoListToGetFromDatabase
        )

        if (catalogPostsFromDatabase.isNotEmpty()) {
          chanThreadsCache.putManyCatalogPostsIntoCache(
            originalPosts = catalogPostsFromDatabase,
            cacheOptions = ChanCacheOptions.default()
          )
        }

        Logger.d(TAG, "getCatalogOriginalPosts() found ${originalPostsFromCache.size} posts in " +
          "the cache and the rest (${catalogPostsFromDatabase.size}) taken from the database")
        return@tryWithTransaction originalPostsFromCache + catalogPostsFromDatabase
      }
    }
  }

  /**
   * Returns LinkedHashMap of OP posts associated with thread descriptors sorted in the order of [threadDescriptors]
   * */
  suspend fun getCatalogOriginalPosts(
    threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>
  ): ModularResult<LinkedHashMap<ChanDescriptor.ThreadDescriptor, ChanOriginalPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val originalPostsFromCache = chanThreadsCache.getCatalogPostsFromCache(threadDescriptors)

        val notCachedOriginalPostThreadDescriptors = threadDescriptors.filter { threadDescriptor ->
          !originalPostsFromCache.containsKey(threadDescriptor)
        }

        if (notCachedOriginalPostThreadDescriptors.isEmpty()) {
          // All posts were found in the cache
          Logger.d(TAG, "getCatalogOriginalPosts() found all posts in the cache " +
            "(count=${originalPostsFromCache.size})")
          return@tryWithTransaction originalPostsFromCache
        }

        val catalogPostsFromDatabase = localSource.getCatalogOriginalPosts(
          notCachedOriginalPostThreadDescriptors
        )

        if (catalogPostsFromDatabase.isNotEmpty()) {
          chanThreadsCache.putManyCatalogPostsIntoCache(
            originalPosts = catalogPostsFromDatabase.values.toList(),
            cacheOptions = ChanCacheOptions.default()
          )
        }

        val tempMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ChanOriginalPost>(
          originalPostsFromCache.size + catalogPostsFromDatabase.size
        )

        Logger.d(TAG, "getCatalogOriginalPosts() found ${originalPostsFromCache.size} posts in " +
          "the cache and the rest (${catalogPostsFromDatabase.size}) taken from the database")

        tempMap.putAll(originalPostsFromCache)
        tempMap.putAll(catalogPostsFromDatabase)

        val resultMap = linkedMapWithCap<ChanDescriptor.ThreadDescriptor, ChanOriginalPost>(tempMap.size)

        threadDescriptors.forEach { threadDescriptor ->
          resultMap[threadDescriptor] = requireNotNull(tempMap[threadDescriptor])
        }

        return@tryWithTransaction resultMap
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ModularResult<Unit> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }
    ensureBackgroundThread()

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        Logger.d(TAG, "preloadForThread($threadDescriptor) begin")

        val time = measureTime {
          val postsFromDatabase = localSource.getThreadPosts(threadDescriptor)
          Logger.d(TAG, "preloadForThread($threadDescriptor) got ${postsFromDatabase.size} from DB")

          if (postsFromDatabase.isNotEmpty()) {
            chanThreadsCache.putManyThreadPostsIntoCache(
              posts = postsFromDatabase,
              cacheOptions = ChanCacheOptions.default()
            )
          }
        }

        Logger.d(TAG, "preloadForThread($threadDescriptor) end, took $time")
      }
    }
  }

  suspend fun getThreadPosts(
    descriptor: ChanDescriptor.ThreadDescriptor
  ): ModularResult<List<ChanPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }
    ensureBackgroundThread()

    Logger.d(TAG, "getThreadPosts(descriptor=$descriptor)")

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val postsFromCache = chanThreadsCache.getThreadPosts(descriptor)
        if (postsFromCache.isNotEmpty()) {
          return@tryWithTransaction postsFromCache
        }

        val postsFromDatabase = localSource.getThreadPosts(descriptor)
        if (postsFromDatabase.isEmpty()) {
          return@tryWithTransaction emptyList()
        }

        chanThreadsCache.putManyThreadPostsIntoCache(
          posts = postsFromDatabase,
          cacheOptions = ChanCacheOptions.default()
        )

        return@tryWithTransaction postsFromDatabase
      }
    }
  }

  suspend fun deleteAll(): ModularResult<Int> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val result = localSource.deleteAll()
        chanThreadsCache.deleteAll()

        return@tryWithTransaction result
      }
    }
  }

  suspend fun deleteThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ModularResult<Unit> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val result = localSource.deleteThread(threadDescriptor)
        chanThreadsCache.deleteThread(threadDescriptor)

        return@tryWithTransaction result
      }
    }
  }

  suspend fun deleteCatalog(catalogDescriptor: ChanDescriptor.CatalogDescriptor): ModularResult<Unit> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val threadDescriptors = chanThreadsCache.getCatalog(catalogDescriptor)
          ?.mapPostsOrdered { chanOriginalPost -> chanOriginalPost.postDescriptor.threadDescriptor() }
          ?.distinct()

        if (threadDescriptors != null) {
          localSource.deleteCatalog(threadDescriptors)
        }

        return@tryWithTransaction
      }
    }
  }

  suspend fun deletePost(postDescriptor: PostDescriptor): ModularResult<Unit> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        localSource.deletePost(postDescriptor)
        chanThreadsCache.deletePost(postDescriptor)

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

  private suspend fun insertOrUpdateCatalogOriginalPosts(
    posts: List<ChanOriginalPost>,
    cacheOptions: ChanCacheOptions
  ): Int {
    ensureBackgroundThread()

    if (posts.isEmpty()) {
      return 0
    }

    val postsToStoreIntoDatabase = chanThreadsCache.putManyCatalogPostsIntoCache(
      originalPosts = posts,
      cacheOptions = cacheOptions
    )

    if (postsToStoreIntoDatabase.isNotEmpty()) {
      localSource.insertManyOriginalPosts(postsToStoreIntoDatabase, cacheOptions)
    }

    return posts.size
  }

  private suspend fun insertOrUpdateThreadPosts(
    posts: List<ChanPost>,
    cacheOptions: ChanCacheOptions
  ): Int {
    ensureBackgroundThread()

    val postsThatDifferWithCache = ArrayList<ChanPost>()

    // Figure out what posts differ from the cache that we want to update in the
    // database
    posts.forEach { chanPost ->
      val differsFromCached = postDiffersFromCached(chanPost)
      if (differsFromCached) {
        postsThatDifferWithCache += chanPost
      }
    }

    if (postsThatDifferWithCache.isEmpty()) {
      Logger.d(TAG, "postsThatDifferWithCache is empty")
      return 0
    }

    Logger.d(TAG, "insertOrUpdateThreadPosts() ${postsThatDifferWithCache.size} posts differ from " +
      "the cache (total posts=${posts.size})")

    val postsToStoreIntoDatabase = chanThreadsCache.putManyThreadPostsIntoCache(
      postsThatDifferWithCache,
      cacheOptions
    )

    if (postsToStoreIntoDatabase.isNotEmpty()) {
      localSource.insertPosts(postsToStoreIntoDatabase, cacheOptions)
    }

    return postsThatDifferWithCache.size
  }

  @OptIn(ExperimentalTime::class)
  suspend fun deleteOldPostsIfNeeded(forced: Boolean = false): ModularResult<ChanPostLocalSource.DeleteResult> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val totalAmountOfPostsInDatabase = localSource.countTotalAmountOfPosts()
        if (totalAmountOfPostsInDatabase <= 0) {
          Logger.d(TAG, "deleteOldPostsIfNeeded database is empty")
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        val maxPostsAmount = appConstants.maxAmountOfPostsInDatabase

        if (!forced && totalAmountOfPostsInDatabase < maxPostsAmount) {
          Logger.d(TAG, "Not enough posts to start deleting, " +
            "posts in database amount: $totalAmountOfPostsInDatabase, " +
            "max allowed posts amount: $maxPostsAmount")
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        val postsInDatabaseToUse = if (forced) {
          totalAmountOfPostsInDatabase
        } else {
          max(totalAmountOfPostsInDatabase, maxPostsAmount)
        }

        val toDeleteCount = (postsInDatabaseToUse / (100f / ChanSettings.databasePostsCleanupRemovePercent.get().toFloat())).toInt()
        if (toDeleteCount <= 0) {
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        Logger.d(TAG, "Starting deleting $toDeleteCount posts " +
          "(totalAmountOfPostsInDatabase = $totalAmountOfPostsInDatabase, " +
          "maxPostsAmount = $maxPostsAmount)")

        val (deleteMRResult, time) = measureTimedValue { Try { localSource.deleteOldPosts(toDeleteCount) } }
        val deleteResult = if (deleteMRResult is ModularResult.Error) {
          Logger.d(TAG, "Error while trying to delete old posts", deleteMRResult.error)
          throw deleteMRResult.error
        } else {
          (deleteMRResult as ModularResult.Value).value
        }

        val newAmount = localSource.countTotalAmountOfPosts()
        Logger.d(TAG, "Deleted ${deleteResult.deletedTotal} posts, " +
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
          Logger.d(TAG, "deleteOldThreadsIfNeeded database is empty")
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        val maxThreadsAmount = appConstants.maxAmountOfThreadsInDatabase

        if (!forced && totalAmountOfThreadsInDatabase < maxThreadsAmount) {
          Logger.d(TAG, "Not enough threads to start deleting, " +
            "threads in database amount: $totalAmountOfThreadsInDatabase, " +
            "max allowed threads amount: $maxThreadsAmount")
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        val threadsInDatabaseToUse = if (forced) {
          totalAmountOfThreadsInDatabase
        } else {
          max(totalAmountOfThreadsInDatabase, maxThreadsAmount)
        }

        val toDeleteCount = (threadsInDatabaseToUse / (100f / ChanSettings.databasePostsCleanupRemovePercent.get().toFloat())).toInt()
        if (toDeleteCount <= 0) {
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        Logger.d(TAG, "Starting deleting $toDeleteCount threads " +
          "(totalAmountOfThreadsInDatabase = $totalAmountOfThreadsInDatabase, " +
          "maxThreadsAmount = $maxThreadsAmount)")

        val (deleteMRResult, time) = measureTimedValue { Try { localSource.deleteOldThreads(toDeleteCount) } }
        val deleteResult = if (deleteMRResult is ModularResult.Error) {
          Logger.d(TAG, "Error while trying to delete old threads", deleteMRResult.error)
          throw deleteMRResult.error
        } else {
          (deleteMRResult as ModularResult.Value).value
        }

        val newAmount = localSource.countTotalAmountOfThreads()
        Logger.d(TAG, "Deleted ${deleteResult.deletedTotal} threads, " +
          "skipped ${deleteResult.skippedTotal} threads, $newAmount threads left, took $time")

        return@tryWithTransaction deleteResult
      }
    }
  }

  private fun postDiffersFromCached(chanPost: ChanPost): Boolean {
    val fromCache = if (chanPost is ChanOriginalPost) {
      chanThreadsCache.getOriginalPostFromCache(chanPost.postDescriptor)
    } else {
      chanThreadsCache.getPostFromCache(chanPost.postDescriptor)
    }

    if (fromCache == null) {
      // Post is not cached yet - update
      return true
    }

    if (fromCache is ChanOriginalPost) {
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