package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.parser.MarkedPost
import com.github.k1rakishou.chan.core.parser.MarkedPostType
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface MarkedPostManager {
  val markedPostsUpdateFlow: SharedFlow<MarkedPostUpdate>

  suspend fun insert(threadDescriptor: ChanDescriptor.ThreadDescriptor, markedPosts: Collection<MarkedPost>)
  suspend fun getMarkedPosts(postDescriptor: PostDescriptor): Set<MarkedPost>
  suspend fun getManyMarkedPosts(postDescriptorSet: Set<PostDescriptor>): Map<PostDescriptor, Set<MarkedPost>>
  suspend fun isAlreadyLoadedForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): Boolean
  suspend fun isPostMarkedAsMine(postDescriptor: PostDescriptor): Boolean
  suspend fun markPost(postDescriptor: PostDescriptor, markedPostType: MarkedPostType): Boolean
  suspend fun unmarkPost(postDescriptor: PostDescriptor, markedPostType: MarkedPostType): Boolean

  sealed class MarkedPostUpdate {
    data class Loaded(val markedPosts: Collection<MarkedPost>) : MarkedPostUpdate()
    data class Marked(val markedPost: MarkedPost) : MarkedPostUpdate()
    data class Unmarked(val markedPost: MarkedPost) : MarkedPostUpdate()
  }
}

class MarkedPostManagerImpl : MarkedPostManager {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val markedPostsStorage = mutableMapOf<ChanDescriptor.ThreadDescriptor, MutableSet<MarkedPost>>()

  private val _markedPostsUpdateFlow = MutableSharedFlow<MarkedPostManager.MarkedPostUpdate>(extraBufferCapacity = Channel.UNLIMITED)
  override val markedPostsUpdateFlow: SharedFlow<MarkedPostManager.MarkedPostUpdate>
    get() = _markedPostsUpdateFlow.asSharedFlow()

  override suspend fun insert(threadDescriptor: ChanDescriptor.ThreadDescriptor, markedPosts: Collection<MarkedPost>) {
    mutex.withLock { markedPostsStorage[threadDescriptor] = markedPosts.toMutableSet() }
    _markedPostsUpdateFlow.emit(MarkedPostManager.MarkedPostUpdate.Loaded(markedPosts))
  }

  override suspend fun getMarkedPosts(postDescriptor: PostDescriptor): Set<MarkedPost> {
    return mutex.withLock {
      return@withLock markedPostsStorage[postDescriptor.threadDescriptor()]
        ?.filter { markedPost -> markedPost.postDescriptor == postDescriptor }
        ?.toSet()
        ?: emptySet()
    }
  }

  override suspend fun getManyMarkedPosts(
    postDescriptorSet: Set<PostDescriptor>
  ): Map<PostDescriptor, Set<MarkedPost>> {
    if (postDescriptorSet.isEmpty()) {
      return emptyMap()
    }

    return mutex.withLock {
      val postDescriptorsGrouped = postDescriptorSet.groupBy { it.threadDescriptor() }
      val resultMap = mutableMapWithCap<PostDescriptor, MutableSet<MarkedPost>>(postDescriptorSet.size)

      for ((threadDescriptor, postDescriptors) in postDescriptorsGrouped) {
        val threadMarkedPosts = markedPostsStorage[threadDescriptor]
        if (threadMarkedPosts == null || threadMarkedPosts.isNullOrEmpty()) {
          continue
        }

        for (markedPost in threadMarkedPosts) {
          if (markedPost.postDescriptor in postDescriptors) {
            val markedPosts = resultMap.getOrPut(
              key = markedPost.postDescriptor,
              defaultValue = { mutableSetOf() }
            )

            markedPosts += markedPost
          }
        }
      }

      return@withLock resultMap
    }
  }

  override suspend fun isAlreadyLoadedForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): Boolean {
    return mutex.withLock { markedPostsStorage.containsKey(threadDescriptor) }
  }

  override suspend fun isPostMarkedAsMine(postDescriptor: PostDescriptor): Boolean {
    return mutex.withLock {
      markedPostsStorage[postDescriptor.threadDescriptor()]
        ?.firstOrNull { markedPost -> markedPost.postDescriptor == postDescriptor }
        ?.markedPostType == MarkedPostType.MyPost
    }
  }

  override suspend fun markPost(postDescriptor: PostDescriptor, markedPostType: MarkedPostType): Boolean {
    val markedPost = MarkedPost(postDescriptor, markedPostType)

    val marked = mutex.withLock {
      val threadMarkedPosts = markedPostsStorage.getOrPut(
        key = postDescriptor.threadDescriptor(),
        defaultValue = { mutableSetOf() }
      )

      val alreadyExisting = threadMarkedPosts.firstOrNull { markedPost -> markedPost.postDescriptor == postDescriptor }
      if (alreadyExisting != null && alreadyExisting.markedPostType == markedPostType) {
        return@withLock false
      }

      threadMarkedPosts += markedPost
      return@withLock true
    }

    if (marked) {
      _markedPostsUpdateFlow.emit(MarkedPostManager.MarkedPostUpdate.Marked(markedPost))
    }

    return marked
  }

  override suspend fun unmarkPost(postDescriptor: PostDescriptor, markedPostType: MarkedPostType): Boolean {
    val unmarked = mutex.withLock {
      val threadMarkedPosts = markedPostsStorage[postDescriptor.threadDescriptor()]
        ?: return@withLock false

      var deleted = false

      threadMarkedPosts.mutableIteration { mutableIterator, markedPost ->
        if (markedPost.postDescriptor == postDescriptor && markedPost.markedPostType == markedPostType) {
          mutableIterator.remove()
          deleted = true
          return@mutableIteration false
        }

        return@mutableIteration true
      }

      return@withLock deleted
    }

    if (unmarked) {
      _markedPostsUpdateFlow.emit(MarkedPostManager.MarkedPostUpdate.Unmarked(MarkedPost(postDescriptor, markedPostType)))
    }

    return unmarked
  }

}