package com.github.k1rakishou.model.source.cache.thread

import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.linkedMapWithCap
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.catalog.ChanCatalog
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheOptions
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.thread.ChanThread
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache
import com.github.k1rakishou.model.util.ensureBackgroundThread
import org.joda.time.Period
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ChanThreadsCache(
  private val isDevBuild: Boolean,
  private val maxCacheSize: Int,
  private val chanCatalogSnapshotCache: ChanCatalogSnapshotCache
) {
  private val chanThreads = ConcurrentHashMap<ChanDescriptor.ThreadDescriptor, ChanThread>(128)
  private val lastEvictInvokeTime = AtomicLong(0L)

  fun isThreadLockCurrentlyLocked(threadDescriptor: ChanDescriptor.ThreadDescriptor): Boolean {
    return chanThreads[threadDescriptor]?.isThreadLockCurrentlyLocked() ?: false
  }

  fun putPostHash(postDescriptor: PostDescriptor, hash: MurmurHashUtils.Murmur3Hash) {
    chanThreads[postDescriptor.threadDescriptor()]?.putPostHash(postDescriptor, hash)
  }

  fun getPostHash(postDescriptor: PostDescriptor): MurmurHashUtils.Murmur3Hash? {
    return chanThreads[postDescriptor.threadDescriptor()]?.getPostHash(postDescriptor)
  }

  fun clearPostHashes() {
    chanThreads.values.forEach { chanThread -> chanThread.clearPostHashes() }
  }

  fun getAllPostsForDatabasePersisting(threadDescriptor: ChanDescriptor.ThreadDescriptor): List<ChanPost> {
    return chanThreads[threadDescriptor]?.getAllPostsForDatabasePersisting() ?: emptyList()
  }

  fun cacheNeedsUpdate(
    chanDescriptor: ChanDescriptor,
    chanCacheUpdateOptions: ChanCacheUpdateOptions
  ): Boolean {
    if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
      return true
    }

    if (chanCacheUpdateOptions is ChanCacheUpdateOptions.DoNotUpdateCache) {
      return false
    }

    if (chanCacheUpdateOptions is ChanCacheUpdateOptions.UpdateCache) {
      return true
    }

    val threadDescriptor = chanDescriptor as ChanDescriptor.ThreadDescriptor

    val chanThread = chanThreads[threadDescriptor]
      ?: return true

    return chanThread.cacheNeedsUpdate(chanCacheUpdateOptions)
  }


  /**
   * Inserts [parsedPosts] into the memory cache by either adding them as new or updating old ones.
   * Returns a list of updated posts.
   * */
  fun putManyCatalogPostsIntoCache(
    parsedPosts: List<ChanOriginalPost>,
    cacheOptions: ChanCacheOptions
  ) {
    runOldPostEvictionRoutineIfNeeded()

    val updatedPosts = mutableListWithCap<ChanOriginalPost>(parsedPosts)

    parsedPosts.forEach { chanOriginalPost ->
      check(chanOriginalPost.postDescriptor.descriptor is ChanDescriptor.ThreadDescriptor) {
        "Only thread descriptors are allowed in the cache!" +
          "descriptor=${chanOriginalPost.postDescriptor.descriptor}"
      }

      val threadDescriptor = chanOriginalPost.postDescriptor.threadDescriptor()

      val chanThread = chanThreads.getOrPut(threadDescriptor, defaultValue = {
        return@getOrPut ChanThread(
          isDevBuild = isDevBuild,
          threadDescriptor = threadDescriptor,
          initialLastAccessTime = getLastThreadAccessTime(cacheOptions)
        )
      })

      chanThread.setOrUpdateOriginalPost(chanOriginalPost)
      // Do not update "lastUpdateTime" here because it will break catalog thread previewing

      updatedPosts += chanThread.getOriginalPost()
    }
  }

  fun putManyThreadPostsIntoCache(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    parsedPosts: List<ChanPost>,
    cacheOptions: ChanCacheOptions,
    cacheUpdateOptions: ChanCacheUpdateOptions
  ) {
    // We are doing some kinda heavy stuff (reply calculations) so we want this method to always be
    //  called on a background thread.
    ensureBackgroundThread()

    val firstPost = parsedPosts.firstOrNull()

    if (isDevBuild && parsedPosts.isNotEmpty()) {
      val distinctByChanDescriptor = parsedPosts
        .map { chanPost -> chanPost.postDescriptor.descriptor }
        .toSet()

      if (distinctByChanDescriptor.size != 1) {
        throw IllegalStateException("The input posts list contains posts from different threads! posts: $parsedPosts")
      }
    }

    runOldPostEvictionRoutineIfNeeded()

    val chanThread = chanThreads.getOrPut(threadDescriptor, defaultValue = {
      return@getOrPut ChanThread(
        isDevBuild = isDevBuild,
        threadDescriptor = threadDescriptor,
        initialLastAccessTime = getLastThreadAccessTime(cacheOptions)
      )
    })

    if (cacheOptions.canStoreInMemory()) {
      chanThread.addOrUpdatePosts(parsedPosts)
    } else if (firstPost != null && firstPost is ChanOriginalPost) {
      chanThread.setOrUpdateOriginalPost(firstPost)
    }

    chanThread.updateLastUpdateTime(cacheUpdateOptions)
  }

  fun getCachedThreadsCount(): Int {
    return chanThreads.size
  }

  fun getThreadsWithMoreThanOnePostCount(): Int {
    return chanThreads
      .count { (_, chanThread) -> chanThread.postsCount > 1 }
  }

  fun getThreadCachedPostsCount(threadDescriptor: ChanDescriptor.ThreadDescriptor): Int? {
    return chanThreads[threadDescriptor]?.postsCount
  }

  fun getOriginalPostFromCache(postDescriptor: PostDescriptor): ChanOriginalPost? {
    val threadDescriptor = postDescriptor.threadDescriptor()
    return chanThreads[threadDescriptor]?.getOriginalPost()
  }

  fun getOriginalPostFromCache(threadDescriptor: ChanDescriptor.ThreadDescriptor): ChanOriginalPost? {
    return chanThreads[threadDescriptor]?.getOriginalPost()
  }

  fun getPostFromCache(chanDescriptor: ChanDescriptor, postNo: Long): ChanPost? {
    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        val postDescriptor = PostDescriptor.create(chanDescriptor, postNo)

        return chanThreads[chanDescriptor]?.getPost(postDescriptor)
      }
      is ChanDescriptor.CatalogDescriptor -> {
        val threadDescriptors = chanCatalogSnapshotCache.get(chanDescriptor.boardDescriptor)
          ?.catalogThreadDescriptorList
          ?: return null

        val threadDescriptor = threadDescriptors
          .firstOrNull { threadDescriptor -> threadDescriptor.threadNo == postNo }
          ?: return null

        return chanThreads[threadDescriptor]?.getOriginalPost()
      }
    }
  }

  fun getPostFromCache(postDescriptor: PostDescriptor): ChanPost? {
    val threadDescriptor = postDescriptor.threadDescriptor()
    return chanThreads[threadDescriptor]?.getPost(postDescriptor)
  }

  fun getCatalog(catalogDescriptor: ChanDescriptor.CatalogDescriptor): ChanCatalog? {
    val threadDescriptors = chanCatalogSnapshotCache.get(catalogDescriptor.boardDescriptor)
      ?.catalogThreadDescriptorList
      ?: return null

    val posts =  threadDescriptors
      .mapNotNull { threadDescriptor -> chanThreads[threadDescriptor]?.getOriginalPost() }

    return ChanCatalog(catalogDescriptor, posts)
  }

  fun getThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ChanThread? {
    return chanThreads[threadDescriptor]
  }

  fun contains(chanDescriptor: ChanDescriptor): Boolean {
    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        return chanThreads[chanDescriptor]?.hasAtLeastOnePost() ?: false
      }
      is ChanDescriptor.CatalogDescriptor -> {
        val catalogThreadDescriptorList = chanCatalogSnapshotCache.get(chanDescriptor.boardDescriptor)
          ?.catalogThreadDescriptorList

        return catalogThreadDescriptorList != null && catalogThreadDescriptorList.isNotEmpty()
      }
    }
  }

  fun getCatalogPostsFromCache(
    threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>
  ): LinkedHashMap<ChanDescriptor.ThreadDescriptor, ChanOriginalPost> {
    val resultMap =
      linkedMapWithCap<ChanDescriptor.ThreadDescriptor, ChanOriginalPost>(threadDescriptors.size)

    threadDescriptors.forEach { threadDescriptor ->
      val originalPost = chanThreads[threadDescriptor]?.getOriginalPost()
        ?: return@forEach

      resultMap[threadDescriptor] = originalPost
    }

    return resultMap
  }

  fun getTotalCachedPostsCount(): Int {
    return chanThreads.values.sumBy { chanThread -> chanThread.postsCount }
  }

  fun getLastPost(threadDescriptor: ChanDescriptor.ThreadDescriptor): ChanPost? {
    return chanThreads[threadDescriptor]?.lastPost()
  }

  fun getThreadPosts(threadDescriptor: ChanDescriptor.ThreadDescriptor): List<ChanPost> {
    val chanThread = chanThreads[threadDescriptor]
      ?: return emptyList()

    val resultList = mutableListWithCap<ChanPost>(chanThread.postsCount)

    chanThread.iteratePostsOrdered { chanPost ->
      resultList.add(chanPost)
    }

    return resultList
  }

  fun getThreadPostNoSet(threadDescriptor: ChanDescriptor.ThreadDescriptor): Set<Long> {
    val chanThread = chanThreads[threadDescriptor]
      ?: return emptySet()

    val resultSet = hashSetWithCap<Long>(chanThread.postsCount)

    chanThread.iteratePostsOrdered { chanPost ->
      resultSet.add(chanPost.postDescriptor.postNo)
    }

    return resultSet
  }

  fun getThreadPostsCount(threadDescriptor: ChanDescriptor.ThreadDescriptor): Int {
    return chanThreads[threadDescriptor]?.postsCount ?: 0
  }

  fun updateThreadState(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    deleted: Boolean? = null,
    archived: Boolean? = null,
    closed: Boolean? = null
  ) {
    if (deleted == null && archived == null && closed == null) {
      return
    }

    chanThreads[threadDescriptor]?.updateThreadState(deleted = deleted, archived = archived, closed = closed)
  }

  fun updateLastAccessTime(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    chanThreads[threadDescriptor]?.updateLastAccessTime()
  }

  fun deletePost(postDescriptor: PostDescriptor) {
    deletePosts(listOf(postDescriptor))
  }

  fun deletePosts(postDescriptors: Collection<PostDescriptor>) {
    if (postDescriptors.isEmpty()) {
      return
    }

    if (postDescriptors.size <= 1) {
      val postDescriptor = postDescriptors.first()
      chanThreads[postDescriptor.threadDescriptor()]?.deletePosts(postDescriptors)

      return
    }

    val postsMap = postDescriptors
      .groupBy { postDescriptor -> postDescriptor.threadDescriptor() }

    postsMap.entries.forEach { (threadDescriptor, postDescriptors) ->
      chanThreads[threadDescriptor]?.deletePosts(postDescriptors)
    }
  }

  fun deleteThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    deleteThreads(listOf(threadDescriptor))
  }

  private fun deleteThreads(threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>) {
    threadDescriptors.forEach { threadDescriptor ->
      chanThreads.remove(threadDescriptor)
    }
  }

  fun deleteAll() {
    lastEvictInvokeTime.set(0)
    chanThreads.clear()
  }

  private fun getLastThreadAccessTime(cacheOptions: ChanCacheOptions): Long {
    val now = System.currentTimeMillis()

    if (cacheOptions.canAddInFrontOfTheMemoryCache()) {
      // This will normally add the thread at the beginning of the eviction queue (default behavior).
      return now
    }

    // This will make it so that this thread will be evicted first on the next eviction routine
    // execution. This is needed for thread previewing to work correctly. If the user then opens this
    // thread normally the lastAccessedTime will be set to the current time so the thread will be
    // moved to top of the eviction queue.
    return now - ONE_YEAR_PERIOD_MILLIS
  }

  @OptIn(ExperimentalTime::class)
  private fun runOldPostEvictionRoutineIfNeeded() {
    val delta = System.currentTimeMillis() - lastEvictInvokeTime.get()
    if (delta < EVICTION_TIMEOUT_MS) {
      return
    }

    val currentTotalPostsCount = getTotalCachedPostsCount()
    if (currentTotalPostsCount <= maxCacheSize) {
      return
    }

    val amountOfThreadsWithMoreThanOnPost = getThreadsWithMoreThanOnePostCount()
    if (amountOfThreadsWithMoreThanOnPost <= IMMUNE_THREADS_COUNT) {
      return
    }

    val amountToEvict = (currentTotalPostsCount - maxCacheSize) + (maxCacheSize / 2)
    if (amountToEvict > 0) {
      Logger.d(TAG, "evictOld start " +
        "(currentTotalPostsCount: ${currentTotalPostsCount}/max:${maxCacheSize}, " +
        "threads with posts: ${amountOfThreadsWithMoreThanOnPost}/total threads: ${getCachedThreadsCount()})")

      val time = measureTime { evictOld(amountToEvict) }

      Logger.d(TAG, "evictOld end " +
        "(currentTotalPostsCount: ${getTotalCachedPostsCount()}/max:${maxCacheSize}), " +
        "threads with posts: ${getThreadsWithMoreThanOnePostCount()})/total threads: ${getCachedThreadsCount()}" +
        " took ${time}")
    }

    lastEvictInvokeTime.set(System.currentTimeMillis())
  }

  private fun evictOld(amountToEvictParam: Int) {
    require(amountToEvictParam > 0) { "amountToEvictParam is too small: $amountToEvictParam" }

    val accessTimes = chanThreads.entries
      .map { (threadDescriptor, chanThread) -> threadDescriptor to chanThread.getLastAccessTime() }
    val totalPostsCount = getTotalCachedPostsCount()

    val threadDescriptorsSorted = accessTimes
      // We will get the oldest accessed key in the beginning of the list
      .sortedBy { (_, lastAccessTime) -> lastAccessTime }
      .dropLast(IMMUNE_THREADS_COUNT)
      .map { (threadDescriptor, _) -> threadDescriptor }

    if (threadDescriptorsSorted.isEmpty()) {
      Logger.d(TAG, "threadDescriptorsSorted is empty, accessTimes size=${accessTimes.size}")
      return
    }

    Logger.d(TAG, "threadDescriptorsSorted size=${threadDescriptorsSorted.size}, " +
        "accessTimes size=${accessTimes.size}, " +
        "totalPostsCount=${totalPostsCount}")

    val threadDescriptorsToClean = mutableListOf<ChanDescriptor.ThreadDescriptor>()
    var amountOfPostsToEvict = amountToEvictParam

    for (threadDescriptor in threadDescriptorsSorted) {
      if (amountOfPostsToEvict <= 0) {
        break
      }

      val count = chanThreads[threadDescriptor]?.postsCount ?: 0

      threadDescriptorsToClean += threadDescriptor
      amountOfPostsToEvict -= count
    }

    Logger.d(TAG, "Evicting ${threadDescriptorsToClean.size} threads, " +
        "postsToEvict=${amountToEvictParam - amountOfPostsToEvict}")

    if (threadDescriptorsToClean.isEmpty()) {
      Logger.d(TAG, "threadDescriptorsToDelete is empty")
      return
    }

    threadDescriptorsToClean.forEach { threadDescriptor ->
      val chanThread = chanThreads[threadDescriptor]
        ?: return@forEach

      chanThread.cleanup()

      val isThreadInCurrentCatalog = chanCatalogSnapshotCache.get(chanThread.threadDescriptor.boardDescriptor)
        ?.catalogThreadDescriptorSet
        ?.contains(threadDescriptor)
        ?: false

      if (!isThreadInCurrentCatalog && chanThread.postsCount <= 1) {
        chanThreads.remove(threadDescriptor)
      }
    }
  }

  companion object {
    private const val TAG = "ChanThreadsCache"

    // The freshest N threads that will never have their posts evicted from the cache. Let's say we
    // have 16 threads in the cache and we want to delete such amount of posts that it will delete
    // posts from 10 threads. Without considering the immune threads it will evict posts for 10
    // threads and will leave 6 threads in the cache. But with immune threads it will only evict
    // posts for 6 oldest threads, always leaving the freshest 10 untouched.
    const val IMMUNE_THREADS_COUNT = 8

    // 15 seconds
    private val EVICTION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(15)

    private val ONE_YEAR_PERIOD_MILLIS = Period.years(1).millis
  }
}