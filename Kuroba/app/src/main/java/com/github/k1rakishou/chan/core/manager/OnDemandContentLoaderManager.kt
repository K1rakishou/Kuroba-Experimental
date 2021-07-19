package com.github.k1rakishou.chan.core.manager

import android.annotation.SuppressLint
import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.loader.LoaderBatchResult
import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader
import com.github.k1rakishou.chan.core.loader.PostLoaderData
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.processors.PublishProcessor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class OnDemandContentLoaderManager(
  private val workerScheduler: Scheduler,
  private val loaders: Set<OnDemandContentLoader>,
  private val chanThreadManager: ChanThreadManager
) {
  private val rwLock = ReentrantReadWriteLock()

  @GuardedBy("rwLock")
  private val activeLoaders = HashMap<ChanDescriptor, HashMap<PostDescriptor, PostLoaderData>>()

  private val postLoaderRxQueue = PublishProcessor.create<PostLoaderData>()
  private val postUpdateRxQueue = PublishProcessor.create<LoaderBatchResult>()

  init {
    Logger.d(TAG, "Loaders count = ${loaders.size}")
    initPostLoaderRxQueue()
  }

  @SuppressLint("CheckResult")
  private fun initPostLoaderRxQueue() {
    postLoaderRxQueue
      .onBackpressureBuffer(MIN_QUEUE_CAPACITY, false, true)
      .observeOn(workerScheduler)
      .flatMap { value -> addDelayIfSomethingIsNotCachedYet(value) }
      .flatMap { postLoaderData -> processLoaders(postLoaderData) }
      .subscribe({
        // Do nothing
      }, { error ->
        throw RuntimeException("$TAG Uncaught exception!!! " +
          "workerQueue is in error state now!!! " +
          "This should not happen!!!, original error = " + error.message)
      }, {
        throw RuntimeException(
          "$TAG workerQueue stream has completed!!! This should not happen!!!"
        )
      })
  }

  /**
   * Checks whether all info for the [postLoaderData] is cached by all loaders. If any loader has
   * no cached data for [postLoaderData] then adds a delay (so that we can have time to be
   * able to cancel it). If everything is cached then the delay is not added.
   * Also, updates [Post.onDemandContentLoadedMap] for the current post.
   * */
  private fun addDelayIfSomethingIsNotCachedYet(postLoaderData: PostLoaderData): Flowable<PostLoaderData> {
    val postDescriptor = postLoaderData.postDescriptor

    val post = chanThreadManager.getPost(postDescriptor)
    if (post == null || post.allLoadersCompletedLoading()) {
      return Flowable.just(postLoaderData)
    }

    val loadersCachedResultFlowable = Flowable.fromIterable(loaders)
      .flatMapSingle { loader ->
        return@flatMapSingle loader.isCached(PostLoaderData(postDescriptor))
          .onErrorReturnItem(false)
      }
      .toList()
      .map { cacheResults -> cacheResults.all { cacheResult -> cacheResult } }
      .toFlowable()
      .share()

    val allCachedStream = loadersCachedResultFlowable
      .filter { allCached -> allCached }
      .map { postLoaderData }

    val notAllCachedStream = loadersCachedResultFlowable
      .filter { allCached -> !allCached }
      .map { postLoaderData }
      // Add LOADING_DELAY_TIME_MS seconds delay to every emitted event.
      // We do that so that we don't download everything when user quickly
      // scrolls through posts. In other words, we only start running the
      // loader after LOADING_DELAY_TIME_MS seconds have passed since
      // onPostBind() was called. If onPostUnbind() was called during that
      // time frame we cancel the loader if it has already started loading or
      // just do nothing if it hasn't started loading yet.
      .zipWith(Flowable.timer(LOADING_DELAY_TIME_MS, TimeUnit.MILLISECONDS, workerScheduler), ZIP_FUNC)
      .onBackpressureBuffer(MIN_QUEUE_CAPACITY, false, true)
      .filter { (postLoaderData, _) -> isStillActive(postLoaderData) }
      .map { (postLoaderData, _) -> postLoaderData }

    return Flowable.merge(allCachedStream, notAllCachedStream)
  }

  private fun processLoaders(postLoaderData: PostLoaderData): Flowable<Unit> {
    return Flowable.fromIterable(loaders)
      .subscribeOn(workerScheduler)
      .flatMapSingle { loader ->
        return@flatMapSingle loader.startLoading(postLoaderData)
          .doOnError { error ->
            // All loaders' unhandled errors come here
            val loaderName = postLoaderData::class.java.simpleName
            Logger.e(TAG, "Loader: $loaderName unhandled error", error)
          }
          .timeout(MAX_LOADER_LOADING_TIME_MS, TimeUnit.MILLISECONDS, workerScheduler)
          .onErrorReturnItem(LoaderResult.Failed(loader.loaderType))
      }
      .toList()
      .map { results -> LoaderBatchResult(postLoaderData.postDescriptor, results) }
      .doOnSuccess { loaderBatchResults ->
        removeFromActiveLoaders(loaderBatchResults.postDescriptor, cancelLoaders = false)
        postUpdateRxQueue.onNext(loaderBatchResults)
      }
      .map { Unit }
      .toFlowable()
  }

  fun listenPostContentUpdates(): Flowable<LoaderBatchResult> {
    BackgroundUtils.ensureMainThread()

    return postUpdateRxQueue
      .onBackpressureBuffer()
      .observeOn(AndroidSchedulers.mainThread())
      .hide()
  }

  fun onPostBind(postDescriptor: PostDescriptor) {
    check(loaders.isNotEmpty()) { "No loaders!" }

    val chanDescriptor = postDescriptor.descriptor

    val postLoaderData = rwLock.write {
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

    postLoaderRxQueue.onNext(postLoaderData)
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

    rwLock.write {
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

  private fun removeFromActiveLoaders(postDescriptor: PostDescriptor, cancelLoaders: Boolean = true) {
    rwLock.write {
      val postLoaderData = activeLoaders[postDescriptor.descriptor]?.remove(postDescriptor)
        ?: return@write

      if (cancelLoaders) {
        loaders.forEach { loader -> loader.cancelLoading(postLoaderData) }
      }
    }
  }

  private fun isStillActive(postLoaderData: PostLoaderData): Boolean {
    return rwLock.read {
      val chanDescriptor = postLoaderData.postDescriptor.descriptor
      val postDescriptor = postLoaderData.postDescriptor

      return@read activeLoaders[chanDescriptor]?.containsKey(postDescriptor)
        ?: false
    }
  }

  companion object {
    private const val TAG = "OnDemandContentLoaderManager"
    private const val MIN_QUEUE_CAPACITY = 32
    const val LOADING_DELAY_TIME_MS = 1500L
    const val MAX_LOADER_LOADING_TIME_MS = 10_000L

    private val ZIP_FUNC = BiFunction<PostLoaderData, Long, Pair<PostLoaderData, Long>> { postLoaderData, timer ->
      Pair(postLoaderData, timer)
    }
  }
}