package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.SeenPost
import com.github.adamantcheese.model.entity.SeenPostEntity

object SeenPostMapper {

    fun toEntity(seenPost: SeenPost): SeenPostEntity {
        return SeenPostEntity(
                seenPost.postUid,
                seenPost.parentLoadableUid,
                seenPost.postId,
                seenPost.insertedAt
        )
    }

    fun fromEntity(seenPostEntity: SeenPostEntity?): SeenPost? {
        if (seenPostEntity == null) {
            return null
        }

        return SeenPost(
                seenPostEntity.postUid,
                seenPostEntity.parentLoadableUid,
                seenPostEntity.postId,
                seenPostEntity.insertedAt
        )
    }

}