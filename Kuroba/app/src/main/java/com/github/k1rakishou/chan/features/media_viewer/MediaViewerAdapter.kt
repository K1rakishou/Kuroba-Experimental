package com.github.k1rakishou.chan.features.media_viewer

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.Chan4CloudFlareImagePreloaderManager
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerScrollerHelper
import com.github.k1rakishou.chan.features.media_viewer.media_view.AudioMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.ExoPlayerVideoMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.FullImageMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.GifMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaViewContract
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaViewState
import com.github.k1rakishou.chan.features.media_viewer.media_view.MpvVideoMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.UnsupportedMediaView
import com.github.k1rakishou.chan.ui.view.OptionalSwipeViewPager
import com.github.k1rakishou.chan.ui.view.ViewPagerAdapter
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.google.android.exoplayer2.upstream.DataSource
import kotlinx.coroutines.CompletableDeferred

class MediaViewerAdapter(
  private val context: Context,
  private val appConstants: AppConstants,
  private val viewModel: MediaViewerControllerViewModel,
  private val mediaViewerToolbar: MediaViewerToolbar,
  private val mediaViewContract: MediaViewContract,
  private val initialPagerIndex: Int,
  private val viewableMediaList: MutableList<ViewableMedia>,
  private val previewThumbnailLocation: MediaLocation,
  private val mediaViewerScrollerHelper: MediaViewerScrollerHelper,
  private val cachedHttpDataSourceFactory: DataSource.Factory,
  private val fileDataSourceFactory: DataSource.Factory,
  private val contentDataSourceFactory: DataSource.Factory,
  private val chan4CloudFlareImagePreloaderManager: Chan4CloudFlareImagePreloaderManager,
  private val isSystemUiHidden: () -> Boolean,
  private val swipeDirection: () -> OptionalSwipeViewPager.SwipeDirection,
  private val getAndConsumeLifecycleChangeFlag: () -> Boolean
) : ViewPagerAdapter() {
  private val forceUpdateViewWithMedia = mutableSetOf<ViewableMedia>()
  private val loadedViews = mutableListOf<LoadedView>()
  private val previewThumbnailLocationLoaded = CompletableDeferred<Unit>()

  private var firstUpdateHappened = false
  private var initialImageBindHappened = false

  private var _lastViewedMediaPosition = 0
  val lastViewedMediaPosition: Int
    get() = _lastViewedMediaPosition
  val totalViewableMediaCount: Int
    get() = viewableMediaList.size

  suspend fun awaitUntilPreviewThumbnailFullyLoaded() {
    Logger.d(TAG, "awaitUntilPreviewThumbnailFullyLoaded()...")
    previewThumbnailLocationLoaded.await()
    Logger.d(TAG, "awaitUntilPreviewThumbnailFullyLoaded()...done")
  }

  fun onDestroy() {
    Logger.d(TAG, "onDestroy()")
    _lastViewedMediaPosition = 0

    if (previewThumbnailLocationLoaded.isActive) {
      previewThumbnailLocationLoaded.cancel()
    }

    loadedViews.forEach { loadedView ->
      if (loadedView.mediaView.shown) {
        loadedView.mediaView.onHide(isLifecycleChange = true, isPausing = false, isBecomingInactive = false)
      }

      if (loadedView.mediaView.bound) {
        loadedView.mediaView.onUnbind()
      }
    }

    loadedViews.clear()
  }

  override fun getCount(): Int = viewableMediaList.size

  fun getLoadedViews(): List<LoadedView> {
    return loadedViews.toList()
  }

  fun onPause() {
    loadedViews.forEach { loadedView ->
      if (loadedView.mediaView.shown) {
        loadedView.mediaView.onHide(isLifecycleChange = true, isPausing = true, isBecomingInactive = false)

        // Store media view state
        val mediaViewState = loadedView.mediaView.mediaViewState
        viewModel.storeMediaViewState(loadedView.mediaView.viewableMedia.mediaLocation, mediaViewState)
      }
    }

    getAndConsumeLifecycleChangeFlag()
  }

  fun onResume() {
    loadedViews.forEach { loadedView ->
      if (loadedView.viewIndex != _lastViewedMediaPosition) {
        return@forEach
      }

      if (!loadedView.mediaView.shown) {
        // Restore media view state
        val mediaLocation = loadedView.mediaView.viewableMedia.mediaLocation
        loadedView.mediaView.mediaViewState.updateFrom(viewModel.getPrevMediaViewStateOrNull(mediaLocation))

        loadedView.mediaView.startPreloading()
        loadedView.mediaView.onShow(mediaViewerToolbar, isLifecycleChange = true)
      }
    }

    getAndConsumeLifecycleChangeFlag()
  }

  override fun getItemPosition(`object`: Any): Int {
    if (`object` !is MediaView<*, *>) {
      return super.getItemPosition(`object`)
    }

    val mediaView = `object` as MediaView<*, *>
    if (mediaView.viewableMedia in forceUpdateViewWithMedia) {
      forceUpdateViewWithMedia.remove(mediaView.viewableMedia)
      return POSITION_NONE
    }

    return super.getItemPosition(`object`)
  }

  @Suppress("UNCHECKED_CAST")
  override fun getView(position: Int, parent: ViewGroup?): View {
    val viewableMedia = viewableMediaList[position]

    val onThumbnailFullyLoaded = {
      if (viewableMedia.mediaLocation == previewThumbnailLocation && !previewThumbnailLocationLoaded.isCompleted) {
        previewThumbnailLocationLoaded.complete(Unit)
      }
    }

    val mediaView = when (viewableMedia) {
      is ViewableMedia.Image -> {
        val initialMediaViewState = viewModel.getPrevMediaViewStateOrNull(viewableMedia.mediaLocation)
          as? FullImageMediaView.FullImageState
          ?: FullImageMediaView.FullImageState()

        FullImageMediaView(
          context = context,
          initialMediaViewState = initialMediaViewState,
          mediaViewContract = mediaViewContract,
          cachedHttpDataSourceFactory = cachedHttpDataSourceFactory,
          fileDataSourceFactory = fileDataSourceFactory,
          contentDataSourceFactory = contentDataSourceFactory,
          onThumbnailFullyLoadedFunc = onThumbnailFullyLoaded,
          isSystemUiHidden = isSystemUiHidden,
          viewableMedia = viewableMedia,
          pagerPosition = position,
          totalPageItemsCount = count
        )
      }
      is ViewableMedia.Gif -> {
        val initialMediaViewState = viewModel.getPrevMediaViewStateOrNull(viewableMedia.mediaLocation)
          as? GifMediaView.GifMediaViewState
          ?: GifMediaView.GifMediaViewState()

        GifMediaView(
          context = context,
          initialMediaViewState = initialMediaViewState,
          mediaViewContract = mediaViewContract,
          cachedHttpDataSourceFactory = cachedHttpDataSourceFactory,
          fileDataSourceFactory = fileDataSourceFactory,
          contentDataSourceFactory = contentDataSourceFactory,
          onThumbnailFullyLoadedFunc = onThumbnailFullyLoaded,
          isSystemUiHidden = isSystemUiHidden,
          viewableMedia = viewableMedia,
          pagerPosition = position,
          totalPageItemsCount = count
        )
      }
      is ViewableMedia.Video -> {
        if (ChanSettings.useMpvVideoPlayer.get()) {
          val initialMediaViewState = viewModel.getPrevMediaViewStateOrNull(viewableMedia.mediaLocation)
            as? MpvVideoMediaView.VideoMediaViewState
            ?: MpvVideoMediaView.VideoMediaViewState()

          MpvVideoMediaView(
            context = context,
            initialMediaViewState = initialMediaViewState,
            viewModel = viewModel,
            mediaViewContract = mediaViewContract,
            cachedHttpDataSourceFactory = cachedHttpDataSourceFactory,
            fileDataSourceFactory = fileDataSourceFactory,
            contentDataSourceFactory = contentDataSourceFactory,
            onThumbnailFullyLoadedFunc = onThumbnailFullyLoaded,
            isSystemUiHidden = isSystemUiHidden,
            viewableMedia = viewableMedia,
            pagerPosition = position,
            totalPageItemsCount = count
          )
        } else {
          val initialMediaViewState = viewModel.getPrevMediaViewStateOrNull(viewableMedia.mediaLocation)
            as? ExoPlayerVideoMediaView.VideoMediaViewState
            ?: ExoPlayerVideoMediaView.VideoMediaViewState()

          ExoPlayerVideoMediaView(
            context = context,
            initialMediaViewState = initialMediaViewState,
            viewModel = viewModel,
            mediaViewContract = mediaViewContract,
            cachedHttpDataSourceFactory = cachedHttpDataSourceFactory,
            fileDataSourceFactory = fileDataSourceFactory,
            contentDataSourceFactory = contentDataSourceFactory,
            onThumbnailFullyLoadedFunc = onThumbnailFullyLoaded,
            isSystemUiHidden = isSystemUiHidden,
            viewableMedia = viewableMedia,
            pagerPosition = position,
            totalPageItemsCount = count
          )
        }
      }
      is ViewableMedia.Audio -> {
        AudioMediaView(
          context = context,
          initialMediaViewState = AudioMediaView.AudioMediaViewState(),
          mediaViewContract = mediaViewContract,
          cachedHttpDataSourceFactory = cachedHttpDataSourceFactory,
          fileDataSourceFactory = fileDataSourceFactory,
          contentDataSourceFactory = contentDataSourceFactory,
          onThumbnailFullyLoadedFunc = onThumbnailFullyLoaded,
          isSystemUiHidden = isSystemUiHidden,
          viewableMedia = viewableMedia,
          pagerPosition = position,
          totalPageItemsCount = count
        )
      }
      is ViewableMedia.Unsupported -> {
        UnsupportedMediaView(
          context = context,
          initialMediaViewState = UnsupportedMediaView.UnsupportedMediaViewState(),
          mediaViewContract = mediaViewContract,
          cachedHttpDataSourceFactory = cachedHttpDataSourceFactory,
          fileDataSourceFactory = fileDataSourceFactory,
          contentDataSourceFactory = contentDataSourceFactory,
          onThumbnailFullyLoadedFunc = onThumbnailFullyLoaded,
          isSystemUiHidden = isSystemUiHidden,
          viewableMedia = viewableMedia,
          pagerPosition = position,
          totalPageItemsCount = count
        )
      }
    }

    mediaView.id = View.generateViewId()
    mediaView.startPreloading()
    loadedViews.add(LoadedView(position, mediaView as MediaView<ViewableMedia, MediaViewState>))

    return mediaView
  }

  fun doBind(position: Int) {
    val viewableMedia = viewableMediaList[position]
    Logger.d(TAG, "doBind(position: ${position})")

    if (position != initialPagerIndex || initialImageBindHappened) {
      viewableMedia.viewableMediaMeta.ownerPostDescriptor?.let { postDescriptor ->
        mediaViewerScrollerHelper.onScrolledTo(
          chanDescriptor = viewModel.chanDescriptor,
          postDescriptor = postDescriptor,
          mediaLocation = viewableMedia.mediaLocation
        )
      }
    }

    if (position == initialPagerIndex) {
      initialImageBindHappened = true
    }

    val view = loadedViews
      .firstOrNull { loadedView -> loadedView.mediaView.viewableMedia == viewableMedia }
      ?: return

    loadedViews.forEach { loadedView ->
      if (loadedView.mediaView.viewableMedia != view.mediaView.viewableMedia) {
        if (loadedView.mediaView.shown) {
          loadedView.mediaView.onHide(isLifecycleChange = false, isPausing = true, isBecomingInactive = true)

          // Store media view state
          val mediaViewState = loadedView.mediaView.mediaViewState
          viewModel.storeMediaViewState(loadedView.mediaView.viewableMedia.mediaLocation, mediaViewState)
        }
      }
    }

    if (!view.mediaView.bound) {
      view.mediaView.onBind()
    }

    if (!view.mediaView.shown) {
      view.mediaView.onShow(mediaViewerToolbar, isLifecycleChange = false)
    }

    Logger.d(TAG, "doBind(position: ${position}), loadedViewsCount=${loadedViews.size}, " +
      "boundCount=${loadedViews.count { it.mediaView.bound }}, " +
      "showCount=${loadedViews.count { it.mediaView.shown }}")

    viewableMedia.viewableMediaMeta.ownerPostDescriptor?.let { postDescriptor ->
      chan4CloudFlareImagePreloaderManager.startLoading(postDescriptor)
    }

    if (!firstUpdateHappened) {
      _lastViewedMediaPosition = initialPagerIndex
    } else {
      _lastViewedMediaPosition = position
    }
  }

  override fun finishUpdate(container: ViewGroup) {
    super.finishUpdate(container)

    forceUpdateViewWithMedia.clear()

    if (firstUpdateHappened) {
      return
    }

    _lastViewedMediaPosition = initialPagerIndex
    val isLifecycleChange = getAndConsumeLifecycleChangeFlag()

    loadedViews.forEach { loadedView ->
      if (!loadedView.mediaView.bound) {
        loadedView.mediaView.onBind()
      }

      if (loadedView.viewIndex == initialPagerIndex && !loadedView.mediaView.shown) {
        loadedView.mediaView.onShow(mediaViewerToolbar, isLifecycleChange = isLifecycleChange)
      }
    }

    if (loadedViews.isNotEmpty()) {
      firstUpdateHappened = true
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
    super.destroyItem(container, position, obj)

    val mediaView = obj as MediaView<ViewableMedia, *>

    if (mediaView.shown) {
      mediaView.onHide(isLifecycleChange = false, isPausing = false, isBecomingInactive = true)
    }

    mediaView.onUnbind()

    loadedViews.mutableIteration { mutableIterator, mv ->
      if (mv.mediaView.viewableMedia == mediaView.viewableMedia) {
        mutableIterator.remove()
      }

      return@mutableIteration true
    }
    
    Logger.d(TAG, "destroyItem(position: ${position}), loadedViewsCount=${loadedViews.size}, " +
      "boundCount=${loadedViews.count { it.mediaView.bound }}, " +
      "showCount=${loadedViews.count { it.mediaView.shown }}")

    mediaView.viewableMedia.viewableMediaMeta.ownerPostDescriptor?.let { postDescriptor ->
      chan4CloudFlareImagePreloaderManager.cancelLoading(
        postDescriptor,
        swipeDirection() == OptionalSwipeViewPager.SwipeDirection.Forward
      )
    }
  }

  fun onSystemUiVisibilityChanged(systemUIHidden: Boolean) {
    loadedViews.forEach { loadedView ->
      loadedView.mediaView.onSystemUiVisibilityChanged(systemUIHidden)
    }
  }

  fun onInsetsChanged() {
    loadedViews.forEach { loadedView ->
      loadedView.mediaView.onInsetsChanged()
    }
  }

  suspend fun reloadMedia(viewableMedia: ViewableMedia) {
    loadedViews.forEach { loadedView ->
      if (loadedView.mediaView.viewableMedia == viewableMedia) {
        loadedView.mediaView.reloadMedia()
      }
    }
  }

  fun markMediaAsDownloaded(viewableMedia: ViewableMedia) {
    loadedViews.forEach { loadedView ->
      if (loadedView.mediaView.viewableMedia == viewableMedia) {
        loadedView.mediaView.markMediaAsDownloaded()
      }
    }
  }

  fun updateTransparency() {
    loadedViews.forEach { loadedView -> loadedView.mediaView.onUpdateTransparency() }
  }

  fun reloadAs(pagerPosition: Int, viewableMedia: ViewableMedia) {
    reloadManyAs(listOf(Pair(pagerPosition, viewableMedia)))
  }

  fun reloadManyAs(toReload: List<Pair<Int, ViewableMedia>>) {
    if (toReload.isEmpty()) {
      return
    }

    toReload.forEach { (pagerPosition, viewableMedia) ->
      forceUpdateViewWithMedia.add(viewableMediaList[pagerPosition])
      viewableMediaList[pagerPosition] = viewableMedia
    }

    notifyDataSetChanged()
  }

  fun indexOfPostImageOrNull(postImage: ChanPostImage): Int? {
    val indexToScroll = viewableMediaList
      .indexOfFirst { viewableMedia -> viewableMedia.mediaLocation.value == postImage.imageUrl?.toString() }

    if (indexToScroll < 0) {
      return null
    }

    return indexToScroll
  }

  data class LoadedView(val viewIndex: Int, val mediaView: MediaView<ViewableMedia, MediaViewState>)

  companion object {
    private const val TAG = "MediaViewerAdapter"
  }
}