package com.github.k1rakishou.chan.features.media_viewer

import android.net.Uri
import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.repository.CurrentlyDisplayedCatalogPostsRepository
import com.github.k1rakishou.chan.core.usecase.FilterOutHiddenImagesUseCase
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaViewState
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.shouldLoadForNetworkType
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.extractFileName
import com.github.k1rakishou.common.flatMapNotNull
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class MediaViewerControllerViewModel : ViewModel() {

  @Inject
  lateinit var chanThreadManager: ChanThreadManager
  @Inject
  lateinit var filterOutHiddenImagesUseCase: FilterOutHiddenImagesUseCase
  @Inject
  lateinit var replyManager: ReplyManager
  @Inject
  lateinit var currentlyDisplayedCatalogPostsRepository: CurrentlyDisplayedCatalogPostsRepository

  private val _mediaViewerState = MutableStateFlow<MediaViewerControllerState?>(null)
  private val _transitionInfoFlow = MutableSharedFlow<ViewableMediaParcelableHolder.TransitionInfo?>(extraBufferCapacity = 1)
  private val _mediaViewerOptions = MutableStateFlow<MediaViewerOptions>(MediaViewerOptions())
  private val mediaViewStateCache = LruCache<MediaLocation, MediaViewState>(offscreenPageLimit())

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
  val mediaViewerOptions: StateFlow<MediaViewerOptions>
    get() = _mediaViewerOptions
  val chanDescriptor: ChanDescriptor?
    get() = mediaViewerState.value?.descriptor

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

      val options = when (viewableMediaParcelableHolder) {
        is ViewableMediaParcelableHolder.CompositeCatalogMediaParcelableHolder -> {
          viewableMediaParcelableHolder.mediaViewerOptions
        }
        is ViewableMediaParcelableHolder.CatalogMediaParcelableHolder -> {
          viewableMediaParcelableHolder.mediaViewerOptions
        }
        is ViewableMediaParcelableHolder.ThreadMediaParcelableHolder -> {
          viewableMediaParcelableHolder.mediaViewerOptions
        }
        is ViewableMediaParcelableHolder.MixedMediaParcelableHolder -> {
          MediaViewerOptions()
        }
        is ViewableMediaParcelableHolder.ReplyAttachMediaParcelableHolder -> {
          MediaViewerOptions()
        }
      }

      _mediaViewerOptions.emit(options)

      if (isNotActivityRecreation) {
        val transitionInfo = when (viewableMediaParcelableHolder) {
          is ViewableMediaParcelableHolder.CompositeCatalogMediaParcelableHolder -> {
            viewableMediaParcelableHolder.transitionInfo
          }
          is ViewableMediaParcelableHolder.CatalogMediaParcelableHolder -> {
            viewableMediaParcelableHolder.transitionInfo
          }
          is ViewableMediaParcelableHolder.ThreadMediaParcelableHolder -> {
            viewableMediaParcelableHolder.transitionInfo
          }
          is ViewableMediaParcelableHolder.MixedMediaParcelableHolder -> null
          is ViewableMediaParcelableHolder.ReplyAttachMediaParcelableHolder -> null
        }

        _transitionInfoFlow.emit(transitionInfo)
      } else {
        _transitionInfoFlow.emit(null)
      }

      val mediaViewerControllerState = when (viewableMediaParcelableHolder) {
        is ViewableMediaParcelableHolder.CompositeCatalogMediaParcelableHolder -> {
          val compositeCatalogDescriptor = viewableMediaParcelableHolder.compositeCatalogDescriptor
          val initialImageUrl = viewableMediaParcelableHolder.initialImageUrl?.toHttpUrlOrNull()

          collectCatalogMedia(compositeCatalogDescriptor, initialImageUrl)
        }
        is ViewableMediaParcelableHolder.CatalogMediaParcelableHolder -> {
          val catalogDescriptor = viewableMediaParcelableHolder.catalogDescriptor
          val initialImageUrl = viewableMediaParcelableHolder.initialImageUrl?.toHttpUrlOrNull()

          collectCatalogMedia(catalogDescriptor, initialImageUrl)
        }
        is ViewableMediaParcelableHolder.ThreadMediaParcelableHolder -> {
          collectThreadMedia(viewableMediaParcelableHolder)
        }
        is ViewableMediaParcelableHolder.MixedMediaParcelableHolder -> {
          collectMixedMedia(viewableMediaParcelableHolder)
        }
        is ViewableMediaParcelableHolder.ReplyAttachMediaParcelableHolder -> {
          collectAttachReplyMedia(viewableMediaParcelableHolder)
        }
      }

      if (mediaViewerControllerState == null || mediaViewerControllerState.isEmpty()) {
        return@withContext false
      }

      _mediaViewerState.value = mediaViewerControllerState
      return@withContext true
    }
  }

  private fun collectAttachReplyMedia(
    viewableMediaParcelableHolder: ViewableMediaParcelableHolder.ReplyAttachMediaParcelableHolder
  ): MediaViewerControllerState? {
    val viewableMediaList = viewableMediaParcelableHolder.replyUuidList.mapNotNull { replyUuid ->
      val replyFile = replyManager.getReplyFileByFileUuid(replyUuid)
        .peekError { error -> Logger.e(TAG, "Failed to access reply file with UUID: $replyUuid", error) }
        .valueOrNull()

      if (replyFile == null) {
        return@mapNotNull null
      }

      val originalFileName = replyFile.getReplyFileMeta()
        .peekError { error -> Logger.e(TAG, "Failed to read meta of file with UUID: $replyUuid", error) }
        .valueOrNull()
        ?.originalFileName

      if (originalFileName == null) {
        return@mapNotNull null
      }

      val mediaLocation = MediaLocation.Local(replyFile.fileOnDisk.absolutePath, isUri = false)
      val fileName = StringUtils.removeExtensionFromFileName(originalFileName)
      val extension = StringUtils.extractFileNameExtension(originalFileName)

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

    return MediaViewerControllerState(
      descriptor = null,
      loadedMedia = viewableMediaList.toMutableList(),
      initialPagerIndex = 0
    )
  }

  private fun collectThreadMedia(
    viewableMediaParcelableHolder: ViewableMediaParcelableHolder.ThreadMediaParcelableHolder
  ): MediaViewerControllerState? {
    BackgroundUtils.ensureBackgroundThread()

    val initialPagerIndex = AtomicInteger(0)
    val mediaIndex = AtomicInteger(0)
    val scrollToImageWithUrl = viewableMediaParcelableHolder.initialImageUrl?.toHttpUrlOrNull()
    val postNoSubNoList = viewableMediaParcelableHolder.postNoSubNoList
    val threadDescriptor = viewableMediaParcelableHolder.threadDescriptor

    val mediaList = if (postNoSubNoList != null) {
      postNoSubNoList.flatMapNotNull { postNoSubNo ->
        val postDescriptor = PostDescriptor.create(
          threadDescriptor = threadDescriptor,
          postNo = postNoSubNo.postNo
        )

        val chanPost = chanThreadManager.getPost(postDescriptor)
        if (chanPost == null) {
          return@flatMapNotNull null
        }

        val mediaList = mutableListOf<ViewableMedia>()

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

        return@flatMapNotNull mediaList
      }
    } else {
      val mediaList = mutableListOf<ViewableMedia>()

      chanThreadManager.getChanThread(threadDescriptor)?.let { chanThread ->
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
      }

      mediaList
    }

    if (mediaList.isNullOrEmpty()) {
      return null
    }

    val input = FilterOutHiddenImagesUseCase.Input(
      images = mediaList,
      index = initialPagerIndex.get(),
      isOpeningAlbum = false,
      postDescriptorSelector = { viewableMedia -> viewableMedia.viewableMediaMeta.ownerPostDescriptor }
    )

    val output = filterOutHiddenImagesUseCase.filter(input)

    val actualInitialPagerIndex = synchronized(this) {
      if (lastPagerIndex >= 0) {
        lastPagerIndex
      } else {
        output.index
      }
    }

    return MediaViewerControllerState(
      descriptor = viewableMediaParcelableHolder.threadDescriptor,
      loadedMedia = output.images.toMutableList(),
      initialPagerIndex = actualInitialPagerIndex
    )
  }

  private fun collectCatalogMedia(
    catalogDescriptor: ChanDescriptor.ICatalogDescriptor,
    initialImageUrl: HttpUrl?
  ): MediaViewerControllerState? {
    BackgroundUtils.ensureBackgroundThread()

    val initialPagerIndex = AtomicInteger(0)
    val mediaIndex = AtomicInteger(0)
    val mediaList = mutableListWithCap<ViewableMedia>(64)

    currentlyDisplayedCatalogPostsRepository.iteratePosts { postDescriptor ->
      val chanOriginalPost = chanThreadManager.getPost(postDescriptor)
        ?: return@iteratePosts

      chanOriginalPost.iteratePostImages { chanPostImage ->
        val viewableMedia = processChanPostImage(
          chanPostImage = chanPostImage,
          scrollToImageWithUrl = initialImageUrl,
          lastViewedIndex = initialPagerIndex,
          mediaIndex = mediaIndex
        )

        if (viewableMedia != null) {
          mediaList += viewableMedia
        }
      }
    }

    if (mediaList.isNullOrEmpty()) {
      return null
    }

    val input = FilterOutHiddenImagesUseCase.Input(
      images = mediaList,
      index = initialPagerIndex.get(),
      isOpeningAlbum = false,
      postDescriptorSelector = { viewableMedia -> viewableMedia.viewableMediaMeta.ownerPostDescriptor }
    )

    val output = filterOutHiddenImagesUseCase.filter(input)

    val actualInitialPagerIndex = synchronized(this) {
      if (lastPagerIndex >= 0) {
        lastPagerIndex
      } else {
        output.index
      }
    }

    return MediaViewerControllerState(
      descriptor = catalogDescriptor as ChanDescriptor,
      loadedMedia = output.images.toMutableList(),
      initialPagerIndex = actualInitialPagerIndex
    )
  }

  private fun collectMixedMedia(
    viewableMediaParcelableHolder: ViewableMediaParcelableHolder.MixedMediaParcelableHolder
  ): MediaViewerControllerState? {
    val viewableMediaList = viewableMediaParcelableHolder.mixedMedia.mapNotNull { mediaLocation ->
      return@mapNotNull when (mediaLocation) {
        is MediaLocation.Local -> mapLocalMedia(mediaLocation)
        is MediaLocation.Remote -> mapRemoteMedia(mediaLocation)
      }
    }

    if (viewableMediaList.isEmpty()) {
      return null
    }

    return MediaViewerControllerState(
      descriptor = null,
      loadedMedia = viewableMediaList.toMutableList(),
      initialPagerIndex = 0
    )
  }

  private fun mapRemoteMedia(mediaLocation: MediaLocation.Remote): ViewableMedia? {
    if (mediaLocation.urlRaw.toHttpUrlOrNull() == null) {
      return null
    }

    val fileName = mediaLocation.url.extractFileName()
      ?.let { fileName -> StringUtils.removeExtensionFromFileName(fileName) }
    val extension = StringUtils.extractFileNameExtension(mediaLocation.urlRaw)

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
      return ViewableMedia.Unsupported(mediaLocation, null, null, meta)
    }

    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
      ?: return ViewableMedia.Unsupported(mediaLocation, null, null, meta)

    if (mimeType.startsWith("video/")) {
      return ViewableMedia.Video(mediaLocation, null, null, meta)
    } else if (mimeType.startsWith("image/")) {
      if (mimeType.endsWith("gif")) {
        return ViewableMedia.Gif(mediaLocation, null, null, meta)
      } else {
        return ViewableMedia.Image(mediaLocation, null, null, meta)
      }
    }

    return ViewableMedia.Unsupported(mediaLocation, null, null, meta)
  }

  private fun mapLocalMedia(mediaLocation: MediaLocation.Local): ViewableMedia? {
    val uri = try {
      Uri.parse(mediaLocation.path)
    } catch (error: Throwable) {
      Logger.e(TAG, "mapLocalMedia() Failed to parse uri: '${mediaLocation.path}'", error)
      return null
    }

    val fullFileName = uri.lastPathSegment?.let { lastPathSegment ->
      if (lastPathSegment.contains("/")) {
        return@let lastPathSegment.substringAfterLast("/")
      }

      return@let lastPathSegment
    }

    if (fullFileName == null) {
      return null
    }

    val fileName = StringUtils.removeExtensionFromFileName(fullFileName)
    val extension = StringUtils.extractFileNameExtension(fullFileName)

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
      return ViewableMedia.Unsupported(mediaLocation, null, null, meta)
    }

    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
      ?: return ViewableMedia.Unsupported(mediaLocation, null, null, meta)

    if (mimeType.startsWith("video/")) {
      return ViewableMedia.Video(mediaLocation, null, null, meta)
    } else if (mimeType.startsWith("image/")) {
      if (mimeType.endsWith("gif")) {
        return ViewableMedia.Gif(mediaLocation, null, null, meta)
      } else {
        return ViewableMedia.Image(mediaLocation, null, null, meta)
      }
    }

    return ViewableMedia.Unsupported(mediaLocation, null, null, meta)
  }

  private fun processChanPostImage(
    chanPostImage: ChanPostImage,
    scrollToImageWithUrl: HttpUrl?,
    lastViewedIndex: AtomicInteger,
    mediaIndex: AtomicInteger
  ): ViewableMedia? {
    BackgroundUtils.ensureBackgroundThread()

    val imageLocation = chanPostImage.imageUrl
      ?.let { imageUrl -> MediaLocation.Remote(imageUrl.toString()) }

    if (imageLocation == null) {
      // No actual image, nothing to do
      return null
    }

    val previewLocation = chanPostImage.actualThumbnailUrl
      ?.let { thumbnailUrl -> MediaLocation.Remote(thumbnailUrl.toString()) }
      ?: MediaLocation.Remote(AppConstants.INLINED_IMAGE_THUMBNAIL)

    val spoilerLocation = if (chanPostImage.imageSpoilered) {
      chanPostImage.spoilerThumbnailUrl
        ?.let { spoilerUrl -> MediaLocation.Remote(spoilerUrl.toString()) }
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
      isSpoiler = chanPostImage.imageSpoilered
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
    val descriptor: ChanDescriptor?,
    val loadedMedia: MutableList<ViewableMedia>,
    val initialPagerIndex: Int = 0
  ) {
    fun isEmpty(): Boolean = loadedMedia.isEmpty()
  }

  companion object {
    private const val TAG = "MediaViewerControllerViewModel"

    fun offscreenPageLimit(): Int {
      return ChanSettings.mediaViewerOffscreenPagesCount()
    }

    @JvmStatic
    fun canAutoLoad(
      cacheHandler: CacheHandler,
      postImage: ChanPostImage
    ): Boolean {
      return canAutoLoad(
        cacheHandler = cacheHandler,
        url = postImage.imageUrl,
        imageType = postImage.type,
        isSpoiler = postImage.imageSpoilered,
        cacheFileType = CacheFileType.PostMediaFull
      )
    }

    fun canAutoLoad(
      cacheHandler: CacheHandler,
      viewableMedia: ViewableMedia,
      cacheFileType: CacheFileType
    ): Boolean {
      val mediaLocation = viewableMedia.mediaLocation
      if (mediaLocation !is MediaLocation.Remote) {
        return false
      }

      val url = mediaLocation.url

      val imageType = when (viewableMedia) {
        is ViewableMedia.Gif -> ChanPostImageType.GIF
        is ViewableMedia.Image -> ChanPostImageType.STATIC
        is ViewableMedia.Audio,
        is ViewableMedia.Video -> ChanPostImageType.MOVIE
        is ViewableMedia.Unsupported -> return false
      }

      return canAutoLoad(
        cacheHandler = cacheHandler,
        url = url,
        imageType = imageType,
        isSpoiler = viewableMedia.viewableMediaMeta.isSpoiler,
        cacheFileType = cacheFileType
      )
    }

    private fun canAutoLoad(
      cacheHandler: CacheHandler,
      url: HttpUrl?,
      imageType: ChanPostImageType?,
      isSpoiler: Boolean,
      cacheFileType: CacheFileType
    ): Boolean {
      val imageUrl = url ?: return false
      val postImageType = imageType ?: return false

      if (cacheHandler.cacheFileExists(cacheFileType, imageUrl.toString())) {
        // Auto load the image when it is cached
        return true
      }

      if (isSpoiler && !ChanSettings.mediaViewerRevealImageSpoilers.get()) {
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