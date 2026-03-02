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
      time = threadBookmarkReply.time,
      commentRaw = threadBookmarkReply.commentRaw
    )
  }

  fun fromThreadBookmarkReplyEntity(
    threadBookmarkFull: ThreadBookmarkFull,
    threadBookmarkReplyEntities: List<ThreadBookmarkReplyEntity>
  ): Map<PostDescriptor, ThreadBookmarkReply> {
    val resultMap = mutableMapWithCap<PostDescriptor, ThreadBookmarkReply>(threadBookmarkReplyEntities)

    threadBookmarkReplyEntities.forEach { threadBookmarkReplyEntity ->
      val postDescriptor = PostDescriptor.create(
        siteName = threadBookmarkFull.siteName,
        boardCode = threadBookmarkFull.boardCode,
        threadNo = threadBookmarkFull.threadNo,
        postNo = threadBookmarkReplyEntity.replyPostNo,
        postSubNo = 0L
      )

      val repliesToPostDescriptor = PostDescriptor.create(
        siteName = threadBookmarkFull.siteName,
        boardCode = threadBookmarkFull.boardCode,
        threadNo = threadBookmarkFull.threadNo,
        postNo = threadBookmarkReplyEntity.repliesToPostNo,
        postSubNo = 0L
      )

      resultMap[postDescriptor] = ThreadBookmarkReply(
        postDescriptor = postDescriptor,
        repliesTo = repliesToPostDescriptor,
        alreadyNotified = threadBookmarkReplyEntity.alreadyNotified,
        alreadySeen = threadBookmarkReplyEntity.alreadySeen,
        alreadyRead = threadBookmarkReplyEntity.alreadyRead,
        time = threadBookmarkReplyEntity.time,
        commentRaw = threadBookmarkReplyEntity.commentRaw
      )
    }

    return resultMap
  }

}