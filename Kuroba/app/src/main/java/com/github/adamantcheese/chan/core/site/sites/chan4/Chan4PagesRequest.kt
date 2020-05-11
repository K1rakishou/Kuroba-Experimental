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
package com.github.adamantcheese.chan.core.site.sites.chan4

import android.util.JsonReader
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*

class Chan4PagesRequest(
  private val boardDescriptor: BoardDescriptor,
  request: Request,
  okHttpClient: OkHttpClient
) : JsonReaderRequest<Chan4PagesRequest.BoardPages>(
  RequestType.Chan4PagesRequest, request, okHttpClient
) {
  
  override suspend fun readJson(reader: JsonReader): BoardPages {
    val pages: MutableList<BoardPage> = ArrayList()
    reader.withArray {
      while (hasNext()) {
        pages.add(readPageEntry(this))
      }
    }
    
    return BoardPages(boardDescriptor, pages)
  }
  
  private fun readPageEntry(reader: JsonReader): BoardPage {
    var pageNo = -1
    var threadNoTimeModPairs: List<ThreadNoTimeModPair>? = null
    
    reader.withObject {
      while (hasNext()) {
        when (nextName()) {
          "page" -> pageNo = nextInt()
          "threads" -> threadNoTimeModPairs = readThreadTimes(this)
          else -> skipValue()
        }
      }
    }
    
    return BoardPage(pageNo, threadNoTimeModPairs ?: emptyList())
  }
  
  private fun readThreadTimes(reader: JsonReader): List<ThreadNoTimeModPair> {
    val threadNoTimeModPairs: MutableList<ThreadNoTimeModPair> = ArrayList()
    
    reader.withArray {
      while (hasNext()) {
        threadNoTimeModPairs.add(readThreadTime(this))
      }
    }
    
    return threadNoTimeModPairs
  }
  
  private fun readThreadTime(reader: JsonReader): ThreadNoTimeModPair {
    var no = -1L
    var modified: Long = -1
    
    reader.withObject {
      while (hasNext()) {
        when (nextName()) {
          "no" -> no = nextInt().toLong()
          "last_modified" -> modified = nextLong()
          else -> skipValue()
        }
      }
    }
    
    return ThreadNoTimeModPair(no, modified)
  }
  
  class BoardPages(val boardDescriptor: BoardDescriptor, val boardPages: List<BoardPage>)
  inner class BoardPage(val page: Int, val threads: List<ThreadNoTimeModPair>)
  inner class ThreadNoTimeModPair(val no: Long, val modified: Long)
  
  companion object {
    private const val TAG = "Chan4PagesRequest"
  }
}