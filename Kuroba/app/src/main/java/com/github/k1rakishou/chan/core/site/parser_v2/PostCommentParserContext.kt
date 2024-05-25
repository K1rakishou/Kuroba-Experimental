package com.github.k1rakishou.chan.core.site.parser_v2

import com.github.k1rakishou.core_parser.comment.HtmlNode

class PostCommentParserContext {
  private var _ulTagsCounter = 0
  val ulTagsCounter: Int
    get() = _ulTagsCounter

  private var _divTagsCounter = 0
  val divTagsCounter: Int
    get() = _divTagsCounter

  private var _tableTagsCounter = 0
  val tableTagsCounter: Int
    get() = _tableTagsCounter

  val isInsideUlTag: Boolean
    get() = _ulTagsCounter > 0
  val isInsideDivTag: Boolean
    get() = _divTagsCounter > 0
  val isInsideTableTag: Boolean
    get() = _tableTagsCounter > 0

  fun onTagOpened(tagNode: HtmlNode.Tag) {
    when (val tagName = tagNode.htmlTag.tagName) {
      "ul" -> ++_ulTagsCounter
      "div" -> ++_divTagsCounter
      "table" -> ++_tableTagsCounter
    }
  }

  fun onTagClosed(tagNode: HtmlNode.Tag) {
    when (val tagName = tagNode.htmlTag.tagName) {
      "ul" -> --_ulTagsCounter
      "div" -> --_divTagsCounter
      "table" -> --_tableTagsCounter
    }
  }
}