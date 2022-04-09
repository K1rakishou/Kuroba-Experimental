package com.github.k1rakishou.chan.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2Service
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2ServiceDelegate
import com.github.k1rakishou.core_logger.Logger
import dagger.Lazy
import javax.inject.Inject

class ImageSaverBroadcastReceiver : BroadcastReceiver() {

  @Inject
  lateinit var imageSaverV2ServiceDelegate: Lazy<ImageSaverV2ServiceDelegate>
  @Inject
  lateinit var imageSaverV2: Lazy<ImageSaverV2>

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

    when (intent.action) {
      ImageSaverV2Service.ACTION_TYPE_CANCEL -> {
        val uniqueId = extras.getString(ImageSaverV2Service.UNIQUE_ID)
        Logger.d(TAG, "Canceling download with uniqueId: '$uniqueId'")

        if (uniqueId == null) {
          return
        }

        val pendingResult = goAsync()

        serializedExecutor.post {
          try {
            imageSaverV2ServiceDelegate.get().cancelDownload(uniqueId)
          } finally {
            pendingResult.finish()
          }
        }
      }
      ImageSaverV2Service.ACTION_TYPE_RETRY_FAILED -> {
        val uniqueId = extras.getString(ImageSaverV2Service.UNIQUE_ID)
        Logger.d(TAG, "Retry downloading images with uniqueId: '$uniqueId'")

        if (uniqueId == null) {
          return
        }

        imageSaverV2.get().restartUnfinished(uniqueId, null)
      }
      ImageSaverV2Service.ACTION_TYPE_DELETE -> {
        val uniqueId = extras.getString(ImageSaverV2Service.UNIQUE_ID)
        Logger.d(TAG, "Deleting download with uniqueId: '$uniqueId'")

        if (uniqueId == null) {
          return
        }

        // Do not call hideNotification() here because it may cause an infinite loop
        //
        // cancel notification
        //    |
        //    v
        // ACTION_TYPE_DELETE intent sent to this broadcast receiver
        //    |
        //    v
        // cancel notification again
        //    |
        //    v
        //   ...
        imageSaverV2.get().deleteDownload(uniqueId)
      }
    }

    // Unknown action
  }

  companion object {
    private const val TAG = "ImageSaverBroadcastReceiver"
  }

}