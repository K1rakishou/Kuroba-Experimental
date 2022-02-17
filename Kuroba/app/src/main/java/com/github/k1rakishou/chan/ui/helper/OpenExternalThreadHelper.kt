package com.github.k1rakishou.chan.ui.helper

import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.ThreadFollowHistoryManager
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import dagger.Lazy

class OpenExternalThreadHelper(
  private val postPopupHelper: PostPopupHelper,
  private val _chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>,
  private val _threadFollowHistoryManager: Lazy<ThreadFollowHistoryManager>
) {

  private val chanThreadViewableInfoManager: ChanThreadViewableInfoManager
    get() = _chanThreadViewableInfoManager.get()
  private val threadFollowHistoryManager: ThreadFollowHistoryManager
    get() = _threadFollowHistoryManager.get()

  fun openExternalThread(
    currentChanDescriptor: ChanDescriptor,
    postDescriptor: PostDescriptor,
    scrollToPost: Boolean,
    openThreadFunc: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    Logger.d(TAG, "openExternalThread($postDescriptor)")

    val threadToOpenDescriptor = postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    openExternalThreadInternal(
      postDescriptor = postDescriptor,
      currentChanDescriptor = currentChanDescriptor,
      threadToOpenDescriptor = threadToOpenDescriptor,
      scrollToPost = scrollToPost,
      openThreadFunc = openThreadFunc
    )
  }

  private fun openExternalThreadInternal(
    postDescriptor: PostDescriptor,
    currentChanDescriptor: ChanDescriptor,
    threadToOpenDescriptor: ChanDescriptor.ThreadDescriptor,
    scrollToPost: Boolean,
    openThreadFunc: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    Logger.d(TAG, "openExternalThread() loading external thread $postDescriptor from $currentChanDescriptor")

    if (scrollToPost) {
      chanThreadViewableInfoManager.update(
        chanDescriptor = threadToOpenDescriptor,
        createEmptyWhenNull = true
      ) { chanThreadViewableInfo -> chanThreadViewableInfo.markedPostNo = postDescriptor.postNo }
    }

    if (currentChanDescriptor is ChanDescriptor.ThreadDescriptor) {
      threadFollowHistoryManager.pushThreadDescriptor(currentChanDescriptor)
    }

    if (postPopupHelper.isOpen) {
      postPopupHelper.postClicked(postDescriptor)
    }

    openThreadFunc(threadToOpenDescriptor)
  }

  companion object {
    private const val TAG = "OpenExternalThreadHelper"
  }

}