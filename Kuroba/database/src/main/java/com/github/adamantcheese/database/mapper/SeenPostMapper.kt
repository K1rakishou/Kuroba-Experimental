package com.github.adamantcheese.database.mapper

import com.github.adamantcheese.database.dto.SeenPost
import com.github.adamantcheese.database.entity.SeenPostEntity

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