package com.github.k1rakishou.common

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.regex.Pattern

object StringUtils {
  private val IMAGE_THUMBNAIL_EXTRACTOR_PATTERN = Pattern.compile("/(\\d{12,32}+)s.(.*)")
  private val HEX_ARRAY = "0123456789ABCDEF".toLowerCase(Locale.ENGLISH).toCharArray()
  private const val RESERVED_CHARACTERS = "|?*<\":>+\\[\\]/'\\\\\\s"
  private const val RESERVED_CHARACTERS_DIR = "[" + RESERVED_CHARACTERS + "." + "]"
  private const val RESERVED_CHARACTERS_FILE = "[" + RESERVED_CHARACTERS + "]"
  private const val UTF8_BOM = "\uFEFF"

  fun bytesToHex(bytes: ByteArray): String {
    val result = CharArray(bytes.size * 2)
    var c = 0

    for (b in bytes) {
      result[c++] = HEX_ARRAY[b.toInt() shr 4 and 0xf]
      result[c++] = HEX_ARRAY[b.toInt() and 0xf]
    }

    return String(result)
  }

  fun convertThumbnailUrlToFilenameOnDisk(url: String): String? {
    val matcher = IMAGE_THUMBNAIL_EXTRACTOR_PATTERN.matcher(url)
    if (matcher.find()) {
      val filename = matcher.group(1)
      val extension = matcher.group(2)

      if (filename == null || extension == null) {
        return null
      }

      if (filename.isEmpty() || extension.isEmpty()) {
        return null
      }

      return String.format("%s_thumbnail.%s", filename, extension)
    }

    return null
  }

  fun extractFileNameExtension(filename: String): String? {
    val index = filename.lastIndexOf('.')
    if (index == -1) {
      return null
    }

    return filename.substring(index + 1)
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

  @JvmStatic
  fun trimToken(token: String): String {
    val tokenEdgeLength = (token.length.toFloat() * 0.2f).toInt() / 2

    val startTokenPart = token.substring(0, tokenEdgeLength)
    val endTokenPart = token.substring(token.length - tokenEdgeLength)

    return "${startTokenPart}<cut>${endTokenPart}"
  }

}