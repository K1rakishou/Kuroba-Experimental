package com.github.k1rakishou.chan.features.drawer.data

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl

data class NavigationHistoryEntry(
  val descriptor: ChanDescriptor,
  val threadThumbnailUrl: HttpUrl,
  val siteThumbnailUrl: HttpUrl?,
  val title: String,
  val pinned: Boolean,
  val additionalInfo: NavHistoryBookmarkAdditionalInfo?
)

data class NavHistoryBookmarkAdditionalInfo(
  val watching: Boolean = false,
  val newPosts: Int = 0,
  val newQuotes: Int = 0,
  val isBumpLimit: Boolean = false,
  val isImageLimit: Boolean = false,
  val isLastPage: Boolean = false
) {

  fun toAnnotatedString(themeEngine: ThemeEngine): AnnotatedString {
    val text = buildString {
      append(newPosts)

      if (newQuotes > 0) {
        append(" (")
        append(newQuotes)
        append(")")
      }
    }

    val spanStyleRanges = mutableListOf<AnnotatedString.Range<SpanStyle>>()

    if (newQuotes > 0) {
      val spanStyle = SpanStyle(color = themeEngine.chanTheme.bookmarkCounterHasRepliesColorCompose)
      spanStyleRanges += AnnotatedString.Range(spanStyle, 0, text.length)
    } else if (!watching) {
      val spanStyle = SpanStyle(color = themeEngine.chanTheme.bookmarkCounterNotWatchingColorCompose)
      spanStyleRanges += AnnotatedString.Range(spanStyle, 0, text.length)
    } else {
      val spanStyle = SpanStyle(color = themeEngine.chanTheme.bookmarkCounterNormalColorCompose)
      spanStyleRanges += AnnotatedString.Range(spanStyle, 0, text.length)
    }

    if (isBumpLimit && isImageLimit) {
      spanStyleRanges += AnnotatedString.Range(SpanStyle(fontWeight = FontWeight.Bold), 0, text.length)
      spanStyleRanges += AnnotatedString.Range(SpanStyle(fontStyle = FontStyle.Italic), 0, text.length)
    } else if (isBumpLimit) {
      spanStyleRanges += AnnotatedString.Range(SpanStyle(fontStyle = FontStyle.Italic), 0, text.length)
    } else if (isImageLimit) {
      spanStyleRanges += AnnotatedString.Range(SpanStyle(fontWeight = FontWeight.Bold), 0, text.length)
    }

    if (isLastPage) {
      spanStyleRanges += AnnotatedString.Range(SpanStyle(textDecoration = TextDecoration.Underline), 0, text.length)
    }

    return AnnotatedString(text = text, spanStyles = spanStyleRanges)
  }

}