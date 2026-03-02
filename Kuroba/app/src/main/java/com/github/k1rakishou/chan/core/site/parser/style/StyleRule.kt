package com.github.k1rakishou.chan.core.site.parser.style

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.github.k1rakishou.chan.core.site.parser.CommentParserHelper.detectLinks
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.core_parser.comment.HtmlTag
import com.github.k1rakishou.core_spannable.AbsoluteSizeSpanHashed
import com.github.k1rakishou.core_spannable.BackgroundColorIdSpan
import com.github.k1rakishou.core_spannable.CustomTypefaceSpan
import com.github.k1rakishou.core_spannable.ForegroundColorIdSpan
import com.github.k1rakishou.core_spannable.OverlineSpan
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_spannable.ScriptSpan
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.google.common.collect.Sets

class StyleRule {
  private val blockElements: MutableSet<String?> = Sets.newHashSet<String?>("p", "div")

  private var tag: String? = null
  private val expectedClasses = HashSet<String>()
  private val notExpectedClasses = HashSet<String>()
  private val actions = ArrayList<Action>()
  private var foregroundChanThemeColorId: ChanThemeColorId? = null
  private var backgroundChanThemeColorId: ChanThemeColorId? = null
  private var strikeThrough = false
  private var underline = false
  private var overline = false
  private var superscript = false
  private var subscript = false
  private var bold = false
  private var italic = false
  private var monospace = false
  private var typeface: Typeface? = null
  private var size = 0
  private var link: PostLinkable.Type? = null
  private var nullify = false
  private var linkify = false
  private var justText: String? = null
  private var blockElement = false
  private var newLine = false
  private var priority: Priority? = Priority.Normal

  fun rulePriority(): Priority? {
    return priority
  }

  fun tag(tag: String): StyleRule {
    this.tag = tag

    if (blockElements.contains(tag)) {
      blockElement = true
    }

    return this
  }

  fun tag(): String {
    return checkNotNull(tag) { "tag must be initialized!" }
  }

  fun withPriority(newPriority: Priority): StyleRule {
    this.priority = newPriority
    return this
  }

  fun withCssClass(cssClass: String): StyleRule {
    expectedClasses.add(cssClass)
    return this
  }

  fun withoutAnyOfCssClass(vararg cssClasses: String): StyleRule {
    notExpectedClasses.addAll(cssClasses)
    return this
  }

  fun action(action: Action): StyleRule {
    actions.add(action)
    return this
  }

  fun foregroundColorId(foregroundChanThemeColorId: ChanThemeColorId): StyleRule {
    this.foregroundChanThemeColorId = foregroundChanThemeColorId
    return this
  }

  fun backgroundColorId(backgroundChanThemeColorId: ChanThemeColorId): StyleRule {
    this.backgroundChanThemeColorId = backgroundChanThemeColorId
    return this
  }

  fun link(link: PostLinkable.Type): StyleRule {
    this.link = link
    return this
  }

  fun strikeThrough(): StyleRule {
    strikeThrough = true
    return this
  }

  fun underline(): StyleRule {
    this.underline = true
    return this
  }

  fun overline(): StyleRule {
    this.overline = true
    return this
  }

  fun superscript(): StyleRule {
    this.superscript = true
    return this
  }

  fun subscript(): StyleRule {
    this.subscript = true
    return this
  }

  fun bold(): StyleRule {
    bold = true
    return this
  }

  fun italic(): StyleRule {
    italic = true
    return this
  }

  fun monospace(): StyleRule {
    monospace = true
    return this
  }

  fun typeface(typeface: Typeface): StyleRule {
    this.typeface = typeface
    return this
  }

  fun size(size: Int): StyleRule {
    this.size = size
    return this
  }

  fun nullify(): StyleRule {
    nullify = true
    return this
  }

  fun linkify(): StyleRule {
    linkify = true
    return this
  }

  fun newLine(): StyleRule {
    newLine = true
    return this
  }

  fun just(justText: String): StyleRule {
    this.justText = justText
    return this
  }

  fun highPriority(): Boolean {
    return !expectedClasses.isEmpty()
  }

  @JvmOverloads
  fun applies(htmlTag: HtmlTag, isWildcard: Boolean = false): Boolean {
    if (!notExpectedClasses.isEmpty()) {
      for (clazz in notExpectedClasses) {
        if (isWildcard) {
          if (htmlTag.hasAttr(clazz)) {
            return false
          }
        } else {
          if (htmlTag.hasClass(clazz)) {
            return false
          }
        }
      }
    }

    if (expectedClasses.isEmpty()) {
      return true
    }

    for (clazz in expectedClasses) {
      if (isWildcard) {
        if (htmlTag.hasAttr(clazz)) {
          return true
        }
      } else {
        if (htmlTag.hasClass(clazz)) {
          return true
        }
      }
    }

    return false
  }

