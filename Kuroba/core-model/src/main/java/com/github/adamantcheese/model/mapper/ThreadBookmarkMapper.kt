package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.bookmark.ThreadBookmark
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.entity.bookmark.ThreadBookmarkEntity
import com.github.adamantcheese.model.entity.bookmark.ThreadBookmarkFull

object ThreadBookmarkMapper {

  fun toThreadBookmarkEntity(threadBookmark: ThreadBookmark, ownerThreadId: Long, order: Int): ThreadBookmarkEntity {
    require(ownerThreadId != 0L) { "Database id cannot be 0" }

    return ThreadBookmarkEntity(
      ownerThreadId = ownerThreadId,
      watchLastCount = threadBookmark.watchLastCount,
      watchNewCount = threadBookmark.watchNewCount,
      quoteLastCount = threadBookmark.quoteLastCount,
      quoteNewCount = threadBookmark.quoteNewCount,
      title = threadBookmark.title,
      thumbnailUrl = threadBookmark.thumbnailUrl,
      state = threadBookmark.state,
      bookmarkOrder = order
    )
  }

  fun toThreadBookmark(threadBookmarkFull: ThreadBookmarkFull): ThreadBookmark {
    return ThreadBookmark.create(
      threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
        siteName = threadBookmarkFull.siteName,
        boardCode = threadBookmarkFull.boardCode,
        threadNo = threadBookmarkFull.threadNo
      )
    ).apply {
      this.watchLastCount = threadBookmarkFull.threadBookmarkEntity.watchLastCount
      this.watchNewCount = threadBookmarkFull.threadBookmarkEntity.watchNewCount
      this.quoteLastCount = threadBookmarkFull.threadBookmarkEntity.quoteLastCount
      this.quoteNewCount = threadBookmarkFull.threadBookmarkEntity.quoteNewCount
      this.title = threadBookmarkFull.threadBookmarkEntity.title
      this.thumbnailUrl = threadBookmarkFull.threadBookmarkEntity.thumbnailUrl
      this.state = threadBookmarkFull.threadBookmarkEntity.state
    }
  }

}