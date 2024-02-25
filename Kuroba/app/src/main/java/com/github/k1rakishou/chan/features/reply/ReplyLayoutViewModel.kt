package com.github.k1rakishou.chan.features.reply

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.viewmodel.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.features.reply.data.ReplyAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController.ThreadControllerType
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.persist_state.ReplyMode
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class ReplyLayoutViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val appConstantsLazy: Lazy<AppConstants>,
  private val siteManagerLazy: Lazy<SiteManager>,
  private val boardManagerLazy: Lazy<BoardManager>,
  private val replyManagerLazy: Lazy<ReplyManager>,
  private val postingLimitationsInfoManagerLazy: Lazy<PostingLimitationsInfoManager>,
  private val imageLoaderV2Lazy: Lazy<ImageLoaderV2>
) : BaseViewModel() {
  private val _replyManagerStateLoaded = AtomicBoolean(false)

  private val _boundCatalogDescriptor = mutableStateOf<ChanDescriptor.ICatalogDescriptor?>(null)
  private val _boundThreadDescriptor = mutableStateOf<ChanDescriptor.ThreadDescriptor?>(null)

  private val _boundChanDescriptor = mutableStateOf<ChanDescriptor?>(null)
  val boundChanDescriptor: State<ChanDescriptor?>
    get() = _boundChanDescriptor

  private val _replyLayoutStates = mutableStateMapOf<ChanDescriptor, ReplyLayoutState>()
  val replyLayoutStates: Map<ChanDescriptor, ReplyLayoutState>
    get() = _replyLayoutStates

  private val appConstants: AppConstants
    get() = appConstantsLazy.get()
  private val replyManager: ReplyManager
    get() = replyManagerLazy.get()

  private val threadControllerType by lazy {
    requireNotNull(savedStateHandle.get<ThreadControllerType>(ThreadControllerTypeParam)) {
      "'${ThreadControllerTypeParam}' was not passed as a parameter of this ViewModel"
    }
  }

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    Logger.debug(TAG) {
      "onViewModelReady() threadControllerType: ${threadControllerType}, instance: ${this.hashCode()}"
    }

    viewModelScope.launch { reloadReplyManagerState() }
  }

  suspend fun bindChanDescriptor(chanDescriptor: ChanDescriptor) {
    replyManager.awaitUntilFilesAreLoaded()

    when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> _boundCatalogDescriptor.value = chanDescriptor
      is ChanDescriptor.ThreadDescriptor -> _boundThreadDescriptor.value = chanDescriptor
    }

    if (_replyLayoutStates.containsKey(chanDescriptor)) {
      return
    }

    _replyLayoutStates[chanDescriptor] = ReplyLayoutState(
      chanDescriptor = chanDescriptor,
      coroutineScope = viewModelScope,
      appConstantsLazy = appConstantsLazy,
      siteManagerLazy = siteManagerLazy,
      boardManagerLazy = boardManagerLazy,
      replyManagerLazy = replyManagerLazy,
      postingLimitationsInfoManagerLazy = postingLimitationsInfoManagerLazy,
      imageLoaderV2Lazy = imageLoaderV2Lazy
    ).also { replyLayoutState -> replyLayoutState.bindChanDescriptor(chanDescriptor) }
  }

  fun cleanup() {
    // TODO("Not yet implemented")
  }

  fun onBack(): Boolean {
    val chanDescriptor = boundChanDescriptor.value
      ?: return false

    val replyLayoutState = replyLayoutStates[chanDescriptor]
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

  fun replyLayoutVisibility(): ReplyLayoutVisibility {
    val chanDescriptor = boundChanDescriptor.value
      ?: return ReplyLayoutVisibility.Collapsed

    return _replyLayoutStates[chanDescriptor]?.replyLayoutVisibility?.value
      ?: return ReplyLayoutVisibility.Collapsed
  }

  fun sendReply(chanDescriptor: ChanDescriptor, replyLayoutState: ReplyLayoutState) {
    TODO("Not yet implemented")
  }

  fun cancelSendReply(replyLayoutState: ReplyLayoutState) {
    TODO("Not yet implemented")
  }

  fun onAttachedMediaClicked(attachedMedia: ReplyAttachable) {
    TODO("Not yet implemented")
  }

  fun removeAttachedMedia(attachedMedia: ReplyAttachable) {
    TODO("Not yet implemented")
  }

  fun onFlagSelectorClicked(chanDescriptor: ChanDescriptor) {
    TODO("Not yet implemented")
  }

  fun showCaptcha(
    chanDescriptor: ChanDescriptor,
    replyMode: ReplyMode,
    autoReply: Boolean,
    afterPostingAttempt: Boolean,
    onFinished: ((Boolean) -> Unit)?
  ) {
    TODO("Not yet implemented")
  }

  fun updateReplyLayoutVisibility(newReplyLayoutVisibility: ReplyLayoutVisibility) {
    val chanDescriptor = boundChanDescriptor.value
      ?: return

    val replyLayoutState = _replyLayoutStates[chanDescriptor]
      ?: return

    when (newReplyLayoutVisibility) {
      ReplyLayoutVisibility.Collapsed -> replyLayoutState.collapseReplyLayout()
      ReplyLayoutVisibility.Opened -> replyLayoutState.openReplyLayout()
      ReplyLayoutVisibility.Expanded -> replyLayoutState.expandReplyLayout()
    }
  }

  fun quote(post: ChanPost, withText: Boolean) {
    TODO("Not yet implemented")
  }

  fun quote(postDescriptor: PostDescriptor, text: CharSequence) {
    TODO("Not yet implemented")
  }

  fun onImageOptionsApplied() {
    TODO("Not yet implemented")
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

  class ReplyFileDoesNotExist(fileUUID: UUID) : ClientException("Reply file with UUID '${fileUUID}' does not exist")

  class ViewModelFactory @Inject constructor(
    private val appConstantsLazy: Lazy<AppConstants>,
    private val siteManagerLazy: Lazy<SiteManager>,
    private val boardManagerLazy: Lazy<BoardManager>,
    private val replyManagerLazy: Lazy<ReplyManager>,
    private val postingLimitationsInfoManagerLazy: Lazy<PostingLimitationsInfoManager>,
    private val imageLoaderV2Lazy: Lazy<ImageLoaderV2>
  ) : ViewModelAssistedFactory<ReplyLayoutViewModel> {
    override fun create(handle: SavedStateHandle): ReplyLayoutViewModel {
      return ReplyLayoutViewModel(
        savedStateHandle = handle,
        appConstantsLazy = appConstantsLazy,
        siteManagerLazy = siteManagerLazy,
        boardManagerLazy = boardManagerLazy,
        replyManagerLazy = replyManagerLazy,
        postingLimitationsInfoManagerLazy = postingLimitationsInfoManagerLazy,
        imageLoaderV2Lazy = imageLoaderV2Lazy
      )
    }
  }

  companion object {
    private const val TAG = "ReplyLayoutViewModel"
    const val ThreadControllerTypeParam = "thread_controller_type"
  }

}