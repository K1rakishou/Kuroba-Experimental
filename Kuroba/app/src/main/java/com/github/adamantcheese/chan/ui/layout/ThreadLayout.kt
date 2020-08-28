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
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.base.SerializedCoroutineExecutor
import com.github.adamantcheese.chan.core.manager.PostFilterManager
import com.github.adamantcheese.chan.core.manager.PostHideManager
import com.github.adamantcheese.chan.core.manager.SiteManager
import com.github.adamantcheese.chan.core.model.ChanThread
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter.ThreadPresenterCallback
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode
import com.github.adamantcheese.chan.core.site.http.Reply
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderException
import com.github.adamantcheese.chan.features.drawer.DrawerCallbacks
import com.github.adamantcheese.chan.ui.adapter.PostsFilter
import com.github.adamantcheese.chan.ui.controller.FloatingListMenuController
import com.github.adamantcheese.chan.ui.helper.ImageOptionsHelper
import com.github.adamantcheese.chan.ui.helper.ImageOptionsHelper.ImageReencodingHelperCallback
import com.github.adamantcheese.chan.ui.helper.PostPopupHelper
import com.github.adamantcheese.chan.ui.helper.PostPopupHelper.PostPopupHelperCallback
import com.github.adamantcheese.chan.ui.helper.RemovedPostsHelper
import com.github.adamantcheese.chan.ui.helper.RemovedPostsHelper.RemovedPostsCallbacks
import com.github.adamantcheese.chan.ui.layout.ThreadListLayout.ThreadListLayoutCallback
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.ui.toolbar.Toolbar
import com.github.adamantcheese.chan.ui.view.HidingFloatingActionButton
import com.github.adamantcheese.chan.ui.view.LoadView
import com.github.adamantcheese.chan.ui.view.ThumbnailView
import com.github.adamantcheese.chan.ui.widget.SnackbarWrapper
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.filter.FilterType
import com.github.adamantcheese.model.data.post.ChanPostHide
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * Wrapper around ThreadListLayout, so that it cleanly manages between a loading state
 * and the recycler view.
 */
class ThreadLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : CoordinatorLayout(context, attrs, defStyle),
  ThreadPresenterCallback,
  PostPopupHelperCallback,
  ImageReencodingHelperCallback,
  RemovedPostsCallbacks,
  View.OnClickListener,
  ThreadListLayoutCallback,
  CoroutineScope {

  private enum class Visible {
    EMPTY, LOADING, THREAD, ERROR
  }

  @Inject
  lateinit var presenter: ThreadPresenter
  @Inject
  lateinit var themeHelper: ThemeHelper
  @Inject
  lateinit var postFilterManager: PostFilterManager
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var postHideManager: PostHideManager

  private lateinit var callback: ThreadLayoutCallback
  private lateinit var progressLayout: View
  private lateinit var loadView: LoadView
  private lateinit var replyButton: HidingFloatingActionButton
  private lateinit var threadListLayout: ThreadListLayout
  private lateinit var errorLayout: LinearLayout
  private lateinit var errorText: TextView
  private lateinit var errorRetryButton: Button
  private lateinit var postPopupHelper: PostPopupHelper
  private lateinit var imageReencodingHelper: ImageOptionsHelper
  private lateinit var removedPostsHelper: RemovedPostsHelper

  private var drawerCallbacks: DrawerCallbacks? = null
  private var newPostsNotification: SnackbarWrapper? = null
  private var replyButtonEnabled = false
  private var showingReplyButton = false
  private var refreshedFromSwipe = false
  private var deletingDialog: ProgressDialog? = null
  private var visible: Visible? = null

  private val job = SupervisorJob()
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("ThreadLayout")

  override val toolbar: Toolbar?
    get() = callback.toolbar

  override val chanDescriptor: ChanDescriptor?
    get() = presenter.chanDescriptor

  override val displayingPosts: List<Post>
    get() = if (postPopupHelper.isOpen) {
      postPopupHelper.displayingPosts
    } else {
      threadListLayout.displayingPosts
    }

  override val currentPosition: IntArray
    get() = threadListLayout.indexAndTop

  init {
    Chan.inject(this)
  }

  fun setDrawerCallbacks(drawerCallbacks: DrawerCallbacks?) {
    this.drawerCallbacks = drawerCallbacks
  }

  fun create(callback: ThreadLayoutCallback) {
    this.callback = callback
    this.serializedCoroutineExecutor = SerializedCoroutineExecutor(this)

    // View binding
    loadView = findViewById(R.id.loadview)
    replyButton = findViewById(R.id.reply_button)

    // Inflate ThreadListLayout
    threadListLayout = AndroidUtils.inflate(context, R.layout.layout_thread_list, this, false) as ThreadListLayout

    // Inflate error layout
    errorLayout = AndroidUtils.inflate(context, R.layout.layout_thread_error, this, false) as LinearLayout
    errorText = errorLayout.findViewById(R.id.text)
    errorRetryButton = errorLayout.findViewById(R.id.button)

    // Inflate thread loading layout
    progressLayout = AndroidUtils.inflate(context, R.layout.layout_thread_progress, this, false)

    // View setup
    presenter.create(context, this)
    threadListLayout.onCreate(presenter, presenter, presenter, presenter, this)
    postPopupHelper = PostPopupHelper(context, presenter, this)
    imageReencodingHelper = ImageOptionsHelper(context, this)
    removedPostsHelper = RemovedPostsHelper(context, presenter, this)
    errorText.typeface = themeHelper.theme.mainFont
    errorRetryButton.setOnClickListener(this)

    // Setup
    replyButtonEnabled = ChanSettings.enableReplyFab.get()

    if (!replyButtonEnabled) {
      AndroidUtils.removeFromParentView(replyButton)
    } else {
      replyButton.setOnClickListener(this)
      replyButton.setToolbar(callback.toolbar!!)

      themeHelper.theme.applyFabColor(replyButton)
    }
  }

  fun destroy() {
    drawerCallbacks = null
    presenter.unbindChanDescriptor()
    threadListLayout.onDestroy()
    job.cancelChildren()
  }

  override fun onClick(v: View) {
    if (v === errorRetryButton) {
      presenter.requestData()
    } else if (v === replyButton) {
      threadListLayout.openReply(true)
    }
  }

  fun canChildScrollUp(): Boolean {
    return if (visible == Visible.THREAD) {
      threadListLayout.canChildScrollUp()
    } else {
      true
    }
  }

  fun onBack(): Boolean {
    return threadListLayout.onBack()
  }

  fun sendKeyEvent(event: KeyEvent): Boolean {
    return threadListLayout.sendKeyEvent(event)
  }

  fun refreshFromSwipe() {
    refreshedFromSwipe = true
    presenter.requestData()
  }

  fun gainedFocus() {
    if (visible == Visible.THREAD) {
      threadListLayout.gainedFocus()
    }
  }

  fun setPostViewMode(postViewMode: PostViewMode) {
    threadListLayout.setPostViewMode(postViewMode)
  }

  override fun hideBottomNavBar(lockTranslation: Boolean, lockCollapse: Boolean) {
    drawerCallbacks?.hideBottomNavBar(lockTranslation, lockCollapse)
  }

  override fun showBottomNavBar(unlockTranslation: Boolean, unlockCollapse: Boolean) {
    drawerCallbacks?.showBottomNavBar(unlockTranslation, unlockCollapse)
  }

  override fun replyLayoutOpen(open: Boolean) {
    showReplyButton(!open)
  }

  override fun showImageReencodingWindow(supportsReencode: Boolean) {
    presenter.showImageReencodingWindow(supportsReencode)
  }

  override fun threadBackPressed(): Boolean {
    return callback.threadBackPressed()
  }

  override suspend fun showPosts(
    thread: ChanThread?,
    filter: PostsFilter,
    refreshAfterHideOrRemovePosts: Boolean
  ) {
    if (thread == null) {
      return
    }

    threadListLayout.showPosts(thread, filter, visible != Visible.THREAD, refreshAfterHideOrRemovePosts)
    switchVisible(Visible.THREAD)
    callback.onShowPosts()
  }

  override fun postClicked(post: Post) {
    if (postPopupHelper.isOpen) {
      postPopupHelper.postClicked(post)
    }
  }

  override fun showError(error: ChanLoaderException) {
    val errorMessage = AndroidUtils.getString(error.errorMessage)
    if (visible == Visible.THREAD) {
      threadListLayout.showError(errorMessage)
    } else {
      switchVisible(Visible.ERROR)
      errorText.text = errorMessage
    }
  }

  override fun showLoading() {
    switchVisible(Visible.LOADING)
  }

  override fun showEmpty() {
    switchVisible(Visible.EMPTY)
  }

  override fun showPostInfo(info: String) {
    AlertDialog.Builder(context).setTitle(R.string.post_info_title)
      .setMessage(info)
      .setPositiveButton(R.string.ok, null)
      .show()
  }

  @Suppress("MoveLambdaOutsideParentheses")
  override fun showPostLinkables(post: Post) {
    val linkables = post.linkables
    val keys = arrayOfNulls<String>(linkables.size)

    for (i in linkables.indices) {
      keys[i] = linkables[i].key.toString()
    }

    AlertDialog.Builder(context)
      .setItems(keys, { _, which -> presenter.onPostLinkableClicked(post, linkables[which]) })
      .show()
  }

  override fun clipboardPost(post: Post) {
    AndroidUtils.setClipboardContent("Post text", post.comment.toString())
    AndroidUtils.showToast(context, R.string.post_text_copied)
  }

  override fun openLink(link: String) {
    if (ChanSettings.openLinkConfirmation.get()) {
      AlertDialog.Builder(context).setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.ok) { dialog: DialogInterface?, which: Int -> openLinkConfirmed(link) }
        .setTitle(R.string.open_link_confirmation)
        .setMessage(link)
        .show()
    } else {
      openLinkConfirmed(link)
    }
  }

  private fun openLinkConfirmed(link: String) {
    if (ChanSettings.openLinkBrowser.get()) {
      AndroidUtils.openLink(link)
    } else {
      AndroidUtils.openLinkInBrowser(context, link, themeHelper.theme)
    }
  }

  override fun openReportView(post: Post) {
    callback.openReportController(post)
  }

  override suspend fun showThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    callback.openThreadCrossThread(threadDescriptor)
  }

  override suspend fun showBoard(boardDescriptor: BoardDescriptor) {
    callback.showBoard(boardDescriptor)
  }

  override suspend fun showBoardAndSearch(boardDescriptor: BoardDescriptor, searchQuery: String?) {
    callback.showBoardAndSearch(boardDescriptor, searchQuery)
  }

  override fun showPostsPopup(forPost: Post, posts: List<Post>) {
    if (this.focusedChild != null) {
      val currentFocus = this.focusedChild
      AndroidUtils.hideKeyboard(currentFocus)
      currentFocus.clearFocus()
    }

    postPopupHelper.showPosts(forPost, posts)
  }

  override fun hidePostsPopup() {
    postPopupHelper.popAll()
  }

  override fun showImages(
    images: List<PostImage>,
    index: Int,
    chanDescriptor: ChanDescriptor,
    thumbnail: ThumbnailView
  ) {
    if (this.focusedChild != null) {
      val currentFocus = this.focusedChild
      AndroidUtils.hideKeyboard(currentFocus)
      currentFocus.clearFocus()
    }

    callback.showImages(images, index, chanDescriptor, thumbnail)
  }

  override fun showAlbum(images: List<PostImage>, index: Int) {
    callback.showAlbum(images, index)
  }

  override fun scrollTo(displayPosition: Int, smooth: Boolean) {
    if (postPopupHelper.isOpen) {
      postPopupHelper.scrollTo(displayPosition, smooth)
    } else if (visible == Visible.THREAD) {
      threadListLayout.scrollTo(displayPosition, smooth)
    }
  }

  override fun smoothScrollNewPosts(displayPosition: Int) {
    threadListLayout.smoothScrollNewPosts(displayPosition)
  }

  override fun highlightPost(post: Post) {
    threadListLayout.highlightPost(post)
  }

  override fun highlightPostId(id: String) {
    threadListLayout.highlightPostId(id)
  }

  override fun highlightPostTripcode(tripcode: CharSequence?) {
    threadListLayout.highlightPostTripcode(tripcode)
  }

  override fun filterPostTripcode(tripcode: CharSequence?) {
    callback.openFilterForType(FilterType.TRIPCODE, tripcode.toString())
  }

  override fun filterPostImageHash(post: Post) {
    if (post.postImages.isEmpty()) {
      return
    }

    if (post.postImages.size == 1) {
      callback.openFilterForType(FilterType.IMAGE, post.firstImage()?.fileHash)
      return
    }

    val hashList = ListView(context)
    val dialog = AlertDialog.Builder(context).setTitle("Select an image to filter.")
      .setView(hashList)
      .create()

    dialog.setCanceledOnTouchOutside(true)

    val hashes: MutableList<String> = ArrayList()
    for (image in post.postImages) {
      if (!image.isInlined && image.fileHash != null) {
        hashes.add(image.fileHash)
      }
    }

    hashList.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, hashes)
    hashList.onItemClickListener = OnItemClickListener { _, _, position: Int, _ ->
      callback.openFilterForType(FilterType.IMAGE, hashes[position])
      dialog.dismiss()
    }

    dialog.show()
  }

  override fun selectPost(post: Long) {
    threadListLayout.selectPost(post)
  }

  override fun showSearch(show: Boolean) {
    threadListLayout.openSearch(show)
  }

  override fun setSearchStatus(query: String?, setEmptyText: Boolean, hideKeyboard: Boolean) {
    threadListLayout.setSearchStatus(query, setEmptyText, hideKeyboard)
  }

  override fun quote(post: Post, withText: Boolean) {
    threadListLayout.openReply(true)
    threadListLayout.replyPresenter.quote(post, withText)
  }

  override fun quote(post: Post, text: CharSequence) {
    threadListLayout.openReply(true)
    threadListLayout.replyPresenter.quote(post, text)
  }

  @Suppress("MoveLambdaOutsideParentheses")
  override fun confirmPostDelete(post: Post) {
    @SuppressLint("InflateParams")
    val view = AndroidUtils.inflate(context, R.layout.dialog_post_delete, null)
    val checkBox = view.findViewById<CheckBox>(R.id.image_only)

    checkBox.buttonTintList = ColorStateList.valueOf(themeHelper.theme.textPrimary)
    checkBox.setTextColor(ColorStateList.valueOf(themeHelper.theme.textPrimary))

    AlertDialog.Builder(context)
      .setTitle(R.string.delete_confirm)
      .setView(view)
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.delete, { _, _ -> presenter.deletePostConfirmed(post, checkBox.isChecked) })
      .show()
  }

  override fun showDeleting() {
    if (deletingDialog == null) {
      deletingDialog = ProgressDialog.show(context, null, AndroidUtils.getString(R.string.delete_wait))
    }
  }

  override fun hideDeleting(message: String) {
    if (deletingDialog != null) {
      deletingDialog!!.dismiss()
      deletingDialog = null

      AlertDialog.Builder(context)
        .setMessage(message)
        .setPositiveButton(R.string.ok, null)
        .show()
    }
  }

  @Suppress("MoveLambdaOutsideParentheses")
  override fun hideThread(post: Post, threadNo: Long, hide: Boolean) {
    serializedCoroutineExecutor.post {
      // hideRepliesToThisPost is false here because we don't have posts in the catalog mode so there
      // is no point in hiding replies to a thread
      val postHide = ChanPostHide(
        postDescriptor = post.postDescriptor,
        onlyHide = hide,
        applyToWholeThread = true,
        applyToReplies = false
      )

      postHideManager.create(postHide)
      presenter.refreshUI()

      val snackbarStringId = if (hide) {
        R.string.thread_hidden
      } else {
        R.string.thread_removed
      }

      SnackbarWrapper.create(this, snackbarStringId, Snackbar.LENGTH_LONG).apply {
        setAction(R.string.undo, {
          serializedCoroutineExecutor.post {
            postFilterManager.remove(post.postDescriptor)
            postHideManager.remove(postHide.postDescriptor)
            presenter.refreshUI()
          }
        })
        show()
      }
    }
  }

  override fun hideOrRemovePosts(hide: Boolean, wholeChain: Boolean, posts: Set<Post>, threadNo: Long) {
    serializedCoroutineExecutor.post {
      val hideList: MutableList<ChanPostHide> = ArrayList()
      for (post in posts) {
        // Do not add the OP post to the hideList since we don't want to hide an OP post
        // while being in a thread (it just doesn't make any sense)
        if (!post.isOP) {
          hideList.add(
            ChanPostHide(
              postDescriptor = post.postDescriptor,
              onlyHide = hide,
              applyToWholeThread = false,
              applyToReplies = wholeChain
            )
          )
        }
      }

      postHideManager.createMany(hideList)
      presenter.refreshUI()

      val formattedString = if (hide) {
        AndroidUtils.getQuantityString(R.plurals.post_hidden, posts.size, posts.size)
      } else {
        AndroidUtils.getQuantityString(R.plurals.post_removed, posts.size, posts.size)
      }

      SnackbarWrapper.create(this, formattedString, Snackbar.LENGTH_LONG).apply {
        setAction(R.string.undo) {
          serializedCoroutineExecutor.post {
            postFilterManager.removeMany(posts.map { post -> post.postDescriptor })

            postHideManager.removeManyChanPostHides(hideList.map { postHide -> postHide.postDescriptor })
            presenter.refreshUI()
          }
        }

        show()
      }
    }
  }

  override fun unhideOrUnremovePost(post: Post) {
    serializedCoroutineExecutor.post {
      postFilterManager.remove(post.postDescriptor)

      postHideManager.removeManyChanPostHides(listOf(post.postDescriptor))
      presenter.refreshUI()
    }
  }

  override fun viewRemovedPostsForTheThread(
    threadPosts: List<Post>,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ) {
    removedPostsHelper.showPosts(threadPosts, threadDescriptor)
  }

  override fun onRestoreRemovedPostsClicked(chanDescriptor: ChanDescriptor, selectedPosts: List<PostDescriptor>) {
    serializedCoroutineExecutor.post {
      postHideManager.removeManyChanPostHides(selectedPosts)
      presenter.refreshUI()

      SnackbarWrapper.create(
        this,
        AndroidUtils.getString(R.string.restored_n_posts, selectedPosts.size),
        Snackbar.LENGTH_LONG
      ).apply { show() }
    }
  }

  override fun onPostUpdated(post: Post) {
    threadListLayout.onPostUpdated(post)
  }

  override fun presentController(floatingListMenuController: FloatingListMenuController, animate: Boolean) {
    callback.presentController(floatingListMenuController, animate)
  }

  override fun showToolbar() {
    toolbar?.collapseShow(true)

    if (replyButton.visibility != View.VISIBLE) {
      replyButton.show()
    }
  }

  override fun showNewPostsNotification(show: Boolean, more: Int) {
    if (!show) {
      dismissSnackbar()
      return
    }

    if (!threadListLayout.scrolledToBottom() && BackgroundUtils.isInForeground()) {
      val text = AndroidUtils.getQuantityString(R.plurals.thread_new_posts, more, more)
      dismissSnackbar()

      newPostsNotification = SnackbarWrapper.create(this, text, Snackbar.LENGTH_LONG).apply {
        setAction(R.string.thread_new_posts_goto) {
          presenter.onNewPostsViewClicked()
          dismissSnackbar()
        }
        show()
      }

      return
    }

    dismissSnackbar()
  }

  private fun dismissSnackbar() {
    newPostsNotification?.dismiss()
    newPostsNotification = null
  }

  override fun onDetachedFromWindow() {
    dismissSnackbar()
    super.onDetachedFromWindow()
  }

  override fun showImageReencodingWindow(chanDescriptor: ChanDescriptor, supportsReencode: Boolean) {
    if (this.focusedChild != null) {
      val currentFocus = this.focusedChild
      AndroidUtils.hideKeyboard(currentFocus)
      currentFocus.clearFocus()
    }

    imageReencodingHelper.showController(chanDescriptor, supportsReencode)
  }

  fun isReplyLayoutOpen(): Boolean = threadListLayout.replyOpen

  fun getThumbnail(postImage: PostImage?): ThumbnailView? {
    return if (postPopupHelper.isOpen) {
      postPopupHelper.getThumbnail(postImage)
    } else {
      threadListLayout.getThumbnail(postImage)
    }
  }

  fun openReply(open: Boolean) {
    threadListLayout.openReply(open)
  }

  private fun showReplyButton(show: Boolean) {
    if (show != showingReplyButton && replyButtonEnabled) {
      showingReplyButton = show

      if (show && replyButton.visibility != View.VISIBLE) {
        replyButton.visibility = View.VISIBLE
      }

      replyButton.animate()
        .setInterpolator(DecelerateInterpolator(2f))
        .setStartDelay(if (show) 100 else 0.toLong())
        .setDuration(200)
        .alpha(if (show) 1f else 0f)
        .scaleX(if (show) 1f else 0f)
        .scaleY(if (show) 1f else 0f)
        .setListener(object : AnimatorListenerAdapter() {
          override fun onAnimationCancel(animation: Animator) {
            replyButton.alpha = if (show) 1f else 0f
            replyButton.scaleX = if (show) 1f else 0f
            replyButton.scaleY = if (show) 1f else 0f
            replyButton.isClickable = show
          }

          override fun onAnimationEnd(animation: Animator) {
            replyButton.isClickable = show
          }
        })
        .start()
    }
  }

  private fun switchVisible(visible: Visible) {
    if (this.visible != visible) {
      if (this.visible != null) {
        if (this.visible == Visible.THREAD) {
          threadListLayout.cleanup()
          postPopupHelper.popAll()

          if (presenter.chanDescriptor == null || presenter.chanDescriptor?.isThreadDescriptor() == true) {
            showSearch(false)
          }

          showReplyButton(false)
          dismissSnackbar()
        }
      }

      this.visible = visible

      when (visible) {
        Visible.EMPTY -> {
          loadView.setView(inflateEmptyView())
          showReplyButton(false)
        }
        Visible.LOADING -> {
          val view = loadView.setView(progressLayout)

          if (refreshedFromSwipe) {
            refreshedFromSwipe = false
            view.visibility = View.GONE
          } else {
            view.visibility = View.VISIBLE
          }

          showReplyButton(false)
        }
        Visible.THREAD -> {
          callback.hideSwipeRefreshLayout()
          loadView.setView(threadListLayout)
          showReplyButton(true)
        }
        Visible.ERROR -> {
          callback.hideSwipeRefreshLayout()
          loadView.setView(errorLayout)
          showReplyButton(false)
        }
      }
    }
  }

  @SuppressLint("InflateParams")
  private fun inflateEmptyView(): View {
    val view = AndroidUtils.inflate(context, R.layout.layout_empty_setup, null)
    val tv = view.findViewById<TextView>(R.id.feature)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      // This unicode symbol crashes app on APIs below 23
      tv.setText(R.string.thread_empty_setup_feature)
    }

    return view
  }

  override fun presentRepliesController(controller: Controller) {
    callback.presentController(controller, true)
  }

  override fun presentReencodeOptionsController(controller: Controller) {
    callback.presentController(controller, true)
  }

  override fun onImageOptionsApplied(reply: Reply, filenameRemoved: Boolean) {
    threadListLayout.onImageOptionsApplied(reply, filenameRemoved)
  }

  override fun onImageOptionsComplete() {
    threadListLayout.onImageOptionsComplete()
  }

  override fun presentRemovedPostsController(controller: Controller) {
    callback.presentController(controller, true)
  }

  @Suppress("MoveLambdaOutsideParentheses")
  override fun showHideOrRemoveWholeChainDialog(hide: Boolean, post: Post, threadNo: Long) {
    val positiveButtonText = if (hide) {
      AndroidUtils.getString(R.string.thread_layout_hide_whole_chain)
    } else {
      AndroidUtils.getString(R.string.thread_layout_remove_whole_chain)
    }

    val negativeButtonText = if (hide) {
      AndroidUtils.getString(R.string.thread_layout_hide_post)
    } else {
      AndroidUtils.getString(R.string.thread_layout_remove_post)
    }

    val message = if (hide) {
      AndroidUtils.getString(R.string.thread_layout_hide_whole_chain_as_well)
    } else {
      AndroidUtils.getString(R.string.thread_layout_remove_whole_chain_as_well)
    }

    val alertDialog = AlertDialog.Builder(context).setMessage(message)
      .setPositiveButton(positiveButtonText, { _, _ -> presenter.hideOrRemovePosts(hide, true, post, threadNo) })
      .setNegativeButton(negativeButtonText, { _, _ -> presenter.hideOrRemovePosts(hide, false, post, threadNo) })
      .create()

    alertDialog.show()
  }

  interface ThreadLayoutCallback {
    val toolbar: Toolbar?

    suspend fun showThread(descriptor: ChanDescriptor.ThreadDescriptor)
    suspend fun openThreadCrossThread(threadToOpenDescriptor: ChanDescriptor.ThreadDescriptor)
    suspend fun showBoard(descriptor: BoardDescriptor)
    suspend fun showBoardAndSearch(descriptor: BoardDescriptor, searchQuery: String?)
    fun showImages(images: @JvmSuppressWildcards List<PostImage>, index: Int, chanDescriptor: ChanDescriptor, thumbnail: ThumbnailView)
    fun showAlbum(images: @JvmSuppressWildcards List<PostImage>, index: Int)
    fun onShowPosts()
    fun presentController(controller: Controller, animated: Boolean)
    fun openReportController(post: Post)
    fun hideSwipeRefreshLayout()
    fun openFilterForType(type: FilterType, filterText: String?)
    fun threadBackPressed(): Boolean
  }
}