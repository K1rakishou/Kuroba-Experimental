package com.github.k1rakishou.common

import androidx.annotation.AnyThread
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

object RegexPatternCompiler {
  private const val NULL_CHAR = '\u0000'

  private val isRegexPattern = Pattern.compile("^/(.*)/(\\w+)?$")

  // an escaped \ and an escaped *, to replace an escaped * from escapeRegex
  private val wildcardPattern = Pattern.compile("\\\\\\*")

  private val filterFilthyPattern = Pattern.compile("([.^$*+?()\\]\\[{}\\\\|-])")

  private const val CASE_INSENSITIVE_FLAG = 'i'
  private const val CASE_MULTILINE_FLAG = 'm'

  @AnyThread
  fun compile(rawPattern: String?, extraPatternFlags: Int = 0): PatternCompilationResult {
    if (rawPattern.isNullOrEmpty()) {
      return PatternCompilationResult.PatternIsEmpty
    }

    val isRegex = isRegexPattern.matcher(rawPattern)

    when {
      isRegex.matches() -> {
        var flags = 0
        // This is a /Pattern/
        val flagsGroup = isRegex.groupOrNull(2)
          ?.lowercase(Locale.ENGLISH)
          ?.toCharArray()

        if (flagsGroup != null) {
          val iFlagIndex = flagsGroup.indexOfFirst { ch -> ch == CASE_INSENSITIVE_FLAG }
          if (iFlagIndex >= 0) {
            flagsGroup[iFlagIndex] = NULL_CHAR
            flags = flags or Pattern.CASE_INSENSITIVE
          }

          val mFlagIndex = flagsGroup.indexOfFirst { ch -> ch == CASE_MULTILINE_FLAG }
          if (mFlagIndex >= 0) {
            flagsGroup[mFlagIndex] = NULL_CHAR
            flags = flags or Pattern.MULTILINE
          }

          if (extraPatternFlags != 0) {
            flags = flags or extraPatternFlags
          }

          val hasNonNullCharacters = flagsGroup.any { ch -> ch != NULL_CHAR }
          if (hasNonNullCharacters) {
            val remainingUnknownFlagsFormatted = flagsGroup
              .filter { ch -> ch != NULL_CHAR }
              .joinToString()

            return PatternCompilationResult.Error("Unsupported flags found: '${remainingUnknownFlagsFormatted}'")
          }
        }

        val patternGroup = isRegex.groupOrNull(1)
        if (patternGroup == null) {
          return PatternCompilationResult.Error("Group 1 (pattern) not found")
        }

        try {
          val pattern = Pattern.compile(patternGroup, flags)
          return PatternCompilationResult.Success(pattern, RegexMode.Pattern)
        } catch (e: PatternSyntaxException) {
          return PatternCompilationResult.Error(e.errorMessageOrClassName())
        }
      }
      rawPattern.length >= 2 && rawPattern[0] == '"' && rawPattern[rawPattern.length - 1] == '"' -> {
        // "matches an exact sentence"
        val text = escapeRegex(rawPattern.substring(1, rawPattern.length - 1))

        val pattern = Pattern.compile(text, Pattern.CASE_INSENSITIVE)
        return PatternCompilationResult.Success(pattern, RegexMode.ExactSentence)
      }
      else -> {
        val words = rawPattern.split(" ")
        val text = StringBuilder(32)
        var index = 0
        val wordsLength = words.size

        while (index < wordsLength) {
          val word = words[index]

          // Find a word (bounded by \b), replacing any * with \S*
          text
            .append("(\\b")
            .append(wildcardPattern.matcher(escapeRegex(word)).replaceAll("\\\\S*"))
            .append("\\b)")

          // Allow multiple words by joining them with |
          if (index < words.size - 1) {
            text.append("|")
          }

          index++
        }

        val pattern = Pattern.compile(
          text.toString(),
          Pattern.CASE_INSENSITIVE
        )

        if (words.size <= 1) {
          return PatternCompilationResult.Success(pattern, RegexMode.ExactSentence)
        } else {
          return PatternCompilationResult.Success(pattern, RegexMode.MultipleWords)
        }
      }
    }
  }

  private fun escapeRegex(filthy: String): String {
    // Escape regex special characters with a \
    return filterFilthyPattern.matcher(filthy).replaceAll("\\\\$1")
  }

  sealed class PatternCompilationResult {
    val patternOrNull: Pattern?
      get() {
        return when (this) {
          is Error,
          PatternIsEmpty -> null
          is Success -> pattern
        }
      }

    data class Success(val pattern: Pattern, val mode: RegexMode) : PatternCompilationResult()
    object PatternIsEmpty : PatternCompilationResult()
    data class Error(val errorMessage: String) : PatternCompilationResult()
  }

  enum class RegexMode {
    EmptyPattern,
    Pattern,
    ExactSentence,
    MultipleWords
  }

}