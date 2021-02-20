package com.github.k1rakishou.chan.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2Service
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2ServiceDelegate
import com.github.k1rakishou.core_logger.Logger
import javax.inject.Inject

class ImageSaverBroadcastReceiver : BroadcastReceiver() {

  @Inject
  lateinit var imageSaverV2ServiceDelegate: ImageSaverV2ServiceDelegate
  @Inject
  lateinit var imageSaverV2: ImageSaverV2

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
            hideImageSaverNotification(context, uniqueId)
            imageSaverV2ServiceDelegate.cancelDownload(uniqueId)
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

        hideImageSaverNotification(context, uniqueId)
        imageSaverV2.retryFailedImages(uniqueId)
      }
      ImageSaverV2Service.ACTION_TYPE_DELETE -> {
        val uniqueId = extras.getString(ImageSaverV2Service.UNIQUE_ID)
        Logger.d(TAG, "Deleting download with uniqueId: '$uniqueId'")

        if (uniqueId == null) {
          return
        }

        val pendingResult = goAsync()

        serializedExecutor.post {
          try {
            // Do not call hideNotification() here because it may cause an infinite loop
            //
            // cancel notification
            //    |
            //    v
            // ACTION_TYPE_DELETE intent to this broadcast receiver
            //    |
            //    v
            // cancel notification again -> ...)
            imageSaverV2ServiceDelegate.deleteDownload(uniqueId)
          } finally {
            pendingResult.finish()
          }
        }
      }
    }

    // Unknown action
  }

  private fun hideImageSaverNotification(context: Context, uniqueId: String) {
    val notificationManagerCompat = NotificationManagerCompat.from(context)
    notificationManagerCompat.cancel(uniqueId, uniqueId.hashCode())
  }

  companion object {
    private const val TAG = "ImageSaverBroadcastReceiver"
  }

}