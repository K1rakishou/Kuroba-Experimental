package com.github.k1rakishou.chan.core.site.sites.kun8

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.common.jsonArray
import com.github.k1rakishou.common.jsonObject
import com.github.k1rakishou.model.data.board.BoardBuilder
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import com.google.gson.stream.JsonReader
import dagger.Lazy
import okhttp3.Request
import java.util.*

class Kun8BoardsRequest(
  private val siteDescriptor: SiteDescriptor,
  private val boardManager: BoardManager,
  request: Request,
  proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>
) : JsonReaderRequest<SiteBoards>(request, proxiedOkHttpClient) {

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