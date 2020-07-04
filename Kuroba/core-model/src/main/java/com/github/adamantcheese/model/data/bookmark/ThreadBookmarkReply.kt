package com.github.adamantcheese.model.data.bookmark

import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import org.joda.time.DateTime

/**
 * The priority of [alreadyNotified], [alreadySeen] and [alreadyRead] is the following:
 *
 * [alreadyRead] > [alreadySeen] > [alreadyNotified]
 *
 * Which means that if [alreadyRead] is true then [alreadySeen] and [alreadyNotified] must be true
 * as well, but if they are not for some reason then we just ignore them. In other words, if
 * [alreadyRead] for a reply and [alreadySeen]/[alreadyNotified] then we won't see the notification
 * (let alone "hear" it) because [alreadyRead] has higher priority than the other two.
 *
 * [alreadyNotified] - is a flag to figure out whether we should notify the user with sound and
 * vibration about this notification. Only used for "new" replies. We update this flag to true
 * right after we show the very first "alert" notification for a reply.
 * [alreadySeen] - is a flag to figure out whether we should show a notification for this reply at
 * all or not. Once the user clicks a notification or swipes it away it won't be show again. We
 * update this flag to true when the user either clicks or swipes away a notification.
 * [alreadyRead] - is a flag to figure out whether we should use a distinct color for this reply
 * (in bookmarks controller or in BottomNavBar badge). We set this flag to true when user scrolls
 * over a post associated with this reply in thread controller.
 * */
data class ThreadBookmarkReply(
  /**
   * Unique ID of the reply post.
   * */
  val postDescriptor: PostDescriptor,
  /**
   * Unique ID of the post this post replies to (your post).
   * */
  val repliesTo: PostDescriptor,
  var alreadyNotified: Boolean,
  var alreadySeen: Boolean,
  var alreadyRead: Boolean,
  /**
   * The time when this reply was inserted into the database. Used as the "when" parameter of a
   * notification, meaning the time when somebody replied to you.
   * */
  var time: DateTime
) {

  fun toThreadBookmarkReplyView(): ThreadBookmarkReplyView {
    return ThreadBookmarkReplyView(
      postDescriptor = postDescriptor,
      repliesTo = repliesTo,
      alreadyNotified = alreadyNotified,
      alreadySeen = alreadySeen,
      alreadyRead = alreadyRead,
      time = time
    )
  }

}