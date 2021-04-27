package com.github.k1rakishou.chan.utils

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.CharacterStyle
import androidx.core.text.getSpans
import com.github.k1rakishou.common.ELLIPSIZE_SYMBOL
import com.github.k1rakishou.core_spannable.PostSearchQueryBackgroundSpan
import com.github.k1rakishou.core_spannable.PostSearchQueryForegroundSpan
import com.github.k1rakishou.core_themes.ThemeEngine

object SpannableHelper {

  fun findAllQueryEntriesInsideSpannableStringAndMarkThem(
    inputQueries: Collection<String>,
    spannableString: SpannableString,
    color: Int,
    minQueryLength: Int
  ) {
    // Remove spans that may be left after previous execution of this function
    cleanSearchSpans(spannableString)

    val validQueries = inputQueries
      .filter { query ->
        return@filter query.isNotEmpty()
          && query.length <= spannableString.length
          && query.length >= minQueryLength
      }

    if (validQueries.isEmpty()) {
      return
    }

    var addedAtLeastOneSpan = false

    for (query in validQueries) {
      var offset = 0
      val spans = mutableListOf<SpanToAdd>()

      while (offset < spannableString.length) {
        if (query[0].equals(spannableString[offset], ignoreCase = true)) {
          val compared = compare(query, spannableString, offset)
          if (compared < 0) {
            break
          }

          if (compared == query.length) {
            spans += SpanToAdd(offset, query.length, PostSearchQueryBackgroundSpan(color))

            val textColor = if (ThemeEngine.isDarkColor(color)) {
              Color.LTGRAY
            } else {
              Color.DKGRAY
            }

            spans += SpanToAdd(offset, query.length, PostSearchQueryForegroundSpan(textColor))

            addedAtLeastOneSpan = true
          }

          offset += compared
          continue
        }

        ++offset
      }

      spans.forEach { spanToAdd ->
        spannableString.setSpan(
          spanToAdd.span,
          spanToAdd.position,
          spanToAdd.position + spanToAdd.length,
          0
        )
      }
    }

    // It is assumed that the original, uncut, text has this query somewhere where we can't see it now.
    if (!addedAtLeastOneSpan
      && spannableString.endsWith(ELLIPSIZE_SYMBOL)
      && spannableString.length >= ELLIPSIZE_SYMBOL.length
    ) {
      val start = spannableString.length - ELLIPSIZE_SYMBOL.length
      val end = spannableString.length

      spannableString.setSpan(PostSearchQueryBackgroundSpan(color), start, end, 0)

      val textColor = if (ThemeEngine.isDarkColor(color)) {
        Color.LTGRAY
      } else {
        Color.DKGRAY
      }

      spannableString.setSpan(PostSearchQueryForegroundSpan(textColor), start, end, 0)
    }
  }

  fun cleanSearchSpans(input: CharSequence) {
    if (input !is Spannable) {
      return
    }

    input.getSpans<PostSearchQueryBackgroundSpan>()
      .forEach { span -> input.removeSpan(span) }
    input.getSpans<PostSearchQueryForegroundSpan>()
      .forEach { span -> input.removeSpan(span) }
  }

  private fun compare(query: String, parsedComment: CharSequence, currentPosition: Int): Int {
    var compared = 0

    for (index in query.indices) {
      val ch = parsedComment.getOrNull(currentPosition + index)
        ?: return -1

      if (!query[index].equals(ch, ignoreCase = true)) {
        return compared
      }

      ++compared
    }

    return compared
  }

  private data class SpanToAdd(
    val position: Int,
    val length: Int,
    val span: CharacterStyle
  )

}