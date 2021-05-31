package com.github.k1rakishou.chan.features.media_viewer

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.viewpager.widget.ViewPager
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.site.ImageSearch
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2OptionsController
import com.github.k1rakishou.chan.features.media_viewer.helper.ExoPlayerCache
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerScrollerHelper
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaViewContract
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayoutNoBackground
import com.github.k1rakishou.chan.ui.view.AppearTransitionImageView
import com.github.k1rakishou.chan.ui.view.DisappearTransitionImageView
import com.github.k1rakishou.chan.ui.view.OptionalSwipeViewPager
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.awaitSilently
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.persist_state.PersistableChanState
import com.github.k1rakishou.persist_state.PersistableChanState.imageSaverV2PersistedOptions
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import javax.inject.Inject
import kotlin.coroutines.resume

class MediaViewerController(
  context: Context,
  private val mediaViewerCallbacks: MediaViewerCallbacks
) : Controller(context), ViewPager.OnPageChangeListener, MediaViewContract {

  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var mediaViewerScrollerHelper: MediaViewerScrollerHelper
  @Inject
  lateinit var exoPlayerCache: ExoPlayerCache
  @Inject
  lateinit var imageSaverV2: ImageSaverV2
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private lateinit var mediaViewerRootLayout: TouchBlockingFrameLayoutNoBackground
  private lateinit var appearPreviewImage: AppearTransitionImageView
  private lateinit var disappearPreviewImage: DisappearTransitionImageView
  private lateinit var pager: OptionalSwipeViewPager

  private val viewModel by (context as ComponentActivity).viewModels<MediaViewerControllerViewModel>()
  private val transitionAnimationShown = CompletableDeferred<Unit>()
  private val mediaOptionsHandlerExecutor = RendezvousCoroutineExecutor(mainScope)

  private val mediaViewerAdapter: MediaViewerAdapter?
    get() = pager.adapter as? MediaViewerAdapter

  private val cacheDataSourceFactory by lazy {
    val defaultDataSourceFactory = DefaultHttpDataSource.Factory()
      .setUserAgent(appConstants.userAgent)

    // TODO(KurobaEx): setDefaultRequestProperties() so that we can set cookie which is used by some
    //  hidden boards
//    Map<String, String> requestProperties = new HashMap<>();
//
//    if (site != null) {
//      SiteRequestModifier<Site> requestModifier = site.requestModifier();
//      if (requestModifier != null) {
//        requestModifier.modifyVideoStreamRequest(site, requestProperties);
//      }
//    }

    return@lazy CacheDataSource.Factory()
      .setCache(exoPlayerCache.actualCache)
      .setUpstreamDataSourceFactory(defaultDataSourceFactory)
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    view = AppModuleAndroidUtils.inflate(context, R.layout.controller_media_viewer)

    mediaViewerRootLayout = view.findViewById(R.id.media_viewer_root_layout)
    appearPreviewImage = view.findViewById(R.id.appear_preview_image)
    disappearPreviewImage = view.findViewById(R.id.disappear_preview_image)
    pager = view.findViewById(R.id.pager)

    pager.addOnPageChangeListener(this)

    // TODO(KurobaEx): make this setting configurable
    pager.offscreenPageLimit = PersistableChanState.mediaViewerOffscreenItemsCount.get()

    mainScope.launch(context = Dispatchers.Main.immediate) {
      viewModel.transitionInfoFlow.collect { transitionInfo ->
        BackgroundUtils.ensureMainThread()

        if (transitionAnimationShown.isCompleted) {
          return@collect
        }

        runAppearAnimation(transitionInfo)
        transitionAnimationShown.complete(Unit)
      }
    }

    mainScope.launch {
      viewModel.mediaViewerState.collect { mediaViewerState ->
        BackgroundUtils.ensureMainThread()

        awaitThumbnailLoadedAndShowViewPager(mediaViewerState)
      }
    }
  }

  override fun onShow() {
    super.onShow()
    mediaViewerAdapter?.onResume()
  }

  override fun onHide() {
    super.onHide()
    mediaViewerAdapter?.onPause()
  }

  override fun onDestroy() {
    super.onDestroy()

    pager.removeOnPageChangeListener(this)
    mediaViewerAdapter?.onDestroy()
  }

  override fun onPageSelected(position: Int) {
    pager.adapter?.let { adapter ->
      adapter as MediaViewerAdapter

      adapter.doBind(position)
      viewModel.updateLastViewedIndex(position)
    }
  }

  override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
    // no-op
  }

  override fun onPageScrollStateChanged(state: Int) {
    // no-op
  }

  override fun changeMediaViewerBackgroundAlpha(newAlpha: Float) {
    mediaViewerRootLayout.alpha = newAlpha
  }

  override fun toggleSoundMuteState() {
    viewModel.toggleIsSoundMuted()
  }

  override fun isSoundCurrentlyMuted(): Boolean {
    return viewModel.isSoundMuted
  }

  override fun onTapped() {
    mediaViewerCallbacks.toggleFullScreenMode()
  }

  override fun closeMediaViewer() {
    mediaViewerCallbacks.finishActivity()
  }

  override suspend fun onDownloadButtonClick(viewableMedia: ViewableMedia, longClick: Boolean): Boolean {
    val simpleImageInfo = viewableMedia.toSimpleImageInfoOrNull()
    if (simpleImageInfo == null) {
      showToast("Cannot save image: ${viewableMedia}")
      return false
    }

    val imageSaverV2Options = imageSaverV2PersistedOptions.get()

    if (!longClick && !imageSaverV2Options.shouldShowImageSaverOptionsController()) {
      imageSaverV2.save(imageSaverV2Options, simpleImageInfo, null)
      return true
    }

    return suspendCancellableCoroutine { continuation ->
      val options = ImageSaverV2OptionsController.Options.SingleImage(
        simpleSaveableMediaInfo = simpleImageInfo,
        onSaveClicked = { updatedImageSaverV2Options, newFileName ->
          imageSaverV2.save(updatedImageSaverV2Options, simpleImageInfo, newFileName)
          continuation.resume(true)
        },
        onCanceled = { continuation.resume(false) }
      )

      val controller = ImageSaverV2OptionsController(context, options)
      presentController(controller)
    }
  }

  override fun onOptionsButtonClick(viewableMedia: ViewableMedia) {
    // TODO(KurobaEx):
  }

  override fun onMediaLongClick(
    view: View,
    viewableMedia: ViewableMedia,
    mediaLongClickOptions: List<FloatingListMenuItem>
  ) {
    if (mediaLongClickOptions.isEmpty()) {
      return
    }

    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

    val floatingListMenuController = FloatingListMenuController(
      context,
      globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      mediaLongClickOptions,
      itemClickListener = { clickedItem ->
        mediaOptionsHandlerExecutor.post { handleMenuItemClick(clickedItem, viewableMedia) }
      }
    )

    presentController(floatingListMenuController)
  }

  private suspend fun handleMenuItemClick(clickedItem: FloatingListMenuItem, viewableMedia: ViewableMedia) {
    when (clickedItem.key as Int) {
      MediaView.ACTION_IMAGE_COPY_FULL_URL -> {
        val remoteLocation = viewableMedia.mediaLocation as? MediaLocation.Remote
          ?: return

        AndroidUtils.setClipboardContent("Image URL", remoteLocation.url.toString())
        AppModuleAndroidUtils.showToast(context, R.string.image_url_copied_to_clipboard)
      }
      MediaView.ACTION_IMAGE_COPY_THUMBNAIL_URL -> {
        val previewLocationUrl = (viewableMedia.previewLocation as? MediaLocation.Remote)?.url
          ?: return

        AndroidUtils.setClipboardContent("Thumbnail URL", previewLocationUrl.toString())
        AppModuleAndroidUtils.showToast(context, R.string.image_url_copied_to_clipboard)
      }
      MediaView.ACTION_IMAGE_COPY_ORIGINAL_FILE_NAME -> {
        AndroidUtils.setClipboardContent("Original file name", viewableMedia.formatFullOriginalFileName())
        AppModuleAndroidUtils.showToast(context, R.string.image_file_name_copied_to_clipboard)
      }
      MediaView.ACTION_IMAGE_COPY_SERVER_FILE_NAME -> {
        AndroidUtils.setClipboardContent("Server file name", viewableMedia.formatFullServerFileName())
        AppModuleAndroidUtils.showToast(context, R.string.image_file_name_copied_to_clipboard)
      }
      MediaView.ACTION_IMAGE_COPY_MD5_HASH_HEX -> {
        AndroidUtils.setClipboardContent("File hash HEX", viewableMedia.viewableMediaMeta.mediaHash)
        AppModuleAndroidUtils.showToast(context, R.string.image_file_hash_copied_to_clipboard)
      }
      MediaView.ACTION_OPEN_IN_BROWSER -> {
        val mediaUrl = (viewableMedia.mediaLocation as? MediaLocation.Remote)?.url
          ?: return

        AppModuleAndroidUtils.openLink(mediaUrl.toString())
      }
      MediaView.ACTION_MEDIA_SEARCH -> {
        showImageSearchOptions(viewableMedia)
      }
      MediaView.ACTION_SHARE_MEDIA_URL -> {
        val mediaUrl = (viewableMedia.mediaLocation as? MediaLocation.Remote)?.url
          ?: return

        AppModuleAndroidUtils.shareLink(mediaUrl.toString())
      }
      MediaView.ACTION_SHARE_MEDIA_CONTENT -> {
        shareMediaContent(viewableMedia)
      }
      MediaView.ACTION_DOWNLOAD_MEDIA_FILE_CONTENT -> {
        downloadMediaFile(context, false, viewableMedia)
      }
      MediaView.ACTION_DOWNLOAD_WITH_OPTIONS_MEDIA_FILE_CONTENT -> {
        downloadMediaFile(context, true, viewableMedia)
      }
    }
  }

  private fun downloadMediaFile(
    context: Context,
    showOptions: Boolean,
    viewableMedia: ViewableMedia
  ) {
    val simpleSaveableMediaInfo = ImageSaverV2.SimpleSaveableMediaInfo.fromViewableMedia(viewableMedia)
    if (simpleSaveableMediaInfo == null) {
      return
    }

    val imageSaverV2Options = imageSaverV2PersistedOptions.get()

    if (!showOptions && !imageSaverV2Options.shouldShowImageSaverOptionsController()) {
      imageSaverV2.save(imageSaverV2Options, simpleSaveableMediaInfo, null)
      mediaViewerAdapter?.markMediaAsDownloaded(viewableMedia)
      return
    }

    val options = ImageSaverV2OptionsController.Options.SingleImage(
      simpleSaveableMediaInfo = simpleSaveableMediaInfo,
      onSaveClicked = { updatedImageSaverV2Options, newFileName ->
        imageSaverV2.save(updatedImageSaverV2Options, simpleSaveableMediaInfo, newFileName)
        mediaViewerAdapter?.markMediaAsDownloaded(viewableMedia)
      },
      onCanceled = {}
    )

    presentController(ImageSaverV2OptionsController(context, options))
  }

  private suspend fun shareMediaContent(viewableMedia: ViewableMedia) {
    val remoteMediaLocation = viewableMedia.mediaLocation as MediaLocation.Remote
      ?: return
    val downloadFileName = viewableMedia.viewableMediaMeta.serverMediaName
      ?: return
    val mediaUrl = remoteMediaLocation.url

    val loadingViewController = LoadingViewController(context, true)
    presentController(loadingViewController)

    try {
      imageSaverV2.downloadMediaAndShare(mediaUrl, downloadFileName)
    } finally {
      loadingViewController.stopPresenting()
    }
  }

  private fun showImageSearchOptions(viewableMedia: ViewableMedia) {
    val items = ImageSearch.engines
      .map { imageSearch -> FloatingListMenuItem(imageSearch.id, imageSearch.name) }

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = items,
      itemClickListener = { item ->
        for (imageSearch in ImageSearch.engines) {
          val id = item.key as Int
          if (id == imageSearch.id) {
            val searchImageUrl = getSearchImageUrl(viewableMedia)
            if (searchImageUrl == null) {
              Logger.e(TAG, "showImageSearchOptions() searchImageUrl == null")
              break
            }

            AppModuleAndroidUtils.openLink(imageSearch.getUrl(searchImageUrl.toString()))
            break
          }
        }
      }
    )

    presentController(floatingListMenuController, true)
  }

  private fun getSearchImageUrl(viewableMedia: ViewableMedia): HttpUrl? {
    if (viewableMedia is ViewableMedia.Video) {
      return (viewableMedia.previewLocation as? MediaLocation.Remote)?.url
    }

    return (viewableMedia.mediaLocation as? MediaLocation.Remote)?.url
  }

  fun onSystemUiVisibilityChanged(systemUIHidden: Boolean) {
    mediaViewerAdapter?.onSystemUiVisibilityChanged(systemUIHidden)
  }

  private suspend fun awaitThumbnailLoadedAndShowViewPager(
    mediaViewerState: MediaViewerControllerViewModel.MediaViewerControllerState?
  ) {
    BackgroundUtils.ensureMainThread()

    if (mediaViewerState == null) {
      return
    }

    transitionAnimationShown.awaitSilently(Unit)

    pager.setVisibilityFast(View.INVISIBLE)
    pager.setSwipingEnabled(true)

    val previewThumbnailLocation =
      mediaViewerState.loadedMedia[mediaViewerState.initialPagerIndex].mediaLocation

    val adapter = MediaViewerAdapter(
      context = context,
      viewModel = viewModel,
      mediaViewContract = this@MediaViewerController,
      initialPagerIndex = mediaViewerState.initialPagerIndex,
      viewableMediaList = mediaViewerState.loadedMedia,
      previewThumbnailLocation = previewThumbnailLocation,
      mediaViewerScrollerHelper = mediaViewerScrollerHelper,
      cacheDataSourceFactory = cacheDataSourceFactory,
      isSystemUiHidden = { mediaViewerCallbacks.isSystemUiHidden() }
    )

    pager.adapter = adapter
    pager.setCurrentItem(mediaViewerState.initialPagerIndex, false)

    adapter.awaitUntilPreviewThumbnailFullyLoaded()

    pager.setVisibilityFast(View.VISIBLE)
    appearPreviewImage.setVisibilityFast(View.INVISIBLE)

    Logger.d(
      TAG, "Loaded ${mediaViewerState.loadedMedia.size} media items, " +
        "initialPagerIndex=${mediaViewerState.initialPagerIndex}"
    )
  }

  private suspend fun runAppearAnimation(transitionInfo: ViewableMediaParcelableHolder.TransitionInfo?) {
    BackgroundUtils.ensureMainThread()

    if (transitionInfo == null) {
      mediaViewerRootLayout.setBackgroundColor(BACKGROUND_COLOR)
      return
    }

    val resultBitmap = imageLoaderV2.loadFromNetworkSuspend(
      context,
      transitionInfo.transitionThumbnailUrl,
      ImageLoaderV2.ImageSize.MeasurableImageSize.create(appearPreviewImage)
    )

    if (resultBitmap == null) {
      return
    }

    appearPreviewImage.setWindowLocation(transitionInfo.lastTouchPosX, transitionInfo.lastTouchPosY)
    appearPreviewImage.setBitmap(resultBitmap)

    suspendCancellableCoroutine<Unit> { continuation ->
      appearPreviewImage.runAppearAnimation(
        view,
        BACKGROUND_COLOR,
        { currentBackgroundColor -> mediaViewerRootLayout.setBackgroundColor(currentBackgroundColor) },
        { continuation.resume(Unit) }
      )
    }
  }

  interface MediaViewerCallbacks {
    fun finishActivity()

    fun isSystemUiHidden(): Boolean
    fun toggleFullScreenMode()
  }

  companion object {
    private const val TAG = "MediaViewerController"
    private const val BACKGROUND_COLOR = 0xDD000000L.toInt()
  }
}