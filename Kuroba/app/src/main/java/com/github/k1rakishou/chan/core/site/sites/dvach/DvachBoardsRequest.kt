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
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class DvachBoardsRequest internal constructor(
  private val siteDescriptor: SiteDescriptor,
  private val boardManager: BoardManager,
  private val proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>,
  private val regularGetBoardsRequestUrl: HttpUrl,
  private val additionalGetBoardsRequestUrl: HttpUrl
) {

  suspend fun execute(): ModularResult<SiteBoards> {
     return Try {
      val dvachBoardAdditionalInfoMap = getBoardsAdditionalInfo(additionalGetBoardsRequestUrl)
      if (dvachBoardAdditionalInfoMap.isEmpty()) {
        throw DvachBoardsRequestException.ParsingError(
          IOException("Failed to parse boards additional info, empty")
        )
      }

      return@Try getSiteBoards(dvachBoardAdditionalInfoMap)
    }
  }

  private suspend fun getSiteBoards(
    dvachBoardAdditionalInfoMap: Map<String, DvachBoardAdditionalInfo>
  ): SiteBoards {
    return genericDvachBoardRequest(
      url = regularGetBoardsRequestUrl,
      readJsonFunc = { jsonReader -> readSiteBoards(jsonReader, dvachBoardAdditionalInfoMap) }
    )
  }

  private suspend fun getBoardsAdditionalInfo(
    additionalGetBoardsRequestUrl: HttpUrl
  ): Map<String, DvachBoardAdditionalInfo> {
    return genericDvachBoardRequest(
      url = additionalGetBoardsRequestUrl,
      readJsonFunc = { jsonReader -> readDvachBoardAdditionalInfoMap(jsonReader) }
    )
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


  private fun readDvachBoardAdditionalInfoMap(
    jsonReader: JsonReader
  ): Map<String, DvachBoardAdditionalInfo> {
    val dvachBoardAdditionalInfoMap = mutableMapOf<String, DvachBoardAdditionalInfo>()

    jsonReader.jsonObject {
      while (hasNext()) {
        nextName()

        jsonArray {
          while (hasNext()) {
            jsonObject {
              val dvachBoardAdditionalInfo = readDvachBoardAdditionalInfoEntry()
              if (dvachBoardAdditionalInfo != null) {
                dvachBoardAdditionalInfoMap[dvachBoardAdditionalInfo.boardCode] = dvachBoardAdditionalInfo
              }
            }
          }
        }
      }
    }

    return dvachBoardAdditionalInfoMap
  }

  private fun JsonReader.readDvachBoardAdditionalInfoEntry(): DvachBoardAdditionalInfo? {
    var boardCode: String? = null
    var pages = -1
    var sageEnabled = false

    while (hasNext()) {
      when (nextName()) {
        "id" -> boardCode = nextStringOrNull()
        "pages" -> pages = nextInt()
        "sage" -> sageEnabled = nextInt() == 1
        else -> skipValue()
      }
    }

    if (boardCode.isNullOrEmpty() || pages < 0) {
      return null
    }

    return DvachBoardAdditionalInfo(boardCode, pages, sageEnabled)
  }

  private fun readSiteBoards(
    reader: JsonReader,
    dvachBoardAdditionalInfoMap: Map<String, DvachBoardAdditionalInfo>
  ): SiteBoards {
    val boardList: MutableList<ChanBoard> = ArrayList()
    
    reader.jsonObject {
      while (hasNext()) {
        val key = nextName()
        if (key == "boards") {
          jsonArray {
            while (hasNext()) {
              val board = readBoardEntry(this, dvachBoardAdditionalInfoMap)
              if (board != null) {
                boardList.add(board)
              }
            }
          }
        } else {
          skipValue()
        }
      }
    }
    
    return SiteBoards(siteDescriptor, boardList)
  }

  private fun readBoardEntry(
    reader: JsonReader,
    dvachBoardAdditionalInfoMap: Map<String, DvachBoardAdditionalInfo>
  ): ChanBoard? {
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

      board.maxFileSize = Dvach.DEFAULT_MAX_FILE_SIZE
  
      if (board.hasMissingInfo()) {
        // Invalid data, ignore
        return@jsonObject null
      }

      dvachBoardAdditionalInfoMap[board.code]?.let { dvachBoardAdditionalInfo ->
        board.pages = dvachBoardAdditionalInfo.pages
        // TODO(KurobaEx): handle SAGE
      }

      return@jsonObject board.toChanBoard(boardManager.byBoardDescriptor(board.boardDescriptor()))
    }
  }

  class DvachBoardAdditionalInfo(
    val boardCode: String,
    val pages: Int,
    val sageEnabled: Boolean
  )

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