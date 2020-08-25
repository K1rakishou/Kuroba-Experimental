package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanSavedReply
import com.github.adamantcheese.model.entity.chan.post.ChanSavedReplyEntity

object ChanSavedReplyMapper {

  fun fromEntity(chanSavedReplyEntity: ChanSavedReplyEntity): ChanSavedReply {
    return ChanSavedReply(
      PostDescriptor.create(
        siteName = chanSavedReplyEntity.siteName,
        boardCode = chanSavedReplyEntity.boardCode,
        threadNo = chanSavedReplyEntity.threadNo,
        postNo = chanSavedReplyEntity.postNo,
        postSubNo = chanSavedReplyEntity.postSubNo,
      ),
      password = chanSavedReplyEntity.postPassword
    )
  }

  fun toEntity(chanSavedReply: ChanSavedReply): ChanSavedReplyEntity {
    return ChanSavedReplyEntity(
      siteName = chanSavedReply.postDescriptor.threadDescriptor().siteName(),
      boardCode = chanSavedReply.postDescriptor.boardDescriptor().boardCode,
      threadNo = chanSavedReply.postDescriptor.threadDescriptor().threadNo,
      postNo = chanSavedReply.postDescriptor.postNo,
      postSubNo = chanSavedReply.postDescriptor.postSubNo,
      postPassword = chanSavedReply.password
    )
  }

}