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
package com.github.k1rakishou.chan.core.site.sites.dvach

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.jsonArray
import com.github.k1rakishou.common.jsonObject
import com.github.k1rakishou.common.nextStringOrNull
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.model.data.board.BoardBuilder
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import com.google.gson.stream.JsonReader
import dagger.Lazy
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class DvachBoardsRequest internal constructor(
  private val siteDescriptor: SiteDescriptor,
  private val boardManager: BoardManager,
  private val proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>,
  private val boardsRequestUrl: HttpUrl
) {

  suspend fun execute(): ModularResult<SiteBoards> {
     return Try {
      return@Try genericDvachBoardRequest(
        url = boardsRequestUrl,
        readJsonFunc = { jsonReader -> readDvachBoards(jsonReader) }
      )
    }
  }

  private suspend fun <T> genericDvachBoardRequest(
    url: HttpUrl,
    readJsonFunc: (JsonReader) -> T
  ): T {
    val request = Request.Builder()
      .url(url)
      .get()
      .build()

    val response = proxiedOkHttpClient.get().okHttpClient().suspendCall(request)
    if (!response.isSuccessful) {
      throw DvachBoardsRequestException.ServerErrorException(response.code)
    }

    if (response.body == null) {
      throw DvachBoardsRequestException.UnknownServerError(EmptyBodyResponseException())
    }

    try {
      return response.body!!.use { body ->
        return@use body.byteStream().use { inputStream ->
          return@use JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { jsonReader ->
            return@use readJsonFunc(jsonReader)
          }
        }
      }
    } catch (error: Throwable) {
      throw DvachBoardsRequestException.ParsingError(error)
    }
  }


  private fun readDvachBoards(
    jsonReader: JsonReader
  ): SiteBoards {
    val boardList: MutableList<ChanBoard> = ArrayList()

    jsonReader.jsonArray {
      while (hasNext()) {
        jsonObject {
          val board = readDvachBoard()
          if (board != null) {
            boardList += board
          }
        }
      }
    }

    return SiteBoards(siteDescriptor, boardList)
  }

  private fun JsonReader.readDvachBoard(): ChanBoard? {
    val board = BoardBuilder(siteDescriptor)

    while (hasNext()) {
      when (nextName()) {
        "id" -> board.code = nextStringOrNull()
        "max_pages" -> board.pages = nextInt()
        "threads_per_page" -> board.perPage = nextInt()
        "name" -> board.name = nextString()
        "max_files_size" -> {
          val maxFileSize = nextInt()
          board.maxFileSize = maxFileSize
          board.maxWebmSize = maxFileSize
        }
        "max_comment" -> board.maxCommentChars = nextInt()
        "bump_limit" -> board.bumpLimit = nextInt()
        "info_outer" -> board.description = nextString()
        "category" -> board.workSafe = "Взрослым" != nextString()
        "enable_flags" -> board.countryFlags = nextBoolean()
        "icons" -> {
          board.countryFlags = true
          skipValue()
        }
        else -> skipValue()
      }
    }

    board.maxFileSize = Dvach.DEFAULT_MAX_FILE_SIZE

    if (board.hasMissingInfo()) {
      // Invalid data, ignore
      return null
    }

    return board.toChanBoard(boardManager.byBoardDescriptor(board.boardDescriptor()))
  }

  private sealed class DvachBoardsRequestException(
    message: String,
  ) : Throwable(message) {
    class ServerErrorException(val code: Int) : DvachBoardsRequestException(
      message = "Bad response code: ${code}"
    )

    class UnknownServerError(val throwable: Throwable) : DvachBoardsRequestException(
      message = "UnknownServerError, cause=${throwable.errorMessageOrClassName()}"
    )

    class ParsingError(val throwable: Throwable) : DvachBoardsRequestException(
      message = "ParsingError, cause=${throwable.errorMessageOrClassName()}"
    )
  }

  companion object {
    private const val TAG = "DvachBoardsRequest"
  }
}