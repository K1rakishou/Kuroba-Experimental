package com.github.k1rakishou.core_parser.html.commands

import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector

class KurobaCommandPushState<T : KurobaHtmlParserCollector>(
  val groupName: String?
) : KurobaParserCommand<T> {
  override fun toString(): String = "KurobaCommandPushState(groupName=$groupName)"
}