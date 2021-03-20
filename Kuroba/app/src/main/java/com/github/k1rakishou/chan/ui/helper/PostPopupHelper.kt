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
import com.github.k1rakishou.chan.ui.controller.PostRepliesController
import com.github.k1rakishou.chan.ui.controller.PostRepliesController.Companion.clearScrollPositionCache
import com.github.k1rakishou.chan.ui.view.post_thumbnail.ThumbnailView
import com.github.k1rakishou.chan.utils.BackgroundUtils
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
  private val dataQueue: MutableList<RepliesData> = ArrayList()
  private var presentingPostRepliesController: PostRepliesController? = null

  val isOpen: Boolean
    get() = presentingPostRepliesController != null && presentingPostRepliesController!!.alive


  fun getDisplayingPostDescriptors(): List<PostDescriptor> {
    return presentingPostRepliesController?.postRepliesData ?: emptyList()
  }

  fun showPosts(
    threadDescriptor: ThreadDescriptor,
    postAdditionalData: PostCellData.PostAdditionalData,
    postDescriptor: PostDescriptor?,
    posts: List<ChanPost>
  ) {
    val data = RepliesData(threadDescriptor, postAdditionalData, postDescriptor, indexPosts(posts))
    dataQueue.add(data)

    if (dataQueue.size == 1) {
      present()
    }

    presentingPostRepliesController?.setPostRepliesData(threadDescriptor, data)
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

  fun topOrNull(): RepliesData? {
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
    checkNotNull(repliesData.threadDescriptor) { "Thread descriptor cannot be null" }

    presentingPostRepliesController?.setPostRepliesData(
      repliesData.threadDescriptor,
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

  private fun present() {
    if (presentingPostRepliesController == null) {
      presentingPostRepliesController = PostRepliesController(context, this, presenter)
      callback.presentRepliesController(presentingPostRepliesController!!)
    }
  }

  class RepliesData(
    val threadDescriptor: ThreadDescriptor?,
    val postAdditionalData: PostCellData.PostAdditionalData,
    val forPostWithDescriptor: PostDescriptor?,
    val posts: List<PostIndexed>
  )

  interface PostPopupHelperCallback {
    fun presentRepliesController(controller: Controller)
  }
}