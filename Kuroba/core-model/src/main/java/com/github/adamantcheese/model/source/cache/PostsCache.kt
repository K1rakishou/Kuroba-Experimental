package com.github.adamantcheese.model.source.cache

import androidx.annotation.GuardedBy
import com.github.adamantcheese.common.MurmurHashUtils
import com.github.adamantcheese.common.mutableMapWithCap
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPost
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class PostsCache(private val maxValueCount: Int) {
  private val mutex = Mutex()
  private val currentValuesCount = AtomicInteger(0)

  @GuardedBy("mutex")
  private val postsCache = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, NavigableMap<PostDescriptor, ChanPost>>(128)

  @GuardedBy("mutex")
  private val originalPostsCache = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ChanPost>(128)

  @GuardedBy("mutex")
  private val accessTimes = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, Long>(128)

  @GuardedBy("mutes")
  private val rawPostHashesMap = mutableMapWithCap<PostDescriptor, MurmurHashUtils.Murmur3Hash>(1024)

  suspend fun putIntoCache(postDescriptor: PostDescriptor, post: ChanPost) {
    mutex.withLock {
      val threadDescriptor = post.postDescriptor.threadDescriptor()

      if (!postsCache.containsKey(threadDescriptor)) {
        postsCache[threadDescriptor] = TreeMap(POST_COMPARATOR)
      }

      val count = if (!postsCache[threadDescriptor]!!.containsKey(postDescriptor)) {
        currentValuesCount.incrementAndGet()
      } else {
        currentValuesCount.get()
      }

      if (count > maxValueCount) {
        // Evict 35% of the cache
        var amountToEvict = (count / 100) * 35
        if (amountToEvict >= postsCache.size) {
          amountToEvict = postsCache.size - 1
        }

        if (amountToEvict > 0) {
          evictOld(amountToEvict)
        }
      }

      if (post.isOp) {
        originalPostsCache[threadDescriptor] = post
      }

      accessTimes[threadDescriptor] = System.currentTimeMillis()
      postsCache[threadDescriptor]!![postDescriptor] = post
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

  suspend fun putPostHash(postDescriptor: PostDescriptor, hash: MurmurHashUtils.Murmur3Hash) {
    mutex.withLock { rawPostHashesMap[postDescriptor] = hash }
  }

  suspend fun getPostHash(postDescriptor: PostDescriptor): MurmurHashUtils.Murmur3Hash? {
    return mutex.withLock { rawPostHashesMap[postDescriptor] }
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
    mutex.withLock {
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

  suspend fun getAll(threadDescriptor: ChanDescriptor.ThreadDescriptor): List<ChanPost> {
    return mutex.withLock {
      accessTimes[threadDescriptor] = System.currentTimeMillis()
      val posts = postsCache[threadDescriptor]?.values?.toList() ?: emptyList()

      return@withLock posts.onEach { post -> post.isFromCache = true }
    }
  }

  private fun evictOld(amountToEvictParam: Int) {
    require(amountToEvictParam > 0) { "amountToEvictParam is too small: $amountToEvictParam" }
    require(mutex.isLocked) { "mutex must be locked!" }

    val keysSorted = accessTimes.entries
      // We will get the latest accessed key in the beginning of the list
      .sortedBy { (_, lastAccessTime) -> lastAccessTime }
      .map { (key, _) -> key }

    val keysToEvict = mutableListOf<ChanDescriptor.ThreadDescriptor>()
    var amountToEvict = amountToEvictParam

    for (key in keysSorted) {
      if (amountToEvict <= 0) {
        break
      }

      // TODO(KurobaEx): do not evict posts for the freshest N threads (let's say 10). We need
      //  to do this to avoid a situation where there is a a couple of really huge threads
      //  (10k+ replies) and the user constantly switches between them. So to avoid constantly
      //  removing/reloading posts from/into cache we need to skip those threads. In other words,
      //  we need to make 10 freshest threads immune to evict procedure.
      val count = postsCache[key]?.size ?: 0

      keysToEvict += key
      amountToEvict -= count
      currentValuesCount.addAndGet(-count)
    }

    if (currentValuesCount.get() < 0) {
      currentValuesCount.set(0)
    }

    if (keysToEvict.isEmpty()) {
      return
    }

    keysToEvict.forEach { threadDescriptor ->
      postsCache[threadDescriptor]?.let { map ->
        map.keys.forEach { postDescriptor ->
          rawPostHashesMap.remove(postDescriptor)
        }
      }

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
    private val POST_COMPARATOR = kotlin.Comparator<PostDescriptor> { desc1, desc2 ->
      return@Comparator desc1.postNo.compareTo(desc2.postNo)
    }
  }
}