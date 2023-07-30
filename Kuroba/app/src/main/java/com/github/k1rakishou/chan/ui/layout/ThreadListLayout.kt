/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.ui.layout

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.BoardPostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressEvent
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.helper.LastViewedPostNoInfoHolder
import com.github.k1rakishou.chan.core.manager.BottomNavBarVisibilityStateManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.PostHighlightManager
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter
import com.github.k1rakishou.chan.core.usecase.ExtractPostMapInfoHolderUseCase
import com.github.k1rakishou.chan.features.reply.ReplyLayout
import com.github.k1rakishou.chan.features.reply.ReplyLayout.ThreadListLayoutCallbacks
import com.github.k1rakishou.chan.features.reply.ReplyLayoutFilesArea
import com.github.k1rakishou.chan.features.reply.ReplyPresenter
import com.github.k1rakishou.chan.ui.adapter.PostAdapter
import com.github.k1rakishou.chan.ui.adapter.PostAdapter.PostAdapterCallback
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.ui.cell.GenericPostCell
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.PreviousThreadScrollPositionData
import com.github.k1rakishou.chan.ui.cell.ThreadStatusCell
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.view.FastScroller
import com.github.k1rakishou.chan.ui.view.FastScrollerHelper
import com.github.k1rakishou.chan.ui.view.FixedLinearLayoutManager
import com.github.k1rakishou.chan.ui.view.NavigationViewContract
import com.github.k1rakishou.chan.ui.view.PostInfoMapItemDecoration
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.TimeUtils
import com.github.k1rakishou.chan.utils.ViewUtils.hackMaxFlingVelocity
import com.github.k1rakishou.chan.utils.setBackgroundColorFast
import com.github.k1rakishou.common.AndroidUtils
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runBlocking
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

/**
 * A layout that wraps around a [RecyclerView] and a [ReplyLayout] to manage showing and replying to posts.
 */
