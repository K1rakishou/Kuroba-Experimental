package com.github.k1rakishou.chan.features.reply

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.GridLayoutManager
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.features.reply.data.ReplyFileAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyNewAttachable
import com.github.k1rakishou.chan.features.reply.data.TooManyAttachables
import com.github.k1rakishou.chan.features.reply.epoxy.epoxyAttachNewFileButtonView
import com.github.k1rakishou.chan.features.reply.epoxy.epoxyAttachNewFileButtonWideView
import com.github.k1rakishou.chan.features.reply.epoxy.epoxyReplyFileView
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextViewWrapHeight
import com.github.k1rakishou.chan.ui.helper.picker.IFilePicker
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.ui.misc.ConstraintLayoutBiasPair
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.android.synthetic.main.layout_reply_input.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

class ReplyLayoutFilesArea @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attributeSet, defStyleAttr), ReplyLayoutFilesAreaView {

  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var replyManager: ReplyManager
  @Inject
  lateinit var imagePickHelper: ImagePickHelper

  private val controller = ReplyFilesEpoxyController()
  private val epoxyRecyclerView: ColorizableEpoxyRecyclerView

  private var threadListLayoutCallbacks: ThreadListLayoutCallbacks? = null
  private var replyLayoutCallbacks: ReplyLayoutCallbacks? = null
  private var scope: CoroutineScope? = null

  private val presenter by lazy {
    return@lazy ReplyLayoutFilesAreaPresenter(
      appConstants,
      replyManager,
      imagePickHelper
    )
  }

  // TODO(KurobaEx): reply layout refactoring: show file size
  // TODO(KurobaEx): reply layout refactoring: show warning when trying to open unsupported file
  // TODO(KurobaEx): reply layout refactoring: reset reply state to default (delete comment text) after post.
  // TODO(KurobaEx): reply layout refactoring: mark/unmark image as spolier
  // TODO(KurobaEx): reply layout refactoring: image editing
  // TODO(KurobaEx): reply layout refactoring: load image by url

  // TODO(KurobaEx): reply layout refactoring: warn when attach file exceeds size limits
  //  val probablyWebm = "webm" == StringUtils.extractFileNameExtension(name)
  //
  //  val maxSize = if (probablyWebm) {
  //    chanBoard.maxWebmSize
  //  } else {
  //    chanBoard.maxFileSize
  //  }
  //
  //  // if the max size is undefined for the board, ignore this message
  //  if (file != null && file.length() > maxSize && maxSize != -1) {
  //    val fileSize = ChanPostUtils.getReadableFileSize(file.length())
  //    val stringResId = if (probablyWebm) {
  //      R.string.reply_webm_too_big
  //    } else {
  //      R.string.reply_file_too_big
  //    }
  //
  //    callback.openPreviewMessage(
  //      true,
  //      AndroidUtils.getString(stringResId, fileSize,
  //        ChanPostUtils.getReadableFileSize(maxSize.toLong())
  //      )
  //    )
  //  } else {
  //    callback.openPreviewMessage(false, null)
  //  }

  init {
    AppModuleAndroidUtils.extractStartActivityComponent(context)
      .inject(this)

    View.inflate(context, R.layout.layout_reply_files_area, this)
    epoxyRecyclerView = findViewById(R.id.epoxy_recycler_view)
    epoxyRecyclerView.setController(controller)

    updateLayoutManager(context)
  }

  fun onCreate() {
    presenter.onCreate(this@ReplyLayoutFilesArea)
  }

  fun updateLayoutManager() {
    updateLayoutManager(context)
  }

  suspend fun onBind(
    chanDescriptor: ChanDescriptor,
    threadListLayoutCallbacks: ThreadListLayoutCallbacks,
    replyLayoutCallbacks: ReplyLayoutCallbacks
  ) {
    this.threadListLayoutCallbacks = threadListLayoutCallbacks
    this.replyLayoutCallbacks = replyLayoutCallbacks

    this.scope?.cancel()
    this.scope = MainScope()

    presenter.bindChanDescriptor(chanDescriptor)

    epoxyRecyclerView.layoutManager = GridLayoutManager(context, MIN_FILES_PER_ROW).apply {
      spanSizeLookup = controller.spanSizeLookup
    }

    scope!!.launch {
      presenter.listenForStateUpdates()
        .collect { state -> renderState(state) }
    }
  }

