package com.github.k1rakishou.common

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.Random
import kotlin.math.roundToLong

object StringUtils {
  private val RANDOM = Random()
  private val HEX_ARRAY = "0123456789ABCDEF".lowercase(Locale.ENGLISH).toCharArray()

  private const val RESERVED_CHARACTERS = "#|?*<\":>+\\[\\]/'\\\\\\s"
  private const val RESERVED_CHARACTERS_DIR = "[" + RESERVED_CHARACTERS + "." + "]"
  private const val RESERVED_CHARACTERS_FILE = "[" + RESERVED_CHARACTERS + "]"
  private const val UTF8_BOM = '\uFEFF'
  const val UNBREAKABLE_SPACE_SYMBOL = '\u00A0'

  @JvmStatic
  fun generatePassword(): String {
    return java.lang.Long.toHexString(RANDOM.nextLong())
  }

  fun bytesToHex(bytes: ByteArray): String {
    val result = CharArray(bytes.size * 2)
    var c = 0

    for (b in bytes) {
      result[c++] = HEX_ARRAY[b.toInt() shr 4 and 0xf]
      result[c++] = HEX_ARRAY[b.toInt() and 0xf]
    }

    return String(result)
  }

  @JvmStatic
  fun extractFileNameExtension(filename: String): String? {
    val index = filename.lastIndexOf('.')
    if (index == -1) {
      return null
    }

    return filename.substring(index + 1)
      .takeIf { ext -> ext.length <= 6 }
  }

  fun removeExtensionFromFileName(filename: String): String {
    val index = filename.lastIndexOf('.')
    if (index == -1) {
      return filename
    }

    return filename.substring(0, index)
  }

  fun dirNameRemoveBadCharacters(dirName: String?): String? {
    return dirName
      ?.replace(" ".toRegex(), "_")
      ?.replace(RESERVED_CHARACTERS_DIR.toRegex(), "")
  }

  /**
   * The same as dirNameRemoveBadCharacters but allows dots since file names can have extensions
   */
  fun fileNameRemoveBadCharacters(filename: String?): String? {
    return filename
      ?.replace(" ".toRegex(), "_")
      ?.replace(RESERVED_CHARACTERS_FILE.toRegex(), "")
  }

  fun encodeBase64(input: String): String {
    return Base64.encodeToString(input.toByteArray(StandardCharsets.UTF_8), Base64.DEFAULT)
  }

  @JvmStatic
  fun decodeBase64(base64Encoded: String): String? {
    val bytes = try {
      Base64.decode(base64Encoded, Base64.DEFAULT)
    } catch (error: Throwable) {
      return null
    }

    return bytesToHex(bytes)
  }

  fun endsWithAny(s: String, suffixes: Array<String>): Boolean {
    for (suffix in suffixes) {
      if (s.endsWith(suffix)) {
        return true
      }
    }
    return false
  }

  fun removeUTF8BOM(_input: String): String {
    var input = _input
    if (input.startsWith(UTF8_BOM)) {
      input = input.substring(1)
    }

    return input
  }

  fun String?.asFormattedToken(): String {
    return formatToken(this)
  }

  @JvmStatic
  fun formatToken(token: String?): String {
    if (token == null) {
      return "<null>"
    }

    if (token.isEmpty()) {
      return "<empty>"
    }

    if (token.isBlank()) {
      return "<blank>"
    }

    val tokenPartLength = ((token.length.toFloat() * 0.2f).toInt() / 2).coerceAtMost(16)
    val startTokenPart = token.substring(0, tokenPartLength)
    val endTokenPart = token.substring(token.length - tokenPartLength)

    return "${startTokenPart}<cut>${endTokenPart}"
  }

  @JvmStatic
  fun calculateSimilarity(str1: String, str2: String): Float {
    if (str1.isEmpty() && str2.isEmpty()) {
      return 1f
    }

    val len1 = str1.length
    val len2 = str2.length

    val dp = Array(len1 + 1) { IntArray(len2 + 1) }

    for (i in 0..len1) {
      for (j in 0..len2) {
        when {
          i == 0 -> dp[i][j] = j
          j == 0 -> dp[i][j] = i
          else -> {
            val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
          }
        }
      }
    }

    val levenshteinDistance = dp[len1][len2]
    return 1.0f - (levenshteinDistance.toFloat() / maxOf(len1, len2).toFloat())
  }

  fun findJsonEnd(json: String, startIndex: Int): Int? {
    var jsonBracketsCounter = 1
    var offset = startIndex + 1

    while (true) {
      val ch = json.getOrNull(offset)
        ?: break

      if (ch == '{') {
        ++jsonBracketsCounter
      } else if (ch == '}') {
        --jsonBracketsCounter
      }

      if (jsonBracketsCounter == 0) {
        return offset + 1
      }

      if (jsonBracketsCounter < 0) {
        error("Invalid json")
      }

      ++offset
    }

    return null
  }

  // Parses time in formats like "01:11:12" or just "11:12" or even with fractional seconds part like "12:34.999"
  fun String.parseTimeStringAsMillis(): Long {
    if (this.isBlank()) {
      return 0L
    }

    val parts = this.trim().split(":")
    var hours = 0L
    var minutes = 0L
    var seconds = 0.0

    when (parts.size) {
      1 -> seconds = parts[0].toDouble()
      2 -> {
        minutes = parts[0].toLong()
        seconds = parts[1].toDouble()
      }
      else -> {
        hours = parts[0].toLong()
        minutes = parts[1].toLong()
        seconds = parts[2].toDouble()
      }
    }
    return hours * 3600_000L + minutes * 60_000L + (seconds * 1000.0).roundToLong()
  }

  fun String.parseTimeStringAsSeconds() = parseTimeStringAsMillis() / 1000L

  fun String.splitOnce(delimiter: String): Pair<String, String>? {
    return indexOf(delimiter)
      .takeIf { index -> index >= 0 }
      ?.let { index ->
        Pair(
          this.slice(0..<index),
          this.slice((index + 1)..<this.length)
        )
      }
  }

}