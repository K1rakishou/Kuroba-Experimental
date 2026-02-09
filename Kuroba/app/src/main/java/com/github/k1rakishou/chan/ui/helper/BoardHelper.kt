package com.github.k1rakishou.chan.ui.helper

import com.github.k1rakishou.model.data.board.ChanBoard
import org.jsoup.parser.Parser

object BoardHelper {
  fun formatName(boardCode: String, boardName: String?): String {
    if (boardName.isNullOrBlank()) {
      return "/$boardCode/"
    }

    return "/$boardCode/ \u2013 $boardName"
  }

  fun formatDescription(board: ChanBoard): String {
    return Parser.unescapeEntities(board.description, false)
  }
}
