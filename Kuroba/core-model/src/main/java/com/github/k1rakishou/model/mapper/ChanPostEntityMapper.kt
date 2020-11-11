package com.github.k1rakishou.model.mapper

import androidx.core.text.toSpanned
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_spannable.SpannableStringMapper
import com.github.k1rakishou.core_spannable.serializable.SerializableSpannableString
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.PostComment
import com.github.k1rakishou.model.entity.chan.post.ChanPostEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostIdEntity
import com.github.k1rakishou.model.entity.chan.post.ChanTextSpanEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity
import com.github.k1rakishou.model.source.local.ChanPostLocalSource
import com.google.gson.Gson
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

object ChanPostEntityMapper {

  fun toEntity(
    chanPostId: Long,
    chanPost: ChanPost
  ): ChanPostEntity {
    return ChanPostEntity(
      chanPostId = chanPostId,
      deleted = chanPost.deleted,
      timestamp = chanPost.timestamp,
      name = chanPost.name,
      posterId = chanPost.posterId,
      moderatorCapcode = chanPost.moderatorCapcode,
      isOp = chanPost is ChanOriginalPost,
      isSavedReply = chanPost.isSavedReply
    )
  }

  fun fromEntity(
    gson: Gson,
    chanDescriptor: ChanDescriptor,
    chanThreadEntity: ChanThreadEntity,
    chanPostIdEntity: ChanPostIdEntity,
    chanPostEntity: ChanPostEntity?,
    chanTextSpanEntityList: List<ChanTextSpanEntity>?,
    postAdditionalData: ChanPostLocalSource.PostAdditionalData
  ): ChanPost? {
    if (chanPostEntity == null) {
      return null
    }

    val postDescriptor = when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> PostDescriptor.create(
        chanDescriptor.siteName(),
        chanDescriptor.boardCode(),
        chanDescriptor.threadNo,
        chanPostIdEntity.postNo
      )
      is ChanDescriptor.CatalogDescriptor -> PostDescriptor.create(
        chanDescriptor.siteName(),
        chanDescriptor.boardCode(),
        chanPostIdEntity.postNo
      )
    }

    val postImages = postAdditionalData.postImageByPostIdMap[chanPostEntity.chanPostId]
      ?.map { chanPostImageEntity -> ChanPostImageMapper.fromEntity(chanPostImageEntity, postDescriptor) }
      ?: emptyList()

    val postIcons = postAdditionalData.postIconsByPostIdMap[chanPostEntity.chanPostId]
      ?.map { chanPostHttpIconEntity -> ChanPostHttpIconMapper.fromEntity(chanPostHttpIconEntity) }
      ?: emptyList()

    val repliesTo = postAdditionalData.postReplyToByPostIdMap[chanPostEntity.chanPostId]
      ?.map { chanPostReplyEntity -> chanPostReplyEntity.replyNo }
      ?.toSet()
      ?: emptySet()

    val post = if (chanPostEntity.isOp) {
      ChanOriginalPost(
        chanPostId = chanPostEntity.chanPostId,
        postDescriptor = postDescriptor,
        postImages =  postImages,
        postIcons = postIcons,
        repliesTo = repliesTo,
        catalogRepliesCount = chanThreadEntity.catalogRepliesCount,
        catalogImagesCount = chanThreadEntity.catalogImagesCount,
        uniqueIps = chanThreadEntity.uniqueIps,
        lastModified = chanThreadEntity.lastModified,
        sticky = chanThreadEntity.sticky,
        closed = chanThreadEntity.closed,
        archived = chanThreadEntity.archived,
        timestamp = chanPostEntity.timestamp,
        name = chanPostEntity.name,
        postComment = mapPostComment(gson, chanTextSpanEntityList),
        subject = mapSubject(gson, chanTextSpanEntityList),
        tripcode = mapTripcode(gson, chanTextSpanEntityList),
        posterId = chanPostEntity.posterId,
        moderatorCapcode = chanPostEntity.moderatorCapcode,
        isSavedReply = chanPostEntity.isSavedReply,
      )
    } else {
      ChanPost(
        chanPostId = chanPostEntity.chanPostId,
        postDescriptor = postDescriptor,
        postImages =  postImages,
        postIcons = postIcons,
        repliesTo = repliesTo,
        timestamp = chanPostEntity.timestamp,
        name = chanPostEntity.name,
        postComment = mapPostComment(gson, chanTextSpanEntityList),
        subject = mapSubject(gson, chanTextSpanEntityList),
        tripcode = mapTripcode(gson, chanTextSpanEntityList),
        posterId = chanPostEntity.posterId,
        moderatorCapcode = chanPostEntity.moderatorCapcode,
        isSavedReply = chanPostEntity.isSavedReply
      )
    }

    post.setPostDeleted(chanPostEntity.deleted)
    return post
  }

  fun mapTripcode(
    gson: Gson,
    chanTextSpanEntityList: List<ChanTextSpanEntity>?
  ): CharSequence {
    val serializableSpannableTripcode = TextSpanMapper.fromEntity(
      gson,
      chanTextSpanEntityList,
      ChanTextSpanEntity.TextType.Tripcode
    ) ?: SerializableSpannableString()

    return SpannableStringMapper.deserializeSpannableString(
      gson,
      serializableSpannableTripcode
    )
  }

  fun mapSubject(
    gson: Gson,
    chanTextSpanEntityList: List<ChanTextSpanEntity>?
  ): CharSequence {
    val serializableSpannableSubject = TextSpanMapper.fromEntity(
      gson,
      chanTextSpanEntityList,
      ChanTextSpanEntity.TextType.Subject
    ) ?: SerializableSpannableString()

    return SpannableStringMapper.deserializeSpannableString(
      gson,
      serializableSpannableSubject
    )
  }

  fun mapPostComment(
    gson: Gson,
    chanTextSpanEntityList: List<ChanTextSpanEntity>?
  ): PostComment {
    val serializableSpannableComment = TextSpanMapper.fromEntity(
      gson,
      chanTextSpanEntityList,
      ChanTextSpanEntity.TextType.PostComment
    ) ?: SerializableSpannableString()

    val comment = SpannableStringMapper.deserializeSpannableString(
      gson,
      serializableSpannableComment
    )

    val postLinkables = comment.toSpanned().getSpans(
      0,
      comment.length,
      PostLinkable::class.java
    ).toList()

    return PostComment(
      comment,
      postLinkables
    )
  }

  fun fillInReplies(totalPosts: List<ChanPost>) {
    val postsByNo: MutableMap<Long, ChanPost> = HashMap()
    for (post in totalPosts) {
      postsByNo[post.postNo()] = post
    }

    // Maps post no's to a list of no's that that post received replies from
    val replies: MutableMap<Long, MutableList<Long>> = HashMap()

    for (sourcePost in totalPosts) {
      for (replyTo in sourcePost.repliesTo) {
        var value = replies[replyTo]

        if (value == null) {
          value = ArrayList(3)
          replies[replyTo] = value
        }

        value.add(sourcePost.postNo())
      }
    }

    for ((postNo, replyList) in replies) {
      val subject = postsByNo[postNo]

      // Sometimes a post replies to a ghost, a post that doesn't exist.
      subject?.repliesFrom?.addAll(replyList)
    }
  }

}