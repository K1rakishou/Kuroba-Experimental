package com.github.adamantcheese.chan.core.manager

import android.annotation.SuppressLint
import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.core.loader.LoaderBatchResult
import com.github.adamantcheese.chan.core.loader.LoaderResult
import com.github.adamantcheese.chan.core.loader.OnDemandContentLoader
import com.github.adamantcheese.chan.core.loader.PostLoaderData
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.PostUtils.getPostUniqueId
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
        private val loaders: Set<OnDemandContentLoader>
) {
    private val rwLock = ReentrantReadWriteLock()

    // HashMap<LoadableUid, HashMap<PostUid, PostLoaderData>>()
    @GuardedBy("rwLock")
    private val activeLoaders = HashMap<String, HashMap<String, PostLoaderData>>()

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
                .flatMap { value ->
                    return@flatMap Flowable.just(value)
                            // Add LOADING_DELAY_TIME_SECONDS seconds delay to every emitted event.
                            // We do that so that we don't download everything when user quickly
                            // scrolls through posts. In other words, we only start running the
                            // loader after LOADING_DELAY_TIME_SECONDS seconds have passed since
                            // onPostBind() was called. If onPostUnbind() was called during that
                            // time frame we cancel the loader if it has already started loading or
                            // just do nothing if it hasn't started loading yet.
                            .zipWith(
                                    Flowable.timer(
                                            LOADING_DELAY_TIME_SECONDS,
                                            TimeUnit.SECONDS,
                                            workerScheduler
                                    ),
                                    ZIP_FUNC
                            )
                }
                .filter { (postLoaderData, _) -> isStillActive(postLoaderData) }
                .map { (postLoaderData, _) -> postLoaderData }
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

    private fun processLoaders(postLoaderData: PostLoaderData): Flowable<Unit>? {
        return Flowable.fromIterable(loaders)
                .flatMapSingle { loader ->
                    return@flatMapSingle loader.startLoading(postLoaderData)
                            .doOnError { error ->
                                // All loaders' unhandled errors come here
                                val loaderName = postLoaderData::class.java.simpleName
                                Logger.e(TAG, "Loader: $loaderName unhandled error", error)
                            }
                            .timeout(MAX_LOADER_LOADING_TIME_SECONDS, TimeUnit.SECONDS, workerScheduler)
                            .onErrorReturnItem(LoaderResult.Failed(loader.loaderType))
                }
                .toList()
                .map { results -> LoaderBatchResult(postLoaderData.loadable, postLoaderData.post, results) }
                .doOnSuccess(postUpdateRxQueue::onNext)
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

    fun onPostBind(loadable: Loadable, post: Post) {
        BackgroundUtils.ensureMainThread()
        check(loaders.isNotEmpty()) { "No loaders!" }

        val loadableUid = loadable.uniqueId
        val postUid = getPostUniqueId(loadable, post)
        val postLoaderData = PostLoaderData(loadable, post)

        val alreadyAdded = rwLock.write {
            if (!activeLoaders.containsKey(loadableUid)) {
                activeLoaders[loadableUid] = hashMapOf()
            }

            if (activeLoaders[loadableUid]!!.containsKey(postUid)) {
                return@write true
            }

            activeLoaders[loadableUid]!![postUid] = postLoaderData
            return@write false
        }

        if (alreadyAdded) {
            return
        }

        postLoaderRxQueue.onNext(postLoaderData)
    }

    fun onPostUnbind(loadable: Loadable, post: Post, isActuallyRecycling: Boolean) {
        BackgroundUtils.ensureMainThread()
        check(loaders.isNotEmpty()) { "No loaders!" }

        if (!isActuallyRecycling) {
            // onPostUnbind was called because we called notifyItemChanged. The view is still
            // visible so we don't want to unbind anything.
            return
        }

        val loadableUid = loadable.uniqueId
        val postUid = getPostUniqueId(loadable, post)

        rwLock.write {
            val postLoaderData = activeLoaders[loadableUid]?.remove(postUid)
                    ?: return@write null

            loaders.forEach { loader -> loader.cancelLoading(postLoaderData) }
        }
    }

    fun cancelAllForLoadable(loadable: Loadable) {
        BackgroundUtils.ensureMainThread()
        val loadableUid = loadable.uniqueId

        Logger.d(TAG, "cancelAllForLoadable called for $loadableUid")

        rwLock.write {
            val postLoaderDataList = activeLoaders[loadableUid]
                    ?: return@write

            postLoaderDataList.values.forEach { postLoaderData ->
                loaders.forEach { loader -> loader.cancelLoading(postLoaderData) }
                postLoaderData.disposeAll()
            }

            postLoaderDataList.clear()
            activeLoaders.remove(loadableUid)
        }
    }

    private fun isStillActive(postLoaderData: PostLoaderData): Boolean {
        return rwLock.read {
            val loadableUid = postLoaderData.getLoadableUniqueId()
            val postUid = postLoaderData.getPostUniqueId()

            return@read activeLoaders[loadableUid]?.containsKey(postUid)
                    ?: false
        }
    }

    companion object {
        private const val TAG = "OnDemandContentLoaderManager"
        private const val MIN_QUEUE_CAPACITY = 32
        private const val LOADING_DELAY_TIME_SECONDS = 1L
        const val MAX_LOADER_LOADING_TIME_SECONDS = 10L

        private val ZIP_FUNC = BiFunction<PostLoaderData, Long, Pair<PostLoaderData, Long>> { postLoaderData, timer ->
            Pair(postLoaderData, timer)
        }
    }
}