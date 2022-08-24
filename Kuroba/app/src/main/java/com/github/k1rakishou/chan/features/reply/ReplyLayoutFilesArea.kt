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
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.features.reply.data.ReplyFileAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyNewAttachable
import com.github.k1rakishou.chan.features.reply.data.TooManyAttachables
import com.github.k1rakishou.chan.features.reply.epoxy.epoxyAttachNewFileButtonView
import com.github.k1rakishou.chan.features.reply.epoxy.epoxyAttachNewFileButtonWideView
import com.github.k1rakishou.chan.features.reply.epoxy.epoxyReplyFileView
import com.github.k1rakishou.chan.features.reply_image_search.ImageSearchController
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextViewWrapHeight
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import com.github.k1rakishou.chan.ui.helper.picker.AbstractFilePicker
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.setAlphaFast
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

class ReplyLayoutFilesArea @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attributeSet, defStyleAttr), ReplyLayoutFilesAreaView {

  @Inject
  lateinit var _appConstants: Lazy<AppConstants>
  @Inject
  lateinit var _replyManager: Lazy<ReplyManager>
  @Inject
  lateinit var _imagePickHelper: Lazy<ImagePickHelper>
  @Inject
  lateinit var _dialogFactory: Lazy<DialogFactory>
  @Inject
  lateinit var _postingLimitationsInfoManager: Lazy<PostingLimitationsInfoManager>
  @Inject
  lateinit var _boardManager: Lazy<BoardManager>
  @Inject
  lateinit var _imageLoaderV2: Lazy<ImageLoaderV2>
  @Inject
  lateinit var _runtimePermissionsHelper: Lazy<RuntimePermissionsHelper>
  @Inject
  lateinit var _globalWindowInsetsManager: Lazy<GlobalWindowInsetsManager>

  private val controller = ReplyFilesEpoxyController()
  private val epoxyRecyclerView: ColorizableEpoxyRecyclerView

  private var threadListLayoutCallbacks: ThreadListLayoutCallbacks? = null
  private var replyLayoutCallbacks: ReplyLayoutCallbacks? = null
  private var scope: CoroutineScope? = null

  private val appConstants: AppConstants
    get() = _appConstants.get()
  private val replyManager: ReplyManager
    get() = _replyManager.get()
  private val imagePickHelper: ImagePickHelper
    get() = _imagePickHelper.get()
  private val dialogFactory: DialogFactory
    get() = _dialogFactory.get()
  private val postingLimitationsInfoManager: PostingLimitationsInfoManager
    get() = _postingLimitationsInfoManager.get()
  private val boardManager: BoardManager
    get() = _boardManager.get()
  private val imageLoaderV2: ImageLoaderV2
    get() = _imageLoaderV2.get()
  private val runtimePermissionsHelper: RuntimePermissionsHelper
    get() = _runtimePermissionsHelper.get()
  private val globalWindowInsetsManager: GlobalWindowInsetsManager
    get() = _globalWindowInsetsManager.get()

  private val presenter by lazy {
    return@lazy ReplyLayoutFilesAreaPresenter(
      context = context,
      appConstants = appConstants,
      replyManager = _replyManager,
      boardManager = _boardManager,
      imageLoaderV2 = _imageLoaderV2,
      postingLimitationsInfoManager = _postingLimitationsInfoManager,
      imagePickHelper = _imagePickHelper,
      runtimePermissionsHelper = runtimePermissionsHelper
    )
  }

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    View.inflate(context, R.layout.layout_reply_files_area, this)
    epoxyRecyclerView = findViewById(R.id.epoxy_recycler_view)
    epoxyRecyclerView.setController(controller)

