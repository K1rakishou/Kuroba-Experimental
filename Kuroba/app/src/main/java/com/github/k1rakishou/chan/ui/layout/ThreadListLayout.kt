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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
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
import com.github.k1rakishou.chan.core.helper.LastViewedPostNoInfoHolder
import com.github.k1rakishou.chan.core.manager.BottomNavBarVisibilityStateManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
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
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.PostCellWidthStorage
import com.github.k1rakishou.chan.ui.cell.PostStubCell
import com.github.k1rakishou.chan.ui.cell.ThreadStatusCell
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.view.FastScroller
import com.github.k1rakishou.chan.ui.view.FastScrollerHelper
import com.github.k1rakishou.chan.ui.view.FixedLinearLayoutManager
import com.github.k1rakishou.chan.ui.view.PostInfoMapItemDecoration
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.setBackgroundColorFast
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

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
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postFilterManager: PostFilterManager
  @Inject
  lateinit var bottomNavBarVisibilityStateManager: BottomNavBarVisibilityStateManager
  @Inject
  lateinit var extractPostMapInfoHolderUseCase: ExtractPostMapInfoHolderUseCase
  @Inject
  lateinit var lastViewedPostNoInfoHolder: LastViewedPostNoInfoHolder
  @Inject
  lateinit var chanThreadViewableInfoManager: ChanThreadViewableInfoManager
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var chanThreadManager: ChanThreadManager

  private val PARTY: ItemDecoration = object : ItemDecoration() {
    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
      if (hat == null) {
        hat = BitmapFactory.decodeResource(resources, R.drawable.partyhat)
      }

      var i = 0
      val j = parent.childCount

      while (i < j) {
        val child = parent.getChildAt(i)
        if (child is GenericPostCell) {
          val post = child.getPost()

          if (post == null || !post.isOP() || post.postImages.isEmpty()) {
            i++
            continue
          }

          val params = child.layoutParams as RecyclerView.LayoutParams
          val top = child.top + params.topMargin
          val left = child.left + params.leftMargin

          c.drawBitmap(
            hat!!,
            left - parent.paddingLeft - dp(25f).toFloat(),
            top - dp(80f) - parent.paddingTop + toolbarHeight().toFloat(),
            null
          )
        }

        i++
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

  val indexAndTop: IntArray?
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

      return intArrayOf(index, top)
    }

  val currentSpanCount: Int
    get() {
      if (layoutManager == null) {
        return 1
      }

      return when (boardPostViewMode) {
        BoardPostViewMode.LIST -> 1
        BoardPostViewMode.GRID -> (layoutManager as GridLayoutManager).spanCount
        BoardPostViewMode.STAGGER -> (layoutManager as StaggeredGridLayoutManager).spanCount
        null -> 1
      }
    }

  private val topAdapterPosition: Int
    get() {
      if (layoutManager == null) {
        return -1
      }

      when (boardPostViewMode) {
        BoardPostViewMode.LIST -> return (layoutManager as FixedLinearLayoutManager).findFirstVisibleItemPosition()
        BoardPostViewMode.GRID -> return (layoutManager as GridLayoutManager).findFirstVisibleItemPosition()
        BoardPostViewMode.STAGGER -> {
          val positions = (layoutManager as StaggeredGridLayoutManager).findFirstVisibleItemPositions(null)
          if (positions.isEmpty()) {
            return -1
          }

          val hasViewTouchingTop = positions.any { position -> position == 0 }
          if (!hasViewTouchingTop) {
            return -1
          }

          return 0
        }
      }

      return -1
    }

  private val completeBottomAdapterPosition: Int
    get() {
      if (layoutManager == null) {
        return -1
      }

      when (boardPostViewMode) {
        BoardPostViewMode.LIST -> return (layoutManager as FixedLinearLayoutManager).findLastCompletelyVisibleItemPosition()
        BoardPostViewMode.GRID -> return (layoutManager as GridLayoutManager).findLastCompletelyVisibleItemPosition()
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
      }
      return -1
    }

  private val gridModeSpaceItemDecoration = GridModeSpaceItemDecoration()

  private lateinit var replyLayout: ReplyLayout
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
  private var threadListLayoutCallback: ThreadListLayoutCallback? = null
  private var boardPostViewMode: BoardPostViewMode? = null
  private var spanCount = 2
  private var prevLastPostNo = 0L
  private var hat: Bitmap? = null

  var replyOpen = false
    private set

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

    val params = replyLayout.layoutParams as LayoutParams
    params.gravity = Gravity.BOTTOM
    replyLayout.layoutParams = params

    onThemeChanged()
  }

  override fun onThemeChanged() {
    val backColor = kotlin.run {
      if (boardPostViewMode != null && boardPostViewMode != BoardPostViewMode.LIST) {
        if (themeEngine.chanTheme.backColor == 0) {
          return@run themeEngine.chanTheme.backColor
        }

        val factor = if (themeEngine.chanTheme.isBackColorDark) {
          1.2f
        } else {
          0.9f
        }

        return@run ThemeEngine.manipulateColor(themeEngine.chanTheme.backColor, factor)
      }

      return@run themeEngine.chanTheme.backColor
    }

    setBackgroundColorFast(backColor)
    replyLayout.setBackgroundColorFast(themeEngine.chanTheme.backColor)
  }

  fun onCreate(
    threadPresenter: ThreadPresenter,
    threadListLayoutCallback: ThreadListLayoutCallback
  ) {
    this.callback = threadPresenter
    this.threadPresenter = threadPresenter
    this.threadListLayoutCallback = threadListLayoutCallback

    listScrollToBottomExecutor = RendezvousCoroutineExecutor(this)
    serializedCoroutineExecutor = SerializedCoroutineExecutor(this)

    postAdapter = PostAdapter(
      recyclerView,
      threadPresenter as PostAdapterCallback,
      threadPresenter as PostCellCallback,
      threadPresenter as ThreadStatusCell.Callback
    )

    replyLayout.onCreate(this, this)

    recyclerView.adapter = postAdapter
    // Man, fuck the RecycledViewPool. Sometimes when scrolling away from a view and the swiftly
    // back to it onViewRecycled() will be called TWICE for that view. Setting setMaxRecycledViews
    // for TYPE_POST to 0 solves this problem. What a buggy piece of shit.
    recyclerView.recycledViewPool.setMaxRecycledViews(PostAdapter.TYPE_POST, 0)
    recyclerView.addOnScrollListener(scrollListener)

    recyclerView.addItemDecoration(gridModeSpaceItemDecoration)

    setFastScroll(false)
    attachToolbarScroll(true)

    threadListLayoutCallback.toolbar?.addToolbarHeightUpdatesCallback(this)
  }

  fun onDestroy() {
    compositeDisposable.clear()
    job.cancelChildren()

    threadListLayoutCallback?.toolbar?.removeToolbarHeightUpdatesCallback(this)
    replyLayout.onDestroy()
    setFastScroll(false)

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
        chanThreadViewableInfo.listViewIndex = indexTop[0]
        chanThreadViewableInfo.listViewTop = indexTop[1]
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

    if (boardPostViewMode == BoardPostViewMode.GRID) {
      (layoutManager as GridLayoutManager).spanCount = spanCount
    } else if (boardPostViewMode == BoardPostViewMode.STAGGER) {
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

        setBackgroundColor(themeEngine.chanTheme.backColor)
      }
      BoardPostViewMode.GRID -> {
        val gridLayoutManager = object : GridLayoutManager(
          context,
          spanCount,
          GridLayoutManager.VERTICAL,
          false
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

        recyclerView.layoutManager = gridLayoutManager
        layoutManager = gridLayoutManager

        setBackgroundColor(themeEngine.chanTheme.backColorSecondary())
      }
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

        setBackgroundColor(themeEngine.chanTheme.backColor)
      }
    }

    recyclerView.recycledViewPool.clear()
    postAdapter.setBoardPostViewMode(boardPostViewMode)

    // Trigger theme update because some colors depend on postViewMode
    onThemeChanged()
  }

  suspend fun showPosts(
    descriptor: ChanDescriptor,
    filter: PostsFilter,
    initial: Boolean
  ): Boolean {
    val presenter = threadPresenter
    if (presenter == null) {
      Logger.d(TAG, "showPosts() threadPresenter==null")
      return false
    }

    if (initial) {
      replyLayout.bindLoadable(descriptor)

      recyclerView.layoutManager = null
      recyclerView.layoutManager = layoutManager
      recyclerView.recycledViewPool.clear()
      party()

      // Reset PostCellWidthStorage every time we fully reload a thread.
      PostCellWidthStorage.reset()
    }

    setFastScroll(true)
    val posts = chanThreadManager.getMutableListOfPosts(descriptor)

    postAdapter.setCompact(boardPostViewMode != BoardPostViewMode.LIST)
    postAdapter.setThread(
      descriptor,
      themeEngine.chanTheme,
      filter.applyFilter(descriptor, posts)
    )

    val chanDescriptor = currentChanDescriptorOrNull()
    if (chanDescriptor != null) {
      restorePrevScrollPosition(chanDescriptor, initial)
    }

    return true
  }

  private fun restorePrevScrollPosition(
    chanDescriptor: ChanDescriptor,
    initial: Boolean
  ) {
    val markedPostNo = chanThreadViewableInfoManager.getMarkedPostNo(chanDescriptor)
    val markedPost = if (markedPostNo != null) {
      chanThreadManager.findPostByPostNo(chanDescriptor, markedPostNo)
    } else {
      null
    }

    if (markedPost == null && initial) {
      chanThreadViewableInfoManager.view(chanDescriptor) { (_, index, top) ->
        when (boardPostViewMode) {
          BoardPostViewMode.LIST -> {
            (layoutManager as FixedLinearLayoutManager).scrollToPositionWithOffset(
              index,
              top
            )
          }
          BoardPostViewMode.GRID -> {
            (layoutManager as GridLayoutManager).scrollToPositionWithOffset(
              index,
              top
            )
          }
          BoardPostViewMode.STAGGER -> {
            (layoutManager as StaggeredGridLayoutManager).scrollToPositionWithOffset(
              index,
              top
            )
          }
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
            currentChanDescriptor is ChanDescriptor.CatalogDescriptor
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
  }

  fun gainedFocus(
    nowFocused: ThreadSlideController.ThreadControllerType,
    isThreadVisible: Boolean
  ) {
    threadPresenter?.gainedFocus(nowFocused)

    if (isThreadVisible) {
      showToolbarIfNeeded()
    }
  }

  override fun currentFocusedController(): ThreadPresenter.CurrentFocusedController {
    return threadPresenter?.currentFocusedController()
      ?: ThreadPresenter.CurrentFocusedController.None
  }

  override fun openReply(open: Boolean) {
    if (currentChanDescriptorOrNull() == null || replyOpen == open) {
      return
    }

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
    viewPropertyAnimator.interpolator = DecelerateInterpolator(2f)
    viewPropertyAnimator.duration = 350

    if (open) {
      replyLayout.visibility = VISIBLE
      replyLayout.translationY = height.toFloat()

      viewPropertyAnimator.translationY(0f)
      viewPropertyAnimator.setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationStart(animation: Animator?) {
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
        override fun onAnimationStart(animation: Animator?) {
          notifyBottomNavBarVisibilityStateManager()
        }

        override fun onAnimationEnd(animation: Animator) {
          viewPropertyAnimator.setListener(null)
          replyLayout.visibility = GONE
        }
      })
    }

    replyLayout.onOpen(open)
    setRecyclerViewPadding()

    if (!open) {
      AndroidUtils.hideKeyboard(replyLayout)
    }

    threadListLayoutCallback?.replyLayoutOpen(open)
    attachToolbarScroll(!open)
  }

  fun showError(error: String?) {
    postAdapter.showError(error)
  }

  fun canChildScrollUp(): Boolean {
    if (replyOpen) {
      return true
    }

    if (topAdapterPosition != 0) {
      return true
    }

    val isDragging = fastScroller?.isDragging ?: false
    if (isDragging) {
      // Disable SwipeRefresh layout when dragging the fast scroller
      return true
    }

    val genericPostCellView = layoutManager?.findViewByPosition(0) as? GenericPostCell
      ?: return true
    val genericPostCellChildView = genericPostCellView.getChildPostCellView()
      ?: return true

    when (boardPostViewMode) {
      BoardPostViewMode.LIST -> {
        return genericPostCellView.top != toolbarHeight()
      }
      BoardPostViewMode.STAGGER,
      BoardPostViewMode.GRID -> {
        if (genericPostCellChildView is PostStubCell) {
          // PostStubCell does not have grid_card_margin
          return genericPostCellView.top != toolbarHeight() + dp(1f)
        } else {
          return genericPostCellView.top != getDimen(R.dimen.grid_card_margin) + dp(1f) + toolbarHeight()
        }
      }
    }
    
    return true
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
    if (layoutManager is GridLayoutManager) {
      (layoutManager as GridLayoutManager).scrollToPositionWithOffset(
        scrollPosition,
        SCROLL_OFFSET
      )

      return
    }

    if (layoutManager is StaggeredGridLayoutManager) {
      (layoutManager as StaggeredGridLayoutManager).scrollToPositionWithOffset(
        scrollPosition,
        SCROLL_OFFSET
      )

      return
    }

    if (layoutManager is FixedLinearLayoutManager) {
      (layoutManager as FixedLinearLayoutManager).scrollToPositionWithOffset(
        scrollPosition,
        SCROLL_OFFSET
      )

      return
    }

    recyclerView.scrollToPosition(scrollPosition)
  }

  fun highlightPostId(id: String) {
    postAdapter.highlightPostId(id)
  }

  fun highlightPostTripcode(tripcode: CharSequence?) {
    postAdapter.highlightPostTripcode(tripcode)
  }

  fun selectPost(postDescriptor: PostDescriptor?) {
    if (postDescriptor == null) {
      postAdapter.selectPosts(emptySet())
    } else {
      postAdapter.selectPosts(setOf(postDescriptor))
    }
  }

  fun highlightPost(postDescriptor: PostDescriptor) {
    highlightPosts(setOf(postDescriptor))
  }

  override fun highlightPosts(postDescriptors: Set<PostDescriptor>) {
    postAdapter.highlightPosts(postDescriptors)
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

  private fun setFastScroll(enable: Boolean) {
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

    if (chanDescriptor is ThreadDescriptor) {
      val chanThread = chanThreadManager.getChanThread(chanDescriptor)
      if (chanThread != null) {
        if (postInfoMapItemDecoration == null) {
          postInfoMapItemDecoration = PostInfoMapItemDecoration(
            context,
            ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT
          )
        }

        postInfoMapItemDecoration!!.setItems(
          extractPostMapInfoHolderUseCase.execute(chanThread.getPostDescriptors()),
          chanThread.postsCount
        )
      }
    }

    if (fastScroller == null) {
      val fastScrollerType = when (chanDescriptor) {
        is ThreadDescriptor -> FastScroller.FastScrollerControllerType.Thread
        is ChanDescriptor.CatalogDescriptor -> FastScroller.FastScrollerControllerType.Catalog
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
    updateRecyclerPaddingsDebouncer.post({ setRecyclerViewPadding() }, 250L)
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

  override fun showLoadingView(cancellationFunc: () -> Unit, titleTextId: Int) {
    BackgroundUtils.ensureMainThread()

    val loadingViewController = LoadingViewController(
      context,
      true,
      context.getString(titleTextId)
    ).apply { enableBack(cancellationFunc) }

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
    val keyboardOpened = globalWindowInsetsManager.isKeyboardOpened

    // measurements
    if (replyOpen) {
      measureReplyLayout()

      val bottomPadding = if (keyboardOpened) {
        replyLayout.paddingBottom
      } else {
        0
      }

      recyclerBottom += (replyLayout.measuredHeight - replyLayout.paddingTop - bottomPadding)
    } else {
      recyclerBottom += if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
        globalWindowInsetsManager.bottom()
      } else {
        globalWindowInsetsManager.bottom() + getDimen(R.dimen.bottom_nav_view_height)
      }
    }

    recyclerView.setPadding(
      defaultPadding,
      recyclerTop,
      recyclerRight,
      recyclerBottom
    )
  }

  fun toolbarHeight(): Int {
    return threadListLayoutCallback?.toolbar?.toolbarHeight ?: 0
  }

  private fun party() {
    val chanDescriptor = getCurrentChanDescriptor()
      ?: return

    if (chanDescriptor.siteDescriptor().is4chan()) {
      val calendar = Calendar.getInstance()
      if (calendar[Calendar.MONTH] == Calendar.OCTOBER && calendar[Calendar.DAY_OF_MONTH] == 1) {
        recyclerView.addItemDecoration(PARTY)
      }
    }
  }

  private fun noParty() {
    recyclerView.removeItemDecoration(PARTY)
  }

  suspend fun onPostUpdated(updatedPost: ChanPost) {
    BackgroundUtils.ensureMainThread()
    postAdapter.updatePost(updatedPost)
  }

  fun isErrorShown(): Boolean {
    BackgroundUtils.ensureMainThread()
    return postAdapter.isErrorShown
  }

  fun onImageOptionsComplete() {
    replyLayout.onImageOptionsComplete()
  }

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

    fun quickReload(
      showLoading: Boolean = false,
      chanCacheUpdateOptions: ChanCacheUpdateOptions = ChanCacheUpdateOptions.UpdateCache
    )

    suspend fun onListScrolledToBottom()
  }

  interface ThreadListLayoutCallback {
    val toolbar: Toolbar?
    val chanDescriptor: ChanDescriptor?

    fun showToolbar()
    fun replyLayoutOpen(open: Boolean)
    fun showImageReencodingWindow(fileUuid: UUID, supportsReencode: Boolean)
    fun threadBackPressed(): Boolean
    fun threadBackLongPressed()
    fun presentController(controller: Controller)
    fun unpresentController(predicate: (Controller) -> Boolean)
  }

  companion object {
    private const val TAG = "ThreadListLayout"
    private val SCROLL_OFFSET = dp(128f)
  }
}