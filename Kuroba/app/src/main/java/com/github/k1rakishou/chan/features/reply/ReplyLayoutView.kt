package com.github.k1rakishou.chan.features.reply

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.requireComponentActivity
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.persist_state.ReplyMode

class ReplyLayoutView(
    context: Context,
    attributeSet: AttributeSet? = null,
    defAttrStyle: Int = 0
) : FrameLayout(context, attributeSet, defAttrStyle), ReplyLayoutViewCallbacks {
    private val composeView: ComposeView

    private val replyLayoutViewModel by lazy(LazyThreadSafetyMode.NONE) {
        context.requireComponentActivity().viewModelByKey<ReplyLayoutViewModel>()
    }

    init {
        removeAllViews()

        composeView = ComposeView(context)
        composeView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        addView(composeView)
    }

    override fun bindChanDescriptor(descriptor: ChanDescriptor) {
        composeView.setContent {
            ReplyLayout(descriptor)
        }
    }

    override fun isExpanded(chanDescriptor: ChanDescriptor): Boolean {
        return replyLayoutViewModel.isExpanded(chanDescriptor)
    }

    override fun showCaptcha(
        chanDescriptor: ChanDescriptor,
        replyMode: ReplyMode,
        autoReply: Boolean,
        afterPostingAttempt: Boolean,
        onFinished: ((Boolean) -> Unit)?
    ) {
        replyLayoutViewModel.showCaptcha(
            chanDescriptor = chanDescriptor,
            replyMode = replyMode,
            autoReply = autoReply,
            afterPostingAttempt = afterPostingAttempt,
            onFinished = onFinished
        )
    }

    override fun openOrCloseReplyLayout(open: Boolean) {
        replyLayoutViewModel.openOrCloseReplyLayout(open)
    }

    override fun quote(post: ChanPost, withText: Boolean) {
        replyLayoutViewModel.quote(post, withText)
    }

    override fun quote(postDescriptor: PostDescriptor, text: CharSequence) {
        replyLayoutViewModel.quote(postDescriptor, text)
    }

    override fun onImageOptionsApplied() {
        replyLayoutViewModel.onImageOptionsApplied()
    }

    override fun cleanup() {
        replyLayoutViewModel.cleanup()
    }

    override fun onBack(): Boolean {
        return replyLayoutViewModel.onBack()
    }

}