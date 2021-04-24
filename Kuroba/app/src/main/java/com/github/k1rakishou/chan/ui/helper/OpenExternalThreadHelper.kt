package com.github.k1rakishou.chan.ui.helper

import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.ThreadFollowHistoryManager
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

class OpenExternalThreadHelper(
  private val postPopupHelper: PostPopupHelper,
  private val chanThreadViewableInfoManager: ChanThreadViewableInfoManager,
  private val threadFollowHistoryManager: ThreadFollowHistoryManager
) {

  fun openExternalThread(
    currentChanDescriptor: ChanDescriptor,
    postDescriptor: PostDescriptor,
    showOpenThreadDialog: Boolean,
    openThreadFunc: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    Logger.d(TAG, "openExternalThread($postDescriptor)")

    val threadToOpenDescriptor = postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    if (!showOpenThreadDialog) {
      openExternalThreadInternal(
        postDescriptor = postDescriptor,
        currentChanDescriptor = currentChanDescriptor,
        threadToOpenDescriptor = threadToOpenDescriptor,
        openThreadFunc = openThreadFunc
      )

      return
    }

    openExternalThreadInternal(
      postDescriptor = postDescriptor,
      currentChanDescriptor = currentChanDescriptor,
      threadToOpenDescriptor = threadToOpenDescriptor,
      openThreadFunc = openThreadFunc
    )
  }

  private fun openExternalThreadInternal(
    postDescriptor: PostDescriptor,
    currentChanDescriptor: ChanDescriptor,
    threadToOpenDescriptor: ChanDescriptor.ThreadDescriptor,
    openThreadFunc: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    Logger.d(TAG, "openExternalThread() loading external thread $postDescriptor from $currentChanDescriptor")

    chanThreadViewableInfoManager.update(
      threadToOpenDescriptor,
      createEmptyWhenNull = true
    ) { chanThreadViewableInfo -> chanThreadViewableInfo.markedPostNo = postDescriptor.postNo }

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