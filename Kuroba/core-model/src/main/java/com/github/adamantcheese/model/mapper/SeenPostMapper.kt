package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.CatalogDescriptor
import com.github.adamantcheese.model.data.SeenPost
import com.github.adamantcheese.model.entity.SeenPostEntity

object SeenPostMapper {

    fun toEntity(ownerBoardId: Long, seenPost: SeenPost): SeenPostEntity {
        return SeenPostEntity(
                seenPost.postId,
                ownerBoardId,
                seenPost.insertedAt
        )
    }

    fun fromEntity(catalogDescriptor: CatalogDescriptor, seenPostEntity: SeenPostEntity?): SeenPost? {
        if (seenPostEntity == null) {
            return null
        }

        return SeenPost(
                catalogDescriptor,
                seenPostEntity.postId,
                seenPostEntity.insertedAt
        )
    }

}