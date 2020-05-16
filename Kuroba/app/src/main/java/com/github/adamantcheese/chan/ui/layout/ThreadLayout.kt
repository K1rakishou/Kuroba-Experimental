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
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.manager.FilterType
import com.github.adamantcheese.chan.core.model.ChanThread
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.model.orm.PostHide
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter.ThreadPresenterCallback
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode
import com.github.adamantcheese.chan.core.site.http.Reply
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderException
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
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.google.android.material.snackbar.Snackbar
import java.util.*
import javax.inject.Inject

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
  ThreadListLayoutCallback {

  private enum class Visible {
    EMPTY, LOADING, THREAD, ERROR
  }

  @Inject
  lateinit var databaseManager: DatabaseManager

  @Inject
  lateinit var presenter: ThreadPresenter

  @Inject
  lateinit var themeHelper: ThemeHelper

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

  private var newPostsNotification: Snackbar? = null
  private var replyButtonEnabled = false
  private var showingReplyButton = false
  private var refreshedFromSwipe = false
  private var deletingDialog: ProgressDialog? = null
  private var visible: Visible? = null

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

  fun create(callback: ThreadLayoutCallback) {
    this.callback = callback

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
    presenter.setContext(context)
    threadListLayout.setCallbacks(presenter, presenter, presenter, presenter, this)
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
      replyButton.setToolbar(callback.toolbar)
      themeHelper.theme.applyFabColor(replyButton)
    }

    presenter.create(this)
  }

  fun destroy() {
    presenter.unbindLoadable()
    threadListLayout.onDestroy()
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

  fun sendKeyEvent(event: KeyEvent?): Boolean {
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

  fun setPostViewMode(postViewMode: PostViewMode?) {
    threadListLayout.setPostViewMode(postViewMode)
  }

  override fun replyLayoutOpen(open: Boolean) {
    showReplyButton(!open)
  }

  override fun getToolbar(): Toolbar {
    return callback.toolbar
  }

  override fun showImageReencodingWindow(supportsReencode: Boolean) {
    presenter.showImageReencodingWindow(supportsReencode)
  }

  override fun threadBackPressed(): Boolean {
    return callback.threadBackPressed()
  }

  override fun showPosts(
    thread: ChanThread?,
    filter: PostsFilter,
    refreshAfterHideOrRemovePosts: Boolean
  ) {
    if (thread!!.loadable.isLocal) {
      if (replyButton.visibility == View.VISIBLE) {
        replyButton.hide()
      }
    } else {
      if (replyButton.visibility != View.VISIBLE) {
        replyButton.show()
      }
    }

    presenter.updateLoadable(thread.loadable.loadableDownloadingState)
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
      if (error.errorMessage == R.string.thread_load_failed_not_found) {
        presenter.markAllPostsAsSeen()
      }
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

  fun openLinkConfirmed(link: String) {
    if (ChanSettings.openLinkBrowser.get()) {
      AndroidUtils.openLink(link)
    } else {
      AndroidUtils.openLinkInBrowser(context, link, themeHelper.theme)
    }
  }

  override fun openReportView(post: Post) {
    callback.openReportController(post)
  }

  override fun showThread(threadLoadable: Loadable) {
    callback.showThread(threadLoadable)
  }

  override fun showBoard(catalogLoadable: Loadable) {
    callback.showBoard(catalogLoadable)
  }

  override fun showBoardAndSearch(catalogLoadable: Loadable, searchQuery: String?) {
    callback.showBoardAndSearch(catalogLoadable, searchQuery)
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


  override fun showImages(images: List<PostImage>, index: Int, loadable: Loadable, thumbnail: ThumbnailView) {
    if (this.focusedChild != null) {
      val currentFocus = this.focusedChild
      AndroidUtils.hideKeyboard(currentFocus)
      currentFocus.clearFocus()
    }

    callback.showImages(images, index, loadable, thumbnail)
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
      callback.openFilterForType(FilterType.IMAGE, post.firstImage()!!.fileHash)
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

  override fun selectPost(post: Int) {
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

    AlertDialog.Builder(context).setTitle(R.string.delete_confirm)
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

      AlertDialog.Builder(context).setMessage(message).setPositiveButton(R.string.ok, null).show()
    }
  }

  @Suppress("MoveLambdaOutsideParentheses")
  override fun hideThread(post: Post, threadNo: Long, hide: Boolean) {
    // hideRepliesToThisPost is false here because we don't have posts in the catalog mode so there
    // is no point in hiding replies to a thread
    val postHide = PostHide.hidePost(post, true, hide, false)

    databaseManager.runTask(databaseManager.databaseHideManager.addThreadHide(postHide))
    presenter.refreshUI()

    val snackbarStringId = if (hide) R.string.thread_hidden else R.string.thread_removed
    val snackbar = Snackbar.make(this, snackbarStringId, Snackbar.LENGTH_LONG)

    snackbar.isGestureInsetBottomIgnored = true
    snackbar.setAction(R.string.undo, {
      databaseManager.runTask(databaseManager.databaseHideManager.removePostHide(postHide))
      presenter.refreshUI()
    }).show()

    AndroidUtils.fixSnackbarText(context, snackbar)
  }

  override fun hideOrRemovePosts(hide: Boolean, wholeChain: Boolean, posts: Set<Post>, threadNo: Long) {
    val hideList: MutableList<PostHide> = ArrayList()
    for (post in posts) {
      // Do not add the OP post to the hideList since we don't want to hide an OP post
      // while being in a thread (it just doesn't make any sense)
      if (!post.isOP) {
        hideList.add(PostHide.hidePost(post, false, hide, wholeChain))
      }
    }

    databaseManager.runTask(databaseManager.databaseHideManager.addPostsHide(hideList))
    presenter.refreshUI()

    val formattedString = if (hide) {
      AndroidUtils.getQuantityString(R.plurals.post_hidden, posts.size, posts.size)
    } else {
      AndroidUtils.getQuantityString(R.plurals.post_removed, posts.size, posts.size)
    }

    val snackbar = Snackbar.make(this, formattedString, Snackbar.LENGTH_LONG)
    snackbar.isGestureInsetBottomIgnored = true
    snackbar.setAction(R.string.undo) {
      databaseManager.runTask(databaseManager.databaseHideManager.removePostsHide(hideList))
      presenter.refreshUI()
    }.show()

    AndroidUtils.fixSnackbarText(context, snackbar)
  }

  override fun unhideOrUnremovePost(post: Post) {
    databaseManager.runTask(databaseManager.databaseHideManager.removePostHide(PostHide.unhidePost(post)))
    presenter.refreshUI()
  }

  override fun viewRemovedPostsForTheThread(threadPosts: List<Post>, threadNo: Long) {
    removedPostsHelper.showPosts(threadPosts, threadNo)
  }

  override fun onRestoreRemovedPostsClicked(threadLoadable: Loadable, selectedPosts: List<Long>) {
    val postsToRestore: MutableList<PostHide> = ArrayList()
    for (postNo in selectedPosts) {
      postsToRestore.add(PostHide.unhidePost(threadLoadable.site.id(), threadLoadable.boardCode, postNo))
    }

    databaseManager.runTask(databaseManager.databaseHideManager.removePostsHide(postsToRestore))
    presenter.refreshUI()

    val snackbar = Snackbar.make(this, AndroidUtils.getString(R.string.restored_n_posts, postsToRestore.size), Snackbar.LENGTH_LONG)
    snackbar.isGestureInsetBottomIgnored = true
    snackbar.show()

    AndroidUtils.fixSnackbarText(context, snackbar)
  }

  override fun onPostUpdated(post: Post) {
    threadListLayout.onPostUpdated(post)
  }

  override fun presentController(floatingListMenuController: FloatingListMenuController, animate: Boolean) {
    callback.presentController(floatingListMenuController, animate)
  }

  override fun showNewPostsNotification(show: Boolean, more: Int) {
    if (!show) {
      dismissSnackbar()
      return
    }

    if (!threadListLayout.scrolledToBottom() && BackgroundUtils.isInForeground()) {
      val text = AndroidUtils.getQuantityString(R.plurals.thread_new_posts, more, more)
      dismissSnackbar()

      newPostsNotification = Snackbar.make(this, text, Snackbar.LENGTH_LONG)
      newPostsNotification!!.isGestureInsetBottomIgnored = true
      newPostsNotification!!.setAction(R.string.thread_new_posts_goto) {
        presenter.onNewPostsViewClicked()
        dismissSnackbar()
      }.show()

      AndroidUtils.fixSnackbarText(context, newPostsNotification)
      return
    }

    dismissSnackbar()
  }

  private fun dismissSnackbar() {
    if (newPostsNotification != null) {
      newPostsNotification!!.dismiss()
      newPostsNotification = null
    }
  }

  override fun getLoadable(): Loadable? {
    return presenter.loadable
  }

  override fun onDetachedFromWindow() {
    dismissSnackbar()
    super.onDetachedFromWindow()
  }

  override fun showImageReencodingWindow(loadable: Loadable, supportsReencode: Boolean) {
    if (this.focusedChild != null) {
      val currentFocus = this.focusedChild
      AndroidUtils.hideKeyboard(currentFocus)
      currentFocus.clearFocus()
    }

    imageReencodingHelper.showController(loadable, supportsReencode)
  }

  fun getThumbnail(postImage: PostImage?): ThumbnailView {
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

          if (loadable == null || loadable?.isThreadMode == true) {
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

          // TODO: cleanup
          if (refreshedFromSwipe) {
            refreshedFromSwipe = false
            view.visibility = View.GONE
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
    callback.presentController(controller)
  }

  override fun presentReencodeOptionsController(controller: Controller) {
    callback.presentController(controller)
  }

  override fun onImageOptionsApplied(reply: Reply, filenameRemoved: Boolean) {
    threadListLayout.onImageOptionsApplied(reply, filenameRemoved)
  }

  override fun onImageOptionsComplete() {
    threadListLayout.onImageOptionsComplete()
  }

  override fun presentRemovedPostsController(controller: Controller) {
    callback.presentController(controller)
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
    val toolbar: Toolbar

    fun showThread(threadLoadable: Loadable)
    fun showBoard(catalogLoadable: Loadable)
    fun showBoardAndSearch(catalogLoadable: Loadable, searchQuery: String?)
    fun showImages(images: @JvmSuppressWildcards List<PostImage>, index: Int, loadable: Loadable, thumbnail: ThumbnailView)
    fun showAlbum(images: @JvmSuppressWildcards List<PostImage>, index: Int)
    fun onShowPosts()
    fun presentController(controller: Controller)
    fun presentController(controller: Controller, animate: Boolean)
    fun openReportController(post: Post)
    fun hideSwipeRefreshLayout()
    fun openFilterForType(type: FilterType?, filterText: String?)
    fun threadBackPressed(): Boolean
  }

}