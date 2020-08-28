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
package com.github.adamantcheese.chan.ui.layout

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
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
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.base.RendezvousCoroutineExecutor
import com.github.adamantcheese.chan.core.manager.*
import com.github.adamantcheese.chan.core.model.ChanThread
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.presenter.ReplyPresenter
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode
import com.github.adamantcheese.chan.core.site.http.Reply
import com.github.adamantcheese.chan.core.usecase.ExtractPostMapInfoHolderUseCase
import com.github.adamantcheese.chan.ui.adapter.PostAdapter
import com.github.adamantcheese.chan.ui.adapter.PostAdapter.PostAdapterCallback
import com.github.adamantcheese.chan.ui.adapter.PostsFilter
import com.github.adamantcheese.chan.ui.cell.PostCellInterface
import com.github.adamantcheese.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.adamantcheese.chan.ui.cell.PostStubCell
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell
import com.github.adamantcheese.chan.ui.layout.ReplyLayout.ReplyLayoutCallback
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.ui.toolbar.Toolbar
import com.github.adamantcheese.chan.ui.view.FastScroller
import com.github.adamantcheese.chan.ui.view.FastScrollerHelper
import com.github.adamantcheese.chan.ui.view.PostInfoMapItemDecoration
import com.github.adamantcheese.chan.ui.view.ThumbnailView
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.updatePaddings
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.*
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * A layout that wraps around a [RecyclerView] and a [ReplyLayout] to manage showing and replying to posts.
 */