  fun onUnbind() {
    this.threadListLayoutCallbacks = null
    this.replyLayoutCallbacks = null

    this.scope?.cancel()
    this.scope = null

    presenter.unbindChanDescriptor()
    presenter.onDestroy()
  }

  fun onWrappingModeChanged(matchParent: Boolean) {
    if (presenter.hasAttachedFiles()) {
      val attachNewFileButtonHeight =
        AppModuleAndroidUtils.getDimen(R.dimen.attach_new_file_button_height)

      epoxyRecyclerView.updateLayoutParams<ConstraintLayout.LayoutParams> {
        if (matchParent) {
          matchConstraintMaxHeight = (attachNewFileButtonHeight * 3) + BOTTOM_OFFSET
        } else {
          matchConstraintMaxHeight = attachNewFileButtonHeight + BOTTOM_OFFSET
        }
      }
    } else {
      val attachNewFileButtonWideHeight =
        AppModuleAndroidUtils.getDimen(R.dimen.attach_new_file_button_wide_height)

      epoxyRecyclerView.updateLayoutParams<ConstraintLayout.LayoutParams> {
        matchConstraintMaxHeight = attachNewFileButtonWideHeight
      }
    }
  }

  fun onBackPressed(): Boolean {
    if (presenter.hasSelectedFiles()) {
      presenter.clearSelection()
      return true
    }

    return false
  }

  private fun renderState(state: ReplyLayoutFilesState) {
    if (state.isEmpty()) {
      return
    }

    controller.callback = stateRenderer@ {
      val attachables = state.attachables

      if (attachables.size == 1 && attachables.first() is ReplyNewAttachable) {
        epoxyAttachNewFileButtonWideView {
          id("epoxy_attach_new_file_button_wide_view")
          onClickListener { presenter.pickNewLocalFile() }
        }

        return@stateRenderer
      }

      attachables.forEach { replyAttachable ->
        when (replyAttachable) {
          is TooManyAttachables -> {
            val message = context.getString(
              R.string.layout_reply_files_area_too_many_attachables_text,
              replyAttachable.attachablesTotal,
              ReplyLayoutFilesAreaPresenter.MAX_ATTACHABLES_COUNT
            )

            epoxyTextViewWrapHeight {
              id("epoxy_reply_too_many_attachables_view")
              message(message)
            }
          }
          is ReplyNewAttachable -> {
            epoxyAttachNewFileButtonView {
              id("epoxy_attach_new_file_button_view")
              onClickListener { presenter.pickNewLocalFile() }
            }
          }
          is ReplyFileAttachable -> {
            epoxyReplyFileView {
              id("epoxy_reply_file_view_${replyAttachable.fileUuid}")
              attachmentFileUuid(replyAttachable.fileUuid)
              attachmentFileName(replyAttachable.fileName)
              attachmentSelected(replyAttachable.selected)
              exceedsMaxFilesPerPostLimit(replyAttachable.exceedsMaxFilesLimit)
              onClickListener { fileUuid -> presenter.updateFileSelection(fileUuid) }
              onLongClickListener { fileUuid -> showOptionsController(fileUuid) }
            }
          }
          else -> throw IllegalStateException(
            "Unknown replyAttachable: ${replyAttachable.javaClass.simpleName}"
          )
        }
      }
    }

    controller.requestModelBuild()
  }

  private fun updateLayoutManager(context: Context) {
    epoxyRecyclerView.requestLayout()

    epoxyRecyclerView.doOnLayout {
      val epoxyRecyclerViewWidth = when {
        epoxyRecyclerView.width > 0 -> epoxyRecyclerView.width
        epoxyRecyclerView.measuredWidth > 0 -> epoxyRecyclerView.measuredWidth
        else -> throw IllegalStateException("View is not measured!")
      }

      val spanCount =
        (epoxyRecyclerViewWidth / AppModuleAndroidUtils.getDimen(R.dimen.attach_new_file_button_width))
          .coerceAtLeast(MIN_FILES_PER_ROW)

      epoxyRecyclerView.layoutManager = GridLayoutManager(context, spanCount).apply {
        spanSizeLookup = controller.spanSizeLookup
      }

      presenter.refreshAttachedFiles()
    }
  }

