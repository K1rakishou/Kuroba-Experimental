package com.github.k1rakishou.chan.ui.controller.popup

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.adapter.PostRepliesAdapter
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.PostIndexed
import java.util.*

class PostRepliesPopupController(
  context: Context,
  postPopupHelper: PostPopupHelper,
  postCellCallback: PostCellInterface.PostCellCallback
) : BasePostPopupController<PostRepliesPopupController.PostRepliesPopupData>(context, postPopupHelper, postCellCallback) {
  override var displayingData: PostRepliesPopupData? = null

  override val postPopupType: PostPopupType
    get() = PostPopupType.Replies

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

  override suspend fun initialDisplayData(
    chanDescriptor: ChanDescriptor,
    data: PostRepliesPopupData
  ): ViewGroup {
    val dataView = AppModuleAndroidUtils.inflate(context, R.layout.layout_post_popup_replies)
    dataView.id = R.id.post_popup_replies_view_id

    val repliesAdapter = PostRepliesAdapter(
      data.postViewMode,
      postCellCallback,
      chanDescriptor,
      data.forPostWithDescriptor,
      chanThreadViewableInfoManager,
      postFilterManager,
      themeEngine.chanTheme
    )

    repliesAdapter.setHasStableIds(true)
    repliesAdapter.setOrUpdateData(data.posts, themeEngine.chanTheme)

    postsView = dataView.findViewById(R.id.post_list)

    postsView.layoutManager = LinearLayoutManager(context)
    postsView.recycledViewPool.setMaxRecycledViews(PostRepliesAdapter.POST_REPLY_VIEW_TYPE, 0)
    postsView.adapter = repliesAdapter
    postsView.addOnScrollListener(scrollListener)

    restoreScrollPosition(chanDescriptor)

    return dataView
  }

  class PostRepliesPopupData(
    override val descriptor: ChanDescriptor,
    override val postViewMode: PostCellData.PostViewMode,
    val forPostWithDescriptor: PostDescriptor?,
    val posts: List<PostIndexed>
  ) : PostPopupHelper.PostPopupData

}