class ThreadListLayout(context: Context, attrs: AttributeSet?)
  : FrameLayout(context, attrs),
  ThreadListLayoutCallbacks,
  Toolbar.ToolbarHeightUpdatesCallback,
  CoroutineScope,
  ThemeEngine.ThemeChangesListener,
  FastScroller.ThumbDragListener,
  ReplyLayoutFilesArea.ThreadListLayoutCallbacks {

  @Inject
  lateinit var _themeEngine: Lazy<ThemeEngine>
  @Inject
  lateinit var _bottomNavBarVisibilityStateManager: Lazy<BottomNavBarVisibilityStateManager>
  @Inject
  lateinit var _extractPostMapInfoHolderUseCase: Lazy<ExtractPostMapInfoHolderUseCase>
  @Inject
  lateinit var _lastViewedPostNoInfoHolder: Lazy<LastViewedPostNoInfoHolder>
  @Inject
  lateinit var _chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>
  @Inject
  lateinit var _globalWindowInsetsManager: Lazy<GlobalWindowInsetsManager>
  @Inject
  lateinit var _chanThreadManager: Lazy<ChanThreadManager>
  @Inject
  lateinit var _chanCatalogSnapshotCache: Lazy<ChanCatalogSnapshotCache>
  @Inject
  lateinit var _chanLoadProgressNotifier: Lazy<ChanLoadProgressNotifier>
  @Inject
  lateinit var _postHighlightManager: Lazy<PostHighlightManager>

  private val themeEngine: ThemeEngine
    get() = _themeEngine.get()
  private val bottomNavBarVisibilityStateManager: BottomNavBarVisibilityStateManager
    get() = _bottomNavBarVisibilityStateManager.get()
  private val extractPostMapInfoHolderUseCase: ExtractPostMapInfoHolderUseCase
    get() = _extractPostMapInfoHolderUseCase.get()
  private val lastViewedPostNoInfoHolder: LastViewedPostNoInfoHolder
    get() = _lastViewedPostNoInfoHolder.get()
  private val chanThreadViewableInfoManager: ChanThreadViewableInfoManager
    get() = _chanThreadViewableInfoManager.get()
  private val globalWindowInsetsManager: GlobalWindowInsetsManager
    get() = _globalWindowInsetsManager.get()
  private val chanThreadManager: ChanThreadManager
    get() = _chanThreadManager.get()
  private val chanCatalogSnapshotCache: ChanCatalogSnapshotCache
    get() = _chanCatalogSnapshotCache.get()
  private val chanLoadProgressNotifier: ChanLoadProgressNotifier
    get() = _chanLoadProgressNotifier.get()
  private val postHighlightManager: PostHighlightManager
    get() = _postHighlightManager.get()

  private val chan4BirthdayDecoration = object : ItemDecoration() {
    private val paint by lazy {
      Paint(Paint.ANTI_ALIAS_FLAG)
        .also { paint -> paint.alpha = 160 }
    }

    private val hat by lazy {
      BitmapFactory.decodeResource(resources, R.drawable.partyhat)!!
    }

    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
      val chanDescriptor = currentChanDescriptorOrNull()
        ?: return

      var index = 0

      val postAlignmentMode = when (chanDescriptor) {
        is ChanDescriptor.ICatalogDescriptor -> ChanSettings.catalogPostAlignmentMode.get()
        is ThreadDescriptor -> ChanSettings.threadPostAlignmentMode.get()
      }

      while (index < parent.childCount) {
        val child = parent.getChildAt(index)
        if (child is GenericPostCell) {
          val post = child.getPost()

          if (post == null || !post.isOP() || post.postImages.isEmpty()) {
            index++
            continue
          }

          val params = child.layoutParams as RecyclerView.LayoutParams

          when (postAlignmentMode) {
            ChanSettings.PostAlignmentMode.AlignLeft -> {
              // Thumbnails on the right side

              val top = child.top + params.topMargin
              val right = child.right + params.rightMargin

              canvas.drawBitmap(
                hat,
                (right - hat.width).toFloat(),
                top - dp(80f) - parent.paddingTop + toolbarHeight().toFloat(),
                paint
              )
            }
            ChanSettings.PostAlignmentMode.AlignRight -> {
              // Thumbnails on the left side

              val top = child.top + params.topMargin
              val left = child.left + params.leftMargin

              canvas.drawBitmap(
                hat,
                left.toFloat(),
                top - dp(80f) - parent.paddingTop + toolbarHeight().toFloat(),
                paint
              )
            }
          }
        }

        index++
      }
    }
  }

  private val scrollListener: RecyclerView.OnScrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      if (newState == RecyclerView.SCROLL_STATE_IDLE) {
        onRecyclerViewScrolled()
      }
    }
  }

  val replyPresenter: ReplyPresenter
    get() = replyLayout.presenter

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

  private lateinit var replyLayout: ReplyLayout
  private lateinit var snowLayout: SnowLayout
  private lateinit var recyclerView: RecyclerView
  private lateinit var postAdapter: PostAdapter

  private val compositeDisposable = CompositeDisposable()
  private val job = SupervisorJob()
  private val updateRecyclerPaddingsDebouncer = Debouncer(false)

  private lateinit var listScrollToBottomExecutor: RendezvousCoroutineExecutor
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("ThreadListLayout")

  private var threadPresenter: ThreadPresenter? = null

  private var layoutManager: RecyclerView.LayoutManager? = null
  private var fastScroller: FastScroller? = null
  private var postInfoMapItemDecoration: PostInfoMapItemDecoration? = null
  private var callback: ThreadListLayoutPresenterCallback? = null
  private var navigationViewContractType: NavigationViewContract.Type = NavigationViewContract.Type.BottomNavView
  private var threadListLayoutCallback: ThreadListLayoutCallback? = null
  private var boardPostViewMode: BoardPostViewMode? = null
  private var spanCount = 2
  private var prevLastPostNo = 0L

  var replyOpen = false
    private set

  override fun isReplyLayoutOpened(): Boolean {
    return replyOpen
  }

  override fun getCurrentChanDescriptor(): ChanDescriptor? {
    return threadPresenter?.currentChanDescriptor
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
    replyLayout = findViewById(R.id.reply)
    recyclerView = findViewById(R.id.recycler_view)
    snowLayout = findViewById(R.id.snow_layout)
    recyclerView.hackMaxFlingVelocity()

    val params = replyLayout.layoutParams as LayoutParams
    params.gravity = Gravity.BOTTOM
    replyLayout.layoutParams = params

    onThemeChanged()
  }

  override fun onThemeChanged() {
    setBackgroundColorFast(themeEngine.chanTheme.backColor)
    replyLayout.setBackgroundColorFast(themeEngine.chanTheme.backColor)
  }

  fun onCreate(
    threadPresenter: ThreadPresenter,
    threadListLayoutCallback: ThreadListLayoutCallback,
    navigationViewContractType: NavigationViewContract.Type
  ) {
    this.callback = threadPresenter
    this.threadPresenter = threadPresenter
    this.threadListLayoutCallback = threadListLayoutCallback
    this.navigationViewContractType = navigationViewContractType

    listScrollToBottomExecutor = RendezvousCoroutineExecutor(this)
    serializedCoroutineExecutor = SerializedCoroutineExecutor(this)

    postAdapter = PostAdapter(
      recyclerView,
      threadPresenter as PostAdapterCallback,
      threadPresenter as PostCellCallback,
      threadPresenter as ThreadStatusCell.Callback
    )

    replyLayout.onCreate(this, this)

    val pool = recyclerView.recycledViewPool
    pool.setMaxRecycledViews(PostCellData.TYPE_POST_ZERO_OR_SINGLE_THUMBNAIL_LEFT_ALIGNMENT, 10)
    pool.setMaxRecycledViews(PostCellData.TYPE_POST_ZERO_OR_SINGLE_THUMBNAIL_RIGHT_ALIGNMENT, 10)
    pool.setMaxRecycledViews(PostCellData.TYPE_POST_MULTIPLE_THUMBNAILS, 10)
    pool.setMaxRecycledViews(PostCellData.TYPE_POST_STUB, 10)
    pool.setMaxRecycledViews(PostCellData.TYPE_POST_CARD, 10)

    recyclerView.adapter = postAdapter
    recyclerView.addOnScrollListener(scrollListener)
    recyclerView.addItemDecoration(gridModeSpaceItemDecoration)

    runBlocking { setFastScroll(false, emptyList()) }
    attachToolbarScroll(true)

    threadListLayoutCallback.toolbar?.addToolbarHeightUpdatesCallback(this)
  }

  fun onDestroy() {
    compositeDisposable.clear()
    job.cancelChildren()

    threadListLayoutCallback?.toolbar?.removeToolbarHeightUpdatesCallback(this)
    replyLayout.onDestroy()
    runBlocking { setFastScroll(false, emptyList()) }

    forceRecycleAllPostViews()
    recyclerView.removeItemDecoration(gridModeSpaceItemDecoration)
    recyclerView.swapAdapter(null, true)
    threadPresenter = null
  }

  override fun onToolbarHeightKnown(heightChanged: Boolean) {
    setRecyclerViewPadding()
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
        val isDragging = fastScroller?.isDragging ?: false
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

  @OptIn(ExperimentalTime::class)
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
      replyLayout.bindChanDescriptor(descriptor)

      recyclerView.layoutManager = null
      recyclerView.layoutManager = layoutManager
      recyclerView.recycledViewPool.clear()
      party()
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

    setFastScroll(true, filteredPosts)

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

  fun onBack(): Boolean {
    return when {
      replyLayout.onBack() -> true
      replyOpen -> {
        openReply(false)
        true
      }
      else -> threadListLayoutCallback!!.threadBackPressed()
    }
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

        val currentFocusedController = threadPresenter?.currentFocusedController()
          ?: ThreadPresenter.CurrentFocusedController.None

        val currentChanDescriptor = threadPresenter?.currentChanDescriptor
          ?: return false

        val canScroll = when (currentFocusedController) {
          ThreadPresenter.CurrentFocusedController.Catalog -> {
            currentChanDescriptor is ChanDescriptor.ICatalogDescriptor
          }
          ThreadPresenter.CurrentFocusedController.Thread -> {
            currentChanDescriptor is ThreadDescriptor
          }
          ThreadPresenter.CurrentFocusedController.None -> false
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

  fun lostFocus(wasFocused: ThreadSlideController.ThreadControllerType) {
    threadPresenter?.lostFocus(wasFocused)
    snowLayout.lostFocus()
  }

  fun gainedFocus(
    nowFocused: ThreadSlideController.ThreadControllerType,
    isThreadVisible: Boolean
  ) {
    threadPresenter?.gainedFocus(nowFocused)
    snowLayout.gainedFocus()

    if (isThreadVisible) {
      showToolbarIfNeeded()
    }
  }

  fun onShown(nowFocused: ThreadSlideController.ThreadControllerType, isThreadVisible: Boolean) {
    if (nowFocused == ThreadSlideController.ThreadControllerType.Thread && isThreadVisible) {
      threadPresenter?.handleMarkedPost()
    }

    snowLayout.onShown()
  }

  fun onHidden(nowFocused: ThreadSlideController.ThreadControllerType, isThreadVisible: Boolean) {
    snowLayout.onHidden()
  }

  fun showCaptcha(
    chanDescriptor: ChanDescriptor,
    replyMode: ReplyMode,
    autoReply: Boolean,
    afterPostingAttempt: Boolean,
    onFinished: ((Boolean) -> Unit)? = null
  ) {
    replyLayout.showCaptcha(chanDescriptor, replyMode, autoReply, afterPostingAttempt, onFinished)
  }

  override fun currentFocusedController(): ThreadPresenter.CurrentFocusedController {
    return threadPresenter?.currentFocusedController()
      ?: ThreadPresenter.CurrentFocusedController.None
  }

  override fun openReply(open: Boolean) {
    if (currentChanDescriptorOrNull() == null || replyOpen == open) {
      return
    }

    Logger.d(TAG, "openReply() open: ${open}")

    val chanDescriptor = currentChanDescriptorOrNull()
    replyOpen = open

    measureReplyLayout()

    fun notifyBottomNavBarVisibilityStateManager() {
      if (chanDescriptor != null) {
        bottomNavBarVisibilityStateManager.replyViewStateChanged(
          chanDescriptor.isCatalogDescriptor(),
          open
        )
      }
    }

    val height = replyLayout.measuredHeight
    val viewPropertyAnimator = replyLayout.animate()

    viewPropertyAnimator.setListener(null)
    viewPropertyAnimator.interpolator = FastOutSlowInInterpolator()
    viewPropertyAnimator.duration = 350

    if (open) {
      replyLayout.visibility = VISIBLE
      replyLayout.translationY = height.toFloat()

      threadListLayoutCallback?.showReplyButton(false)

      viewPropertyAnimator.translationY(0f)
      viewPropertyAnimator.setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationStart(animation: Animator) {
          notifyBottomNavBarVisibilityStateManager()
        }

        override fun onAnimationEnd(animation: Animator) {
          viewPropertyAnimator.setListener(null)
        }
      })
    } else {
      replyLayout.translationY = 0f

      viewPropertyAnimator.translationY(height.toFloat())
      viewPropertyAnimator.setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationStart(animation: Animator) {
          notifyBottomNavBarVisibilityStateManager()
        }

        override fun onAnimationEnd(animation: Animator) {
          viewPropertyAnimator.setListener(null)
          replyLayout.visibility = GONE

          threadListLayoutCallback?.showReplyButton(true)
        }
      })
    }

    replyLayout.onOpen(open)
    replyLayout.requestWrappingModeUpdate()

    if (!open) {
      AndroidUtils.hideKeyboard(replyLayout)
    }

    attachToolbarScroll(!open)
  }

  fun showError(error: String?) {
    postAdapter.showError(error)
  }

  fun canChildScrollUp(): Boolean {
    if (replyLayout.presenter.isExpanded) {
      return true
    }

    val isDragging = fastScroller?.isDragging ?: false
    if (isDragging) {
      // Disable SwipeRefresh layout when dragging the fast scroller
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
      // position + 1 for last seen view
      displayPosition + 1,
      SCROLL_OFFSET
    )
  }

  fun cleanup() {
    postAdapter.cleanup()
    replyLayout.cleanup()

    openReply(false)

    prevLastPostNo = 0
    noParty()
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

  fun highlightPost(postDescriptor: PostDescriptor?, blink: Boolean) {
    if (postDescriptor == null) {
      highlightPosts(null, blink)
    } else {
      highlightPosts(setOf(postDescriptor), blink)
    }
  }

  override fun highlightPosts(postDescriptors: Set<PostDescriptor>?, blink: Boolean) {
    postHighlightManager.highlightPosts(postAdapter.threadCellData, postDescriptors, blink)
  }

  override fun showThread(threadDescriptor: ThreadDescriptor) {
    serializedCoroutineExecutor.post {
      callback?.showThread(threadDescriptor)
    }
  }

  override fun requestNewPostLoad() {
    callback?.requestNewPostLoad()
  }

  override fun showImageReencodingWindow(fileUuid: UUID, supportsReencode: Boolean) {
    threadListLayoutCallback?.showImageReencodingWindow(fileUuid, supportsReencode)
  }

  private fun canToolbarCollapse(): Boolean {
    return (ChanSettings.getCurrentLayoutMode() != ChanSettings.LayoutMode.SPLIT
      && !ChanSettings.neverHideToolbar.get())
  }

  private fun attachToolbarScroll(attach: Boolean) {
    if (!canToolbarCollapse()) {
      return
    }

    val toolbar = threadListLayoutCallback?.toolbar
      ?: return

    if (attach && !replyOpen) {
      toolbar.attachRecyclerViewScrollStateListener(recyclerView)
    } else {
      toolbar.detachRecyclerViewScrollStateListener(recyclerView)
      toolbar.collapseShow(true)
    }
  }

  private fun showToolbarIfNeeded() {
    if (!canToolbarCollapse()) {
      return
    }

    // Of coming back to focus from a dual controller, like the threadlistcontroller,
    // check if we should show the toolbar again (after the other controller made it hide).
    // It should show if the search or reply is open, or if the thread was scrolled at the
    // top showing an empty space.
    val toolbar = threadListLayoutCallback?.toolbar
      ?: return

    if (replyOpen) {
      // force toolbar to show
      toolbar.collapseShow(true)
    } else {
      // check if it should show if it was scrolled at the top
      toolbar.checkToolbarCollapseState(recyclerView, true)
    }
  }

  private suspend fun setFastScroll(enable: Boolean, posts: List<PostIndexed>) {
    val enabledInSettings = ChanSettings.draggableScrollbars.get().isEnabled

    if (!enable || !enabledInSettings) {
      if (fastScroller != null) {
        recyclerView.removeItemDecoration(fastScroller!!)

        fastScroller?.destroyCallbacks()
        fastScroller?.onCleanup()
        fastScroller = null
      }

      postInfoMapItemDecoration = null
      recyclerView.isVerticalScrollBarEnabled = true

      return
    }

    val chanDescriptor = currentChanDescriptorOrNull()
    if (chanDescriptor == null) {
      return
    }

    val postDescriptors = posts.map { postIndexed -> postIndexed.chanPost.postDescriptor }

    when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> {
        if (postInfoMapItemDecoration == null) {
          postInfoMapItemDecoration = PostInfoMapItemDecoration(context)
        }

        val params = ExtractPostMapInfoHolderUseCase.Params(
          postDescriptors = postDescriptors,
          isViewingThread = false
        )

        postInfoMapItemDecoration!!.setItems(
          extractPostMapInfoHolderUseCase.execute(params),
          postDescriptors.size
        )
      }
      is ThreadDescriptor -> {
        if (postInfoMapItemDecoration == null) {
          postInfoMapItemDecoration = PostInfoMapItemDecoration(context)
        }

        val params = ExtractPostMapInfoHolderUseCase.Params(
          postDescriptors = postDescriptors,
          isViewingThread = true
        )

        postInfoMapItemDecoration!!.setItems(
          extractPostMapInfoHolderUseCase.execute(params),
          postDescriptors.size
        )
      }
    }

    if (fastScroller == null) {
      val fastScrollerType = when (chanDescriptor) {
        is ThreadDescriptor -> FastScroller.FastScrollerControllerType.Thread
        is ChanDescriptor.ICatalogDescriptor -> FastScroller.FastScrollerControllerType.Catalog
      }

      val scroller = FastScrollerHelper.create(
        fastScrollerType,
        recyclerView,
        postInfoMapItemDecoration
      )

      scroller.setThumbDragListener(this)
      fastScroller = scroller

      recyclerView.isVerticalScrollBarEnabled = false
    }
  }

  override fun onDragStarted() {
    if (!canToolbarCollapse() || replyOpen) {
      return
    }

    val toolbar = threadListLayoutCallback?.toolbar
      ?: return

    toolbar.detachRecyclerViewScrollStateListener(recyclerView)
    toolbar.collapseHide(true)
  }

  override fun onDragEnded() {
    // Fast scroller does not trigger RecyclerView's onScrollStateChanged() so we need to call it
    //  manually after we are down scrolling via Fast scroller.
    onRecyclerViewScrolled()

    if (!canToolbarCollapse() || replyOpen) {
      return
    }

    val toolbar = threadListLayoutCallback?.toolbar
      ?: return

    toolbar.attachRecyclerViewScrollStateListener(recyclerView)
    toolbar.collapseShow(true)
  }

  override fun updateRecyclerViewPaddings() {
    updateRecyclerPaddingsDebouncer.post({ setRecyclerViewPadding() }, 50L)
  }

  override fun measureReplyLayout() {
    replyLayout.measure(
      MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    )
  }

  override fun presentController(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    threadListLayoutCallback?.presentController(controller)
  }

  override fun pushController(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    threadListLayoutCallback?.pushController(controller)
  }

  override fun showLoadingView(cancellationFunc: () -> Unit, titleTextId: Int) {
    BackgroundUtils.ensureMainThread()

    val loadingViewController = LoadingViewController(
      context,
      true,
      context.getString(titleTextId)
    ).apply { enableCancellation(cancellationFunc) }

    threadListLayoutCallback?.presentController(loadingViewController)
  }

  override fun hideLoadingView() {
    BackgroundUtils.ensureMainThread()

    threadListLayoutCallback?.unpresentController { controller -> controller is LoadingViewController }
  }

  private fun setRecyclerViewPadding() {
    val defaultPadding = if (boardPostViewMode == BoardPostViewMode.GRID || boardPostViewMode == BoardPostViewMode.STAGGER) {
      dp(1f)
    } else {
      0
    }

    val recyclerRight = if (ChanSettings.draggableScrollbars.get().isEnabled) {
      defaultPadding + FastScrollerHelper.FAST_SCROLLER_WIDTH
    } else {
      defaultPadding
    }

    val recyclerTop = defaultPadding + toolbarHeight()
    var recyclerBottom = defaultPadding

    // measurements
    if (replyOpen) {
      measureReplyLayout()

      recyclerBottom += (replyLayout.measuredHeight - replyLayout.paddingTop)
    } else {
      recyclerBottom += when (navigationViewContractType) {
        NavigationViewContract.Type.BottomNavView -> {
          if (ChanSettings.isNavigationViewEnabled()) {
            globalWindowInsetsManager.bottom() + getDimen(R.dimen.navigation_view_size)
          } else {
            globalWindowInsetsManager.bottom()
          }
        }
        NavigationViewContract.Type.SideNavView -> {
          globalWindowInsetsManager.bottom()
        }
      }
    }

    recyclerView.setPadding(defaultPadding, recyclerTop, recyclerRight, recyclerBottom)
  }

  fun toolbarHeight(): Int {
    return threadListLayoutCallback?.toolbar?.toolbarHeight ?: 0
  }

  private fun party() {
    val chanDescriptor = getCurrentChanDescriptor()
      ?: return

    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return
    }

    if (chanDescriptor.siteDescriptor().is4chan() && TimeUtils.is4chanBirthdayToday()) {
      recyclerView.addItemDecoration(chan4BirthdayDecoration)
    }
  }

  private fun noParty() {
    recyclerView.removeItemDecoration(chan4BirthdayDecoration)
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

  fun onImageOptionsComplete() {
    replyLayout.onImageOptionsComplete()
  }

  data class ShowPostsResult @OptIn(ExperimentalTime::class) constructor(
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

  interface ThreadListLayoutPresenterCallback {
    suspend fun showThread(threadDescriptor: ThreadDescriptor)
    fun requestNewPostLoad()
    fun quickReloadFromMemoryCache(chanLoadOptions: ChanLoadOptions = ChanLoadOptions.retainAll())
    suspend fun onListScrolledToBottom()
  }

  interface ThreadListLayoutCallback {
    val toolbar: Toolbar?
    val chanDescriptor: ChanDescriptor?

    fun showToolbar()
    fun showReplyButton(show: Boolean)
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