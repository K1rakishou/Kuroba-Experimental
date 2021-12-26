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
package com.github.k1rakishou.chan.ui.helper

import android.content.Context
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.controller.popup.BasePostPopupController
import com.github.k1rakishou.chan.ui.controller.popup.PostRepliesPopupController
import com.github.k1rakishou.chan.ui.controller.popup.PostSearchPopupController
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.PostIndexed
import dagger.Lazy
import java.util.*

class PostPopupHelper(
  private val context: Context,
  private val postCellCallback: PostCellInterface.PostCellCallback,
  private val _chanThreadManager: Lazy<ChanThreadManager>,
  private val callback: PostPopupHelperCallback
) {
  private val dataQueue: MutableList<PostPopupData> = ArrayList()
  private var presentingPostRepliesController: BasePostPopupController<out PostPopupData>? = null

  private val chanThreadManager: ChanThreadManager
    get() = _chanThreadManager.get()

  val isOpen: Boolean
    get() = presentingPostRepliesController != null && presentingPostRepliesController!!.alive
  val displayingAnything: Boolean
    get() = dataQueue.isNotEmpty()

  fun getDisplayingPostDescriptors(): List<PostDescriptor> {
    return presentingPostRepliesController?.getDisplayingPostDescriptors() ?: emptyList()
  }

  fun showRepliesPopup(
    threadDescriptor: ThreadDescriptor,
    postViewMode: PostCellData.PostViewMode,
    postDescriptor: PostDescriptor,
    posts: List<ChanPost>
  ) {
    val data = PostRepliesPopupController.PostRepliesPopupData(
      descriptor = threadDescriptor,
      postViewMode = postViewMode,
      forPostWithDescriptor = postDescriptor,
      posts = indexPosts(posts)
    )

    val prevPostViewMode = dataQueue.lastOrNull()?.postViewMode
    dataQueue.add(data)

    if (dataQueue.size == 1 || prevPostViewMode != postViewMode) {
      present(PostRepliesPopupController(context, this, postCellCallback))
    }

    presentingPostRepliesController?.displayData(threadDescriptor, data)
  }

  fun showSearchPopup(chanDescriptor: ChanDescriptor, searchQuery: String? = null) {
    val postViewMode = PostCellData.PostViewMode.Search

    val data = PostSearchPopupController.PostSearchPopupData(
      chanDescriptor,
      postViewMode
    )

    val prevPostViewMode = dataQueue.lastOrNull()?.postViewMode

    if (searchQuery == null || prevPostViewMode != postViewMode) {
      dataQueue.add(data)
    }

    if (dataQueue.size == 1 || prevPostViewMode != postViewMode) {
      present(PostSearchPopupController(context, this, postCellCallback, searchQuery))
    }

    presentingPostRepliesController?.displayData(chanDescriptor, data)
  }

  private fun indexPosts(posts: List<ChanPost>): List<PostIndexed> {
    if (posts.isEmpty()) {
      return emptyList()
    }

    val postIndexedList: MutableList<PostIndexed> = ArrayList()
    val threadDescriptor = posts[0].postDescriptor.threadDescriptor()

    chanThreadManager.iteratePostIndexes(
      threadDescriptor,
      posts,
      ChanPost::postDescriptor
    ) { chanPost: ChanPost, postIndex: Int ->
      postIndexedList.add(PostIndexed(chanPost, postIndex))
      Unit
    }

    return postIndexedList
  }

  fun topOrNull(): PostPopupData? {
    if (dataQueue.isEmpty()) {
      return null
    }

    return dataQueue.getOrNull(dataQueue.size - 1)
  }

  fun resetCachedPostData(postDescriptor: PostDescriptor) {
    presentingPostRepliesController?.resetCachedPostData(postDescriptor)
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
      when (repliesData.postViewMode) {
        PostCellData.PostViewMode.PostSelection,
        PostCellData.PostViewMode.Normal -> {
          throw IllegalArgumentException("Invalid postViewMode: ${repliesData.postViewMode}")
        }
        PostCellData.PostViewMode.RepliesPopup,
        PostCellData.PostViewMode.ExternalPostsPopup,
        PostCellData.PostViewMode.MediaViewerPostsPopup -> {
          present(PostRepliesPopupController(context, this, postCellCallback))
        }
        PostCellData.PostViewMode.Search -> {
          present(PostSearchPopupController(context, this, postCellCallback))
        }
      }.exhaustive
    }

    presentingPostRepliesController?.displayData(
      repliesData.descriptor,
      repliesData
    )
  }

  private fun isNotSearchPostViewMode(repliesData: PostPopupData): Boolean {
    return repliesData.postViewMode != PostCellData.PostViewMode.Search
  }

  private fun isNotReplyPostViewMode(repliesData: PostPopupData): Boolean {
    return repliesData.postViewMode != PostCellData.PostViewMode.RepliesPopup
      && repliesData.postViewMode != PostCellData.PostViewMode.ExternalPostsPopup
      && repliesData.postViewMode != PostCellData.PostViewMode.MediaViewerPostsPopup
  }

  fun popAll() {
    dataQueue.clear()
    dismiss()
  }

  fun scrollTo(displayPosition: Int, smooth: Boolean) {
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
    callback.scrollToPost(postDescriptor, smooth = true)
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

  interface PostPopupData {
    val descriptor: ChanDescriptor
    val postViewMode: PostCellData.PostViewMode
  }

  interface PostPopupHelperCallback {
    fun presentRepliesController(controller: Controller)
    fun highlightPost(postDescriptor: PostDescriptor?, blink: Boolean)
    fun scrollToPost(postDescriptor: PostDescriptor, smooth: Boolean)
  }
}