  fun apply(styleRulesParams: StyleRulesParams): CharSequence? {
    if (nullify) {
      return null
    }

    if (justText != null) {
      return justText
    }

    var resultText: CharSequence? = styleRulesParams.text
    val htmlTag = styleRulesParams.htmlTag

    val post = styleRulesParams.post
    val callback = styleRulesParams.callback

    if (callback != null && post != null) {
      for (action in actions) {
        resultText = action.execute(callback, post, resultText, htmlTag)
      }
    }

    val spansToApply: MutableList<Any?> = ArrayList<Any?>(2)

    if (backgroundChanThemeColorId != null) {
      spansToApply.add(BackgroundColorIdSpan(backgroundChanThemeColorId!!))
    }

    if (foregroundChanThemeColorId != null) {
      spansToApply.add(ForegroundColorIdSpan(foregroundChanThemeColorId!!))
    }

    if (strikeThrough) {
      spansToApply.add(StrikethroughSpan())
    }

    if (underline) {
      spansToApply.add(UnderlineSpan())
    }

    if (overline) {
      spansToApply.add(OverlineSpan())
    }

    if (superscript) {
      spansToApply.add(ScriptSpan(true))
    }

    if (subscript) {
      spansToApply.add(ScriptSpan(false))
    }

    if (bold && italic) {
      spansToApply.add(StyleSpan(Typeface.BOLD_ITALIC))
    } else if (bold) {
      spansToApply.add(StyleSpan(Typeface.BOLD))
    } else if (italic) {
      spansToApply.add(StyleSpan(Typeface.ITALIC))
    }

    if (monospace) {
      spansToApply.add(TypefaceSpan("monospace"))
    }

    if (typeface != null) {
      spansToApply.add(CustomTypefaceSpan("", typeface))
    }

    if (size != 0) {
      spansToApply.add(AbsoluteSizeSpanHashed(size))
    }

    if (resultText != null && link != null && post != null) {
      val pl = PostLinkable(
        key = resultText,
        linkableValue = PostLinkable.Value.StringValue(resultText),
        type = link!!
      )

      post.addLinkable(pl)
      spansToApply.add(pl)
    }

    if (resultText != null && !spansToApply.isEmpty()) {
      resultText = applySpan(resultText, spansToApply)
    }

    // Apply break if not the last element.
    if (blockElement && htmlTag.hasNextSibling()) {
      resultText = TextUtils.concat(resultText, "\n")
    }

    if (resultText != null && linkify && post != null) {
      resultText = detectLinks(
        post = post,
        text = resultText,
        forceHttpsScheme = styleRulesParams.isForceHttpsScheme,
        linkHandler = null
      )
    }

    if (resultText != null && newLine && !resultText.endsWith("\n", false)) {
      resultText = TextUtils.concat(resultText, "\n")
    }

    return resultText
  }

  private fun applySpan(text: CharSequence, spans: MutableList<Any?>): SpannableString {
    val result = SpannableString(text)

    for (span in spans) {
      if (span != null) {
        // priority is 0 by default which is maximum above all else; higher priority is
        // like higher layers, i.e. 2 is above 1, 3 is above 2, etc.
        // we use 1000 here for to go above everything else
        result.setSpan(
          span,
          0,
          result.length,
          (1000 shl Spanned.SPAN_PRIORITY_SHIFT) and Spanned.SPAN_PRIORITY
        )
      }
    }
    return result
  }

  fun areTheSame(other: StyleRule?): Boolean {
    if (other == null) {
      return false
    }

    if ((tag == null) != (other.tag == null)) {
      return false
    }

    if (tag != null && tag != other.tag) {
      return false
    }

    if (expectedClasses.size != other.expectedClasses.size) {
      return false
    }

    for (expectedClass in expectedClasses) {
      if (!other.expectedClasses.contains(expectedClass)) {
        return false
      }
    }

    if (notExpectedClasses.size != other.notExpectedClasses.size) {
      return false
    }

    for (notExpectedClass in notExpectedClasses) {
      if (!other.notExpectedClasses.contains(notExpectedClass)) {
        return false
      }
    }

    return true
  }

  override fun toString(): String {
    return "StyleRule{" +
      "tag='" + tag + '\'' +
      ", expectedClasses=" + expectedClasses +
      '}'
  }

  enum class Priority {
    BeforeWildcardRules,
    Normal
  }

  fun interface Action {
    fun execute(callback: PostParser.Callback,
                post: ChanPostBuilder,
                text: CharSequence?,
                htmlTag: HtmlTag): CharSequence?
  }

  companion object {
    @JvmStatic
    fun tagRule(tag: String): StyleRule {
      return StyleRule()
        .tag(tag)
    }

    fun tagRuleWithAttr(tag: String, attr: String): StyleRule {
      return StyleRule()
        .tag(tag)
        .withCssClass(attr)
    }
  }
}
