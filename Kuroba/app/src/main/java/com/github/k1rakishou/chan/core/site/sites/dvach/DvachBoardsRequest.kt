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
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class DvachBoardsRequest internal constructor(
  private val dvach: Dvach,
  private val siteDescriptor: SiteDescriptor,
  private val boardManager: BoardManager,
  private val proxiedOkHttpClient: RealProxiedOkHttpClient,
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
      .also { requestBuilder -> dvach.requestModifier().modifyBoardsGetRequest(requestBuilder) }
      .build()

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
    if (!response.isSuccessful) {
      throw DvachBoardsRequestException.ServerErrorException(response.code)
    }

    if (response.body == null) {
      throw DvachBoardsRequestException.UnknownServerError(EmptyBodyResponseException())
    }

    try {
      return response.body.use { body ->
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
          // 2ch.hk's file size is in KBs
          val maxFileSize = nextInt() * 1024
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

    if (board.maxFileSize < 0) {
      board.maxFileSize = Dvach.DEFAULT_MAX_FILE_SIZE
    }
    if (board.maxWebmSize < 0) {
      board.maxWebmSize = Dvach.DEFAULT_MAX_FILE_SIZE
    }

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