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
package com.github.adamantcheese.chan.core.site.sites.chan420

import android.util.JsonReader
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.model.data.board.BoardBuilder
import com.github.adamantcheese.model.data.board.ChanBoard
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.*

class Chan420BoardsRequest(
  private val siteDescriptor: SiteDescriptor,
  request: Request,
  okHttpClient: OkHttpClient
) : JsonReaderRequest<List<ChanBoard>>(RequestType.Chan420BoardsRequest, request, okHttpClient) {

  override suspend fun readJson(reader: JsonReader): List<ChanBoard> {
    val list: MutableList<ChanBoard> = ArrayList()

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

  @Throws(IOException::class)
  private fun readBoardEntry(reader: JsonReader): ChanBoard? {
    return reader.withObject {
      val board = BoardBuilder(siteDescriptor)

      while (hasNext()) {
        when (nextName()) {
          "board" -> {
            board.code = nextString()

            if (FILE_SIZE_LIMIT_MAP.containsKey(board.code)) {
              board.maxFileSize = FILE_SIZE_LIMIT_MAP[board.code]!!
            } else {
              board.maxFileSize = DEFAULT_MAX_FILE_SIZE
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
        return@withObject null
      }

      return@withObject board.toChanBoard()
    }

  }

  companion object {
    private const val TAG = "Chan420BoardsRequest"
    private const val DEFAULT_MAX_FILE_SIZE = 20480 * 1024

    private val FILE_SIZE_LIMIT_MAP = HashMap<String, Int>().apply {
      this["f"] = 40960 * 1024
      this["m"] = 40960 * 1024
      this["h"] = 40960 * 1024
      this["wooo"] = 204800 * 1024
    }
  }
}