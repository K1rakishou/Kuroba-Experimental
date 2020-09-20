package com.github.adamantcheese.chan.core.site.parser.search

import android.text.SpannableString
import android.text.TextUtils
import com.github.adamantcheese.chan.core.site.parser.style.StyleRule
import com.github.adamantcheese.chan.core.site.parser.style.StyleRulesParamsBuilder
import com.github.adamantcheese.chan.ui.text.span.PostLinkable
import com.github.adamantcheese.chan.ui.theme.Theme
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.DoNotStrip
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.*

@DoNotStrip
open class Chan4SearchPostParser {
  private val rules: MutableMap<String, MutableList<StyleRule>> = HashMap()

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
      .size(AndroidUtils.sp(12f))
      .backgroundColor(StyleRule.BackgroundColor.CODE)
    )

    rule(StyleRule.tagRule("span")
      .cssClass("spoiler")
      .link(PostLinkable.Type.SPOILER)
    )

    rule(StyleRule.tagRule("span").cssClass("abbr").nullify())
    rule(StyleRule.tagRule("span").foregroundColor(StyleRule.ForegroundColor.INLINE_QUOTE).linkify())

    rule(StyleRule.tagRule("strong").bold())
    rule(StyleRule.tagRule("strong-red;").bold().foregroundColor(StyleRule.ForegroundColor.RED))
  }

  open fun parseComment(
    theme: Theme,
    commentRaw: CharSequence
  ): CharSequence? {
    var total: CharSequence? = SpannableString("")

    try {
      val comment = commentRaw.toString()
      val document = Jsoup.parseBodyFragment(comment)
      val nodes = document.body().childNodes()
      val texts = ArrayList<CharSequence>(nodes.size)

      for (node in nodes) {
        val nodeParsed = parseNode(theme, node)
        if (nodeParsed != null) {
          texts.add(nodeParsed)
        }
      }

      total = TextUtils.concat(*texts.toTypedArray())
    } catch (e: Exception) {
      Logger.e(TAG, "Error parsing comment html", e)
    }

    return total
  }

  private fun parseNode(
    theme: Theme,
    node: Node
  ): CharSequence? {
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

      for (innerNode in innerNodes) {
        val nodeParsed = parseNode(theme, innerNode)
        if (nodeParsed != null) {
          texts.add(nodeParsed)
        }
      }

      val allInnerText = TextUtils.concat(*texts.toTypedArray())

      val result: CharSequence? = handleTag(
        theme,
        nodeName,
        allInnerText,
        node
      )

      return result ?: allInnerText
    }

    Logger.e(TAG, "Unknown node instance: " + node.javaClass.name)
    return ""
  }

  private fun handleTag(
    theme: Theme,
    tag: String,
    text: CharSequence,
    element: Element
  ): CharSequence? {
    val rules = rules[tag]

    if (rules == null) {
      return text
    }

    for (i in 0..1) {
      val highPriority = i == 0

      for (rule in rules) {
        if (rule.highPriority() == highPriority && rule.applies(element)) {
          val params = StyleRulesParamsBuilder()
            .withTheme(theme)
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