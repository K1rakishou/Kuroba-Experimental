package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.bookmark.ThreadBookmark
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkFull
import org.joda.time.DateTime

object ThreadBookmarkMapper {

  fun toThreadBookmarkEntity(threadBookmark: ThreadBookmark, ownerThreadId: Long, createdOn: DateTime): ThreadBookmarkEntity {
    require(ownerThreadId != 0L) { "Database id cannot be 0" }

    return ThreadBookmarkEntity(
      ownerThreadId = ownerThreadId,
      seenPostsCount = threadBookmark.seenPostsCount,
      totalPostsCount = threadBookmark.threadRepliesCount,
      lastViewedPostNo = threadBookmark.lastViewedPostNo,
      threadLastPostNo = threadBookmark.threadLastPostNo,
      title = threadBookmark.title,
      thumbnailUrl = threadBookmark.thumbnailUrl,
      state = threadBookmark.state,
      createdOn = createdOn
    )
  }

  fun toThreadBookmark(threadBookmarkFull: ThreadBookmarkFull): ThreadBookmark {
    return ThreadBookmark.create(
      threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
        siteName = threadBookmarkFull.siteName,
        boardCode = threadBookmarkFull.boardCode,
        threadNo = threadBookmarkFull.threadNo,
      ),
      createdOn = threadBookmarkFull.threadBookmarkEntity.createdOn
    ).apply {
      this.seenPostsCount = threadBookmarkFull.threadBookmarkEntity.seenPostsCount
      this.threadRepliesCount = threadBookmarkFull.threadBookmarkEntity.totalPostsCount
      this.lastViewedPostNo = threadBookmarkFull.threadBookmarkEntity.lastViewedPostNo
      this.threadLastPostNo = threadBookmarkFull.threadBookmarkEntity.threadLastPostNo
      this.title = threadBookmarkFull.threadBookmarkEntity.title
      this.thumbnailUrl = threadBookmarkFull.threadBookmarkEntity.thumbnailUrl
      this.state = threadBookmarkFull.threadBookmarkEntity.state

      val replies = ThreadBookmarkReplyMapper.fromThreadBookmarkReplyEntity(
        threadBookmarkFull,
        threadBookmarkFull.threadBookmarkReplyEntities
      )

      this.threadBookmarkReplies.clear()
      this.threadBookmarkReplies.putAll(replies)
    }
  }

}