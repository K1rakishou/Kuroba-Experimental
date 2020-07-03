package com.github.adamantcheese.model.data.bookmark

import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import org.joda.time.DateTime

data class ThreadBookmarkReply(
  val postDescriptor: PostDescriptor,
  val repliesTo: PostDescriptor,
  // Used in BookmarksController, to filter out replies that the user has already seen
  // (or more precise their amount)
  var alreadySeen: Boolean,
  // Used when showing notifications to not show a notification for the same reply more than once
  var alreadyNotified: Boolean,
  var time: DateTime
) {

  fun toThreadBookmarkReplyView(): ThreadBookmarkReplyView {
    return ThreadBookmarkReplyView(
      postDescriptor = postDescriptor,
      repliesTo = repliesTo,
      seen = alreadySeen,
      notified = alreadyNotified,
      time = time
    )
  }

}