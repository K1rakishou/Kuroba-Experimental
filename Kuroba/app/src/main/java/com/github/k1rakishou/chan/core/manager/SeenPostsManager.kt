package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.linkedMapWithCap
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.common.toHashSetBy
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.SeenPost
import com.github.k1rakishou.model.repository.SeenPostRepository
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.joda.time.DateTime
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@Suppress("EXPERIMENTAL_API_USAGE")
class SeenPostsManager(
  private val appScope: CoroutineScope,
  private val verboseLogsEnabled: Boolean,
  private val chanThreadsCache: ChanThreadsCache,
  private val catalogSnapshotCache: ChanCatalogSnapshotCache,
  private val seenPostsRepository: SeenPostRepository
) {
  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val seenPostsMap = linkedMapWithCap<ChanDescriptor.ThreadDescriptor, MutableMap<PostDescriptor, SeenPost>>(256)
  @GuardedBy("lock")
  private val seenPostsToPersist = mutableMapOf<ChanDescriptor.ThreadDescriptor, MutableMap<PostDescriptor, SeenPost>>()
  @GuardedBy("lock")
  private var lastLoadedCatalogDescriptor: ChanDescriptor.ICatalogDescriptor? = null
  @GuardedBy("lock")
  private val alreadyLoadedDescriptorsForUnlimitedCatalog = hashSetWithCap<ChanDescriptor.ThreadDescriptor>(32)

  private val _seenThreadUpdatesFlow = MutableSharedFlow<ChanDescriptor.ThreadDescriptor>(extraBufferCapacity = 64)
  val seenThreadUpdatesFlow: SharedFlow<ChanDescriptor.ThreadDescriptor>
    get() = _seenThreadUpdatesFlow.asSharedFlow()

  private val debouncingCoroutineExecutor = DebouncingCoroutineExecutor(appScope)

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
    if (!isEnabled()) {
      return
    }

    val alreadyPreloaded = lock.read {
      // We consider data preloaded only if it contains more than one entry (for original post) per thread.
      return@read (seenPostsMap[threadDescriptor]?.size ?: 0) > 1
    }

    if (alreadyPreloaded) {
      return
    }

    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForThread($threadDescriptor) begin")
    }

    val time = measureTime {
      val seenPosts = seenPostsRepository.selectAllByThreadDescriptor(threadDescriptor)
        .safeUnwrap { error ->
          Logger.e(TAG, "Error while trying to select all seen posts by threadDescriptor " +
            "($threadDescriptor), error = ${error.errorMessageOrClassName()}")

          return@measureTime
        }

      lock.write {
        val resultMap = mutableMapWithCap<PostDescriptor, SeenPost>(seenPosts.size)

        for (seenPost in seenPosts) {
          resultMap[seenPost.postDescriptor] = seenPost
        }

        seenPostsMap.put(threadDescriptor, resultMap)
      }
    }

    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForThread($threadDescriptor) end, took $time")
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun loadForCatalog(
    catalogDescriptor: ChanDescriptor.CatalogDescriptor,
    threadDescriptors: List<ChanDescriptor.ThreadDescriptor>? = null
  ) {
    if (!isEnabled()) {
      return
    }

    val threadDescriptorsToLoad = getThreadDescriptorsToLoad(threadDescriptors, catalogDescriptor)
    if (threadDescriptorsToLoad.isEmpty()) {
      return
    }

    val seenPostsGrouped = seenPostsRepository.selectAllByThreadDescriptors(
      boardDescriptor = catalogDescriptor.boardDescriptor,
      threadDescriptors = threadDescriptorsToLoad
    ).safeUnwrap { error ->
      Logger.e(TAG, "Error while trying to select all seen posts by threadDescriptors " +
        "(${catalogDescriptor}, ${threadDescriptorsToLoad.size}), " +
        "error = ${error.errorMessageOrClassName()}")

      return
    }.groupBy { seenPost -> seenPost.postDescriptor.threadDescriptor() }

    lock.write {
      Logger.d(TAG, "loadForCatalog($catalogDescriptor) " +
        "threadDescriptorsToLoad=${threadDescriptorsToLoad.size}, " +
        "seenPostsGrouped=${seenPostsGrouped.size}, " +
        "alreadyLoadedDescriptorsForUnlimitedCatalog=${alreadyLoadedDescriptorsForUnlimitedCatalog.size}")

      seenPostsGrouped.entries.forEach { (threadDescriptor, seenPosts) ->
        seenPostsMap.putIfNotContains(threadDescriptor, mutableMapWithCap(seenPosts.size))

        val innerMap = seenPostsMap[threadDescriptor]!!
        seenPosts.forEach { seenPost -> innerMap[seenPost.postDescriptor] = seenPost }
      }
    }
  }

  fun onPostBind(threadMode: Boolean, postDescriptor: PostDescriptor) {
    if (!threadMode || !isEnabled()) {
      return
    }

    val seenPost = SeenPost(
      postDescriptor = postDescriptor,
      insertedAt = DateTime.now()
    )

    createNewSeenPosts(listOf(seenPost))
  }

  fun onPostUnbind(threadMode: Boolean, postDescriptor: PostDescriptor) {
    // No-op (maybe something will be added here in the future)
  }

  fun getSeenPostOrNull(postDescriptor: PostDescriptor): SeenPost? {
    return lock.read { seenPostsMap[postDescriptor.threadDescriptor()]?.get(postDescriptor) }
  }

  fun isThreadAlreadySeen(threadDescriptor: ChanDescriptor.ThreadDescriptor): Boolean {
    return lock.read { (seenPostsMap[threadDescriptor]?.size ?: 0) > 0 }
  }

  private fun createNewSeenPosts(seenPosts: Collection<SeenPost>) {
    if (seenPosts.isEmpty()) {
      return
    }

    val needPersist = lock.write {
      var needPersist = false

      for (seenPost in seenPosts) {
        val postDescriptor = seenPost.postDescriptor
        val threadDescriptor = postDescriptor.threadDescriptor()

        seenPostsToPersist.putIfNotContains(threadDescriptor, mutableMapWithCap(32))

        if (seenPostsMap[threadDescriptor]?.contains(postDescriptor) == true) {
          continue
        }

        if (seenPostsToPersist[threadDescriptor]?.contains(postDescriptor) == true) {
          continue
        }

        seenPostsToPersist[threadDescriptor]!!.put(postDescriptor, seenPost)
        needPersist = true
      }

      return@write needPersist
    }

    if (!needPersist) {
      return
    }

    debouncingCoroutineExecutor.post(DEBOUNCE_TIMEOUT_MS) {
      val threadDescriptors = seenPosts.toHashSetBy { seenPost -> seenPost.postDescriptor.threadDescriptor() }

      val toPersistMap = lock.write {
        val toPersistMap = mutableMapOf<ChanDescriptor.ThreadDescriptor, Set<SeenPost>>()

        for (threadDescriptor in threadDescriptors) {
          val seenPostsMap = seenPostsToPersist.remove(threadDescriptor)
            ?.toMutableMap()

          if (seenPostsMap == null || seenPostsMap.values.isEmpty()) {
            continue
          }

          toPersistMap[threadDescriptor] = seenPostsMap.values.toSet()
        }

        return@write toPersistMap
      }

      if (toPersistMap.isEmpty()) {
        return@post
      }

      toPersistMap.forEach { (threadDescriptor, seenPostSet) ->
        if (verboseLogsEnabled) {
          Logger.d(TAG, "onPostBind() persisting ${seenPostSet.size} posts")
        }

        seenPostsRepository.insertMany(threadDescriptor, seenPostSet)
          .safeUnwrap { error ->
            Logger.e(TAG, "Error while trying to store new seen post with threadDescriptor " +
                "($threadDescriptor), error = ${error.errorMessageOrClassName()}")
            return@post
          }

        lock.write {
          seenPostsMap.putIfNotContains(threadDescriptor, mutableMapWithCap(32))

          val innerMap = seenPostsMap[threadDescriptor]!!
          seenPostSet.forEach { seenPost -> innerMap[seenPost.postDescriptor] = seenPost }
        }

        _seenThreadUpdatesFlow.emit(threadDescriptor)
      }
    }
  }

  private fun isEnabled() = ChanSettings.markUnseenPosts.get()

  private fun onThreadDeleteEventReceived(threadDeleteEvent: ChanThreadsCache.ThreadDeleteEvent) {
    lock.write {
      when (threadDeleteEvent) {
        is ChanThreadsCache.ThreadDeleteEvent.RemoveThreads -> {
          var removedThreads = 0

          threadDeleteEvent.threadDescriptors.forEach { threadDescriptor ->
            ++removedThreads
            seenPostsMap.remove(threadDescriptor)
          }

          Logger.d(TAG, "onThreadDeleteEventReceived.RemoveThreads() removed ${removedThreads} threads")
        }
        is ChanThreadsCache.ThreadDeleteEvent.RemoveThreadPostsExceptOP -> {
          var removedPosts = 0

          threadDeleteEvent.entries.forEach { (threadDescriptor, originalPostDescriptor) ->
            seenPostsMap[threadDescriptor]?.keys?.mutableIteration { mutableIterator, postDescriptor ->
              if (postDescriptor != originalPostDescriptor) {
                ++removedPosts
                mutableIterator.remove()
              }

              return@mutableIteration true
            }
          }

          Logger.d(TAG, "onThreadDeleteEventReceived.RemoveThreadPostsExceptOP() removed ${removedPosts} posts")
        }
      }
    }
  }

  private fun getThreadDescriptorsToLoad(
    threadDescriptors: List<ChanDescriptor.ThreadDescriptor>?,
    catalogDescriptor: ChanDescriptor.ICatalogDescriptor
  ): List<ChanDescriptor.ThreadDescriptor> {
    return lock.read {
      if (threadDescriptors != null) {
        alreadyLoadedDescriptorsForUnlimitedCatalog.clear()
        alreadyLoadedDescriptorsForUnlimitedCatalog.addAll(threadDescriptors)

        return@read threadDescriptors
      }

      val catalogSnapshot = catalogSnapshotCache.get(catalogDescriptor)
        ?: return@read emptyList()

      if (catalogDescriptor != lastLoadedCatalogDescriptor || !catalogSnapshot.isUnlimitedOrCompositeCatalog) {
        alreadyLoadedDescriptorsForUnlimitedCatalog.clear()
        lastLoadedCatalogDescriptor = catalogDescriptor
      }

      val threadDescriptorsToLoad = catalogSnapshot.catalogThreadDescriptorSet
        .filter { threadDescriptor ->
          threadDescriptor !in alreadyLoadedDescriptorsForUnlimitedCatalog && threadDescriptor !in seenPostsMap
        }

      if (catalogSnapshot.isUnlimitedOrCompositeCatalog) {
        alreadyLoadedDescriptorsForUnlimitedCatalog.addAll(catalogSnapshot.catalogThreadDescriptorSet)
      }

      return@read threadDescriptorsToLoad
    }
  }

  companion object {
    private const val TAG = "SeenPostsManager"
    private const val DEBOUNCE_TIMEOUT_MS = 250L
  }
}