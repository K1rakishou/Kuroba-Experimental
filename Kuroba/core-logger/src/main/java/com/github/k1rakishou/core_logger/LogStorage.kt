package com.github.k1rakishou.core_logger

import android.content.Context
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import org.joda.time.Duration
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class LogStorage {
    private val logsDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(job + logsDispatcher + CoroutineName(TAG))

    private val loggerDatabaseRef = AtomicReference<LoggerDatabase?>(null)
    private val oldLogsCleared = AtomicBoolean(false)

    @GuardedBy("itself")
    private val logEntries = ArrayList<LogEntry>(256)
    private val actor = coroutineScope.actor<Unit>(logsDispatcher) {
        for (event in channel) {
            processLogs()
        }
    }

    private var tagPrefix: String = ""

    fun init(prefix: String, appContext: Context) {
        tagPrefix = prefix
        loggerDatabaseRef.set(LoggerDatabase.buildDatabase(appContext))
    }

    suspend fun <T> selectLogs(
        duration: Duration,
        logLevels: Array<LogLevel>,
        logSortOrder: LogSortOrder,
        logFormatter: (List<LogEntryEntity>, DateTimeFormatter) -> T
    ): T? {
        val loggerDatabase = loggerDatabaseRef.get()
        if (loggerDatabase == null) {
            return null
        }

        if (duration.millis <= 0L) {
            return null
        }

        return withContext(logsDispatcher) {
            val endTime = DateTime.now().minus(duration).millis

            val startDay = DateTime.now().dayOfMonth
            val endDay = DateTime.now().minus(duration).dayOfMonth

            val timeFormatter = if (startDay != endDay || duration.standardDays > 1) {
                logDateTimeFormatter
            } else {
                logTimeFormatter
            }

            val logLevelNames = logLevels.map { logLevel -> logLevel.logLevelTag }

            val logEntryEntities = when (logSortOrder) {
                LogSortOrder.Ascending -> {
                    loggerDatabase.logEntryDao().selectLogsAsc(
                        endTime = endTime,
                        logLevels = logLevelNames
                    )
                }
                LogSortOrder.Descending -> {
                    loggerDatabase.logEntryDao().selectLogsDesc(
                        endTime = endTime,
                        logLevels = logLevelNames
                    )
                }
            }

            return@withContext logFormatter(logEntryEntities, timeFormatter)
        }
    }

    fun persistLog(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        synchronized(logEntries) {
            logEntries += LogEntry(level, tag, message, throwable, System.currentTimeMillis())
        }

        actor.trySend(Unit)
    }

    private suspend fun processLogs() {
        try {
            val loggerDatabase = loggerDatabaseRef.get()
                ?: return

            val localLogEntries = synchronized(logEntries) {
                val logEntriesCopy = logEntries.toList()
                logEntries.clear()
                return@synchronized logEntriesCopy
            }

            if (localLogEntries.isEmpty()) {
                return
            }

            if (oldLogsCleared.compareAndSet(false, true)) {
                val oneWeekInMillis = DateTime.now()
                    .minus(Duration.standardDays(7))
                    .millis

                loggerDatabase.logEntryDao().removeLogsOlderThan(oneWeekInMillis)
            }

            loggerDatabase.logEntryDao().insertLogEntries(localLogEntries)
        } catch (error: Throwable) {
            Log.e(TAG, "processLogs() error: ${error.message ?: error::class.java.simpleName}")
        }
    }

    enum class LogSortOrder {
        Ascending,
        Descending
    }

    enum class LogLevel(val logLevelTag: String, val logLevelName: String) {
        Dependencies("DI", "Dependency Injection"),
        Verbose("V", "Verbose"),
        Debug("D", "Debug"),
        Warning("W", "Warning"),
        Error("E", "Error");

        companion object {
            fun from(level: String): LogLevel {
                return when (level.uppercase()) {
                   "DI" -> LogLevel.Dependencies
                   "V" -> LogLevel.Verbose
                   "D" -> LogLevel.Debug
                   "W" -> LogLevel.Warning
                   "E" -> LogLevel.Error
                    else -> Debug
                }
            }
        }
    }

    companion object {
        private const val TAG = "LogStorage"

        val logTimeFormatter = DateTimeFormatterBuilder()
            .append(ISODateTimeFormat.hourMinuteSecondFraction())
            .toFormatter()

        val logDateTimeFormatter = DateTimeFormatterBuilder()
            .append(ISODateTimeFormat.dateHourMinuteSecondFraction())
            .toFormatter()

        fun <T> defaultFormatter(): (List<LogEntryEntity>, DateTimeFormatter) -> T {
            return func@ { logEntryEntities, dateTimeFormatter ->
                return@func buildString(capacity = 256) {
                    logEntryEntities.forEach { logEntryEntity ->
                        append("[")
                        append(logEntryEntity.level)
                        append("]")
                        append(" ")
                        append(dateTimeFormatter.print(logEntryEntity.time))
                        append(" ")
                        append(logEntryEntity.tag)
                        append(" ")
                        append(logEntryEntity.message)

                        if (!logEntryEntity.exceptionClass.isNullOrBlank()) {
                            appendLine()
                            append("Exception title: ")
                            append(logEntryEntity.exceptionClass)
                        }

                        if (!logEntryEntity.exceptionMessage.isNullOrBlank()) {
                            appendLine()
                            append("Exception body: ")
                            append(logEntryEntity.exceptionMessage)
                        }

                        appendLine()
                    }
                } as T
            }
        }

        fun <T : AnnotatedString> composeFormatter(): (List<LogEntryEntity>, DateTimeFormatter) -> T {
            return func@ { logEntryEntities, dateTimeFormatter ->
                return@func buildAnnotatedString {
                    logEntryEntities.forEach { logEntryEntity ->
                        val logLevel = LogLevel.from(logEntryEntity.level)

                        val mainColor = when (logLevel) {
                            LogLevel.Dependencies -> Color.DarkGray
                            LogLevel.Verbose -> Color.Gray
                            LogLevel.Debug -> Color.LightGray
                            LogLevel.Warning -> Color(0xffff7b00L)
                            LogLevel.Error -> Color.Red
                        }

                        append("[")
                        withStyle(SpanStyle(color = mainColor)) {
                            append(logEntryEntity.level)
                        }
                        append("]")
                        append(" ")
                        withStyle(SpanStyle(color = Color.LightGray)) {
                            append(dateTimeFormatter.print(logEntryEntity.time))
                        }
                        append(" ")
                        withStyle(SpanStyle(color = logEntryEntity.tag.toComposeColor())) {
                            append(logEntryEntity.tag)
                        }
                        append(" ")
                        withStyle(SpanStyle(color = mainColor)) {
                            append(logEntryEntity.message)
                        }

                        if (!logEntryEntity.exceptionClass.isNullOrBlank()) {
                            appendLine()
                            withStyle(SpanStyle(color = Color.Red)) {
                                append("Exception title: ")
                                append(logEntryEntity.exceptionClass)
                            }
                        }

                        if (!logEntryEntity.exceptionMessage.isNullOrBlank()) {
                            appendLine()
                            withStyle(SpanStyle(color = Color.Red)) {
                                append("Exception message: ")
                                append(logEntryEntity.exceptionMessage)
                            }
                        }

                        appendLine()
                    }
                } as T
            }
        }

        private fun String.toComposeColor(): Color {
            val hash: Int = this.hashCode()

            val r = hash shr 24 and 0xff
            val g = hash shr 16 and 0xff
            val b = hash shr 8 and 0xff

            return Color((0xff shl 24) + (r shl 16) + (g shl 8) + b)
        }

    }

}