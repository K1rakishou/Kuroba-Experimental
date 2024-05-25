package com.github.k1rakishou.chan.core.site.sites.chan420

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.net.AbstractRequest
import com.github.k1rakishou.common.jsonArray
import com.github.k1rakishou.common.jsonObject
import com.github.k1rakishou.common.useJsonReader
import com.github.k1rakishou.model.data.board.BoardBuilder
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.google.gson.stream.JsonReader
import dagger.Lazy
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.IOException
import java.util.*

class Chan420BoardsRequest(
  private val siteDescriptor: SiteDescriptor,
  private val boardManager: BoardManager,
  request: Request,
  proxiedOkHttpClient: RealProxiedOkHttpClient
) : AbstractRequest<List<ChanBoard>>(request, proxiedOkHttpClient) {

  override suspend fun processBody(responseBody: ResponseBody): List<ChanBoard> {
    return responseBody.useJsonReader { jsonReader -> readJson(jsonReader) }
  }

  private fun readJson(reader: JsonReader): List<ChanBoard> {
    val list: MutableList<ChanBoard> = ArrayList()

    reader.jsonObject {
      while (hasNext()) {
        val key = nextName()
        if (key == "boards") {
          jsonArray {
            while (hasNext()) {
              val board = readBoardEntry(this)
              if (board != null) {
                list.add(board)
              }
            }
          }
        } else {
          skipValue()
        }
      }
    }

    return list
  }

  @Throws(IOException::class)
  private fun readBoardEntry(reader: JsonReader): ChanBoard? {
    return reader.jsonObject {
      val board = BoardBuilder(siteDescriptor)

      while (hasNext()) {
        when (nextName()) {
          "board" -> {
            board.code = nextString()

            if (FILE_SIZE_LIMIT_MAP.containsKey(board.code)) {
              board.maxFileSize = FILE_SIZE_LIMIT_MAP[board.code]!!
            } else {
              board.maxFileSize = Chan420.DEFAULT_MAX_FILE_SIZE
            }
          }
          "title" -> board.name = nextString()
          "nws_board" -> board.workSafe = nextInt() == 1
          "display_order" -> board.order = nextInt()
          else -> skipValue()
        }
      }

      if (board.hasMissingInfo()) {
        // Invalid data, ignore
        return@jsonObject null
      }

      return@jsonObject board.toChanBoard(boardManager.byBoardDescriptor(board.boardDescriptor()))
    }

  }

  companion object {
    private const val TAG = "Chan420BoardsRequest"

    private val FILE_SIZE_LIMIT_MAP = HashMap<String, Int>().apply {
      this["f"] = 40960 * 1024
      this["m"] = 40960 * 1024
      this["h"] = 40960 * 1024
      this["wooo"] = 204800 * 1024
    }
  }
}