package com.github.k1rakishou.chan.core.site.parser.search

import android.text.SpannableString
import android.text.TextUtils
import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule
import com.github.k1rakishou.chan.core.site.parser.style.StyleRulesParamsBuilder
import com.github.k1rakishou.chan.ui.text.span.PostLinkable
import com.github.k1rakishou.chan.ui.theme.ChanTheme
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.model.data.theme.ChanThemeColorId
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@DoNotStrip
open class SimpleCommentParser {
  private val lock = ReentrantReadWriteLock()

  @GuardedBy("lock")
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
    theme: ChanTheme,
    commentRaw: CharSequence
  ): CharSequence? {
    var total: CharSequence? = null

    try {
      val comment = commentRaw.toString()
      val document = Jsoup.parseBodyFragment(comment)
      val nodes = document.body().childNodes()
      val texts = ArrayList<CharSequence>(nodes.size)

      nodes.mapNotNullTo(texts) { parseNode(theme, it) }

      total = TextUtils.concat(*texts.toTypedArray())
    } catch (e: Exception) {
      Logger.e(TAG, "Error parsing comment html", e)
      return null
    }

    return total
  }

  private fun parseNode(
    theme: ChanTheme,
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

      innerNodes.mapNotNullTo(texts) { parseNode(theme, it) }

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
    theme: ChanTheme,
    tag: String,
    text: CharSequence,
    element: Element
  ): CharSequence? {
    val rules = lock.read { rules[tag] }
      ?: return text

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
    lock.write {
      var list = rules[rule.tag()]
      if (list == null) {
        list = ArrayList(3)
        rules[rule.tag()] = list
      }

      list.add(rule)
    }
  }

  companion object {
    private const val TAG = "Chan4SearchPostParser"
  }
}