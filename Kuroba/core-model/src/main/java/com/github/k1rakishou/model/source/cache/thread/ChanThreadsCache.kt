package com.github.k1rakishou.model.source.cache.thread

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.linkedMapWithCap
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.options.ChanCacheOptions
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.catalog.ChanCatalog
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.thread.ChanThread
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache
import com.github.k1rakishou.model.util.ensureBackgroundThread
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ChanThreadsCache(
  private val isDevBuild: Boolean,
  private val maxCacheSize: Int,
  private val chanCatalogSnapshotCache: ChanCatalogSnapshotCache
) {
  private val tag = "PostsCache"

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val chanThreads = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ChanThread>(128)
  @GuardedBy("lock")
  private val rawPostHashesMap = mutableMapWithCap<PostDescriptor, MurmurHashUtils.Murmur3Hash>(1024)

  private val lastEvictInvokeTime = AtomicLong(0L)

  fun putPostHash(postDescriptor: PostDescriptor, hash: MurmurHashUtils.Murmur3Hash) {
    lock.write { rawPostHashesMap[postDescriptor] = hash }
  }

  fun getPostHash(postDescriptor: PostDescriptor): MurmurHashUtils.Murmur3Hash? {
    return lock.read { rawPostHashesMap[postDescriptor] }
  }

  fun putManyCatalogPostsIntoCache(originalPosts: List<ChanOriginalPost>) {
    if (originalPosts.isEmpty()) {
      return
    }

    lock.write {
      runOldPostEvictionRoutineIfNeeded()

      originalPosts.forEach { chanOriginalPost ->
        val threadDescriptor = chanOriginalPost.postDescriptor.threadDescriptor()

        if (!chanThreads.containsKey(threadDescriptor)) {
          chanThreads[threadDescriptor] = ChanThread(isDevBuild, threadDescriptor)
        }

        chanThreads[threadDescriptor]?.setOrUpdateOriginalPost(chanOriginalPost)
      }
    }
  }

  fun putManyThreadPostsIntoCache(posts: List<ChanPost>, cacheOptions: ChanCacheOptions) {
    // We are doing some kinda heavy stuff (reply calculations) so we want this method to always be
    //  called on a background thread.
    ensureBackgroundThread()

    if (posts.isEmpty()) {
      return
    }

    val originalPost = posts.first()
    require(originalPost is ChanOriginalPost) { "First post is not a original post: ${posts.first()}" }

    if (isDevBuild) {
      val distinctByChanDescriptor = posts
        .map { chanPost -> chanPost.postDescriptor.descriptor }
        .toSet()

      if (distinctByChanDescriptor.size != 1) {
        throw IllegalStateException(
          "The input posts list contains posts from different threads! " +
            "posts: $posts"
        )
      }
    }

    lock.write {
      runOldPostEvictionRoutineIfNeeded()
      val threadDescriptor = originalPost.postDescriptor.descriptor as ChanDescriptor.ThreadDescriptor

      if (!chanThreads.containsKey(threadDescriptor)) {
        chanThreads[threadDescriptor] = ChanThread(isDevBuild, threadDescriptor)
      }

      if (cacheOptions.canStoreInMemory()) {
        chanThreads[threadDescriptor]!!.addOrUpdatePosts(posts)
      } else {
        chanThreads[threadDescriptor]!!.setOrUpdateOriginalPost(originalPost)
      }
    }
  }

  fun getOriginalPostFromCache(postDescriptor: PostDescriptor): ChanOriginalPost? {
    return lock.read {
      val threadDescriptor = postDescriptor.threadDescriptor()
      return@read chanThreads[threadDescriptor]?.getOriginalPost()
    }
  }

  fun getOriginalPostFromCache(threadDescriptor: ChanDescriptor.ThreadDescriptor): ChanOriginalPost? {
    return lock.read { chanThreads[threadDescriptor]?.getOriginalPost() }
  }

  fun getPostFromCache(chanDescriptor: ChanDescriptor, postNo: Long): ChanPost? {
    return lock.read {
      when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> {
          val postDescriptor = PostDescriptor.create(chanDescriptor, postNo)

          return@read chanThreads[chanDescriptor]?.getPost(postDescriptor)
        }
        is ChanDescriptor.CatalogDescriptor -> {
          val threadDescriptors = chanCatalogSnapshotCache.get(chanDescriptor.boardDescriptor)
            ?.catalogThreadDescriptors
            ?: return@read null

          val threadDescriptor = threadDescriptors
            .firstOrNull { threadDescriptor -> threadDescriptor.threadNo == postNo }
            ?: return@read null

          return@read chanThreads[threadDescriptor]?.getOriginalPost()
        }
      }
    }
  }

  fun getPostFromCache(postDescriptor: PostDescriptor): ChanPost? {
    return lock.read {
      val threadDescriptor = postDescriptor.threadDescriptor()
      return@read chanThreads[threadDescriptor]?.getPost(postDescriptor)
    }
  }

  fun getCatalog(catalogDescriptor: ChanDescriptor.CatalogDescriptor): ChanCatalog? {
    return lock.read {
      val threadDescriptors = chanCatalogSnapshotCache.get(catalogDescriptor.boardDescriptor)
        ?.catalogThreadDescriptors
        ?: return@read null

      val posts =  threadDescriptors
        .mapNotNull { threadDescriptor -> chanThreads[threadDescriptor]?.getOriginalPost() }

      return ChanCatalog(catalogDescriptor, posts)
    }
  }

  fun getThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ChanThread? {
    return lock.read { chanThreads[threadDescriptor] }
  }

  fun contains(chanDescriptor: ChanDescriptor): Boolean {
    return lock.read {
      when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> {
          return@read chanThreads[chanDescriptor]?.hasAtLeastOnePost() ?: false
        }
        is ChanDescriptor.CatalogDescriptor -> {
          val catalogThreadDescriptors = chanCatalogSnapshotCache.get(chanDescriptor.boardDescriptor)
            ?.catalogThreadDescriptors
            ?: return@read false

          return@read catalogThreadDescriptors.any { threadDescriptor ->
            chanThreads[threadDescriptor]?.hasAtLeastOnePost() ?: false
          }
        }
      }
    }
  }

  fun getCatalogPostsFromCache(
    threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>
  ): LinkedHashMap<ChanDescriptor.ThreadDescriptor, ChanOriginalPost> {
    return lock.write {
      val resultMap =
        linkedMapWithCap<ChanDescriptor.ThreadDescriptor, ChanOriginalPost>(threadDescriptors.size)

      threadDescriptors.forEach { threadDescriptor ->
        val originalPost = chanThreads[threadDescriptor]?.getOriginalPost()
          ?: return@forEach

        resultMap[threadDescriptor] = originalPost
      }

      return@write resultMap
    }
  }

  fun getTotalCachedPostsCount(): Int {
    return lock.read { chanThreads.values.sumBy { chanThread -> chanThread.postsCount } }
  }

  fun getLastPost(threadDescriptor: ChanDescriptor.ThreadDescriptor): ChanPost? {
    return lock.read { chanThreads[threadDescriptor]?.lastPost() }
  }

  fun getThreadPosts(threadDescriptor: ChanDescriptor.ThreadDescriptor): List<ChanPost> {
    return lock.write {
      val chanThread = chanThreads[threadDescriptor]
        ?: return@write emptyList()

      val resultList = mutableListWithCap<ChanPost>(chanThread.postsCount)

      chanThread.iteratePostsOrdered { chanPost ->
        resultList.add(chanPost)
      }

      return@write resultList
    }
  }

  fun getThreadPostNoSet(threadDescriptor: ChanDescriptor.ThreadDescriptor): Set<Long> {
    return lock.write {
      val chanThread = chanThreads[threadDescriptor]
        ?: return@write emptySet()

      val resultSet = hashSetWithCap<Long>(chanThread.postsCount)

      chanThread.iteratePostsOrdered { chanPost ->
        resultSet.add(chanPost.postDescriptor.postNo)
      }

      return@write resultSet
    }
  }

  fun getThreadPostsCount(threadDescriptor: ChanDescriptor.ThreadDescriptor): Int {
    return lock.read { chanThreads[threadDescriptor]?.postsCount ?: 0 }
  }

  fun markThreadAsDeleted(threadDescriptor: ChanDescriptor.ThreadDescriptor, deleted: Boolean) {
    lock.write {
      chanThreads[threadDescriptor]?.setDeleted(deleted)
    }
  }

  fun deletePost(postDescriptor: PostDescriptor) {
    lock.write {
      val threadDescriptor = postDescriptor.threadDescriptor()

      val chanThread = chanThreads[threadDescriptor]
      if (chanThread != null) {
        chanThread.deletePost(postDescriptor)
      }

      rawPostHashesMap.remove(postDescriptor)
    }
  }

  fun deleteThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    deleteThreads(listOf(threadDescriptor))
  }

  fun deleteThreads(threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>) {
    lock.write {
      threadDescriptors.forEach { threadDescriptor ->
        chanThreads[threadDescriptor]?.iteratePostsOrdered { chanPost ->
          rawPostHashesMap.remove(chanPost.postDescriptor)
        }

        chanThreads.remove(threadDescriptor)
      }
    }
  }

  fun deleteAll() {
    lock.write {
      lastEvictInvokeTime.set(0)
      rawPostHashesMap.clear()
      chanThreads.clear()
      rawPostHashesMap.clear()
    }
  }

  @OptIn(ExperimentalTime::class)
  private fun runOldPostEvictionRoutineIfNeeded() {
    require(lock.isWriteLocked) { "Lock must be write locked!" }

    val delta = System.currentTimeMillis() - lastEvictInvokeTime.get()
    if (delta > EVICTION_TIMEOUT_MS) {
      val currentTotalPostsCount = getTotalCachedPostsCount()
      if (currentTotalPostsCount > maxCacheSize && chanThreads.size > IMMUNE_THREADS_COUNT) {
        // Evict 35% of the cache
        val amountToEvict = (currentTotalPostsCount / 100) * 35
        if (amountToEvict > 0) {
          Logger.d(tag, "evictOld start (posts: ${currentTotalPostsCount}/${maxCacheSize})")
          val time = measureTime { evictOld(amountToEvict) }
          Logger.d(
            tag,
            "evictOld end (posts: ${currentTotalPostsCount}/${maxCacheSize}), took ${time}"
          )
        }

        lastEvictInvokeTime.set(System.currentTimeMillis())
      }
    }
  }

  private fun evictOld(amountToEvictParam: Int) {
    require(amountToEvictParam > 0) { "amountToEvictParam is too small: $amountToEvictParam" }
    require(lock.isWriteLocked) { "mutex must be write locked!" }

    val accessTimes = chanThreads.entries
      .map { (threadDescriptor, chanThread) -> threadDescriptor to chanThread.getLastAccessTime() }
    val totalPostsCount = getTotalCachedPostsCount()

    val keysSorted = accessTimes
      // We will get the oldest accessed key in the beginning of the list
      .sortedBy { (_, lastAccessTime) -> lastAccessTime }
      .dropLast(IMMUNE_THREADS_COUNT)
      .map { (key, _) -> key }

    if (keysSorted.isEmpty()) {
      Logger.d(tag, "keysSorted is empty, accessTimes size=${accessTimes.size}")
      return
    }

    Logger.d(
      tag, "keysSorted size=${keysSorted.size}, " +
        "accessTimes size=${accessTimes.size}, " +
        "totalPostsCount=${totalPostsCount}"
    )

    val threadDescriptorsToDelete = mutableListOf<ChanDescriptor.ThreadDescriptor>()
    var amountOfPostsToEvict = amountToEvictParam

    for (key in keysSorted) {
      if (amountOfPostsToEvict <= 0) {
        break
      }

      val count = chanThreads[key]?.postsCount ?: 0

      threadDescriptorsToDelete += key
      amountOfPostsToEvict -= count
    }

    Logger.d(
      tag, "Evicting ${threadDescriptorsToDelete.size} threads, " +
        "postsToEvict=${amountToEvictParam - amountOfPostsToEvict}"
    )

    if (threadDescriptorsToDelete.isEmpty()) {
      Logger.d(tag, "threadDescriptorsToDelete is empty")
      return
    }

    deleteThreads(threadDescriptorsToDelete)
  }

  companion object {
    // The freshest N threads that will never have their posts evicted from the cache. Let's say we
    // have 16 threads in the cache and we want to delete such amount of posts that it will delete
    // posts from 10 threads. Without considering the immune threads it will evict posts for 10
    // threads and will leave 6 threads in the cache. But with immune threads it will only evict
    // posts for 6 oldest threads, always leaving the freshest 10 untouched.
    private const val IMMUNE_THREADS_COUNT = 25
    private const val DEFAULT_CATALOG_THREADS_COUNT = 150

    // 1 minute
    private val EVICTION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(1)
  }
}