  private fun showOptionsController(selectedFileUuid: UUID) {
    val floatingListMenuItems = mutableListOf<FloatingListMenuItem>()

    floatingListMenuItems += FloatingListMenuItem(
      key = ACTION_OPEN_IN_EDITOR,
      name = context.getString(R.string.layout_reply_files_area_open_in_editor_action),
      value = selectedFileUuid
    )

    floatingListMenuItems += FloatingListMenuItem(
      key = ACTION_DELETE_FILE,
      name = context.getString(R.string.layout_reply_files_area_delete_file_action),
      value = selectedFileUuid
    )

    if (presenter.selectedFilesCount() > 1) {
      floatingListMenuItems += FloatingListMenuItem(
        key = ACTION_DELETE_SELECTED_FILES,
        name = context.getString(R.string.layout_reply_files_area_delete_selected_files_action),
        value = selectedFileUuid
      )
    }

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBiasPair = ConstraintLayoutBiasPair.Bottom,
      items = floatingListMenuItems,
      itemClickListener = { item -> onItemClicked(item) }
    )

    threadListLayoutCallbacks?.presentController(floatingListMenuController)
  }

  private fun onItemClicked(item: FloatingListMenuItem) {
    val id = item.key as Int
    val clickedFileUuid = item.value as UUID

    when (id) {
      ACTION_OPEN_IN_EDITOR -> {
        // TODO(KurobaEx): reply layout refactoring
      }
      ACTION_DELETE_FILE -> {
        presenter.deleteFiles(clickedFileUuid)
      }
      ACTION_DELETE_SELECTED_FILES -> {
        presenter.deleteSelectedFiles()
      }
    }
  }

  override fun showFilePickerErrorToast(filePickerError: IFilePicker.FilePickerError) {
    BackgroundUtils.ensureMainThread()
    showToast(context, filePickerError.errorMessageOrClassName(), Toast.LENGTH_LONG)
  }

  override fun showGenericErrorToast(errorMessage: String) {
    BackgroundUtils.ensureMainThread()
    showToast(context, errorMessage, Toast.LENGTH_LONG)
  }

  override fun requestReplyLayoutWrappingModeUpdate() {
    BackgroundUtils.ensureMainThread()
    replyLayoutCallbacks?.requestWrappingModeUpdate()
  }

  override fun showLoadingView() {
    BackgroundUtils.ensureMainThread()
    threadListLayoutCallbacks?.showLoadingView()
  }

  override fun hideLoadingView() {
    BackgroundUtils.ensureMainThread()
    threadListLayoutCallbacks?.hideLoadingView()
  }

  override fun updateSendButtonState(attachedSelectedFilesCount: Int, maxAllowedAttachedFilesCount: Int) {
    BackgroundUtils.ensureMainThread()

    if (attachedSelectedFilesCount == 0 || attachedSelectedFilesCount <= maxAllowedAttachedFilesCount) {
      replyLayoutCallbacks?.enableSendButton()
    } else {
      replyLayoutCallbacks?.disableSendButton()
    }
  }

  private inner class ReplyFilesEpoxyController : EpoxyController() {
    var callback: EpoxyController.() -> Unit = {}

    override fun buildModels() {
      callback(this)
    }

  }

  interface ThreadListLayoutCallbacks {
    fun presentController(controller: FloatingListMenuController)
    fun showLoadingView()
    fun hideLoadingView()
  }

  interface ReplyLayoutCallbacks {
    fun requestWrappingModeUpdate()
    fun disableSendButton()
    fun enableSendButton()
  }

  companion object {
    private const val ACTION_OPEN_IN_EDITOR = 1
    private const val ACTION_DELETE_FILE = 2
    private const val ACTION_DELETE_SELECTED_FILES = 3

    private const val MIN_FILES_PER_ROW = 2

    private val BOTTOM_OFFSET = dp(24f)
  }
}