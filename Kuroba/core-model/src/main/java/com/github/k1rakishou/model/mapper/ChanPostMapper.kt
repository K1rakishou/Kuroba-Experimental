package com.github.k1rakishou.model.mapper

import android.text.SpannableString
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.data.post.PostComment

object ChanPostMapper {

  @JvmStatic
  fun toPostBuilder(chanPost: ChanPost): ChanPostBuilder {
    val postDescriptor = chanPost.postDescriptor

    if (chanPost is ChanOriginalPost) {
      return ChanPostBuilder()
        .boardDescriptor(postDescriptor.boardDescriptor())
        .id(postDescriptor.postNo)
        .opId(postDescriptor.getThreadNo())
        .op(postDescriptor.isOP())
        .replies(chanPost.catalogRepliesCount)
        .uniqueIps(chanPost.uniqueIps)
        .lastModified(chanPost.lastModified)
        .sticky(chanPost.sticky)
        .archived(chanPost.archived)
        .deleted(chanPost.isDeleted)
        .closed(chanPost.closed)
        .subject(chanPost.subject)
        .name(chanPost.name)
        .comment(chanPost.postComment.originalUnparsedComment)
        .tripcode(chanPost.tripcode)
        .setUnixTimestampSeconds(chanPost.timestamp)
        .postImages(chanPost.postImages, postDescriptor)
        .posterId(chanPost.posterId)
        .posterIdColor(chanPost.posterIdColor)
        .moderatorCapcode(chanPost.moderatorCapcode)
        .httpIcons(chanPost.postIcons)
        .isSavedReply(chanPost.isSavedReply)
        .postLinkables(chanPost.postComment.getAllLinkables())
        .repliesToIds(chanPost.repliesTo)
    } else {
      return ChanPostBuilder()
        .boardDescriptor(postDescriptor.boardDescriptor())
        .id(postDescriptor.postNo)
        .opId(postDescriptor.getThreadNo())
        .op(postDescriptor.isOP())
        .replies(chanPost.catalogRepliesCount)
        .deleted(chanPost.isDeleted)
        .subject(chanPost.subject)
        .name(chanPost.name)
        .comment(chanPost.postComment.originalUnparsedComment)
        .tripcode(chanPost.tripcode)
        .setUnixTimestampSeconds(chanPost.timestamp)
        .postImages(chanPost.postImages, postDescriptor)
        .posterId(chanPost.posterId)
        .posterIdColor(chanPost.posterIdColor)
        .moderatorCapcode(chanPost.moderatorCapcode)
        .httpIcons(chanPost.postIcons)
        .isSavedReply(chanPost.isSavedReply)
        .postLinkables(chanPost.postComment.getAllLinkables())
        .repliesToIds(chanPost.repliesTo)
    }
  }

  @JvmStatic
  fun fromPostBuilder(chanPostBuilder: ChanPostBuilder): ChanPost {
    val postDescriptor = chanPostBuilder.postDescriptor

    val postComment = PostComment(
      originalComment = SpannableString(chanPostBuilder.postCommentBuilder.getComment()),
      originalUnparsedComment = chanPostBuilder.postCommentBuilder.getUnparsedComment(),
      linkables = chanPostBuilder.postCommentBuilder.getAllLinkables()
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
        posterIdColor = chanPostBuilder.idColor,
        moderatorCapcode = chanPostBuilder.moderatorCapcode,
        sticky = chanPostBuilder.sticky,
        closed = chanPostBuilder.closed,
        archived = chanPostBuilder.archived,
        deleted = chanPostBuilder.deleted,
        endless = chanPostBuilder.endless,
        isSage = chanPostBuilder.sage,
        isSavedReply = chanPostBuilder.isSavedReply
      )
    } else {
      return ChanPost(
        chanPostId = 0L,
        postDescriptor = postDescriptor,
        _postImages = chanPostBuilder.postImages.toMutableList(),
        postIcons = chanPostBuilder.httpIcons,
        repliesTo = chanPostBuilder.repliesToIds,
        timestamp = chanPostBuilder.unixTimestampSeconds,
        name = chanPostBuilder.name,
        postComment = postComment,
        subject = chanPostBuilder.subject,
        tripcode = chanPostBuilder.tripcode,
        posterId = chanPostBuilder.posterId,
        posterIdColor = chanPostBuilder.idColor,
        moderatorCapcode = chanPostBuilder.moderatorCapcode,
        isSavedReply = chanPostBuilder.isSavedReply,
        deleted = chanPostBuilder.deleted,
        isSage = chanPostBuilder.sage,
      )
    }
  }

}