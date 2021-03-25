package com.github.k1rakishou.chan.ui.controller.popup

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.adapter.PostRepliesAdapter
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.PostIndexed

class PostPopupController(
  context: Context,
  postPopupHelper: PostPopupHelper,
  postCellCallback: PostCellInterface.PostCellCallback
) : BasePostPopupController<PostPopupController.PostRepliesPopupData>(context, postPopupHelper, postCellCallback) {
  private var repliesBackText: TextView? = null
  private var repliesCloseText: TextView? = null

  override var displayingData: PostRepliesPopupData? = null
  private var first = true

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onThemeChanged() {
    if (!themeEngineInitialized) {
      return
    }

    val isDarkColor = ThemeEngine.isDarkColor(themeEngine.chanTheme.backColor)
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

    super.onThemeChanged()
  }

  override suspend fun initialDisplayData(
    chanDescriptor: ChanDescriptor,
    data: PostRepliesPopupData
  ) {
    displayingData = data

    val dataView = AppModuleAndroidUtils.inflate(context, R.layout.layout_post_replies_bottombuttons)
    dataView.id = R.id.post_replies_data_view_id

    postsView = dataView.findViewById(R.id.post_list)

    val repliesBack = dataView.findViewById<View>(R.id.replies_back)
    repliesBack.setOnClickListener { postPopupHelper.pop() }

    val repliesClose = dataView.findViewById<View>(R.id.replies_close)
    repliesClose.setOnClickListener { postPopupHelper.popAll() }

    repliesBackText = dataView.findViewById(R.id.replies_back_icon)
    repliesCloseText = dataView.findViewById(R.id.replies_close_icon)

    val repliesAdapter = PostRepliesAdapter(
      data.postAdditionalData,
      postCellCallback,
      chanDescriptor,
      data.forPostWithDescriptor,
      chanThreadViewableInfoManager,
      postFilterManager,
      themeEngine.chanTheme
    )

    repliesAdapter.setHasStableIds(true)
    repliesAdapter.setOrUpdateData(data.posts, themeEngine.chanTheme)

    postsView.layoutManager = LinearLayoutManager(context)
    postsView.recycledViewPool.setMaxRecycledViews(PostRepliesAdapter.POST_REPLY_VIEW_TYPE, 0)
    postsView.adapter = repliesAdapter
    postsView.addOnScrollListener(scrollListener)

    loadView.setFadeDuration(if (first) 0 else 150)
    loadView.setView(dataView)

    first = false

    restoreScrollPosition(chanDescriptor, data)
    onThemeChanged()
  }

  class PostRepliesPopupData(
    override val descriptor: ChanDescriptor,
    override val postAdditionalData: PostCellData.PostAdditionalData,
    override val forPostWithDescriptor: PostDescriptor?,
    override val posts: List<PostIndexed>
  ) : PostPopupHelper.PostPopupData

}