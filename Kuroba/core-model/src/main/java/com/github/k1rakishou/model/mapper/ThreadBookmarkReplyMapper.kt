package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkReply
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkFull
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkReplyEntity

object ThreadBookmarkReplyMapper {

  fun toThreadBookmarkReplyEntity(
    ownerThreadBookmarkId: Long,
    threadBookmarkReply: ThreadBookmarkReply
  ): ThreadBookmarkReplyEntity {
    return ThreadBookmarkReplyEntity(
      threadBookmarkReplyId = 0L,
      ownerThreadBookmarkId = ownerThreadBookmarkId,
      replyPostNo = threadBookmarkReply.postDescriptor.postNo,
      repliesToPostNo = threadBookmarkReply.repliesTo.postNo,
      alreadyNotified = threadBookmarkReply.alreadyNotified,
      alreadySeen = threadBookmarkReply.alreadySeen,
      alreadyRead = threadBookmarkReply.alreadyRead,
      time = threadBookmarkReply.time
    )
  }

  fun fromThreadBookmarkReplyEntity(
    threadBookmarkFull: ThreadBookmarkFull,
    threadBookmarkReplyEntities: List<ThreadBookmarkReplyEntity>
  ): Map<PostDescriptor, ThreadBookmarkReply> {
    val resultMap = mutableMapWithCap<PostDescriptor, ThreadBookmarkReply>(threadBookmarkReplyEntities)

    threadBookmarkReplyEntities.forEach { threadBookmarkReplyEntity ->
      val postDescriptor = PostDescriptor.create(
        threadBookmarkFull.siteName,
        threadBookmarkFull.boardCode,
        threadBookmarkFull.threadNo,
        threadBookmarkReplyEntity.replyPostNo
      )

      val repliesToPostDescriptor = PostDescriptor.create(
        threadBookmarkFull.siteName,
        threadBookmarkFull.boardCode,
        threadBookmarkFull.threadNo,
        threadBookmarkReplyEntity.repliesToPostNo
      )

      resultMap[postDescriptor] = ThreadBookmarkReply(
        postDescriptor = postDescriptor,
        repliesTo = repliesToPostDescriptor,
        alreadyNotified = threadBookmarkReplyEntity.alreadyNotified,
        alreadySeen = threadBookmarkReplyEntity.alreadySeen,
        alreadyRead = threadBookmarkReplyEntity.alreadyRead,
        time = threadBookmarkReplyEntity.time
      )
    }

    return resultMap
  }

}