package com.github.adamantcheese.model.data

import org.joda.time.DateTime

data class SeenPost(
        val catalogDescriptor: CatalogDescriptor,
        val postId: Long,
        val insertedAt: DateTime
)