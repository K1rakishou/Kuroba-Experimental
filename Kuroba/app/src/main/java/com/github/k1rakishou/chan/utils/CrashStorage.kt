package com.github.k1rakishou.chan.utils

import android.content.Context
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_logger.Logger
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.IdentityHashMap

object CrashStorage {
  private const val TAG = "CrashStorage"
  private const val CRASH_FILE_VERSION = 1
  private const val CRASH_FILE = "crash_file_data.txt"

  fun saveCrash(
    context: Context,
    exception: Throwable,
    userAgent: String,
    appLifeTime: String
  ) {
    try {
      // Save throwable stacktrace to file
      val file = File(context.filesDir, CRASH_FILE)
      if (file.exists()) {
        file.delete()
      }

      file.createNewFile()

      val crashFileData = buildString(capacity = 4096 * 4) {
        appendLine(CRASH_FILE_VERSION)
        appendLine(userAgent)
        appendLine(appLifeTime)
        appendLine(exception::class.java.name)
        appendLine(extractExceptionMessage(exception))
        appendLine(getStackTraceString(exception))
      }

      file.writeText(crashFileData)

      Logger.d(TAG, "Crash data saved successfully")
    } catch (e: Exception) {
      Logger.e(TAG, "Failed to save crash data", e)
    }
  }

  fun loadCrash(context: Context): CrashData? {
    try {
      val file = File(context.filesDir, CRASH_FILE)
      if (!file.exists()) {
        Logger.d(TAG, "No crash file found")
        return null
      }

      val crashFileTextLines = file.readText().lines()
      var index = 0

      val version = crashFileTextLines.getOrNull(index++)?.toIntOrNull()
      if (version == null) {
        Logger.warning(TAG) { "Failed to read crash file version (raw: '${crashFileTextLines.getOrNull(index)}')" }
        return null
      }

      val userAgent = crashFileTextLines.getOrNull(index++)
        ?.takeIf { it.isNotNullNorBlank() }
        ?: "No user-agent"
      val appLifeTime = crashFileTextLines.getOrNull(index++)
        ?.takeIf { it.isNotNullNorBlank() }
        ?: "No app lifetime"
      val errorClass = crashFileTextLines.getOrNull(index++)
        ?.takeIf { it.isNotNullNorBlank() }
        ?: "No error class"
      val errorMessage = crashFileTextLines.getOrNull(index++)
        ?.takeIf { it.isNotNullNorBlank() }
        ?: "No error message"
      val stackTrace = crashFileTextLines
        .slice(index..<crashFileTextLines.size)
        .joinToString(separator = "\n")

      Logger.d(TAG, "Crash data loaded")

      return CrashData(
        errorClassName = errorClass,
        errorMessage = errorMessage,
        stackTrace = stackTrace,
        userAgent = userAgent,
        appLifeTime = appLifeTime
      )
    } catch (e: Exception) {
      Logger.e(TAG, "Failed to load crash data", e)
      return null
    }
  }

  fun clearCrash(context: Context) {
    try {
      val file = File(context.filesDir, CRASH_FILE)
      if (file.exists()) {
        file.delete()
      }

      Logger.d(TAG, "Crash data cleared")
    } catch (e: Exception) {
      Logger.e(TAG, "Failed to clear crash data", e)
    }
  }

  private fun getStackTraceString(throwable: Throwable): String {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    throwable.printStackTrace(pw)
    return sw.toString()
  }

  private fun extractExceptionMessage(exception: Throwable): String? {
    var message = exception.message
    var throwable: Throwable? = exception

    val processed = IdentityHashMap<Throwable, Unit>()
    processed.put(exception, Unit)

    while (true) {
      if (throwable == null) {
        break
      }

      val parentMessage = throwable.message
      if (parentMessage.isNullOrEmpty()) {
        break
      }

      throwable = throwable.cause

      if (throwable != null && processed.contains(throwable)) {
        break
      }

      val isAppStacktrace = throwable
        ?.stackTrace
        ?.any { stackTraceElement -> stackTraceElement.className.contains("com.github.k1rakishou") }
        ?: false

      if (isAppStacktrace) {
        message = parentMessage
      }
    }

    return message ?: "No message"
  }

  data class CrashData(
    val errorClassName: String,
    val errorMessage: String,
    val stackTrace: String,
    val userAgent: String,
    val appLifeTime: String
  )
}