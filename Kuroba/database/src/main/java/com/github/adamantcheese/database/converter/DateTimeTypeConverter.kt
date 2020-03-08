package com.github.adamantcheese.database.converter

import androidx.room.TypeConverter
import org.joda.time.DateTime

class DateTimeTypeConverter {

    @TypeConverter
    fun toDateTime(instant: Long): DateTime {
        return DateTime(instant)
    }

    @TypeConverter
    fun fromDateTime(dateTime: DateTime): Long {
        return dateTime.millis
    }

}