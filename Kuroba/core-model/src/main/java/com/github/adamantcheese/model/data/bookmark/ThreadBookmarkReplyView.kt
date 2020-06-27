package com.github.adamantcheese.model.data.bookmark

import com.github.adamantcheese.model.data.descriptor.PostDescriptor

data class ThreadBookmarkReplyView(
  val postDescriptor: PostDescriptor,
  val repliesTo: PostDescriptor,
  // Used in BookmarksController, to filter out replies that the user has already seen
  // (or more precise their amount)
  var seen: Boolean,
  // Used when showing notifications to not show a notification for the same reply more than once
  var notified: Boolean
)