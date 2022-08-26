package com.github.k1rakishou.chan.core.manager

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.HashingUtil
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.persist_state.PersistableChanState
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.joda.time.Duration
import org.joda.time.format.PeriodFormatterBuilder

class ReportManager(
  private val appScope: CoroutineScope,
  private val appContext: Context,
  private val proxiedOkHttpClient: Lazy<ProxiedOkHttpClient>,
  private val gson: Lazy<Gson>,
  private val appConstants: AppConstants
) {
  private val activityManager: ActivityManager?
    get() = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
  private val okHttpClient: OkHttpClient
    get() = proxiedOkHttpClient.get().okHttpClient()

  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(
    scope = appScope,
    dispatcher = Dispatchers.Default
  )

  fun sendComment(
    issueNumber: Int,
    description: String,
    logs: String?,
    onReportSendResult: (ModularResult<Unit>) -> Unit
  ) {
    require(description.isNotEmpty() || logs != null) { "description is empty" }

    serializedCoroutineExecutor.post {
      val body = buildString(8192) {
        appendLine(description)

        if (logs.isNotNullNorEmpty()) {
          append("```")
          append(logs)
          append("```")
        }
      }

      val request = ReportRequest.comment(
        body = body
      )

      val result = sendInternal(
        reportRequest = request,
        issueNumber = issueNumber
      )

      withContext(Dispatchers.Main) { onReportSendResult.invoke(result) }
    }
  }

  fun sendCrashlog(
    title: String,
    body: String,
    onReportSendResult: (ModularResult<Unit>) -> Unit
  ) {
    serializedCoroutineExecutor.post {
      val request = ReportRequest.crashLog(
        title = title,
        body = body
      )

      val result = sendInternal(request)
      withContext(Dispatchers.Main) { onReportSendResult.invoke(result) }
    }
  }

  fun sendReport(
    title: String,
    description: String,
    logs: String?,
    onReportSendResult: (ModularResult<Unit>) -> Unit
  ) {
    require(title.isNotEmpty()) { "title is empty" }
    require(description.isNotEmpty() || logs != null) { "description is empty" }

    serializedCoroutineExecutor.post {
      val body = buildString(8192) {
        appendLine(description)

        if (logs.isNotNullNorEmpty()) {
          append("```")
          append(logs)
          append("```")
        }
      }

      val request = ReportRequest.report(
        title = title,
        body = body
      )

      val result = sendInternal(request)
      withContext(Dispatchers.Main) { onReportSendResult.invoke(result) }
    }
  }

  fun getReportFooter(context: Context): String {
    return buildString(capacity = 2048) {
      appendLine("------------------------------")
      appendLine("Android API Level: " + Build.VERSION.SDK_INT)
      appendLine("App Version: " + BuildConfig.VERSION_NAME)
      appendLine("Phone Model: " + Build.MANUFACTURER + " " + Build.MODEL)
      appendLine("Build type: " + AppModuleAndroidUtils.getVerifiedBuildType().name)
      appendLine("Flavor type: " + AppModuleAndroidUtils.getFlavorType().name)
      appendLine("isLowRamDevice: ${ChanSettings.isLowRamDevice()}, isLowRamDeviceForced: ${ChanSettings.isLowRamDeviceForced.get()}")
      appendLine("MemoryClass: ${activityManager?.memoryClass}")
      appendLine("App running time: ${formatAppRunningTime()}")
      appendLine("System animations state: ${systemAnimationsState(context)}")
      appendLine("------------------------------")
      appendLine("Current layout mode: ${ChanSettings.getCurrentLayoutMode().name}")
      appendLine("Board view mode: ${ChanSettings.boardPostViewMode.get()}")
      appendLine("Bottom navigation enabled: ${ChanSettings.bottomNavigationViewEnabled.get()}")
      appendLine("Prefetching enabled: ${ChanSettings.prefetchMedia.get()}")
      appendLine("Hi-res thumbnails enabled: ${ChanSettings.highResCells.get()}")
      appendLine("mediaViewerMaxOffscreenPages: ${ChanSettings.mediaViewerMaxOffscreenPages.get()}")
      appendLine("CloudFlare force preload enabled: ${ChanSettings.cloudflareForcePreload.get()}")
      appendLine("useMpvVideoPlayer: ${ChanSettings.useMpvVideoPlayer.get()}")
      appendLine("userAgent: ${appConstants.userAgent}")
      appendLine("kurobaExCustomUserAgent: ${appConstants.kurobaExCustomUserAgent}")

      appendLine("maxPostsCountInPostsCache: ${appConstants.maxPostsCountInPostsCache}")
      appendLine("maxAmountOfPostsInDatabase: ${appConstants.maxAmountOfPostsInDatabase}")
      appendLine("maxAmountOfThreadsInDatabase: ${appConstants.maxAmountOfThreadsInDatabase}")

      appendLine("diskCacheSizeMegabytes: ${ChanSettings.diskCacheSizeMegabytes.get()}")
      appendLine("prefetchDiskCacheSizeMegabytes: ${ChanSettings.prefetchDiskCacheSizeMegabytes.get()}")
      appendLine("diskCacheCleanupRemovePercent: ${ChanSettings.diskCacheCleanupRemovePercent.get()}")

      appendLine("ImageSaver root directory: ${PersistableChanState.imageSaverV2PersistedOptions.get().rootDirectoryUri}")
      appendLine("OkHttp IPv6 support enabled: ${ChanSettings.okHttpAllowIpv6.get()}")
      appendLine("OkHttp HTTP/2 support enabled: ${ChanSettings.okHttpAllowHttp2.get()}")

      appendLine("Foreground watcher enabled: ${ChanSettings.watchEnabled.get()}")
      if (ChanSettings.watchEnabled.get()) {
        appendLine("Watch foreground interval: ${ChanSettings.watchForegroundInterval.get()}")
        appendLine("Watch foreground adaptive interval: ${ChanSettings.watchForegroundAdaptiveInterval.get()}")
      }

      appendLine("Background watcher enabled: ${ChanSettings.watchBackground.get()}")
      if (ChanSettings.watchBackground.get()) {
        appendLine("Watch background interval: ${ChanSettings.watchBackgroundInterval.get()}")
      }

      appendLine("Filter watch enabled: ${ChanSettings.filterWatchEnabled.get()}")
      if (ChanSettings.filterWatchEnabled.get()) {
        appendLine("Filter watch interval: ${ChanSettings.filterWatchInterval.get()}")
      }

      appendLine("Thread downloader interval: ${ChanSettings.threadDownloaderUpdateInterval.get()}")
      appendLine("Thread downloader download media on metered network: ${ChanSettings.threadDownloaderDownloadMediaOnMeteredNetwork.get()}")

      appendLine("------------------------------")
    }
  }

  private fun systemAnimationsState(context: Context): String {
    val duration = Settings.Global.getFloat(
      context.contentResolver,
      Settings.Global.ANIMATOR_DURATION_SCALE, 0f
    )

    val transition = Settings.Global.getFloat(
      context.contentResolver,
      Settings.Global.TRANSITION_ANIMATION_SCALE, 0f
    )

    val window = Settings.Global.getFloat(
      context.contentResolver,
      Settings.Global.WINDOW_ANIMATION_SCALE, 0f
    )

    return "duration: ${duration}, transition: ${transition}, window: ${window}"
  }

  private fun formatAppRunningTime(): String {
    val time = ((appContext as? Chan)?.appRunningTime) ?: -1L
    if (time <= 0) {
      return "Unknown (appContext=${appContext::class.java.simpleName}), time ms: $time"
    }

    return appRunningTimeFormatter.print(Duration.millis(time).toPeriod())
  }

  private suspend fun sendInternal(reportRequest: ReportRequest, issueNumber: Int? = null): ModularResult<Unit> {
    BackgroundUtils.ensureBackgroundThread()

    return ModularResult.Try {
      val json = try {
        gson.get().toJson(reportRequest)
      } catch (error: Throwable) {
        Logger.e(TAG, "Couldn't convert $reportRequest to json", error)
        throw error
      }

      val reportUrl = if (issueNumber != null) {
        "https://api.github.com/repos/kurobaexreports/reports/issues/${issueNumber}/comments"
      } else {
        "https://api.github.com/repos/kurobaexreports/reports/issues"
      }
      val requestBody = json.toRequestBody("application/json".toMediaType())

      val request = Request.Builder()
        .url(reportUrl)
        .post(requestBody)
        .header("Accept", "application/vnd.github.v3+json")
        .header("Authorization", "token ${supersikritdonotlook()}")
        .build()

      val response = okHttpClient.suspendCall(request)

      if (!response.isSuccessful) {
        val errorMessage = response.body
          ?.let { body -> gson.get().fromJson(body.string(), ReportResponse::class.java) }
          ?.errorMessage

        val message = if (errorMessage.isNullOrEmpty()) {
          "Response is not successful. Status: ${response.code}"
        } else {
          "Response is not successful. Status: ${response.code}. ErrorMessage: '${errorMessage}'"
        }

        throw ReportError(message)
      }
    }
  }

  private fun supersikritdonotlook(): String {
    return HashingUtil.stringBase64Decode("Z2hwXzJMOUpaZ1ozM24xcDhBM3pwVnBYNmgwVkNPTzUzRzB1b2lCZA==")
  }

  private class ReportError(val errorMessage: String) : Exception(errorMessage)

  data class ReportRequest(
    @SerializedName("title")
    val title: String?,
    @SerializedName("body")
    val body: String,
    @SerializedName("labels")
    val labels: List<String>
  ) {

    companion object {

      fun crashLog(title: String, body: String): ReportRequest {
        return ReportRequest(
          title = title,
          body = body,
          labels = listOf("KurobaEx", "New", "Crash")
        )
      }

      fun report(title: String, body: String): ReportRequest {
        return ReportRequest(
          title = title,
          body = body,
          labels = listOf("KurobaEx", "New", "Report")
        )
      }

      fun comment(body: String): ReportRequest {
        return ReportRequest(
          title = null,
          body = body,
          labels = emptyList()
        )
      }

    }

  }

  data class ReportResponse(
    @SerializedName("message")
    val errorMessage: String?,
  )

  companion object {
    private const val TAG = "ReportManager"

    const val MAX_TITLE_LENGTH = 512
    const val MAX_DESCRIPTION_LENGTH = 8192
    const val MAX_LOGS_LENGTH = 65535

    private val appRunningTimeFormatter = PeriodFormatterBuilder()
      .printZeroAlways()
      .minimumPrintedDigits(2)
      .appendHours()
      .appendSuffix(":")
      .appendMinutes()
      .appendSuffix(":")
      .appendSeconds()
      .appendSuffix(".")
      .appendMillis3Digit()
      .toFormatter()
  }

}