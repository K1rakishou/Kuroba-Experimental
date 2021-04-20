package com.github.k1rakishou.chan.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.features.posting.PostingService
import com.github.k1rakishou.chan.features.posting.PostingServiceDelegate
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import javax.inject.Inject

class PostingServiceBroadcastReceiver : BroadcastReceiver() {

  @Inject
  lateinit var postingServiceDelegate: PostingServiceDelegate

  private val scope = KurobaCoroutineScope()
  private val serializedExecutor = SerializedCoroutineExecutor(scope)

  init {
    Chan.getComponent()
      .inject(this)
  }

  override fun onReceive(context: Context?, intent: Intent?) {
    if (context == null || intent == null) {
      return
    }

    when (intent.action) {
      PostingService.ACTION_TYPE_CANCEL -> {
        val chanDescriptor = intent.extras
          ?.getParcelable<DescriptorParcelable>(PostingService.CHAN_DESCRIPTOR)
          ?.toChanDescriptor()

        if (chanDescriptor == null) {
          return
        }

        Logger.d(TAG, "Canceling post upload for ${chanDescriptor}")
        val pendingResult = goAsync()

        serializedExecutor.post {
          try {
            postingServiceDelegate.cancel(chanDescriptor)
          } finally {
            pendingResult.finish()
          }
        }
      }
      PostingService.ACTION_TYPE_CANCEL_ALL -> {
        Logger.d(TAG, "Canceling all post uploads")
        val pendingResult = goAsync()

        serializedExecutor.post {
          try {
            postingServiceDelegate.cancelAll()
          } finally {
            pendingResult.finish()
          }
        }
      }
    }
  }

  companion object {
    private const val TAG = "PostingServiceBroadcastReceiver"
  }

}