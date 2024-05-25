package com.github.k1rakishou.chan.ui.compose.post.ui.title

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerControllerViewModel
import com.github.k1rakishou.chan.ui.compose.image.ImageLoaderRequest
import com.github.k1rakishou.chan.ui.compose.image.ImageLoaderRequestData
import com.github.k1rakishou.chan.ui.compose.image.ImageLoaderRequestProvider
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellMediaState
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

private const val TAG = "PostCellTitleShared"

internal fun getImageLoaderRequestProvider(
  chanDescriptor: ChanDescriptor?,
  postCellMediaState: PostCellMediaState
): ImageLoaderRequestProvider {
  val cacheHandler = appDependencies().cacheHandler
  val chanThreadsCache = appDependencies().chanThreadsCache
  val revealedSpoilerImagesManager = appDependencies().revealedSpoilerImagesManager

  return ImageLoaderRequestProvider(
    key = ImageLoaderRequestProvider.FullKey(arrayOf(chanDescriptor, postCellMediaState)),
    provide = {
      if (chanDescriptor == null) {
        return@ImageLoaderRequestProvider null
      }

      return@ImageLoaderRequestProvider withContext(Dispatchers.IO) {
        val postImage = chanThreadsCache.getPostFromCache(chanDescriptor, postCellMediaState.postDescriptor)
          ?.firstPostImageOrNull { chanPostImage ->
            return@firstPostImageOrNull chanPostImage.actualThumbnailUrl == postCellMediaState.thumbnailImageUrl
              && chanPostImage.imageUrl == postCellMediaState.fullImageUrl
          }

        if (postImage == null) {
          Logger.error(TAG) { "getImageLoaderRequestProvider() failed to find postImage for ${postCellMediaState}" }
          return@withContext null
        }

        val revealSpoilerImage = revealedSpoilerImagesManager.isImageSpoilerImageRevealed(postImage)

        val (imageUrl, cacheFileType) = getImageUrlAndCacheFileType(
          cacheHandler = cacheHandler,
          postImage = postImage,
          canUseHighResCells = true,
          revealSpoilerImage = revealSpoilerImage
        )

        if (imageUrl == null || cacheFileType == null) {
          Logger.error(TAG) {
            "getImageLoaderRequestProvider() failed to determine which imageUrl or cacheFileType to use " +
              "(imageUrl: ${imageUrl}, cacheFileType: ${cacheFileType})"
          }

          return@withContext null
        }

        return@withContext ImageLoaderRequest(
          data = ImageLoaderRequestData.Url(
            httpUrl = imageUrl,
            cacheFileType = cacheFileType
          ),
          transformations = emptyList()
        )
      }
    }
  )
}

private fun getImageUrlAndCacheFileType(
  cacheHandler: CacheHandler,
  postImage: ChanPostImage,
  canUseHighResCells: Boolean,
  revealSpoilerImage: Boolean
): Pair<HttpUrl?, CacheFileType?> {
  val thumbnailUrl = postImage.getThumbnailUrl(isSpoilerRevealed = revealSpoilerImage)
  if (thumbnailUrl == null) {
    Logger.e(TAG, "getUrl() postImage: $postImage, has no thumbnail url")
    return null to null
  }

  val highRes = postImage.imageUrl != null
    && ChanSettings.highResCells.get()
    && postImage.canBeUsedAsHighResolutionThumbnail()
    && canUseHighResCells
    && postImage.type == ChanPostImageType.STATIC
    && MediaViewerControllerViewModel.canAutoLoad(cacheHandler, postImage)

  if (!highRes) {
    return thumbnailUrl to CacheFileType.PostMediaThumbnail
  }

  Logger.verbose(TAG) { "getUrl() using high-res thumbnail for ${postImage.actualThumbnailUrl}" }
  return postImage.imageUrl to CacheFileType.PostMediaFull
}