package com.github.k1rakishou.chan.core.site.sites.dvach

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.common.jsonArray
import com.github.k1rakishou.common.jsonObject
import com.github.k1rakishou.common.linkedMapWithCap
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.nextStringOrNull
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.board.pages.BoardPage
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.board.pages.ThreadNoTimeModPair
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.google.gson.stream.JsonReader
import dagger.Lazy
import okhttp3.Request

class DvachPagesRequest(
  private val chanBoard: ChanBoard,
  request: Request,
  proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>
) : JsonReaderRequest<BoardPages>(
  request,
  proxiedOkHttpClient
) {

  override suspend fun readJson(reader: JsonReader): BoardPages {
    val threadNoTimeModPairs = mutableListWithCap<ThreadNoTimeModPair>(
      chanBoard.pages * chanBoard.perPage
    )

    reader.jsonObject {
      while (hasNext()) {
        when (nextName()) {
          "Board" -> {
            val actualBoardCode = nextStringOrNull()

            check(actualBoardCode == chanBoard.boardCode()) {
              "Unexpected board code: expected \"${chanBoard.boardCode()}\", actual \"$actualBoardCode\""
            }
          }
          "threads" -> readThreads(reader, threadNoTimeModPairs)
          else -> skipValue()
        }
      }
    }

    val boardPages = mutableListWithCap<BoardPage>(chanBoard.pages)

    threadNoTimeModPairs
      .chunked(threadNoTimeModPairs.size / chanBoard.pages)
      .forEachIndexed { pageIndex, threadsOnPage ->
        val threadsMap = linkedMapWithCap<ChanDescriptor.ThreadDescriptor, Long>(threadsOnPage.size)

        for (threadNoTimeModPair in threadsOnPage) {
          threadsMap[threadNoTimeModPair.threadDescriptor] = threadNoTimeModPair.modified
        }

        boardPages += BoardPage(
          currentPage = pageIndex + 1,
          totalPages = chanBoard.pages,
          threads = threadsMap
        )
      }

    return BoardPages(
      chanBoard.boardDescriptor,
      boardPages
    )
  }

  private fun readThreads(
    reader: JsonReader,
    threadNoTimeModPairs: MutableList<ThreadNoTimeModPair>
  ) {
    reader.jsonArray {
      while (hasNext()) {
        threadNoTimeModPairs.add(readThreadTime(this))
      }
    }
  }

  private fun readThreadTime(reader: JsonReader): ThreadNoTimeModPair {
    var no = -1L
    var modified: Long = -1

    reader.jsonObject {
      while (hasNext()) {
        when (nextName()) {
          "num" -> no = nextInt().toLong()
          "lasthit" -> modified = nextLong()
          else -> skipValue()
        }
      }
    }

    return ThreadNoTimeModPair(
      threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
        boardDescriptor = chanBoard.boardDescriptor,
        threadNo = no
      ),
      modified = modified
    )
  }

}