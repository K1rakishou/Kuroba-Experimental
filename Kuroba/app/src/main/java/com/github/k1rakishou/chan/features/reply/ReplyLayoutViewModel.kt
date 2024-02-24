package com.github.k1rakishou.chan.features.reply

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
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
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.persist_state.ReplyMode
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ReplyLayoutViewModel(
    private val appConstantsLazy: Lazy<AppConstants>,
    private val siteManagerLazy: Lazy<SiteManager>,
    private val boardManagerLazy: Lazy<BoardManager>,
    private val replyManagerLazy: Lazy<ReplyManager>,
    private val postingLimitationsInfoManagerLazy: Lazy<PostingLimitationsInfoManager>,
    private val imageLoaderV2Lazy: Lazy<ImageLoaderV2>
) : BaseViewModel() {
    private val _replyManagerStateLoaded = AtomicBoolean(false)

    private val _replyLayoutStates = mutableStateMapOf<ChanDescriptor, ReplyLayoutState>()
    val replyLayoutStates: Map<ChanDescriptor, ReplyLayoutState>
        get() = _replyLayoutStates

    private val appConstants: AppConstants
        get() = appConstantsLazy.get()
    private val replyManager: ReplyManager
        get() = replyManagerLazy.get()

    override fun injectDependencies(component: ViewModelComponent) {
        component.inject(this)
    }

    override suspend fun onViewModelReady() {

    }

    suspend fun bindChanDescriptor(chanDescriptor: ChanDescriptor) {
        reloadReplyManagerState()

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

    suspend fun unbindChanDescriptor(chanDescriptor: ChanDescriptor) {
        _replyLayoutStates[chanDescriptor]?.unbindChanDescriptor(chanDescriptor)
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

    fun onBack(): Boolean {
        TODO("Not yet implemented")
    }

    fun isExpanded(chanDescriptor: ChanDescriptor): Boolean {
        val replyLayoutVisibility = _replyLayoutStates[chanDescriptor]?.replyLayoutVisibility?.value
            ?: return false

        return replyLayoutVisibility == ReplyLayoutVisibility.Expanded
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

    fun openOrCloseReplyLayout(open: Boolean) {
        TODO("Not yet implemented")
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

    fun cleanup() {
        TODO("Not yet implemented")
    }

    private suspend fun reloadReplyManagerState() {
        if (!_replyManagerStateLoaded.compareAndSet(false, true)) {
            return
        }

        withContext(Dispatchers.IO) {
            replyManager.reloadReplyManagerStateFromDisk(appConstants)
                .unwrap()

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

    class ReplyFileDoesNotExist(fileUUID: UUID): ClientException("Reply file with UUID '${fileUUID}' does not exist")

}