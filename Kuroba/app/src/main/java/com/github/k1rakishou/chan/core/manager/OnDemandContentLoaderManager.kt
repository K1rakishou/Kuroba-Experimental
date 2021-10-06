package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.QueueableConcurrentCoroutineExecutor
import com.github.k1rakishou.chan.core.loader.LoaderBatchResult
import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader
import com.github.k1rakishou.chan.core.loader.PostLoaderData
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.common.processDataCollectionConcurrently
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.HashMap
import kotlin.collections.set
import kotlin.concurrent.read
import kotlin.concurrent.write

class OnDemandContentLoaderManager(
  private val scope: CoroutineScope,
  private val dispatcher: CoroutineDispatcher,
  private val loadersLazy: Lazy<HashSet<OnDemandContentLoader>>,
  private val chanThreadManager: ChanThreadManager
) {
  private val lock = ReentrantReadWriteLock()
  private val loaders: HashSet<OnDemandContentLoader>
    get() = loadersLazy.value

  @GuardedBy("rwLock")
  private val activeLoaders = HashMap<ChanDescriptor, HashMap<PostDescriptor, PostLoaderData>>()

  private val _postUpdateFlow = MutableSharedFlow<LoaderBatchResult>(extraBufferCapacity = 128)
  val postUpdateFlow: SharedFlow<LoaderBatchResult>
    get() = _postUpdateFlow

  private val executor = QueueableConcurrentCoroutineExecutor(
    maxConcurrency = 64,
    dispatcher = dispatcher,
    scope = scope
  )

  fun onPostBind(postDescriptor: PostDescriptor) {
    check(loaders.isNotEmpty()) { "No loaders!" }

    val chanDescriptor = postDescriptor.descriptor

    val postLoaderData = lock.write {
      if (!activeLoaders.containsKey(chanDescriptor)) {
        activeLoaders[chanDescriptor] = hashMapOf()
      }

      if (activeLoaders[chanDescriptor]!!.containsKey(postDescriptor)) {
        return@write null
      }

      val postLoaderData = PostLoaderData(postDescriptor)
      activeLoaders[chanDescriptor]!![postDescriptor] = postLoaderData
      return@write postLoaderData
    }

    if (postLoaderData == null) {
      // Already added
      return
    }

    val job = executor.post {
      val loaderBatchResults = try {
        onPostBindInternal(postLoaderData)
      } catch (error: Throwable) {
        if (error.isExceptionImportant()) {
          Logger.e(TAG, "onPostBindInternal(${postLoaderData.postDescriptor}) error", error)
        }

        null
      }

      if (loaderBatchResults != null) {
        _postUpdateFlow.emit(loaderBatchResults)
      }
    }

    postLoaderData.setJob(job)
  }

  fun onPostUnbind(postDescriptor: PostDescriptor, isActuallyRecycling: Boolean) {
    check(loaders.isNotEmpty()) { "No loaders!" }

    if (!isActuallyRecycling) {
      // onPostUnbind was called because we called notifyItemChanged. The view is still
      // visible so we don't want to unbind anything.
      return
    }

    removeFromActiveLoaders(postDescriptor)
  }

  fun cancelAllForDescriptor(chanDescriptor: ChanDescriptor) {
    if (chanDescriptor.isCatalogDescriptor()) {
      return
    }

    BackgroundUtils.ensureMainThread()
    val threadDescriptor = chanDescriptor as ChanDescriptor.ThreadDescriptor

    Logger.d(TAG, "cancelAllForDescriptor called for $threadDescriptor")

    lock.write {
      val postLoaderDataList = activeLoaders[threadDescriptor]
        ?: return@write

      postLoaderDataList.values.forEach { postLoaderData ->
        loaders.forEach { loader -> loader.cancelLoading(postLoaderData) }
        postLoaderData.disposeAll()
      }

      postLoaderDataList.clear()
      activeLoaders.remove(threadDescriptor)
    }
  }

  private suspend fun onPostBindInternal(postLoaderData: PostLoaderData): LoaderBatchResult? {
    BackgroundUtils.ensureBackgroundThread()
    val postDescriptor = postLoaderData.postDescriptor

    val post = chanThreadManager.getPost(postDescriptor)
    if (post == null || post.allLoadersCompletedLoading()) {
      // Everything is done
      return null
    }

    val allLoadersCached = loaders.all { loader -> loader.isCached(PostLoaderData(postDescriptor)) }
    if (!allLoadersCached) {
      // Add LOADING_DELAY_TIME_MS seconds delay to every emitted event.
      // We do that so that we don't download everything when user quickly
      // scrolls through posts. In other words, we only start running the
      // loader after LOADING_DELAY_TIME_MS seconds have passed since
      // onPostBind() was called. If onPostUnbind() was called during that
      // time frame we cancel the loader if it has already started loading or
      // just do nothing if it hasn't started loading yet.
      delay(LOADING_DELAY_TIME_MS)
    }

    if (!isStillActive(postLoaderData)) {
      return null
    }

    val loaderResults = processDataCollectionConcurrently(loaders) { loader ->
      val result = withTimeoutOrNull(MAX_LOADER_LOADING_TIME_MS) { loader.startLoading(postLoaderData) }
      if (result == null) {
        return@processDataCollectionConcurrently LoaderResult.Failed(loader.loaderType)
      }

      return@processDataCollectionConcurrently result
    }

    val loaderBatchResults = LoaderBatchResult(postLoaderData.postDescriptor, loaderResults)
    removeFromActiveLoaders(loaderBatchResults.postDescriptor, cancelLoaders = false)

    return loaderBatchResults
  }

  private fun removeFromActiveLoaders(postDescriptor: PostDescriptor, cancelLoaders: Boolean = true) {
    lock.write {
      val postLoaderData = activeLoaders[postDescriptor.descriptor]?.remove(postDescriptor)
        ?: return@write

      if (cancelLoaders) {
        loaders.forEach { loader -> loader.cancelLoading(postLoaderData) }
      }
    }
  }

  private fun isStillActive(postLoaderData: PostLoaderData): Boolean {
    return lock.read {
      val chanDescriptor = postLoaderData.postDescriptor.descriptor
      val postDescriptor = postLoaderData.postDescriptor

      return@read activeLoaders[chanDescriptor]?.containsKey(postDescriptor)
        ?: false
    }
  }

  companion object {
    private const val TAG = "OnDemandContentLoaderManager"
    const val LOADING_DELAY_TIME_MS = 1500L
    const val MAX_LOADER_LOADING_TIME_MS = 10_000L
  }
}