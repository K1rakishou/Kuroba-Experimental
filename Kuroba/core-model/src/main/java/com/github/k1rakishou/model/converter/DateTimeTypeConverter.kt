package com.github.k1rakishou.model.converter

import androidx.room.TypeConverter
import org.joda.time.DateTime

class DateTimeTypeConverter {

  @TypeConverter
  fun toDateTime(instant: Long?): DateTime? {
    if (instant == null) {
      return null
    }

    return DateTime(instant)
  }

  @TypeConverter
  fun fromDateTime(dateTime: DateTime?): Long? {
    if (dateTime == null) {
      return null
    }

    return dateTime.millis
  }

}