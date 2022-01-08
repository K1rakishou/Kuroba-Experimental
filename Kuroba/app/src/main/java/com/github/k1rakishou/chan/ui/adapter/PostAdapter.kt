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
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.BoardPostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.PostFilterHighlightManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHighlightManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.repository.CurrentlyDisplayedCatalogPostsRepository
import com.github.k1rakishou.chan.ui.cell.CatalogStatusCell
import com.github.k1rakishou.chan.ui.cell.GenericPostCell
import com.github.k1rakishou.chan.ui.cell.PostCell
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.PreviousThreadScrollPositionData
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
import dagger.Lazy
import java.util.*
import javax.inject.Inject

class PostAdapter(
  recyclerView: RecyclerView,
  postAdapterCallback: PostAdapterCallback,
  postCellCallback: PostCellCallback,
  statusCellCallback: ThreadStatusCell.Callback
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  @Inject
  lateinit var chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>
  @Inject
  lateinit var postFilterManager: Lazy<PostFilterManager>
  @Inject
  lateinit var savedReplyManager: Lazy<SavedReplyManager>
  @Inject
  lateinit var postFilterHighlightManager: Lazy<PostFilterHighlightManager>
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postHighlightManager: PostHighlightManager
  @Inject
  lateinit var currentlyDisplayedCatalogPostsRepository: CurrentlyDisplayedCatalogPostsRepository

  private val postAdapterCallback: PostAdapterCallback
  private val postCellCallback: PostCellCallback
  private val recyclerView: RecyclerView
  private val statusCellCallback: ThreadStatusCell.Callback
  val threadCellData: ThreadCellData

  /**
   * A hack for OnDemandContentLoader see comments in [onViewRecycled]
   */
  private val updatingPosts: MutableSet<PostDescriptor> = HashSet(64)

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
      chanThreadViewableInfoManager = chanThreadViewableInfoManager,
      _postFilterManager = postFilterManager,
      _postFilterHighlightManager = postFilterHighlightManager,
      _savedReplyManager = savedReplyManager,
      initialTheme = themeEngine.chanTheme
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
      PostCellData.TYPE_POST_ZERO_OR_SINGLE_THUMBNAIL_LEFT_ALIGNMENT,
      PostCellData.TYPE_POST_ZERO_OR_SINGLE_THUMBNAIL_RIGHT_ALIGNMENT,
      PostCellData.TYPE_POST_MULTIPLE_THUMBNAILS,
      PostCellData.TYPE_POST_STUB,
      PostCellData.TYPE_POST_CARD -> {
        return PostViewHolder(GenericPostCell(inflateContext))
      }
      PostCellData.TYPE_LAST_SEEN -> {
        return LastSeenViewHolder(
          themeEngine,
          AppModuleAndroidUtils.inflate(inflateContext, R.layout.cell_post_last_seen, parent, false)
        )
      }
      PostCellData.TYPE_LOADING_MORE -> {
        val catalogStatusCell = CatalogStatusCell(
          threadCellData.chanDescriptor as ChanDescriptor.ICatalogDescriptor,
          inflateContext
        )

        val loadingMoreViewHolder = LoadingMoreViewHolder(
          postAdapterCallback,
          catalogStatusCell
        )

        catalogStatusCell.setPostAdapterCallback(postAdapterCallback)
        catalogStatusCell.setError(threadCellData.error)

        return loadingMoreViewHolder
      }
      PostCellData.TYPE_STATUS -> {
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
      PostCellData.TYPE_POST_ZERO_OR_SINGLE_THUMBNAIL_LEFT_ALIGNMENT,
      PostCellData.TYPE_POST_ZERO_OR_SINGLE_THUMBNAIL_RIGHT_ALIGNMENT,
      PostCellData.TYPE_POST_MULTIPLE_THUMBNAILS,
      PostCellData.TYPE_POST_STUB,
      PostCellData.TYPE_POST_CARD -> {
        checkNotNull(threadCellData.chanDescriptor) { "chanDescriptor cannot be null" }

        val postViewHolder = holder as PostViewHolder
        val postCellData = threadCellData.getPostCellData(position)
        val postCell = postViewHolder.itemView as GenericPostCell
        postCell.setPost(postCellData)

        postAdapterCallback.onPostCellBound(postCell)
      }
      PostCellData.TYPE_LAST_SEEN -> (holder as LastSeenViewHolder).updateLabelColor()
      PostCellData.TYPE_LOADING_MORE -> {
        val loadingMoreViewHolder = (holder as LoadingMoreViewHolder)

        if (loadingMoreViewHolder.itemView.layoutParams is StaggeredGridLayoutManager.LayoutParams) {
          loadingMoreViewHolder.itemView.updateLayoutParams<StaggeredGridLayoutManager.LayoutParams> {
            isFullSpan = true
          }
        }

        loadingMoreViewHolder.catalogStatusCell.setError(threadCellData.error)
        loadingMoreViewHolder.bind()
      }
    }
  }

  override fun getItemCount(): Int {
    return threadCellData.postsCount()
  }

  override fun getItemViewType(position: Int): Int {
    if (position == threadCellData.lastSeenIndicatorPosition) {
      return PostCellData.TYPE_LAST_SEEN
    } else if (showLoadingMoreView() && position == itemCount - 1) {
      return PostCellData.TYPE_LOADING_MORE
    } else if (showStatusView() && position == itemCount - 1) {
      return PostCellData.TYPE_STATUS
    } else {
      val postCellData = threadCellData.getPostCellData(position)
      if (postCellData.stub) {
        return PostCellData.TYPE_POST_STUB
      } else {
        return getPostCellItemViewType(postCellData)
      }
    }
  }

  override fun getItemId(position: Int): Long {
    when (getItemViewType(position)) {
      PostCellData.TYPE_STATUS -> {
        return -1
      }
      PostCellData.TYPE_LAST_SEEN -> {
        return -2
      }
      PostCellData.TYPE_LOADING_MORE -> {
        return -3
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
    super.onViewAttachedToWindow(holder)

    // this is a hack to make sure text is selectable
    if (holder.itemView is GenericPostCell) {
      val postCell = (holder.itemView as? GenericPostCell)?.getChildPostCellView() as? PostCell
      if (postCell != null) {
        postCell.findViewById<View>(R.id.comment)?.let { postComment ->
          postComment.isEnabled = false
          postComment.isEnabled = true
        }
      }
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

      val isActuallyRecycling = !updatingPosts.remove(post.postDescriptor)

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
    postIndexedList: List<PostIndexed>,
    postCellDataWidthNoPaddings: Int,
    prevScrollPositionData: PreviousThreadScrollPositionData? = null
  ) {
    BackgroundUtils.ensureMainThread()

    threadCellData.updateThreadData(
      postCellCallback = postCellCallback,
      chanDescriptor = chanDescriptor,
      postIndexedList = postIndexedList,
      postCellDataWidthNoPaddings = postCellDataWidthNoPaddings,
      theme = chanTheme,
      prevScrollPositionData = prevScrollPositionData
    )

    if (threadCellData.chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
      currentlyDisplayedCatalogPostsRepository.updatePosts(
        postDescriptors = threadCellData.map { postCellData -> postCellData.postDescriptor }
      )
    }

    notifyDataSetChanged()

    Logger.d(TAG, "setThread() notifyDataSetChanged called, postIndexedList.size=" + postIndexedList.size)
  }

  fun cleanup() {
    if (threadCellData.chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
      currentlyDisplayedCatalogPostsRepository.clear()
    }

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

    if (showLoadingMoreView()) {
      val childCount = recyclerView.childCount
      for (i in 0 until childCount) {
        val child = recyclerView.getChildAt(i)
        if (child is CatalogStatusCell) {
          child.setError(error)
        }
      }
    }
  }

  fun setBoardPostViewMode(boardPostViewMode: BoardPostViewMode) {
    threadCellData.setBoardPostViewMode(boardPostViewMode)
    notifyDataSetChanged()
  }

  fun getScrollPosition(displayPosition: Int): Int {
    return threadCellData.getScrollPosition(displayPosition)
  }

  fun resetCachedPostData(postDescriptor: PostDescriptor) {
    threadCellData.resetCachedPostData(postDescriptor)
  }

  private fun showStatusView(): Boolean {
    val chanDescriptor = postAdapterCallback.currentChanDescriptor
    // the chanDescriptor can be null while this adapter is used between cleanup and the removal
    // of the recyclerview from the view hierarchy, although it's rare.
    return chanDescriptor != null
  }

  private fun showLoadingMoreView(): Boolean {
    val chanDescriptor = postAdapterCallback.currentChanDescriptor
    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      return false
    }

    return postAdapterCallback.isUnlimitedOrCompositeCatalog
  }

  suspend fun updatePosts(updatedPosts: List<ChanPost>) {
    BackgroundUtils.ensureMainThread()

    if (updatedPosts.isEmpty()) {
      return
    }

    val updatePostDescriptors = updatedPosts.map { post -> post.postDescriptor }

    val postIndexRange = threadCellData.getPostCellDataIndexToUpdate(updatePostDescriptors)
      ?: return

    if (!threadCellData.onPostsUpdated(updatedPosts)) {
      return
    }

    updatingPosts.addAll(updatePostDescriptors)

    if (postIndexRange.last == postIndexRange.first) {
      notifyItemChanged(postIndexRange.first)
    } else {
      notifyItemRangeChanged(postIndexRange.first, (postIndexRange.last - postIndexRange.first) + 1)
    }
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
    if (itemViewType == PostCellData.TYPE_STATUS) {
      correctedPosition = threadCellData.getPostPosition(correctedPosition - 1)
      itemViewType = getItemViewTypeSafe(correctedPosition)
    }

    if (itemViewType < 0) {
      return -1L
    }

    if (!PostCellData.PostCellItemViewType.isAnyPostType(itemViewType) && itemViewType != PostCellData.TYPE_POST_STUB) {
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
      return PostCellData.TYPE_LAST_SEEN
    }

    if (showStatusView() && position == itemCount - 1) {
      return PostCellData.TYPE_STATUS
    }

    if (showLoadingMoreView() && position == itemCount - 1) {
      return PostCellData.TYPE_LOADING_MORE
    }

    val correctedPosition = threadCellData.getPostPosition(position)
    if (correctedPosition < 0 || correctedPosition > threadCellData.postsCount()) {
      return -1
    }

    val postCellData = threadCellData.getPostCellDataSafe(correctedPosition)
    if (postCellData == null) {
      return -1
    }

    if (postFilterManager.get().getFilterStub(postCellData.postDescriptor)) {
      return PostCellData.TYPE_POST_STUB
    } else {
      return getPostCellItemViewType(postCellData)
    }
  }

  private fun getPostCellItemViewType(postCellData: PostCellData): Int {
    if (postCellData.stub) {
      return PostCellData.PostCellItemViewType.TypePostStub.viewTypeRaw
    }

    val postViewMode = postCellData.boardPostViewMode

    val postAlignmentMode = when (postCellData.chanDescriptor) {
      is ChanDescriptor.CatalogDescriptor,
      is ChanDescriptor.CompositeCatalogDescriptor -> ChanSettings.catalogPostAlignmentMode.get()
      is ChanDescriptor.ThreadDescriptor -> ChanSettings.threadPostAlignmentMode.get()
    }

    checkNotNull(postAlignmentMode) { "postAlignmentMode is null" }

    when (postViewMode) {
      BoardPostViewMode.LIST -> {
        if (postCellData.imagesCount <= 1) {
          when (postAlignmentMode) {
            ChanSettings.PostAlignmentMode.AlignLeft -> {
              return PostCellData.PostCellItemViewType.TypePostZeroOrSingleThumbnailLeftAlignment.viewTypeRaw
            }
            ChanSettings.PostAlignmentMode.AlignRight -> {
              return PostCellData.PostCellItemViewType.TypePostZeroOrSingleThumbnailRightAlignment.viewTypeRaw
            }
          }
        } else {
          return PostCellData.PostCellItemViewType.TypePostMultipleThumbnails.viewTypeRaw
        }
      }
      BoardPostViewMode.GRID,
      BoardPostViewMode.STAGGER -> {
        return PostCellData.PostCellItemViewType.TypePostCard.viewTypeRaw
      }
    }
  }

  class PostViewHolder(genericPostCell: GenericPostCell) : RecyclerView.ViewHolder(genericPostCell)

  class StatusViewHolder(val threadStatusCell: ThreadStatusCell) : RecyclerView.ViewHolder(threadStatusCell)

  class LoadingMoreViewHolder(
    private val postAdapterCallback: PostAdapterCallback,
    val catalogStatusCell: CatalogStatusCell
  ) : RecyclerView.ViewHolder(catalogStatusCell) {
    private var prevCatalogPage: Int? = null

    fun bind() {
      if (postAdapterCallback.endOfCatalogReached) {
        catalogStatusCell.onCatalogEndReached()
        return
      }

      val nextPage = postAdapterCallback.getNextPage()
        ?: return
      val prevPage = prevCatalogPage

      if (prevPage != null && nextPage <= prevPage) {
        return
      }

      if (postAdapterCallback.unlimitedOrCompositeCatalogEndReached) {
        catalogStatusCell.onCatalogEndReached()
        return
      }

      if (catalogStatusCell.isError) {
        return
      }

      prevCatalogPage = nextPage

      postAdapterCallback.loadCatalogPage()
      catalogStatusCell.setProgress(nextPage)
    }

  }

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
    val endOfCatalogReached: Boolean
    val isUnlimitedOrCompositeCatalog: Boolean
    val unlimitedOrCompositeCatalogEndReached: Boolean

    fun loadCatalogPage(overridePage: Int? = null)
    fun getNextPage(): Int?
    fun onPostCellBound(postCell: GenericPostCell)
  }

  companion object {
    private const val TAG = "PostAdapter"
  }

}