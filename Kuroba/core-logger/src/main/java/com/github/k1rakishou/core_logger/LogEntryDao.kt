package com.github.k1rakishou.core_logger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
internal abstract class LogEntryDao {

    suspend fun insertLogEntries(loggerDatabase: List<LogEntry>) {
        val logEntryEntities = loggerDatabase.map { logEntry ->
            LogEntryEntity(
                level = logEntry.level.logLevelTag,
                tag = logEntry.tag,
                message = logEntry.message,
                exceptionClass = logEntry.throwable?.let { throwable -> throwable::class.java.simpleName },
                exceptionMessage = buildString {
                    val message = logEntry.throwable?.message
                    val stacktrace = logEntry.throwable?.stackTraceToString()

                    if (message.isNullOrEmpty() && stacktrace.isNullOrEmpty()) {
                        return@buildString
                    }

                    append(message)
                    appendLine(stacktrace)
                },
                time = logEntry.time
            )
        }

        insertMany(logEntryEntities)
    }

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMany(logEntryEntities: List<LogEntryEntity>)

    @Query("""
        SELECT * 
        FROM log_entries
        WHERE 
            time >= :endTime
        AND
            level IN (:logLevels)
        ORDER BY time DESC
    """)
    abstract suspend fun selectLogsDesc(endTime: Long, logLevels: List<String>): List<LogEntryEntity>

    @Query("""
        SELECT * 
        FROM log_entries
        WHERE 
            time >= :endTime
        AND
            level IN (:logLevels)
        ORDER BY time ASC
    """)
    abstract suspend fun selectLogsAsc(endTime: Long, logLevels: List<String>): List<LogEntryEntity>

    @Query("""
        DELETE FROM log_entries
        WHERE time < :endTime
    """)
    abstract suspend fun removeLogsOlderThan(endTime: Long)

}