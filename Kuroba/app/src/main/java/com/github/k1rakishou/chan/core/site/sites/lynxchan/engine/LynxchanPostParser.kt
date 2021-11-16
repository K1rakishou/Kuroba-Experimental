package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.core_parser.comment.HtmlNode

class LynxchanPostParser(
  commentParser: LynxchanCommentParser,
  archivesManager: ArchivesManager
) : DefaultPostParser(commentParser, archivesManager) {

  override fun postProcessText(textNode: HtmlNode.Text, text: String): String {
    val parentTag = textNode.parentNode?.asTagOrNull()?.htmlTag
    if (parentTag == null || parentTag.tagName != "a") {
      return super.postProcessText(textNode, text)
    }

    if (!parentTag.hasClass("quoteLink")) {
      return super.postProcessText(textNode, text)
    }

    // For some reason Lynxchan does not include ";" at the end of "&gt" which confuses Jsoup's HTML
    // unescaping method.
    return text.replace("&gt", ">")
  }

}