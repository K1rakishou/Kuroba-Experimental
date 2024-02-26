package com.github.k1rakishou.chan.features.reply

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.ui.compose.providers.ProvideEverythingForCompose
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.requireComponentActivity
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.persist_state.ReplyMode

class ReplyLayoutView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : FrameLayout(context, attributeSet, defAttrStyle), ReplyLayoutViewCallbacks {
  private val composeView: ComposeView

  private val _readyState = mutableStateOf(false)
  private lateinit var replyLayoutViewModel: ReplyLayoutViewModel

  init {
    removeAllViews()

    composeView = ComposeView(context)
    composeView.layoutParams = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )

    addView(composeView)

    composeView.setContent {
      ProvideEverythingForCompose {
        val ready by _readyState
        if (!ready) {
          return@ProvideEverythingForCompose
        }

        ReplyLayout(replyLayoutViewModel)
      }
    }
  }

  fun onCreate(threadControllerType: ThreadControllerType) {
    replyLayoutViewModel = context.requireComponentActivity().viewModelByKey<ReplyLayoutViewModel>(
      key = threadControllerType.name,
      defaultArgs = bundleOf(ReplyLayoutViewModel.ThreadControllerTypeParam to threadControllerType)
    )
    _readyState.value = true
  }

  override suspend fun bindChanDescriptor(descriptor: ChanDescriptor, threadControllerType: ThreadControllerType) {
    replyLayoutViewModel.bindChanDescriptor(descriptor, threadControllerType)
  }

  override fun replyLayoutVisibility(): ReplyLayoutVisibility {
    return replyLayoutViewModel.replyLayoutVisibility()
  }

  override fun isCatalogMode(): Boolean? {
    return replyLayoutViewModel.isCatalogMode()
  }

  override fun isExpanded(): Boolean {
    return replyLayoutViewModel.replyLayoutVisibility() == ReplyLayoutVisibility.Expanded
  }

  override fun isOpened(): Boolean {
    return replyLayoutViewModel.replyLayoutVisibility() == ReplyLayoutVisibility.Opened
  }

  override fun isCollapsed(): Boolean {
    return replyLayoutViewModel.replyLayoutVisibility() == ReplyLayoutVisibility.Collapsed
  }

  override fun updateReplyLayoutVisibility(newReplyLayoutVisibility: ReplyLayoutVisibility) {
    replyLayoutViewModel.updateReplyLayoutVisibility(newReplyLayoutVisibility)
  }

  override fun quote(post: ChanPost, withText: Boolean) {
    replyLayoutViewModel.quote(post, withText)
  }

  override fun quote(postDescriptor: PostDescriptor, text: CharSequence) {
    replyLayoutViewModel.quote(postDescriptor, text)
  }

  override fun makeSubmitCall(chanDescriptor: ChanDescriptor, replyMode: ReplyMode, retrying: Boolean) {
    replyLayoutViewModel.makeSubmitCall(context.applicationContext, chanDescriptor, replyMode, retrying)
  }

  override fun onImageOptionsApplied() {
    replyLayoutViewModel.onImageOptionsApplied()
  }

  override fun hideKeyboard() {
    AndroidUtils.hideKeyboard(this)
  }

  override fun cleanup() {
    replyLayoutViewModel.cleanup()
  }

  override fun onBack(): Boolean {
    return replyLayoutViewModel.onBack()
  }

}