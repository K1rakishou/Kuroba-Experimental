package com.github.k1rakishou.chan.utils

import com.github.k1rakishou.ChanSettings
import org.joda.time.DateTime
import org.joda.time.Period
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.format.PeriodFormatterBuilder
import java.util.*

object TimeUtils {
  private val calendar = Calendar.getInstance()

  private val REPORT_DATE_TIME_PRINTER = DateTimeFormatterBuilder()
    .append(ISODateTimeFormat.date())
    .appendLiteral(' ')
    .append(ISODateTimeFormat.hourMinuteSecond())
    .appendLiteral(" UTC")
    .toFormatter()
    .withZoneUTC()

  private val periodFormatter = PeriodFormatterBuilder().printZeroAlways()
    .minimumPrintedDigits(2)
    .appendMinutes()
    .appendLiteral(":")
    .appendSeconds()
    .toFormatter()

  @JvmStatic
  fun formatPeriod(period: Long): String {
    return periodFormatter.print(Period(period))
  }

  @JvmStatic
  fun getCurrentDateAndTimeUTC(): String {
    return REPORT_DATE_TIME_PRINTER.print(DateTime.now())
  }

  @JvmStatic
  fun isHalloweenToday(): Boolean {
    if (!ChanSettings.funThingsAreFun.get()) {
      return false
    }

    if (ChanSettings.forceHalloweenMode.get()) {
      return true
    }

    val day = calendar[Calendar.DAY_OF_MONTH]

    if (calendar[Calendar.MONTH] == Calendar.OCTOBER) {
      return day in 30..31
    }

    return false
  }

  @JvmStatic
  fun is4chanBirthdayToday(): Boolean {
    if (!ChanSettings.funThingsAreFun.get()) {
      return false
    }

    if (ChanSettings.force4chanBirthdayMode.get()) {
      return true
    }

    return calendar[Calendar.MONTH] == Calendar.OCTOBER
      && calendar[Calendar.DAY_OF_MONTH] == 1
  }

  @JvmStatic
  fun isChristmasToday(): Boolean {
    if (!ChanSettings.funThingsAreFun.get()) {
      return false
    }

    if (ChanSettings.forceChristmasMode.get()) {
      return true
    }

    val day = calendar[Calendar.DAY_OF_MONTH]

    val isDecember = calendar[Calendar.MONTH] == Calendar.DECEMBER
    if (isDecember) {
      return day in 24..25
    }

    return false
  }

  @JvmStatic
  fun isNewYearToday(): Boolean {
    if (!ChanSettings.funThingsAreFun.get()) {
      return false
    }

    if (ChanSettings.forceNewYearMode.get()) {
      return true
    }

    val day = calendar[Calendar.DAY_OF_MONTH]
    return calendar[Calendar.MONTH] == Calendar.DECEMBER && day == 31
  }

}