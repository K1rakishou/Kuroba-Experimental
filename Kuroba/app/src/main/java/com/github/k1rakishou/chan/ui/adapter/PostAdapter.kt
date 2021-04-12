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
package com.github.k1rakishou.chan.ui.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.k1rakishou.ChanSettings.BoardPostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.ui.cell.GenericPostCell
import com.github.k1rakishou.chan.ui.cell.PostCell
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.ThreadCellData
import com.github.k1rakishou.chan.ui.cell.ThreadStatusCell
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.PostIndexed
import java.util.*
import javax.inject.Inject

class PostAdapter(
  recyclerView: RecyclerView,
  postAdapterCallback: PostAdapterCallback,
  postCellCallback: PostCellCallback,
  statusCellCallback: ThreadStatusCell.Callback
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  @Inject
  lateinit var chanThreadViewableInfoManager: ChanThreadViewableInfoManager
  @Inject
  lateinit var postFilterManager: PostFilterManager
  @Inject
  lateinit var themeEngine: ThemeEngine

  private val postAdapterCallback: PostAdapterCallback
  private val postCellCallback: PostCellCallback
  private val recyclerView: RecyclerView
  private val statusCellCallback: ThreadStatusCell.Callback
  private val threadCellData: ThreadCellData

  /**
   * A hack for OnDemandContentLoader see comments in [onViewRecycled]
   */
  private val updatingPosts: MutableSet<Long> = HashSet(64)

  val isErrorShown: Boolean
    get() = threadCellData.error != null

  val displayList: List<PostDescriptor>
    get() {
      if (threadCellData.isEmpty()) {
        return ArrayList()
      }

      val size = Math.min(16, threadCellData.postsCount())
      val postDescriptors: MutableList<PostDescriptor> = ArrayList(size)

      for (postCellData in threadCellData) {
        postDescriptors.add(postCellData.postDescriptor)
      }

      return postDescriptors
    }

  val lastPostNo: Long
    get() {
      val postCellData = threadCellData.getLastPostCellDataOrNull()
        ?: return -1

      return postCellData.postNo
    }

  init {
    AppModuleAndroidUtils.extractActivityComponent(recyclerView.context)
      .inject(this)

    this.recyclerView = recyclerView
    this.postAdapterCallback = postAdapterCallback
    this.postCellCallback = postCellCallback
    this.statusCellCallback = statusCellCallback

    threadCellData = ThreadCellData(
      chanThreadViewableInfoManager,
      postFilterManager,
      themeEngine.chanTheme
    )

    themeEngine.preloadAttributeResource(
      recyclerView.context,
      android.R.attr.selectableItemBackgroundBorderless
    )

    themeEngine.preloadAttributeResource(
      recyclerView.context,
      android.R.attr.selectableItemBackground
    )

    setHasStableIds(true)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    val inflateContext = parent.context

    when (viewType) {
      TYPE_POST,
      TYPE_POST_STUB -> {
        return PostViewHolder(GenericPostCell(inflateContext))
      }
      TYPE_LAST_SEEN -> {
        return LastSeenViewHolder(
          themeEngine,
          AppModuleAndroidUtils.inflate(inflateContext, R.layout.cell_post_last_seen, parent, false)
        )
      }
      TYPE_STATUS -> {
        val statusCell = AppModuleAndroidUtils.inflate(
          inflateContext,
          R.layout.cell_thread_status,
          parent,
          false
        ) as ThreadStatusCell

        val statusViewHolder = StatusViewHolder(statusCell)
        statusCell.setCallback(statusCellCallback)
        statusCell.setError(threadCellData.error)
        return statusViewHolder
      }
      else -> throw IllegalStateException()
    }
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    when (getItemViewType(position)) {
      TYPE_POST, TYPE_POST_STUB -> {
        checkNotNull(threadCellData.chanDescriptor) { "chanDescriptor cannot be null" }

        val postViewHolder = holder as PostViewHolder
        val postCellData = threadCellData.getPostCellData(position)
        val postCell = postViewHolder.itemView as GenericPostCell
        postCell.setPost(postCellData)
      }

      TYPE_STATUS -> (holder.itemView as ThreadStatusCell).update()
      TYPE_LAST_SEEN -> (holder as LastSeenViewHolder).updateLabelColor()
    }
  }

  override fun getItemCount(): Int {
    return threadCellData.postsCount()
  }

  override fun getItemViewType(position: Int): Int {
    if (position == threadCellData.lastSeenIndicatorPosition) {
      return TYPE_LAST_SEEN
    } else if (showStatusView() && position == itemCount - 1) {
      return TYPE_STATUS
    } else {
      val postCellData = threadCellData.getPostCellData(position)
      if (postCellData.stub) {
        return TYPE_POST_STUB
      } else {
        return TYPE_POST
      }
    }
  }

  override fun getItemId(position: Int): Long {
    when (getItemViewType(position)) {
      TYPE_STATUS -> {
        return -1
      }
      TYPE_LAST_SEEN -> {
        return -2
      }
      else -> {
        val postCellData = threadCellData.getPostCellData(position)
        val repliesFromCount = postCellData.repliesFromCount
        val compactValue = if (postCellData.compact) 1L else 0L

        return ((repliesFromCount.toLong() shl 32)
          + postCellData.postNo
          + postCellData.postSubNo
          + compactValue)
      }
    }
  }

  override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
    // this is a hack to make sure text is selectable
    super.onViewAttachedToWindow(holder)

    if (holder.itemView is PostCell) {
      val cell = holder.itemView as PostCell
      cell.findViewById<View>(R.id.comment).isEnabled = false
      cell.findViewById<View>(R.id.comment).isEnabled = true
    }
  }

  /**
   * Do not use onViewAttachedToWindow/onViewDetachedFromWindow in PostCell/CardPostCell etc to
   * bind/unbind posts because it's really bad and will cause a shit-ton of problems. We should
   * only use onViewRecycled() instead (because onViewAttachedToWindow/onViewDetachedFromWindow
   * and onViewRecycled() have different lifecycles). So by using onViewAttachedToWindow/
   * onViewDetachedFromWindow we may end up in a situation where we unbind a post
   * (null out the callbacks) but in reality the post (view) is still alive in internal RecycleView
   * cache so the next time recycler decides to update the view it will either throw a NPE or will
   * just show an empty view. Using onViewRecycled to unbind posts is the correct way to handle
   * this issue.
   */
  override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
    if (holder.itemView is GenericPostCell) {
      val post = (holder.itemView as GenericPostCell).getPost()
        ?: return

      val postNo = post.postNo()
      val isActuallyRecycling = !updatingPosts.remove(postNo)

      /**
       * Hack! (kinda)
       *
       * We have some managers that we want to release all their resources once a post is
       * recycled. However, onViewRecycled may not only get called when the view is offscreen
       * and RecyclerView decides to recycle it, but also when we call notifyItemChanged
       * [.updatePost]. So the point of this hack is to check whether onViewRecycled was
       * called because of us calling notifyItemChanged or it was the RecyclerView that
       * actually decided to recycle it. For that we put a post id into a HashSet before
       * calling notifyItemChanged and then checking, right here, whether the current post's
       * id exists in the HashSet. If it exists in the HashSet that means that it was
       * (most likely) us calling notifyItemChanged that triggered onViewRecycled call.
       */

      (holder.itemView as GenericPostCell).onPostRecycled(isActuallyRecycling)
    }
  }

  suspend fun setThread(
    chanDescriptor: ChanDescriptor,
    chanTheme: ChanTheme,
    postIndexedList: List<PostIndexed>
  ) {
    BackgroundUtils.ensureMainThread()
    threadCellData.updateThreadData(postCellCallback, chanDescriptor, postIndexedList, chanTheme)
    showError(null)
    notifyDataSetChanged()

    Logger.d(TAG, "setThread() notifyDataSetChanged called, postIndexedList.size=" + postIndexedList.size)
  }

  fun cleanup() {
    updatingPosts.clear()
    threadCellData.cleanup()
    notifyDataSetChanged()
  }

  fun showError(error: String?) {
    threadCellData.error = error

    if (showStatusView()) {
      val childCount = recyclerView.childCount
      for (i in 0 until childCount) {
        val child = recyclerView.getChildAt(i)
        if (child is ThreadStatusCell) {
          child.setError(error)
          child.update()
        }
      }
    }
  }

  fun highlightPosts(postDescriptors: Set<PostDescriptor>) {
    threadCellData.highlightPosts(postDescriptors)
    notifyDataSetChanged()
  }

  fun highlightPostId(postId: String?) {
    threadCellData.highlightPostsByPostId(postId)
    notifyDataSetChanged()
  }

  fun highlightPostTripcode(tripcode: CharSequence?) {
    threadCellData.highlightPostsByTripcode(tripcode)
    notifyDataSetChanged()
  }

  fun selectPosts(postDescriptors: Set<PostDescriptor>) {
    threadCellData.selectPosts(postDescriptors)
    notifyDataSetChanged()
  }

  fun setBoardPostViewMode(boardPostViewMode: BoardPostViewMode) {
    threadCellData.setBoardPostViewMode(boardPostViewMode)
    notifyDataSetChanged()
  }

  fun setCompact(compact: Boolean) {
    threadCellData.setCompact(compact)
    notifyDataSetChanged()
  }

  fun getScrollPosition(displayPosition: Int): Int {
    return threadCellData.getScrollPosition(displayPosition)
  }

  private fun showStatusView(): Boolean {
    val chanDescriptor = postAdapterCallback.currentChanDescriptor
    // the chanDescriptor can be null while this adapter is used between cleanup and the removal
    // of the recyclerview from the view hierarchy, although it's rare.
    return chanDescriptor != null
  }

  suspend fun updatePost(updatedPost: ChanPost) {
    BackgroundUtils.ensureMainThread()

    val postIndex = threadCellData.getPostCellDataIndexToUpdate(updatedPost.postDescriptor)
      ?: return

    if (!threadCellData.onPostUpdated(updatedPost)) {
      return
    }

    updatingPosts.add(updatedPost.postNo())
    notifyItemChanged(postIndex)
  }

  fun getPostNo(itemPosition: Int): Long {
    if (itemPosition < 0) {
      return -1L
    }

    var correctedPosition = threadCellData.getPostPosition(itemPosition)
    if (correctedPosition < 0) {
      return -1L
    }

    var itemViewType = getItemViewTypeSafe(correctedPosition)
    if (itemViewType == TYPE_STATUS) {
      correctedPosition = threadCellData.getPostPosition(correctedPosition - 1)
      itemViewType = getItemViewTypeSafe(correctedPosition)
    }

    if (itemViewType < 0) {
      return -1L
    }

    if (itemViewType != TYPE_POST && itemViewType != TYPE_POST_STUB) {
      return -1L
    }

    if (correctedPosition < 0 || correctedPosition >= threadCellData.postsCount()) {
      return -1L
    }

    val postCellData = threadCellData.getPostCellData(correctedPosition)
    return postCellData.postNo
  }

  private fun getItemViewTypeSafe(position: Int): Int {
    if (position == threadCellData.lastSeenIndicatorPosition) {
      return TYPE_LAST_SEEN
    }

    if (showStatusView() && position == itemCount - 1) {
      return TYPE_STATUS
    }

    val correctedPosition = threadCellData.getPostPosition(position)
    if (correctedPosition < 0 || correctedPosition > threadCellData.postsCount()) {
      return -1
    }

    val postCellData = threadCellData.getPostCellData(correctedPosition)

    if (postFilterManager.getFilterStub(postCellData.postDescriptor)) {
      return TYPE_POST_STUB
    } else {
      return TYPE_POST
    }
  }

  class PostViewHolder(genericPostCell: GenericPostCell) : RecyclerView.ViewHolder(genericPostCell)

  class StatusViewHolder(threadStatusCell: ThreadStatusCell) : RecyclerView.ViewHolder(threadStatusCell)

  class LastSeenViewHolder(
    private val themeEngine: ThemeEngine,
    itemView: View
  ) : RecyclerView.ViewHolder(itemView) {

    init {
      updateLabelColor()
    }

    fun updateLabelColor() {
      itemView.setBackgroundColor(themeEngine.chanTheme.accentColor)
    }

  }

  interface PostAdapterCallback {
    val currentChanDescriptor: ChanDescriptor?
  }

  companion object {
    private const val TAG = "PostAdapter"

    //we don't recycle POST cells because of layout changes between cell contents
    const val TYPE_POST = 0
    private const val TYPE_STATUS = 1
    private const val TYPE_POST_STUB = 2
    private const val TYPE_LAST_SEEN = 3
  }

}