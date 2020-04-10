package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.SeenPost
import com.github.adamantcheese.model.data.descriptor.ThreadDescriptor
import com.github.adamantcheese.model.entity.SeenPostEntity

object SeenPostMapper {

    fun toEntity(ownerThreadId: Long, seenPost: SeenPost): SeenPostEntity {
        return SeenPostEntity(
                postId = seenPost.postId,
                ownerThreadId = ownerThreadId,
                insertedAt = seenPost.insertedAt
        )
    }

    fun fromEntity(threadDescriptor: ThreadDescriptor, seenPostEntity: SeenPostEntity?): SeenPost? {
        if (seenPostEntity == null) {
            return null
        }

        return SeenPost(
                threadDescriptor,
                seenPostEntity.postId,
                seenPostEntity.insertedAt
        )
    }

}