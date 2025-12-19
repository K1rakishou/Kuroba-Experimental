package com.github.k1rakishou.chan.features.album

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerControllerViewModel
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.data.ChanDescriptorUi
import com.github.k1rakishou.chan.ui.compose.image.ImageLoaderRequest
import com.github.k1rakishou.chan.ui.compose.image.ImageLoaderRequestData
import com.github.k1rakishou.chan.ui.compose.image.ImageLoaderRequestProvider
import com.github.k1rakishou.chan.ui.compose.image.KurobaComposePostImageIndicators
import com.github.k1rakishou.chan.ui.compose.image.KurobaComposePostImageThumbnail
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

private const val TAG = "AlbumItem"
private const val TransparentBlackColorAlpha = 0.4f
private const val HighResCellsMaxSpanCountPhone = 3
private const val HighResCellsMaxSpanCountTablet = 5

@Composable
fun AlbumItem(
  modifier: Modifier,
  isInSelectionMode: Boolean,
  isSelected: Boolean,
  isNsfwModeEnabled: Boolean,
  showAlbumViewsImageDetails: Boolean,
  albumSpanCount: Int,
  controllerKey: ControllerKey,
  chanDescriptorUi: ChanDescriptorUi?,
  albumItemData: AlbumItemData,
  downloadingAlbumItem: DownloadingAlbumItem?,
  onClick: (AlbumItemData) -> Unit,
  onLongClick: (AlbumItemData) -> Unit,
  clearDownloadingAlbumItemState: (DownloadingAlbumItem) -> Unit
) {
  val onDemandContentLoaderManager = appDependencies().onDemandContentLoaderManager

  DisposableEffect(key1 = Unit) {
    onDemandContentLoaderManager.onPostBind(albumItemData.postDescriptor, albumItemData.isCatalogMode)
    onDispose { onDemandContentLoaderManager.onPostUnbind(albumItemData.postDescriptor, true) }
  }

  val requestProvider = remember(chanDescriptorUi, albumItemData, albumSpanCount, isInSelectionMode) {
    getImageLoaderRequestProvider(
      chanDescriptor = chanDescriptorUi?.chanDescriptor,
      albumItemData = albumItemData,
      albumSpanCount = albumSpanCount,
      isInSelectionMode = isInSelectionMode
    )
  }

  Box(
    modifier = modifier
      .then(
        Modifier.albumItemSelection(isInSelectionMode, isSelected)
      )
  ) {
    KurobaComposePostImageThumbnail(
      modifier = Modifier.fillMaxWidth(),
      controllerKey = controllerKey,
      postImageThumbnailKey = albumItemData.albumItemDataKey,
      requestProvider = requestProvider,
      mediaType = albumItemData.mediaType,
      isNsfwModeEnabled = isNsfwModeEnabled,
      displayErrorMessage = true,
      showShimmerEffectWhenLoading = true,
      onClick = { onClick(albumItemData) },
      onLongClick = { onLongClick(albumItemData) }
    )

    DownloadingAlbumItemOverlay(
      modifier = Modifier.fillMaxSize(),
      downloadingAlbumItem = downloadingAlbumItem,
      clearDownloadingAlbumItemState = clearDownloadingAlbumItemState
    )

    if (chanDescriptorUi != null && albumItemData.fullImageUrlString != null) {
      KurobaComposePostImageIndicators(
        modifier = Modifier
          .align(Alignment.TopStart),
        chanDescriptor = chanDescriptorUi.chanDescriptor,
        postDescriptor = albumItemData.postDescriptor,
        imageFullUrlString = albumItemData.fullImageUrlString,
        backgroundAlpha = TransparentBlackColorAlpha
      )
    }

    if (showAlbumViewsImageDetails && albumItemData.albumItemPostData?.isNotEmpty() == true) {
      AlbumItemInfo(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .align(Alignment.BottomCenter),
        albumItemPostData = albumItemData.albumItemPostData
      )
    }
  }
}

