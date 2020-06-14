package com.github.adamantcheese.chan.core.loader.impl

import com.github.adamantcheese.chan.core.loader.LoaderResult
import com.github.adamantcheese.chan.core.loader.LoaderType
import com.github.adamantcheese.chan.core.loader.OnDemandContentLoader
import com.github.adamantcheese.chan.core.loader.PostLoaderData
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.data.InlinedFileInfo
import com.github.adamantcheese.model.repository.InlinedFileInfoRepository
import io.reactivex.Scheduler
import io.reactivex.Single
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.supervisorScope

class InlinedFileInfoLoader(
  private val scheduler: Scheduler,
  private val inlinedFileInfoRepository: InlinedFileInfoRepository
) : OnDemandContentLoader(LoaderType.InlinedFileInfoLoader) {

  override fun isCached(postLoaderData: PostLoaderData): Single<Boolean> {
    val inlinedFiles = postLoaderData.post.postImages.filter { postImage ->
      return@filter postImage.isInlined && postImage.imageUrl != null
    }

    return rxSingle {
      return@rxSingle inlinedFiles.all { inlinedFile ->
        inlinedFileInfoRepository.isCached(inlinedFile.imageUrl.toString())
          .unwrap()
      }
    }
      .subscribeOn(scheduler)
      .onErrorReturnItem(false)
  }

  override fun startLoading(postLoaderData: PostLoaderData): Single<LoaderResult> {
    BackgroundUtils.ensureBackgroundThread()

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
    BackgroundUtils.ensureMainThread()

    // I guess there is no real need to cancel these requests since they are lightweight
  }

  private suspend fun updateInlinedImagesFileSizes(
    inlinedImages: List<PostImage>,
    postLoaderData: PostLoaderData
  ): LoaderResult {
    BackgroundUtils.ensureBackgroundThread()

    val results = getInlinedFilesBatched(inlinedImages)
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

  private suspend fun getInlinedFilesBatched(
    inlinedImages: List<PostImage>
  ): List<ModularResult<InlinedFileInfo>> {
    BackgroundUtils.ensureBackgroundThread()

    return inlinedImages
      .chunked(MAX_CONCURRENCY)
      .flatMap { inlinedImagesChunk ->
        return@flatMap supervisorScope {
          return@supervisorScope inlinedImagesChunk.mapNotNull { inlinedImage ->
            val imageUrl = inlinedImage.imageUrl
              ?: return@mapNotNull null

            return@mapNotNull async {
              return@async inlinedFileInfoRepository.getInlinedFileInfo(imageUrl.toString())
            }
          }.awaitAll()
        }
      }
  }

  companion object {
    private const val MAX_CONCURRENCY = 4
  }
}