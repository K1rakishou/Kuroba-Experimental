package com.github.k1rakishou.chan.utils

import android.text.SpannableString
import androidx.core.text.getSpans
import com.github.k1rakishou.core_spannable.BackgroundColorSpanHashed
import com.github.k1rakishou.core_spannable.PostSearchQuerySpan

object SpannableHelper {

  fun findAllQueryEntriesInsideSpannableStringAndMarkThem(
    inputQueries: Collection<String>,
    spannableString: SpannableString,
    color: Int,
    removePrevSpans: Boolean,
    minQueryLength: Int
  ) {
    if (removePrevSpans) {
      spannableString.getSpans<PostSearchQuerySpan>().forEach { prevSpan ->
        spannableString.removeSpan(prevSpan)
      }
    }

    val validQueries = inputQueries
      .filter { query ->
        return@filter query.isNotEmpty()
          && query.length <= spannableString.length
          && query.length >= minQueryLength
      }

    if (validQueries.isEmpty()) {
      return
    }

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
            spans += SpanToAdd(offset, query.length, PostSearchQuerySpan(color))
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
    val span: BackgroundColorSpanHashed
  )

}