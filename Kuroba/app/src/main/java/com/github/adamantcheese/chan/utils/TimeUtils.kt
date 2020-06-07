package com.github.adamantcheese.chan.utils

import org.joda.time.DateTime
import org.joda.time.Period
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.format.PeriodFormatterBuilder

object TimeUtils {
  private val REPORT_DATE_TIME_PRINTER = DateTimeFormatterBuilder()
    .append(ISODateTimeFormat.date())
    .appendLiteral(' ')
    .append(ISODateTimeFormat.hourMinuteSecond())
    .appendLiteral(" UTC")
    .toFormatter()
    .withZoneUTC()

  private val ARCHIVE_AVAILABILITY_TIME_PRINTER = PeriodFormatterBuilder()
    .appendLiteral("[")
    .minimumPrintedDigits(0)
    .printZeroAlways()
    .appendMinutes()
    .appendSuffix(":")
    .minimumPrintedDigits(2)
    .appendSeconds()
    .appendLiteral("]")
    .toFormatter()

  @JvmStatic
  fun getCurrentDateAndTimeUTC(): String {
    return REPORT_DATE_TIME_PRINTER.print(DateTime.now())
  }

  @JvmStatic
  fun getArchiveAvailabilityFormatted(archiveAvailability: Period): String {
    return ARCHIVE_AVAILABILITY_TIME_PRINTER.print(archiveAvailability)
  }
}