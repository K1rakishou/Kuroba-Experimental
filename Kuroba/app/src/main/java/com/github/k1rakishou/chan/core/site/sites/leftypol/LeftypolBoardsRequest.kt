/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.site.sites.leftypol

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
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
import dagger.Lazy
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.IOException

class LeftypolBoardsRequest(
  private val siteDescriptor: SiteDescriptor,
  private val boardManager: BoardManager,
  request: Request,
  proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>
) : AbstractRequest<SiteBoards>(request, proxiedOkHttpClient) {

  override suspend fun processBody(responseBody: ResponseBody): SiteBoards {
    return responseBody.useJsonReader { jsonReader -> readJson(jsonReader) }
  }

  private fun readJson(reader: JsonReader): SiteBoards {
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
    
    return SiteBoards(siteDescriptor, list)
  }
  
  @Throws(IOException::class)
  private fun readBoardEntry(reader: JsonReader): ChanBoard? {
    return reader.jsonObject {
      val board = BoardBuilder(siteDescriptor)
      board.codeTags = true
      board.spoilers = true
      board.pages = 36
      board.perPage = 10
      //board.countryFlags = true
      while (hasNext()) {
        when (nextName()) {
          "name" -> board.name = nextString()
          "code" -> board.code = nextString()
          "sfw" -> board.workSafe = nextBoolean()
          else -> skipValue()
        }
      }

      if (board.name == "overboard" || board.name == "sfw" || board.name == "alt") {
        board.pages = 1
        board.perPage = 30
      }
  
      if (board.hasMissingInfo()) {
        return@jsonObject null
      }

      return@jsonObject board.toChanBoard(boardManager.byBoardDescriptor(board.boardDescriptor()))
    }
    
  }
  
  companion object {
    private const val TAG = "LeftypolBoardsRequest"
  }
}