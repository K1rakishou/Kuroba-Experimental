package com.github.k1rakishou.chan.features.reply

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.viewmodel.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.features.posting.PostingService
import com.github.k1rakishou.chan.features.reply.data.PostFormattingButtonsFactory
import com.github.k1rakishou.chan.features.reply.data.ReplyAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutFileEnumerator
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.persist_state.ReplyMode
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class ReplyLayoutViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val appResourcesLazy: Lazy<AppResources>,
  private val appConstantsLazy: Lazy<AppConstants>,
  private val replyLayoutFileEnumeratorLazy: Lazy<ReplyLayoutFileEnumerator>,
  private val boardManagerLazy: Lazy<BoardManager>,
  private val replyManagerLazy: Lazy<ReplyManager>,
  private val postFormattingButtonsFactoryLazy: Lazy<PostFormattingButtonsFactory>,
  private val themeEngineLazy: Lazy<ThemeEngine>,
  private val globalUiStateHolderLazy: Lazy<GlobalUiStateHolder>,
  private val captchaHolderLazy: Lazy<CaptchaHolder>
) : BaseViewModel() {
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
  private val replyManager: ReplyManager
    get() = replyManagerLazy.get()
  private val captchaHolder: CaptchaHolder
    get() = captchaHolderLazy.get()

  private val threadControllerType by lazy {
    requireNotNull(savedStateHandle.get<ThreadControllerType>(ThreadControllerTypeParam)) {
      "'${ThreadControllerTypeParam}' was not passed as a parameter of this ViewModel"
    }
  }

  val captchaHolderCaptchaCounterUpdatesFlow: Flow<Int>
    get() = captchaHolder.listenForCaptchaUpdates()

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    Logger.debug(TAG) {
      "onViewModelReady() threadControllerType: ${threadControllerType}, instance: ${this.hashCode()}"
    }

    viewModelScope.launch { reloadReplyManagerState() }
  }

  suspend fun bindChanDescriptor(chanDescriptor: ChanDescriptor, threadControllerType: ThreadControllerType) {
    replyManager.awaitUntilFilesAreLoaded()

    if (_boundChanDescriptor.value == chanDescriptor) {
      return
    }

    _boundChanDescriptor.value = chanDescriptor

    _replyLayoutState.value = ReplyLayoutState(
      chanDescriptor = chanDescriptor,
      threadControllerType = threadControllerType,
      coroutineScope = viewModelScope,
      appResourcesLazy = appResourcesLazy,
      replyLayoutFileEnumeratorLazy = replyLayoutFileEnumeratorLazy,
      boardManagerLazy = boardManagerLazy,
      replyManagerLazy = replyManagerLazy,
      postFormattingButtonsFactoryLazy = postFormattingButtonsFactoryLazy,
      themeEngineLazy = themeEngineLazy,
      globalUiStateHolderLazy = globalUiStateHolderLazy
    ).also { replyLayoutState -> replyLayoutState.bindChanDescriptor(chanDescriptor) }
  }

  fun cleanup() {
    // TODO: New reply layout
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

  fun sendReply(chanDescriptor: ChanDescriptor, replyLayoutState: ReplyLayoutState) {
    TODO("New reply layout")
  }

  fun cancelSendReply(replyLayoutState: ReplyLayoutState) {
    TODO("New reply layout")
  }

  fun onAttachedMediaClicked(attachedMedia: ReplyAttachable) {
    TODO("New reply layout")
  }

  fun removeAttachedMedia(attachedMedia: ReplyAttachable) {
    TODO("New reply layout")
  }

  fun onFlagSelectorClicked(chanDescriptor: ChanDescriptor) {
    TODO("New reply layout")
  }

  fun updateReplyLayoutVisibility(newReplyLayoutVisibility: ReplyLayoutVisibility) {
    val replyLayoutState = currentReplyLayoutState
      ?: return

    when (newReplyLayoutVisibility) {
      ReplyLayoutVisibility.Collapsed -> replyLayoutState.collapseReplyLayout()
      ReplyLayoutVisibility.Opened -> replyLayoutState.openReplyLayout()
      ReplyLayoutVisibility.Expanded -> replyLayoutState.expandReplyLayout()
    }
  }

  fun quote(post: ChanPost, withText: Boolean) {
    TODO("New reply layout")
  }

  fun quote(postDescriptor: PostDescriptor, text: CharSequence) {
    TODO("New reply layout")
  }

  fun onImageOptionsApplied() {
    TODO("New reply layout")
  }

  fun onPickLocalMediaButtonClicked() {
    TODO("New reply layout")
  }

  fun onPickRemoteMediaButtonClicked() {
    TODO("New reply layout")
  }

  fun onSearchRemoteMediaButtonClicked() {
    TODO("New reply layout")
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

  fun makeSubmitCall(
    appContext: Context,
    chanDescriptor: ChanDescriptor,
    replyMode: ReplyMode,
    retrying: Boolean
  ) {
    closeAll()
    PostingService.enqueueReplyChanDescriptor(appContext, chanDescriptor, replyMode, retrying)
  }

  private fun closeAll() {
    // TODO: New reply layout.
//    isExpanded = false
//    previewOpen = false
//
//    commentEditingHistory.clear()
//
//    callback.highlightPosts(emptySet())
//    callback.dialogMessage(null)
//    callback.setExpanded(expanded = false, isCleaningUp = true)
//    callback.openSubject(false)
//    callback.hideFlag()
//    callback.openNameOptions(false)
//    callback.updateRevertChangeButtonVisibility(isBufferEmpty = true)
  }

  class ReplyFileDoesNotExist(fileUUID: UUID) : ClientException("Reply file with UUID '${fileUUID}' does not exist")

  class ViewModelFactory @Inject constructor(
    private val appResourcesLazy: Lazy<AppResources>,
    private val appConstantsLazy: Lazy<AppConstants>,
    private val replyLayoutFileEnumeratorLazy: Lazy<ReplyLayoutFileEnumerator>,
    private val boardManagerLazy: Lazy<BoardManager>,
    private val replyManagerLazy: Lazy<ReplyManager>,
    private val postFormattingButtonsFactoryLazy: Lazy<PostFormattingButtonsFactory>,
    private val themeEngineLazy: Lazy<ThemeEngine>,
    private val globalUiStateHolderLazy: Lazy<GlobalUiStateHolder>,
    private val captchaHolderLazy: Lazy<CaptchaHolder>
  ) : ViewModelAssistedFactory<ReplyLayoutViewModel> {
    override fun create(handle: SavedStateHandle): ReplyLayoutViewModel {
      return ReplyLayoutViewModel(
        savedStateHandle = handle,
        appResourcesLazy = appResourcesLazy,
        appConstantsLazy = appConstantsLazy,
        replyLayoutFileEnumeratorLazy = replyLayoutFileEnumeratorLazy,
        boardManagerLazy = boardManagerLazy,
        replyManagerLazy = replyManagerLazy,
        postFormattingButtonsFactoryLazy = postFormattingButtonsFactoryLazy,
        themeEngineLazy = themeEngineLazy,
        globalUiStateHolderLazy = globalUiStateHolderLazy,
        captchaHolderLazy = captchaHolderLazy
      )
    }
  }

  companion object {
    private const val TAG = "ReplyLayoutViewModel"
    const val ThreadControllerTypeParam = "thread_controller_type"
  }

}