package com.github.k1rakishou.chan.features.media_viewer

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.viewpager.widget.ViewPager
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.Chan4CloudFlareImagePreloaderManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2OptionsController
import com.github.k1rakishou.chan.features.media_viewer.helper.ExoPlayerCache
import com.github.k1rakishou.chan.features.media_viewer.helper.ExoPlayerWrapper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaLongClickMenuHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerGoToImagePostHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerGoToPostHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerMenuHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerOpenAlbumHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerOpenThreadHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerScrollerHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.ViewPagerAutoSwiper
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaViewContract
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.helper.PostLinkableClickHelper
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper
import com.github.k1rakishou.chan.ui.view.AppearTransitionImageView
import com.github.k1rakishou.chan.ui.view.OptionalSwipeViewPager
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.awaitSilently
import com.github.k1rakishou.common.extractFileName
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.persist_state.PersistableChanState.imageSaverV2PersistedOptions
import com.google.android.exoplayer2.upstream.ContentDataSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import dagger.Lazy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

class MediaViewerController(
  context: Context,
  private val mediaViewerCallbacks: MediaViewerCallbacks
) : Controller(context), ViewPager.OnPageChangeListener, MediaViewContract, WindowInsetsListener {

  @Inject
  lateinit var _imageSaverV2: Lazy<ImageSaverV2>
  @Inject
  lateinit var _chanThreadManager: Lazy<ChanThreadManager>
  @Inject
  lateinit var _siteManager: Lazy<SiteManager>
  @Inject
  lateinit var _boardManager: Lazy<BoardManager>
  @Inject
  lateinit var _archivesManager: Lazy<ArchivesManager>
  @Inject
  lateinit var _postHideManager: Lazy<PostHideManager>

  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var mediaViewerScrollerHelper: MediaViewerScrollerHelper
  @Inject
  lateinit var exoPlayerCache: ExoPlayerCache
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var chan4CloudFlareImagePreloaderManager: Chan4CloudFlareImagePreloaderManager
  @Inject
  lateinit var exclusionZonesHolder: Android10GesturesExclusionZonesHolder
  @Inject
  lateinit var mediaViewerOpenAlbumHelper: MediaViewerOpenAlbumHelper
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var mediaViewerGoToImagePostHelper: MediaViewerGoToImagePostHelper
  @Inject
  lateinit var mediaViewerGoToPostHelper: MediaViewerGoToPostHelper
  @Inject
  lateinit var mediaViewerOpenThreadHelper: MediaViewerOpenThreadHelper

  private var chanDescriptor: ChanDescriptor? = null
  private var autoSwipeJob: Job? = null
  private var lifecycleChange = false

  override val viewerChanDescriptor: ChanDescriptor?
    get() = chanDescriptor

  private lateinit var mediaViewerRootLayout: MediaViewerRootLayout
  private lateinit var appearPreviewImage: AppearTransitionImageView
  private lateinit var pager: OptionalSwipeViewPager
  private lateinit var mediaViewerToolbar: MediaViewerToolbar

  private val chanThreadManager: ChanThreadManager
    get() = _chanThreadManager.get()
  private val siteManager: SiteManager
    get() = _siteManager.get()
  private val imageSaverV2: ImageSaverV2
    get() = _imageSaverV2.get()
  private val boardManager: BoardManager
    get() = _boardManager.get()
  private val archivesManager: ArchivesManager
    get() = _archivesManager.get()
  private val postHideManager: PostHideManager
    get() = _postHideManager.get()

  private val viewPagerAutoSwiperLazy = lazy {
    ViewPagerAutoSwiper(pager)
  }
  private val viewPagerAutoSwiper by viewPagerAutoSwiperLazy

  private val postLinkableClickHelper by lazy {
    PostLinkableClickHelper(
      siteManager = siteManager,
      boardManager = boardManager,
      archivesManager = archivesManager
    )
  }

  private val mediaViewerPostCellCallback = object : PostCellInterface.PostCellCallback {
    override val currentChanDescriptor: ChanDescriptor? = chanDescriptor

    override fun onPostBind(postCellData: PostCellData) {}
    override fun onPostUnbind(postCellData: PostCellData, isActuallyRecycling: Boolean) {}
    override fun onPostClicked(postDescriptor: PostDescriptor) {}
    override fun onGoToPostButtonLongClicked(post: ChanPost, postViewMode: PostCellData.PostViewMode) {}
    override fun getBoardPages(boardDescriptor: BoardDescriptor): BoardPages? = null
    override fun onThumbnailOmittedFilesClicked(postCellData: PostCellData, postImage: ChanPostImage) {}
    override fun onPreviewThreadPostsClicked(post: ChanPost) {}
    override fun onPostOptionClicked(post: ChanPost, item: FloatingListMenuItem, inPopup: Boolean) {}
    override fun currentSpanCount(): Int = 1

    override fun onThumbnailLongClicked(chanDescriptor: ChanDescriptor, postImage: ChanPostImage) {
      notSupported()
    }
    override fun onPostPosterIdClicked(post: ChanPost) {
      notSupported()
    }
    override fun onPostPosterNameClicked(post: ChanPost) {
      notSupported()
    }
    override fun onPostPosterTripcodeClicked(post: ChanPost) {
      notSupported()
    }
    override fun onPopulatePostOptions(post: ChanPost, menu: MutableList<FloatingListMenuItem>, inPopup: Boolean) {
      notSupported()
    }
    override fun onPostLinkableLongClicked(post: ChanPost, linkable: PostLinkable, inPopup: Boolean) {
      notSupported()
    }
    override fun onPostNoClicked(post: ChanPost) {
      notSupported()
    }
    override fun onPostSelectionQuoted(postDescriptor: PostDescriptor, selection: CharSequence) {
      notSupported()
    }
    override fun onPostSelectionFilter(postDescriptor: PostDescriptor, selection: CharSequence) {
      notSupported()
    }
    override fun showPostOptions(post: ChanPost, inPopup: Boolean, items: List<FloatingListMenuItem>) {
      notSupported()
    }
    override fun onUnhidePostClick(post: ChanPost, inPopup: Boolean) {
      notSupported()
    }

    override fun onGoToPostButtonClicked(post: ChanPost, postViewMode: PostCellData.PostViewMode) {
      onGoToPostClick(post.postDescriptor)
    }
    override fun onThumbnailClicked(postCellData: PostCellData, postImage: ChanPostImage) {
      scrollToClickedImage(postImage)
    }
    override fun onShowPostReplies(post: ChanPost) {
      showReplyChain(post.postDescriptor)
    }
    override fun onPostLinkableClicked(post: ChanPost, linkable: PostLinkable, inPopup: Boolean) {
      mainScope.launch {
        val currentChanDescriptor = chanDescriptor
          ?: return@launch

        postLinkableClickHelper.onPostLinkableClicked(
          context = context,
          post = post,
          currentChanDescriptor = currentChanDescriptor,
          linkable = linkable,
          onQuoteClicked = { postNo ->
            val postDescriptor = when (currentChanDescriptor) {
              is ChanDescriptor.CompositeCatalogDescriptor -> {
                error("Cannot use CompositeCatalogDescriptor here")
              }
              is ChanDescriptor.CatalogDescriptor -> {
                PostDescriptor.create(currentChanDescriptor, postNo)
              }
              is ChanDescriptor.ThreadDescriptor -> {
                PostDescriptor.create(currentChanDescriptor, postNo)
              }
            }

            showPost(postDescriptor)
          },
          onQuoteToHiddenOrRemovedPostClicked = { notSupported() },
          onLinkClicked = { notSupported() },
          onCrossThreadLinkClicked = { notSupported() },
          onBoardLinkClicked = { notSupported() },
          onSearchLinkClicked = { _, _ -> notSupported() },
          onDeadQuoteClicked = { _, _ -> notSupported() },
          onArchiveQuoteClicked = { notSupported() }
        )
      }
    }

    private fun notSupported() {
      showToast(R.string.media_viewer_action_not_supported)
    }
  }

  private val postPopupHelperCallback = object : PostPopupHelper.PostPopupHelperCallback {
    override fun presentRepliesController(controller: Controller) = presentController(controller)
    override fun highlightPost(postDescriptor: PostDescriptor?, blink: Boolean) {}
    override fun scrollToPost(postDescriptor: PostDescriptor, smooth: Boolean) {}
  }

  private val postPopupHelper by lazy {
    PostPopupHelper(
      context = context,
      postCellCallback = mediaViewerPostCellCallback,
      _chanThreadManager = _chanThreadManager,
      callback = postPopupHelperCallback
    )
  }

  private val viewModel by (context as ComponentActivity).viewModels<MediaViewerControllerViewModel>()

  private val mediaViewerMenuHelper by lazy {
    MediaViewerMenuHelper(
      globalWindowInsetsManager = globalWindowInsetsManager,
      presentControllerFunc = { controller -> presentController(controller, true) },
      showToastFunc = { messageId -> showToast(messageId) }
    )
  }

  private val mediaLongClickMenuHelper by lazy {
    MediaLongClickMenuHelper(
      scope = mainScope,
      globalWindowInsetsManager = globalWindowInsetsManager,
      imageSaverV2 = imageSaverV2,
      getMediaViewerAdapterFunc = { mediaViewerAdapter },
      presentControllerFunc = { controller -> presentController(controller, true) }
    )
  }

  private val transitionAnimationAwaitable = CompletableDeferred<Unit>()

  private val mediaViewerAdapter: MediaViewerAdapter?
    get() = pager.adapter as? MediaViewerAdapter

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    view = AppModuleAndroidUtils.inflate(context, R.layout.controller_media_viewer)

    mediaViewerRootLayout = view.findViewById(R.id.media_viewer_root_layout)
    appearPreviewImage = view.findViewById(R.id.appear_preview_image)

    mediaViewerToolbar = view.findViewById(R.id.media_viewer_toolbar)
    mediaViewerToolbar.onCreate()

    globalWindowInsetsManager.addInsetsUpdatesListener(this)

    pager = view.findViewById(R.id.pager)
    pager.addOnPageChangeListener(this)

    val offscreenPageLimit = MediaViewerControllerViewModel.offscreenPageLimit()
    Logger.d(TAG, "offscreenPageLimit=$offscreenPageLimit")
    pager.offscreenPageLimit = offscreenPageLimit

    onInsetsChanged()

    mainScope.launch(context = Dispatchers.Main.immediate) {
      viewModel.transitionInfoFlow.collect { transitionInfo ->
        BackgroundUtils.ensureMainThread()

        if (transitionAnimationAwaitable.isCompleted) {
          return@collect
        }

        runAppearAnimation(transitionInfo)
        transitionAnimationAwaitable.complete(Unit)
      }
    }

    mainScope.launch {
      viewModel.mediaViewerState.collect { mediaViewerState ->
        BackgroundUtils.ensureMainThread()

        awaitThumbnailLoadedAndShowViewPager(mediaViewerState)
      }
    }
  }

  fun onResume() {
    lifecycleChange = true
    mediaViewerAdapter?.onResume()
  }

  fun onPause() {
    lifecycleChange = true
    mediaViewerAdapter?.onPause()

    if (viewPagerAutoSwiperLazy.isInitialized()) {
      viewPagerAutoSwiper.stop()
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    postPopupHelper.popAll()
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)

    mediaViewerAdapter?.onDestroy()
    mediaLongClickMenuHelper.onDestroy()
    mediaViewerToolbar.onDestroy()

    chanDescriptor = null
    pager.removeOnPageChangeListener(this)
    pager.adapter = null

    ExoPlayerWrapper.releaseAll()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    mediaViewerAdapter?.onInsetsChanged()
    onInsetsChanged()
  }

  override fun onInsetsChanged() {
    if (ChanSettings.mediaViewerDrawBehindNotch.get()) {
      mediaViewerRootLayout.updatePaddings(top = 0, left = 0, right = 0, bottom = 0)
    } else {
      mediaViewerRootLayout.updatePaddings(
        top = globalWindowInsetsManager.top(),
        left = globalWindowInsetsManager.left(),
        right = globalWindowInsetsManager.right(),
        bottom = globalWindowInsetsManager.bottom()
      )
    }
  }

  override fun onPageSelected(position: Int) {
    mediaViewerAdapter?.let { adapter ->
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

  override fun isSystemUiHidden(): Boolean {
    return mediaViewerCallbacks.isSystemUiHidden()
  }

  override fun onTapped() {
    mediaViewerCallbacks.toggleFullScreenMode()
  }

  override fun closeMediaViewer() {
    mediaViewerCallbacks.finishActivity()
  }

  override suspend fun onDownloadButtonClick(viewableMedia: ViewableMedia, longClick: Boolean): Boolean {
    val downloading = if (viewableMedia.viewableMediaMeta.ownerPostDescriptor == null) {
      tryDownloadIntoUserProvidedFile(viewableMedia)
    } else {
      val simpleImageInfo = viewableMedia.toSimpleImageInfoOrNull()
      if (simpleImageInfo == null) {
        showToast(getString(R.string.media_viewer_cannot_save_media, viewableMedia))
        return false
      }

      startMediaDownloadInternal(longClick, simpleImageInfo)
    }

    if (downloading && ChanSettings.mediaViewerAutoSwipeAfterDownload.get()) {
      tryEnqueueAutoSwipe()
    }

    return downloading
  }

  private fun tryEnqueueAutoSwipe() {
    autoSwipeJob?.cancel()
    autoSwipeJob = null

    autoSwipeJob = mainScope.launch {
      val swiped = when (pager.swipeDirection.withoutDefault()) {
        null,
        OptionalSwipeViewPager.SwipeDirection.Default -> return@launch
        OptionalSwipeViewPager.SwipeDirection.Forward -> {
          delay(AUTO_SWIPE_DELAY)
          pager.swipeForward()
        }
        OptionalSwipeViewPager.SwipeDirection.Backward -> {
          delay(AUTO_SWIPE_DELAY)
          pager.swipeBackward()
        }
      }

      if (!swiped) {
        showToast(getString(R.string.media_viewer_auto_swipe_end_reached))
      }

      autoSwipeJob = null
    }
  }

  private suspend fun tryDownloadIntoUserProvidedFile(viewableMedia: ViewableMedia): Boolean {
    val remoteMediaLocation = viewableMedia.mediaLocation as? MediaLocation.Remote
      ?: return false

    var fileName = remoteMediaLocation.url.extractFileName()
    if (fileName.isNullOrEmpty()) {
      val fileExtension = StringUtils.extractFileNameExtension(remoteMediaLocation.url.toString())
      if (fileExtension.isNotNullNorEmpty()) {
        fileName = "media_file.${fileExtension}"
      }
    }

    if (fileName == null) {
      fileName = "media_file"
    }

    val outputFileUri = suspendCancellableCoroutine<Uri?> { cancellableContinuation ->
      fileChooser.openCreateFileDialog(fileName, object : FileCreateCallback() {
        override fun onCancel(reason: String) {
          cancellableContinuation.resumeValueSafe(null)
        }

        override fun onResult(uri: Uri) {
          cancellableContinuation.resumeValueSafe(uri)
        }
      })
    }

    if (outputFileUri == null) {
      return false
    }

    val result = imageSaverV2.downloadMediaIntoUserProvidedFile(
      mediaUrl = remoteMediaLocation.url,
      outputFileUri = outputFileUri
    )

    when (result) {
      is ModularResult.Error -> {
        Logger.e(TAG, "downloadMediaIntoUserProvidedFile('${remoteMediaLocation.url}', " +
          "'${outputFileUri}') error", result.error)
        return false
      }
      is ModularResult.Value -> {
        Logger.d(TAG, "downloadMediaIntoUserProvidedFile('${remoteMediaLocation.url}', " +
          "'${outputFileUri}') success")
        return true
      }
    }
  }

  private suspend fun startMediaDownloadInternal(
    longClick: Boolean,
    simpleImageInfo: ImageSaverV2.SimpleSaveableMediaInfo
  ): Boolean {
    val imageSaverV2Options = imageSaverV2PersistedOptions.get()

    if (!longClick && !imageSaverV2Options.shouldShowImageSaverOptionsController()) {
      imageSaverV2.save(imageSaverV2Options, simpleImageInfo, null)
      return true
    }

    return suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
      val options = ImageSaverV2OptionsController.Options.SingleImage(
        simpleSaveableMediaInfo = simpleImageInfo,
        onSaveClicked = { updatedImageSaverV2Options, newFileName ->
          imageSaverV2.save(updatedImageSaverV2Options, simpleImageInfo, newFileName)
          cancellableContinuation.resumeValueSafe(true)
        },
        onCanceled = { cancellableContinuation.resumeValueSafe(false) }
      )

      val controller = ImageSaverV2OptionsController(context, options)
      presentController(controller)

      cancellableContinuation.invokeOnCancellation { cause ->
        if (cause == null) {
          return@invokeOnCancellation
        }

        controller.stopPresenting()
      }
    }
  }

  override fun onOptionsButtonClick(viewableMedia: ViewableMedia) {
    val adapter = mediaViewerAdapter
      ?: return

    mediaViewerMenuHelper.onMediaViewerOptionsClick(
      context = context,
      mediaViewerAdapter = adapter,
      handleClickedOption = { clickedOptionId ->
        when (clickedOptionId) {
          MediaViewerMenuHelper.ACTION_USE_MPV -> {
            reloadLoadedMedia()
          }
          MediaViewerMenuHelper.ACTION_VIEW_PAGER_AUTO_SWIPE -> {
            viewPagerAutoSwiper.start()
          }
          else -> {
            // no-op
          }
        }
      }
    )
  }

  override fun onMediaLongClick(
    view: View,
    viewableMedia: ViewableMedia
  ) {
    mediaLongClickMenuHelper.onMediaLongClick(view, viewableMedia)
  }

  private fun onGoToPostClick(postDescriptor: PostDescriptor) {
    if (mediaViewerGoToPostHelper.tryGoToPost(postDescriptor)) {
      closeMediaViewer()
    }
  }

  override fun onGoToPostMediaClick(viewableMedia: ViewableMedia, postDescriptor: PostDescriptor) {
    val mediaViewerOptions = viewModel.mediaViewerOptions.value
    var closeMediaViewer = false

    if (mediaViewerOptions.mediaViewerOpenedFromAlbum) {
      val mediaLocation = viewableMedia.mediaLocation

      if (mediaViewerGoToImagePostHelper.tryGoToPost(chanDescriptor, postDescriptor, mediaLocation)) {
        closeMediaViewer = true
      }
    } else if (chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
      if (mediaViewerOpenThreadHelper.tryToOpenThread(postDescriptor)) {
        closeMediaViewer = true
      }
    }

    if (closeMediaViewer) {
      closeMediaViewer()
    }
  }

  private fun scrollToClickedImage(postImage: ChanPostImage) {
    val adapter = mediaViewerAdapter
      ?: return

    val index = adapter.indexOfPostImageOrNull(postImage)
      ?: return

    if (adapter.lastViewedMediaPosition != index) {
      pager.setCurrentItem(index, false)
    }

    postPopupHelper.popAll()
  }

  private fun showPost(postDescriptor: PostDescriptor) {
    val threadDescriptor = postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    val chanPost = chanThreadManager.getPost(postDescriptor)
    if (chanPost == null) {
      Logger.e(TAG, "showPost($postDescriptor) chanPost is null")
      return
    }

    if (postHideManager.hiddenOrRemoved(chanPost.postDescriptor)) {
      Logger.e(TAG, "showPost($postDescriptor) chanPost is hidden or removed")
      return
    }

    postPopupHelper.showRepliesPopup(
      threadDescriptor = threadDescriptor,
      postViewMode = PostCellData.PostViewMode.MediaViewerPostsPopup,
      postDescriptor = postDescriptor,
      posts = listOf(chanPost)
    )

    Logger.d(TAG, "showReplyChain($postDescriptor)")
  }

  override fun showReplyChain(postDescriptor: PostDescriptor) {
    val chanPost = chanThreadManager.getPost(postDescriptor)
    if (chanPost == null) {
      Logger.e(TAG, "showReplyChain($postDescriptor) chanPost is null")
      return
    }

    if (postHideManager.hiddenOrRemoved(chanPost.postDescriptor)) {
      Logger.e(TAG, "showReplyChain($postDescriptor) chanPost is hidden or removed")
      return
    }

    val threadDescriptor = chanPost.postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
    if (threadDescriptor == null) {
      Logger.e(TAG, "showReplyChain($postDescriptor) threadDescriptor is null")
      return
    }

    val posts = ArrayList<ChanPost>()

    chanPost.repliesFromCopy.forEach { replyPostDescriptor ->
      val replyPost = chanThreadManager.findPostByPostDescriptor(replyPostDescriptor)
      if (replyPost != null) {
        posts.add(replyPost)
      }
    }

    if (!postPopupHelper.displayingAnything) {
      posts.add(0, chanPost)
    }

    if (posts.isEmpty()) {
      return
    }

    postPopupHelper.showRepliesPopup(
      threadDescriptor = threadDescriptor,
      postViewMode = PostCellData.PostViewMode.MediaViewerPostsPopup,
      postDescriptor = postDescriptor,
      posts = posts
    )

    Logger.d(TAG, "showReplyChain($postDescriptor) posts.size=${posts.size}")
  }

  override suspend fun defaultArtworkDrawable(): Drawable? {
    return imageLoaderV2.loadFromNetworkSuspend(
      context = context,
      url = AppConstants.RESOURCES_ENDPOINT + AUDIO_THUMB_FILE_NAME,
      cacheFileType = CacheFileType.PostMediaFull,
      imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(appearPreviewImage),
      transformations = emptyList()
    ).valueOrNull()
  }

  override fun openAlbum(viewableMedia: ViewableMedia) {
    val postDescriptor = viewableMedia.viewableMediaMeta.ownerPostDescriptor
      ?: return

    val albumOpened = mediaViewerOpenAlbumHelper.openAlbum(
      chanDescriptor = chanDescriptor,
      postDescriptor = postDescriptor,
      mediaLocation = viewableMedia.mediaLocation
    )

    if (!albumOpened) {
      return
    }

    closeMediaViewer()
  }

  private fun reloadLoadedMedia() {
    val adapter = mediaViewerAdapter ?: return

    val loadedViews = adapter.getLoadedViews()
    if (loadedViews.isEmpty()) {
      return
    }

    val toReload = loadedViews
      .filter { loadedView -> loadedView.mediaView.viewableMedia is ViewableMedia.Video }
      .map { loadedView -> Pair(loadedView.viewIndex, loadedView.mediaView.viewableMedia) }

    adapter.reloadManyAs(toReload)
    adapter.doBind(adapter.lastViewedMediaPosition)
  }

  override fun reloadAs(pagerPosition: Int, viewableMedia: ViewableMedia) {
    mediaViewerAdapter?.reloadAs(pagerPosition, viewableMedia)
    onPageSelected(pagerPosition)
  }

  fun onSystemUiVisibilityChanged(systemUIHidden: Boolean) {
    mediaViewerAdapter?.onSystemUiVisibilityChanged(systemUIHidden)
  }

  private suspend fun awaitThumbnailLoadedAndShowViewPager(
    mediaViewerState: MediaViewerControllerViewModel.MediaViewerControllerState?
  ) {
    BackgroundUtils.ensureMainThread()

    this.chanDescriptor = mediaViewerState?.descriptor

    if (mediaViewerState == null) {
      return
    }

    transitionAnimationAwaitable.awaitSilently(Unit)

    pager.setVisibilityFast(View.INVISIBLE)
    pager.setSwipingEnabled(true)

    val previewThumbnailLocation = mediaViewerState.loadedMedia.getOrNull(mediaViewerState.initialPagerIndex)
      ?.mediaLocation
      ?: return

    val adapter = MediaViewerAdapter(
      context = context,
      appConstants = appConstants,
      viewModel = viewModel,
      mediaViewerToolbar = mediaViewerToolbar,
      mediaViewContract = this@MediaViewerController,
      initialPagerIndex = mediaViewerState.initialPagerIndex,
      viewableMediaList = mediaViewerState.loadedMedia,
      previewThumbnailLocation = previewThumbnailLocation,
      mediaViewerScrollerHelper = mediaViewerScrollerHelper,
      cachedHttpDataSourceFactory = createCacheDataSourceFactory(mediaViewerState.loadedMedia),
      fileDataSourceFactory = FileDataSource.Factory(),
      contentDataSourceFactory = DataSource.Factory { ContentDataSource(context) },
      chan4CloudFlareImagePreloaderManager = chan4CloudFlareImagePreloaderManager,
      isSystemUiHidden = { mediaViewerCallbacks.isSystemUiHidden() },
      swipeDirection = { pager.swipeDirection },
      getAndConsumeLifecycleChangeFlag = {
        val wasLifecycleChange = lifecycleChange
        lifecycleChange = false
        return@MediaViewerAdapter wasLifecycleChange
      }
    )

    pager.adapter = adapter
    pager.setCurrentItem(mediaViewerState.initialPagerIndex, false)

    withTimeoutOrNull(MAX_WAIT_TIME_MS) { adapter.awaitUntilPreviewThumbnailFullyLoaded() }

    pager.setVisibilityFast(View.VISIBLE)
    appearPreviewImage.setVisibilityFast(View.INVISIBLE)

    Logger.d(TAG, "Loaded ${mediaViewerState.loadedMedia.size} media items, " +
        "initialPagerIndex=${mediaViewerState.initialPagerIndex}")
  }

  private fun createCacheDataSourceFactory(viewableMedia: List<ViewableMedia>): DataSource.Factory {
    val defaultDataSourceFactory = DefaultHttpDataSource.Factory()
      .setDefaultRequestProperties(createRequestProperties(viewableMedia))

    return CacheDataSource.Factory()
      .setCache(exoPlayerCache.actualCache)
      .setUpstreamDataSourceFactory(defaultDataSourceFactory)
  }

  private fun createRequestProperties(viewableMediaList: List<ViewableMedia>): Map<String, String> {
    val siteDescriptors = hashSetOf<SiteDescriptor>()

    viewableMediaList.forEach { viewableMedia ->
      val siteDescriptor = viewableMedia.viewableMediaMeta.ownerPostDescriptor?.siteDescriptor()
        ?: return@forEach

      siteDescriptors += siteDescriptor
    }

    if (siteDescriptors.isEmpty()) {
      return emptyMap()
    }

    val requestProps = mutableMapOf<String, String>()

    siteDescriptors.forEach { siteDescriptor ->
      val site = siteManager.bySiteDescriptor(siteDescriptor)
      if (site != null) {
        site.requestModifier().modifyVideoStreamRequest(site, requestProps)
      }
    }

    return requestProps
  }

  private suspend fun runAppearAnimation(transitionInfo: ViewableMediaParcelableHolder.TransitionInfo?) {
    BackgroundUtils.ensureMainThread()

    if (transitionInfo == null) {
      mediaViewerRootLayout.setBackgroundColor(FINAL_BACKGROUND_COLOR)
      return
    }

    val resultBitmap = withTimeoutOrNull(MAX_WAIT_TIME_MS) {
      return@withTimeoutOrNull imageLoaderV2.loadFromNetworkSuspend(
        context = context,
        url = transitionInfo.transitionThumbnailUrl,
        cacheFileType = CacheFileType.PostMediaThumbnail,
        imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(appearPreviewImage)
      ).valueOrNull()?.bitmap
    }

    if (resultBitmap == null) {
      mediaViewerRootLayout.setBackgroundColor(FINAL_BACKGROUND_COLOR)
      return
    }

    appearPreviewImage.setWindowLocation(transitionInfo.lastTouchPosX, transitionInfo.lastTouchPosY)
    appearPreviewImage.setBitmap(resultBitmap)

    suspendCancellableCoroutine<Unit> { continuation ->
      appearPreviewImage.runAppearAnimation(
        view,
        START_BACKGROUND_COLOR,
        FINAL_BACKGROUND_COLOR,
        { currentBackgroundColor -> mediaViewerRootLayout.setBackgroundColor(currentBackgroundColor) },
        { continuation.resumeValueSafe(Unit) }
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
    private const val START_BACKGROUND_COLOR = Color.TRANSPARENT
    private const val FINAL_BACKGROUND_COLOR = Color.BLACK
    private const val MAX_WAIT_TIME_MS = 1000L
    private const val AUTO_SWIPE_DELAY = 350L
    private const val AUDIO_THUMB_FILE_NAME = "audio_thumb.png"
  }
}