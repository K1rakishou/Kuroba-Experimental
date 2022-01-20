package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanSavedReply
import com.github.k1rakishou.model.repository.ChanSavedReplyRepository
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class SavedReplyManager(
  private val verboseLogsEnabled: Boolean,
  private val chanThreadsCache: ChanThreadsCache,
  private val savedReplyRepository: ChanSavedReplyRepository
) {
  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val savedReplyMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, MutableList<ChanSavedReply>>(16)

  private val _savedRepliesUpdateFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  val savedRepliesUpdateFlow: SharedFlow<Unit>
    get() = _savedRepliesUpdateFlow.asSharedFlow()

  init {
    chanThreadsCache.addChanThreadDeleteEventListener { threadDeleteEvent ->
      if (verboseLogsEnabled) {
        Logger.d(TAG, "chanThreadsCache.chanThreadDeleteEventFlow() " +
          "threadDeleteEvent=${threadDeleteEvent.javaClass.simpleName}")
      }

      onThreadDeleteEventReceived(threadDeleteEvent)
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val alreadyPreloaded = lock.read { savedReplyMap[threadDescriptor]?.isNotEmpty() == true }
    if (alreadyPreloaded) {
      return
    }

    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForThread($threadDescriptor) begin")
    }

    val time = measureTime { preloadForThreadInternal(threadDescriptor) }

    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForThread($threadDescriptor) end, took $time")
    }
  }

  suspend fun loadAll(): ModularResult<Unit> {
    return ModularResult.Try {
      val allSavedReplies = savedReplyRepository.loadAll().unwrap()

      lock.write {
        val groupedSavedReplies = allSavedReplies.groupBy { savedReply -> savedReply.postDescriptor.threadDescriptor() }

        groupedSavedReplies.entries.forEach { (threadDescriptor, savedReplies) ->
          savedReplyMap[threadDescriptor] = savedReplies.toMutableList()
        }
      }
    }
  }

  fun getAll(): Map<ChanDescriptor.ThreadDescriptor, List<ChanSavedReply>> {
    return lock.write {
      return@write savedReplyMap.toMap()
    }
  }

  fun isSaved(chanDescriptor: ChanDescriptor, postNo: Long, postSubNo: Long): Boolean {
    val threadDescriptor = when (chanDescriptor) {
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        error("Cannot use CompositeCatalogDescriptor here")
      }
      is ChanDescriptor.ThreadDescriptor -> chanDescriptor
      is ChanDescriptor.CatalogDescriptor -> ChanDescriptor.ThreadDescriptor.create(chanDescriptor, postNo)
    }

    return lock.read {
      return@read savedReplyMap[threadDescriptor]
        ?.any { chanSavedReply ->
          return@any chanSavedReply.postDescriptor.postNo == postNo
            && chanSavedReply.postDescriptor.postSubNo == postSubNo
        } ?: false
    }
  }

  fun isSaved(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      return@read savedReplyMap[postDescriptor.threadDescriptor()]?.any { chanSavedReply ->
        chanSavedReply.postDescriptor == postDescriptor
      } ?: false
    }
  }

  fun getSavedReply(postDescriptor: PostDescriptor): ChanSavedReply? {
    return lock.read {
      return@read savedReplyMap[postDescriptor.threadDescriptor()]?.firstOrNull { chanSavedReply ->
        chanSavedReply.postDescriptor == postDescriptor
      }
    }
  }

  suspend fun unsavePost(postDescriptor: PostDescriptor) {
    unsavePosts(listOf(postDescriptor))
  }

  suspend fun unsavePosts(postDescriptors: Collection<PostDescriptor>) {
    savedReplyRepository.unsavePosts(postDescriptors)
      .safeUnwrap { error ->
        Logger.e(TAG, "unsavePost(${postDescriptors.size} posts) error", error)
        return
      }

    val updated = lock.write {
      var updated = false

      postDescriptors.forEach { postDescriptor ->
        val index = savedReplyMap[postDescriptor.threadDescriptor()]
          ?.indexOfFirst { chanSavedReply -> chanSavedReply.postDescriptor == postDescriptor } ?: -1

        if (index >= 0) {
          savedReplyMap[postDescriptor.threadDescriptor()]?.removeAt(index)
          updated = true
        }
      }

      return@write updated
    }

    if (updated) {
      _savedRepliesUpdateFlow.tryEmit(Unit)
    }
  }

  suspend fun deleteAll() {
    savedReplyRepository.unsaveAll()
      .peekError { error -> Logger.e(TAG, "unsaveAll() error", error) }
      .ignore()

    lock.write { savedReplyMap.clear() }
    _savedRepliesUpdateFlow.tryEmit(Unit)
  }

  suspend fun savePost(postDescriptor: PostDescriptor) {
    val post = chanThreadsCache.getPostFromCache(postDescriptor)

    val comment = post?.postComment?.originalComment()?.toString()
    val subject = chanThreadsCache.getOriginalPostFromCache(postDescriptor)?.let { chanOriginalPost ->
      return@let ChanPostUtils.getTitle(chanOriginalPost, postDescriptor.descriptor)
    }

    val savedReply = ChanSavedReply(postDescriptor = postDescriptor, comment = comment, subject = subject)
    saveReply(savedReply)
  }

  suspend fun saveReply(savedReply: ChanSavedReply) {
    val postDescriptor = savedReply.postDescriptor

    savedReplyRepository.savePost(savedReply)
      .safeUnwrap { error ->
        Logger.e(TAG, "savePost($postDescriptor) error", error)
        return
      }

    val updated = lock.write {
      val index = savedReplyMap[postDescriptor.threadDescriptor()]
        ?.indexOfFirst { chanSavedReply -> chanSavedReply.postDescriptor == postDescriptor } ?: -1

      if (index < 0) {
        savedReplyMap[postDescriptor.threadDescriptor()]?.add(savedReply)
        return@write true
      }

      return@write false
    }

    if (updated) {
      _savedRepliesUpdateFlow.tryEmit(Unit)
    }
  }

  fun retainSavedPostNoMap(
    postList: List<PostDescriptor>,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): List<PostDescriptor> {
    if (postList.isEmpty()) {
      return emptyList()
    }

    return lock.read {
      val savedRepliesNoSet = savedReplyMap[threadDescriptor]
        ?.map { chanSavedReply -> chanSavedReply.postDescriptor }
        ?.toSet()
        ?: return@read emptyList()

      return@read postList
        .filter { postDescriptor -> savedRepliesNoSet.contains(postDescriptor) }
    }
  }

  fun getThreadSavedReplies(threadDescriptor: ChanDescriptor.ThreadDescriptor): List<ChanSavedReply> {
    return lock.read { savedReplyMap[threadDescriptor] ?: emptyList() }
  }

  private suspend fun preloadForThreadInternal(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val savedReplies = savedReplyRepository.preloadForThread(threadDescriptor)
      .safeUnwrap { error ->
        Logger.e(TAG, "Error while trying to preload saved replies for thread ($threadDescriptor)", error)
        return
      }

    lock.write {
      savedReplyMap[threadDescriptor] = savedReplies.toMutableList()
    }
  }

  private fun onThreadDeleteEventReceived(threadDeleteEvent: ChanThreadsCache.ThreadDeleteEvent) {
    lock.write {
      when (threadDeleteEvent) {
        is ChanThreadsCache.ThreadDeleteEvent.RemoveThreads -> {
          var removedThreads = 0

          threadDeleteEvent.threadDescriptors.forEach { threadDescriptor ->
            ++removedThreads
            savedReplyMap.remove(threadDescriptor)
          }

          Logger.d(TAG, "onThreadDeleteEventReceived.RemoveThreads() removed ${removedThreads} threads")
        }
        is ChanThreadsCache.ThreadDeleteEvent.RemoveThreadPostsExceptOP -> {
          var removedPosts = 0

          threadDeleteEvent.entries.forEach { (threadDescriptor, originalPostDescriptor) ->
            val innerPostHideMap = savedReplyMap[threadDescriptor]
              ?: return@forEach

            innerPostHideMap.mutableIteration { mutableIterator, mapEntry ->
              if (mapEntry.postDescriptor != originalPostDescriptor) {
                ++removedPosts
                mutableIterator.remove()
              }

              return@mutableIteration true
            }
          }

          Logger.d(TAG, "onThreadDeleteEventReceived.RemoveThreadPostsExceptOP() removed ${removedPosts} saved posts")
        }
      }
    }
  }

  companion object {
    private const val TAG = "SavedReplyManager"
  }
}