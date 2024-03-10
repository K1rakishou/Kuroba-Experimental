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
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.usecase.LoadBoardFlagsUseCase
import com.github.k1rakishou.chan.features.reply.data.ReplyFileAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.features.reply_image_search.ImageSearchController
import com.github.k1rakishou.chan.ui.compose.providers.ProvideEverythingForCompose
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.OpenUrlInWebViewController
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaComposeDialogController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.layout.ThreadListLayout
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
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
import java.util.*
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
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

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

  override fun onImageOptionsApplied(fileUuid: UUID) {
    replyLayoutViewModel.onImageOptionsApplied(fileUuid)
  }

  override fun hideKeyboard() {
    AndroidUtils.hideKeyboard(this)
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

  override suspend fun promptUserForMediaUrl(): String? {
    val params = KurobaComposeDialogController.dialogWithInput(
      title = KurobaComposeDialogController.Text.Id(R.string.reply_layout_remote_file_pick_enter_url_dialog_title),
      input = KurobaComposeDialogController.Input.String(
        hint = KurobaComposeDialogController.Text.Id(R.string.reply_layout_remote_file_pick_enter_url_dialog_hint)
      )
    )

    dialogFactory.showDialog(
      context = context,
      params = params
    )

    return params.awaitInputResult()
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

  override fun onReplyLayoutOptionsButtonClicked() {
    // TODO: New reply layout.
  }

  override fun onAttachedMediaClicked(attachedMedia: ReplyFileAttachable, isFileSupportedForReencoding: Boolean) {
    replyLayoutCallbacks.showMediaReencodingController(attachedMedia, isFileSupportedForReencoding)
  }

  override suspend fun onAttachedMediaLongClicked(attachedMedia: ReplyFileAttachable) {
    showAttachFileOptions(attachedMedia.fileUuid)
  }

  override fun onDontKeepActivitiesSettingDetected() {
    dialogFactory.createSimpleInformationDialog(
      context = context,
      titleText = appResources.string(R.string.dont_keep_activities_setting_enabled),
      descriptionText = appResources.string(R.string.dont_keep_activities_setting_enabled_description)
    )
  }

  override fun showFileStatusDialog(attachableFileStatus: AnnotatedString) {
    dialogFactory.showDialog(
      context = context,
      params = KurobaComposeDialogController.informationDialog(
        title = KurobaComposeDialogController.Text.String(
          value = appResources.string(R.string.reply_file_status_dialog_title)
        ),
        description = KurobaComposeDialogController.Text.AnnotatedString(attachableFileStatus)
      )
    )
  }

  override suspend fun promptUserToSelectFlag(chanDescriptor: ChanDescriptor): LoadBoardFlagsUseCase.FlagInfo? {
    val flagInfoList = replyLayoutViewModel.getFlagInfoList(chanDescriptor.boardDescriptor())
    if (flagInfoList.isEmpty()) {
      return null
    }

    val floatingMenuItems = mutableListOf<FloatingListMenuItem>()

    flagInfoList.forEach { boardFlag ->
      floatingMenuItems += FloatingListMenuItem(
        key = boardFlag.flagKey,
        name = boardFlag.asUserReadableString(),
        value = boardFlag
      )
    }

    if (floatingMenuItems.isEmpty()) {
      return null
    }

    val selectedBoardFlag = suspendCancellableCoroutine<LoadBoardFlagsUseCase.FlagInfo?> { continuation ->
      val floatingMenuScreen = FloatingListMenuController(
        context = context,
        constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
        items = floatingMenuItems,
        itemClickListener = { clickedItem ->
          val selectedFlagInfo = clickedItem.value as? LoadBoardFlagsUseCase.FlagInfo
          continuation.resumeValueSafe(selectedFlagInfo)
        },
        menuDismissListener = {
          continuation.resumeValueSafe(null)
        }
      )

      replyLayoutCallbacks.presentController(floatingMenuScreen)
    }

    return selectedBoardFlag
  }

  override suspend fun promptUserToConfirmMediaDeletion(): Boolean {
    return suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
      dialogFactory.showDialog(
        context = context,
        params = KurobaComposeDialogController.confirmationDialog(
          title = KurobaComposeDialogController.Text.Id(R.string.reply_layout_attached_deletion_dialog_title),
          description = null,
          negativeButton = KurobaComposeDialogController.DialogButton(
            buttonText = R.string.no,
            onClick = { cancellableContinuation.resumeValueSafe(false) }
          ),
          positionButton = KurobaComposeDialogController.PositiveDialogButton(
            buttonText = R.string.yes,
            isActionDangerous = true,
            onClick = { cancellableContinuation.resumeValueSafe(true) }
          )
        ),
        onDismissListener = { cancellableContinuation.resumeValueSafe(false) }
      )
    }
  }

  private fun hasWebViewLinks(message: CharSequence?): Boolean {
    var hasWebViewLinks = false

    if (message is Spannable) {
      hasWebViewLinks = message.getSpans<WebViewLink>().isNotEmpty()
    }

    return hasWebViewLinks
  }

  private suspend fun showAttachFileOptions(selectedFileUuid: UUID) {
    val floatingListMenuItems = mutableListOf<FloatingListMenuItem>()

    floatingListMenuItems += FloatingListMenuItem(
      key = ACTION_DELETE_FILE,
      name = context.getString(R.string.layout_reply_files_area_delete_file_action),
      value = selectedFileUuid
    )

    if (replyLayoutViewModel.hasSelectedFiles()) {
      floatingListMenuItems += FloatingListMenuItem(
        key = ACTION_DELETE_FILES,
        name = context.getString(R.string.layout_reply_files_area_delete_selected_files_action),
        value = selectedFileUuid
      )

      floatingListMenuItems += FloatingListMenuItem(
        key = ACTION_REMOVE_FILE_NAME,
        name = context.getString(R.string.layout_reply_files_area_remove_file_name),
        value = selectedFileUuid
      )

      floatingListMenuItems += FloatingListMenuItem(
        key = ACTION_REMOVE_METADATA,
        name = context.getString(R.string.layout_reply_files_area_remove_file_metadata),
        value = selectedFileUuid
      )

      floatingListMenuItems += FloatingListMenuItem(
        key = ACTION_CHANGE_CHECKSUM,
        name = context.getString(R.string.layout_reply_files_area_change_checksum),
        value = selectedFileUuid
      )
    }

    if (replyLayoutViewModel.boardsSupportsSpoilers()) {
      if (replyLayoutViewModel.isReplyFileMarkedAsSpoiler(selectedFileUuid)) {
        floatingListMenuItems += FloatingListMenuItem(
          key = ACTION_UNMARK_AS_SPOILER,
          name = context.getString(R.string.layout_reply_files_area_unmark_as_spoiler),
          value = selectedFileUuid
        )
      } else {
        floatingListMenuItems += FloatingListMenuItem(
          key = ACTION_MARK_AS_SPOILER,
          name = context.getString(R.string.layout_reply_files_area_mark_as_spoiler),
          value = selectedFileUuid
        )
      }
    }

    if (!replyLayoutViewModel.allFilesSelected()) {
      floatingListMenuItems += FloatingListMenuItem(
        key = ACTION_SELECT_ALL,
        name = context.getString(R.string.layout_reply_files_area_select_all),
        value = selectedFileUuid
      )
    } else {
      floatingListMenuItems += FloatingListMenuItem(
        key = ACTION_UNSELECT_ALL,
        name = context.getString(R.string.layout_reply_files_area_unselect_all),
        value = selectedFileUuid
      )
    }

    val clickedItem = suspendCancellableCoroutine<FloatingListMenuItem?> { cancellableContinuation ->
      val floatingListMenuController = FloatingListMenuController(
        context = context,
        constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
        items = floatingListMenuItems,
        itemClickListener = { item -> cancellableContinuation.resumeValueSafe(item) },
        menuDismissListener = { cancellableContinuation.resumeValueSafe(null) }
      )

      replyLayoutCallbacks.presentController(floatingListMenuController)
    }

    if (clickedItem == null) {
      return
    }

    onAttachFileItemClicked(clickedItem)
  }

  private fun onAttachFileItemClicked(item: FloatingListMenuItem) {
    val id = item.key as Int
    val clickedFileUuid = item.value as UUID

    when (id) {
      ACTION_DELETE_FILE -> replyLayoutViewModel.removeAttachedMedia(clickedFileUuid)
      ACTION_DELETE_FILES -> replyLayoutViewModel.deleteSelectedFiles()
      ACTION_REMOVE_FILE_NAME -> replyLayoutViewModel.removeSelectedFilesName()
      ACTION_REMOVE_METADATA -> replyLayoutViewModel.removeSelectedFilesMetadata()
      ACTION_CHANGE_CHECKSUM -> replyLayoutViewModel.changeSelectedFilesChecksum()
      ACTION_SELECT_ALL -> replyLayoutViewModel.selectUnselectAll(selectAll = true)
      ACTION_UNSELECT_ALL -> replyLayoutViewModel.selectUnselectAll(selectAll = false)
      ACTION_MARK_AS_SPOILER -> replyLayoutViewModel.markUnmarkAsSpoiler(clickedFileUuid, spoiler = true)
      ACTION_UNMARK_AS_SPOILER -> replyLayoutViewModel.markUnmarkAsSpoiler(clickedFileUuid, spoiler = false)
    }
  }

  companion object {
    private const val TAG = "ReplyLayoutView"

    private const val ACTION_DELETE_FILE = 1
    private const val ACTION_DELETE_FILES = 2
    private const val ACTION_REMOVE_FILE_NAME = 3
    private const val ACTION_REMOVE_METADATA = 4
    private const val ACTION_CHANGE_CHECKSUM = 5
    private const val ACTION_SELECT_ALL = 6
    private const val ACTION_UNSELECT_ALL = 7
    private const val ACTION_MARK_AS_SPOILER = 8
    private const val ACTION_UNMARK_AS_SPOILER = 9
  }

}