package com.github.k1rakishou.chan.core.site.parser.search

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.TextUtils
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule
import com.github.k1rakishou.chan.core.site.parser.style.StyleRulesParamsBuilder
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_themes.ChanThemeColorId
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@DoNotStrip
open class SimpleCommentParser {
  private val rules = ConcurrentHashMap<String, MutableList<StyleRule>>()

  init {
    rule(StyleRule.tagRule("p"))
    rule(StyleRule.tagRule("div"))
    rule(StyleRule.tagRule("br").just("\n"))
    rule(StyleRule.tagRule("wbr").nullify())

    rule(StyleRule.tagRule("s").link(PostLinkable.Type.SPOILER))
    rule(StyleRule.tagRule("b").bold())
    rule(StyleRule.tagRule("i").italic())
    rule(StyleRule.tagRule("em").italic())

    rule(StyleRule.tagRule("pre")
      .cssClass("prettyprint")
      .monospace()
      .size(sp(12f))
      .backgroundColorId(ChanThemeColorId.BackColorSecondary)
    )

    rule(StyleRule.tagRule("span")
      .cssClass("spoiler")
      .link(PostLinkable.Type.SPOILER)
    )

    rule(StyleRule.tagRule("span").cssClass("abbr").nullify())
    rule(StyleRule.tagRule("span").foregroundColorId(ChanThemeColorId.PostInlineQuoteColor).linkify())

    rule(StyleRule.tagRule("strong").bold())
    rule(StyleRule.tagRule("strong-red;").bold().foregroundColorId(ChanThemeColorId.AccentColor))
  }

  open fun parseComment(
    commentRaw: CharSequence
  ): Spanned? {
    val total = SpannableStringBuilder()

    try {
      val comment = commentRaw.toString()
      val document = Jsoup.parseBodyFragment(comment)
      val nodes = document.body().childNodes()

      nodes.forEach { node ->
        total.append(parseNode(node))
      }

    } catch (e: Exception) {
      Logger.e(TAG, "Error parsing comment html", e)
      return null
    }

    return total
  }

  private fun parseNode(
    node: Node
  ): Spanned {
    if (node is TextNode) {
       return SpannableString(node.text())
    }

    if (node is Element) {
      var nodeName = node.nodeName()
      val styleAttr = node.attr("style")

      if (styleAttr.isNotEmpty() && nodeName != "span") {
        nodeName = nodeName + '-' + styleAttr.split(":".toRegex()).toTypedArray()[1].trim()
      }

      val innerNodes = node.childNodes()
      val texts: MutableList<CharSequence> = ArrayList(innerNodes.size + 1)

      innerNodes.mapNotNullTo(texts) { parseNode(it) }

      val allInnerText = TextUtils.concat(*texts.toTypedArray())

      val result: CharSequence? = handleTag(
        nodeName,
        allInnerText,
        node
      )

      return SpannedString.valueOf(result ?: allInnerText)
    }

    Logger.e(TAG, "Unknown node instance: " + node.javaClass.name)
    return SpannedString.valueOf("")
  }

  private fun handleTag(
    tag: String,
    text: CharSequence,
    element: Element
  ): CharSequence? {
    val rules = rules[tag]
      ?: return text

    for (i in 0..1) {
      val highPriority = i == 0

      for (rule in rules) {
        if (rule.highPriority() == highPriority && rule.applies(element)) {
          val params = StyleRulesParamsBuilder()
            .withText(text)
            .withElement(element)
            .build()

          return rule.apply(params)
        }
      }
    }

    // Unknown tag, return the text;
    return text
  }

  private fun rule(rule: StyleRule) {
    var list = rules[rule.tag()]
    if (list == null) {
      list = ArrayList(3)
      rules[rule.tag()] = list
    }

    list.add(rule)
  }

  companion object {
    private const val TAG = "Chan4SearchPostParser"
  }
}