class ThreadListLayout(context: Context, attrs: AttributeSet?)
  : FrameLayout(context, attrs),
  ReplyLayoutCallback,
  Toolbar.ToolbarHeightUpdatesCallback,
  CoroutineScope{

  @Inject
  lateinit var themeHelper: ThemeHelper
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

  private lateinit var reply: ReplyLayout
  private lateinit var searchStatus: TextView
  private lateinit var recyclerView: RecyclerView
  private lateinit var postAdapter: PostAdapter

  private val compositeDisposable = CompositeDisposable()
  private val job = SupervisorJob()

  private val listScrollToBottomExecutor = RendezvousCoroutineExecutor(this)

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("ThreadListLayout")

  private var layoutManager: RecyclerView.LayoutManager? = null
  private var fastScroller: FastScroller? = null
  private var postInfoMapItemDecoration: PostInfoMapItemDecoration? = null
  private var callback: ThreadListLayoutPresenterCallback? = null
  private var threadListLayoutCallback: ThreadListLayoutCallback? = null
  private var postViewMode: PostViewMode? = null
  private var spanCount = 2
  private var background = 0
  private var searchOpen = false
  private var lastPostCount = 0
  private var hat: Bitmap? = null
  private var showingThread: ChanThread? = null

  var replyOpen = false
    private set

  private val PARTY: ItemDecoration = object : ItemDecoration() {
    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
      if (hat == null) {
        hat = BitmapFactory.decodeResource(resources, R.drawable.partyhat)
      }

      var i = 0
      val j = parent.childCount

      while (i < j) {
        val child = parent.getChildAt(i)
        if (child is PostCellInterface) {
          val postView = child as PostCellInterface
          val post = postView.getPost()

          if (post == null || !post.isOP || post.postImages.isEmpty()) {
            i++
            continue
          }

          val params = child.layoutParams as RecyclerView.LayoutParams
          val top = child.top + params.topMargin
          val left = child.left + params.leftMargin

          c.drawBitmap(
            hat!!,
            left - parent.paddingLeft - AndroidUtils.dp(25f).toFloat(),
            top - AndroidUtils.dp(80f) - parent.paddingTop + toolbarHeight().toFloat(),
            null
          )
        }

        i++
      }
    }
  }

  private val scrollListener: RecyclerView.OnScrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
      onRecyclerViewScrolled()
    }
  }

  val replyPresenter: ReplyPresenter
    get() = reply.getPresenter()

  val displayingPosts: List<Post>
    get() = postAdapter.displayList

  val indexAndTop: IntArray
    get() {
      var index = 0
      var top = 0

      if (recyclerView.layoutManager?.childCount ?: -1 > 0) {
        val topChild = recyclerView.layoutManager!!.getChildAt(0)
        index = (topChild!!.layoutParams as RecyclerView.LayoutParams).viewLayoutPosition
        val params = topChild.layoutParams as RecyclerView.LayoutParams
        top = layoutManager!!.getDecoratedTop(topChild) - params.topMargin - recyclerView.paddingTop
      }

      return intArrayOf(index, top)
    }

  private val topAdapterPosition: Int
    get() {
      when (postViewMode) {
        PostViewMode.LIST -> return (layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        PostViewMode.CARD -> return (layoutManager as GridLayoutManager).findFirstVisibleItemPosition()
      }
      return -1
    }

  private val completeBottomAdapterPosition: Int
    get() {
      when (postViewMode) {
        PostViewMode.LIST -> return (layoutManager as LinearLayoutManager).findLastCompletelyVisibleItemPosition()
        PostViewMode.CARD -> return (layoutManager as GridLayoutManager).findLastCompletelyVisibleItemPosition()
      }
      return -1
    }

  private fun currentThreadDescriptorOrNull(): ThreadDescriptor? {
    return showingThread?.chanDescriptor?.threadDescriptorOrNull()
  }

  private fun currentChanDescriptorOrNull(): ChanDescriptor? {
    return showingThread?.chanDescriptor
  }

  private fun forceRecycleAllPostViews() {
    val adapter = recyclerView.adapter
    if (adapter is PostAdapter) {
      recyclerView.recycledViewPool.clear()
      adapter.cleanup()
    }
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    Chan.inject(this)

    // View binding
    reply = findViewById(R.id.reply)
    searchStatus = findViewById(R.id.search_status)
    recyclerView = findViewById(R.id.recycler_view)

    val params = reply.layoutParams as LayoutParams
    params.gravity = Gravity.BOTTOM
    reply.layoutParams = params

    // View setup
    reply.setCallback(this)
    searchStatus.typeface = themeHelper.theme.mainFont
  }

  fun setCallbacks(
    postAdapterCallback: PostAdapterCallback?,
    postCellCallback: PostCellCallback?,
    statusCellCallback: ThreadStatusCell.Callback?,
    callback: ThreadListLayoutPresenterCallback?,
    threadListLayoutCallback: ThreadListLayoutCallback?
  ) {
    this.callback = callback
    this.threadListLayoutCallback = threadListLayoutCallback

    postAdapter = PostAdapter(
      postFilterManager,
      recyclerView,
      postAdapterCallback,
      postCellCallback,
      statusCellCallback,
      themeHelper.theme
    )

    recyclerView.adapter = postAdapter
    // Man, fuck the RecycledViewPool. Sometimes when scrolling away from a view and the swiftly
    // back to it onViewRecycled() will be called TWICE for that view. Setting setMaxRecycledViews
    // for TYPE_POST to 0 solves this problem. What a buggy piece of shit.
    recyclerView.recycledViewPool.setMaxRecycledViews(PostAdapter.TYPE_POST, 0)
    recyclerView.addOnScrollListener(scrollListener)

    setFastScroll(false)
    attachToolbarScroll(true)

    threadListLayoutCallback?.toolbar?.addToolbarHeightUpdatesCallback(this)

    // Wait a little bit so that the toolbar has it's updated height (which depends on the window
    // insets)
    post {
      searchStatus.updatePaddings(top = searchStatus.paddingTop + toolbarHeight())
    }
  }

  fun onDestroy() {
    compositeDisposable.clear()
    job.cancelChildren()

    threadListLayoutCallback?.toolbar?.removeToolbarHeightUpdatesCallback(this)

    forceRecycleAllPostViews()
    recyclerView.adapter = null
  }

  override fun onToolbarHeightKnown(heightChanged: Boolean) {
    setRecyclerViewPadding()
  }

  private fun onRecyclerViewScrolled() {
    // onScrolled can be called after cleanup()
    if (showingThread == null) {
      return
    }

    val chanDescriptor = currentChanDescriptorOrNull()
      ?: return
    val indexTop = indexAndTop

    chanThreadViewableInfoManager.update(chanDescriptor) { chanThreadViewableInfo ->
      chanThreadViewableInfo.listViewIndex = indexTop[0]
      chanThreadViewableInfo.listViewTop = indexTop[1]
    }

    val last = completeBottomAdapterPosition
    updateLastViewedPostNo(last)

    if (last == postAdapter.itemCount - 1 && last > lastPostCount) {
      lastPostCount = last

      // As requested by the RecyclerView, make sure that the adapter isn't changed
      // while in a layout pass. Postpone to the next frame.
      listScrollToBottomExecutor.post { callback?.onListScrolledToBottom() }
    }
  }

  private fun updateLastViewedPostNo(last: Int) {
    if (last <= lastPostCount) {
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

    val cardWidth = AndroidUtils.getDimen(R.dimen.grid_card_width)
    val gridCountSetting = ChanSettings.boardGridSpanCount.get()
    val compactMode: Boolean

    if (gridCountSetting > 0) {
      spanCount = gridCountSetting
      compactMode = measuredWidth / spanCount < AndroidUtils.dp(120f)
    } else {
      spanCount = Math.max(1, Math.round(measuredWidth.toFloat() / cardWidth))
      compactMode = false
    }

    if (postViewMode == PostViewMode.CARD) {
      postAdapter.setCompact(compactMode)
      (layoutManager as GridLayoutManager?)!!.spanCount = spanCount
    }
  }

  fun setPostViewMode(postViewMode: PostViewMode) {
    if (this.postViewMode == postViewMode) {
      return
    }

    this.postViewMode = postViewMode
    layoutManager = null

    when (postViewMode) {
      PostViewMode.LIST -> {
        val linearLayoutManager: LinearLayoutManager = object : LinearLayoutManager(context) {
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

        if (background != R.attr.backcolor) {
          background = R.attr.backcolor
          setBackgroundColor(AndroidUtils.getAttrColor(context, R.attr.backcolor))
        }
      }
      PostViewMode.CARD -> {
        val gridLayoutManager: GridLayoutManager = object : GridLayoutManager(null, spanCount, VERTICAL, false) {
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

        if (background != R.attr.backcolor_secondary) {
          background = R.attr.backcolor_secondary
          setBackgroundColor(AndroidUtils.getAttrColor(context, R.attr.backcolor_secondary))
        }
      }
    }

    recyclerView.recycledViewPool.clear()
    postAdapter.setPostViewMode(postViewMode)
  }

  suspend fun showPosts(
    thread: ChanThread,
    filter: PostsFilter,
    initial: Boolean,
    refreshAfterHideOrRemovePosts: Boolean
  ) {
    showingThread = thread

    if (initial) {
      reply.bindLoadable(thread.chanDescriptor)

      recyclerView.layoutManager = null
      recyclerView.layoutManager = layoutManager
      recyclerView.recycledViewPool.clear()

      val chanDescriptor = currentChanDescriptorOrNull()
      if (chanDescriptor != null) {
        chanThreadViewableInfoManager.view(chanDescriptor) { (_, index, top) ->
          when (postViewMode) {
            PostViewMode.LIST -> (layoutManager as LinearLayoutManager).scrollToPositionWithOffset(index, top)
            PostViewMode.CARD -> (layoutManager as GridLayoutManager).scrollToPositionWithOffset(index, top)
          }
        }
      }

      party()
    }

    setFastScroll(true)

    postAdapter.setThread(
      thread.chanDescriptor,
      thread.postPreloadedInfoHolder,
      filter.apply(thread.posts),
      refreshAfterHideOrRemovePosts
    )
  }

  fun onBack(): Boolean {
    return when {
      reply.onBack() -> true
      replyOpen -> {
        openReply(false)
        true
      }
      else -> threadListLayoutCallback!!.threadBackPressed()
    }
  }

  fun sendKeyEvent(event: KeyEvent): Boolean {
    if (!ChanSettings.volumeKeysScrolling.get()) {
      return false
    }

    when (event.keyCode) {
      KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
        if (event.action == KeyEvent.ACTION_DOWN) {
          val down = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
          val scroll = (height * 0.75).toInt()
          recyclerView.smoothScrollBy(0, if (down) scroll else -scroll)
        }

        return true
      }
    }

    return false
  }

  fun gainedFocus() {
    showToolbarIfNeeded()
  }

  override fun openReply(open: Boolean) {
    if (showingThread == null || replyOpen == open) {
      return
    }

    val thread = thread
      ?: return
    val chanDescriptor = thread.chanDescriptor

    if (chanDescriptor != null) {
      bottomNavBarVisibilityStateManager.replyViewStateChanged(chanDescriptor.isCatalogDescriptor(), open)
    }

    replyOpen = open

    reply.measure(
      MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    )

    val height = reply.measuredHeight
    val viewPropertyAnimator = reply.animate()

    viewPropertyAnimator.setListener(null)
    viewPropertyAnimator.interpolator = DecelerateInterpolator(2f)
    viewPropertyAnimator.duration = 600

    val isSplitMode = ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT
    if (open) {
      reply.visibility = VISIBLE
      reply.translationY = height.toFloat()
      viewPropertyAnimator.translationY(0f)

      if (!isSplitMode) {
        threadListLayoutCallback?.hideBottomNavBar(lockTranslation = true, lockCollapse = true)
      }
    } else {
      if (!isSplitMode) {
        threadListLayoutCallback?.showBottomNavBar(unlockTranslation = true, unlockCollapse = true)
      }

      reply.translationY = 0f

      viewPropertyAnimator.translationY(height.toFloat())
      viewPropertyAnimator.setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
          viewPropertyAnimator.setListener(null)
          reply.visibility = GONE
        }
      })
    }

    reply.onOpen(open)
    setRecyclerViewPadding()

    if (!open) {
      AndroidUtils.hideKeyboard(reply)
    }

    threadListLayoutCallback?.replyLayoutOpen(open)
    attachToolbarScroll(!open && !searchOpen)
  }

  fun showError(error: String?) {
    postAdapter.showError(error)
  }

  fun openSearch(open: Boolean) {
    if (showingThread == null || searchOpen == open) {
      return
    }

    searchOpen = open
    searchStatus.measure(
      MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    )

    val height = searchStatus.measuredHeight
    val viewPropertyAnimator = searchStatus.animate()

    viewPropertyAnimator.setListener(null)
    viewPropertyAnimator.interpolator = DecelerateInterpolator(2f)
    viewPropertyAnimator.duration = 600

    if (open) {
      searchStatus.visibility = VISIBLE
      searchStatus.translationY = -height.toFloat()

      viewPropertyAnimator.translationY(0f)
    } else {
      searchStatus.translationY = 0f

      viewPropertyAnimator.translationY(-height.toFloat())
      viewPropertyAnimator.setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
          viewPropertyAnimator.setListener(null)
          searchStatus.visibility = GONE
        }
      })
    }

    setRecyclerViewPadding()

    if (open) {
      searchStatus.setText(R.string.search_empty)
    } else {
      threadListLayoutCallback!!.toolbar!!.closeSearchPhoneMode()
    }

    attachToolbarScroll(!(open || replyOpen))
  }

  @SuppressLint("StringFormatMatches")
  //android studio doesn't like the nested getQuantityString and messes up, but nothing is wrong
  fun setSearchStatus(query: String?, setEmptyText: Boolean, hideKeyboard: Boolean) {
    if (hideKeyboard) {
      AndroidUtils.hideKeyboard(this)
    }

    if (setEmptyText) {
      searchStatus.setText(R.string.search_empty)
    }

    if (query != null) {
      val size = displayingPosts.size
      searchStatus.text = AndroidUtils.getString(R.string.search_results,
        AndroidUtils.getQuantityString(R.plurals.posts, size, size),
        query
      )
    }
  }

  fun canChildScrollUp(): Boolean {
    if (replyOpen) {
      return true
    }

    if (topAdapterPosition != 0) {
      return true
    }

    val top = layoutManager?.findViewByPosition(0)
      ?: return true

    if (searchOpen) {
      val searchExtraHeight = findViewById<View>(R.id.search_status).height

      if (postViewMode == PostViewMode.LIST) {
        return top.top != searchExtraHeight
      } else {
        return if (top is PostStubCell) {
          // PostStubCell does not have grid_card_margin
          top.getTop() != searchExtraHeight + AndroidUtils.dp(1f)
        } else {
          top.top != AndroidUtils.getDimen(R.dimen.grid_card_margin) + AndroidUtils.dp(1f) + searchExtraHeight
        }
      }
    }

    when (postViewMode) {
      PostViewMode.LIST -> return top.top != toolbarHeight()
      PostViewMode.CARD -> return if (top is PostStubCell) {
        // PostStubCell does not have grid_card_margin
        top.getTop() != toolbarHeight() + AndroidUtils.dp(1f)
      } else {
        top.top != AndroidUtils.getDimen(R.dimen.grid_card_margin) + AndroidUtils.dp(1f) + toolbarHeight()
      }
    }
    return true
  }

  fun scrolledToBottom(): Boolean {
    return completeBottomAdapterPosition == postAdapter.itemCount - 1
  }

  fun smoothScrollNewPosts(displayPosition: Int) {
    if (layoutManager !is LinearLayoutManager) {
      Logger.wtf(TAG, "Layout manager is grid inside thread??")
      return
    }

    (layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
      //position + 1 for last seen view, dp(4) for it's height
      displayPosition + 1,
      recyclerView.height - recyclerView.paddingTop - AndroidUtils.dp(4f)
    )
  }

  fun cleanup() {
    postAdapter.cleanup()
    reply.cleanup()

    openReply(false)

    if (showingThread != null && showingThread?.chanDescriptor?.isThreadDescriptor() == true) {
      openSearch(false)
    }

    showingThread = null
    lastPostCount = 0
    noParty()
  }

  fun getThumbnail(postImage: PostImage?): ThumbnailView? {
    val layoutManager = recyclerView.layoutManager
      ?: return null

    for (i in 0 until layoutManager.childCount) {
      val view = layoutManager.getChildAt(i)

      if (view is PostCellInterface) {
        val postView = view as PostCellInterface
        val post = postView.getPost()

        if (post != null) {
          for (image in post.postImages) {
            if (image.equalUrl(postImage)) {
              return postView.getThumbnailView(postImage!!)
            }
          }
        }
      }
    }

    return null
  }

  fun scrollTo(displayPosition: Int, isSmooth: Boolean) {
    var smooth = isSmooth

    if (displayPosition < 0) {
      val bottom = postAdapter.itemCount - 1
      val difference = Math.abs(bottom - topAdapterPosition)

      if (difference > MAX_SMOOTH_SCROLL_DISTANCE) {
        smooth = false
      }

      if (smooth) {
        recyclerView.smoothScrollToPosition(bottom)
      } else {
        recyclerView.scrollToPosition(bottom)
        // No animation means no animation, wait for the layout to finish and skip all animations
        val itemAnimator = recyclerView.itemAnimator

        AndroidUtils.waitForLayout(recyclerView) { view: View? ->
          itemAnimator!!.endAnimations()
          true
        }
      }

      return
    }

    val scrollPosition = postAdapter.getScrollPosition(displayPosition)

    val difference = Math.abs(scrollPosition - topAdapterPosition)
    if (difference > MAX_SMOOTH_SCROLL_DISTANCE) {
      smooth = false
    }

    if (smooth) {
      recyclerView.smoothScrollToPosition(scrollPosition)
    } else {
      recyclerView.scrollToPosition(scrollPosition)
      // No animation means no animation, wait for the layout to finish and skip all animations
      val itemAnimator = recyclerView.itemAnimator

      AndroidUtils.waitForLayout(recyclerView) {
        itemAnimator!!.endAnimations()
        true
      }
    }
  }

  fun highlightPost(post: Post?) {
    postAdapter.highlightPost(post)
  }

  fun highlightPostId(id: String?) {
    postAdapter.highlightPostId(id)
  }

  fun highlightPostTripcode(tripcode: CharSequence?) {
    postAdapter.highlightPostTripcode(tripcode)
  }

  fun selectPost(post: Long) {
    postAdapter.selectPost(post)
  }

  override fun highlightPostNo(no: Int) {
    postAdapter.highlightPostNo(no)
  }

  override fun showThread(threadDescriptor: ThreadDescriptor) {
    callback?.showThread(threadDescriptor)
  }

  override fun requestNewPostLoad() {
    callback?.requestNewPostLoad()
  }

  override fun getThread(): ChanThread? {
    return showingThread
  }

  override fun showImageReencodingWindow(supportsReencode: Boolean) {
    threadListLayoutCallback?.showImageReencodingWindow(supportsReencode)
  }

  private fun shouldToolbarCollapse(): Boolean {
    return (ChanSettings.getCurrentLayoutMode() != ChanSettings.LayoutMode.SPLIT
      && !ChanSettings.neverHideToolbar.get())
  }

  private fun attachToolbarScroll(attach: Boolean) {
    if (shouldToolbarCollapse()) {
      val toolbar = threadListLayoutCallback?.toolbar
        ?: return

      if (attach) {
        toolbar.attachRecyclerViewScrollStateListener(recyclerView)
      } else {
        toolbar.detachRecyclerViewScrollStateListener(recyclerView)
        toolbar.collapseShow(true)
      }
    }
  }

  private fun showToolbarIfNeeded() {
    if (shouldToolbarCollapse()) {
      // Of coming back to focus from a dual controller, like the threadlistcontroller,
      // check if we should show the toolbar again (after the other controller made it hide).
      // It should show if the search or reply is open, or if the thread was scrolled at the
      // top showing an empty space.
      val toolbar = threadListLayoutCallback?.toolbar
        ?: return

      if (searchOpen || replyOpen) {
        // force toolbar to show
        toolbar.collapseShow(true)
      } else {
        // check if it should show if it was scrolled at the top
        toolbar.checkToolbarCollapseState(recyclerView)
      }
    }
  }

  private fun setFastScroll(enabled: Boolean) {
    if (!enabled) {
      if (fastScroller != null) {
        recyclerView.removeItemDecoration(fastScroller!!)
        fastScroller = null
      }

      postInfoMapItemDecoration = null
    } else {
      val thread = thread
      if (thread != null) {
        if (thread.chanDescriptor.isThreadDescriptor() && postInfoMapItemDecoration == null) {
          postInfoMapItemDecoration = PostInfoMapItemDecoration(
            context,
            ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT
          )
        }
        if (postInfoMapItemDecoration != null) {
          postInfoMapItemDecoration!!.setItems(
            extractPostMapInfoHolderUseCase.execute(thread.posts),
            thread.postsCount
          )
        }
        if (fastScroller == null) {
          fastScroller = FastScrollerHelper.create(
            globalWindowInsetsManager,
            recyclerView,
            postInfoMapItemDecoration,
            themeHelper.theme
          )
        }
      }
    }

    recyclerView.isVerticalScrollBarEnabled = !enabled
  }

  override fun updatePadding() {
    setRecyclerViewPadding()
  }

  private fun setRecyclerViewPadding() {
    val defaultPadding = if (postViewMode == PostViewMode.CARD) AndroidUtils.dp(1f) else 0
    var recyclerTop = defaultPadding + toolbarHeight()
    var recyclerBottom = defaultPadding

    // measurements
    if (replyOpen) {
      reply.measure(
        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
      )

      // recycler view padding calculations
      recyclerBottom += reply.measuredHeight
    }

    if (searchOpen) {
      searchStatus.measure(
        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
      )

      // search status has built-in padding for the toolbar height
      recyclerTop += searchStatus.measuredHeight
      recyclerTop -= toolbarHeight()
    }

    recyclerView.setPadding(defaultPadding, recyclerTop, defaultPadding, recyclerBottom)
  }

  fun toolbarHeight(): Int {
    return threadListLayoutCallback!!.toolbar!!.toolbarHeight
  }

  private fun party() {
    if (showingThread == null) {
      return
    }

    if (showingThread!!.chanDescriptor.siteDescriptor().is4chan()) {
      val calendar = Calendar.getInstance()
      if (calendar[Calendar.MONTH] == Calendar.OCTOBER && calendar[Calendar.DAY_OF_MONTH] == 1) {
        recyclerView.addItemDecoration(PARTY)
      }
    }
  }

  private fun noParty() {
    recyclerView.removeItemDecoration(PARTY)
  }

  fun onImageOptionsApplied(modifiedReply: Reply?, filenameRemoved: Boolean) {
    reply.onImageOptionsApplied(modifiedReply, filenameRemoved)
  }

  fun onImageOptionsComplete() {
    reply.onImageOptionsComplete()
  }

  fun onPostUpdated(post: Post) {
    BackgroundUtils.ensureMainThread()

    postAdapter.updatePost(post)
  }

  interface ThreadListLayoutPresenterCallback {
    fun showThread(threadDescriptor: ThreadDescriptor)
    fun requestNewPostLoad()
    suspend fun onListScrolledToBottom()
  }

  interface ThreadListLayoutCallback {
    val toolbar: Toolbar?
    val chanDescriptor: ChanDescriptor?

    fun hideBottomNavBar(lockTranslation: Boolean, lockCollapse: Boolean)
    fun showBottomNavBar(unlockTranslation: Boolean, unlockCollapse: Boolean)
    fun replyLayoutOpen(open: Boolean)
    fun showImageReencodingWindow(supportsReencode: Boolean)
    fun threadBackPressed(): Boolean
  }

  companion object {
    private const val TAG = "ThreadListLayout"
    const val MAX_SMOOTH_SCROLL_DISTANCE = 20
  }
}