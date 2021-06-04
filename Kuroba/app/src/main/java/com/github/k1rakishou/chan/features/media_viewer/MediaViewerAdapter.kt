package com.github.k1rakishou.chan.features.media_viewer

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.github.k1rakishou.chan.core.manager.Chan4CloudFlareImagePreloaderManager
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerScrollerHelper
import com.github.k1rakishou.chan.features.media_viewer.media_view.FullImageMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.GifMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaViewContract
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaViewState
import com.github.k1rakishou.chan.features.media_viewer.media_view.UnsupportedMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.VideoMediaView
import com.github.k1rakishou.chan.ui.view.OptionalSwipeViewPager
import com.github.k1rakishou.chan.ui.view.ViewPagerAdapter
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.core_logger.Logger
import com.google.android.exoplayer2.upstream.DataSource
import kotlinx.coroutines.CompletableDeferred

class MediaViewerAdapter(
  private val context: Context,
  private val viewModel: MediaViewerControllerViewModel,
  private val mediaViewContract: MediaViewContract,
  private val initialPagerIndex: Int,
  private val viewableMediaList: List<ViewableMedia>,
  private val previewThumbnailLocation: MediaLocation,
  private val mediaViewerScrollerHelper: MediaViewerScrollerHelper,
  private val cacheDataSourceFactory: DataSource.Factory,
  private val chan4CloudFlareImagePreloaderManager: Chan4CloudFlareImagePreloaderManager,
  private val isSystemUiHidden: () -> Boolean,
  private val swipeDirection: () -> OptionalSwipeViewPager.SwipeDirection
) : ViewPagerAdapter() {
  private val loadedViews = mutableListOf<LoadedView>()
  private val previewThumbnailLocationLoaded = CompletableDeferred<Unit>()

  private var firstUpdateHappened = false
  private var initialImageBindHappened = false
  private var lastViewedMediaPosition = 0

  suspend fun awaitUntilPreviewThumbnailFullyLoaded() {
    Logger.d(TAG, "awaitUntilPreviewThumbnailFullyLoaded()...")
    previewThumbnailLocationLoaded.await()
    Logger.d(TAG, "awaitUntilPreviewThumbnailFullyLoaded()...done")
  }

  fun onDestroy() {
    Logger.d(TAG, "onDestroy()")
    lastViewedMediaPosition = 0

    if (previewThumbnailLocationLoaded.isActive) {
      previewThumbnailLocationLoaded.cancel()
    }

    loadedViews.forEach { loadedView ->
      if (loadedView.mediaView.shown) {
        loadedView.mediaView.onHide()
      }

      if (loadedView.mediaView.bound) {
        loadedView.mediaView.onUnbind()
      }
    }

    loadedViews.clear()
  }

  override fun getCount(): Int = viewableMediaList.size

  fun onPause() {
    loadedViews.forEach { loadedView ->
      if (loadedView.mediaView.shown) {
        loadedView.mediaView.onHide()

        // Store media view state
        val mediaViewState = loadedView.mediaView.mediaViewState
        viewModel.storeMediaViewState(loadedView.mediaView.viewableMedia.mediaLocation, mediaViewState)
      }
    }
  }

  fun onResume() {
    loadedViews.forEach { loadedView ->
      if (loadedView.viewIndex != lastViewedMediaPosition) {
        return@forEach
      }

      if (!loadedView.mediaView.shown) {
        // Restore media view state
        val mediaLocation = loadedView.mediaView.viewableMedia.mediaLocation
        loadedView.mediaView.mediaViewState.updateFrom(viewModel.getPrevMediaViewStateOrNull(mediaLocation))

        loadedView.mediaView.startPreloading()
        loadedView.mediaView.onShow()
      }
    }
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
          cacheDataSourceFactory = cacheDataSourceFactory,
          onThumbnailFullyLoadedFunc = onThumbnailFullyLoaded,
          isSystemUiHidden = isSystemUiHidden,
          viewableMedia = viewableMedia,
          pagerPosition = position,
          totalPageItemsCount = count
        )
      }
      is ViewableMedia.Gif -> {
        GifMediaView(
          context = context,
          initialMediaViewState = GifMediaView.GifMediaViewState(),
          mediaViewContract = mediaViewContract,
          cacheDataSourceFactory = cacheDataSourceFactory,
          onThumbnailFullyLoadedFunc = onThumbnailFullyLoaded,
          isSystemUiHidden = isSystemUiHidden,
          viewableMedia = viewableMedia,
          pagerPosition = position,
          totalPageItemsCount = count
        )
      }
      is ViewableMedia.Video -> {
        val initialMediaViewState = viewModel.getPrevMediaViewStateOrNull(viewableMedia.mediaLocation)
          as? VideoMediaView.VideoMediaViewState
          ?: VideoMediaView.VideoMediaViewState()

        VideoMediaView(
          context = context,
          initialMediaViewState = initialMediaViewState,
          viewModel = viewModel,
          mediaViewContract = mediaViewContract,
          cacheDataSourceFactory = cacheDataSourceFactory,
          onThumbnailFullyLoadedFunc = onThumbnailFullyLoaded,
          isSystemUiHidden = isSystemUiHidden,
          viewableMedia = viewableMedia,
          pagerPosition = position,
          totalPageItemsCount = count,
        )
      }
      is ViewableMedia.Unsupported -> {
        UnsupportedMediaView(
          context = context,
          initialMediaViewState = UnsupportedMediaView.UnsupportedMediaViewState(),
          mediaViewContract = mediaViewContract,
          cacheDataSourceFactory = cacheDataSourceFactory,
          onThumbnailFullyLoadedFunc = onThumbnailFullyLoaded,
          isSystemUiHidden = isSystemUiHidden,
          viewableMedia = viewableMedia,
          pagerPosition = position,
          totalPageItemsCount = count
        )
      }
    }

    mediaView.startPreloading()
    loadedViews.add(LoadedView(position, mediaView as MediaView<ViewableMedia, MediaViewState>))

    return mediaView
  }

  fun doBind(position: Int) {
    val viewableMedia = viewableMediaList[position]
    Logger.d(TAG, "doBind(position: ${position})")

    if (position != initialPagerIndex || initialImageBindHappened) {
      viewableMedia.viewableMediaMeta.ownerPostDescriptor?.let { postDescriptor ->
        mediaViewerScrollerHelper.onScrolledTo(postDescriptor, viewableMedia.mediaLocation)
      }
    }

    if (position == initialPagerIndex) {
      initialImageBindHappened = true
    }

    val view = loadedViews
      .firstOrNull { loadedView -> loadedView.mediaView.viewableMedia == viewableMedia }
      ?: return

    if (!view.mediaView.bound) {
      view.mediaView.onBind()
    }

    if (!view.mediaView.shown) {
      view.mediaView.onShow()
    }

    loadedViews.forEach { loadedView ->
      if (loadedView.mediaView.viewableMedia != view.mediaView.viewableMedia) {
        if (loadedView.mediaView.shown) {
          loadedView.mediaView.onHide()

          // Store media view state
          val mediaViewState = loadedView.mediaView.mediaViewState
          viewModel.storeMediaViewState(loadedView.mediaView.viewableMedia.mediaLocation, mediaViewState)
        }
      }
    }

    Logger.d(TAG, "doBind(position: ${position}), loadedViewsCount=${loadedViews.size}, " +
      "boundCount=${loadedViews.count { it.mediaView.bound }}, " +
      "showCount=${loadedViews.count { it.mediaView.shown }}")

    viewableMedia.viewableMediaMeta.ownerPostDescriptor?.let { postDescriptor ->
      chan4CloudFlareImagePreloaderManager.startLoading(postDescriptor)
    }

    if (!firstUpdateHappened) {
      lastViewedMediaPosition = initialPagerIndex
    } else {
      lastViewedMediaPosition = position
    }
  }

  override fun finishUpdate(container: ViewGroup) {
    super.finishUpdate(container)

    if (firstUpdateHappened) {
      return
    }

    lastViewedMediaPosition = initialPagerIndex

    loadedViews.forEach { loadedView ->
      if (!loadedView.mediaView.bound) {
        loadedView.mediaView.onBind()
      }

      if (loadedView.viewIndex == initialPagerIndex && !loadedView.mediaView.shown) {
        loadedView.mediaView.onShow()
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

    chan4CloudFlareImagePreloaderManager.cancelLoading(
      mediaView.viewableMedia.viewableMediaMeta.ownerPostDescriptor!!,
      swipeDirection() == OptionalSwipeViewPager.SwipeDirection.Forward
    )
  }

  fun onSystemUiVisibilityChanged(systemUIHidden: Boolean) {
    loadedViews.forEach { loadedView ->
      loadedView.mediaView.onSystemUiVisibilityChanged(systemUIHidden)
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

  data class LoadedView(val viewIndex: Int, val mediaView: MediaView<ViewableMedia, MediaViewState>)

  companion object {
    private const val TAG = "MediaViewerAdapter"
  }
}