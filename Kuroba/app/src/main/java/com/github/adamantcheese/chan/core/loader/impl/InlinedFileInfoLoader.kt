package com.github.adamantcheese.chan.core.loader.impl

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.chan.core.loader.LoaderResult
import com.github.adamantcheese.chan.core.loader.LoaderType
import com.github.adamantcheese.chan.core.loader.OnDemandContentLoader
import com.github.adamantcheese.chan.core.loader.PostLoaderData
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.database.repository.InlinedFileInfoRepository
import io.reactivex.Scheduler
import io.reactivex.Single
import kotlinx.coroutines.rx2.rxSingle

class InlinedFileInfoLoader(
        private val scheduler: Scheduler,
        private val inlinedFileInfoRepository: InlinedFileInfoRepository
) : OnDemandContentLoader(LoaderType.InlinedFileInfoLoader) {

    override fun startLoading(postLoaderData: PostLoaderData): Single<LoaderResult> {
        if (postLoaderData.post.isContentLoadedForLoader(loaderType)) {
            return rejected()
        }

        if (!ChanSettings.fetchInlinedFileSizes.get()) {
            return rejected()
        }

        val inlinedImages = postLoaderData.post.postImages.filter { postImage ->
            return@filter postImage.isInlined && postImage.imageUrl != null
        }
        if (inlinedImages.isEmpty()) {
            return rejected()
        }

        return rxSingle { updateInlinedImagesFileSizes(inlinedImages, postLoaderData) }
                .subscribeOn(scheduler)
                .onErrorReturnItem(LoaderResult.Failed(loaderType))
    }

    override fun cancelLoading(postLoaderData: PostLoaderData) {
        // I guess there is no real need to cancel these requests since they are lightweight
    }

    private suspend fun updateInlinedImagesFileSizes(
            inlinedImages: List<PostImage>,
            postLoaderData: PostLoaderData
    ): LoaderResult {
        // TODO(ODL): batching?
        val results = inlinedImages.map { inlinedImage ->
            return@map inlinedFileInfoRepository.getInlinedFileInfo(inlinedImage.imageUrl!!.toString())
        }

        val successResults = results.filter { result ->
            return@filter result is ModularResult.Value && !result.value.isEmpty()
        }

        if (successResults.isEmpty()) {
            return LoaderResult.Failed(loaderType)
        }

        successResults.forEach { successResult ->
            check(successResult is ModularResult.Value) {
                "successResult is not Value when it shouldn't be! (${successResult::class.java.simpleName})"
            }

            val fileUrl = successResult.value.fileUrl
            val fileSize = checkNotNull(successResult.value.fileSize) {
                "fileSize is null when it shouldn't be!"
            }

            postLoaderData.post.updatePostImageSize(fileUrl, fileSize)
        }

        postLoaderData.post.setContentLoadedForLoader(loaderType)
        return LoaderResult.Succeeded(loaderType, true)
    }

}