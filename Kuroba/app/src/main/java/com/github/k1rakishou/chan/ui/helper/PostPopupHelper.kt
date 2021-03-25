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
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.controller.popup.BasePostPopupController
import com.github.k1rakishou.chan.ui.controller.popup.BasePostPopupController.Companion.clearScrollPositionCache
import com.github.k1rakishou.chan.ui.controller.popup.PostPopupController
import com.github.k1rakishou.chan.ui.view.post_thumbnail.ThumbnailView
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.PostIndexed
import java.util.*

class PostPopupHelper(
  private val context: Context,
  private val presenter: ThreadPresenter,
  private val chanThreadManager: ChanThreadManager,
  private val callback: PostPopupHelperCallback
) {
  private val dataQueue: MutableList<PostPopupData> = ArrayList()
  private var presentingPostRepliesController: BasePostPopupController<out PostPopupData>? = null

  val isOpen: Boolean
    get() = presentingPostRepliesController != null && presentingPostRepliesController!!.alive

  fun getDisplayingPostDescriptors(): List<PostDescriptor> {
    return presentingPostRepliesController?.postRepliesData ?: emptyList()
  }

  fun showRepliesPopup(
    threadDescriptor: ThreadDescriptor,
    postAdditionalData: PostCellData.PostAdditionalData,
    postDescriptor: PostDescriptor?,
    posts: List<ChanPost>
  ) {
    val data = PostPopupController.PostRepliesPopupData(
      threadDescriptor,
      postAdditionalData,
      postDescriptor,
      indexPosts(posts)
    )

    dataQueue.add(data)

    if (dataQueue.size == 1) {
      present(PostPopupController(context, this, presenter))
    }

    presentingPostRepliesController?.initialDisplayData(threadDescriptor, data)
  }

  fun showSearchPopup(chanDescriptor: ChanDescriptor) {

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

    return dataQueue[dataQueue.size - 1]
  }

  suspend fun onPostUpdated(updatedPost: ChanPost) {
    BackgroundUtils.ensureMainThread()
    presentingPostRepliesController?.onPostUpdated(updatedPost)
  }

  fun pop() {
    if (dataQueue.size > 0) {
      dataQueue.removeAt(dataQueue.size - 1)
    }

    if (dataQueue.size <= 0) {
      dismiss()
      return
    }

    val repliesData = dataQueue[dataQueue.size - 1]
    checkNotNull(repliesData.descriptor) { "Descriptor cannot be null" }

    presentingPostRepliesController?.initialDisplayData(
      repliesData.descriptor,
      repliesData
    )
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
    presenter.highlightPost(postDescriptor)
    presenter.scrollToPost(postDescriptor, true)
  }

  private fun dismiss() {
    clearScrollPositionCache()

    presentingPostRepliesController?.stopPresenting()
    presentingPostRepliesController = null
  }

  private fun present(controller: BasePostPopupController<out PostPopupData>) {
    if (presentingPostRepliesController == null) {
      presentingPostRepliesController = controller
      callback.presentRepliesController(presentingPostRepliesController!!)
    }
  }

  interface PostPopupData {
    val descriptor: ChanDescriptor
    val postAdditionalData: PostCellData.PostAdditionalData
    val forPostWithDescriptor: PostDescriptor?
    val posts: List<PostIndexed>
  }

  interface PostPopupHelperCallback {
    fun presentRepliesController(controller: Controller)
  }
}