package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.SeenPost
import com.github.k1rakishou.model.entity.SeenPostEntity

object SeenPostMapper {

  fun toEntity(ownerThreadId: Long, seenPost: SeenPost): SeenPostEntity {
    return SeenPostEntity(
      postNo = seenPost.postNo,
      ownerThreadId = ownerThreadId,
      insertedAt = seenPost.insertedAt
    )
  }

  fun fromEntity(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    seenPostEntity: SeenPostEntity?
  ): SeenPost? {
    if (seenPostEntity == null) {
      return null
    }

    return SeenPost(
      threadDescriptor = threadDescriptor,
      postNo = seenPostEntity.postNo,
      insertedAt = seenPostEntity.insertedAt
    )
  }

}