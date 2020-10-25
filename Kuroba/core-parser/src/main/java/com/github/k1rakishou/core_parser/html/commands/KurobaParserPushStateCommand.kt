package com.github.k1rakishou.core_parser.html.commands

import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector

class KurobaParserPushStateCommand<T : KurobaHtmlParserCollector> : KurobaParserCommand<T> {
  override fun toString(): String = "PushStateCommand"
}