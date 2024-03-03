package com.github.k1rakishou.chan.features.reply

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.viewmodel.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.repository.BoardFlagInfoRepository
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.features.posting.PostingService
import com.github.k1rakishou.chan.features.posting.PostingServiceDelegate
import com.github.k1rakishou.chan.features.reply.data.PostFormattingButtonsFactory
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.features.reply.data.ReplyFileAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutHelper
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.features.reply.data.SendReplyState
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.rethrowCancellationException
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.persist_state.ReplyMode
import com.github.k1rakishou.prefs.OptionsSetting
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class ReplyLayoutViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val appContext: Context,
  private val appResourcesLazy: Lazy<AppResources>,
  private val appConstantsLazy: Lazy<AppConstants>,
  private val siteManagerLazy: Lazy<SiteManager>,
  private val replyLayoutHelperLazy: Lazy<ReplyLayoutHelper>,
  private val boardManagerLazy: Lazy<BoardManager>,
  private val replyManagerLazy: Lazy<ReplyManager>,
  private val postFormattingButtonsFactoryLazy: Lazy<PostFormattingButtonsFactory>,
  private val themeEngineLazy: Lazy<ThemeEngine>,
  private val globalUiStateHolderLazy: Lazy<GlobalUiStateHolder>,
  private val captchaHolderLazy: Lazy<CaptchaHolder>,
  private val postingServiceDelegateLazy: Lazy<PostingServiceDelegate>,
  private val boardFlagInfoRepositoryLazy: Lazy<BoardFlagInfoRepository>,
  private val runtimePermissionsHelperLazy: Lazy<RuntimePermissionsHelper>,
  private val imagePickHelperLazy: Lazy<ImagePickHelper>
) : BaseViewModel(), ReplyLayoutState.Callbacks {
  private val _replyManagerStateLoaded = AtomicBoolean(false)

  private val _boundChanDescriptor = mutableStateOf<ChanDescriptor?>(null)
  val boundChanDescriptor: State<ChanDescriptor?>
    get() = _boundChanDescriptor

  private val _replyLayoutState = mutableStateOf<ReplyLayoutState?>(null)
  val replyLayoutState: State<ReplyLayoutState?>
    get() = _replyLayoutState
  private val currentReplyLayoutState: ReplyLayoutState?
    get() = _replyLayoutState.value

  private val appConstants: AppConstants
    get() = appConstantsLazy.get()
  private val siteManager: SiteManager
    get() = siteManagerLazy.get()
  private val replyManager: ReplyManager
    get() = replyManagerLazy.get()
  private val captchaHolder: CaptchaHolder
    get() = captchaHolderLazy.get()
  private val postingServiceDelegate: PostingServiceDelegate
    get() = postingServiceDelegateLazy.get()
  private val appResources: AppResources
    get() = appResourcesLazy.get()

  private val threadControllerType by lazy {
    requireNotNull(savedStateHandle.get<ThreadControllerType>(ThreadControllerTypeParam)) {
      "'${ThreadControllerTypeParam}' was not passed as a parameter of this ViewModel"
    }
  }

  val captchaHolderCaptchaCounterUpdatesFlow: Flow<Int>
    get() = captchaHolder.listenForCaptchaUpdates()

  private var sendReplyJob: Job? = null
  private var listenForPostingStatusUpdatesJob: Job? = null
  private var threadListLayoutCallbacks: ThreadListLayoutCallbacks? = null
  private var replyLayoutViewCallbacks: ReplyLayoutViewCallbacks? = null

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    Logger.debug(TAG) {
      "onViewModelReady() threadControllerType: ${threadControllerType}, instance: ${this.hashCode()}"
    }

    viewModelScope.launch { reloadReplyManagerState() }
  }

  fun bindThreadListLayoutCallbacks(callbacks: ThreadListLayoutCallbacks) {
    threadListLayoutCallbacks = callbacks
  }

  fun bindReplyLayoutViewCallbacks(callbacks: ReplyLayoutViewCallbacks) {
    replyLayoutViewCallbacks = callbacks
  }

  fun unbindCallbacks() {
    threadListLayoutCallbacks = null
    replyLayoutViewCallbacks = null
  }

  override fun showCaptcha(
    chanDescriptor: ChanDescriptor,
    replyMode: ReplyMode,
    autoReply: Boolean,
    afterPostingAttempt: Boolean
  ) {
    threadListLayoutCallbacks?.showCaptcha(chanDescriptor, replyMode, autoReply, afterPostingAttempt)
  }

  override fun showDialog(
    title: String,
    message: CharSequence,
    onDismissListener: (() -> Unit)?
  ) {
    replyLayoutViewCallbacks?.showDialog(title, message, onDismissListener)
  }

  override fun hideDialog() {
    replyLayoutViewCallbacks?.hideDialog()
  }

  override fun showToast(message: String) {
    replyLayoutViewCallbacks?.showToast(message)
  }

  override suspend fun onPostedSuccessfully(
    prevChanDescriptor: ChanDescriptor,
    newThreadDescriptor: ChanDescriptor.ThreadDescriptor
  ) {
    threadListLayoutCallbacks?.onPostedSuccessfully(prevChanDescriptor, newThreadDescriptor)
  }

  suspend fun bindChanDescriptor(chanDescriptor: ChanDescriptor, threadControllerType: ThreadControllerType) {
    replyManager.awaitUntilFilesAreLoaded()

    if (_boundChanDescriptor.value == chanDescriptor) {
      return
    }

    _boundChanDescriptor.value = chanDescriptor

    listenForPostingStatusUpdatesJob?.cancel()
    listenForPostingStatusUpdatesJob = viewModelScope.launch {
      postingServiceDelegate.listenForPostingStatusUpdates(chanDescriptor)
        .onEach { postingStatus -> replyLayoutState.value?.onPostingStatusEvent(postingStatus) }
        .collect()
    }

    val replyLayoutState = ReplyLayoutState(
      chanDescriptor = chanDescriptor,
      threadControllerType = threadControllerType,
      callbacks = this,
      coroutineScope = viewModelScope,
      appResourcesLazy = appResourcesLazy,
      replyLayoutHelperLazy = replyLayoutHelperLazy,
      siteManagerLazy = siteManagerLazy,
      boardManagerLazy = boardManagerLazy,
      replyManagerLazy = replyManagerLazy,
      postFormattingButtonsFactoryLazy = postFormattingButtonsFactoryLazy,
      themeEngineLazy = themeEngineLazy,
      globalUiStateHolderLazy = globalUiStateHolderLazy,
      postingServiceDelegateLazy = postingServiceDelegateLazy,
      boardFlagInfoRepositoryLazy = boardFlagInfoRepositoryLazy,
      runtimePermissionsHelperLazy = runtimePermissionsHelperLazy,
      imagePickHelperLazy = imagePickHelperLazy
    )

    _replyLayoutState.value?.unbindChanDescriptor()
    replyLayoutState.bindChanDescriptor(chanDescriptor)

    _replyLayoutState.value = replyLayoutState
  }

  fun cleanup() {
    _replyLayoutState.value?.unbindChanDescriptor()

    // TODO: New reply layout
    sendReplyJob?.cancel()
    sendReplyJob = null

    listenForPostingStatusUpdatesJob?.cancel()
    listenForPostingStatusUpdatesJob = null
  }

  fun onBack(): Boolean {
    val replyLayoutState = currentReplyLayoutState
      ?: return false

    return when (replyLayoutState.replyLayoutVisibility.value) {
      ReplyLayoutVisibility.Collapsed -> false
      ReplyLayoutVisibility.Opened -> {
        replyLayoutState.collapseReplyLayout()
        return true
      }
      ReplyLayoutVisibility.Expanded -> {
        replyLayoutState.openReplyLayout()
        return true
      }
    }
  }

  suspend fun getReplyFileByUuid(fileUUID: UUID): ModularResult<ReplyFile> {
    return withContext(Dispatchers.IO) {
      return@withContext replyManager.getReplyFileByFileUuid(fileUUID)
        .mapValue { replyFile ->
          if (replyFile == null) {
            throw ReplyFileDoesNotExist(fileUUID)
          }

          return@mapValue replyFile
        }
    }
  }

  fun isCatalogMode(): Boolean? {
    return _boundChanDescriptor.value?.isCatalogDescriptor()
  }

  fun replyLayoutVisibility(): ReplyLayoutVisibility {
    val replyLayoutState = currentReplyLayoutState
      ?: return ReplyLayoutVisibility.Collapsed

    return replyLayoutState.replyLayoutVisibility.value
  }

  fun enqueueReply(chanDescriptor: ChanDescriptor, replyMode: ReplyMode? = null, retrying: Boolean = false) {
    withReplyLayoutState {
      if (sendReplyJob?.isActive == true) {
        return@withReplyLayoutState
      }

      sendReplyJob = viewModelScope.launch {
        var success = false
        val replyLayoutState = replyLayoutState.value

        try {
          if (threadListLayoutCallbacks == null || replyLayoutViewCallbacks == null) {
            Logger.debug(TAG) { "enqueueReply(${chanDescriptor}) callbacks are null" }
            return@launch
          }

          if (replyLayoutState == null) {
            Logger.debug(TAG) { "enqueueReply(${chanDescriptor}) replyLayoutState is null" }
            return@launch
          }

          if (!isReplyLayoutEnabled()) {
            Logger.debug(TAG) {
              "enqueueReply(${chanDescriptor}) Canceling posting attempt for ${chanDescriptor} because cancel button was clicked"
            }

            postingServiceDelegate.cancel(chanDescriptor)
            return@launch
          }

          if (replyLayoutState.sendReplyState.value != SendReplyState.Finished) {
            val sendReplyState = replyLayoutState.sendReplyState.value
            Logger.debug(TAG) {
              "enqueueReply(${chanDescriptor}) sendReplyState is not SendReplyState.Finished (sendReplyState: ${sendReplyState})"
            }

            return@launch
          }

          replyLayoutState.onSendReplyStart()

          Logger.debug(TAG) { "enqueueReply(${chanDescriptor}) start" }

          var actualReplyMode = replyMode
          if (actualReplyMode == null) {
            actualReplyMode = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
              ?.getSettingBySettingId<OptionsSetting<ReplyMode>>(SiteSetting.SiteSettingId.LastUsedReplyMode)
              ?.get()
              ?: ReplyMode.Unknown
          }

          success = enqueueNewReply(
            chanDescriptor = chanDescriptor,
            replyLayoutState = replyLayoutState,
            replyMode = actualReplyMode,
            retrying = retrying
          )

          if (success) {
            Logger.debug(TAG) { "enqueueReply(${chanDescriptor}) reply enqueued" }
            replyLayoutState.onReplyEnqueued()
          } else {
            Logger.debug(TAG) { "enqueueReply(${chanDescriptor}) failed to enqueue reply" }
          }

          Logger.debug(TAG) { "enqueueReply(${chanDescriptor}) end" }
        } catch (error: Throwable) {
          Logger.error(TAG) { "enqueueReply(${chanDescriptor}) unhandled error: ${error}" }

          error.rethrowCancellationException()
        } finally {
          if (!success) {
            replyLayoutState?.onSendReplyEnd()
          }

          sendReplyJob = null
        }
      }
    }
  }

  fun cancelSendReply() {
    withReplyLayoutState {
      sendReplyJob?.cancel()
      sendReplyJob = viewModelScope.launch {
        try {
          val chanDescriptor = boundChanDescriptor.value
            ?: return@launch

          postingServiceDelegate.cancel(chanDescriptor)
        } finally {
          sendReplyJob = null
        }
      }
    }
  }

  fun onAttachedMediaClicked(attachedMedia: ReplyFileAttachable) {
    withReplyLayoutState {
      replyLayoutViewCallbacks?.onAttachedMediaClicked(attachedMedia)
    }
  }

  fun onAttachedMediaLongClicked(attachedMedia: ReplyFileAttachable) {
    withReplyLayoutState {
      replyLayoutViewCallbacks?.onAttachedMediaLongClicked(attachedMedia)
    }
  }

  fun removeAttachedMedia(attachedMedia: ReplyFileAttachable) {
    withReplyLayoutState { replyLayoutState ->
      replyLayoutState.removeAttachedMedia(attachedMedia)
    }
  }

  fun onAttachableSelectionChanged(attachedMedia: ReplyFileAttachable, selected: Boolean) {
    withReplyLayoutState { replyLayoutState ->
      replyLayoutState.onAttachableSelectionChanged(attachedMedia, selected)
    }
  }

  fun onAttachableStatusIconButtonClicked(replyFileAttachable: ReplyFileAttachable) {
    withReplyLayoutState { replyLayoutState ->
      viewModelScope.launch {
        val attachableFileStatus = replyLayoutState.attachableFileStatus(replyFileAttachable)
        replyLayoutViewCallbacks?.showFileStatusDialog(attachableFileStatus)
      }
    }
  }

  fun onFlagSelectorClicked(chanDescriptor: ChanDescriptor) {
    withReplyLayoutState { replyLayoutState ->
      TODO("New reply layout")
    }
  }

  fun updateReplyLayoutVisibility(newReplyLayoutVisibility: ReplyLayoutVisibility) {
    withReplyLayoutState { replyLayoutState ->
      when (newReplyLayoutVisibility) {
        ReplyLayoutVisibility.Collapsed -> replyLayoutState.collapseReplyLayout()
        ReplyLayoutVisibility.Opened -> replyLayoutState.openReplyLayout()
        ReplyLayoutVisibility.Expanded -> replyLayoutState.expandReplyLayout()
      }
    }
  }

  fun quote(post: ChanPost, withText: Boolean) {
    withReplyLayoutState { replyLayoutState ->
      TODO("New reply layout")
    }
  }

  fun quote(postDescriptor: PostDescriptor, text: CharSequence) {
    withReplyLayoutState { replyLayoutState ->
      TODO("New reply layout")
    }
  }

  fun onImageOptionsApplied() {
    // no-op
  }

  fun onPickLocalMediaButtonClicked() {
    // TODO: New reply layout
//    if (AppModuleAndroidUtils.checkDontKeepActivitiesSettingEnabledForWarningDialog(context)) {
//      withViewNormal { onDontKeepActivitiesSettingDetected() }
//      return
//    }

    withReplyLayoutState { replyLayoutState -> replyLayoutState.pickLocalMedia(showFilePickerChooser = false) }
  }

  fun onPickLocalMediaButtonLongClicked() {
    // TODO: New reply layout
//    if (AppModuleAndroidUtils.checkDontKeepActivitiesSettingEnabledForWarningDialog(context)) {
//      withViewNormal { onDontKeepActivitiesSettingDetected() }
//      return
//    }

    withReplyLayoutState { replyLayoutState -> replyLayoutState.pickLocalMedia(showFilePickerChooser = true) }
  }

  fun onRemoteImageSelected(selectedImageUrl: HttpUrl) {
    withReplyLayoutState { replyLayoutState -> replyLayoutState.pickRemoteMedia(selectedImageUrl) }
  }

  fun onPickRemoteMediaButtonClicked() {
    withReplyLayoutState { replyLayoutState ->
      // TODO: New reply layout
    }
  }

  fun onReplyLayoutOptionsButtonClicked() {
    replyLayoutViewCallbacks?.onReplyLayoutOptionsButtonClicked()
  }

  private suspend fun enqueueNewReply(
    chanDescriptor: ChanDescriptor,
    replyLayoutState: ReplyLayoutState,
    replyMode: ReplyMode,
    retrying: Boolean
  ): Boolean {
    Logger.debug(TAG) { "enqueueNewReply(${chanDescriptor}) replyMode: ${replyMode}, retrying: ${retrying}" }

    if (replyMode == ReplyMode.Unknown) {
      onReplyLayoutOptionsButtonClicked()
      return false
    }

    if (!onPrepareToEnqueue(chanDescriptor, replyLayoutState)) {
      Logger.debug(TAG) { "enqueueNewReply(${chanDescriptor}) onPrepareToSubmit() -> false" }
      return false
    }

    if (replyMode == ReplyMode.ReplyModeSolveCaptchaManually && !captchaHolder.hasSolution()) {
      Logger.debug(TAG) { "enqueueNewReply(${chanDescriptor}) no captcha solution, showing captcha" }

      threadListLayoutCallbacks?.showCaptcha(
        chanDescriptor = chanDescriptor,
        replyMode = replyMode,
        autoReply = true,
        afterPostingAttempt = false
      )

      return false
    }

    PostingService.enqueueReplyChanDescriptor(
      context = appContext,
      chanDescriptor = chanDescriptor,
      replyMode = replyMode,
      retrying = retrying
    )

    Logger.debug(TAG) { "enqueueNewReply(${chanDescriptor}) success" }
    return true
  }

  private suspend fun onPrepareToEnqueue(
    chanDescriptor: ChanDescriptor,
    replyLayoutState: ReplyLayoutState
  ): Boolean {
    val hasSelectedFiles = replyManager.hasSelectedFiles()
      .onError { error ->
        Logger.error(TAG, error) {
          "onPrepareToEnqueue(${chanDescriptor}) replyManager.hasSelectedFiles() -> error"
        }
      }
      .valueOrNull()

    if (hasSelectedFiles == null) {
      Logger.debug(TAG) { "onPrepareToEnqueue(${chanDescriptor}) hasSelectedFiles == null" }
      replyLayoutViewCallbacks?.showDialog(
        appResources.string(R.string.reply_layout_dialog_title),
        appResources.string(R.string.reply_failed_to_prepare_reply)
      )

      return false
    }

    if (!replyLayoutState.loadViewsIntoDraft()) {
      Logger.debug(TAG) { "onPrepareToEnqueue(${chanDescriptor}) loadViewsIntoDraft() -> false" }
      replyLayoutViewCallbacks?.showDialog(
        appResources.string(R.string.reply_layout_dialog_title),
        appResources.string(R.string.reply_failed_to_load_drafts_data_into_reply)
      )

      return false
    }

    return replyManager.readReply(chanDescriptor) { reply ->
      if (!hasSelectedFiles && reply.isCommentEmpty()) {
        Logger.debug(TAG) { "onPrepareToEnqueue(${chanDescriptor}) reply is empty" }
        replyLayoutViewCallbacks?.showDialog(
          appResources.string(R.string.reply_layout_dialog_title),
          appResources.string(R.string.reply_comment_empty)
        )

        return@readReply false
      }

      reply.resetCaptcha()
      Logger.debug(TAG) { "onPrepareToEnqueue(${chanDescriptor}) success" }

      return@readReply true
    }
  }

  private suspend fun isReplyLayoutEnabled(): Boolean {
    val descriptor = boundChanDescriptor.value
      ?: return true

    return postingServiceDelegate.isReplyCurrentlyInProgress(descriptor)
  }

  private suspend fun reloadReplyManagerState() {
    if (!_replyManagerStateLoaded.compareAndSet(false, true)) {
      return
    }

    withContext(Dispatchers.IO) {
      replyManager.reloadReplyManagerStateFromDisk(appConstants)
        .unwrap()

      // TODO: New reply layout. Do we really need to do this? Seems pointless.
      replyManager.iterateFilesOrdered { _, _, replyFileMeta ->
        if (replyFileMeta.selected) {
          replyManager.updateFileSelection(
            fileUuid = replyFileMeta.fileUuid,
            selected = true,
            notifyListeners = false
          )
        }
      }
    }
  }

  private fun withReplyLayoutState(block: (ReplyLayoutState) -> Unit) {
    val replyLayoutState = currentReplyLayoutState
      ?: return

    val chanDescriptor = boundChanDescriptor.value
    if (chanDescriptor == null) {
      replyLayoutState.collapseReplyLayout()
      return
    }

    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      replyLayoutState.collapseReplyLayout()
      return
    }

    block(replyLayoutState)
  }

  interface ThreadListLayoutCallbacks {
    fun onPresolveCaptchaButtonClicked()

    fun showCaptcha(
      chanDescriptor: ChanDescriptor,
      replyMode: ReplyMode,
      autoReply: Boolean,
      afterPostingAttempt: Boolean,
      onFinished: ((Boolean) -> Unit)? = null
    )

    suspend fun onPostedSuccessfully(
      prevChanDescriptor: ChanDescriptor,
      newThreadDescriptor: ChanDescriptor.ThreadDescriptor
    )

    fun presentController(controller: BaseFloatingController)
    fun pushController(controller: Controller)
  }

  interface ReplyLayoutViewCallbacks {
    suspend fun showDialogSuspend(title: String, message: CharSequence?)
    fun showDialog(title: String, message: CharSequence?, onDismissListener: (() -> Unit)? = null)
    fun hideDialog()
    fun showToast(message: String)

    fun onSearchRemoteMediaButtonClicked()
    fun onReplyLayoutOptionsButtonClicked()
    fun onAttachedMediaClicked(attachedMedia: ReplyFileAttachable)
    fun onAttachedMediaLongClicked(attachedMedia: ReplyFileAttachable)
    fun showFileStatusDialog(attachableFileStatus: AnnotatedString)
  }

  class ReplyFileDoesNotExist(fileUUID: UUID) : ClientException("Reply file with UUID '${fileUUID}' does not exist")

  class ViewModelFactory @Inject constructor(
    private val appResourcesLazy: Lazy<AppResources>,
    private val appContext: Context,
    private val appConstantsLazy: Lazy<AppConstants>,
    private val siteManagerLazy: Lazy<SiteManager>,
    private val replyLayoutHelperLazy: Lazy<ReplyLayoutHelper>,
    private val boardManagerLazy: Lazy<BoardManager>,
    private val replyManagerLazy: Lazy<ReplyManager>,
    private val postFormattingButtonsFactoryLazy: Lazy<PostFormattingButtonsFactory>,
    private val themeEngineLazy: Lazy<ThemeEngine>,
    private val globalUiStateHolderLazy: Lazy<GlobalUiStateHolder>,
    private val captchaHolderLazy: Lazy<CaptchaHolder>,
    private val postingServiceDelegateLazy: Lazy<PostingServiceDelegate>,
    private val boardFlagInfoRepositoryLazy: Lazy<BoardFlagInfoRepository>,
    private val runtimePermissionsHelperLazy: Lazy<RuntimePermissionsHelper>,
    private val imagePickHelperLazy: Lazy<ImagePickHelper>
  ) : ViewModelAssistedFactory<ReplyLayoutViewModel> {
    override fun create(handle: SavedStateHandle): ReplyLayoutViewModel {
      return ReplyLayoutViewModel(
        savedStateHandle = handle,
        appContext = appContext,
        appResourcesLazy = appResourcesLazy,
        appConstantsLazy = appConstantsLazy,
        siteManagerLazy = siteManagerLazy,
        replyLayoutHelperLazy = replyLayoutHelperLazy,
        boardManagerLazy = boardManagerLazy,
        replyManagerLazy = replyManagerLazy,
        postFormattingButtonsFactoryLazy = postFormattingButtonsFactoryLazy,
        themeEngineLazy = themeEngineLazy,
        globalUiStateHolderLazy = globalUiStateHolderLazy,
        captchaHolderLazy = captchaHolderLazy,
        postingServiceDelegateLazy = postingServiceDelegateLazy,
        boardFlagInfoRepositoryLazy = boardFlagInfoRepositoryLazy,
        runtimePermissionsHelperLazy = runtimePermissionsHelperLazy,
        imagePickHelperLazy = imagePickHelperLazy
      )
    }
  }

  companion object {
    private const val TAG = "ReplyLayoutViewModel"
    const val ThreadControllerTypeParam = "thread_controller_type"
  }

}