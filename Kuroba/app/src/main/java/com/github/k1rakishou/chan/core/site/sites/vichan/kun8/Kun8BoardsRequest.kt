package com.github.k1rakishou.chan.core.site.sites.vichan.kun8

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.net.AbstractRequest
import com.github.k1rakishou.common.jsonArray
import com.github.k1rakishou.common.jsonObject
import com.github.k1rakishou.common.useJsonReader
import com.github.k1rakishou.model.data.board.BoardBuilder
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import com.google.gson.stream.JsonReader
import okhttp3.Request
import okhttp3.ResponseBody

class Kun8BoardsRequest(
  private val siteDescriptor: SiteDescriptor,
  private val boardManager: BoardManager,
  request: Request,
  proxiedOkHttpClient: ProxiedOkHttpClient
) : AbstractRequest<SiteBoards>(request, proxiedOkHttpClient) {

  override suspend fun processBody(responseBody: ResponseBody): SiteBoards {
    return responseBody.useJsonReader { jsonReader -> readJson(jsonReader) }
  }

  private suspend fun readJson(reader: JsonReader): SiteBoards {
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

    return SiteBoards.Result.Success(siteDescriptor, list)
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