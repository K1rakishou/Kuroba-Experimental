package com.github.adamantcheese.chan.core.site.sites.kun8

import com.github.adamantcheese.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.adamantcheese.chan.core.manager.BoardManager
import com.github.adamantcheese.chan.core.model.SiteBoards
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.common.jsonArray
import com.github.adamantcheese.common.jsonObject
import com.github.adamantcheese.model.data.board.BoardBuilder
import com.github.adamantcheese.model.data.board.ChanBoard
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.google.gson.stream.JsonReader
import okhttp3.Request
import java.util.*

class Kun8BoardsRequest(
  private val siteDescriptor: SiteDescriptor,
  private val boardManager: BoardManager,
  request: Request,
  okHttpClient: ProxiedOkHttpClient
) : JsonReaderRequest<SiteBoards>(JsonRequestType.Kun8BoardsJsonRequest, request, okHttpClient) {

  override suspend fun readJson(reader: JsonReader): SiteBoards {
    val list: MutableList<ChanBoard> = ArrayList()

    reader.jsonArray {
      while (hasNext()) {
        jsonObject {
          val board = readBoardEntry()
          if (board != null) {
            list.add(board)
          }
        }
      }
    }

    return SiteBoards(siteDescriptor, list)
  }

  private fun JsonReader.readBoardEntry(): ChanBoard? {
    val board = BoardBuilder(siteDescriptor)
    board.perPage = 15
    board.pages = 10

    while (hasNext()) {
      when (nextName()) {
        "title" -> board.name = nextString()
        "uri" -> board.code = nextString()
        "sfw" -> board.workSafe = nextInt() == 1
        "subtitle" -> board.description = nextString()
        else -> skipValue()
      }
    }

    if (board.hasMissingInfo()) {
      // Invalid data, ignore
      return null
    }

    return board.toChanBoard(boardManager.byBoardDescriptor(board.boardDescriptor()))
  }

}