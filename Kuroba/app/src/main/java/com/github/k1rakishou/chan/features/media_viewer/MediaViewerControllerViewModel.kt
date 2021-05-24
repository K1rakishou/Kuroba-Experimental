package com.github.k1rakishou.chan.features.media_viewer

import androidx.lifecycle.ViewModel
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.post.ChanPostImageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

class MediaViewerControllerViewModel : ViewModel() {

  @Inject
  lateinit var chanThreadManager: ChanThreadManager

  private val _mediaViewerState = MutableStateFlow<MediaViewerControllerState?>(null)

  val mediaViewerState: StateFlow<MediaViewerControllerState?>
    get() = _mediaViewerState.asStateFlow()

  fun showMedia(viewableMediaParcelableHolder: ViewableMediaParcelableHolder): Boolean {
    val mediaViewerControllerState = when (viewableMediaParcelableHolder) {
      is ViewableMediaParcelableHolder.CatalogMediaParcelableHolder -> {
        collectCatalogMedia(viewableMediaParcelableHolder)
      }
      is ViewableMediaParcelableHolder.ThreadMediaParcelableHolder -> {
        collectThreadMedia(viewableMediaParcelableHolder)
      }
      is ViewableMediaParcelableHolder.LocalMediaParcelableHolder -> TODO()
      is ViewableMediaParcelableHolder.RemoteMediaParcelableHolder -> TODO()
    }

    if (mediaViewerControllerState == null || mediaViewerControllerState.isEmpty()) {
      return false
    }

    _mediaViewerState.value = mediaViewerControllerState
    return true
  }

  private fun collectThreadMedia(
    viewableMediaParcelableHolder: ViewableMediaParcelableHolder.ThreadMediaParcelableHolder
  ): MediaViewerControllerState? {
    var lastViewedIndex = 0
    val scrollToImageWithUrl = viewableMediaParcelableHolder.scrollToImageWithUrl?.toHttpUrlOrNull()

    val mediaList = chanThreadManager.getChanThread(viewableMediaParcelableHolder.threadDescriptor)
      ?.let { chanThread ->
        val mediaList = mutableListWithCap<ViewableMedia>(chanThread.postsCount)
        var mediaIndex = 0

        chanThread.iteratePostsOrdered { chanPost ->
          chanPost.iteratePostImages { chanPostImage ->
            val imageLocation = chanPostImage.imageUrl
              ?.let { imageUrl -> MediaLocation.Remote(imageUrl) }
            // No actual image, nothing to do
              ?: return@iteratePostImages

            val previewLocation = chanPostImage.actualThumbnailUrl
              ?.let { thumbnailUrl -> MediaLocation.Remote(thumbnailUrl) }
              ?: MediaLocation.Remote(DEFAULT_THUMBNAIL)

            val spoilerLocation = chanPostImage.spoilerThumbnailUrl
              ?.let { spoilerUrl -> MediaLocation.Remote(spoilerUrl) }

            val viewableMediaMeta = ViewableMediaMeta(
              chanPostImage.filename ?: chanPostImage.serverFilename,
              chanPostImage.imageWidth,
              chanPostImage.imageHeight,
              chanPostImage.size
            )

            mediaList += when (chanPostImage.type) {
              ChanPostImageType.STATIC -> {
                ViewableMedia.Image(imageLocation, previewLocation, spoilerLocation, viewableMediaMeta)
              }
              ChanPostImageType.GIF -> {
                ViewableMedia.Gif(imageLocation, previewLocation, spoilerLocation, viewableMediaMeta)
              }
              ChanPostImageType.MOVIE -> {
                ViewableMedia.Video(imageLocation, previewLocation, spoilerLocation, viewableMediaMeta)
              }
              ChanPostImageType.PDF,
              ChanPostImageType.SWF,
              null -> {
                ViewableMedia.Unsupported(imageLocation, previewLocation, spoilerLocation, viewableMediaMeta)
              }
            }

            if (scrollToImageWithUrl != null && chanPostImage.imageUrl == scrollToImageWithUrl) {
              lastViewedIndex = mediaIndex
            }

            ++mediaIndex
          }
        }

        return@let mediaList
      }

    if (mediaList.isNullOrEmpty()) {
      return null
    }

    return MediaViewerControllerState(mediaList, lastViewedIndex)
  }

  private fun collectCatalogMedia(
    viewableMediaParcelableHolder: ViewableMediaParcelableHolder.CatalogMediaParcelableHolder
  ): MediaViewerControllerState? {
    var lastViewedIndex = 0
    val scrollToImageWithUrl = viewableMediaParcelableHolder.scrollToImageWithUrl?.toHttpUrlOrNull()

    val mediaList = chanThreadManager.getChanCatalog(viewableMediaParcelableHolder.catalogDescriptor)
      ?.let { chanCatalog ->
        val mediaList = mutableListWithCap<ViewableMedia>(chanCatalog.postsCount())
        var mediaIndex = 0

        chanCatalog.iteratePostsOrdered { chanOriginalPost ->
          chanOriginalPost.iteratePostImages { chanPostImage ->
            val imageLocation = chanPostImage.imageUrl
              ?.let { imageUrl -> MediaLocation.Remote(imageUrl) }
            // No actual image, nothing to do
              ?: return@iteratePostImages

            val previewLocation = chanPostImage.actualThumbnailUrl
              ?.let { thumbnailUrl -> MediaLocation.Remote(thumbnailUrl) }
              ?: MediaLocation.Remote(DEFAULT_THUMBNAIL)

            val spoilerLocation = chanPostImage.spoilerThumbnailUrl
              ?.let { spoilerUrl -> MediaLocation.Remote(spoilerUrl) }

            val viewableMediaMeta = ViewableMediaMeta(
              chanPostImage.filename ?: chanPostImage.serverFilename,
              chanPostImage.imageWidth,
              chanPostImage.imageHeight,
              chanPostImage.size
            )

            mediaList += when (chanPostImage.type) {
              ChanPostImageType.STATIC -> {
                ViewableMedia.Image(imageLocation, previewLocation, spoilerLocation, viewableMediaMeta)
              }
              ChanPostImageType.GIF -> {
                ViewableMedia.Gif(imageLocation, previewLocation, spoilerLocation, viewableMediaMeta)
              }
              ChanPostImageType.MOVIE -> {
                ViewableMedia.Video(imageLocation, previewLocation, spoilerLocation, viewableMediaMeta)
              }
              ChanPostImageType.PDF,
              ChanPostImageType.SWF,
              null -> {
                ViewableMedia.Unsupported(imageLocation, previewLocation, spoilerLocation, viewableMediaMeta)
              }
            }

            if (scrollToImageWithUrl != null && chanPostImage.imageUrl == scrollToImageWithUrl) {
              lastViewedIndex = mediaIndex
            }

            ++mediaIndex
          }
        }

        return@let mediaList
      }

    if (mediaList.isNullOrEmpty()) {
      return null
    }

    return MediaViewerControllerState(mediaList, lastViewedIndex)
  }

  data class MediaViewerControllerState(
    val loadedMedia: List<ViewableMedia>,
    var lastViewedIndex: Int = 0 // TODO(KurobaEx): update the index when swiping the pager
  ) {

    fun isEmpty(): Boolean = loadedMedia.isEmpty()

  }

  companion object {
    private val DEFAULT_THUMBNAIL = (AppConstants.RESOURCES_ENDPOINT + "internal_spoiler.png").toHttpUrl()
  }

}