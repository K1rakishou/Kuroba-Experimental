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
package com.github.k1rakishou.chan.core.site.sites.chan4

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

class Chan4BoardsRequest(
  private val siteDescriptor: SiteDescriptor,
  private val boardManager: BoardManager,
  request: Request,
  proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>
) : AbstractRequest<SiteBoards>(request, proxiedOkHttpClient) {

  override suspend fun processBody(responseBody: ResponseBody): SiteBoards {
    return responseBody.useJsonReader { jsonReader -> readJson(jsonReader) }
  }

  private suspend fun readJson(reader: JsonReader): SiteBoards {
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

      while (hasNext()) {
        when (nextName()) {
          "title" -> board.name = nextString()
          "board" -> board.code = nextString()
          "ws_board" -> board.workSafe = nextInt() == 1
          "per_page" -> board.perPage = nextInt()
          "pages" -> board.pages = nextInt()
          "max_filesize" -> board.maxFileSize = nextInt()
          "max_webm_filesize" -> board.maxWebmSize = nextInt()
          "max_comment_chars" -> board.maxCommentChars = nextInt()
          "bump_limit" -> board.bumpLimit = nextInt()
          "image_limit" -> board.imageLimit = nextInt()
          "cooldowns" -> readCooldowns(this, board)
          "spoilers" -> board.spoilers = nextInt() == 1
          "custom_spoilers" -> board.customSpoilers = nextInt()
          "user_ids" -> board.userIds = nextInt() == 1
          "code_tags" -> board.codeTags = nextInt() == 1
          "country_flags" -> board.countryFlags = nextInt() == 1
          "board_flags" -> {
            board.countryFlags = true
            skipValue()
          }
          "math_tags" -> board.mathTags = nextInt() == 1
          "meta_description" -> board.description = nextString()
          "is_archived" -> board.archive = nextInt() == 1
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
  
  private fun readCooldowns(reader: JsonReader, boardBuilder: BoardBuilder) {
    reader.jsonObject {
      while (hasNext()) {
        when (nextName()) {
          "threads" -> boardBuilder.cooldownThreads = nextInt()
          "replies" -> boardBuilder.cooldownReplies = nextInt()
          "images" -> boardBuilder.cooldownImages = nextInt()
          else -> skipValue()
        }
      }
    }
  }
  
  companion object {
    private const val TAG = "Chan4BoardsRequest"
  }
}