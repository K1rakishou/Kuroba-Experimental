package com.github.k1rakishou.chan.core.loader.impl

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader
import com.github.k1rakishou.chan.core.loader.PostLoaderData
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.InlinedFileInfo
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.LoaderType
import com.github.k1rakishou.model.repository.InlinedFileInfoRepository
import io.reactivex.Scheduler
import io.reactivex.Single
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.supervisorScope

class InlinedFileInfoLoader(
  private val scheduler: Scheduler,
  private val inlinedFileInfoRepository: InlinedFileInfoRepository,
  private val chanThreadManager: ChanThreadManager
) : OnDemandContentLoader(LoaderType.InlinedFileInfoLoader) {

  override fun isCached(postLoaderData: PostLoaderData): Single<Boolean> {
    val post = chanThreadManager.getPost(postLoaderData.postDescriptor)
    if (post == null) {
      return Single.just(false)
    }

    val inlinedFiles = post.postImages.filter { postImage ->
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

    val post = chanThreadManager.getPost(postLoaderData.postDescriptor)
    if (post == null || chanThreadManager.isContentLoadedForLoader(post.postDescriptor, loaderType)) {
      return rejected()
    }

    if (!ChanSettings.fetchInlinedFileSizes.get()) {
      return rejected()
    }

    val inlinedImages = post.postImages.filter { postImage ->
      return@filter postImage.isInlined && postImage.imageUrl != null
    }

    if (inlinedImages.isEmpty()) {
      return rejected()
    }

    return rxSingle { updateInlinedImagesFileSizes(post, inlinedImages) }
      .subscribeOn(scheduler)
      .onErrorReturnItem(LoaderResult.Failed(loaderType))
  }

  override fun cancelLoading(postLoaderData: PostLoaderData) {
    BackgroundUtils.ensureMainThread()

    // I guess there is no real need to cancel these requests since they are lightweight
  }

  private suspend fun updateInlinedImagesFileSizes(
    post: ChanPost,
    inlinedImages: List<ChanPostImage>
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

      post.updatePostImageSize(fileUrl, fileSize)
    }

    chanThreadManager.setContentLoadedForLoader(post.postDescriptor, loaderType)
    return LoaderResult.Succeeded(loaderType, true)
  }

  private suspend fun getInlinedFilesBatched(
    inlinedImages: List<ChanPostImage>
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