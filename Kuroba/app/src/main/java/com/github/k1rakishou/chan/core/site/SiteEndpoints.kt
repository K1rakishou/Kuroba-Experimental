package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import okhttp3.HttpUrl

@Suppress("MaxLineLength")
interface SiteEndpoints {
  fun catalogPage(boardDescriptor: BoardDescriptor, page: Int?): HttpUrl? = null
  fun catalog(boardDescriptor: BoardDescriptor, contentType: ContentType): HttpUrl? = null
  fun thread(threadDescriptor: ThreadDescriptor, contentType: ContentType, archive: Boolean, partialLoad: Boolean): HttpUrl? = null
  fun imageUrl(boardDescriptor: BoardDescriptor, arg: Map<String, String>): HttpUrl? = null
  fun thumbnailUrl(boardDescriptor: BoardDescriptor, spoiler: Boolean, customSpoilers: Int, arg: Map<String, String>): HttpUrl?
  fun icon(icon: String, arg: Map<String, String>): HttpUrl? = null
  fun boards(): HttpUrl? = null
  fun pages(board: ChanBoard): HttpUrl? = null
  fun reply(chanDescriptor: ChanDescriptor): HttpUrl? = null
  fun delete(post: ChanPost): HttpUrl? = null
  fun report(post: ChanPost): HttpUrl? = null
  fun login(): HttpUrl? = null
  fun passCodeInfo(): HttpUrl? = null
  fun search(): HttpUrl? = null
  fun boardArchive(boardDescriptor: BoardDescriptor, page: Int?): HttpUrl? = null

  enum class ContentType {
    Json,
    Html
  }

  companion object {
    fun makeArgument(vararg toMap: String): Map<String, String> {
      if (toMap.isEmpty()) {
        return emptyMap()
      }

      val map = mutableMapWithCap<String, String>(initialCapacity = toMap.size)

      toMap
        .toList()
        .asSequence()
        .chunked(2)
        .forEach { (key, value) -> map[key] = value }

      return map
    }
  }
}
