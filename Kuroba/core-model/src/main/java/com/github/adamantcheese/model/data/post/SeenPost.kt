package com.github.adamantcheese.model.data.post

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import org.joda.time.DateTime

data class SeenPost(
        val threadDescriptor: ChanDescriptor.ThreadDescriptor,
        val postNo: Long,
        val insertedAt: DateTime
)