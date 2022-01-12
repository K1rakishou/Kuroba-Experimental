package com.github.k1rakishou.chan.ui.controller.popup

import android.content.Context
import android.util.LruCache
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.adapter.PostRepliesAdapter
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.RecyclerUtils
import com.github.k1rakishou.chan.utils.RecyclerUtils.restoreScrollPosition
import com.github.k1rakishou.chan.utils.awaitUntilGloballyLaidOut
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.PostIndexed
import com.github.k1rakishou.persist_state.IndexAndTop
import kotlinx.coroutines.launch
import java.util.*

class PostRepliesPopupController(
  context: Context,
  postPopupHelper: PostPopupHelper,
  postCellCallback: PostCellInterface.PostCellCallback
) : BasePostPopupController<PostRepliesPopupController.PostRepliesPopupData>(context, postPopupHelper, postCellCallback) {
  override var displayingData: PostRepliesPopupData? = null

  private val scrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      super.onScrollStateChanged(recyclerView, newState)

      if (newState == RecyclerView.SCROLL_STATE_IDLE) {
        storeScrollPosition()
      }
    }
  }

  override val postPopupType: PostPopupType
    get() = PostPopupType.Replies

  override fun onDestroy() {
    super.onDestroy()

    if (postsViewInitialized) {
      postsView.removeOnScrollListener(scrollListener)
    }
  }

  override fun getDisplayingPostDescriptors(): List<PostDescriptor> {
    if (displayingData == null) {
      return emptyList()
    }

    val postDescriptors: MutableList<PostDescriptor> = ArrayList()
    for (post in displayingData!!.posts) {
      postDescriptors.add(post.post.postDescriptor)
    }

    return postDescriptors
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override suspend fun displayData(
    chanDescriptor: ChanDescriptor,
    data: PostRepliesPopupData
  ): ViewGroup {
    BackgroundUtils.ensureMainThread()

    val dataView = AppModuleAndroidUtils.inflate(context, R.layout.layout_post_popup_replies)
    dataView.id = R.id.post_popup_replies_view_id

    val repliesAdapter = PostRepliesAdapter(
      postViewMode = data.postViewMode,
      postCellCallback = postCellCallback,
      chanDescriptor = chanDescriptor,
      clickedPostDescriptor = data.forPostWithDescriptor,
      chanThreadViewableInfoManager = chanThreadViewableInfoManager,
      postFilterManager = postFilterManager,
      savedReplyManager = savedReplyManager,
      postFilterHighlightManager = postFilterHighlightManager,
      initialTheme = themeEngine.chanTheme
    )

    repliesAdapter.setHasStableIds(true)

    postsView = dataView.findViewById(R.id.post_list)
    postsView.layoutManager = LinearLayoutManager(context)
    postsView.recycledViewPool.setMaxRecycledViews(PostRepliesAdapter.POST_REPLY_VIEW_TYPE, 0)
    postsView.adapter = repliesAdapter
    postsView.addOnScrollListener(scrollListener)

    mainScope.launch {
      postsView.awaitUntilGloballyLaidOut(waitForWidth = true)

      repliesAdapter.setOrUpdateData(postsView.width, data.posts, themeEngine.chanTheme)
      restoreScrollPosition(data.forPostWithDescriptor)
    }

    return dataView
  }

  override fun onImageIsAboutToShowUp() {
    // no-op
  }

  private fun storeScrollPosition() {
    if (!postsViewInitialized) {
      return
    }

    val firstPostDescriptor = displayingData?.forPostWithDescriptor
    if (firstPostDescriptor == null) {
      return
    }

    scrollPositionCache.put(
      firstPostDescriptor,
      RecyclerUtils.getIndexAndTop(postsView)
    )
  }

  private fun restoreScrollPosition(postDescriptor: PostDescriptor) {
    if (!postsViewInitialized) {
      return
    }

    val scrollPosition = scrollPositionCache[postDescriptor]
      ?: return

    postsView.restoreScrollPosition(scrollPosition)
  }

  class PostRepliesPopupData(
    override val descriptor: ChanDescriptor,
    val forPostWithDescriptor: PostDescriptor,
    override val postViewMode: PostCellData.PostViewMode,
    val posts: List<PostIndexed>
  ) : PostPopupHelper.PostPopupData

  companion object {
    val scrollPositionCache = LruCache<PostDescriptor, IndexAndTop>(128)
  }

}