package com.github.adamantcheese.model.source.cache

import androidx.annotation.GuardedBy
import com.github.adamantcheese.common.MurmurHashUtils
import com.github.adamantcheese.common.mutableMapWithCap
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPost
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class PostsCache(
  private val maxValueCount: Int,
  private val loggerTag: String,
  private val logger: Logger,
) {
  private val tag = "$loggerTag PostsCache"

  private val mutex = Mutex()
  private val currentValuesCount = AtomicInteger(0)

  @GuardedBy("mutex")
  private val postsCache = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, NavigableMap<PostDescriptor, ChanPost>>(128)

  @GuardedBy("mutex")
  private val originalPostsCache = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ChanPost>(128)

  @GuardedBy("mutex")
  private val accessTimes = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, Long>(128)

  @GuardedBy("mutex")
  private val rawPostHashesMap = mutableMapWithCap<PostDescriptor, MurmurHashUtils.Murmur3Hash>(1024)

  private val lastEvictInvokeTime = AtomicLong(0L)

  suspend fun putPostHash(postDescriptor: PostDescriptor, hash: MurmurHashUtils.Murmur3Hash) {
    mutex.withLock { rawPostHashesMap[postDescriptor] = hash }
  }

  suspend fun getPostHash(postDescriptor: PostDescriptor): MurmurHashUtils.Murmur3Hash? {
    return mutex.withLock { rawPostHashesMap[postDescriptor] }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun putIntoCache(post: ChanPost) {
    putManyIntoCache(listOf(post))
  }

  @OptIn(ExperimentalTime::class)
  suspend fun putManyIntoCache(posts: Collection<ChanPost>) {
    if (posts.isEmpty()) {
      return
    }

    mutex.withLock {
      val threadDescriptors = posts.map { post -> post.postDescriptor.threadDescriptor() }.toSet()

      threadDescriptors.forEach { threadDescriptor ->
        if (!postsCache.containsKey(threadDescriptor)) {
          postsCache[threadDescriptor] = TreeMap(POST_COMPARATOR)
        }
      }

      if (currentValuesCount.get() > maxValueCount && accessTimes.size > IMMUNE_THREADS_COUNT) {
        val delta = System.currentTimeMillis() - lastEvictInvokeTime.get()
        if (delta > EVICTION_TIMEOUT_MS) {
          // Evict 35% of the cache
          val amountToEvict = (currentValuesCount.get() / 100) * 35
          if (amountToEvict > 0) {
            logger.log(tag, "evictOld start (posts: ${currentValuesCount.get()}/${maxValueCount})")
            val time = measureTime { evictOld(amountToEvict) }
            logger.log(tag, "evictOld end (posts: ${currentValuesCount.get()}/${maxValueCount}), took ${time}")
          }

          lastEvictInvokeTime.set(System.currentTimeMillis())
        }
      }

      posts.forEach { post ->
        val threadDescriptor = post.postDescriptor.threadDescriptor()
        val postDescriptor = post.postDescriptor

        if (post.isOp) {
          originalPostsCache[threadDescriptor] = post
        }

        if (!postsCache[threadDescriptor]!!.containsKey(postDescriptor)) {
          currentValuesCount.incrementAndGet()
        }

        postsCache[threadDescriptor]!![postDescriptor] = post
        accessTimes[threadDescriptor] = 0L
      }
    }
  }

  suspend fun getPostFromCache(postDescriptor: PostDescriptor, isOP: Boolean): ChanPost? {
    return mutex.withLock {
      val threadDescriptor = postDescriptor.threadDescriptor()
      accessTimes[threadDescriptor] = System.currentTimeMillis()

      val post = postsCache[threadDescriptor]?.get(postDescriptor)
        ?: return@withLock null

      if (isOP) {
        val originalPost = requireNotNull(originalPostsCache[threadDescriptor]) {
          "Post is OP but it doesn't have it's original post part"
        }

        return merge(post, originalPost)
          ?.apply { isFromCache = true }
      }

      return@withLock post
        .apply { isFromCache = true }
    }
  }

  suspend fun getOriginalPostsFromCache(
    threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>
  ): Map<ChanDescriptor.ThreadDescriptor, ChanPost> {
    return mutex.withLock {
      threadDescriptors.forEach { threadDescriptor ->
        accessTimes[threadDescriptor] = System.currentTimeMillis()
      }

      val resultMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ChanPost>(threadDescriptors.size)

      threadDescriptors.forEach { threadDescriptor ->
        val post = postsCache[threadDescriptor]?.values?.firstOrNull { post -> post.isOp }
          ?: return@forEach

        val originalPost = requireNotNull(originalPostsCache[threadDescriptor]) {
          "Post is OP but it doesn't have it's original post part"
        }

        val mergedPost = merge(post, originalPost)
          ?.apply { isFromCache = true }

        if (mergedPost == null) {
          return@forEach
        }

        resultMap[threadDescriptor] = mergedPost
      }

      return@withLock resultMap
    }
  }

  suspend fun getOriginalPostFromCache(threadDescriptor: ChanDescriptor.ThreadDescriptor): ChanPost? {
    return mutex.withLock {
      accessTimes[threadDescriptor] = System.currentTimeMillis()

      val post = postsCache[threadDescriptor]?.values?.firstOrNull { post -> post.isOp }
        ?: return@withLock null

      val originalPost = requireNotNull(originalPostsCache[threadDescriptor]) {
        "Post is OP but it doesn't have it's original post part"
      }

      return@withLock merge(post, originalPost)
        ?.apply { isFromCache = true }
    }
  }

  suspend fun getLatest(threadDescriptor: ChanDescriptor.ThreadDescriptor, maxCount: Int): List<ChanPost> {
    return mutex.withLock {
      accessTimes[threadDescriptor] = System.currentTimeMillis()

      val navigableMap = postsCache[threadDescriptor]
        ?: return@withLock emptyList()

      val navigableKeySet = postsCache[threadDescriptor]?.navigableKeySet()
        ?: return@withLock emptyList()

      val resultList = mutableListOf<ChanPost>()
      var count = 0

      for (postDescriptor in navigableKeySet.iterator()) {
        if (count >= maxCount) {
          break
        }

        ++count

        val chanPost = navigableMap[postDescriptor]
          ?: continue

        chanPost.isFromCache = true
        resultList += chanPost
      }

      return@withLock resultList
    }
  }

  suspend fun getTotalCachedPostsCount(): Int {
    return mutex.withLock {
      return@withLock currentValuesCount.get()
    }
  }

  suspend fun deletePost(postDescriptor: PostDescriptor) {
    mutex.withLock {
      val threadDescriptor = postDescriptor.threadDescriptor()

      val contains = postsCache[threadDescriptor]?.containsKey(postDescriptor) ?: false
      if (contains) {
        currentValuesCount.decrementAndGet()
      }

      postsCache[threadDescriptor]?.remove(postDescriptor)

      if (postDescriptor.isOP()) {
        originalPostsCache.remove(threadDescriptor)
      }

      rawPostHashesMap.remove(postDescriptor)
      accessTimes[threadDescriptor] = System.currentTimeMillis()
    }
  }

  suspend fun deleteThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    deleteThreads(listOf(threadDescriptor))
  }

  suspend fun deleteThreads(threadDescriptors: List<ChanDescriptor.ThreadDescriptor>) {
    mutex.withLock {
      threadDescriptors.forEach { threadDescriptor ->
        val size = postsCache[threadDescriptor]?.size ?: -1
        if (size > 0) {
          currentValuesCount.addAndGet(-size)
        }

        postsCache.remove(threadDescriptor)?.let { map ->
          map.keys.forEach { postDescriptor ->
            rawPostHashesMap.remove(postDescriptor)
          }
        }

        originalPostsCache.remove(threadDescriptor)
        accessTimes.remove(threadDescriptor)
      }
    }
  }

  suspend fun deleteAll() {
    mutex.withLock {
      currentValuesCount.set(0)
      rawPostHashesMap.clear()
      postsCache.clear()
      originalPostsCache.clear()
      accessTimes.clear()
    }
  }

  suspend fun getLatestOriginalPostsFromCache(
    descriptor: ChanDescriptor.CatalogDescriptor,
    count: Int
  ): List<ChanPost> {
    return mutex.withLock {
      accessTimes.entries
        .asSequence()
        .filter { (threadDescriptor, _) -> threadDescriptor.boardDescriptor == descriptor.boardDescriptor }
        .sortedByDescending { (_, accessTime) -> accessTime }
        .take(count)
        .mapNotNull { (threadDescriptor, _) -> originalPostsCache[threadDescriptor] }
        .onEach { post -> post.isFromCache = true }
        .toList()
    }
  }

  suspend fun getPostsFromCache(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    postNoSet: Set<Long>
  ): List<ChanPost> {
    return mutex.withLock {
      accessTimes[threadDescriptor] = System.currentTimeMillis()

      val postsMap = postsCache[threadDescriptor]
      if (postsMap.isNullOrEmpty()) {
        return@withLock emptyList()
      }

      val resultList = mutableListOf<ChanPost>()

      postsMap.keys.forEach { postDescriptor ->
        if (postDescriptor.postNo in postNoSet) {
          val post = requireNotNull(postsMap[postDescriptor]) {
            "getPostsFromCache() probably synchronization issue"
          }

          resultList.add(post)
        }
      }

      return@withLock resultList
        .onEach { post -> post.isFromCache = true }
    }
  }

  suspend fun getAll(threadDescriptor: ChanDescriptor.ThreadDescriptor, maxCount: Int): List<ChanPost> {
    return mutex.withLock {
      accessTimes[threadDescriptor] = System.currentTimeMillis()

      val posts = postsCache[threadDescriptor]?.values?.toList()?.takeLast(maxCount)
        ?: emptyList()

      return@withLock posts
        .onEach { post -> post.isFromCache = true }
    }
  }

  suspend fun getAllPostNoSet(threadDescriptor: ChanDescriptor.ThreadDescriptor, maxCount: Int): Set<Long> {
    return mutex.withLock {
      accessTimes[threadDescriptor] = System.currentTimeMillis()

      return@withLock postsCache[threadDescriptor]?.values
        ?.map { it.postDescriptor.postNo }
        ?.takeLast(maxCount)
        ?.toSet()
        ?: emptySet()
    }
  }

  private fun evictOld(amountToEvictParam: Int) {
    require(amountToEvictParam > 0) { "amountToEvictParam is too small: $amountToEvictParam" }
    require(mutex.isLocked) { "mutex must be locked!" }

    val keysSorted = accessTimes.entries
      // We will get the oldest accessed key in the beginning of the list
      .sortedBy { (_, lastAccessTime) -> lastAccessTime }
      .dropLast(IMMUNE_THREADS_COUNT)
      .map { (key, _) -> key }

    if (keysSorted.isEmpty()) {
      logger.log(tag, "keysSorted is empty, accessTimes size=${accessTimes.size}")
      return
    }

    logger.log(tag, "keysSorted size=${keysSorted.size}, " +
      "accessTimes size=${accessTimes.size}, " +
      "currentValuesCount=${currentValuesCount.get()}")

    val keysToEvict = mutableListOf<ChanDescriptor.ThreadDescriptor>()
    var amountOfPostsToEvict = amountToEvictParam

    for (key in keysSorted) {
      if (amountOfPostsToEvict <= 0) {
        break
      }

      val count = postsCache[key]?.size ?: 0

      keysToEvict += key
      amountOfPostsToEvict -= count
      currentValuesCount.addAndGet(-count)
    }

    logger.log(tag, "Evicting ${keysToEvict.size} threads, postsToEvict=${amountToEvictParam - amountOfPostsToEvict}")

    if (currentValuesCount.get() < 0) {
      currentValuesCount.set(0)
    }

    if (keysToEvict.isEmpty()) {
      logger.log(tag, "keysToEvict is empty")
      return
    }

    keysToEvict.forEach { threadDescriptor ->
      postsCache[threadDescriptor]?.let { map ->
        map.keys.forEach { postDescriptor ->
          rawPostHashesMap.remove(postDescriptor)
        }
      }

      originalPostsCache.remove(threadDescriptor)
      postsCache.remove(threadDescriptor)?.clear()
      accessTimes.remove(threadDescriptor)
    }
  }

  private fun merge(post: ChanPost, originalPost: ChanPost): ChanPost? {
    require(originalPost.isOp) { "originalPost is not OP" }
    require(post.isOp) { "post is not OP" }
    require(originalPost.postDescriptor == post.postDescriptor) {
      "post descriptor differ (${originalPost.postDescriptor}, ${post.postDescriptor})"
    }

    return ChanPost(
      chanPostId = post.chanPostId,
      postDescriptor = post.postDescriptor,
      postImages = post.postImages,
      postIcons = post.postIcons,
      replies = originalPost.replies,
      threadImagesCount = originalPost.threadImagesCount,
      uniqueIps = originalPost.uniqueIps,
      lastModified = originalPost.lastModified,
      sticky = originalPost.sticky,
      closed = originalPost.closed,
      archived = originalPost.archived,
      timestamp = post.timestamp,
      name = post.name,
      postComment = post.postComment,
      subject = post.subject,
      tripcode = post.tripcode,
      posterId = post.posterId,
      moderatorCapcode = post.moderatorCapcode,
      isOp = post.isOp,
      isSavedReply = post.isSavedReply
    )
  }

  companion object {
    // The freshest N threads that will never have their posts evicted from the cache. Let's say we
    // have 16 threads in the cache and we want to delete such amount of posts that it will delete
    // posts from 10 threads. Without considering the immune threads it will evict posts for 10
    // threads and will leave 6 threads in the cache. But with immune threads it will only evict
    // posts for 6 oldest threads, always leaving the freshest 10 untouched.
    private const val IMMUNE_THREADS_COUNT = 10

    // 1 minute
    private val EVICTION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(1)

    private val POST_COMPARATOR = kotlin.Comparator<PostDescriptor> { desc1, desc2 ->
      return@Comparator desc1.postNo.compareTo(desc2.postNo)
    }
  }
}