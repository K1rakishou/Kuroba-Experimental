package com.github.adamantcheese.model.converter

import androidx.room.TypeConverter
import org.joda.time.Period

class PeriodTypeConverter {

  @TypeConverter
  fun toPeriod(periodStr: String?): Period? {
    if (periodStr == null) {
      return null
    }

    return Period.parse(periodStr)
  }

  @TypeConverter
  fun fromPeriod(period: Period?): String? {
    return period?.toString()
  }

}