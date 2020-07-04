package com.github.adamantcheese.model.data.bookmark

import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import org.joda.time.DateTime

data class ThreadBookmarkReplyView(
  val postDescriptor: PostDescriptor,
  val repliesTo: PostDescriptor,
  val alreadyNotified: Boolean,
  val alreadySeen: Boolean,
  val alreadyRead: Boolean,
  val time: DateTime
)