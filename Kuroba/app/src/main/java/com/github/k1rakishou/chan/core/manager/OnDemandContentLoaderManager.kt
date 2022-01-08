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

  fun onPostBind(postDescriptor: PostDescriptor, catalogMode: Boolean) {
    check(loaders.isNotEmpty()) { "No loaders!" }

    val chanDescriptor = postDescriptor.descriptor

    val postLoaderData = lock.write {
      if (!activeLoaders.containsKey(chanDescriptor)) {
        activeLoaders[chanDescriptor] = hashMapOf()
      }

      if (activeLoaders[chanDescriptor]!!.containsKey(postDescriptor)) {
        return@write null
      }

      val postLoaderData = PostLoaderData(catalogMode, postDescriptor)
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

    val threadDescriptor = chanDescriptor as ChanDescriptor.ThreadDescriptor

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

    val allLoadersCached = loaders.all { loader -> loader.isCached(postLoaderData) }

    // Add some delay here to avoid visual glitches when quickly scrolling through posts
    // (Especially when using the fast scroller). In case when the post loader results are not
    // cached by the loaders we use the long delay, otherwise if everything is already cached,
    // we use the short delay.
    if (!allLoadersCached) {
      delay(LONG_LOADING_DELAY_TIME_MS)
    } else {
      delay(SHORT_LOADING_DELAY_TIME_MS)
    }

    if (!isStillActive(postLoaderData)) {
      removeFromActiveLoaders(postDescriptor)
      return null
    }

    val loaderResults = processDataCollectionConcurrently(loaders) { loader ->
      val result = withTimeoutOrNull(MAX_LOADER_LOADING_TIME_MS) { loader.startLoading(postLoaderData) }
      if (result == null) {
        removeFromActiveLoaders(postDescriptor)
        return@processDataCollectionConcurrently LoaderResult.Failed(loader.loaderType)
      }

      return@processDataCollectionConcurrently result
    }

    return LoaderBatchResult(postLoaderData.postDescriptor, loaderResults)
  }

  private fun removeFromActiveLoaders(postDescriptor: PostDescriptor) {
    val postLoaderData = lock.write {
      activeLoaders[postDescriptor.descriptor]?.remove(postDescriptor)
    }

    if (postLoaderData != null) {
      loaders.forEach { loader -> loader.cancelLoading(postLoaderData) }
      postLoaderData.disposeAll()
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
    const val LONG_LOADING_DELAY_TIME_MS = 1500L
    const val SHORT_LOADING_DELAY_TIME_MS = 500L
    const val MAX_LOADER_LOADING_TIME_MS = 10_000L
  }
}