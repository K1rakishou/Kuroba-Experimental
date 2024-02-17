package com.github.k1rakishou.core_logger

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "log_entries",
    indices = [
        Index("level"),
        Index("tag"),
        Index("time")
    ]
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo("id")
    val id: Long = 0L,
    @ColumnInfo("level")
    val level: String,
    @ColumnInfo("tag")
    val tag: String,
    @ColumnInfo("message")
    val message: String,
    @ColumnInfo("exception_class")
    val exceptionClass: String?,
    @ColumnInfo("exception_message")
    val exceptionMessage: String?,
    @ColumnInfo("time")
    val time: Long
)