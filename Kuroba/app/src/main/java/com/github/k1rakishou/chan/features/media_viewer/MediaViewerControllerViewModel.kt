package com.github.k1rakishou.chan.features.media_viewer

import androidx.lifecycle.ViewModel
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.shouldLoadForNetworkType
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class MediaViewerControllerViewModel : ViewModel() {

  @Inject
  lateinit var chanThreadManager: ChanThreadManager

  private val _mediaViewerState = MutableStateFlow<MediaViewerControllerState?>(null)
  private val _transitionInfoFlow = MutableSharedFlow<ViewableMediaParcelableHolder.TransitionInfo?>(extraBufferCapacity = 1)

  private var lastPagerIndex = -1

  val transitionInfoFlow: SharedFlow<ViewableMediaParcelableHolder.TransitionInfo?>
    get() = _transitionInfoFlow.asSharedFlow()
  val mediaViewerState: StateFlow<MediaViewerControllerState?>
    get() = _mediaViewerState.asStateFlow()

  suspend fun showMedia(
    isNotActivityRecreation: Boolean,
    viewableMediaParcelableHolder: ViewableMediaParcelableHolder
  ): Boolean {
    if (isNotActivityRecreation) {
      val transitionInfo = when (viewableMediaParcelableHolder) {
        is ViewableMediaParcelableHolder.CatalogMediaParcelableHolder -> {
          viewableMediaParcelableHolder.transitionInfo
        }
        is ViewableMediaParcelableHolder.ThreadMediaParcelableHolder -> {
          viewableMediaParcelableHolder.transitionInfo
        }
        is ViewableMediaParcelableHolder.LocalMediaParcelableHolder -> null
        is ViewableMediaParcelableHolder.RemoteMediaParcelableHolder -> null
      }

      _transitionInfoFlow.emit(transitionInfo)
    } else {
      _transitionInfoFlow.emit(null)
    }

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

  fun updateLastViewedIndex(newLastViewedIndex: Int) {
    lastPagerIndex = newLastViewedIndex
  }

  private fun collectThreadMedia(
    viewableMediaParcelableHolder: ViewableMediaParcelableHolder.ThreadMediaParcelableHolder
  ): MediaViewerControllerState? {
    val initialPagerIndex = AtomicInteger(0)
    val scrollToImageWithUrl = viewableMediaParcelableHolder.initialImageUrl?.toHttpUrlOrNull()

    val mediaList = chanThreadManager.getChanThread(viewableMediaParcelableHolder.threadDescriptor)
      ?.let { chanThread ->
        val mediaList = mutableListWithCap<ViewableMedia>(chanThread.postsCount)
        val mediaIndex = AtomicInteger(0)

        chanThread.iteratePostsOrdered { chanPost ->
          chanPost.iteratePostImages { chanPostImage ->
            val viewableMedia = processChanPostImage(
              chanPostImage = chanPostImage,
              scrollToImageWithUrl = scrollToImageWithUrl,
              lastViewedIndex = initialPagerIndex,
              mediaIndex = mediaIndex
            )

            if (viewableMedia != null) {
              mediaList += viewableMedia
            }
          }
        }

        return@let mediaList
      }

    if (mediaList.isNullOrEmpty()) {
      return null
    }

    val actualInitialPagerIndex = if (lastPagerIndex >= 0) {
      lastPagerIndex
    } else {
      initialPagerIndex.get()
    }

    return MediaViewerControllerState(mediaList, actualInitialPagerIndex)
  }

  private fun collectCatalogMedia(
    viewableMediaParcelableHolder: ViewableMediaParcelableHolder.CatalogMediaParcelableHolder
  ): MediaViewerControllerState? {
    val initialPagerIndex = AtomicInteger(0)
    val scrollToImageWithUrl = viewableMediaParcelableHolder.initialImageUrl?.toHttpUrlOrNull()

    val mediaList = chanThreadManager.getChanCatalog(viewableMediaParcelableHolder.catalogDescriptor)
      ?.let { chanCatalog ->
        val mediaList = mutableListWithCap<ViewableMedia>(chanCatalog.postsCount())
        val mediaIndex = AtomicInteger(0)

        chanCatalog.iteratePostsOrdered { chanOriginalPost ->
          chanOriginalPost.iteratePostImages { chanPostImage ->
            val viewableMedia = processChanPostImage(chanPostImage, scrollToImageWithUrl, initialPagerIndex, mediaIndex)
            if (viewableMedia != null) {
              mediaList += viewableMedia
            }
          }
        }

        return@let mediaList
      }

    if (mediaList.isNullOrEmpty()) {
      return null
    }

    val actualInitialPagerIndex = if (lastPagerIndex >= 0) {
      lastPagerIndex
    } else {
      initialPagerIndex.get()
    }

    return MediaViewerControllerState(mediaList, actualInitialPagerIndex)
  }

  private fun processChanPostImage(
    chanPostImage: ChanPostImage,
    scrollToImageWithUrl: HttpUrl?,
    lastViewedIndex: AtomicInteger,
    mediaIndex: AtomicInteger
  ): ViewableMedia? {
    val imageLocation = chanPostImage.imageUrl
      ?.let { imageUrl -> MediaLocation.Remote(imageUrl) }

    if (imageLocation == null) {
      // No actual image, nothing to do
      return null
    }

    val previewLocation = chanPostImage.actualThumbnailUrl
      ?.let { thumbnailUrl -> MediaLocation.Remote(thumbnailUrl) }
      ?: MediaLocation.Remote(DEFAULT_THUMBNAIL)

    val spoilerLocation = chanPostImage.spoilerThumbnailUrl
      ?.let { spoilerUrl -> MediaLocation.Remote(spoilerUrl) }

    val viewableMediaMeta = ViewableMediaMeta(
      mediaName = chanPostImage.filename ?: chanPostImage.serverFilename,
      mediaWidth = chanPostImage.imageWidth,
      mediaHeight = chanPostImage.imageHeight,
      mediaSize = chanPostImage.size,
      mediaHash = chanPostImage.fileHash,
      isSpoiler = chanPostImage.spoiler,
      inlined = chanPostImage.isInlined
    )

    val viewableMedia = when (chanPostImage.type) {
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
      lastViewedIndex.set(mediaIndex.get())
    }

    mediaIndex.incrementAndGet()
    return viewableMedia
  }

  data class MediaViewerControllerState(
    val loadedMedia: List<ViewableMedia>,
    val initialPagerIndex: Int = 0
  ) {
    fun isEmpty(): Boolean = loadedMedia.isEmpty()
  }

  companion object {
    private const val TAG = "MediaViewerControllerViewModel"
    private val DEFAULT_THUMBNAIL = (AppConstants.RESOURCES_ENDPOINT + "internal_spoiler.png").toHttpUrl()

    fun canAutoLoad(cacheHandler: CacheHandler, postImage: ChanPostImage): Boolean {
      return canAutoLoad(cacheHandler, postImage.isInlined, postImage.imageUrl, postImage.type)
    }

    fun canAutoLoad(cacheHandler: CacheHandler, viewableMedia: ViewableMedia): Boolean {
      val mediaLocation = viewableMedia.mediaLocation
      if (mediaLocation !is MediaLocation.Remote) {
        return false
      }

      val isInlined = viewableMedia.viewableMediaMeta.inlined
      val url = mediaLocation.url

      val imageType = when (viewableMedia) {
        is ViewableMedia.Gif -> ChanPostImageType.GIF
        is ViewableMedia.Image -> ChanPostImageType.STATIC
        is ViewableMedia.Video -> ChanPostImageType.MOVIE
        is ViewableMedia.Unsupported -> return false
      }

      return canAutoLoad(cacheHandler, isInlined, url, imageType)
    }

    fun canAutoLoad(
      cacheHandler: CacheHandler,
      isInlined: Boolean,
      url: HttpUrl?,
      imageType: ChanPostImageType?
    ): Boolean {
      if (isInlined) {
        return false
      }

      val imageUrl = url ?: return false
      val postImageType = imageType ?: return false

      if (cacheHandler.cacheFileExists(imageUrl.toString())) {
        // Auto load the image when it is cached
        return true
      }

      return when (postImageType) {
        ChanPostImageType.GIF,
        ChanPostImageType.STATIC -> shouldLoadForNetworkType(ChanSettings.imageAutoLoadNetwork.get())
        ChanPostImageType.MOVIE -> shouldLoadForNetworkType(ChanSettings.videoAutoLoadNetwork.get())
        ChanPostImageType.PDF,
        ChanPostImageType.SWF -> false
        else -> throw IllegalArgumentException("Not handled " + postImageType.name)
      }
    }
  }

}