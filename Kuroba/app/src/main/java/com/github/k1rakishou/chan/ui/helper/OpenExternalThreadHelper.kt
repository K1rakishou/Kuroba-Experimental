package com.github.k1rakishou.chan.ui.helper

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.ThreadFollowHistoryManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.util.ChanPostUtils

class OpenExternalThreadHelper(
  private val context: Context,
  private val postPopupHelper: PostPopupHelper,
  private val chanThreadManager: ChanThreadManager,
  private val dialogFactory: DialogFactory,
  private val chanThreadViewableInfoManager: ChanThreadViewableInfoManager,
  private val threadFollowHistoryManager: ThreadFollowHistoryManager
) {

  fun openExternalThread(
    currentChanDescriptor: ChanDescriptor,
    postDescriptor: PostDescriptor,
    openThreadFunc: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    Logger.d(TAG, "openExternalThread($postDescriptor)")

    val threadToOpenDescriptor = postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    val originalPostDescriptor = PostDescriptor.create(
      postDescriptor.descriptor,
      postDescriptor.getThreadNo()
    )

    val threadTitle = chanThreadManager.getPost(originalPostDescriptor)?.let { originalPost ->
      ChanPostUtils.getTitle(originalPost, postDescriptor.descriptor)
    }

    val fullPostLink = threadToOpenDescriptor.siteName() + "/" +
      threadToOpenDescriptor.boardCode() + "/" +
      threadToOpenDescriptor.threadNo + "/" +
      postDescriptor.postNo

    val fullThreadName = buildString {
      if (threadTitle.isNotNullNorEmpty()) {
        append('\'')
        append(threadTitle)
        append('\'')
        appendLine()
        appendLine()
      }

      append(fullPostLink)
    }

    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.open_thread_confirmation,
      descriptionText = fullThreadName,
      negativeButtonText = AppModuleAndroidUtils.getString(R.string.cancel),
      positiveButtonText = AppModuleAndroidUtils.getString(R.string.ok),
      onPositiveButtonClickListener = {
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
    )
  }

  companion object {
    private const val TAG = "OpenExternalThreadHelper"
  }

}