    updateLayoutManager(context, isReplyLayoutExpanded = false)
  }

  fun onCreate() {
    presenter.onCreate(this@ReplyLayoutFilesArea)
  }

  fun updateLayoutManager(isReplyLayoutExpanded: Boolean) {
    updateLayoutManager(context, isReplyLayoutExpanded)
  }

  suspend fun onBind(
    chanDescriptor: ChanDescriptor,
    threadListLayoutCallbacks: ThreadListLayoutCallbacks,
    replyLayoutCallbacks: ReplyLayoutCallbacks
  ) {
    this.threadListLayoutCallbacks = threadListLayoutCallbacks
    this.replyLayoutCallbacks = replyLayoutCallbacks

    this.scope?.cancel()
    val newScope = MainScope()
    this.scope = newScope

    presenter.bindChanDescriptor(chanDescriptor)

    epoxyRecyclerView.layoutManager = GridLayoutManager(context, MIN_FILES_PER_ROW)
      .apply { spanSizeLookup = controller.spanSizeLookup }

    newScope.launch {
      presenter.listenForStateUpdates()
        .collect { state -> renderState(state) }
    }
  }

  fun onUnbind() {
    this.threadListLayoutCallbacks?.hideLoadingView()

    this.threadListLayoutCallbacks = null
    this.replyLayoutCallbacks = null

    this.scope?.cancel()
    this.scope = null

    presenter.unbindChanDescriptor()
    presenter.onDestroy()

    epoxyRecyclerView.clear()
  }

  fun onWrappingModeChanged(matchParent: Boolean) {
    if (presenter.hasAttachedFiles()) {
      val attachNewFileButtonHeight = getDimen(R.dimen.attach_new_file_button_height)

      epoxyRecyclerView.updateLayoutParams<ConstraintLayout.LayoutParams> {
        if (matchParent) {
          // Use full height
          matchConstraintMaxHeight = 0
        } else {
          matchConstraintMaxHeight = attachNewFileButtonHeight + BOTTOM_OFFSET
        }
      }
    } else {
      val attachNewFileButtonWideHeight = getDimen(R.dimen.attach_new_file_button_wide_height)
      val buttonMargins = getDimen(R.dimen.attach_new_file_button_vertical_margin) * 2

      epoxyRecyclerView.updateLayoutParams<ConstraintLayout.LayoutParams> {
        matchConstraintMaxHeight = attachNewFileButtonWideHeight + buttonMargins
      }
    }
  }

  private fun renderState(state: ReplyLayoutFilesState) {
    if (state.isEmpty()) {
      return
    }

    controller.callback = stateRenderer@ {
      if (state.loading) {
        epoxyLoadingView {
          id("reply_layout_files_area_loading_view")
        }
        return@stateRenderer
      }

      val attachables = state.attachables

      if (attachables.size == 1 && attachables.first() is ReplyNewAttachable) {
        epoxyAttachNewFileButtonWideView {
          id("epoxy_attach_new_file_button_wide_view")
          onClickListener { presenter.pickLocalFile(showFilePickerChooser = false) }
          onLongClickListener { showPickFileOptions() }
          onAttachImageByUrlClickListener { onPickFileItemClicked(ACTION_PICK_REMOTE_FILE) }
          onImageRemoteSearchClickListener { onPickFileItemClicked(ACTION_USE_REMOTE_IMAGE_SEARCH) }
        }

        return@stateRenderer
      }

      attachables.forEach { replyAttachable ->
        when (replyAttachable) {
          is TooManyAttachables -> {
            val message = context.getString(
              R.string.layout_reply_files_area_too_many_attachables_text,
              replyAttachable.attachablesTotal,
              ReplyLayoutFilesAreaPresenter.MAX_VISIBLE_ATTACHABLES_COUNT
            )

            epoxyTextViewWrapHeight {
              id("epoxy_reply_too_many_attachables_view")
              message(message)
            }
          }
          is ReplyNewAttachable -> {
            epoxyAttachNewFileButtonView {
              id("epoxy_attach_new_file_button_view")
              expandedMode(state.isReplyLayoutExpanded)
              onClickListener { presenter.pickLocalFile(showFilePickerChooser = false) }
              onLongClickListener { showPickFileOptions() }
              onAttachImageByUrlClickListener { onPickFileItemClicked(ACTION_PICK_REMOTE_FILE) }
              onImageRemoteSearchClickListener { onPickFileItemClicked(ACTION_USE_REMOTE_IMAGE_SEARCH) }
            }
          }
          is ReplyFileAttachable -> {
            epoxyReplyFileView {
              id("epoxy_reply_file_view_${replyAttachable.fileUuid}")
              expandedMode(state.isReplyLayoutExpanded)
              attachmentFileUuid(replyAttachable.fileUuid)
              attachmentFileName(replyAttachable.fileName)
              attachmentSelected(replyAttachable.selected)
              attachmentSpoiler(replyAttachable.spoilerInfo)
              attachmentFileSize(replyAttachable.fileSize)
              attachmentFileDimensions(replyAttachable.imageDimensions)
              attachAdditionalInfo(replyAttachable.attachAdditionalInfo)
              exceedsMaxFilesPerPostLimit(replyAttachable.maxAttachedFilesCountExceeded)
              onRootClickListener { fileUuid -> onReplyFileRootViewClicked(fileUuid) }
              onRootLongClickListener { fileUuid -> showAttachFileOptions(fileUuid) }
              onCheckClickListener { fileUuid -> presenter.updateFileSelection(fileUuid) }
              onStatusIconClickListener { fileUuid -> presenter.onFileStatusRequested(fileUuid) }
              onSpoilerMarkClickListener { fileUuid -> presenter.updateFileSpoilerFlag(fileUuid) }
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

  private fun updateLayoutManager(context: Context, isReplyLayoutExpanded: Boolean) {
    epoxyRecyclerView.requestLayout()

    epoxyRecyclerView.doOnLayout {
      val epoxyRecyclerViewWidth = when {
        epoxyRecyclerView.width > 0 -> epoxyRecyclerView.width
        epoxyRecyclerView.measuredWidth > 0 -> epoxyRecyclerView.measuredWidth
        else -> throw IllegalStateException("View is not measured!")
      }

      val attachNewFileButtonWidth =
        getDimen(R.dimen.attach_new_file_button_width)

      val spanCount = if (isReplyLayoutExpanded) {
        MIN_FILES_PER_ROW
      } else {
        (epoxyRecyclerViewWidth / attachNewFileButtonWidth)
          .coerceAtLeast(MIN_FILES_PER_ROW)
      }

      epoxyRecyclerView.layoutManager = GridLayoutManager(context, spanCount)
        .apply { spanSizeLookup = controller.spanSizeLookup }

      presenter.refreshAttachedFiles(isReplyLayoutExpanded = isReplyLayoutExpanded)
    }
  }

  private fun showAttachFileOptions(selectedFileUuid: UUID) {
    val floatingListMenuItems = mutableListOf<FloatingListMenuItem>()

    floatingListMenuItems += FloatingListMenuItem(
      key = ACTION_DELETE_FILE,
      name = context.getString(R.string.layout_reply_files_area_delete_file_action),
      value = selectedFileUuid
    )

    if (presenter.hasSelectedFiles()) {
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

    if (!presenter.allFilesSelected()) {
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

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = floatingListMenuItems,
      itemClickListener = { item -> onAttachFileItemClicked(item) }
    )

    threadListLayoutCallbacks?.presentController(floatingListMenuController)
  }

  private fun onReplyFileRootViewClicked(clickedFileUuid: UUID) {
    threadListLayoutCallbacks?.showImageReencodingWindow(
      clickedFileUuid,
      presenter.isFileSupportedForReencoding(clickedFileUuid)
    )
  }

  private fun onAttachFileItemClicked(item: FloatingListMenuItem) {
    val id = item.key as Int
    val clickedFileUuid = item.value as UUID

    when (id) {
      ACTION_DELETE_FILE -> presenter.deleteFile(clickedFileUuid)
      ACTION_DELETE_FILES -> presenter.deleteSelectedFiles()
      ACTION_REMOVE_FILE_NAME -> presenter.removeSelectedFilesName()
      ACTION_REMOVE_METADATA -> presenter.removeSelectedFilesMetadata(context)
      ACTION_CHANGE_CHECKSUM -> presenter.changeSelectedFilesChecksum(context)
      ACTION_SELECT_ALL -> presenter.selectUnselectAll(selectAll = true)
      ACTION_UNSELECT_ALL -> presenter.selectUnselectAll(selectAll = false)
    }
  }

  private fun showPickFileOptions() {
    val floatingListMenuItems = mutableListOf<FloatingListMenuItem>()

    floatingListMenuItems += FloatingListMenuItem(
      key = ACTION_PICK_LOCAL_FILE_SHOW_ALL_FILE_PICKERS,
      name = context.getString(R.string.layout_reply_files_area_pick_local_file_show_pickers)
    )

    floatingListMenuItems += FloatingListMenuItem(
      key = ACTION_PICK_REMOTE_FILE,
      name = context.getString(R.string.layout_reply_files_area_pick_remote_file)
    )

    floatingListMenuItems += FloatingListMenuItem(
      key = ACTION_USE_REMOTE_IMAGE_SEARCH,
      name = context.getString(R.string.layout_reply_files_area_remote_image_search)
    )

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = floatingListMenuItems,
      itemClickListener = { item -> onPickFileItemClicked(item.key as Int) }
    )

    threadListLayoutCallbacks?.presentController(floatingListMenuController)
  }

  private fun onPickFileItemClicked(clickedItemKey: Int) {
    when (clickedItemKey) {
      ACTION_PICK_LOCAL_FILE_SHOW_ALL_FILE_PICKERS -> {
        presenter.pickLocalFile(showFilePickerChooser = true)
      }
      ACTION_PICK_REMOTE_FILE -> {
        dialogFactory.createSimpleDialogWithInput(
          context = context,
          titleTextId = R.string.enter_image_video_url,
          inputType = DialogFactory.DialogInputType.String,
          onValueEntered = { url -> presenter.pickRemoteFile(url) }
        )
      }
      ACTION_USE_REMOTE_IMAGE_SEARCH -> {
        val boundChanDescriptor = presenter.boundChanDescriptor
          ?: return

        val imageSearchController = ImageSearchController(
          context = context,
          boundChanDescriptor = boundChanDescriptor,
          onImageSelected = { imageUrl -> presenter.pickRemoteFile(imageUrl.toString()) }
        )

        replyLayoutCallbacks?.hideKeyboard()
        threadListLayoutCallbacks?.pushController(imageSearchController)
      }
    }
  }

  override fun showFilePickerErrorToast(filePickerError: AbstractFilePicker.FilePickerError) {
    BackgroundUtils.ensureMainThread()

    if (filePickerError.isCanceled()) {
      return
    }

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

  override fun showLoadingView(cancellationFunc: () -> Unit, titleTextId: Int) {
    BackgroundUtils.ensureMainThread()
    threadListLayoutCallbacks?.showLoadingView(cancellationFunc, titleTextId)
  }

  override fun hideLoadingView() {
    BackgroundUtils.ensureMainThread()
    threadListLayoutCallbacks?.hideLoadingView()
  }

  override fun showReplyLayoutMessage(message: String?, hideDelayMs: Int) {
    replyLayoutCallbacks?.openMessage(message, hideDelayMs)
  }

  override fun updateSelectedFilesCounter(selectedCount: Int, maxAllowedCount: Int, totalCount: Int) {
    replyLayoutCallbacks?.updateSelectedFilesCounter(selectedCount, maxAllowedCount, totalCount)
  }

  override fun showFileStatusMessage(fileStatusString: String) {
    dialogFactory.createSimpleInformationDialog(
      context,
      titleText = context.getString(R.string.attached_file_info),
      descriptionText = fileStatusString
    )
  }

  override fun onDontKeepActivitiesSettingDetected() {
    dialogFactory.createSimpleInformationDialog(
      context = context,
      titleText = getString(R.string.dont_keep_activities_setting_enabled),
      descriptionText = getString(R.string.dont_keep_activities_setting_enabled_description)
    )
  }

  fun onImageOptionsComplete() {
    presenter.refreshAttachedFiles()
  }

  fun enableOrDisable(enable: Boolean) {
    if (!enable) {
      setAlphaFast(0.6f)
    } else {
      setAlphaFast(1f)
    }
  }

  private inner class ReplyFilesEpoxyController : EpoxyController() {
    var callback: EpoxyController.() -> Unit = {}

    override fun buildModels() {
      callback(this)
    }

  }

  interface ThreadListLayoutCallbacks {
    fun presentController(controller: Controller)
    fun pushController(controller: Controller)
    fun showImageReencodingWindow(fileUuid: UUID, supportsReencode: Boolean)
    fun showLoadingView(cancellationFunc: () -> Unit, titleTextId: Int)
    fun hideLoadingView()
  }

  interface ReplyLayoutCallbacks {
    fun hideKeyboard()
    fun requestWrappingModeUpdate()
    fun openMessage(message: String?, hideDelayMs: Int)
    fun updateSelectedFilesCounter(selectedCount: Int, maxAllowedCount: Int, totalCount: Int)
    fun showReplyLayoutMessage(message: String, duration: Int = 5000)
  }

  companion object {
    private const val ACTION_DELETE_FILE = 1
    private const val ACTION_DELETE_FILES = 2
    private const val ACTION_REMOVE_FILE_NAME = 3
    private const val ACTION_REMOVE_METADATA = 4
    private const val ACTION_CHANGE_CHECKSUM = 5
    private const val ACTION_SELECT_ALL = 6
    private const val ACTION_UNSELECT_ALL = 7

    private const val ACTION_PICK_LOCAL_FILE_SHOW_ALL_FILE_PICKERS = 100
    private const val ACTION_PICK_REMOTE_FILE = 101
    private const val ACTION_USE_REMOTE_IMAGE_SEARCH = 102

    private const val MIN_FILES_PER_ROW = 2

    private val BOTTOM_OFFSET = dp(24f)
  }
}