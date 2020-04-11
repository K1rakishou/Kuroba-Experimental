package com.github.adamantcheese.model.data

import com.github.adamantcheese.model.data.descriptor.ThreadDescriptor
import org.joda.time.DateTime

data class SeenPost(
        val threadDescriptor: ThreadDescriptor,
        val postNo: Long,
        val insertedAt: DateTime
)