package com.github.k1rakishou.chan.core.mapper

import com.github.k1rakishou.chan.core.model.ChanPostBuilder
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.PostComment

object ChanPostMapper {

  @JvmStatic
  fun fromPostBuilder(
    chanPostBuilder: ChanPostBuilder
  ): ChanPost {
    val postDescriptor = chanPostBuilder.postDescriptor

    val postComment = PostComment(
      chanPostBuilder.postCommentBuilder.getComment(),
      chanPostBuilder.postCommentBuilder.getAllLinkables()
    )

    if (chanPostBuilder.op) {
      return ChanOriginalPost(
        chanPostId = 0L,
        postDescriptor = postDescriptor,
        postImages = chanPostBuilder.postImages,
        postIcons = chanPostBuilder.httpIcons,
        repliesTo = chanPostBuilder.repliesToIds,
        catalogRepliesCount = chanPostBuilder.totalRepliesCount,
        catalogImagesCount = chanPostBuilder.threadImagesCount,
        uniqueIps = chanPostBuilder.uniqueIps,
        lastModified = chanPostBuilder.lastModified,
        timestamp = chanPostBuilder.unixTimestampSeconds,
        name = chanPostBuilder.name,
        postComment = postComment,
        subject = chanPostBuilder.subject,
        tripcode = chanPostBuilder.tripcode,
        posterId = chanPostBuilder.posterId,
        moderatorCapcode = chanPostBuilder.moderatorCapcode,
        sticky = chanPostBuilder.sticky,
        closed = chanPostBuilder.closed,
        archived = chanPostBuilder.archived,
        isSavedReply = chanPostBuilder.isSavedReply
      ).also { chanOriginalPost ->
        chanOriginalPost.setPostDeleted(chanPostBuilder.deleted)
      }
    } else {
      return ChanPost(
        chanPostId = 0L,
        postDescriptor = postDescriptor,
        postImages = chanPostBuilder.postImages,
        postIcons = chanPostBuilder.httpIcons,
        repliesTo = chanPostBuilder.repliesToIds,
        timestamp = chanPostBuilder.unixTimestampSeconds,
        name = chanPostBuilder.name,
        postComment = postComment,
        subject = chanPostBuilder.subject,
        tripcode = chanPostBuilder.tripcode,
        posterId = chanPostBuilder.posterId,
        moderatorCapcode = chanPostBuilder.moderatorCapcode,
        isSavedReply = chanPostBuilder.isSavedReply
      ).also { chanOriginalPost ->
        chanOriginalPost.setPostDeleted(chanPostBuilder.deleted)
      }
    }
  }

}