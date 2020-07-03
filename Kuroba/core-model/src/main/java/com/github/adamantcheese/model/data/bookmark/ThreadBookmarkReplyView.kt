package com.github.adamantcheese.model.data.bookmark

import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import org.joda.time.DateTime

data class ThreadBookmarkReplyView(
  val postDescriptor: PostDescriptor,
  val repliesTo: PostDescriptor,
  // Used in BookmarksController, to filter out replies that the user has already seen
  // (or more precise their amount)
  val seen: Boolean,
  // Used when showing notifications to not show a notification for the same reply more than once
  val notified: Boolean,
  val time: DateTime
)