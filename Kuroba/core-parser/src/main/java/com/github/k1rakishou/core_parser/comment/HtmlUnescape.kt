package com.github.k1rakishou.core_parser.comment

import java.util.concurrent.ConcurrentHashMap

object HtmlUnescape {

  private val replacementRules = ConcurrentHashMap<Char, MutableList<ReplacementRule>>()

  init {
    addReplacementRule('l', "&lt;", "<")
    addReplacementRule('g', "&gt;", ">")
    addReplacementRule('n', "&nbsp;", " ")
    addReplacementRule('q', "&quot;", "\"")
    addReplacementRule('a', "&amp;", "&")
  }

  fun unescape(input: String): String {
    if (input.isEmpty()) {
      return ""
    }

    val stringBuilder = StringBuilder(input.length)
    var offset = 0

    while (offset < input.length) {
      val currentChar = input[offset]

      when (currentChar) {
        '&' -> {
          val processedCharacters = unescapeTextElement(stringBuilder, input, offset)
          if (processedCharacters > 0) {
            offset += processedCharacters
            continue
          }

          // fallthrough
        }
      }

      stringBuilder.append(currentChar)
      ++offset
    }

    return stringBuilder.toString()
  }

  private fun unescapeTextElement(
    stringBuilder: StringBuilder,
    input: String,
    offset: Int
  ): Int {
    val firstChar = input[offset]
    if (firstChar != '&') {
      return 0
    }

    val secondChar = input.getOrNull(offset + 1)
      ?: return 0

    when (secondChar) {
      '#' -> {
        return unescapeAsciiCharacter(input, offset, stringBuilder)
      }
      else -> {
        val replacementInfoList = replacementRules[secondChar]
        if (replacementInfoList == null || replacementInfoList.isEmpty()) {
          return 0
        }

        for (replacementInfo in replacementInfoList) {
          val equals = compareExactSafe(input, offset, replacementInfo.fullEscapedValue)
          if (equals) {
            stringBuilder.append(replacementInfo.replacement)
            return replacementInfo.fullEscapedValue.length
          }
        }

        return 0
      }
    }
  }

  private fun unescapeAsciiCharacter(
    input: String,
    offset: Int,
    stringBuilder: StringBuilder
  ): Int {
    var localOffset = 2
    var endFound = false
    val asciiEncoded = StringBuilder(3)

    while (true) {
      val asciiPart = input.getOrNull(offset + localOffset)
        ?: break

      ++localOffset

      if (!asciiPart.isDigit()) {
        if (asciiPart == ';') {
          endFound = true
        }

        break
      }

      asciiEncoded.append(asciiPart)
    }

    if (!endFound) {
      return 0
    }

    val asciiChar = asciiEncoded.toString().toIntOrNull()?.toChar()
      ?: return 0

    stringBuilder.append(asciiChar)

    return localOffset
  }

  private fun compareExactSafe(
    string: String,
    offset: Int,
    text: String
  ): Boolean {
    for ((index, ch1) in text.withIndex()) {
      val ch2 =  string.getOrNull(offset + index)
        ?: return false

      if (ch1 != ch2) {
        return false
      }
    }

    return true
  }

  class ReplacementRule(
    val fullEscapedValue: String,
    val replacement: String
  )

  private fun addReplacementRule(key: Char, value: String, replacement: String) {
    val replacementList = replacementRules.getOrPut(
      key = key,
      defaultValue = { mutableListOf() }
    )

    replacementList += ReplacementRule(
      fullEscapedValue = value,
      replacement = replacement
    )
  }

}