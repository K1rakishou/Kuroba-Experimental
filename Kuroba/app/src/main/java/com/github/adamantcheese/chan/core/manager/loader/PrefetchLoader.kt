package com.github.adamantcheese.chan.core.manager.loader

import com.github.adamantcheese.chan.core.cache.FileCacheV2
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Loadable
import io.reactivex.Completable
import io.reactivex.Single

class PrefetchLoader(
        private val fileCacheV2: FileCacheV2
) : OnDemandContentLoader(LoaderType.PrefetchLoader) {

    override fun isAlreadyCached(loadable: Loadable, post: Post): Boolean {
        return false
    }

    override fun startLoading(loadable: Loadable, post: Post): Single<LoaderResult> {
        return Single.just(LoaderResult.Rejected(loaderType))
    }

    override fun cancelLoading(loadable: Loadable, post: Post): Completable {
        return Completable.complete()
    }

    //    private val rwLock = ReentrantReadWriteLock()
//    private val handler = Handler(Looper.getMainLooper())
//
//    // HashMap<ImageUrl, Prefetch>()
//    @GuardedBy("rwLock")
//    private val pendingPrefetches = hashMapOf<String, Prefetch>()
//
//    // HashMap<LoadableUid, HashMap<ImageUrl, Prefetch>>()
//    @GuardedBy("rwLock")
//    private val activePrefetches = hashMapOf<String, HashMap<String, CancelableDownload>>()
//
//    override fun startLoading(loadable: Loadable, post: Post): Completable {
//        if (post.images.isEmpty()) {
//            return Completable.complete()
//        }
//
//        if (!isLoadableSuitableForPrefetch(loadable)) {
//            return Completable.complete()
//        }
//
//        val prefetchList = post.images.mapNotNull { postImage ->
//            if (postImage.imageUrl == null || postImage.isInlined) {
//                // No url or image is inlined
//                return@mapNotNull null
//            }
//
//            if (!canPrefetchImageType(postImage.type)) {
//                // Bad type or not good enough network
//                return@mapNotNull null
//            }
//
//            val imageUrl = postImage.imageUrl.toString()
//            val alreadyPending = rwLock.read { pendingPrefetches.containsKey(imageUrl) }
//
//            if (alreadyPending) {
//                return@mapNotNull null
//            }
//
//            return@mapNotNull rwLock.write {
//                return@write Prefetch(postImage, loadable!!).also { prefetch ->
//                    pendingPrefetches[imageUrl] = prefetch
//                }
//            }
//        }
//
//        prefetchList.forEach { prefetch ->
//            val imageUrl = prefetch.postImage.imageUrl?.toString()
//                    ?: return@forEach
//
//            HandlerCompat.postDelayed(handler, {
//                val prevPrefetch = rwLock.write { pendingPrefetches.remove(imageUrl) }
//                        ?: return@postDelayed
//
//                enqueuePrefetch(prevPrefetch)
//            }, imageUrl, DEBOUNCE_DELAY)
//        }
//    }
//
//    override fun cancelLoading(loadable: Loadable, post: Post): Completable {
//        if (post.images.isEmpty()) {
//            return Completable.complete()
//        }
//
//        if (!isLoadableSuitableForPrefetch(loadable)) {
//            return Completable.complete()
//        }
//
//        val imageUrls = post.images.mapNotNull { postImage ->
//            if (postImage.imageUrl == null || postImage.isInlined) {
//                return@mapNotNull null
//            }
//
//            return@mapNotNull postImage.imageUrl.toString()
//        }
//
//        imageUrls.forEach { imageUrl ->
//            rwLock.write {
//                val loadableUid = checkNotNull(loadable) {
//                    "onPostUnbind() Loadable turned into null for some unknown reason"
//                }.uniqueId
//
//                Logger.d(TAG, "cancelPrefetchForImage called for ${loadableUid} loadable, ${imageUrl} image")
//
//                pendingPrefetches.remove(imageUrl)
//                handler.removeCallbacksAndMessages(imageUrl)
//                activePrefetches.get(loadableUid)?.remove(imageUrl)?.cancelPrefetch()
//
//                if (activePrefetches.get(loadableUid)?.isEmpty() != false) {
//                    activePrefetches.remove(loadableUid)
//                }
//            }
//        }
//    }
//
//    fun cancelPrefetchForLoadable(loadable: Loadable?) {
//        if (!isLoadableSuitableForPrefetch(loadable)) {
//            return
//        }
//
//        rwLock.write {
//            val loadableUid = checkNotNull(loadable) {
//                "cancelPrefetchForLoadable() Loadable turned into null for some unknown reason"
//            }.uniqueId
//
//            val prefetchMap = activePrefetches.remove(loadableUid)
//            if (prefetchMap == null) {
//                // Prefetches are still in pending state (the downloads are not yet running)
//
//                val pendingPrefetchesMap = pendingPrefetches.filter { (_, prefetch) ->
//                    prefetch.loadable.uniqueId == loadableUid
//                }
//
//                if (pendingPrefetchesMap.isEmpty()) {
//                    return@write
//                }
//
//                pendingPrefetchesMap.forEach { (imageUrl, _) ->
//                    Logger.d(TAG, "cancelPendingPrefetchForLoadable called for ${loadableUid} loadable, ${imageUrl} image")
//
//                    pendingPrefetches.remove(imageUrl)
//                    handler.removeCallbacksAndMessages(imageUrl)
//                }
//            } else {
//                // Prefetches switched to active state (the downloads may be already running at this
//                // point)
//
//                prefetchMap.forEach { (imageUrl, cancelableDownload) ->
//                    Logger.d(TAG, "cancelActivePrefetchForLoadable called for ${loadableUid} loadable, ${imageUrl} image")
//
//                    pendingPrefetches.remove(imageUrl)
//                    handler.removeCallbacksAndMessages(imageUrl)
//                    cancelableDownload.cancelPrefetch()
//                }
//
//                prefetchMap.clear()
//            }
//        }
//    }
//
//    private fun enqueuePrefetch(prefetch: Prefetch) {
//        Logger.d(TAG, "enqueuePrefetch called for ${prefetch.postImage.imageUrl} image")
//
//        val cancelableDownload = fileCacheV2.enqueueMediaPrefetchRequest(
//                prefetch.loadable,
//                prefetch.postImage
//        )
//
//        if (cancelableDownload == null) {
//            // Already cached or something like that
//            return
//        }
//
//        rwLock.write {
//            val loadableUid = prefetch.loadable.uniqueId
//            val imageUrl = prefetch.postImage.imageUrl?.toString()
//                    ?: return@write
//
//            if (!activePrefetches.containsKey(loadableUid)) {
//                activePrefetches.put(loadableUid, hashMapOf())
//            }
//
//            activePrefetches[loadableUid]!!.put(imageUrl, cancelableDownload)
//        }
//    }
//
//    private fun canPrefetchImageType(type: PostImage.Type?): Boolean {
//        if (type == null) {
//            return false
//        }
//
//        return when (type) {
//            PostImage.Type.STATIC,
//            PostImage.Type.GIF -> shouldLoadForNetworkType(ChanSettings.imageAutoLoadNetwork.get())
//            PostImage.Type.MOVIE -> shouldLoadForNetworkType(ChanSettings.videoAutoLoadNetwork.get())
//            PostImage.Type.PDF,
//            PostImage.Type.SWF -> false
//        }.exhaustive
//    }
//
//    private fun isLoadableSuitableForPrefetch(loadable: Loadable?): Boolean {
//        if (loadable == null) {
//            return false
//        }
//
//        if (!ChanSettings.autoLoadThreadImages.get()) {
//            // Prefetching disabled
//            return false
//        }
//
//        if (loadable.isLocal || loadable.isDownloading) {
//            // Cannot prefetch local threads
//            return false
//        }
//
//        return true
//    }
//
//    private data class Prefetch(
//            val postImage: PostImage,
//            val loadable: Loadable
//    )
//
//    companion object {
//        private const val TAG = "PrefetchManager"
//
//        /**
//         * Delay that we wait before actually starting prefetching an image. The user might scroll
//         * through the thread very quickly and we don't want to start prefetching every image in
//         * such case. Only prefetch images if there is at least one second difference between
//         * onBindPost() and onUnbindPost() calls.
//         * */
//        private val DEBOUNCE_DELAY = TimeUnit.SECONDS.toMillis(1)
//    }
}