@Composable
private fun AlbumItemInfo(
  modifier: Modifier,
  albumItemPostData: AlbumViewControllerViewModel.AlbumItemPostData
) {
  Column(
    modifier = modifier
      .drawBehind { drawRect(Color.Black.copy(alpha = TransparentBlackColorAlpha)) }
      .padding(horizontal = 2.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (albumItemPostData.threadSubject != null) {
      KurobaComposeText(
        text = albumItemPostData.threadSubject,
        color = Color.White,
        maxLines = 1,
        fontSize = 10.ktu,
        overflow = TextOverflow.Ellipsis
      )
    }

    if (albumItemPostData.mediaInfo != null) {
      KurobaComposeText(
        text = albumItemPostData.mediaInfo,
        color = Color.White,
        maxLines = 1,
        fontSize = 10.ktu,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun DownloadingAlbumItemOverlay(
  modifier: Modifier,
  downloadingAlbumItem: DownloadingAlbumItem?,
  clearDownloadingAlbumItemState: (DownloadingAlbumItem) -> Unit
) {
  if (downloadingAlbumItem == null) {
    return
  }

  LaunchedEffect(key1 = downloadingAlbumItem.state) {
    when (downloadingAlbumItem.state) {
      DownloadingAlbumItem.State.Enqueued,
      DownloadingAlbumItem.State.Downloading -> {
        // no-op
      }
      DownloadingAlbumItem.State.Downloaded,
      DownloadingAlbumItem.State.FailedToDownload,
      DownloadingAlbumItem.State.Canceled -> {
        delay(2000)
        clearDownloadingAlbumItemState(downloadingAlbumItem)
      }
      DownloadingAlbumItem.State.Deleted -> {
        clearDownloadingAlbumItemState(downloadingAlbumItem)
      }
    }
  }

  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    Box {
      val painter = when (downloadingAlbumItem.state) {
        DownloadingAlbumItem.State.Enqueued -> {
          painterResource(id = R.drawable.ic_download_anim0)
        }
        DownloadingAlbumItem.State.Downloading -> {
          var animationAtEnd by remember { mutableStateOf(false) }

          LaunchedEffect(downloadingAlbumItem.state) {
            while (isActive) {
              animationAtEnd = !animationAtEnd
              delay(1500)
            }
          }

          rememberAnimatedVectorPainter(
            animatedImageVector = AnimatedImageVector.animatedVectorResource(id = R.drawable.ic_download_anim),
            atEnd = animationAtEnd
          )
        }
        DownloadingAlbumItem.State.Downloaded -> {
          painterResource(id = R.drawable.ic_download_anim1)
        }
        DownloadingAlbumItem.State.FailedToDownload,
        DownloadingAlbumItem.State.Canceled -> {
          painterResource(id = R.drawable.ic_baseline_warning_24)
        }
        DownloadingAlbumItem.State.Deleted -> {
          null
        }
      }

      if (painter != null) {
        Image(
          modifier = Modifier
            .size(40.dp)
            .drawBehind { drawCircle(color = Color.Black.copy(alpha = TransparentBlackColorAlpha)) }
            .padding(4.dp),
          painter = painter,
          contentDescription = null,
        )
      }
    }
  }
}

private fun Modifier.albumItemSelection(
  isInSelectionMode: Boolean,
  isSelected: Boolean,
): Modifier {
  return composed {
    val chanTheme = LocalChanTheme.current

    val inSelectionModeAndSelected = isInSelectionMode && isSelected
    val inSelectionModeAndNotSelected = isInSelectionMode && !isSelected

    val scaleAnimated = animateFloatAsState(
      targetValue = if (inSelectionModeAndSelected) 0.9f else 1f
    )
    val borderColorAnimated = animateColorAsState(
      targetValue = if (inSelectionModeAndSelected) chanTheme.accentColorCompose else Color.Transparent
    )
    val unselectedImageTintAlphaAnimated = animateFloatAsState(
      targetValue = if (inSelectionModeAndNotSelected) 0.6f else 0f
    )

    return@composed graphicsLayer {
      scaleX = scaleAnimated.value
      scaleY = scaleAnimated.value
    }.drawWithContent {
      drawContent()

      drawRect(
        color = chanTheme.backColorCompose,
        alpha = unselectedImageTintAlphaAnimated.value
      )

      drawRect(
        color = borderColorAnimated.value,
        style = Stroke(width = 2.dp.toPx())
      )
    }
  }
}

private fun getImageLoaderRequestProvider(
  chanDescriptor: ChanDescriptor?,
  albumItemData: AlbumItemData,
  albumSpanCount: Int,
  isInSelectionMode: Boolean
): ImageLoaderRequestProvider {
  val cacheHandler = appDependencies().cacheHandler
  val chanThreadsCache = appDependencies().chanThreadsCache
  val revealedSpoilerImagesManager = appDependencies().revealedSpoilerImagesManager

  return ImageLoaderRequestProvider(
    key = ImageLoaderRequestProvider.FullKey(arrayOf(chanDescriptor, albumItemData, albumItemData, isInSelectionMode)),
    provide = {
      if (chanDescriptor == null) {
        return@ImageLoaderRequestProvider null
      }

      return@ImageLoaderRequestProvider withContext(Dispatchers.IO) {
        val postImage = chanThreadsCache.getPostFromCache(chanDescriptor, albumItemData.postDescriptor)
          ?.firstPostImageOrNull { chanPostImage ->
            return@firstPostImageOrNull chanPostImage.actualThumbnailUrl == albumItemData.thumbnailImageUrl
              && chanPostImage.imageUrl == albumItemData.fullImageUrl
          }

        if (postImage == null) {
          Logger.error(TAG) { "getImageLoaderRequestProvider() failed to find postImage for ${albumItemData}" }
          return@withContext null
        }

        val revealSpoilerImage = revealedSpoilerImagesManager.isImageSpoilerImageRevealed(postImage)

        val canUseHighResCells = if (AppModuleAndroidUtils.isTablet) {
          albumSpanCount <= HighResCellsMaxSpanCountTablet
        } else {
          albumSpanCount <= HighResCellsMaxSpanCountPhone
        }

        val (imageUrl, cacheFileType) = getImageUrlAndCacheFileType(
          cacheHandler = cacheHandler,
          postImage = postImage,
          canUseHighResCells = canUseHighResCells,
          revealSpoilerImage = revealSpoilerImage || isInSelectionMode
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