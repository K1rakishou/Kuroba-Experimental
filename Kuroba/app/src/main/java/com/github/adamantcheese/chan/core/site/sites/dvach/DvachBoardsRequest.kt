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

import android.util.JsonReader
import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.chan.core.site.Site
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*

class DvachBoardsRequest internal constructor(
  private val site: Site,
  request: Request,
  okHttpClient: OkHttpClient
) : JsonReaderRequest<List<Board>>(RequestType.DvachBoardsRequest, request, okHttpClient) {
  
  override suspend fun readJson(reader: JsonReader): List<Board> {
    val list: MutableList<Board> = ArrayList()
    
    reader.withObject {
      while (hasNext()) {
        val key = nextName()
        if (key == "boards") {
          withArray {
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

  private fun readBoardEntry(reader: JsonReader): Board? {
    return reader.withObject {
      val board = Board()
      board.siteId = site.id()
      board.site = site
  
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
        return@withObject null
      }
      
      return@withObject board
    }
  }

  companion object {
    private const val TAG = "DvachBoardsRequest"
    private const val MAX_FILE_SIZE = 20480 * 1024 //20MB
  }
}