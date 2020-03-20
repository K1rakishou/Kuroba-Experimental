package com.github.adamantcheese.database.converter

import androidx.room.TypeConverter
import org.joda.time.Period

class PeriodTypeConverter {

    @TypeConverter
    fun toPeriod(periodStr: String): Period {
        return Period.parse(periodStr)
    }

    @TypeConverter
    fun fromPeriod(period: Period): String {
        return period.toString()
    }

}