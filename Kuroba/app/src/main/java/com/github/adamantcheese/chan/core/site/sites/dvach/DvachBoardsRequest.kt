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
package com.github.adamantcheese.chan.core.site.sites.dvach

import com.github.adamantcheese.chan.core.di.NetModule
import com.github.adamantcheese.chan.core.manager.BoardManager
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.common.jsonArray
import com.github.adamantcheese.common.jsonObject
import com.github.adamantcheese.model.data.board.BoardBuilder
import com.github.adamantcheese.model.data.board.ChanBoard
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.google.gson.stream.JsonReader
import okhttp3.Request
import java.util.*

class DvachBoardsRequest internal constructor(
  private val siteDescriptor: SiteDescriptor,
  private val boardManager: BoardManager,
  request: Request,
  okHttpClient: NetModule.ProxiedOkHttpClient
) : JsonReaderRequest<List<ChanBoard>>(RequestType.DvachBoardsRequest, request, okHttpClient) {
  
  override suspend fun readJson(reader: JsonReader): List<ChanBoard> {
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

  private fun readBoardEntry(reader: JsonReader): ChanBoard? {
    return reader.jsonObject {
      val board = BoardBuilder(siteDescriptor)

      while (hasNext()) {
        when (nextName()) {
          "name" -> board.name = nextString()
          "id" -> board.code = nextString()
          "bump_limit" -> board.bumpLimit = nextInt()
          "info" -> board.description = nextString()
          "category" -> board.workSafe = "Взрослым" != nextString()
          else -> skipValue()
        }
      }
  
      board.maxFileSize = MAX_FILE_SIZE
  
      if (board.hasMissingInfo()) {
        // Invalid data, ignore
        return@jsonObject null
      }
      
      return@jsonObject board.toChanBoard(boardManager.byBoardDescriptor(board.boardDescriptor()))
    }
  }

  companion object {
    private const val TAG = "DvachBoardsRequest"
    private const val MAX_FILE_SIZE = 20480 * 1024 //20MB
  }
}