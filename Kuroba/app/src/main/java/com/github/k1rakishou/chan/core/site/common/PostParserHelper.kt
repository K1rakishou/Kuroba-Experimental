package com.github.k1rakishou.chan.core.site.common

import android.graphics.Color
import android.text.Spannable
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.setSpanSafe
import com.github.k1rakishou.core_spannable.BackgroundColorSpanHashed
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed
import com.github.k1rakishou.core_spannable.ThemeJsonSpannable
import com.github.k1rakishou.core_themes.ThemeEngine
import java.util.regex.Pattern

object PostParserHelper {
  private val THEME_JSON_KEYS = listOf(
    "\"name\"",
    "\"is_light_theme\"",
    "\"light_nav_bar\"",
    "\"light_status_bar\"",
    "\"accent_color\"",
    "\"primary_color\"",
    "\"back_color\""
  )

  private val RAW_COLOR_PATTERN = Pattern.compile("(#[a-fA-F0-9]{1,8})")
  private val THEME_NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"(.+)\"")
  private val THEME_TYPE_PATTERN = Pattern.compile("\"is_light_theme\"\\s*:\\s*(true|false)")

  @JvmStatic
  fun detectAndMarkThemeJsonSpan(parsedCommentText: Spannable) {
    if (!textContainsValidJson(parsedCommentText)) {
      return
    }

    val jsonParts = collectAllJsonParts(parsedCommentText);
    if (jsonParts.isEmpty()) {
      return
    }

    for ((jsonOpenBracketIndex, jsonEndBracketIndex) in jsonParts) {
      val themeJson = parsedCommentText.subSequence(jsonOpenBracketIndex, jsonEndBracketIndex) as Spannable
      if (!isProbablyThemeJson(themeJson)) {
        continue
      }

      val isLightThemeMaybe = tryFigureOutIsThemeLight(themeJson);
      if (isLightThemeMaybe == null) {
        continue
      }

      val themeName = tryExtractThemeName(themeJson)
      val isLightTheme = isLightThemeMaybe == true

      applyThemeJsonSpannable(parsedCommentText, jsonOpenBracketIndex, jsonEndBracketIndex, themeName, isLightTheme)
    }
  }

  private fun applyThemeJsonSpannable(
    parsedCommentText: Spannable,
    spannableStart: Int,
    spannableEnd: Int,
    themeName: String,
    isLightTheme: Boolean
  ) {
    val matcher = RAW_COLOR_PATTERN.matcher(parsedCommentText)
    var hasAtLeastOneValidColor = false

    while (matcher.find()) {
      val colorMaybe = matcher.groupOrNull(1)
        ?: continue

      val color = try {
        Color.parseColor(colorMaybe)
      } catch (error: Throwable) {
        continue
      }

      val textColor = if (ThemeEngine.isDarkColor(color)) {
        Color.WHITE
      } else {
        Color.BLACK
      }

      parsedCommentText.setSpanSafe(BackgroundColorSpanHashed(color), matcher.start(), matcher.end(), 0)
      parsedCommentText.setSpanSafe(ForegroundColorSpanHashed(textColor), matcher.start(), matcher.end(), 0)

      hasAtLeastOneValidColor = true
    }

    if (!hasAtLeastOneValidColor) {
      return
    }

    val themeJsonSpannable = ThemeJsonSpannable(themeName, isLightTheme)
    parsedCommentText.setSpanSafe(themeJsonSpannable, spannableStart, spannableEnd, 0)
  }

  private fun tryExtractThemeName(themeJson: CharSequence): String {
    val themeNameMatcher = THEME_NAME_PATTERN.matcher(themeJson)

    if (!themeNameMatcher.find()) {
      return "Unknown theme name"
    }

    return themeNameMatcher.groupOrNull(1)
      ?: "Unknown theme name"
  }

  private fun tryFigureOutIsThemeLight(themeJson: CharSequence): Boolean? {
    val themeTypeMatcher = THEME_TYPE_PATTERN.matcher(themeJson)
    if (!themeTypeMatcher.find()) {
      return null
    }

    return themeTypeMatcher.groupOrNull(1)?.toBoolean()
  }

  private fun isProbablyThemeJson(themeJson: CharSequence): Boolean {
    for (json_key in THEME_JSON_KEYS) {
      if (!themeJson.contains(json_key)) {
        return false
      }
    }

    return true
  }

  private fun textContainsValidJson(parsedCommentText: CharSequence): Boolean {
    var jsonOpenBracketIndex = -1;
    var jsonCloseBracketIndex = -1;

    for ((index, char) in parsedCommentText.withIndex()) {
      if (char == '{' && jsonOpenBracketIndex == -1) {
        jsonOpenBracketIndex = index;
      }

      if (char == '}') {
        jsonCloseBracketIndex = index
      }

      if (jsonOpenBracketIndex >= 0 && jsonCloseBracketIndex >= 0) {
        break
      }
    }

    return jsonOpenBracketIndex >= 0
      && jsonCloseBracketIndex >= 0
      && jsonCloseBracketIndex > jsonOpenBracketIndex
  }

  private fun collectAllJsonParts(parsedCommentText: CharSequence): List<Pair<Int, Int>> {
    var jsonOpenBracketIndex = -1;
    var openedBracketsCount = 0;
    val resultList = mutableListOf<Pair<Int, Int>>()

    if (parsedCommentText.isEmpty()) {
      return resultList;
    }

    for ((index, char) in parsedCommentText.withIndex()) {
      if (char == '{') {
        openedBracketsCount += 1;

        if (jsonOpenBracketIndex == -1) {
          jsonOpenBracketIndex = index;
        }

        continue;
      }

      if (char == '}') {
        openedBracketsCount -= 1;

        if (openedBracketsCount < 0) {
          break;
        }

        if (openedBracketsCount == 0) {
          resultList += Pair(jsonOpenBracketIndex, index + 1)
          jsonOpenBracketIndex = -1;
        }

        continue;
      }
    }

    return resultList;
  }


}