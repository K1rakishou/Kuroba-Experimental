package com.github.k1rakishou.chan.features.reply

import android.content.Context
import android.text.Spannable
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.AnnotatedString
import androidx.core.os.bundleOf
import androidx.core.text.getSpans
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.features.reply.data.ReplyFileAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.features.reply_image_search.ImageSearchController
import com.github.k1rakishou.chan.ui.compose.providers.ProvideEverythingForCompose
import com.github.k1rakishou.chan.ui.controller.OpenUrlInWebViewController
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaComposeDialogController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.layout.ThreadListLayout
import com.github.k1rakishou.chan.ui.widget.dialog.KurobaAlertDialog
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.WebViewLink
import com.github.k1rakishou.chan.utils.WebViewLinkMovementMethod
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.requireComponentActivity
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.persist_state.ReplyMode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

class ReplyLayoutView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : FrameLayout(context, attributeSet, defAttrStyle),
  ReplyLayoutViewModel.ReplyLayoutViewCallbacks,
  ThreadListLayout.ReplyLayoutViewCallbacks,
  WebViewLinkMovementMethod.ClickListener {

  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var appResources: AppResources

  private lateinit var replyLayoutViewModel: ReplyLayoutViewModel
  private lateinit var replyLayoutCallbacks: ReplyLayoutViewModel.ThreadListLayoutCallbacks

  private var prevToast: Toast? = null

  private val composeView: ComposeView
  private val coroutineScope = KurobaCoroutineScope()
  private val readyState = mutableStateOf(false)
  private val dialogHandle = AtomicReference<KurobaAlertDialog.AlertDialogHandle?>(null)
  private val showPostingDialogExecutor = SerializedCoroutineExecutor(coroutineScope)

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    removeAllViews()

    composeView = ComposeView(context)
    composeView.layoutParams = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )

    addView(composeView)

    composeView.setContent {
      ProvideEverythingForCompose {
        val ready by readyState
        if (!ready) {
          return@ProvideEverythingForCompose
        }

        ReplyLayout(
          replyLayoutViewModel = replyLayoutViewModel,
          onPresolveCaptchaButtonClicked = replyLayoutCallbacks::onPresolveCaptchaButtonClicked,
          onSearchRemoteMediaButtonClicked = { chanDescriptor ->
            val imageSearchController = ImageSearchController(
              context = context,
              boundChanDescriptor = chanDescriptor,
              onImageSelected = { selectedImageUrl -> replyLayoutViewModel.onRemoteImageSelected(selectedImageUrl) }
            )

            replyLayoutCallbacks.pushController(imageSearchController)
          }
        )
      }
    }
  }

  override fun onCreate(threadControllerType: ThreadControllerType, callbacks: ReplyLayoutViewModel.ThreadListLayoutCallbacks) {
    replyLayoutCallbacks = callbacks

    replyLayoutViewModel = context.requireComponentActivity().viewModelByKey<ReplyLayoutViewModel>(
      key = threadControllerType.name,
      defaultArgs = bundleOf(ReplyLayoutViewModel.ThreadControllerTypeParam to threadControllerType)
    )
    replyLayoutViewModel.bindThreadListLayoutCallbacks(callbacks)
    replyLayoutViewModel.bindReplyLayoutViewCallbacks(this)

    readyState.value = true
  }

  override fun onDestroy() {
    prevToast?.cancel()
    prevToast = null

    replyLayoutViewModel.unbindCallbacks()
    coroutineScope.cancelChildren()
  }

  override suspend fun bindChanDescriptor(descriptor: ChanDescriptor, threadControllerType: ThreadControllerType) {
    replyLayoutViewModel.bindChanDescriptor(descriptor, threadControllerType)
  }

  override fun quote(post: ChanPost, withText: Boolean) {
    replyLayoutViewModel.quote(post, withText)
  }

  override fun quote(postDescriptor: PostDescriptor, text: CharSequence) {
    replyLayoutViewModel.quote(postDescriptor, text)
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

  override fun enqueueReply(chanDescriptor: ChanDescriptor, replyMode: ReplyMode, retrying: Boolean) {
    replyLayoutViewModel.enqueueReply(chanDescriptor, replyMode, retrying)
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

  override fun onWebViewLinkClick(type: WebViewLink.Type, link: String) {
    Logger.d(TAG, "onWebViewLinkClick type: ${type}, link: ${link}")

    when (type) {
      WebViewLink.Type.BanMessage -> {
        replyLayoutCallbacks.presentController(OpenUrlInWebViewController(context, link))
      }
    }
  }

  override suspend fun showDialogSuspend(title: String, message: CharSequence?) {
    suspendCancellableCoroutine<Unit> { cancellableContinuation ->
      showDialog(
        title = title,
        message = message,
        onDismissListener = { cancellableContinuation.resumeValueSafe(Unit) }
      )
    }
  }

  override fun showDialog(title: String, message: CharSequence?, onDismissListener: (() -> Unit)?) {
    if (title.isBlank() && message.isNullOrBlank()) {
      hideDialog()
      onDismissListener?.invoke()
      return
    }

    showPostingDialogExecutor.post {
      try {
        val linkMovementMethod = if (hasWebViewLinks(message)) {
          WebViewLinkMovementMethod(webViewLinkClickListener = this)
        } else {
          null
        }

        val dialogId = "ReplyPresenterPostingErrorDialog"

        suspendCancellableCoroutine<Unit> { continuation ->
          continuation.invokeOnCancellation { dialogHandle.getAndSet(null)?.dismiss() }

          dialogFactory.dismissDialogById(dialogId)

          val handle = dialogFactory.createSimpleInformationDialog(
            context = context,
            dialogId = dialogId,
            titleText = title,
            descriptionText = message,
            customLinkMovementMethod = linkMovementMethod,
            onDismissListener = { continuation.resumeValueSafe(Unit) }
          )

          dialogHandle.getAndSet(handle)
            ?.dismiss()
        }
      } finally {
        onDismissListener?.invoke()
      }
    }
  }

  override fun hideDialog() {
    dialogHandle.getAndSet(null)
      ?.dismiss()
  }

  override fun showToast(message: String) {
    prevToast?.cancel()
    prevToast = null

    prevToast = Toast.makeText(context, message, Toast.LENGTH_LONG)
      .also { toast -> toast.show() }
  }

  override fun onSearchRemoteMediaButtonClicked() {
    TODO("Not yet implemented")
  }

  override fun onReplyLayoutOptionsButtonClicked() {
    TODO("Not yet implemented")
  }

  override fun onAttachedMediaClicked(attachedMedia: ReplyFileAttachable) {
    // TODO("Not yet implemented")
  }

  override fun onAttachedMediaLongClicked(attachedMedia: ReplyFileAttachable) {
    // TODO("Not yet implemented")
  }

  override fun showFileStatusDialog(attachableFileStatus: AnnotatedString) {
    dialogFactory.dialog(
      context = context,
      params = KurobaComposeDialogController.informationDialog(
        title = KurobaComposeDialogController.Text.String(
          value = appResources.string(R.string.reply_file_status_dialog_title)
        ),
        description = KurobaComposeDialogController.Text.AnnotatedString(attachableFileStatus)
      )
    )
  }

  private fun hasWebViewLinks(message: CharSequence?): Boolean {
    var hasWebViewLinks = false

    if (message is Spannable) {
      hasWebViewLinks = message.getSpans<WebViewLink>().isNotEmpty()
    }

    return hasWebViewLinks
  }

  companion object {
    private const val TAG = "ReplyLayoutView"
  }

}