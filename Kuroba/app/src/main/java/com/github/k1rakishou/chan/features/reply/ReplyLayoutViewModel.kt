package com.github.k1rakishou.chan.features.reply

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.ThrottleFirstCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.viewmodel.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.repository.BoardFlagInfoRepository
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.core.usecase.ClearPostingCookies
import com.github.k1rakishou.chan.core.usecase.LoadBoardFlagsUseCase
import com.github.k1rakishou.chan.features.posting.PostingService
import com.github.k1rakishou.chan.features.posting.PostingServiceDelegate
import com.github.k1rakishou.chan.features.posting.solvers.two_captcha.TwoCaptchaSolver
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
import com.github.k1rakishou.chan.ui.globalstate.drawer.DrawerAppearanceEvent
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.MediaUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.rethrowCancellationException
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.persist_state.ReplyMode
import com.github.k1rakishou.prefs.OptionsSetting
import com.github.k1rakishou.prefs.StringSetting
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
  private val imagePickHelperLazy: Lazy<ImagePickHelper>,
  private val twoCaptchaSolverLazy: Lazy<TwoCaptchaSolver>,
  private val clearPostingCookiesLazy: Lazy<ClearPostingCookies>
) : BaseViewModel(), ReplyLayoutState.Callbacks {
  private val appConstants: AppConstants
    get() = appConstantsLazy.get()
  private val siteManager: SiteManager
    get() = siteManagerLazy.get()
  private val boardManager: BoardManager
    get() = boardManagerLazy.get()
  private val replyManager: ReplyManager
    get() = replyManagerLazy.get()
  private val captchaHolder: CaptchaHolder
    get() = captchaHolderLazy.get()
  private val postingServiceDelegate: PostingServiceDelegate
    get() = postingServiceDelegateLazy.get()
  private val boardFlagInfoRepository: BoardFlagInfoRepository
    get() = boardFlagInfoRepositoryLazy.get()
  private val appResources: AppResources
    get() = appResourcesLazy.get()
  private val globalUiStateHolder: GlobalUiStateHolder
    get() = globalUiStateHolderLazy.get()
  private val twoCaptchaSolver: TwoCaptchaSolver
    get() = twoCaptchaSolverLazy.get()

  private val _replyManagerStateLoaded = AtomicBoolean(false)

  private val _boundChanDescriptor = mutableStateOf<ChanDescriptor?>(null)
  val boundChanDescriptor: State<ChanDescriptor?>
    get() = _boundChanDescriptor
  private val _replyLayoutState = mutableStateOf<ReplyLayoutState?>(null)
  val replyLayoutState: State<ReplyLayoutState?>
    get() = _replyLayoutState
  private val currentReplyLayoutState: ReplyLayoutState?
    get() = _replyLayoutState.value

  val drawerAppearanceEventFlow: StateFlow<DrawerAppearanceEvent>
    get() = globalUiStateHolder.drawer.drawerAppearanceEventFlow

  private val threadControllerType by lazy {
    requireNotNull(savedStateHandle.get<ThreadControllerType>(ThreadControllerTypeParam)) {
      "'${ThreadControllerTypeParam}' was not passed as a parameter of this ViewModel"
    }
  }

  val captchaHolderCaptchaCounterUpdatesFlow: Flow<Int>
    get() = captchaHolder.listenForCaptchaUpdates()

  private val attachableMediaClickExecutor = ThrottleFirstCoroutineExecutor(viewModelScope)
  private val promptUserForMediaUrlExecutor = ThrottleFirstCoroutineExecutor(viewModelScope)
  private val flagSelectorClickExecutor = ThrottleFirstCoroutineExecutor(viewModelScope)

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
    attachableMediaClickExecutor.stop()
    promptUserForMediaUrlExecutor.stop()
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

  override fun showBanDialog(
    title: String,
    message: CharSequence,
    neutralButton: () -> Unit,
    positiveButton: () -> Unit,
    onDismissListener: (() -> Unit)?
  ) {
    replyLayoutViewCallbacks?.showBanDialog(
      title = title,
      message = message,
      neutralButton = neutralButton,
      positiveButton = positiveButton,
      onDismissListener = onDismissListener
    )
  }

  override fun hideBanDialog() {
    replyLayoutViewCallbacks?.hideBanDialog()
  }

  override fun showProgressDialog(title: String) {
    threadListLayoutCallbacks?.showProgressDialog(title)
  }

  override fun hideProgressDialog() {
    threadListLayoutCallbacks?.hideProgressDialog()
  }

  override fun showToast(message: String) {
    replyLayoutViewCallbacks?.showToast(message)
  }

  override fun showErrorToast(throwable: Throwable) {
    val message = appResources.string(
      R.string.reply_layout_error_toast_generic_message,
      throwable.errorMessageOrClassName()
    )

    replyLayoutViewCallbacks?.showToast(message)
  }

  override suspend fun onPostedSuccessfully(
    prevChanDescriptor: ChanDescriptor,
    newThreadDescriptor: ChanDescriptor.ThreadDescriptor
  ) {
    threadListLayoutCallbacks?.onPostedSuccessfully(prevChanDescriptor, newThreadDescriptor)
  }

  override fun highlightQuotes(quotes: Set<PostDescriptor>) {
    threadListLayoutCallbacks?.highlightQuotes(quotes)
  }

  suspend fun bindChanDescriptor(chanDescriptor: ChanDescriptor, threadControllerType: ThreadControllerType) {
    replyManager.awaitUntilFilesAreLoaded()

    if (_boundChanDescriptor.value == chanDescriptor) {
      return
    }

    _boundChanDescriptor.value = chanDescriptor

    _replyLayoutState.value?.unbindChanDescriptor()
    sendReplyJob?.cancel()
    sendReplyJob = null
    listenForPostingStatusUpdatesJob?.cancel()

    listenForPostingStatusUpdatesJob = viewModelScope.launch {
      postingServiceDelegate.listenForPostingStatusUpdates(chanDescriptor)
        .onSubscription { Logger.debug(TAG) { "listenForPostingStatusUpdates(${chanDescriptor}) start" } }
        .onCompletion { Logger.debug(TAG) { "listenForPostingStatusUpdates(${chanDescriptor}) end" } }
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
      imagePickHelperLazy = imagePickHelperLazy,
      clearPostingCookiesLazy = clearPostingCookiesLazy
    )

    replyLayoutState.bindChanDescriptor(chanDescriptor)

    _replyLayoutState.value = replyLayoutState
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
      attachableMediaClickExecutor.post(timeout = 500) {
        val isFileSupportedForReencoding = isFileSupportedForReencoding(attachedMedia.fileUuid)

        replyLayoutViewCallbacks?.onAttachedMediaClicked(attachedMedia, isFileSupportedForReencoding)
      }
    }
  }

  fun onAttachedMediaLongClicked(attachedMedia: ReplyFileAttachable) {
    withReplyLayoutState {
      viewModelScope.launch {
        replyLayoutViewCallbacks?.onAttachedMediaLongClicked(attachedMedia)
      }
    }
  }

  fun onRemoteImageSelected(selectedImageUrl: HttpUrl) {
    withReplyLayoutState { replyLayoutState -> replyLayoutState.pickRemoteMedia(selectedImageUrl) }
  }

  fun removeAttachedMedia(attachedMedia: ReplyFileAttachable) {
    withReplyLayoutState { replyLayoutState ->
      viewModelScope.launch {
        val delete = replyLayoutViewCallbacks?.promptUserToConfirmMediaDeletion() ?: false
        if (!delete) {
          return@launch
        }

        replyLayoutState.removeAttachedMedia(attachedMedia)
      }
    }
  }

  fun removeAttachedMedia(fileUuid: UUID) {
    withReplyLayoutState { replyLayoutState -> replyLayoutState.removeAttachedMedia(fileUuid) }
  }

  fun deleteSelectedFiles() {
    withReplyLayoutState { replyLayoutState -> replyLayoutState.deleteSelectedFiles() }
  }

  fun removeSelectedFilesName() {
    withReplyLayoutState { replyLayoutState -> replyLayoutState.removeSelectedFilesName() }
  }

  fun removeSelectedFilesMetadata() {
    withReplyLayoutState { replyLayoutState -> replyLayoutState.removeSelectedFilesMetadata() }
  }

  fun changeSelectedFilesChecksum() {
    withReplyLayoutState { replyLayoutState -> replyLayoutState.changeSelectedFilesChecksum() }
  }

  fun selectUnselectAll(selectAll: Boolean) {
    withReplyLayoutState { replyLayoutState -> replyLayoutState.selectUnselectAll(selectAll) }
  }

  fun markUnmarkAsSpoiler(fileUuid: UUID, spoiler: Boolean) {
    withReplyLayoutState { replyLayoutState -> replyLayoutState.markUnmarkAsSpoiler(fileUuid, spoiler) }
  }

  fun hasSelectedFiles(): Boolean {
    return withReplyLayoutState { replyLayoutState -> replyLayoutState.hasSelectedFiles() } ?: false
  }

  fun allFilesSelected(): Boolean {
    return withReplyLayoutState { replyLayoutState -> replyLayoutState.allFilesSelected() } ?: false
  }

  fun boardsSupportsSpoilers(): Boolean {
    val chanDescriptor = boundChanDescriptor.value
      ?: return false

    return boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())?.spoilers ?: false
  }

  fun isReplyFileMarkedAsSpoiler(fileUuid: UUID): Boolean {
    return withReplyLayoutState { replyLayoutState -> replyLayoutState.isReplyFileMarkedAsSpoiler(fileUuid) } ?: false
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
      flagSelectorClickExecutor.post(500) {
        val lastUsedCountryFlagPerBoardSetting = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
          ?.getSettingBySettingId<StringSetting>(SiteSetting.SiteSettingId.LastUsedCountryFlagPerBoard)

        val selectedFlag = replyLayoutViewCallbacks?.promptUserToSelectFlag(chanDescriptor)
        if (lastUsedCountryFlagPerBoardSetting == null || selectedFlag == null) {
          return@post
        }

        boardFlagInfoRepository.storeLastUsedFlag(
          lastUsedCountryFlagPerBoardSetting = lastUsedCountryFlagPerBoardSetting,
          selectedFlagInfo = selectedFlag,
          currentBoardCode = chanDescriptor.boardCode()
        )

        replyLayoutState.onFlagSelected(selectedFlag)
      }
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
    withReplyLayoutState { replyLayoutState -> replyLayoutState.quote(post, withText) }
  }

  fun quote(postDescriptor: PostDescriptor, text: CharSequence) {
    withReplyLayoutState { replyLayoutState -> replyLayoutState.quote(postDescriptor, text) }
  }

  suspend fun getFlagInfoList(boardDescriptor: BoardDescriptor): List<LoadBoardFlagsUseCase.FlagInfo> {
    return boardFlagInfoRepository.getFlagInfoList(boardDescriptor)
  }

  fun onImageOptionsApplied(fileUuid: UUID) {
    withReplyLayoutState { replyLayoutState -> replyLayoutState.onImageOptionsApplied(fileUuid) }
  }

  fun onPickLocalMediaButtonClicked() {
    if (AppModuleAndroidUtils.checkDontKeepActivitiesSettingEnabledForWarningDialog(appContext)) {
      replyLayoutViewCallbacks?.onDontKeepActivitiesSettingDetected()
      return
    }

    withReplyLayoutState { replyLayoutState -> replyLayoutState.pickLocalMedia(showFilePickerChooser = false) }
  }

  fun onPickLocalMediaButtonLongClicked() {
    if (AppModuleAndroidUtils.checkDontKeepActivitiesSettingEnabledForWarningDialog(appContext)) {
      replyLayoutViewCallbacks?.onDontKeepActivitiesSettingDetected()
      return
    }

    withReplyLayoutState { replyLayoutState -> replyLayoutState.pickLocalMedia(showFilePickerChooser = true) }
  }

  fun onPickRemoteMediaButtonClicked() {
    withReplyLayoutState { replyLayoutState ->
      promptUserForMediaUrlExecutor.post(500) {
        val mediaUrl = replyLayoutViewCallbacks?.promptUserForMediaUrl()
        if (mediaUrl.isNullOrBlank()) {
          replyLayoutViewCallbacks?.showToast(appResources.string(R.string.reply_layout_remote_file_pick_no_url_provided))
          return@post
        }

        val mediaHttpUrl = mediaUrl.toHttpUrlOrNull()
        if (mediaHttpUrl == null) {
          replyLayoutViewCallbacks?.showToast(appResources.string(R.string.reply_layout_remote_file_pick_url_not_url))
          return@post
        }

        replyLayoutState.pickRemoteMedia(mediaHttpUrl)
      }
    }
  }

  fun onReplyLayoutOptionsButtonClicked() {
    replyLayoutViewCallbacks?.onReplyLayoutOptionsButtonClicked()
  }

  fun allReplyLayoutsCollapsed(): Boolean {
    return globalUiStateHolder.replyLayout.allReplyLayoutCollapsed()
  }

  fun updateCaptchaButtonVisibility() {
    withReplyLayoutState { replyLayoutState -> replyLayoutState.updateCaptchaButtonVisibility() }
  }

  fun paidCaptchaSolversSupportedAndEnabled(chanDescriptor: ChanDescriptor): Boolean {
    return twoCaptchaSolver.isSiteCurrentCaptchaTypeSupported(chanDescriptor.siteDescriptor())
      && twoCaptchaSolver.isLoggedIn
  }

  fun onReplyLayoutPositionChanged(boundsInRoot: Rect) {
    withReplyLayoutState { replyLayoutState -> replyLayoutState.onReplyLayoutPositionChanged(boundsInRoot) }
  }

  private suspend fun isFileSupportedForReencoding(clickedFileUuid: UUID): Boolean {
    return withContext(Dispatchers.IO) {
      val replyFile = replyManager.getReplyFileByFileUuid(clickedFileUuid).valueOrNull()
        ?: return@withContext false

      return@withContext MediaUtils.isFileSupportedForReencoding(replyFile.fileOnDisk)
    }
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
    }
  }

  private fun <T : Any> withReplyLayoutState(block: (ReplyLayoutState) -> T): T? {
    val replyLayoutState = currentReplyLayoutState
      ?: return null

    val chanDescriptor = boundChanDescriptor.value
    if (chanDescriptor == null) {
      replyLayoutState.collapseReplyLayout()
      return null
    }

    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      replyLayoutState.collapseReplyLayout()
      return null
    }

    return block(replyLayoutState)
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

    fun showProgressDialog(title: String)
    fun hideProgressDialog()

    fun presentController(controller: BaseFloatingController)
    fun pushController(controller: Controller)
    fun showMediaReencodingController(attachedMedia: ReplyFileAttachable, fileSupportedForReencoding: Boolean)
    fun highlightQuotes(quotes: Set<PostDescriptor>)
  }

  interface ReplyLayoutViewCallbacks {
    suspend fun showDialogSuspend(title: String, message: CharSequence?)
    suspend fun promptUserForMediaUrl(): String?
    suspend fun promptUserToSelectFlag(chanDescriptor: ChanDescriptor): LoadBoardFlagsUseCase.FlagInfo?
    suspend fun promptUserToConfirmMediaDeletion(): Boolean

    fun showDialog(
      title: String,
      message: CharSequence?,
      onDismissListener: (() -> Unit)? = null
    )

    fun showBanDialog(
      title: String,
      message: CharSequence?,
      neutralButton: () -> Unit,
      positiveButton: () -> Unit,
      onDismissListener: (() -> Unit)? = null
    )

    fun hideDialog()
    fun hideBanDialog()

    fun showToast(message: String)

    fun onReplyLayoutOptionsButtonClicked()
    fun onAttachedMediaClicked(attachedMedia: ReplyFileAttachable, isFileSupportedForReencoding: Boolean)
    suspend fun onAttachedMediaLongClicked(attachedMedia: ReplyFileAttachable)
    fun onDontKeepActivitiesSettingDetected()
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
    private val imagePickHelperLazy: Lazy<ImagePickHelper>,
    private val twoCaptchaSolverLazy: Lazy<TwoCaptchaSolver>,
    private val clearPostingCookiesLazy: Lazy<ClearPostingCookies>
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
        imagePickHelperLazy = imagePickHelperLazy,
        twoCaptchaSolverLazy = twoCaptchaSolverLazy,
        clearPostingCookiesLazy = clearPostingCookiesLazy
      )
    }
  }

  companion object {
    private const val TAG = "ReplyLayoutViewModel"
    const val ThreadControllerTypeParam = "thread_controller_type"
  }

}