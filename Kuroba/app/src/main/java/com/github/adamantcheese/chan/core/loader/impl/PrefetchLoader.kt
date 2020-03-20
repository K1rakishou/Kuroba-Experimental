package com.github.adamantcheese.chan.core.loader.impl

import com.github.adamantcheese.chan.core.cache.FileCacheListener
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
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.exhaustive
import com.github.k1rakishou.fsaf.file.RawFile
import io.reactivex.Single

class PrefetchLoader(
        private val fileCacheV2: FileCacheV2
) : OnDemandContentLoader(LoaderType.PrefetchLoader) {

    override fun startLoading(postLoaderData: PostLoaderData): Single<LoaderResult> {
        BackgroundUtils.ensureBackgroundThread()

        if (postLoaderData.post.isContentLoadedForLoader(loaderType)) {
            return rejected()
        }

        val post = postLoaderData.post
        val loadable = postLoaderData.loadable

        if (post.postImages.isEmpty()) {
            return rejected()
        }

        if (!isLoadableSuitableForPrefetch(loadable)) {
            return rejected()
        }

        val prefetchList = getPrefetchBatch(post, loadable)
        if (prefetchList.isEmpty()) {
            return rejected()
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

            cancelableDownload.addCallback(object : FileCacheListener() {
                override fun onSuccess(file: RawFile?) {
                    postLoaderData.post.setContentLoadedForLoader(loaderType)
                }
            })
            postLoaderData.addDisposeFunc { cancelableDownload.cancelPrefetch() }
        }

        // Always false for prefetches because there is nothing in the view that we need to update
        // after doing a prefetch
        return succeeded(false)
    }

    override fun cancelLoading(postLoaderData: PostLoaderData) {
        BackgroundUtils.ensureMainThread()

        return postLoaderData.disposeAll()
    }

    private fun getPrefetchBatch(post: Post, loadable: Loadable): List<Prefetch> {
        BackgroundUtils.ensureBackgroundThread()

        return post.postImages.mapNotNull { postImage ->
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
        BackgroundUtils.ensureBackgroundThread()

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
        BackgroundUtils.ensureBackgroundThread()

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
