package com.github.k1rakishou.chan.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.getSpans
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.ELLIPSIZE_SYMBOL
import com.github.k1rakishou.common.setSpanSafe
import com.github.k1rakishou.core_spannable.BackgroundColorSpanHashed
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed
import com.github.k1rakishou.core_spannable.PostFilterHighlightBackgroundSpan
import com.github.k1rakishou.core_spannable.PostFilterHighlightForegroundSpan
import com.github.k1rakishou.core_spannable.PostSearchQueryBackgroundSpan
import com.github.k1rakishou.core_spannable.PostSearchQueryForegroundSpan
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.filter.HighlightFilterKeyword
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

object SpannableHelper {

  fun convertHtmlStringTagsIntoSpans(message: Spannable, chanTheme: ChanTheme): Spannable {
    val typefaceSpans = message.getSpans(0, message.length, TypefaceSpan::class.java)

    val spanBgColor = if (chanTheme.isBackColorDark) {
      0x22aaaaaaL.toInt()
    } else {
      0x22000000L.toInt()
    }

    for (span in typefaceSpans) {
      if (span.family.equals("monospace")) {
        val start = message.getSpanStart(span)
        val end = message.getSpanEnd(span)

        message.setSpanSafe(BackgroundColorSpan(spanBgColor), start, end, 0)
      }
    }

    val styleSpans: Array<StyleSpan> = message.getSpans(0, message.length, StyleSpan::class.java)
    for (span in styleSpans) {
      if (span.style == Typeface.ITALIC) {
        val start = message.getSpanStart(span)
        val end = message.getSpanEnd(span)

        message.setSpanSafe(BackgroundColorSpan(spanBgColor), start, end, 0)
      }
    }

    return message
  }

  fun findAllFilterHighlightQueryEntriesInsideSpannableStringAndMarkThem(
    inputQueries: Collection<String>,
    spannableString: SpannableString,
    minQueryLength: Int,
    keywordsToHighlightMap: Map<String, HighlightFilterKeyword>
  ) {
    findAllQueryEntriesInsideSpannableStringAndMarkThem(
      inputQueries = inputQueries,
      spannableString = spannableString,
      bgColor = 0,
      minQueryLength = minQueryLength,
      shouldDeleteSpanFunc = { style ->
        style is PostFilterHighlightForegroundSpan || style is PostFilterHighlightBackgroundSpan
      },
      queryBgSpanColorFunc = { keyword ->
        val bgColor = keywordsToHighlightMap[keyword]?.color ?: 0
        val bgColorWithAlpha = ColorUtils.setAlphaComponent(bgColor, 160)

        PostFilterHighlightBackgroundSpan(bgColorWithAlpha)
      },
      queryFgSpanColorFunc = { keyword ->
        val color = keywordsToHighlightMap[keyword]?.color ?: 0

        val textColor = if (ThemeEngine.isDarkColor(color)) {
          Color.LTGRAY
        } else {
          Color.DKGRAY
        }

        PostFilterHighlightForegroundSpan(textColor)
      }
    )
  }

  fun findAllQueryEntriesInsideSpannableStringAndMarkThem(
    inputQueries: Collection<String>,
    spannableString: SpannableString,
    bgColor: Int,
    minQueryLength: Int,
    shouldDeleteSpanFunc: (CharacterStyle) -> Boolean = { style ->
      style is PostSearchQueryBackgroundSpan || style is PostSearchQueryForegroundSpan
    },
    queryBgSpanColorFunc: (String) -> BackgroundColorSpanHashed = {
      val bgColorWithAlpha = ColorUtils.setAlphaComponent(bgColor, 160)
      PostSearchQueryBackgroundSpan(bgColorWithAlpha)
    },
    queryFgSpanColorFunc: (String) -> ForegroundColorSpanHashed = {
      val textColor = if (ThemeEngine.isDarkColor(bgColor)) {
        Color.LTGRAY
      } else {
        Color.DKGRAY
      }

      PostSearchQueryForegroundSpan(textColor)
    }
  ) {
    // Remove spans that may be left after previous execution of this function
    cleanSearchSpans(spannableString, shouldDeleteSpanFunc)

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
            val keyword = spannableString.substring(offset, offset + query.length)

            spans += SpanToAdd(offset, query.length, queryBgSpanColorFunc(keyword))
            spans += SpanToAdd(offset, query.length, queryFgSpanColorFunc(keyword))

            addedAtLeastOneSpan = true
          }

