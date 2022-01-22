package com.github.k1rakishou.model.source.cache.thread

import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.linkedMapWithCap
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.PostsFromServerData
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ChanThreadsCache(
  private val isDevBuild: Boolean,
  private val isLowRamDevice: Boolean,
  private val maxCacheSize: Int,
  private val chanCatalogSnapshotCache: ChanCatalogSnapshotCache
) {
  private val chanThreads = ConcurrentHashMap<ChanDescriptor.ThreadDescriptor, ChanThread>(128)
  private val lastEvictInvokeTime = AtomicLong(0L)
  private val oneYearPeriodMillis by lazy { Period.years(1).millis }
  private val chanThreadDeleteEventListeners = CopyOnWriteArrayList<(ThreadDeleteEvent) -> Unit>()

  fun addChanThreadDeleteEventListener(listener: (ThreadDeleteEvent) -> Unit) {
    chanThreadDeleteEventListeners += listener
  }

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

  fun cacheNeedsUpdate(
    chanDescriptor: ChanDescriptor,
    chanCacheUpdateOptions: ChanCacheUpdateOptions
  ): Boolean {
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
    }
  }

  fun putManyThreadPostsIntoCache(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    parsedPosts: List<ChanPost>,
    cacheOptions: ChanCacheOptions,
    chanCacheUpdateOptions: ChanCacheUpdateOptions,
    postsFromServerData: PostsFromServerData?
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
      chanThread.addOrUpdatePosts(parsedPosts, postsFromServerData)
    } else if (firstPost != null && firstPost is ChanOriginalPost) {
      chanThread.setOrUpdateOriginalPost(firstPost)
    }

    chanThread.updateLastUpdateTime(chanCacheUpdateOptions)
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

  fun getPostFromCache(chanDescriptor: ChanDescriptor, postDescriptor: PostDescriptor): ChanPost? {
    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        return chanThreads[chanDescriptor]?.getPost(postDescriptor)
      }
      is ChanDescriptor.ICatalogDescriptor -> {
        val threadDescriptor = chanCatalogSnapshotCache.get(chanDescriptor)
          ?.catalogThreadDescriptorList
          ?.firstOrNull { threadDescriptor -> threadDescriptor.threadNo == postDescriptor.getThreadNo() }
          ?: return null

        return chanThreads[threadDescriptor]?.getOriginalPost()
      }
    }
  }

  fun getPostFromCache(postDescriptor: PostDescriptor): ChanPost? {
    val threadDescriptor = postDescriptor.threadDescriptor()
    return chanThreads[threadDescriptor]?.getPost(postDescriptor)
  }

  fun getCatalogThreadDescriptors(
    catalogDescriptor: ChanDescriptor.ICatalogDescriptor
  ): List<ChanDescriptor.ThreadDescriptor> {
    return chanCatalogSnapshotCache.get(catalogDescriptor)
      ?.catalogThreadDescriptorList
      ?: emptyList()
  }

  fun getCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor): ChanCatalog? {
    val threadDescriptors = chanCatalogSnapshotCache.get(catalogDescriptor)
      ?.catalogThreadDescriptorList
      ?: return null

    val posts = threadDescriptors
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
      is ChanDescriptor.ICatalogDescriptor -> {
        val catalogThreadDescriptorList = chanCatalogSnapshotCache.get(chanDescriptor)
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

    chanThreads[threadDescriptor]?.updateThreadState(
      deleted = deleted,
      archived = archived,
      closed = closed
    )
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
    if (threadDescriptors.isEmpty()) {
      return
    }

    val entries = mutableListWithCap<ThreadDeleteEvent.RemoveThreadPostsExceptOP.Entry>(threadDescriptors.size)

    threadDescriptors.forEach { threadDescriptor ->
      val chanThread = chanThreads.remove(threadDescriptor)
        ?: return@forEach

      val originalPostDescriptor = chanThread.getOriginalPost()?.postDescriptor
        ?: return@forEach

      entries += ThreadDeleteEvent.RemoveThreadPostsExceptOP.Entry(
        threadDescriptor = threadDescriptor,
        originalPostDescriptor = originalPostDescriptor
      )
    }

    val event = ThreadDeleteEvent.RemoveThreadPostsExceptOP(
      evictingOld = false,
      entries = entries
    )

    notifyChanThreadDeleteEventListeners(event)
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
    return now - oneYearPeriodMillis
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
    val actualImmuneThreadsCount = immuneThreadsCount(isLowRamDevice)

    if (amountOfThreadsWithMoreThanOnPost <= actualImmuneThreadsCount) {
      return
    }

    val amountToEvict = (currentTotalPostsCount - maxCacheSize) + (maxCacheSize / 2)
    if (amountToEvict > 0) {
      Logger.d(TAG, "evictOld start (immuneThreadsCount=${actualImmuneThreadsCount}, " +
        "currentTotalPostsCount: ${currentTotalPostsCount} / max:${maxCacheSize}, " +
        "threads with posts: ${amountOfThreadsWithMoreThanOnPost} / total threads: ${getCachedThreadsCount()})")

      val time = measureTime {
        evictOld(immuneThreadsCount = actualImmuneThreadsCount, amountToEvictParam = amountToEvict)
      }

      Logger.d(TAG, "evictOld end (immuneThreadsCount=${actualImmuneThreadsCount}, " +
        "currentTotalPostsCount: ${getTotalCachedPostsCount()} / max:${maxCacheSize}), " +
        "threads with posts: ${getThreadsWithMoreThanOnePostCount()}) / total threads: ${getCachedThreadsCount()} " +
        "took ${time}")
    }

    lastEvictInvokeTime.set(System.currentTimeMillis())
  }

  private fun evictOld(immuneThreadsCount: Int, amountToEvictParam: Int) {
    require(amountToEvictParam > 0) { "amountToEvictParam is too small: $amountToEvictParam" }

    val accessTimes = chanThreads.entries
      .map { (threadDescriptor, chanThread) -> threadDescriptor to chanThread.getLastAccessTime() }
    val totalPostsCount = getTotalCachedPostsCount()

    val threadDescriptorsSorted = accessTimes
      // We will get the oldest accessed key in the beginning of the list
      .sortedBy { (_, lastAccessTime) -> lastAccessTime }
      .dropLast(immuneThreadsCount)
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

    val threadsToRemove = mutableListWithCap<ChanDescriptor.ThreadDescriptor>(16)
    val threadsToClean = mutableListWithCap<ThreadDeleteEvent.RemoveThreadPostsExceptOP.Entry>(16)

    threadDescriptorsToClean.forEach { threadDescriptor ->
      val chanThread = chanThreads[threadDescriptor]
        ?: return@forEach

      chanThread.cleanup()

      val isThreadInCurrentCatalog = chanCatalogSnapshotCache.get(chanThread.threadDescriptor.catalogDescriptor())
        ?.catalogThreadDescriptorSet
        ?.contains(threadDescriptor)
        ?: false

      if (!isThreadInCurrentCatalog && chanThread.postsCount <= 1) {
        threadsToRemove += threadDescriptor
        chanThreads.remove(threadDescriptor)
      } else {
        val originalPostDescriptor = chanThread.getOriginalPost()?.postDescriptor

        if (originalPostDescriptor != null) {
          threadsToClean += ThreadDeleteEvent.RemoveThreadPostsExceptOP.Entry(
            threadDescriptor,
            originalPostDescriptor
          )
        }
      }
    }

    Logger.d(TAG, "evictOld() threadsToRemove=${threadsToRemove.size}, threadsToClean=${threadsToClean.size}")

    if (threadsToRemove.isNotEmpty()) {
      val event = ThreadDeleteEvent.RemoveThreads(
        evictingOld = true,
        threadDescriptors = threadsToRemove
      )

      notifyChanThreadDeleteEventListeners(event)
    }

    if (threadsToClean.isNotEmpty()) {
      val event = ThreadDeleteEvent.RemoveThreadPostsExceptOP(
        evictingOld = true,
        entries = threadsToClean
      )

      notifyChanThreadDeleteEventListeners(event)
    }
  }

  private fun notifyChanThreadDeleteEventListeners(threadDeleteEvent: ThreadDeleteEvent) {
    chanThreadDeleteEventListeners.forEach { listener ->
      listener.invoke(threadDeleteEvent)
    }
  }

  sealed class ThreadDeleteEvent(val evictingOld: Boolean) {

    class RemoveThreads(
      evictingOld: Boolean,
      val threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>
    ) : ThreadDeleteEvent(evictingOld) {

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RemoveThreads

        if (threadDescriptors != other.threadDescriptors) return false

        return true
      }

      override fun hashCode(): Int {
        return threadDescriptors.hashCode()
      }

      override fun toString(): String {
        return "RemoveThreads(threadDescriptors=$threadDescriptors)"
      }

    }

    class RemoveThreadPostsExceptOP(
      evictingOld: Boolean,
      val entries: Collection<Entry>,
    ) : ThreadDeleteEvent(evictingOld) {

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RemoveThreadPostsExceptOP

        if (entries != other.entries) return false

        return true
      }

      override fun hashCode(): Int {
        return entries.hashCode()
      }

      override fun toString(): String {
        return "RemoveThreadPostsExceptOP(entries=$entries)"
      }

      data class Entry(
        val threadDescriptor: ChanDescriptor.ThreadDescriptor,
        val originalPostDescriptor: PostDescriptor
      )

    }

  }

  companion object {
    private const val TAG = "ChanThreadsCache"

    // The freshest N threads that will never have their posts evicted from the cache. Let's say we
    // have 16 threads in the cache and we want to delete such amount of posts that it will delete
    // posts from 10 threads. Without considering the immune threads it will evict posts for 10
    // threads and will leave 6 threads in the cache. But with immune threads it will only evict
    // posts for 6 oldest threads, always leaving the freshest 10 untouched.
    private const val IMMUNE_THREADS_COUNT = 8
    private const val IMMUNE_THREADS_LOW_RAM_COUNT = 5

    // 15 seconds
    private val EVICTION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(15)

    fun immuneThreadsCount(isLowRamDevice: Boolean): Int {
      return if (isLowRamDevice) {
        IMMUNE_THREADS_LOW_RAM_COUNT
      } else {
        IMMUNE_THREADS_COUNT
      }
    }
  }
}