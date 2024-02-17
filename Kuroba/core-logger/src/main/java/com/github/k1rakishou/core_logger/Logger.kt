/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.core_logger

import android.content.Context
import android.util.Log
import org.joda.time.DateTime
import org.joda.time.Duration
import org.joda.time.format.DateTimeFormatter

object Logger {
    private var tagPrefix: String? = null
    private var isCurrentBuildDev = false
    private var verboseLogsEnabled = false

    const val DI_TAG = "Dependency Injection"

    private val logStorage = LogStorage()

    private val time: String
        get() {
            if (isCurrentBuildDev) {
                return ""
            }

            return "(" + LogStorage.logTimeFormatter.print(DateTime.now()) + ") "
        }

    fun init(prefix: String, isDevBuild: Boolean, verboseLogs: Boolean, appContext: Context) {
        tagPrefix = prefix
        isCurrentBuildDev = isDevBuild
        verboseLogsEnabled = verboseLogs
        logStorage.init(prefix, appContext)
    }

    suspend fun <T> selectLogs(
        duration: Duration,
        logLevels: Array<LogStorage.LogLevel> = allLogLevels(),
        logSortOrder: LogStorage.LogSortOrder,
        logFormatter: (List<LogEntryEntity>, DateTimeFormatter) -> T = LogStorage.defaultFormatter()
    ): T? {
        return logStorage.selectLogs(
            duration = duration,
            logLevels = logLevels,
            logSortOrder = logSortOrder,
            logFormatter = logFormatter
        )
    }

    fun allLogLevels(): Array<LogStorage.LogLevel> {
        return arrayOf(
            LogStorage.LogLevel.Dependencies,
            LogStorage.LogLevel.Verbose,
            LogStorage.LogLevel.Debug,
            LogStorage.LogLevel.Warning,
            LogStorage.LogLevel.Error,
        )
    }

    @JvmStatic
    fun d(tag: String, message: String) {
        if (canLog()) {
            Log.d(time + tagPrefix + tag, message)
            logStorage.persistLog(LogStorage.LogLevel.Debug, tag, message)
        }
    }

    @JvmStatic
    fun w(tag: String, message: String) {
        if (canLog()) {
            Log.w(time + tagPrefix + tag, message)
            logStorage.persistLog(LogStorage.LogLevel.Warning, tag, message)
        }
    }

    @JvmStatic
    fun e(tag: String, message: String) {
        if (canLog()) {
            Log.e(time + tagPrefix + tag, message)
            logStorage.persistLog(LogStorage.LogLevel.Error, tag, message)
        }
    }

    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable?) {
        if (canLog()) {
            Log.e(time + tagPrefix + tag, message, throwable)
            logStorage.persistLog(LogStorage.LogLevel.Error, tag, message, throwable)
        }
    }

    @JvmStatic
    fun deps(message: String) {
        if (!isCurrentBuildDev) {
            return
        }

        if (canLog()) {
            val threadName = "[" + Thread.currentThread().name + ":" + Thread.currentThread().id + "]"
            val actualMessage = "${threadName} ${message}"

            d(DI_TAG, actualMessage)
            logStorage.persistLog(LogStorage.LogLevel.Dependencies, DI_TAG, actualMessage)
        }
    }

    // ========================================================

    fun verbose(tag: String, message: () -> String) {
        if (canLog() && verboseLogsEnabled) {
            val msg = message()

            Log.d(time + tagPrefix + tag, msg)
            logStorage.persistLog(LogStorage.LogLevel.Verbose, tag, msg)
        }
    }

    fun Any.verbose(tag: String? = null, message: () -> String) {
        if (canLog() && verboseLogsEnabled) {
            val msg = message()
            val actualTag = (tag ?: outerClassName())

            Log.d(time + tagPrefix + actualTag, msg)
            logStorage.persistLog(LogStorage.LogLevel.Verbose, actualTag, msg)
        }
    }

    fun debug(tag: String, message: () -> String) {
        if (canLog()) {
            val msg = message()

            Log.d(time + tagPrefix + tag, msg)
            logStorage.persistLog(LogStorage.LogLevel.Debug, tag, msg)
        }
    }

    fun Any.debug(tag: String? = null, message: () -> String) {
        if (canLog()) {
            val msg = message()
            val actualTag = (tag ?: outerClassName())

            Log.d(time + tagPrefix + actualTag, msg)
            logStorage.persistLog(LogStorage.LogLevel.Debug, actualTag, msg)
        }
    }

    fun warning(tag: String, message: () -> String) {
        if (canLog()) {
            val msg = message()

            Log.w(time + tagPrefix + tag, msg)
            logStorage.persistLog(LogStorage.LogLevel.Warning, tag, msg)
        }
    }

    fun Any.warning(tag: String? = null, message: () -> String) {
        if (canLog()) {
            val msg = message()
            val actualTag = (tag ?: outerClassName())

            Log.w(time + tagPrefix + actualTag, msg)
            logStorage.persistLog(LogStorage.LogLevel.Warning, actualTag, msg)
        }
    }

    fun error(tag: String, message: () -> String) {
        if (canLog()) {
            val msg = message()

            Log.e(time + tagPrefix + tag, msg)
            logStorage.persistLog(LogStorage.LogLevel.Error, tag, msg)
        }
    }

    fun Any.error(tag: String? = null, message: () -> String) {
        if (canLog()) {
            val msg = message()
            val actualTag = (tag ?: outerClassName())

            Log.e(time + tagPrefix + actualTag, msg)
            logStorage.persistLog(LogStorage.LogLevel.Error, actualTag, msg)
        }
    }

    fun error(tag: String, throwable: Throwable, message: () -> String) {
        if (canLog()) {
            val msg = message()

            Log.e(time + tagPrefix + tag, msg, throwable)
            logStorage.persistLog(LogStorage.LogLevel.Error, tag, msg)
        }
    }

    fun Any.error(tag: String? = null, throwable: Throwable, message: () -> String) {
        if (canLog()) {
            val msg = message()
            val actualTag = (tag ?: outerClassName())

            Log.e(time + tagPrefix + actualTag, msg, throwable)
            logStorage.persistLog(LogStorage.LogLevel.Error, actualTag, msg)
        }
    }

    @PublishedApi
    internal fun Any.outerClassName(): String {
        val javaClass = this::class.java
        val fullClassName = javaClass.name
        val outerClassName = fullClassName.substringBefore('$')
        val simplerOuterClassName = outerClassName.substringAfterLast('.')
        return if (simplerOuterClassName.isEmpty()) {
            fullClassName
        } else {
            simplerOuterClassName.removeSuffix("Kt")
        }
    }

    private fun canLog(): Boolean {
        return true
    }

}
