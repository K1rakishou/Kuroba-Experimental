package com.github.k1rakishou.chan.core.manager

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.features.issues.ReportFile
import com.github.k1rakishou.chan.ui.controller.LogsController
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.HashingUtil
import com.github.k1rakishou.chan.utils.TimeUtils.getCurrentDateAndTimeUTC
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.persist_state.PersistableChanState
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.joda.time.Duration
import org.joda.time.format.PeriodFormatterBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class ReportManager(
  private val appScope: CoroutineScope,
  private val appContext: Context,
  private val proxiedOkHttpClient: Lazy<ProxiedOkHttpClient>,
  private val settingsNotificationManager: Lazy<SettingsNotificationManager>,
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

  private val anrsDir: File
    get() = appConstants.anrsDir
  private val crashLogsDir: File
    get() = appConstants.crashLogsDir

  fun storeAnr(anrByteStream: ByteArrayOutputStream) {
    if (!createDirectoriesIfNotExists()) {
      return
    }

    val currentTime = System.currentTimeMillis()

    val newAnr = File(anrsDir, "${ANR_FILE_NAME_PREFIX}_${currentTime}.txt")
    if (newAnr.exists()) {
      return
    }

    try {
      newAnr.outputStream().use { outputStream ->
        anrByteStream.use { inputStream ->
          inputStream.writeTo(outputStream)
        }
      }
    } catch (error: Throwable) {
      Logger.e(TAG, "Error writing to a ANR file", error)
      return
    }

    deleteOldAnrReports()

    Logger.d(TAG, "Stored new ANR, path = ${newAnr.absolutePath}")
    settingsNotificationManager.get().notify(SettingNotificationType.CrashLogOrAnr)
  }

  fun storeCrashLog(exceptionMessage: String?, error: String) {
    if (!createDirectoriesIfNotExists()) {
      return
    }

    val currentTime = System.currentTimeMillis()

    val newCrashLog = File(crashLogsDir, "${CRASH_LOG_FILE_NAME_PREFIX}_${currentTime}.txt")
    if (newCrashLog.exists()) {
      return
    }

    try {
      val settings = getReportFooter()
      val logs = LogsController.loadLogs()

      // Most of the time logs already contain the crash logs so we don't really want to print
      // it twice.
      val logsAlreadyContainCrash = exceptionMessage
        ?.let { msg -> logs?.contains(msg, ignoreCase = true) }
        ?: false

      val resultString = buildString(capacity = 4096) {
        // To avoid log spam that may happen because of, let's say, server failure for
        // couple of days, we want some kind of marker to be able to filter them
        appendLine("=== LOGS(${getCurrentDateAndTimeUTC()}) ===")
        logs?.let { append(it) }
        append("\n\n")

        if (!logsAlreadyContainCrash) {
          appendLine("=== STACKTRACE ===")
          append(error)
          append("\n\n")
        }

        append(settings)
      }

      newCrashLog.writeText(resultString)
    } catch (error: Throwable) {
      Logger.e(TAG, "Error writing to a crash log file", error)
      return
    }

    deleteOldCrashlogs()

    Logger.d(TAG, "Stored new crash log, path = ${newCrashLog.absolutePath}")
    settingsNotificationManager.get().notify(SettingNotificationType.CrashLogOrAnr)
  }

  fun hasReportFiles(): Boolean {
    if (!createDirectoriesIfNotExists()) {
      return false
    }

    val crashLogs = crashLogsDir.listFiles()
    if (crashLogs != null && crashLogs.isNotEmpty()) {
      return true
    }

    val anrs = anrsDir.listFiles()
    if (anrs != null && anrs.isNotEmpty()) {
      return true
    }

    return false
  }

  fun countReportFiles(): Int {
    if (!createDirectoriesIfNotExists()) {
      return 0
    }

    val crashlogs = crashLogsDir.listFiles()?.size ?: 0
    val anrs = anrsDir.listFiles()?.size ?: 0

    return crashlogs + anrs
  }

  fun getReportFiles(): List<File> {
    if (!createDirectoriesIfNotExists()) {
      return emptyList()
    }

    val crashLogs = crashLogsDir.listFiles() ?: emptyArray()
    val anrs = anrsDir.listFiles() ?: emptyArray()

    return (crashLogs + anrs)
      .sortedByDescending { file -> file.lastModified() }
      .toList()
  }

  fun deleteReportFiles(reportFiles: List<ReportFile>) {
    if (!createDirectoriesIfNotExists()) {
      settingsNotificationManager.get().cancel(SettingNotificationType.CrashLogOrAnr)
      return
    }

    reportFiles.forEach { crashLog -> crashLog.file.delete() }

    val remainingCrashLogs = crashLogsDir.listFiles()?.size ?: 0
    val remainingAnrs = anrsDir.listFiles()?.size ?: 0

    if (remainingCrashLogs == 0 && remainingAnrs == 0) {
      settingsNotificationManager.get().cancel(SettingNotificationType.CrashLogOrAnr)
      return
    }

    // There are still crash logs/ANRs left, so show the notifications if they are not shown yet
    settingsNotificationManager.get().notify(SettingNotificationType.CrashLogOrAnr)
  }

  fun deleteAllCrashLogs() {
    deleteAllReportFiles(deleteCrashLogs = true, deleteAnrs = false)
  }

  fun deleteAllAnrs() {
    deleteAllReportFiles(deleteCrashLogs = false, deleteAnrs = true)
  }

  fun deleteAllReportFiles(deleteCrashLogs: Boolean, deleteAnrs: Boolean) {
    if (!deleteCrashLogs && !deleteAnrs) {
      throw IllegalArgumentException("Invalid parameters! deleteCrashLogs=$deleteCrashLogs, deleteAnrs=$deleteAnrs")
    }

    if (!createDirectoriesIfNotExists()) {
      settingsNotificationManager.get().cancel(SettingNotificationType.CrashLogOrAnr)
      return
    }

    val potentialReports = when {
      deleteCrashLogs && deleteAnrs -> {
        (crashLogsDir.listFiles() ?: emptyArray()) + (anrsDir.listFiles() ?: emptyArray())
      }
      deleteCrashLogs -> {
        crashLogsDir.listFiles() ?: emptyArray()
      }
      deleteAnrs -> {
        anrsDir.listFiles() ?: emptyArray()
      }
      else -> {
        throw IllegalArgumentException("Invalid parameters! deleteCrashLogs=$deleteCrashLogs, deleteAnrs=$deleteAnrs")
      }
    }

    if (potentialReports.isNullOrEmpty()) {
      Logger.d(TAG, "No new crash logs/ANRs")
      settingsNotificationManager.get().cancel(SettingNotificationType.CrashLogOrAnr)
      return
    }

    potentialReports.asSequence()
      .forEach { crashLogFile -> crashLogFile.delete() }

    val remainingCrashLogs = crashLogsDir.listFiles()?.size ?: 0
    val remainingAnrs = anrsDir.listFiles()?.size ?: 0

    if (remainingCrashLogs == 0 && remainingAnrs == 0) {
      settingsNotificationManager.get().cancel(SettingNotificationType.CrashLogOrAnr)
      return
    }

    // There are still crash logs left, so show the notifications if they are not shown yet
    settingsNotificationManager.get().notify(SettingNotificationType.CrashLogOrAnr)
  }

  fun sendCrashLogs(
    reportFiles: List<ReportFile>,
    onCrashLogsSent: (ModularResult<Unit>) -> Unit
  ) {
    serializedCoroutineExecutor.post {
      if (!createDirectoriesIfNotExists()) {
        withContext(Dispatchers.Main) { onCrashLogsSent(ModularResult.value(Unit)) }
        return@post
      }

      if (reportFiles.isEmpty()) {
        withContext(Dispatchers.Main) { onCrashLogsSent(ModularResult.value(Unit)) }
        return@post
      }

      val results = supervisorScope {
        reportFiles
          .mapNotNull { crashLog -> createReportRequest(crashLog) }
          .map { request ->
            appScope.async(Dispatchers.IO) {
              processSingleRequest(request.reportRequest, request.crashLogFile)
            }
          }
          .awaitAll()
      }

      // If no more crash logs left, remove the notification
      if (!hasReportFiles()) {
        settingsNotificationManager.get().cancel(SettingNotificationType.CrashLogOrAnr)
      }

      val errorResult = results
        .firstOrNull { result -> result is ModularResult.Error }
        ?: results.first() as ModularResult.Value<Unit>

      withContext(Dispatchers.Main) { onCrashLogsSent(errorResult) }
    }
  }

  fun sendComment(
    issueNumber: Int,
    description: String,
    logs: String?,
    onReportSendResult: (ModularResult<Unit>) -> Unit
  ) {
    require(description.isNotEmpty() || logs != null) { "description is empty" }
    require(description.length <= MAX_DESCRIPTION_LENGTH) { "description is too long ${description.length}" }
    logs?.let { require(it.length <= MAX_LOGS_LENGTH) { "logs are too long" } }

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

  fun sendReport(
    title: String,
    description: String,
    logs: String?,
    onReportSendResult: (ModularResult<Unit>) -> Unit
  ) {
    require(title.isNotEmpty()) { "title is empty" }
    require(description.isNotEmpty() || logs != null) { "description is empty" }
    require(title.length <= MAX_TITLE_LENGTH) { "title is too long ${title.length}" }
    require(description.length <= MAX_DESCRIPTION_LENGTH) { "description is too long ${description.length}" }
    logs?.let { require(it.length <= MAX_LOGS_LENGTH) { "logs are too long" } }

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

  private fun deleteOldAnrReports() {
    val prevAnrs = anrsDir.listFiles() ?: arrayOf<File>()
    if (prevAnrs.size <= MAX_ANR_FILES) {
      return
    }

    val toDelete = prevAnrs.size - MAX_ANR_FILES
    if (toDelete <= 0) {
      return
    }

    Logger.d(TAG, "deleteOldAnrReports() found ${prevAnrs.size} anrs, toDelete=${toDelete}")

    val oldestAnrFilesToDelete = prevAnrs.map { prevAnrFile ->
      val matcher = ANR_EXTRACT_TIME_PATTERN.matcher(prevAnrFile.name)
      if (!matcher.find()) {
        return@map prevAnrFile to 0L
      }

      return@map prevAnrFile to (matcher.groupOrNull(1)?.toLongOrNull() ?: 0L)
    }
      .sortedByDescending { (_, time) -> time }
      .takeLast(toDelete)
      .map { (anrFile, _) -> anrFile }

    oldestAnrFilesToDelete.forEach { anrFile ->
      Logger.d(TAG, "deleteOldAnrReports() deleting anrFile: '${anrFile.name}'")
      anrFile.delete()
    }
  }

  private fun deleteOldCrashlogs() {
    val prevCrashlogs = crashLogsDir.listFiles() ?: arrayOf<File>()
    if (prevCrashlogs.size <= MAX_CRASHLOG_FILES) {
      return
    }

    val toDelete = prevCrashlogs.size - MAX_CRASHLOG_FILES
    if (toDelete <= 0) {
      return
    }

    Logger.d(TAG, "deleteOldCrashlogs() found ${prevCrashlogs.size} crashlogs, toDelete=${toDelete}")

    val oldestCrashlogFilesToDelete = prevCrashlogs.map { prevCrashlogFile ->
      val matcher = CRASHLOG_EXTRACT_TIME_PATTERN.matcher(prevCrashlogFile.name)
      if (!matcher.find()) {
        return@map prevCrashlogFile to 0L
      }

      return@map prevCrashlogFile to (matcher.groupOrNull(1)?.toLongOrNull() ?: 0L)
    }
      .sortedByDescending { (_, time) -> time }
      .takeLast(toDelete)
      .map { (anrFile, _) -> anrFile }

    oldestCrashlogFilesToDelete.forEach { crashlogFile ->
      Logger.d(TAG, "deleteOldCrashlogs() deleting crashlogFile: '${crashlogFile.name}'")
      crashlogFile.delete()
    }
  }

  private suspend fun processSingleRequest(request: ReportRequest, crashLogFile: File): ModularResult<Unit> {
    BackgroundUtils.ensureBackgroundThread()

    // Delete old crash logs
    if (System.currentTimeMillis() - crashLogFile.lastModified() > MAX_CRASH_LOG_LIFETIME) {
      if (!crashLogFile.delete()) {
        Logger.e(TAG, "Couldn't delete crash log file: ${crashLogFile.absolutePath}")
      }

      return ModularResult.value(Unit)
    }

    val result = sendInternal(request)
    when (result) {
      is ModularResult.Value -> {
        Logger.d(TAG, "Crash log ${crashLogFile.absolutePath} sent")

        if (!crashLogFile.delete()) {
          Logger.e(TAG, "Couldn't delete crash log file: ${crashLogFile.absolutePath}")
        }
      }
      is ModularResult.Error -> {
        if (result.error is ReportError) {
          val reportError = result.error as ReportError

          Logger.e(TAG, "Bad response: '${reportError.errorMessage}'")
        } else {
          Logger.e(TAG, "Error while trying to send crash log", result.error)
        }
      }
    }

    return result
  }

  fun getReportFooter(): String {
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

  private fun formatAppRunningTime(): String {
    val time = ((appContext as? Chan)?.appRunningTime) ?: -1L
    if (time <= 0) {
      return "Unknown (appContext=${appContext::class.java.simpleName}), time ms: $time"
    }

    return appRunningTimeFormatter.print(Duration.millis(time).toPeriod())
  }

  private fun createReportRequest(reportFile: ReportFile): ReportRequestWithFile? {
    BackgroundUtils.ensureBackgroundThread()

    val log = try {
      reportFile.file.readText()
    } catch (error: Throwable) {
      Logger.e(TAG, "Error reading crash log file", error)
      return null
    }

    val isAnr = reportFile.fileName.startsWith(ANR_FILE_NAME_PREFIX)

    val title = if (isAnr) {
      "ANR report"
    } else {
      "Crash report"
    }

    val body = buildString(8192) {
      append("```")
      append(log)
      append("```")
    }

    val request = if (isAnr) {
      ReportRequest.anr(
        title = title,
        body = body
      )
    } else {
      ReportRequest.crashLog(
        title = title,
        body = body
      )
    }

    return ReportRequestWithFile(
      reportRequest = request,
      crashLogFile = reportFile.file
    )
  }

  private fun createDirectoriesIfNotExists(): Boolean {
    var success = true

    if (!crashLogsDir.exists()) {
      if (!crashLogsDir.mkdir()) {
        Logger.e(TAG, "Couldn't create crash logs directory! path = ${crashLogsDir.absolutePath}")
        success = false
      }
    }

    if (!anrsDir.exists()) {
      if (!anrsDir.mkdir()) {
        Logger.e(TAG, "Couldn't create ANRs directory! path = ${anrsDir.absolutePath}")
        success = false
      }
    }

    return success
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

  fun postTask(task: () -> Unit) {
    serializedCoroutineExecutor.post { task() }
  }

  private class ReportError(val errorMessage: String) : Exception(errorMessage)

  data class ReportRequestWithFile(
    val reportRequest: ReportRequest,
    val crashLogFile: File
  )

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
          labels = listOf("New", "Crash")
        )
      }

      fun report(title: String, body: String): ReportRequest {
        return ReportRequest(
          title = title,
          body = body,
          labels = listOf("New", "Report")
        )
      }

      fun comment(body: String): ReportRequest {
        return ReportRequest(
          title = null,
          body = body,
          labels = emptyList()
        )
      }

      fun anr(title: String, body: String): ReportRequest {
        return ReportRequest(
          title = title,
          body = body,
          labels = listOf("New", "ANR")
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
    private const val CRASH_LOG_FILE_NAME_PREFIX = "crashlog"
    private const val ANR_FILE_NAME_PREFIX = "anr"
    private const val MAX_ANR_FILES = 5
    private const val MAX_CRASHLOG_FILES = 5

    private val MAX_CRASH_LOG_LIFETIME = TimeUnit.DAYS.toMillis(3)
    private val ANR_EXTRACT_TIME_PATTERN = Pattern.compile("${ANR_FILE_NAME_PREFIX}_(\\d+)\\.txt")
    private val CRASHLOG_EXTRACT_TIME_PATTERN = Pattern.compile("${CRASH_LOG_FILE_NAME_PREFIX}_(\\d+)\\.txt")

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