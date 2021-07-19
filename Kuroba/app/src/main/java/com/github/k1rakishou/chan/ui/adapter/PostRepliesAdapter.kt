package com.github.k1rakishou.chan.ui.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.ui.cell.GenericPostCell
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.cell.ThreadCellData
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.PostIndexed

class PostRepliesAdapter(
  private val appConstants: AppConstants,
  private val postViewMode: PostCellData.PostViewMode,
  private val postCellCallback: PostCellInterface.PostCellCallback,
  private val chanDescriptor: ChanDescriptor,
  private val clickedPostDescriptor: PostDescriptor?,
  chanThreadViewableInfoManager: ChanThreadViewableInfoManager,
  postFilterManager: PostFilterManager,
  initialTheme: ChanTheme
) : RecyclerView.Adapter<PostRepliesAdapter.ReplyViewHolder>() {

  private val threadCellData = ThreadCellData(
    appConstants,
    chanThreadViewableInfoManager,
    postFilterManager,
    initialTheme
  )

  init {
    threadCellData.postViewMode = postViewMode
    threadCellData.defaultIsCompact = false
    threadCellData.defaultBoardPostViewMode = ChanSettings.BoardPostViewMode.LIST
    threadCellData.defaultMarkedNo = clickedPostDescriptor?.postNo
    threadCellData.defaultShowDividerFunc = { postIndex: Int, totalPostsCount: Int -> postIndex < totalPostsCount - 1 }
    threadCellData.defaultStubFunc = { _, _ -> emptyMap<PostDescriptor, Boolean>() }
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
    val post = threadCellData.getPostCellData(position).post
    val repliesFromCount = post.repliesFromCount

    return (repliesFromCount.toLong() shl 32) +
      post.postNo() +
      post.postSubNo()
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

  fun clear() {
    threadCellData.cleanup()
    notifyDataSetChanged()
  }

  suspend fun onPostUpdated(updatedPost: ChanPost) {
    val postDescriptor = updatedPost.postDescriptor

    val postCellDataIndex = threadCellData.getPostCellDataIndex(postDescriptor)
    if (postCellDataIndex == null) {
      return
    }

    if (!threadCellData.onPostUpdated(updatedPost)) {
      return
    }

    notifyItemChanged(postCellDataIndex)
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