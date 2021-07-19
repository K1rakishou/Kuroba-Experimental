package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.SeenPost
import com.github.k1rakishou.model.entity.SeenPostEntity

object SeenPostMapper {

  fun toEntity(ownerThreadId: Long, seenPost: SeenPost): SeenPostEntity {
    return SeenPostEntity(
      ownerThreadId = ownerThreadId,
      postNo = seenPost.postDescriptor.postNo,
      postSubNo = seenPost.postDescriptor.postSubNo,
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

    if (seenPostEntity.postNo <= 0 || threadDescriptor.threadNo <= 0) {
      return null
    }

    return SeenPost(
      postDescriptor = PostDescriptor.create(
        chanDescriptor = threadDescriptor,
        threadNo = threadDescriptor.threadNo,
        postNo = seenPostEntity.postNo,
        postSubNo = seenPostEntity.postSubNo
      ),
      insertedAt = seenPostEntity.insertedAt
    )
  }

}