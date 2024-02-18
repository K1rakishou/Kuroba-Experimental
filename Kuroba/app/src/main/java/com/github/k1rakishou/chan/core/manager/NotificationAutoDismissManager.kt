package com.github.k1rakishou.chan.core.manager

import androidx.core.app.NotificationManagerCompat
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2Service
import com.github.k1rakishou.chan.features.posting.PostingService
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class NotificationAutoDismissManager(
    private val appScope: CoroutineScope,
    private val notificationManagerCompat: NotificationManagerCompat
) {
    private val cancelNotificationJobMap = ConcurrentHashMap<UniqueNotificationId, Job>()

    fun enqueue(
        notificationId: Int,
        notificationType: NotificationType,
        timeout: Long = 10_000L
    ) {
        val uniqueNotificationId = uniqueNotificationId(notificationId, notificationType)
        cancel(notificationId, notificationType)

        val job = appScope.launch {
            try {
                delay(timeout)

                if (isActive) {
                    when (notificationType) {
                        NotificationType.ImageSaverV2Service -> {
                            imageSaverV2ServiceCancelNotification(
                                notificationManagerCompat = notificationManagerCompat,
                                notificationId = notificationId
                            )
                        }
                        NotificationType.PostingService -> {
                            postingServiceCancelNotification(
                                notificationManagerCompat = notificationManagerCompat,
                                notificationId = notificationId
                            )
                        }
                    }
                }
            } finally {
                cancelNotificationJobMap.remove(uniqueNotificationId)
            }
        }

        cancelNotificationJobMap[uniqueNotificationId] = job
    }

    fun cancel(notificationId: Int, notificationType: NotificationType) {
        val uniqueNotificationId = uniqueNotificationId(notificationId, notificationType)
        cancelNotificationJobMap.remove(uniqueNotificationId)?.cancel()
    }

    private fun uniqueNotificationId(
        notificationId: Int,
        notificationType: NotificationType
    ): UniqueNotificationId {
        return UniqueNotificationId(notificationId, notificationType)
    }

    enum class NotificationType {
        ImageSaverV2Service,
        PostingService
    }

    private data class UniqueNotificationId(
        val notificationId: Int,
        val notificationType: NotificationType
    )

    companion object {
        private const val TAG = "NotificationAutoDismissManager"

        fun imageSaverV2ServiceCancelNotification(
            notificationManagerCompat: NotificationManagerCompat,
            notificationId: Int
        ) {
            Logger.d(TAG, "imageSaverV2ServiceCancelNotification() cancelNotification('$notificationId', '$notificationId')")

            notificationManagerCompat.cancel(
                ImageSaverV2Service.IMAGE_SAVER_NOTIFICATIONS_TAG,
                notificationId
            )
        }

        fun postingServiceCancelNotification(
            notificationManagerCompat: NotificationManagerCompat,
            notificationId: Int
        ) {
            Logger.d(TAG, "postingServiceCancelNotification() cancelNotification('$notificationId', '$notificationId')")

            notificationManagerCompat.cancel(
                PostingService.CHILD_NOTIFICATION_TAG,
                notificationId
            )
        }
    }

}