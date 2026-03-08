package com.github.k1rakishou.chan.core.helper

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil.memory.MemoryCache
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.image.InputFile
import com.github.k1rakishou.chan.core.image.loader.KurobaImageSize
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.NotificationConstants
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class KurobaSystemNotifications(
  private val appContext: Context,
  private val themeEngine: ThemeEngine,
  private val notificationManagerCompat: NotificationManagerCompat
) {
  private val _channelsSetup = AtomicBoolean(false)

  suspend fun showNotification(notificationData: NotificationData) {
    try {
      if (_channelsSetup.compareAndSet(false, true)) {
        setupChannels()
      }

      if (AppModuleAndroidUtils.hasPostNotificationsPermission(appContext)) {
        @SuppressLint("MissingPermission")
        notificationManagerCompat.notify(
          NotificationConstants.Generic.TAG,
          NotificationConstants.Generic.notificationId(notificationData.id),
          notificationData.build(appContext, themeEngine)
        )
      }
    } catch (error: Throwable) {
      Logger.error(TAG, error) { "Failed to show notification" }
    }
  }

  fun hideNotification(notificationId: String) {
    notificationManagerCompat.cancel(
      NotificationConstants.Generic.TAG,
      NotificationConstants.Generic.notificationId(notificationId),
    )
  }

  @SuppressLint("NewApi")
  private fun setupChannels() {
    Logger.d(TAG, "setupChannels() called")

    if (notificationManagerCompat.getNotificationChannel(NotificationConstants.Generic.CHANNEL_ID) == null) {
      Logger.debug(TAG) {
        "setupChannels() creating ${NotificationConstants.Generic.CHANNEL_ID} channel"
      }

      val genericNotificationChannel = NotificationChannel(
        NotificationConstants.Generic.CHANNEL_ID,
        NotificationConstants.Generic.CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
      )

      genericNotificationChannel.setSound(
        Settings.System.DEFAULT_NOTIFICATION_URI,
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
          .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
          .build()
      )

      genericNotificationChannel.enableLights(true)
      genericNotificationChannel.enableVibration(true)
      genericNotificationChannel.lightColor = themeEngine.chanTheme.accentColor

      notificationManagerCompat.createNotificationChannel(genericNotificationChannel)
    }
  }

  data class NotificationData(
    val id: String,
    val style: Style,
    val priority: Priority = Priority.Default,
    val withSound: Boolean = false,
    val withVibration: Boolean = true,
    val time: Long = System.currentTimeMillis(),
    val lights: Lights = Lights(1000, 1000),
    val vibrationPattern: VibrationPattern = VibrationPattern.Default,
    val autoCancel: Boolean = true,
    val largeIcon: LargeIcon? = null,
    val smallIcon: Int = R.drawable.ic_stat_notify_alert
  ) {
    enum class Priority(val systemPriority: Int) {
      Default(NotificationCompat.PRIORITY_DEFAULT),
      Low(NotificationCompat.PRIORITY_LOW),
      Min(NotificationCompat.PRIORITY_MIN),
      High(NotificationCompat.PRIORITY_HIGH),
      Max(NotificationCompat.PRIORITY_MAX),
    }

    sealed interface Style {
      fun title(): String {
        return when (this) {
          is Default -> title
          is BigTextStyle -> title
        }
      }

      fun content(): String {
        return when (this) {
          is Default -> content
          is BigTextStyle -> shortText
        }
      }

      data class Default(
        val title: String,
        val content: String
      ) : Style

      data class BigTextStyle(
        val title: String,
        val shortText: String,
        val fullText: String = shortText
      ) : Style
    }

    data class Lights(
      val onMs: Int,
      val offMs: Int
    )

    sealed interface LargeIcon {
      data class Bitmap(val bitmap: android.graphics.Bitmap) : LargeIcon
      data class LocalFile(val file: File) : LargeIcon
      data class RemoteUrl(val url: HttpUrl) : LargeIcon
    }

    sealed class VibrationPattern(val pattern: LongArray) {
      data object LowPriority : VibrationPattern(longArrayOf(0, 200))
      data object Default : VibrationPattern(longArrayOf(0, 250, 250, 250))
      data object GentleReminder : VibrationPattern(longArrayOf(0, 300, 1000, 300))
      data object Attention : VibrationPattern(longArrayOf(0, 500))
      data object Alert : VibrationPattern(longArrayOf(0, 200, 100, 200, 100, 200, 100, 200))
    }

    suspend fun build(appContext: Context, themeEngine: ThemeEngine): Notification {
      var defaultOptions = Notification.DEFAULT_LIGHTS
      if (withSound) {
        defaultOptions = defaultOptions or Notification.DEFAULT_SOUND
      }
      if (withVibration) {
        defaultOptions = defaultOptions or Notification.DEFAULT_VIBRATE
      }

      // Dispatchers.Default should be fine here, as we won't do any heavy IO since it will be delegated to other
      // thread pools
      val largeIcon = withContext(Dispatchers.Default) { retrieveLargeIcon(appContext, largeIcon) }

      return NotificationCompat.Builder(appContext, NotificationConstants.Generic.CHANNEL_ID)
        .setWhen(time)
        .setShowWhen(true)
        .setContentTitle(style.title())
        .setContentText(style.content())
        .setDefaults(defaultOptions)
        .setLights(themeEngine.chanTheme.accentColor, lights.onMs, lights.offMs)
        .setAllowSystemGeneratedContextualActions(false)
        .setPriority(priority.systemPriority)
        .setAutoCancel(autoCancel)
        .setSmallIcon(smallIcon)
        .setLargeIcon(largeIcon)
        .setCategory(Notification.CATEGORY_MESSAGE)
        .also { builder ->
          if (withVibration) {
            builder.setVibrate(vibrationPattern.pattern)
          }
        }
        .also { builder ->
          val notificationStyle = when (style) {
            is Style.Default -> {
              // No-op
              return@also
            }
            is Style.BigTextStyle -> {
              NotificationCompat.BigTextStyle(builder)
                .setBigContentTitle(style.title)
                .setSummaryText(style.shortText)
                .bigText(style.fullText)
            }
          }

          builder.setStyle(notificationStyle)
        }
        .build()
    }

    private suspend fun retrieveLargeIcon(appContext: Context, largeIcon: LargeIcon?): Bitmap? {
      val imageSize = KurobaImageSize.FixedImageSize(128, 128)
      val imageLoader = appDependencies().kurobaImageLoader

      return when (largeIcon) {
        is LargeIcon.Bitmap -> largeIcon.bitmap
        is LargeIcon.LocalFile -> {
          val file = largeIcon.file
          val filePath = file.absolutePath

          imageLoader.loadFromDisk(
            context = appContext,
            inputFile = InputFile.JavaFile(file),
            memoryCacheKey = MemoryCache.Key(filePath),
            imageSize = imageSize
          ).onError { error ->
            Logger.error(TAG, error) {
              "Failed to retrieve notification icon from file with path: '${filePath}'"
            }
          }
            .valueOrNull()
            ?.bitmap
        }

        is LargeIcon.RemoteUrl -> {
          val urlString = largeIcon.url.toString()

          imageLoader.loadFromNetwork(
            context = appContext,
            url = urlString,
            memoryCacheKey = MemoryCache.Key(urlString),
            cacheFileType = CacheFileType.Other,
            imageSize = imageSize,
          ).onError { error ->
            Logger.error(TAG, error) {
              "Failed to retrieve notification icon from remote url: '${urlString}'"
            }
          }
            .valueOrNull()
            ?.bitmap
        }

        null -> null
      }
    }
  }

  companion object {
    private const val TAG = "KurobaSystemNotifications"
  }

}