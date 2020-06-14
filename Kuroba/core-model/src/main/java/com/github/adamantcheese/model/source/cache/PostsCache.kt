package com.github.adamantcheese.model.source.cache

import androidx.annotation.GuardedBy
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPost
import kotlinx.coroutines.sync.Mutex
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class PostsCache(private val maxValueCount: Int) {
  private val mutex = Mutex()
  private val currentValuesCount = AtomicInteger(0)

  @GuardedBy("mutex")
  private val postsCache = mutableMapOf<ChanDescriptor.ThreadDescriptor, NavigableMap<PostDescriptor, ChanPost>>()

  @GuardedBy("mutex")
  private val originalPostsCache = mutableMapOf<ChanDescriptor.ThreadDescriptor, ChanPost>()

  @GuardedBy("mutex")
  private val accessTimes = mutableMapOf<ChanDescriptor.ThreadDescriptor, Long>()

  suspend fun putIntoCache(postDescriptor: PostDescriptor, post: ChanPost) {
//        mutex.withLock {
//            val threadDescriptor = post.postDescriptor.getThreadDescriptor()
//
//            if (!postsCache.containsKey(threadDescriptor)) {
//                postsCache[threadDescriptor] = TreeMap(POST_COMPARATOR)
//            }
//
//            val count = if (!postsCache[threadDescriptor]!!.containsKey(postDescriptor)) {
//                currentValuesCount.incrementAndGet()
//            } else {
//                currentValuesCount.get()
//            }
//
//            if (count > maxValueCount) {
//                // Evict 1/4 of the cache
//                var amountToEvict = (count / 100) * 25
//                if (amountToEvict >= postsCache.size) {
//                    amountToEvict = postsCache.size - 1
//                }
//
//                if (amountToEvict > 0) {
//                    evictOld(amountToEvict)
//                }
//            }
//
//            if (post.isOp) {
//                originalPostsCache[threadDescriptor] = post
//            }
//
//            accessTimes[threadDescriptor] = System.currentTimeMillis()
//            postsCache[threadDescriptor]!![postDescriptor] = post
//        }
  }

  suspend fun getPostFromCache(postDescriptor: PostDescriptor, isOP: Boolean): ChanPost? {
    return null
//        return mutex.withLock {
//            val threadDescriptor = postDescriptor.getThreadDescriptor()
//            accessTimes[threadDescriptor] = System.currentTimeMillis()
//
//            val post = postsCache[threadDescriptor]?.get(postDescriptor)
//                    ?: return@withLock null
//
//            if (isOP) {
//                val originalPost = requireNotNull(originalPostsCache[threadDescriptor]) {
//                    "Post is OP but it doesn't have it's original post part"
//                }
//
//                return merge(post, originalPost)
//            }
//
//            return@withLock post
//              .apply { isFromCache = true }
//        }
  }

  suspend fun getLatestOriginalPostsFromCache(
    descriptor: ChanDescriptor.CatalogDescriptor,
    count: Int
  ): List<ChanPost> {
    return emptyList()
//        return mutex.withLock {
//            accessTimes.entries
//              .asSequence()
//              .filter { (threadDescriptor, _) -> threadDescriptor.boardDescriptor == descriptor.boardDescriptor }
//              .sortedByDescending { (_, accessTime) -> accessTime }
//              .take(count)
//              .mapNotNull { (threadDescriptor, _) ->  originalPostsCache[threadDescriptor] }
//              .onEach { post -> post.isFromCache = true }
//              .toList()
//        }
  }

  suspend fun getOriginalPostFromCache(threadDescriptor: ChanDescriptor.ThreadDescriptor): ChanPost? {
    return null
//        return mutex.withLock {
//            accessTimes[threadDescriptor] = System.currentTimeMillis()
//
//            val post = postsCache[threadDescriptor]?.values?.firstOrNull { post -> post.isOp }
//                    ?: return@withLock null
//
//            val originalPost = requireNotNull(originalPostsCache[threadDescriptor]) {
//                "Post is OP but it doesn't have it's original post part"
//            }
//
//            return@withLock merge(post, originalPost)
//              ?.apply { isFromCache = true }
//        }
  }

  suspend fun getPostsFromCache(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    postNoSet: Set<Long>
  ): List<ChanPost> {
    return emptyList()
//        return mutex.withLock {
//            accessTimes[threadDescriptor] = System.currentTimeMillis()
//
//            val postsMap = postsCache[threadDescriptor]
//            if (postsMap.isNullOrEmpty()) {
//                return@withLock emptyList()
//            }
//
//            val resultList = mutableListOf<ChanPost>()
//
//            postsMap.keys.forEach { postDescriptor ->
//                if (postDescriptor.postNo in postNoSet) {
//                    val post = requireNotNull(postsMap[postDescriptor]) {
//                        "getPostsFromCache() probably synchronization issue"
//                    }
//
//                    resultList.add(post)
//                }
//            }
//
//            return@withLock resultList
//              .onEach { post -> post.isFromCache = true }
//        }
  }

  suspend fun getLatest(threadDescriptor: ChanDescriptor.ThreadDescriptor, maxCount: Int): List<ChanPost> {
    return emptyList()
//        return mutex.withLock {
//            accessTimes[threadDescriptor] = System.currentTimeMillis()
//
//            val navigableMap = postsCache[threadDescriptor]
//              ?: return@withLock emptyList()
//
//            val navigableKeySet = postsCache[threadDescriptor]?.navigableKeySet()
//              ?: return@withLock emptyList()
//
//            val resultList = mutableListOf<ChanPost>()
//            var count = 0
//
//            for (postDescriptor in navigableKeySet.iterator()) {
//                if (count >= maxCount) {
//                    break
//                }
//
//                ++count
//
//                val chanPost = navigableMap[postDescriptor]
//                  ?: continue
//
//                chanPost.isFromCache = true
//                resultList += chanPost
//            }
//
//            return@withLock resultList
//        }
  }

  suspend fun getAll(threadDescriptor: ChanDescriptor.ThreadDescriptor): List<ChanPost> {
    return emptyList()
//        return mutex.withLock {
//            accessTimes[threadDescriptor] = System.currentTimeMillis()
//            val posts = postsCache[threadDescriptor]?.values?.toList() ?: emptyList()
//
//            return@withLock posts.onEach { post -> post.isFromCache = true }
//        }
  }

  suspend fun getCachedValuesCount(): Int {
    return 0
//        return mutex.withLock {
//            return@withLock currentValuesCount.get()
//        }
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

    keysToEvict.forEach { key ->
      postsCache.remove(key)?.clear()
      accessTimes.remove(key)
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