          offset += compared
          continue
        }

        ++offset
      }

      spans.forEach { spanToAdd ->
        spannableString.setSpanSafe(spanToAdd.span, spanToAdd.position, spanToAdd.position + spanToAdd.length, 0)
      }
    }

    // It is assumed that the original, uncut, text has this query somewhere where we can't see it now.
    if (bgColor != 0
      && !addedAtLeastOneSpan
      && spannableString.endsWith(ELLIPSIZE_SYMBOL)
      && spannableString.length >= ELLIPSIZE_SYMBOL.length
    ) {
      val start = spannableString.length - ELLIPSIZE_SYMBOL.length
      val end = spannableString.length

      val textColor = if (ThemeEngine.isDarkColor(bgColor)) {
        Color.LTGRAY
      } else {
        Color.DKGRAY
      }

      spannableString.setSpanSafe(PostSearchQueryBackgroundSpan(bgColor), start, end, 0)
      spannableString.setSpanSafe(PostSearchQueryForegroundSpan(textColor), start, end, 0)
    }
  }

  fun cleanSearchSpans(
    input: CharSequence,
    shouldDeleteSpanFunc: (CharacterStyle) -> Boolean = { style ->
      style is PostSearchQueryBackgroundSpan || style is PostSearchQueryForegroundSpan
    }
  ) {
    if (input !is Spannable) {
      return
    }

    input.getSpans<CharacterStyle>()
      .forEach { span ->
        if (shouldDeleteSpanFunc(span)) {
          input.removeSpan(span)
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
    val span: CharacterStyle
  )

  @OptIn(ExperimentalTime::class)
  suspend fun getCompositeCatalogNavigationSubtitle(
    siteManager: SiteManager,
    coroutineScope: CoroutineScope,
    context: Context,
    fontSizePx: Int,
    compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor,
    visibleCatalogsCount: Int = 4
  ): CharSequence {
    val spannableStringBuilder = SpannableStringBuilder()
    val catalogsBySites = compositeCatalogDescriptor.catalogDescriptors
    val cache = mutableMapOf<SiteDescriptor, Bitmap>()

    catalogsBySites
      .take(visibleCatalogsCount)
      .forEach { catalogDescriptor ->
        coroutineScope.ensureActive()

        val bitmap = cache.getOrPut(
          key = catalogDescriptor.siteDescriptor(),
          defaultValue = {
            var iconBitmap = withTimeoutOrNull(5.seconds) {
              siteManager.bySiteDescriptor(catalogDescriptor.siteDescriptor())
                ?.icon()
                ?.getIconSuspend(context)
                ?.bitmap
            }

            if (iconBitmap == null) {
              iconBitmap = AppModuleAndroidUtils.getDrawable(R.drawable.error_icon).toBitmap()
            }

            return@getOrPut iconBitmap
          })

        if (spannableStringBuilder.isNotEmpty()) {
          spannableStringBuilder.append("+")
        }

        spannableStringBuilder
          .append("  ", getIconSpan(bitmap, fontSizePx), 0)
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

class WebViewLink(
  val type: Type,
  val link: String
) : ClickableSpan() {

  override fun onClick(widget: View) {
  }

  enum class Type {
    BanMessage
  }

}

class WebViewLinkMovementMethod(
  private val webViewLinkClickListener: ClickListener
) : LinkMovementMethod() {

  override fun onTouchEvent(widget: TextView?, buffer: Spannable?, event: MotionEvent?): Boolean {
    val actionMasked = event?.actionMasked

    if (widget != null && buffer != null && actionMasked != null) {
      var x = event.x.toInt()
      var y = event.y.toInt()

      x -= widget.totalPaddingLeft
      y -= widget.totalPaddingTop
      x += widget.scrollX
      y += widget.scrollY

      val layout = widget.layout
      val line = layout.getLineForVertical(y)
      val lineLeft = layout.getLineLeft(line)
      val lineRight = layout.getLineRight(line)

      if (clickCoordinatesHitPostComment(x, lineLeft, lineRight)) {
        val offset = layout.getOffsetForHorizontal(line, x.toFloat())
        val clickableSpan = buffer.getSpans(offset, offset, ClickableSpan::class.java)?.lastOrNull()
        if (clickableSpan != null) {
          if (actionMasked == MotionEvent.ACTION_UP) {
            if (clickableSpan is WebViewLink) {
              webViewLinkClickListener.onWebViewLinkClick(clickableSpan.type, clickableSpan.link)
            } else {
              clickableSpan.onClick(widget)
            }
          }

          return true
        }
      }
    }

    return super.onTouchEvent(widget, buffer, event)
  }


  private fun clickCoordinatesHitPostComment(x: Int, lineLeft: Float, lineRight: Float): Boolean {
    return x >= lineLeft && x < lineRight
  }

  interface ClickListener {
    fun onWebViewLinkClick(type: WebViewLink.Type, link: String)
  }

}