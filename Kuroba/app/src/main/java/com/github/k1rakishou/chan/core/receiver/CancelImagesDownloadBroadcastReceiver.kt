package com.github.k1rakishou.chan.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2Delegate
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2ForegroundWorker
import com.github.k1rakishou.core_logger.Logger
import javax.inject.Inject

class CancelImagesDownloadBroadcastReceiver : BroadcastReceiver() {

  @Inject
  lateinit var imageSaverV2Delegate: ImageSaverV2Delegate

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

    val extras = intent.extras
      ?: return

    if (intent.action != ImageSaverV2ForegroundWorker.ACTION_TYPE_CANCEL) {
      return
    }

    val uniqueId = extras.getString(ImageSaverV2ForegroundWorker.UNIQUE_ID)
      ?: return

    Logger.d(TAG, "Canceling download with uniqueId: '$uniqueId'")
    val pendingResult = goAsync()

    serializedExecutor.post {
      try {
        imageSaverV2Delegate.cancelDownload(uniqueId)
      } finally {
        pendingResult.finish()
      }
    }
  }

  companion object {
    private const val TAG = "CancelImagesDownloadBroadcastReceiver"
  }

}