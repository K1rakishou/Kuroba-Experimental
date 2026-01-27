package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.common.jsonArray
import com.github.k1rakishou.common.jsonObject
import com.github.k1rakishou.common.linkedMapWithCap
import com.github.k1rakishou.model.data.board.pages.BoardPage
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.board.pages.ThreadNoTimeModPair
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.google.gson.stream.JsonReader
import okhttp3.Request

class Chan4PagesRequest(
  private val boardDescriptor: BoardDescriptor,
  private val boardTotalPagesCount: Int,
  request: Request,
  proxiedOkHttpClient: ProxiedOkHttpClient
) : JsonReaderRequest<BoardPages>(
  request,
  proxiedOkHttpClient
) {
  
  override suspend fun readJson(reader: JsonReader): BoardPages {
    val pages: MutableList<BoardPage> = ArrayList()
    reader.jsonArray {
      while (hasNext()) {
        pages.add(readPageEntry(this))
      }
    }
    
    return BoardPages(boardDescriptor, pages)
  }
  
  private fun readPageEntry(reader: JsonReader): BoardPage {
    var pageIndex = -1
    var threadNoTimeModPairs: List<ThreadNoTimeModPair>? = null
    
    reader.jsonObject {
      while (hasNext()) {
        when (nextName()) {
          "page" -> pageIndex = nextInt()
          "threads" -> threadNoTimeModPairs = readThreadTimes(this)
          else -> skipValue()
        }
      }
    }

    val threads = if (threadNoTimeModPairs.isNullOrEmpty()) {
      linkedMapOf<ChanDescriptor.ThreadDescriptor, Long>()
    } else {
      val threadPairs = threadNoTimeModPairs!!
      val resultMap = linkedMapWithCap<ChanDescriptor.ThreadDescriptor, Long>(threadPairs.size)

      for (threadPair in threadPairs) {
        resultMap[threadPair.threadDescriptor] = threadPair.modified
      }

      resultMap
    }
    
    return BoardPage(
      pageIndex,
      boardTotalPagesCount,
      threads
    )
  }
  
  private fun readThreadTimes(reader: JsonReader): List<ThreadNoTimeModPair> {
    val threadNoTimeModPairs: MutableList<ThreadNoTimeModPair> = ArrayList()
    
    reader.jsonArray {
      while (hasNext()) {
        threadNoTimeModPairs.add(readThreadTime(this))
      }
    }
    
    return threadNoTimeModPairs
  }
  
  private fun readThreadTime(reader: JsonReader): ThreadNoTimeModPair {
    var no = -1L
    var modified: Long = -1
    
    reader.jsonObject {
      while (hasNext()) {
        when (nextName()) {
          "no" -> no = nextInt().toLong()
          "last_modified" -> modified = nextLong()
          else -> skipValue()
        }
      }
    }
    
    return ThreadNoTimeModPair(
      threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
        boardDescriptor = boardDescriptor,
        threadNo = no
      ),
      modified = modified
    )
  }

  companion object {
    private const val TAG = "Chan4PagesRequest"
  }
}