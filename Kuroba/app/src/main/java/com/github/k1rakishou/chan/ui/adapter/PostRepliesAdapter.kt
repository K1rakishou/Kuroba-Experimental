package com.github.k1rakishou.chan.ui.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.PostFilterHighlightManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.ui.cell.GenericPostCell
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.cell.ThreadCellData
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.PostIndexed
import dagger.Lazy

class PostRepliesAdapter(
  private val recyclerView: RecyclerView,
  private val postViewMode: PostCellData.PostViewMode,
  private val postCellCallback: PostCellInterface.PostCellCallback,
  val chanDescriptor: ChanDescriptor,
  private val clickedPostDescriptor: PostDescriptor?,
  _chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>,
  _chanThreadManager: Lazy<ChanThreadManager>,
  _postFilterManager: Lazy<PostFilterManager>,
  _savedReplyManager: Lazy<SavedReplyManager>,
  _postFilterHighlightManager: Lazy<PostFilterHighlightManager>,
  _postHideManager: Lazy<PostHideManager>,
  initialTheme: ChanTheme
) : RecyclerView.Adapter<PostRepliesAdapter.ReplyViewHolder>() {

  private val threadCellData = ThreadCellData(
    _chanThreadViewableInfoManager = _chanThreadViewableInfoManager,
    _chanThreadManager = _chanThreadManager,
    _postFilterManager = _postFilterManager,
    _savedReplyManager = _savedReplyManager,
    _postFilterHighlightManager = _postFilterHighlightManager,
    _postHideManager = _postHideManager,
    initialTheme = initialTheme
  )

  init {
    threadCellData.postViewMode = postViewMode
    threadCellData.defaultIsCompact = false
    threadCellData.defaultBoardPostViewMode = ChanSettings.BoardPostViewMode.LIST
    threadCellData.defaultMarkedNo = clickedPostDescriptor?.postNo
    threadCellData.defaultShowDividerFunc = { postIndex: Int, totalPostsCount: Int -> postIndex < totalPostsCount - 1 }
  }

  fun displayedPosts(): List<PostDescriptor> {
    return threadCellData.map { it.post.postDescriptor }
  }

  fun cleanup() {
    val childCount = recyclerView.childCount
    for (i in 0 until childCount) {
      val child = recyclerView.getChildAt(i)
      if (child is GenericPostCell) {
        child.onPostRecycled(isActuallyRecycling = true)
      }
    }

    threadCellData.cleanup()
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReplyViewHolder {
    return ReplyViewHolder(GenericPostCell(parent.context))
  }

  override fun onBindViewHolder(holder: ReplyViewHolder, position: Int) {
    holder.onBind(threadCellData.getPostCellData(position))
  }

  override fun getItemViewType(position: Int): Int {
    return POST_REPLY_VIEW_TYPE
  }

  override fun getItemCount(): Int {
    return threadCellData.postsCount()
  }

  override fun getItemId(position: Int): Long {
    return threadCellData.getPostCellData(position).hashForAdapter()
  }

  override fun onViewRecycled(holder: ReplyViewHolder) {
    if (holder.itemView is GenericPostCell) {
      holder.itemView.onPostRecycled(true)
    }
  }

  suspend fun setOrUpdateData(
    postCellDataWidthNoPaddings: Int,
    postIndexedList: List<PostIndexed>,
    theme: ChanTheme
  ) {
    threadCellData.updateThreadData(
      postCellCallback,
      chanDescriptor,
      postIndexedList,
      postCellDataWidthNoPaddings,
      theme
    )

    notifyDataSetChanged()
  }

  fun setSearchQuery(searchQuery: PostCellData.SearchQuery) {
    threadCellData.setSearchQuery(searchQuery)

    notifyDataSetChanged()
  }

  fun refresh() {
    notifyDataSetChanged()
  }

  fun resetCachedPostData(postDescriptors: Collection<PostDescriptor>) {
    threadCellData.resetCachedPostData(postDescriptors)
  }

  suspend fun updatePosts(updatedPosts: List<ChanPost>) {
    val postDescriptors = updatedPosts.map { post -> post.postDescriptor }

    val postIndexRange = threadCellData.getPostCellDataIndexes(postDescriptors)
    if (postIndexRange == null) {
      return
    }

    if (!threadCellData.onPostsUpdated(updatedPosts)) {
      return
    }

    if (postIndexRange.last == postIndexRange.first) {
      notifyItemChanged(postIndexRange.first)
    } else {
      notifyItemRangeChanged(postIndexRange.first, (postIndexRange.last - postIndexRange.first) + 1)
    }
  }

  class ReplyViewHolder(itemView: GenericPostCell) : RecyclerView.ViewHolder(itemView) {
    private val genericPostCell = itemView

    fun onBind(postCellData: PostCellData) {
      genericPostCell.setPost(postCellData)
    }

  }

  companion object {
    const val POST_REPLY_VIEW_TYPE = 10
  }

}