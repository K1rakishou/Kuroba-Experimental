package com.github.k1rakishou.model.data.filter

import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import org.jsoup.parser.Parser

class FilterWatchCatalogInfoObject(
  val boardDescriptor: BoardDescriptor,
  val catalogThreads: List<FilterWatchCatalogThreadInfoObject>
)

class FilterWatchCatalogThreadInfoObject(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val closed: Boolean,
  val archived: Boolean,
  commentRaw: String,
  subjectRaw: String,
  val thumbnailUrl: HttpUrl?
) {
  private var comment: String = ""
  val subject: String

  init {
    comment = commentRaw
    subject = Parser.unescapeEntities(subjectRaw, false)
  }

  @Synchronized
  fun replaceRawCommentWithParsed(parsedComment: String) {
    this.comment = parsedComment
  }

  @Synchronized
  fun comment(): String {
    return this.comment
  }

}