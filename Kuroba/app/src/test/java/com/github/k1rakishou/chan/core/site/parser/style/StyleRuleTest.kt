package com.github.k1rakishou.chan.core.site.parser.style

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.CharacterStyle
import com.github.k1rakishou.core_parser.comment.HtmlTag
import com.github.k1rakishou.core_spannable.ForegroundColorIdSpan
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_themes.ChanThemeColorId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StyleRuleTest {
  @Test
  fun `nested links are drawn after the inline quote color`() {
    val text = SpannableStringBuilder("plain ")
    val quoteStart = text.length
    val quote = PostLinkable(
      key = ">>123",
      linkableValue = PostLinkable.Value.PostIdValue(123L, 0L),
      type = PostLinkable.Type.QUOTE,
      revealTextSpoilers = false
    )
    text.append(quote.key)
    text.setSpan(quote, quoteStart, text.length, spanFlags(250))

    text.append(" ")
    val autoLinkStart = text.length
    val autoLink = PostLinkable(
      key = "https://example.com",
      linkableValue = PostLinkable.Value.StringValue("https://example.com"),
      type = PostLinkable.Type.LINK,
      revealTextSpoilers = false
    )
    text.append(autoLink.key)
    text.setSpan(autoLink, autoLinkStart, text.length, spanFlags(500))

    val result = checkNotNull(
      StyleRule.tagRule("span")
        .foregroundColorId(ChanThemeColorId.PostInlineQuoteColor)
        .withSpanPriority(255)
        .apply(styleRulesParams(text))
    ) as Spanned

    val inlineQuoteSpan = result
      .getSpans(0, result.length, ForegroundColorIdSpan::class.java)
      .single()
    assertEquals(ChanThemeColorId.PostInlineQuoteColor, inlineQuoteSpan.chanThemeColorId)
    assertEquals(255, spanPriority(result, inlineQuoteSpan))
    assertEquals(250, spanPriority(result, quote))
    assertEquals(244, spanPriority(result, autoLink))
    assertEquals(listOf(inlineQuoteSpan), characterStylesAt(result, 0))

    // CharacterStyle spans are returned in draw order, so the nested color is applied last.
    val quoteStyles = characterStylesAt(result, quoteStart)
    assertSame(inlineQuoteSpan, quoteStyles.first())
    assertSame(quote, quoteStyles.last())

    val autoLinkStyles = characterStylesAt(result, autoLinkStart)
    assertSame(inlineQuoteSpan, autoLinkStyles.first())
    assertSame(autoLink, autoLinkStyles.last())
  }

  @Test
  fun `span priority must fit in Android priority bits`() {
    assertThrows(IllegalArgumentException::class.java) {
      StyleRule.tagRule("span").withSpanPriority(-1)
    }
    assertThrows(IllegalArgumentException::class.java) {
      StyleRule.tagRule("span").withSpanPriority(256)
    }
  }

  private fun characterStylesAt(text: Spanned, offset: Int): List<CharacterStyle> {
    return text.getSpans(offset, offset + 1, CharacterStyle::class.java).toList()
  }

  private fun spanFlags(priority: Int): Int {
    return (priority shl Spanned.SPAN_PRIORITY_SHIFT) and Spanned.SPAN_PRIORITY
  }

  private fun spanPriority(text: Spanned, span: Any): Int {
    return (text.getSpanFlags(span) and Spanned.SPAN_PRIORITY) shr Spanned.SPAN_PRIORITY_SHIFT
  }

  private fun styleRulesParams(text: CharSequence): StyleRulesParams {
    return StyleRulesParams(
      text = text,
      htmlTag = HtmlTag(
        index = 0,
        parentNode = null,
        tagName = "span",
        attributes = emptyList(),
        children = emptyList(),
        isVoidElement = false
      ),
      callback = null,
      post = null,
      forceHttpsScheme = true,
      revealTextSpoilers = false
    )
  }
}
