package com.github.k1rakishou.core_logger

data class LogEntry(
    val level: LogStorage.LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable?,
    val time: Long
)