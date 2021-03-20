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
package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter
import com.github.k1rakishou.chan.ui.cell.GenericPostCell
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.cell.ThreadCellData
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper.RepliesData
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableRecyclerView
import com.github.k1rakishou.chan.ui.view.LoadView
import com.github.k1rakishou.chan.ui.view.post_thumbnail.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.RecyclerUtils.getIndexAndTop
import com.github.k1rakishou.chan.utils.RecyclerUtils.restoreScrollPosition
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.isDarkColor
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.PostIndexed
import com.github.k1rakishou.persist_state.IndexAndTop
import java.util.*
import javax.inject.Inject

class PostRepliesController(
  context: Context,
  private val postPopupHelper: PostPopupHelper,
  private val presenter: ThreadPresenter
) : BaseFloatingController(context), ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postFilterManager: PostFilterManager
  @Inject
  lateinit var chanThreadViewableInfoManager: ChanThreadViewableInfoManager

  private lateinit var loadView: LoadView

  private var repliesView: ColorizableRecyclerView? = null
  private var displayingData: RepliesData? = null
  private var first = true
  private var repliesBackText: TextView? = null
  private var repliesCloseText: TextView? = null

  private val scope = KurobaCoroutineScope()
  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(scope)

  val postRepliesData: List<PostDescriptor>
    get() {
      val postDescriptors: MutableList<PostDescriptor> = ArrayList()
      for (post in displayingData!!.posts) {
        postDescriptors.add(post.post.postDescriptor)
      }

      return postDescriptors
    }

  private val scrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      super.onScrollStateChanged(recyclerView, newState)

      if (newState == RecyclerView.SCROLL_STATE_IDLE) {
        storeScrollPositionForVisiblePopup()
      }
    }
  }

  override fun getLayoutId(): Int {
    return R.layout.layout_post_replies_container
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    // Clicking outside the popup view
    view.setOnClickListener { postPopupHelper.pop() }
    loadView = view.findViewById(R.id.loadview)
    themeEngine.addListener(this)
  }

  override fun onShow() {
    super.onShow()
    onThemeChanged()
  }

  override fun onDestroy() {
    super.onDestroy()

    themeEngine.removeListener(this)

    repliesView?.removeOnScrollListener(scrollListener)
    repliesView?.swapAdapter(null, true)

    scope.cancelChildren()
  }

  override fun onThemeChanged() {
    if (!::themeEngine.isInitialized) {
      return
    }

    val isDarkColor = isDarkColor(themeEngine.chanTheme.backColor)
    val backDrawable = themeEngine.getDrawableTinted(context, R.drawable.ic_arrow_back_white_24dp, isDarkColor)
    val doneDrawable = themeEngine.getDrawableTinted(context, R.drawable.ic_done_white_24dp, isDarkColor)

    if (repliesBackText != null) {
      repliesBackText?.setTextColor(themeEngine.chanTheme.textColorPrimary)
      repliesBackText?.setCompoundDrawablesWithIntrinsicBounds(backDrawable, null, null, null)
    }

    if (repliesCloseText != null) {
      repliesCloseText?.setTextColor(themeEngine.chanTheme.textColorPrimary)
      repliesCloseText?.setCompoundDrawablesWithIntrinsicBounds(doneDrawable, null, null, null)
    }

    if (repliesView != null) {
      val adapter = repliesView?.adapter
      if (adapter is RepliesAdapter) {
        adapter.refresh()
      }
    }
  }

  fun getThumbnail(postImage: ChanPostImage): ThumbnailView? {
    if (repliesView == null) {
      return null
    }

    var thumbnail: ThumbnailView? = null
    for (i in 0 until repliesView!!.childCount) {
      val view = repliesView!!.getChildAt(i)

      if (view is GenericPostCell) {
        val genericPostCell = view
        val post = genericPostCell.getPost()

        if (post != null) {
          for (image in post.postImages) {
            if (image.equalUrl(postImage)) {
              thumbnail = genericPostCell.getThumbnailView(postImage)
            }
          }
        }
      }
    }

    return thumbnail
  }

  suspend fun onPostUpdated(updatedPost: ChanPost) {
    BackgroundUtils.ensureMainThread()

    val adapter = repliesView?.adapter as? RepliesAdapter
      ?: return

    adapter.onPostUpdated(updatedPost)
  }

  fun setPostRepliesData(threadDescriptor: ChanDescriptor.ThreadDescriptor, data: RepliesData) {
    rendezvousCoroutineExecutor.post { displayData(threadDescriptor, data) }
  }

  fun scrollTo(displayPosition: Int) {
    repliesView?.smoothScrollToPosition(displayPosition)
  }

  private suspend fun displayData(threadDescriptor: ChanDescriptor.ThreadDescriptor, data: RepliesData) {
    displayingData = data

    val dataView = AppModuleAndroidUtils.inflate(context, R.layout.layout_post_replies_bottombuttons)
    dataView.id = R.id.post_replies_data_view_id

    repliesView = dataView.findViewById(R.id.post_list)

    val repliesBack = dataView.findViewById<View>(R.id.replies_back)
    repliesBack.setOnClickListener { postPopupHelper.pop() }

    val repliesClose = dataView.findViewById<View>(R.id.replies_close)
    repliesClose.setOnClickListener { postPopupHelper.popAll() }

    repliesBackText = dataView.findViewById(R.id.replies_back_icon)
    repliesCloseText = dataView.findViewById(R.id.replies_close_icon)

    val repliesAdapter = RepliesAdapter(
      data.postAdditionalData,
      presenter,
      threadDescriptor,
      data.forPostWithDescriptor,
      chanThreadViewableInfoManager,
      postFilterManager,
      themeEngine.chanTheme
    )

    repliesAdapter.setHasStableIds(true)
    repliesAdapter.setData(data.posts, themeEngine.chanTheme)

    repliesView!!.layoutManager = LinearLayoutManager(context)
    repliesView!!.recycledViewPool.setMaxRecycledViews(RepliesAdapter.POST_REPLY_VIEW_TYPE, 0)
    repliesView!!.adapter = repliesAdapter
    repliesView!!.addOnScrollListener(scrollListener)

    loadView.setFadeDuration(if (first) 0 else 150)
    loadView.setView(dataView)

    first = false

    restoreScrollPosition(threadDescriptor, data)
    onThemeChanged()
  }

  private fun storeScrollPositionForVisiblePopup() {
    if (repliesView == null) {
      return
    }

    val postNo = displayingData?.forPostWithDescriptor?.postNo
      ?: displayingData?.threadDescriptor?.threadNo

    if (postNo == null) {
      return
    }

    scrollPositionCache.put(
      postNo,
      getIndexAndTop(repliesView!!)
    )
  }

  private fun restoreScrollPosition(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    repliesData: RepliesData
  ) {
    if (repliesView == null) {
      return
    }

    val postNo = repliesData.forPostWithDescriptor?.postNo
      ?: threadDescriptor.threadNo

    val scrollPosition = scrollPositionCache[postNo]
      ?: return

    repliesView!!.restoreScrollPosition(scrollPosition)
  }

  private class RepliesAdapter(
    private val postAdditionalData: PostCellData.PostAdditionalData,
    private val postCellCallback: PostCellInterface.PostCellCallback,
    private val chanDescriptor: ChanDescriptor,
    private val clickedPostDescriptor: PostDescriptor?,
    chanThreadViewableInfoManager: ChanThreadViewableInfoManager,
    postFilterManager: PostFilterManager,
    initialTheme: ChanTheme
  ) : RecyclerView.Adapter<ReplyViewHolder>() {

    private val threadCellData = ThreadCellData(
      chanThreadViewableInfoManager,
      postFilterManager,
      initialTheme
    )

    init {
      threadCellData.postAdditionalData = postAdditionalData
      threadCellData.defaultIsCompact = false
      threadCellData.defaultBoardPostViewMode = ChanSettings.PostViewMode.LIST
      threadCellData.defaultMarkedNo = clickedPostDescriptor?.postNo
      threadCellData.defaultShowDividerFunc = { postIndex: Int, totalPostsCount: Int -> postIndex < totalPostsCount - 1 }
      threadCellData.defaultStubFunc = { false }
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

    suspend fun setData(postIndexedList: List<PostIndexed>, theme: ChanTheme) {
      threadCellData.updateThreadData(postCellCallback, chanDescriptor, postIndexedList, theme)

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

      threadCellData.onPostUpdated(updatedPost)
      notifyItemChanged(postCellDataIndex)
    }

    companion object {
      const val POST_REPLY_VIEW_TYPE = 10
    }

  }

  private class ReplyViewHolder(itemView: GenericPostCell) : RecyclerView.ViewHolder(itemView) {
    private val genericPostCell = itemView

    fun onBind(postCellData: PostCellData) {
      genericPostCell.setPost(postCellData)
    }

  }

  override fun onBack(): Boolean {
    postPopupHelper.pop()
    return true
  }

  companion object {
    private val scrollPositionCache = LruCache<Long, IndexAndTop>(128)

    @JvmStatic
    fun clearScrollPositionCache() {
      scrollPositionCache.evictAll()
    }
  }
}