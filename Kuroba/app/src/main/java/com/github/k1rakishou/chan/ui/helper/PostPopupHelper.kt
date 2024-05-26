package com.github.k1rakishou.chan.ui.helper

import android.content.Context
import androidx.compose.runtime.Immutable
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.popup.BasePostPopupController
import com.github.k1rakishou.chan.ui.controller.popup.PostRepliesPopupController
import com.github.k1rakishou.chan.ui.controller.popup.PostSearchPopupController
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import dagger.Lazy

class PostPopupHelper(
  private val context: Context,
  private val postCellCallback: PostCellInterface.PostCellCallback,
  private val chanThreadManagerLazy: Lazy<ChanThreadManager>,
  private val callback: PostPopupHelperCallback
) {
  private val dataQueue: MutableList<PostPopupData> = ArrayList()
  private var presentingPostRepliesController: BasePostPopupController<out PostPopupData>? = null

  private val chanThreadManager: ChanThreadManager
    get() = chanThreadManagerLazy.get()

  val isOpen: Boolean
    get() = presentingPostRepliesController != null && presentingPostRepliesController!!.alive
  val displayingAnything: Boolean
    get() = dataQueue.isNotEmpty()

  fun getDisplayingPostDescriptors(): List<PostDescriptor> {
    return presentingPostRepliesController?.getDisplayingPostDescriptors() ?: emptyList()
  }

  fun showRepliesPopup(
    threadDescriptor: ThreadDescriptor,
    popupControllerType: PostCellData.PopupControllerType,
    postDescriptor: PostDescriptor,
    posts: List<ChanPost>
  ) {
    val data = PostRepliesPopupController.PostRepliesPopupData(
      descriptor = threadDescriptor,
      forPostWithDescriptor = postDescriptor,
      popupControllerType = popupControllerType,
      posts = posts
    )

    val prevPostViewMode = dataQueue.lastOrNull()?.popupControllerType
    dataQueue.add(data)

    if (dataQueue.size == 1 || prevPostViewMode != popupControllerType) {
      present(PostRepliesPopupController(context, this, postCellCallback))
    }

    presentingPostRepliesController?.displayData(threadDescriptor, data)
  }

  fun showSearchPopup(chanDescriptor: ChanDescriptor, searchQuery: String? = null) {
    val popupControllerType = PostCellData.PopupControllerType.Search

    val data = PostSearchPopupController.PostSearchPopupData(
      chanDescriptor,
      popupControllerType
    )

    val prevPostViewMode = dataQueue.lastOrNull()?.popupControllerType

    if (searchQuery == null || prevPostViewMode != popupControllerType) {
      dataQueue.add(data)
    }

    if (dataQueue.size == 1 || prevPostViewMode != popupControllerType) {
      present(PostSearchPopupController(context, this, postCellCallback, searchQuery))
    }

    presentingPostRepliesController?.displayData(chanDescriptor, data)
  }

  fun topOrNull(): PostPopupData? {
    if (dataQueue.isEmpty()) {
      return null
    }

    return dataQueue.getOrNull(dataQueue.size - 1)
  }

  fun resetCachedPostData(postDescriptor: PostDescriptor) {
    presentingPostRepliesController?.resetCachedPostData(listOf(postDescriptor))
  }

  fun resetCachedPostData(postDescriptors: Collection<PostDescriptor>) {
    presentingPostRepliesController?.resetCachedPostData(postDescriptors)
  }

  suspend fun onPostsWithDescriptorsUpdated(updatedPostDescriptors: Collection<PostDescriptor>) {
    BackgroundUtils.ensureMainThread()

    val updatedPosts = chanThreadManager.getPosts(updatedPostDescriptors)
    if (updatedPosts.isEmpty()) {
      return
    }

    onPostsUpdated(updatedPosts)
  }

  suspend fun updateAllPosts(chanDescriptor: ChanDescriptor) {
    BackgroundUtils.ensureMainThread()
    presentingPostRepliesController?.updateAllPosts(chanDescriptor)
  }

  suspend fun onPostsUpdated(updatedPosts: List<ChanPost>) {
    BackgroundUtils.ensureMainThread()
    presentingPostRepliesController?.onPostsUpdated(updatedPosts)
  }

  fun pop() {
    if (dataQueue.size > 0) {
      dataQueue.removeAt(dataQueue.size - 1)
    }

    if (dataQueue.size <= 0) {
      dismiss()
      return
    }

    val postRepliesController = presentingPostRepliesController
      ?: return

    val repliesData = dataQueue[dataQueue.size - 1]
    checkNotNull(repliesData.descriptor) { "Descriptor cannot be null" }

    val needPresentController = when (postRepliesController.postPopupType) {
      BasePostPopupController.PostPopupType.Replies -> isNotReplyPostViewMode(repliesData)
      BasePostPopupController.PostPopupType.Search -> isNotSearchPostViewMode(repliesData)
    }

    if (needPresentController) {
      when (repliesData.popupControllerType) {
        PostCellData.PopupControllerType.PostSelection,
        PostCellData.PopupControllerType.Normal -> {
          throw IllegalArgumentException("Invalid postViewMode: ${repliesData.popupControllerType}")
        }
        PostCellData.PopupControllerType.RepliesPopup,
        PostCellData.PopupControllerType.ExternalPostsPopup,
        PostCellData.PopupControllerType.MediaViewerPostsPopup -> {
          present(PostRepliesPopupController(context, this, postCellCallback))
        }
        PostCellData.PopupControllerType.Search -> {
          present(PostSearchPopupController(context, this, postCellCallback))
        }
      }
    }

    presentingPostRepliesController?.displayData(
      repliesData.descriptor,
      repliesData
    )
  }

  private fun isNotSearchPostViewMode(repliesData: PostPopupData): Boolean {
    return repliesData.popupControllerType != PostCellData.PopupControllerType.Search
  }

  private fun isNotReplyPostViewMode(repliesData: PostPopupData): Boolean {
    return repliesData.popupControllerType != PostCellData.PopupControllerType.RepliesPopup
      && repliesData.popupControllerType != PostCellData.PopupControllerType.ExternalPostsPopup
      && repliesData.popupControllerType != PostCellData.PopupControllerType.MediaViewerPostsPopup
  }

  fun popAll() {
    dataQueue.clear()
    dismiss()
  }

  fun scrollTo(displayPosition: Int) {
    presentingPostRepliesController?.scrollTo(displayPosition)
  }

  fun getThumbnail(postImage: ChanPostImage?): ThumbnailView? {
    if (postImage == null) {
      return null
    }

    return presentingPostRepliesController?.getThumbnail(postImage)
  }

  fun postClicked(postDescriptor: PostDescriptor) {
    popAll()
    callback.highlightPost(postDescriptor, blink = true)
    callback.scrollToPost(postDescriptor)
  }

  private fun dismiss() {
    presentingPostRepliesController?.stopPresenting()
    presentingPostRepliesController = null
  }

  private fun present(controller: BasePostPopupController<out PostPopupData>) {
    if (presentingPostRepliesController != null) {
      presentingPostRepliesController?.stopPresenting()
      presentingPostRepliesController = null
    }

    if (presentingPostRepliesController == null) {
      presentingPostRepliesController = controller
      callback.presentRepliesController(presentingPostRepliesController!!)
    }
  }

  fun onImageIsAboutToShowUp() {
    presentingPostRepliesController?.onImageIsAboutToShowUp()
  }

  @Immutable
  interface PostPopupData {
    val descriptor: ChanDescriptor
    val popupControllerType: PostCellData.PopupControllerType
  }

  interface PostPopupHelperCallback {
    fun presentRepliesController(controller: Controller)
    fun highlightPost(postDescriptor: PostDescriptor?, blink: Boolean)
    fun scrollToPost(postDescriptor: PostDescriptor)
  }
}