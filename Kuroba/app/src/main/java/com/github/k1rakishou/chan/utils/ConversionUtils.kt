package com.github.k1rakishou.chan.utils

import android.graphics.Color
import com.github.k1rakishou.common.groupOrNull
import okhttp3.internal.and
import java.util.*
import java.util.regex.Pattern


object ConversionUtils {
  private val FILE_SIZE_RAW_PATTERN = Pattern.compile("(\\d+).?(\\d+)?\\s+(\\w+)")

  private val SIZES_TABLE = mapOf(
    "GiB".uppercase(Locale.ENGLISH) to 1024 * 1024 * 1024L,
    "MiB".uppercase(Locale.ENGLISH) to 1024 * 1024L,
    "KiB".uppercase(Locale.ENGLISH) to 1024L,
    "GB".uppercase(Locale.ENGLISH) to 1000 * 1000 * 1000L,
    "MB".uppercase(Locale.ENGLISH) to 1000 * 1000L,
    "KB".uppercase(Locale.ENGLISH) to 1000,
    "B".uppercase(Locale.ENGLISH) to 1L,
  )

  fun fileSizeRawToFileSizeInBytes(input: String): Long? {
    val matcher = FILE_SIZE_RAW_PATTERN.matcher(input)
    if (!matcher.find()) {
      return null
    }

    val value = matcher.groupOrNull(1)?.toIntOrNull()
      ?: return null
    val fileSizeType = matcher.groupOrNull(3)?.uppercase(Locale.ENGLISH)
      ?: return null

    val fraction = matcher.groupOrNull(2)?.let { fractionString ->
      if (fractionString.isEmpty()) {
        return@let null
      }

      return@let fractionString.toIntOrNull()
        ?.toFloat()
        ?.div(Math.pow(10.0, fractionString.length.toDouble()).toInt())
    } ?: 0f

    val fileSizeMultiplier = SIZES_TABLE[fileSizeType]
      ?: return null

    return ((value * fileSizeMultiplier).toFloat() + (fraction * fileSizeMultiplier.toFloat())).toLong()
  }

  @JvmStatic
  fun intToByteArray(value: Int): ByteArray {
    return byteArrayOf(
      (value ushr 24).toByte(),
      (value ushr 16).toByte(),
      (value ushr 8).toByte(),
      value.toByte()
    )
  }

  @JvmStatic
  fun intToCharArray(value: Int): CharArray {
    return charArrayOf(
      (value ushr 24).toChar(),
      (value ushr 16).toChar(),
      (value ushr 8).toChar(),
      value.toChar()
    )
  }

  @JvmStatic
  fun byteArrayToInt(bytes: ByteArray): Int {
    return (bytes[0] and 0xFF) shl 24 or
      ((bytes[1] and 0xFF) shl 16) or
      ((bytes[2] and 0xFF) shl 8) or
      ((bytes[3] and 0xFF) shl 0)
  }

  @JvmStatic
  fun charArrayToInt(bytes: CharArray): Int {
    return (bytes[0].toByte() and 0xFF) shl 24 or
      ((bytes[1].toByte() and 0xFF) shl 16) or
      ((bytes[2].toByte() and 0xFF) shl 8) or
      ((bytes[3].toByte() and 0xFF) shl 0)
  }

  @JvmOverloads
  @JvmStatic
  fun toIntOrNull(maybeInt: String, radix: Int = 16): Int? {
    return maybeInt.toIntOrNull(radix)
  }

  @JvmStatic
  fun colorFromArgb(alpha: Int, r: String?, g: String?, b: String?): Int? {
    val red = r?.toIntOrNull(radix = 10) ?: return null
    val green = g?.toIntOrNull(radix = 10) ?: return null
    val blue = b?.toIntOrNull(radix = 10) ?: return null

    return Color.argb(alpha, red, green, blue)
  }

}