package com.github.adamantcheese.chan.core.loader.impl

import com.github.adamantcheese.chan.core.cache.FileCacheV2
import com.github.adamantcheese.chan.core.loader.LoaderResult
import com.github.adamantcheese.chan.core.loader.LoaderType
import com.github.adamantcheese.chan.core.loader.OnDemandContentLoader
import com.github.adamantcheese.chan.core.loader.PostLoaderData
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.ChanSettings.MediaAutoLoadMode.shouldLoadForNetworkType
import com.github.adamantcheese.chan.utils.exhaustive
import io.reactivex.Single

class PrefetchLoader(
        private val fileCacheV2: FileCacheV2
) : OnDemandContentLoader(LoaderType.PrefetchLoader) {

    override fun isAlreadyCached(postLoaderData: PostLoaderData): Boolean {
        // Always false, fileCacheV2 will check it internally
        return false
    }

    override fun startLoading(postLoaderData: PostLoaderData): Single<LoaderResult> {
        val post = postLoaderData.post
        val loadable = postLoaderData.loadable

        if (post.images.isEmpty()) {
            return reject()
        }

        if (!isLoadableSuitableForPrefetch(loadable)) {
            return reject()
        }

        val prefetchList = getPrefetchBatch(post, loadable)
        if (prefetchList.isEmpty()) {
            return reject()
        }

        prefetchList.forEach { prefetch ->
            val cancelableDownload = fileCacheV2.enqueueMediaPrefetchRequest(
                    prefetch.loadable,
                    prefetch.postImage
            )

            if (cancelableDownload == null) {
                // Already cached or something like that
                return@forEach
            }

            postLoaderData.addDisposeFunc { cancelableDownload.cancelPrefetch() }
        }

        return success()
    }

    override fun cancelLoading(postLoaderData: PostLoaderData) {
        return postLoaderData.disposeAll()
    }

    private fun getPrefetchBatch(post: Post, loadable: Loadable): List<Prefetch> {
        return post.images.mapNotNull { postImage ->
            if (postImage.imageUrl == null || postImage.isInlined) {
                // No url or image is inlined
                return@mapNotNull null
            }

            if (!canPrefetchImageType(postImage.type)) {
                // Bad type or not good enough network
                return@mapNotNull null
            }

            return@mapNotNull Prefetch(postImage, loadable)
        }
    }

    private fun canPrefetchImageType(type: PostImage.Type?): Boolean {
        if (type == null) {
            return false
        }

        return when (type) {
            PostImage.Type.STATIC,
            PostImage.Type.GIF -> shouldLoadForNetworkType(ChanSettings.imageAutoLoadNetwork.get())
            PostImage.Type.MOVIE -> shouldLoadForNetworkType(ChanSettings.videoAutoLoadNetwork.get())
            PostImage.Type.PDF,
            PostImage.Type.SWF -> false
        }.exhaustive
    }

    private fun isLoadableSuitableForPrefetch(loadable: Loadable?): Boolean {
        if (loadable == null) {
            return false
        }

        if (!ChanSettings.autoLoadThreadImages.get()) {
            // Prefetching disabled
            return false
        }

        if (loadable.isLocal || loadable.isDownloading) {
            // Cannot prefetch local threads
            return false
        }

        return true
    }

    private data class Prefetch(
            val postImage: PostImage,
            val loadable: Loadable
    )

    companion object {
        private const val TAG = "PrefetchLoader"
    }
}
