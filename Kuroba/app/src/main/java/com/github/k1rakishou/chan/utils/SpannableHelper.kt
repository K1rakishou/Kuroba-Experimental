package com.github.k1rakishou.chan.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.CharacterStyle
import android.text.style.ImageSpan
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.getSpans
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.ELLIPSIZE_SYMBOL
import com.github.k1rakishou.core_spannable.PostSearchQueryBackgroundSpan
import com.github.k1rakishou.core_spannable.PostSearchQueryForegroundSpan
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

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

  @OptIn(ExperimentalTime::class)
  suspend fun getCompositeCatalogNavigationSubtitle(
    siteManager: SiteManager,
    coroutineScope: CoroutineScope,
    context: Context,
    fontSizePx: Int,
    compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor,
    visibleCatalogsCount: Int = 3
  ): CharSequence {
    val spannableStringBuilder = SpannableStringBuilder()
    val catalogsBySites = compositeCatalogDescriptor.catalogDescriptors

    catalogsBySites
      .take(visibleCatalogsCount)
      .forEach { catalogDescriptor ->
        coroutineScope.ensureActive()

        var iconBitmap = withTimeoutOrNull(Duration.seconds(5)) {
          siteManager.bySiteDescriptor(catalogDescriptor.siteDescriptor())
            ?.icon()
            ?.getIconSuspend(context)
            ?.bitmap
        }

        if (iconBitmap == null) {
          iconBitmap = AppModuleAndroidUtils.getDrawable(R.drawable.error_icon).toBitmap()
        }

        if (spannableStringBuilder.isNotEmpty()) {
          spannableStringBuilder.append("+")
        }

        spannableStringBuilder
          .append("  ", getIconSpan(iconBitmap, fontSizePx), 0)
          .append(catalogDescriptor.boardCode())
      }

    if (catalogsBySites.size > visibleCatalogsCount) {
      val omittedCount = catalogsBySites.size - visibleCatalogsCount

      spannableStringBuilder
        .append(" + ")
        .append(omittedCount.toString())
        .append(" more")
    }

    return spannableStringBuilder
  }

  private fun getIconSpan(icon: Bitmap, fontSizePx: Int): ImageSpan {
    val iconSpan = ImageSpan(AndroidUtils.getAppContext(), icon)
    val width = (fontSizePx.toFloat() / (icon.height.toFloat() / icon.width.toFloat())).toInt()

    iconSpan.drawable.setBounds(0, 0, width, fontSizePx)
    return iconSpan
  }

}