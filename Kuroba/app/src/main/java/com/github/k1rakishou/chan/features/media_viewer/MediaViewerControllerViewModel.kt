package com.github.k1rakishou.chan.features.media_viewer

import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.usecase.FilterOutHiddenImagesUseCase
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaViewState
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.shouldLoadForNetworkType
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageType
import com.github.k1rakishou.persist_state.PersistableChanState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class MediaViewerControllerViewModel : ViewModel() {

  @Inject
  lateinit var chanThreadManager: ChanThreadManager
  @Inject
  lateinit var filterOutHiddenImagesUseCase: FilterOutHiddenImagesUseCase

  private val _mediaViewerState = MutableStateFlow<MediaViewerControllerState?>(null)
  private val _transitionInfoFlow = MutableSharedFlow<ViewableMediaParcelableHolder.TransitionInfo?>(extraBufferCapacity = 1)
  private val mediaViewStateCache = LruCache<MediaLocation, MediaViewState>(PersistableChanState.mediaViewerOffscreenItemsCount.get())

  private var lastPagerIndex = -1

  private val defaultMuteState: Boolean
    get() = ChanSettings.videoDefaultMuted.get()
      && (ChanSettings.headsetDefaultMuted.get() || !AndroidUtils.getAudioManager().isWiredHeadsetOn)

  private var _isSoundMuted = defaultMuteState
  val isSoundMuted: Boolean
    get() = _isSoundMuted

  val transitionInfoFlow: SharedFlow<ViewableMediaParcelableHolder.TransitionInfo?>
    get() = _transitionInfoFlow.asSharedFlow()
  val mediaViewerState: StateFlow<MediaViewerControllerState?>
    get() = _mediaViewerState.asStateFlow()

  fun toggleIsSoundMuted() {
    _isSoundMuted = _isSoundMuted.not()
  }

  fun updateLastViewedIndex(newLastViewedIndex: Int) {
    synchronized(this) { lastPagerIndex = newLastViewedIndex }
  }

  fun storeMediaViewState(mediaLocation: MediaLocation, mediaViewState: MediaViewState?) {
    if (mediaViewState == null) {
      mediaViewStateCache.remove(mediaLocation)
      return
    }

    mediaViewStateCache.put(mediaLocation, mediaViewState.clone())
  }

  fun getPrevMediaViewStateOrNull(mediaLocation: MediaLocation): MediaViewState? {
    return mediaViewStateCache.get(mediaLocation)
  }

  suspend fun showMedia(
    isNotActivityRecreation: Boolean,
    viewableMediaParcelableHolder: ViewableMediaParcelableHolder
  ): Boolean {
    return withContext(Dispatchers.Default) {
      BackgroundUtils.ensureBackgroundThread()

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
        is ViewableMediaParcelableHolder.LocalMediaParcelableHolder -> {
          // TODO(KurobaEx):
          null
        }
        is ViewableMediaParcelableHolder.RemoteMediaParcelableHolder -> {
          collectMediaFromUrls(viewableMediaParcelableHolder)
        }
      }

      // TODO: 6/2/2021 filter out hidden images

      if (mediaViewerControllerState == null || mediaViewerControllerState.isEmpty()) {
        return@withContext false
      }

      _mediaViewerState.value = mediaViewerControllerState
      return@withContext true
    }
  }

  private fun collectMediaFromUrls(
    viewableMediaParcelableHolder: ViewableMediaParcelableHolder.RemoteMediaParcelableHolder
  ): MediaViewerControllerState? {
    val viewableMediaList = viewableMediaParcelableHolder.urlList.mapNotNull { url ->
      val actualUrl = url.toHttpUrlOrNull()
        ?: return@mapNotNull null

      val mediaLocation = MediaLocation.Remote(actualUrl)
      val fileName = actualUrl.pathSegments.lastOrNull()
        ?.let { fileName -> StringUtils.removeExtensionFromFileName(fileName) }
      val extension = StringUtils.extractFileNameExtension(url)

      val meta = ViewableMediaMeta(
        ownerPostDescriptor = null,
        serverMediaName = fileName,
        originalMediaName = null,
        extension = extension,
        mediaWidth = null,
        mediaHeight = null,
        mediaSize = null,
        mediaHash = null,
        isSpoiler = false
      )

      if (extension == null) {
        return@mapNotNull ViewableMedia.Unsupported(mediaLocation, null, null, meta)
      }

      val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: return@mapNotNull ViewableMedia.Unsupported(mediaLocation, null, null, meta)

      if (mimeType.startsWith("video/")) {
        return@mapNotNull ViewableMedia.Video(mediaLocation, null, null, meta)
      } else if (mimeType.startsWith("image/")) {
        if (mimeType.endsWith("gif")) {
          return@mapNotNull ViewableMedia.Gif(mediaLocation, null, null, meta)
        } else {
          return@mapNotNull ViewableMedia.Image(mediaLocation, null, null, meta)
        }
      }

      return@mapNotNull ViewableMedia.Unsupported(mediaLocation, null, null, meta)
    }

    if (viewableMediaList.isEmpty()) {
      return null
    }

    return MediaViewerControllerState(loadedMedia = viewableMediaList, initialPagerIndex = 0)
  }

  private fun collectThreadMedia(
    viewableMediaParcelableHolder: ViewableMediaParcelableHolder.ThreadMediaParcelableHolder
  ): MediaViewerControllerState? {
    BackgroundUtils.ensureBackgroundThread()

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

    val actualInitialPagerIndex = synchronized(this) {
      if (lastPagerIndex >= 0) {
        lastPagerIndex
      } else {
        initialPagerIndex.get()
      }
    }

    return MediaViewerControllerState(mediaList, actualInitialPagerIndex)
  }

  private fun collectCatalogMedia(
    viewableMediaParcelableHolder: ViewableMediaParcelableHolder.CatalogMediaParcelableHolder
  ): MediaViewerControllerState? {
    BackgroundUtils.ensureBackgroundThread()

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

    val actualInitialPagerIndex = synchronized(this) {
      if (lastPagerIndex >= 0) {
        lastPagerIndex
      } else {
        initialPagerIndex.get()
      }
    }

    return MediaViewerControllerState(mediaList, actualInitialPagerIndex)
  }

  private fun processChanPostImage(
    chanPostImage: ChanPostImage,
    scrollToImageWithUrl: HttpUrl?,
    lastViewedIndex: AtomicInteger,
    mediaIndex: AtomicInteger
  ): ViewableMedia? {
    BackgroundUtils.ensureBackgroundThread()

    val imageLocation = chanPostImage.imageUrl
      ?.let { imageUrl -> MediaLocation.Remote(imageUrl) }

    if (imageLocation == null) {
      // No actual image, nothing to do
      return null
    }

    val previewLocation = chanPostImage.actualThumbnailUrl
      ?.let { thumbnailUrl -> MediaLocation.Remote(thumbnailUrl) }
      ?: MediaLocation.Remote(DEFAULT_THUMBNAIL)

    val spoilerLocation = if (chanPostImage.spoiler) {
      chanPostImage.spoilerThumbnailUrl
        ?.let { spoilerUrl -> MediaLocation.Remote(spoilerUrl) }
    } else {
      null
    }

    val viewableMediaMeta = ViewableMediaMeta(
      ownerPostDescriptor = chanPostImage.ownerPostDescriptor,
      serverMediaName = chanPostImage.serverFilename,
      originalMediaName = chanPostImage.filename,
      extension = chanPostImage.extension,
      mediaWidth = chanPostImage.imageWidth,
      mediaHeight = chanPostImage.imageHeight,
      mediaSize = chanPostImage.size,
      mediaHash = chanPostImage.fileHash,
      isSpoiler = chanPostImage.spoiler
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

  class MediaViewerControllerState(
    val loadedMedia: List<ViewableMedia>,
    val initialPagerIndex: Int = 0
  ) {
    fun isEmpty(): Boolean = loadedMedia.isEmpty()
  }

  companion object {
    private const val TAG = "MediaViewerControllerViewModel"
    private val DEFAULT_THUMBNAIL = (AppConstants.RESOURCES_ENDPOINT + "internal_spoiler.png").toHttpUrl()

    @JvmStatic
    fun canAutoLoad(cacheHandler: CacheHandler, postImage: ChanPostImage): Boolean {
      return canAutoLoad(cacheHandler, postImage.imageUrl, postImage.type, postImage.spoiler)
    }

    fun canAutoLoad(cacheHandler: CacheHandler, viewableMedia: ViewableMedia): Boolean {
      val mediaLocation = viewableMedia.mediaLocation
      if (mediaLocation !is MediaLocation.Remote) {
        return false
      }

      val url = mediaLocation.url

      val imageType = when (viewableMedia) {
        is ViewableMedia.Gif -> ChanPostImageType.GIF
        is ViewableMedia.Image -> ChanPostImageType.STATIC
        is ViewableMedia.Video -> ChanPostImageType.MOVIE
        is ViewableMedia.Unsupported -> return false
      }

      return canAutoLoad(cacheHandler, url, imageType, viewableMedia.viewableMediaMeta.isSpoiler)
    }

    fun canAutoLoad(
      cacheHandler: CacheHandler,
      url: HttpUrl?,
      imageType: ChanPostImageType?,
      isSpoiler: Boolean
    ): Boolean {
      val imageUrl = url ?: return false
      val postImageType = imageType ?: return false

      if (cacheHandler.cacheFileExists(imageUrl.toString())) {
        // Auto load the image when it is cached
        return true
      }

      if (isSpoiler && !ChanSettings.revealImageSpoilers.get()) {
        return false
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