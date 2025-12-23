package com.github.k1rakishou.chan.ui.layout

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.snapshotFlow
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.BoardPostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressEvent
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.LastViewedPostNoInfoHolder
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.CurrentFocusedControllers
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.PostHighlightManager
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter
import com.github.k1rakishou.chan.core.usecase.ExtractPostMapInfoHolderUseCase
import com.github.k1rakishou.chan.features.reply.ReplyLayoutView
import com.github.k1rakishou.chan.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.chan.features.reply.data.ReplyFileAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarState
import com.github.k1rakishou.chan.ui.adapter.PostAdapter
import com.github.k1rakishou.chan.ui.adapter.PostAdapter.PostAdapterCallback
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.ui.cell.GenericPostCell
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.PreviousThreadScrollPositionData
import com.github.k1rakishou.chan.ui.cell.ThreadStatusCell
import com.github.k1rakishou.chan.ui.compose.lazylist.ScrollbarView
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.controller.CaptchaContainerController
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.view.FixedLinearLayoutManager
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.ViewUtils.hackMaxFlingVelocity
import com.github.k1rakishou.chan.utils.setBackgroundColorFast
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.options.ChanLoadOptions
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.PostIndexed
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache
import com.github.k1rakishou.persist_state.IndexAndTop
import com.github.k1rakishou.persist_state.ReplyMode
import dagger.Lazy
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class ThreadListLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defAttrStyle: Int = 0
) : FrameLayout(context, attrs, defAttrStyle),
  ThemeEngine.ThemeChangesListener,
  ScrollbarView.ThumbDragListener,
  ReplyLayoutViewModel.ThreadListLayoutCallbacks {

  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var appResources: AppResources
  @Inject
  lateinit var globalUiStateHolder: GlobalUiStateHolder

  @Inject
  lateinit var themeEngineLazy: Lazy<ThemeEngine>
  @Inject
  lateinit var extractPostMapInfoHolderUseCaseLazy: Lazy<ExtractPostMapInfoHolderUseCase>
  @Inject
  lateinit var lastViewedPostNoInfoHolderLazy: Lazy<LastViewedPostNoInfoHolder>
  @Inject
  lateinit var chanThreadViewableInfoManagerLazy: Lazy<ChanThreadViewableInfoManager>
  @Inject
  lateinit var globalWindowInsetsManagerLazy: Lazy<GlobalWindowInsetsManager>
  @Inject
  lateinit var chanThreadManagerLazy: Lazy<ChanThreadManager>
  @Inject
  lateinit var chanCatalogSnapshotCacheLazy: Lazy<ChanCatalogSnapshotCache>
  @Inject
  lateinit var chanLoadProgressNotifierLazy: Lazy<ChanLoadProgressNotifier>
  @Inject
  lateinit var postHighlightManagerLazy: Lazy<PostHighlightManager>
  @Inject
  lateinit var currentOpenedDescriptorStateManagerLazy: Lazy<CurrentOpenedDescriptorStateManager>

  private val themeEngine: ThemeEngine
    get() = themeEngineLazy.get()
  private val extractPostMapInfoHolderUseCase: ExtractPostMapInfoHolderUseCase
    get() = extractPostMapInfoHolderUseCaseLazy.get()
  private val lastViewedPostNoInfoHolder: LastViewedPostNoInfoHolder
    get() = lastViewedPostNoInfoHolderLazy.get()
  private val chanThreadViewableInfoManager: ChanThreadViewableInfoManager
    get() = chanThreadViewableInfoManagerLazy.get()
  private val globalWindowInsetsManager: GlobalWindowInsetsManager
    get() = globalWindowInsetsManagerLazy.get()
  private val chanThreadManager: ChanThreadManager
    get() = chanThreadManagerLazy.get()
  private val chanCatalogSnapshotCache: ChanCatalogSnapshotCache
    get() = chanCatalogSnapshotCacheLazy.get()
  private val chanLoadProgressNotifier: ChanLoadProgressNotifier
    get() = chanLoadProgressNotifierLazy.get()
  private val postHighlightManager: PostHighlightManager
    get() = postHighlightManagerLazy.get()
  private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
    get() = currentOpenedDescriptorStateManagerLazy.get()

  private val scrollListener: RecyclerView.OnScrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      if (newState == RecyclerView.SCROLL_STATE_IDLE) {
        onRecyclerViewScrolled()
      }
    }
  }

  val replyLayoutViewCallbacks: ThreadListLayout.ReplyLayoutViewCallbacks
    get() = replyLayoutView

  val displayingPostDescriptors: List<PostDescriptor>
    get() = postAdapter.displayList

  val indexAndTop: IndexAndTop?
    get() {
      var index = 0
      var top = 0

      val layoutManager = recyclerView.layoutManager
        ?: return null

      if (layoutManager.childCount > 0) {
        val topChild = layoutManager.getChildAt(0)
          ?: return null

        index = (topChild.layoutParams as RecyclerView.LayoutParams).viewLayoutPosition
        val params = topChild.layoutParams as RecyclerView.LayoutParams
        top = layoutManager.getDecoratedTop(topChild) - params.topMargin - recyclerView.paddingTop
      }

      return IndexAndTop(index = index, top = top)
    }

  val currentSpanCount: Int
    get() {
      if (layoutManager == null) {
        return 1
      }

      return when (boardPostViewMode) {
        BoardPostViewMode.LIST -> 1
        BoardPostViewMode.GRID,
        BoardPostViewMode.STAGGER -> (layoutManager as StaggeredGridLayoutManager).spanCount
        null -> 1
      }
    }

  private val completeBottomAdapterPosition: Int
    get() {
      if (layoutManager == null) {
        return -1
      }

      when (boardPostViewMode) {
        BoardPostViewMode.LIST -> return (layoutManager as FixedLinearLayoutManager).findLastCompletelyVisibleItemPosition()
        BoardPostViewMode.GRID,
        BoardPostViewMode.STAGGER -> {
          val positions = (layoutManager as StaggeredGridLayoutManager).findLastCompletelyVisibleItemPositions(null)
          if (positions.isEmpty()) {
            return -1
          }

          val totalItemsCount = (layoutManager as StaggeredGridLayoutManager).itemCount - 1

          val hasViewTouchingBottom = positions.any { position -> position == totalItemsCount }
          if (!hasViewTouchingBottom) {
            return -1
          }

          return totalItemsCount
        }
        null -> -1
      }

      return -1
    }

  private val gridModeSpaceItemDecoration = GridModeSpaceItemDecoration()

  private lateinit var replyLayoutView: ReplyLayoutView
  private lateinit var snowLayout: SnowLayout
  private lateinit var recyclerView: RecyclerView
  private lateinit var scrollbarView: ScrollbarView
  private lateinit var postAdapter: PostAdapter

  private val compositeDisposable = CompositeDisposable()
  private val job = SupervisorJob()
  private val coroutineScope = CoroutineScope(job + Dispatchers.Main + CoroutineName("ThreadListLayout"))

  private lateinit var listScrollToBottomExecutor: RendezvousCoroutineExecutor
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor

  private var threadPresenter: ThreadPresenter? = null
  private var layoutManager: RecyclerView.LayoutManager? = null
  private var callback: ThreadListLayoutPresenterCallback? = null
  private var currentThreadControllerType: ThreadControllerType? = null
  private var threadListLayoutCallback: ThreadListLayoutCallback? = null
  private var boardPostViewMode: BoardPostViewMode? = null
  private var spanCount = 2
  private var prevLastPostNo = 0L
  private var updatePostMarksJob: Job? = null

  private fun getCurrentChanDescriptor(): ChanDescriptor? {
    return threadPresenter?.currentChanDescriptor
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    themeEngine.removeListener(this)
  }

  override fun onFinishInflate() {
    super.onFinishInflate()

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    // View binding
    replyLayoutView = findViewById(R.id.reply_layout_view)
    snowLayout = findViewById(R.id.snow_layout)
    recyclerView = findViewById(R.id.recycler_view)
    recyclerView.hackMaxFlingVelocity()
    scrollbarView = findViewById(R.id.thread_list_controller_scrollbar)
    scrollbarView.attachRecyclerView(recyclerView)
    scrollbarView.isScrollbarDraggable(true)
    scrollbarView.thumbDragListener(this)

    onThemeChanged()
  }

  override fun onThemeChanged() {
    setBackgroundColorFast(themeEngine.chanTheme.backColor)
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    val cardWidth = getDimen(R.dimen.grid_card_width)
    val gridCountSetting = ChanSettings.catalogSpanCount.get()

    if (gridCountSetting > 0) {
      spanCount = gridCountSetting
    } else {
      spanCount = max(1, (measuredWidth.toFloat() / cardWidth).roundToInt())
    }

    if (boardPostViewMode == BoardPostViewMode.GRID
      || boardPostViewMode == BoardPostViewMode.STAGGER) {
      (layoutManager as StaggeredGridLayoutManager).spanCount = spanCount
    }
  }

  override fun onDragStarted() {
    // no-op
  }

  override fun onDragEnded() {
    // Fast scroller does not trigger RecyclerView's onScrollStateChanged() so we need to call it
    //  manually after we are down scrolling via Fast scroller.
    onRecyclerViewScrolled()
  }

  override fun onPresolveCaptchaButtonClicked() {
    val chanDescriptor = currentChanDescriptorOrNull()
      ?: return

    val replyMode = threadPresenter?.replyModeForChanDescriptor(chanDescriptor)
      ?: return

    showCaptcha(
      chanDescriptor = chanDescriptor,
      replyMode = replyMode,
      autoReply = false,
      afterPostingAttempt = false,
      onFinished = null
    )
  }

  override suspend fun onPostedSuccessfully(
    prevChanDescriptor: ChanDescriptor,
    newThreadDescriptor: ThreadDescriptor
  ) {
    threadPresenter?.requestNewPostLoad()

    if (prevChanDescriptor.isCatalogDescriptor()) {
      callback?.showThread(newThreadDescriptor)
    }
  }

  override fun showProgressDialog(title: String) {
    val loadingViewController = LoadingViewController(context, true, title)
    presentController(loadingViewController)
  }

  override fun hideProgressDialog() {
    threadListLayoutCallback?.unpresentController { controller -> controller is LoadingViewController }
  }

  override fun presentController(controller: BaseFloatingController) {
    threadListLayoutCallback?.presentController(controller)
  }

  override fun showMediaReencodingController(attachedMedia: ReplyFileAttachable, fileSupportedForReencoding: Boolean) {
    threadListLayoutCallback?.showImageReencodingWindow(attachedMedia.fileUuid, fileSupportedForReencoding)
  }

  override fun highlightQuotes(quotes: Set<PostDescriptor>) {
    highlightPosts(
      postDescriptors = quotes,
      blink = false
    )
  }

  fun onCreate(
    threadPresenter: ThreadPresenter,
    threadListLayoutCallback: ThreadListLayoutCallback,
    threadControllerType: ThreadControllerType
  ) {
    this.callback = threadPresenter
    this.threadPresenter = threadPresenter
    this.threadListLayoutCallback = threadListLayoutCallback
    this.currentThreadControllerType = threadControllerType

    listScrollToBottomExecutor = RendezvousCoroutineExecutor(coroutineScope)
    serializedCoroutineExecutor = SerializedCoroutineExecutor(coroutineScope)

    replyLayoutView.onCreate(threadControllerType, this)

    postAdapter = PostAdapter(
      recyclerView,
      threadPresenter as PostAdapterCallback,
      threadPresenter as PostCellCallback,
      threadPresenter as ThreadStatusCell.Callback
    )

    val pool = recyclerView.recycledViewPool
    pool.setMaxRecycledViews(PostCellData.TYPE_POST_ZERO_OR_SINGLE_THUMBNAIL_LEFT_ALIGNMENT, 10)
    pool.setMaxRecycledViews(PostCellData.TYPE_POST_ZERO_OR_SINGLE_THUMBNAIL_RIGHT_ALIGNMENT, 10)
    pool.setMaxRecycledViews(PostCellData.TYPE_POST_MULTIPLE_THUMBNAILS, 10)
    pool.setMaxRecycledViews(PostCellData.TYPE_POST_STUB, 10)
    pool.setMaxRecycledViews(PostCellData.TYPE_POST_CARD, 10)

    recyclerView.adapter = postAdapter
    recyclerView.addOnScrollListener(scrollListener)
    recyclerView.addItemDecoration(gridModeSpaceItemDecoration)

    attachToolbarScroll(attach = true)

    coroutineScope.launch {
      if (ChanSettings.isSplitLayoutMode()) {
        combine(
          flow = globalUiStateHolder.replyLayout.state(ThreadControllerType.Catalog).height,
          flow2 = globalUiStateHolder.replyLayout.state(ThreadControllerType.Thread).height,
          transform = { catalogReplyLayoutHeight, threadReplyLayoutHeight -> catalogReplyLayoutHeight to threadReplyLayoutHeight }
        )
          .onEach { setRecyclerViewPadding() }
          .collect()
      } else {
        globalUiStateHolder.replyLayout.state(threadControllerType).height
          .onEach { setRecyclerViewPadding() }
          .collect()
      }
    }

    coroutineScope.launch {
      snapshotFlow { globalWindowInsetsManager.currentWindowInsets.value }
        .onEach { setRecyclerViewPadding() }
        .collect()
    }

    coroutineScope.launch {
      globalUiStateHolder.replyLayout.state(threadControllerType).layoutVisibility
        .onEach { replyLayoutVisibility ->
          attachToolbarScroll(attach = replyLayoutVisibility.isCollapsed())
        }
        .collect()
    }

    coroutineScope.launch {
      threadListLayoutCallback.kurobaToolbarState.toolbarHeightState
        .onEach { setRecyclerViewPadding() }
        .collect()
    }
  }

  fun onDestroy() {
    compositeDisposable.clear()
    job.cancelChildren()

    updatePostMarksJob?.cancel()
    updatePostMarksJob = null

    recyclerView.removeOnScrollListener(scrollListener)

    forceRecycleAllPostViews()
    replyLayoutView.onDestroy()
    recyclerView.removeItemDecoration(gridModeSpaceItemDecoration)
    recyclerView.swapAdapter(null, true)
    scrollbarView.cleanup()
    threadPresenter = null
  }

  fun onBack(): Boolean {
    return when {
      replyLayoutView.onBack() -> true
      else -> (threadListLayoutCallback?.threadBackPressed() ?: false)
    }
  }

  suspend fun showPosts(
    recyclerViewWidth: Int,
    descriptor: ChanDescriptor,
    filter: PostsFilter,
    initial: Boolean,
    additionalPostsToReparse: MutableSet<PostDescriptor>
  ): ShowPostsResult {
    val presenter = threadPresenter
    if (presenter == null) {
      Logger.d(TAG, "showPosts() threadPresenter==null")

      return ShowPostsResult(
        result = false,
        applyFilterDuration = Duration.ZERO,
        setThreadPostsDuration = Duration.ZERO
      )
    }

    onThemeChanged()

    if (initial) {
      replyLayoutView.bindChanDescriptor(descriptor, requireThreadControllerType())

      recyclerView.layoutManager = null
      recyclerView.layoutManager = layoutManager
      recyclerView.recycledViewPool.clear()
    }

    val posts = chanThreadManager.getMutableListOfPosts(descriptor)

    val (filteredPosts, applyFilterDuration) = measureTimedValue {
      filter.applyFilter(descriptor, posts, additionalPostsToReparse)
    }

    val chanDescriptor = currentChanDescriptorOrNull()
    chanLoadProgressNotifier.sendProgressEvent(ChanLoadProgressEvent.RefreshingPosts(descriptor))

    val setThreadPostsDuration = measureTime {
      val prevScrollPositionData = getPrevScrollPosition(chanDescriptor, initial)

      postAdapter.setThread(
        chanDescriptor = descriptor,
        chanTheme = themeEngine.chanTheme,
        postIndexedList = filteredPosts,
        postCellDataWidthNoPaddings = recyclerViewWidth,
        prevScrollPositionData = prevScrollPositionData,
      )
    }

    updatePostMarksJob?.cancel()
    updatePostMarksJob = coroutineScope.launch { scrollbarViewUpdatePostMarks(filteredPosts) }

    if (chanDescriptor != null) {
      // Use post() here to wait until recycler had processed the new posts so that we don't end up
      // with a race condition where restorePrevScrollPosition() can be called with still empty
      // recyclerview
      recyclerView.post { restorePrevScrollPosition(chanDescriptor, initial) }
    }

    chanLoadProgressNotifier.sendProgressEvent(ChanLoadProgressEvent.End(descriptor))

    if (descriptor.isCatalogDescriptor()) {
      postHighlightManager.onCatalogLoaded(postAdapter.threadCellData)
    }

    return ShowPostsResult(
      result = true,
      applyFilterDuration = applyFilterDuration,
      setThreadPostsDuration = setThreadPostsDuration
    )
  }

  private suspend fun scrollbarViewUpdatePostMarks(posts: List<PostIndexed>) {
    val chanDescriptor = currentChanDescriptorOrNull()
    if (chanDescriptor == null) {
      scrollbarView.hideScrollbarMarks()
      return
    }

    val postDescriptors = posts.map { postIndexed -> postIndexed.chanPost.postDescriptor }

    when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> {
        val params = ExtractPostMapInfoHolderUseCase.Params(
          postDescriptors = postDescriptors,
          isViewingThread = false
        )

        scrollbarView.updateScrollbarMarks(
          extractPostMapInfoHolderUseCase.execute(params)
        )
      }
      is ThreadDescriptor -> {
        val params = ExtractPostMapInfoHolderUseCase.Params(
          postDescriptors = postDescriptors,
          isViewingThread = true
        )

        scrollbarView.updateScrollbarMarks(
          extractPostMapInfoHolderUseCase.execute(params)
        )
      }
    }
  }

  fun setBoardPostViewMode(boardPostViewMode: BoardPostViewMode) {
    if (this.boardPostViewMode == boardPostViewMode) {
      return
    }

    this.boardPostViewMode = boardPostViewMode
    layoutManager = null

    when (boardPostViewMode) {
      BoardPostViewMode.LIST -> {
        val linearLayoutManager = object : FixedLinearLayoutManager(recyclerView) {
          override fun requestChildRectangleOnScreen(
            parent: RecyclerView,
            child: View,
            rect: Rect,
            immediate: Boolean,
            focusedChildVisible: Boolean
          ): Boolean {
            return false
          }
        }

        setRecyclerViewPadding()

        recyclerView.layoutManager = linearLayoutManager
        layoutManager = linearLayoutManager
      }
      BoardPostViewMode.GRID,
      BoardPostViewMode.STAGGER -> {
        val staggerLayoutManager = object : StaggeredGridLayoutManager(
          spanCount,
          StaggeredGridLayoutManager.VERTICAL,
        ) {
          override fun requestChildRectangleOnScreen(
            parent: RecyclerView,
            child: View,
            rect: Rect,
            immediate: Boolean,
            focusedChildVisible: Boolean
          ): Boolean {
            return false
          }
        }

        setRecyclerViewPadding()

        recyclerView.layoutManager = staggerLayoutManager
        layoutManager = staggerLayoutManager
      }
    }

    recyclerView.recycledViewPool.clear()
    postAdapter.setBoardPostViewMode(boardPostViewMode)

    // Trigger theme update because some colors depend on postViewMode
    onThemeChanged()
  }

  fun sendKeyEvent(event: KeyEvent): Boolean {
    when (event.keyCode) {
      KeyEvent.KEYCODE_VOLUME_UP,
      KeyEvent.KEYCODE_VOLUME_DOWN -> {
        if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
          // Both controllers are always focused when in SPLIT layout mode
          return false
        }

        if (!ChanSettings.volumeKeysScrolling.get()) {
          return false
        }

        val currentChanDescriptor = threadPresenter?.currentChanDescriptor
          ?: return false

        val canScroll = when (currentOpenedDescriptorStateManager.currentControllersFocusState) {
          CurrentFocusedControllers.FocusState.Catalog -> currentChanDescriptor is ChanDescriptor.ICatalogDescriptor
          CurrentFocusedControllers.FocusState.Thread -> currentChanDescriptor is ThreadDescriptor
          CurrentFocusedControllers.FocusState.None,
          CurrentFocusedControllers.FocusState.Both -> false
        }

        if (!canScroll) {
          return false
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
          val down = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
          val scroll = (height * 0.75).toInt()
          recyclerView.smoothScrollBy(0, if (down) scroll else -scroll)
        }

        return true
      }
      KeyEvent.KEYCODE_BACK -> {
        if (event.isLongPress) {
          threadListLayoutCallback?.threadBackLongPressed()
          return true
        }
      }
    }

    return false
  }

  fun lostFocus(wasFocused: ThreadControllerType) {
    snowLayout.lostFocus()
  }

  fun gainedFocus(
    nowFocused: ThreadControllerType,
    isContentVisible: Boolean
  ) {
    snowLayout.gainedFocus()

    if (isContentVisible) {
      showToolbarIfNeeded()
    }
  }

  fun onShown(nowFocused: ThreadControllerType, isContentVisible: Boolean) {
    if (nowFocused == ThreadControllerType.Thread && isContentVisible) {
      threadPresenter?.handleMarkedPost()
    }

    snowLayout.onShown()
  }

  fun onHidden(nowFocused: ThreadControllerType, isContentVisible: Boolean) {
    snowLayout.onHidden()
  }

  override fun showCaptcha(
    chanDescriptor: ChanDescriptor,
    replyMode: ReplyMode,
    autoReply: Boolean,
    afterPostingAttempt: Boolean,
    onFinished: ((Boolean) -> Unit)?
  ) {
    val controller = CaptchaContainerController(
      context = context,
      afterPostingAttempt = afterPostingAttempt,
      chanDescriptor = chanDescriptor
    ) { authenticationResult ->
      when (authenticationResult) {
        is CaptchaContainerController.AuthenticationResult.Failure -> {
          Logger.e(TAG, "CaptchaContainerController failure, ${authenticationResult.throwable}")

          dialogFactory.createSimpleInformationDialog(
            context = context,
            titleText = appResources.string(R.string.reply_captcha_failure),
            descriptionText = authenticationResult.throwable.errorMessageOrClassName(),
            onDismissListener = {
              if (!autoReply) {
                onFinished?.invoke(false)
              }
            }
          )
        }
        is CaptchaContainerController.AuthenticationResult.Success -> {
          Logger.d(TAG, "CaptchaContainerController success")

          if (autoReply) {
            replyLayoutView.enqueueReply(
              chanDescriptor = chanDescriptor,
              replyMode = replyMode,
              retrying = false
            )
          } else {
            onFinished?.invoke(true)
          }
        }
      }
    }

    replyLayoutView.hideKeyboard()

    coroutineScope.launch {
      // Wait a little bit for the keyboard to get hidden
      delay(100)

      threadListLayoutCallback?.presentController(controller)
    }
  }

  fun isReplyLayoutOpened(): Boolean {
    return replyLayoutView.isOpened()
  }

  fun openReplyLayout() {
    openOrCloseReplyLayout(open = true)
  }

  fun closeReplyLayout() {
    openOrCloseReplyLayout(open = false)
  }

  fun onPickLocalMediaButtonClicked() {
    replyLayoutView.onPickLocalMediaButtonClicked()
  }

  fun onPickRemoteMediaButtonClicked() {
    replyLayoutView.onPickRemoteMediaButtonClicked()
  }

  fun onSearchRemoteMediaButtonClicked(chanDescriptor: ChanDescriptor) {
    replyLayoutView.onSearchRemoteMediaButtonClicked(chanDescriptor)
  }

  fun showError(error: String?) {
    postAdapter.showError(error)
  }

  fun canChildScrollUp(): Boolean {
    currentChanDescriptorOrNull()
      ?: return true

    if (!replyLayoutView.isCollapsed()) {
      return true
    }

    val isDragging = scrollbarView.isDragging
    if (isDragging) {
      // Disable SwipeRefresh layout when dragging the fast scroller
      return true
    }

    if (threadListLayoutCallback?.kurobaToolbarState?.topToolbar?.kind?.isDefaultToolbar() != true) {
      // Disable SwipeRefresh layout when in search/reply/selection mode
      return true
    }

    return recyclerView.canScrollVertically(-1)
  }

  fun scrolledToBottom(): Boolean {
    return completeBottomAdapterPosition == postAdapter.itemCount - 1
  }

  fun smoothScrollNewPosts(displayPosition: Int) {
    if (layoutManager !is FixedLinearLayoutManager) {
      throw IllegalStateException("Layout manager is grid inside thread??")
    }

    (layoutManager as FixedLinearLayoutManager).scrollToPositionWithOffset(
      displayPosition,
      SCROLL_OFFSET
    )
  }

  fun cleanup() {
    postAdapter.cleanup()
    closeReplyLayout()

    prevLastPostNo = 0
  }

  fun getThumbnail(postImage: ChanPostImage?): ThumbnailView? {
    val layoutManager = recyclerView.layoutManager
      ?: return null

    for (i in 0 until layoutManager.childCount) {
      val view = layoutManager.getChildAt(i)

      if (view is GenericPostCell) {
        val post = view.getPost()
        if (post != null) {
          for (image in post.postImages) {
            if (image.equalUrl(postImage)) {
              return view.getThumbnailView(postImage!!)
            }
          }
        }
      }
    }

    return null
  }

  fun scrollTo(displayPosition: Int) {
    val scrollPosition = if (displayPosition < 0) {
      postAdapter.itemCount - 1
    } else {
      postAdapter.getScrollPosition(displayPosition)
    }

    recyclerView.post {
      scrollToInternal(scrollPosition)
      onRecyclerViewScrolled()
    }
  }

  fun highlightPost(postDescriptor: PostDescriptor?, blink: Boolean) {
    if (postDescriptor == null) {
      highlightPosts(null, blink)
    } else {
      highlightPosts(setOf(postDescriptor), blink)
    }
  }

  fun highlightPosts(postDescriptors: Set<PostDescriptor>?, blink: Boolean) {
    postHighlightManager.highlightPosts(postAdapter.threadCellData, postDescriptors, blink)
  }

  fun presentController(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    threadListLayoutCallback?.presentController(controller)
  }

  override fun pushController(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    threadListLayoutCallback?.pushController(controller)
  }

  fun toolbarHeight(): Int {
    val toolbarHeightDp = threadListLayoutCallback?.kurobaToolbarState?.toolbarHeight

    var toolbarHeight = with(appResources.composeDensity) { toolbarHeightDp?.toPx()?.toInt() }
    if (toolbarHeight == null) {
      toolbarHeight = appResources.dimension(com.github.k1rakishou.chan.R.dimen.toolbar_height).toInt()
    }

    return toolbarHeight
  }

  suspend fun onPostsWithDescriptorsUpdated(updatedPostDescriptors: Collection<PostDescriptor>) {
    BackgroundUtils.ensureMainThread()

    val updatedPosts = chanThreadManager.getPosts(updatedPostDescriptors)
    if (updatedPosts.isEmpty()) {
      return
    }

    postAdapter.updatePosts(updatedPosts)
  }

  suspend fun onPostsUpdated(updatedPosts: List<ChanPost>) {
    BackgroundUtils.ensureMainThread()
    postAdapter.updatePosts(updatedPosts)
  }

  fun isErrorShown(): Boolean {
    BackgroundUtils.ensureMainThread()
    return postAdapter.isErrorShown
  }

  fun resetCachedPostData(postDescriptor: PostDescriptor) {
    resetCachedPostData(listOf(postDescriptor))
  }

  fun resetCachedPostData(postDescriptors: Collection<PostDescriptor>) {
    postAdapter.resetCachedPostData(postDescriptors)
  }

  fun onImageOptionsApplied(fileUuid: UUID) {
    replyLayoutView.onImageOptionsApplied(fileUuid)
  }

  private fun setRecyclerViewPadding() {
    val defaultPadding = if (boardPostViewMode == BoardPostViewMode.GRID || boardPostViewMode == BoardPostViewMode.STAGGER) {
      dp(1f)
    } else {
      0
    }

    val recyclerRight = defaultPadding + scrollbarView.scrollbarWidthPx
    val recyclerTop = defaultPadding + toolbarHeight()
    var recyclerBottom = defaultPadding

    if (replyLayoutView.isOpened()) {
      val replyLayoutViewHeight = if (ChanSettings.isSplitLayoutMode()) {
        maxOf(
          globalUiStateHolder.replyLayout.state(ThreadControllerType.Catalog).height.value,
          globalUiStateHolder.replyLayout.state(ThreadControllerType.Thread).height.value,
        )
      } else {
        val threadControllerType = currentThreadControllerType
        if (threadControllerType == null) {
          return
        }

        globalUiStateHolder.replyLayout.state(threadControllerType).height.value
      }

      recyclerBottom += replyLayoutViewHeight
    } else {
      recyclerBottom += globalWindowInsetsManager.bottom()
    }

    recyclerView.setPadding(defaultPadding, recyclerTop, recyclerRight, recyclerBottom)
  }

  private fun scrollToInternal(scrollPosition: Int) {
    if (layoutManager is StaggeredGridLayoutManager) {
      (layoutManager as StaggeredGridLayoutManager).scrollToPositionWithOffset(scrollPosition, 0)
      return
    }

    if (layoutManager is FixedLinearLayoutManager) {
      (layoutManager as FixedLinearLayoutManager).scrollToPositionWithOffset(scrollPosition, 0)
      return
    }

    recyclerView.scrollToPosition(scrollPosition)
  }

  private fun attachToolbarScroll(attach: Boolean) {
    if (!ChanSettings.canCollapseToolbar()) {
      return
    }

    if (attach && replyLayoutView.isCollapsed()) {
      globalUiStateHolder.updateScrollState {
        attachToRecyclerView(recyclerView)
      }
    } else {
      globalUiStateHolder.updateScrollState {
        detachFromRecyclerView(recyclerView)
        resetScrollState()
      }
    }
  }

  fun showToolbarIfNeeded() {
    if (!ChanSettings.canCollapseToolbar()) {
      return
    }

    globalUiStateHolder.updateScrollState { resetScrollState() }
    globalUiStateHolder.updateToolbarState { updateToolbarVisibilityState(true) }
  }

  private fun openOrCloseReplyLayout(open: Boolean) {
    val chanDescriptor = currentChanDescriptorOrNull()

    if (chanDescriptor == null || replyLayoutView.isOpenedOrExpanded() == open) {
      return
    }

    Logger.d(TAG, "openOrCloseReplyLayout() open: ${open}")

    if (open) {
      replyLayoutView.updateReplyLayoutVisibility(ReplyLayoutVisibility.Opened)
    } else {
      replyLayoutView.updateReplyLayoutVisibility(ReplyLayoutVisibility.Collapsed)
    }

    if (!open) {
      AndroidUtils.hideKeyboard(replyLayoutView)
    }
  }

  private fun requireThreadControllerType(): ThreadControllerType {
    return requireNotNull(currentThreadControllerType) { "currentThreadControllerType is null!" }
  }

  private fun currentThreadDescriptorOrNull(): ThreadDescriptor? {
    return getCurrentChanDescriptor()?.threadDescriptorOrNull()
  }

  private fun currentChanDescriptorOrNull(): ChanDescriptor? {
    return getCurrentChanDescriptor()
  }

  private fun forceRecycleAllPostViews() {
    val adapter = recyclerView.adapter
    if (adapter is PostAdapter) {
      recyclerView.recycledViewPool.clear()
      adapter.cleanup()
    }
  }

  private fun onRecyclerViewScrolled() {
    recyclerView.post {
      // onScrolled can be called after cleanup()
      if (getCurrentChanDescriptor() == null) {
        return@post
      }

      val chanThreadLoadingState = threadPresenter?.chanThreadLoadingState
        ?: ThreadPresenter.ChanThreadLoadingState.Uninitialized

      if (chanThreadLoadingState != ThreadPresenter.ChanThreadLoadingState.Loaded) {
        // When reloading a thread, this callback will be called immediately which will result in
        //  "indexAndTop" being zeroes which will overwrite the old scroll position with incorrect
        //  values.
        return@post
      }

      val chanDescriptor = currentChanDescriptorOrNull()
        ?: return@post
      val indexTop = indexAndTop
        ?: return@post

      chanThreadViewableInfoManager.update(chanDescriptor) { chanThreadViewableInfo ->
        chanThreadViewableInfo.listViewIndex = indexTop.index
        chanThreadViewableInfo.listViewTop = indexTop.top
      }

      val currentLastPostNo = postAdapter.lastPostNo

      val lastVisibleItemPosition = completeBottomAdapterPosition
      if (lastVisibleItemPosition >= 0) {
        updateLastViewedPostNo(lastVisibleItemPosition)
      }

      if (lastVisibleItemPosition == postAdapter.itemCount - 1 && currentLastPostNo > prevLastPostNo) {
        prevLastPostNo = currentLastPostNo

        // As requested by the RecyclerView, make sure that the adapter isn't changed
        // while in a layout pass. Postpone to the next frame.
        listScrollToBottomExecutor.post { callback?.onListScrolledToBottom() }
      }

      if (lastVisibleItemPosition == postAdapter.itemCount - 1) {
        val isDragging = scrollbarView.isDragging
        if (!isDragging) {
          threadListLayoutCallback?.showToolbar()
        }
      }
    }
  }

  private fun updateLastViewedPostNo(last: Int) {
    if (last < 0) {
      return
    }

    val threadDescriptor = currentThreadDescriptorOrNull()
    if (threadDescriptor != null) {
      val postNo = postAdapter.getPostNo(last)
      if (postNo >= 0L) {
        lastViewedPostNoInfoHolder.setLastViewedPostNo(threadDescriptor, postNo)
      }
    }
  }

  private fun getPrevScrollPosition(chanDescriptor: ChanDescriptor?, initial: Boolean): PreviousThreadScrollPositionData? {
    if (chanDescriptor == null) {
      return null
    }

    if (initial) {
      val markedPostNo = chanThreadViewableInfoManager.getMarkedPostNo(chanDescriptor)
      if (markedPostNo != null) {
        return PreviousThreadScrollPositionData(
          prevVisibleItemIndex = null,
          prevVisiblePostNo = markedPostNo
        )
      }

      val prevVisibleItemIndex = chanThreadViewableInfoManager.view(chanDescriptor) { (_, index, _) -> index }
      if (prevVisibleItemIndex != null) {
        return PreviousThreadScrollPositionData(
          prevVisibleItemIndex = prevVisibleItemIndex,
          prevVisiblePostNo = null
        )
      }
    } else {
      val currentIndexAndTop = indexAndTop
      if (currentIndexAndTop != null) {
        return PreviousThreadScrollPositionData(
          prevVisibleItemIndex = currentIndexAndTop.index,
          prevVisiblePostNo = null
        )
      }
    }

    return null
  }

  private fun restorePrevScrollPosition(
    chanDescriptor: ChanDescriptor,
    initial: Boolean
  ) {
    if (!initial) {
      return
    }

    val markedPostNo = chanThreadViewableInfoManager.getMarkedPostNo(chanDescriptor)
    val markedPost = if (markedPostNo != null) {
      chanThreadManager.findPostByPostNo(chanDescriptor, markedPostNo)
    } else {
      null
    }

    if (markedPost != null) {
      Logger.e(TAG, "restorePrevScrollPosition($chanDescriptor) markedPost != null")
      return
    }

    val lm = layoutManager
    if (lm == null) {
      Logger.e(TAG, "restorePrevScrollPosition($chanDescriptor) layoutManager == null")
      return
    }

    chanThreadViewableInfoManager.view(chanDescriptor) { (_, index, top) ->
      when (boardPostViewMode) {
        BoardPostViewMode.LIST -> {
          (lm as FixedLinearLayoutManager).scrollToPositionWithOffset(index, top)
        }
        BoardPostViewMode.GRID,
        BoardPostViewMode.STAGGER -> {
          (lm as StaggeredGridLayoutManager).scrollToPositionWithOffset(index, top)
        }
        null -> {
          // no-op
        }
      }
    }
  }

  data class ShowPostsResult constructor(
    val result: Boolean,
    val applyFilterDuration: Duration,
    val setThreadPostsDuration: Duration
  )

  class GridModeSpaceItemDecoration : ItemDecoration() {
    override fun getItemOffsets(
      outRect: Rect,
      view: View,
      parent: RecyclerView,
      state: RecyclerView.State
    ) {
      if (view is GenericPostCell) {
        val margins = view.getMargins()

        outRect.left = margins
        outRect.right = margins
        outRect.top = margins
        outRect.bottom = margins
      }
    }
  }

  interface ReplyLayoutViewCallbacks {
    fun onCreate(threadControllerType: ThreadControllerType, callbacks: ReplyLayoutViewModel.ThreadListLayoutCallbacks)
    fun onDestroy()

    suspend fun bindChanDescriptor(descriptor: ChanDescriptor, threadControllerType: ThreadControllerType)

    fun quote(post: ChanPost, withText: Boolean)
    fun quote(postDescriptor: PostDescriptor, text: CharSequence)
    fun replyLayoutVisibility(): ReplyLayoutVisibility
    fun isExpanded(): Boolean
    fun isOpened(): Boolean
    fun isCollapsed(): Boolean
    fun isOpenedOrExpanded(): Boolean
    fun updateReplyLayoutVisibility(newReplyLayoutVisibility: ReplyLayoutVisibility)
    fun enqueueReply(chanDescriptor: ChanDescriptor, replyMode: ReplyMode, retrying: Boolean)
    fun onImageOptionsApplied(fileUuid: UUID)
    fun hideKeyboard()
    fun onBack(): Boolean
  }

  interface ThreadListLayoutPresenterCallback {
    suspend fun showThread(threadDescriptor: ThreadDescriptor)
    fun requestNewPostLoad()
    fun quickReloadFromMemoryCache(chanLoadOptions: ChanLoadOptions = ChanLoadOptions.retainAll())
    suspend fun onListScrolledToBottom()
  }

  interface ThreadListLayoutCallback {
    val kurobaToolbarState: KurobaToolbarState
    val chanDescriptor: ChanDescriptor?

    fun showToolbar()
    fun showImageReencodingWindow(fileUuid: UUID, supportsReencode: Boolean)
    fun threadBackPressed(): Boolean
    fun threadBackLongPressed()

    fun presentController(controller: Controller)
    fun pushController(controller: Controller)
    fun unpresentController(predicate: (Controller) -> Boolean)
  }

  companion object {
    private const val TAG = "ThreadListLayout"
    private val SCROLL_OFFSET = dp(128f)
  }
}