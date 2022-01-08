package com.github.k1rakishou.model.mapper

import android.text.SpannableString
import androidx.core.text.toSpanned
import com.github.k1rakishou.core_spannable.ParcelableSpannableString
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_spannable.parcelable_spannable_string.ParcelableSpannableStringMapper
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

object ChanPostEntityMapper {

  fun toEntity(
    chanPostId: Long,
    chanPost: ChanPost
  ): ChanPostEntity {
    return ChanPostEntity(
      chanPostId = chanPostId,
      deleted = chanPost.isDeleted,
      timestamp = chanPost.timestamp,
      name = chanPost.name,
      posterId = chanPost.posterId,
      posterIdColor = chanPost.posterIdColor,
      moderatorCapcode = chanPost.moderatorCapcode,
      isOp = chanPost is ChanOriginalPost,
      isSavedReply = chanPost.isSavedReply,
      isSage = chanPost.isSage
    )
  }

  fun fromEntity(
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
      is ChanDescriptor.ThreadDescriptor -> {
        PostDescriptor.create(
          siteName = chanDescriptor.siteName(),
          boardCode = chanDescriptor.boardCode(),
          threadNo = chanDescriptor.threadNo,
          postNo = chanPostIdEntity.postNo
        )
      }
      is ChanDescriptor.CatalogDescriptor -> {
        PostDescriptor.create(
          siteName = chanDescriptor.siteName(),
          boardCode = chanDescriptor.boardCode(),
          threadNo = chanPostIdEntity.postNo
        )
      }
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        error("Cannot load/store CompositeCatalogDescriptor from/to database")
      }
    }

    val postImages = postAdditionalData.postImageByPostIdMap[chanPostEntity.chanPostId]
      ?.map { chanPostImageEntity -> ChanPostImageMapper.fromEntity(chanPostImageEntity, postDescriptor) }
      ?: emptyList()

    val postIcons = postAdditionalData.postIconsByPostIdMap[chanPostEntity.chanPostId]
      ?.map { chanPostHttpIconEntity -> ChanPostHttpIconMapper.fromEntity(chanPostHttpIconEntity) }
      ?: emptyList()

    val repliesTo = postAdditionalData.postReplyToByPostIdMap[chanPostEntity.chanPostId]
      ?.map { chanPostReplyEntity ->
        return@map PostDescriptor.create(
          siteName = postDescriptor.siteDescriptor().siteName,
          boardCode = postDescriptor.boardDescriptor().boardCode,
          threadNo = postDescriptor.getThreadNo(),
          postNo = chanPostReplyEntity.replyNo,
          postSubNo = chanPostReplyEntity.replySubNo
        )
      }
      ?.toSet()
      ?: emptySet()

    val lastModified = if (chanThreadEntity.lastModified < 0) {
      0
    } else {
      chanThreadEntity.lastModified
    }

    return if (chanPostEntity.isOp) {
      ChanOriginalPost(
        chanPostId = chanPostEntity.chanPostId,
        postDescriptor = postDescriptor,
        postImages = postImages,
        postIcons = postIcons,
        repliesTo = repliesTo,
        catalogRepliesCount = chanThreadEntity.catalogRepliesCount,
        catalogImagesCount = chanThreadEntity.catalogImagesCount,
        uniqueIps = chanThreadEntity.uniqueIps,
        lastModified = lastModified,
        sticky = chanThreadEntity.sticky,
        closed = chanThreadEntity.closed,
        archived = chanThreadEntity.archived,
        deleted = chanPostEntity.deleted,
        timestamp = chanPostEntity.timestamp,
        name = chanPostEntity.name,
        postComment = mapPostComment(chanTextSpanEntityList),
        subject = mapSubject(chanTextSpanEntityList),
        tripcode = mapTripcode(chanTextSpanEntityList),
        posterId = chanPostEntity.posterId,
        posterIdColor = chanPostEntity.posterIdColor,
        moderatorCapcode = chanPostEntity.moderatorCapcode,
        isSavedReply = chanPostEntity.isSavedReply,
        isSage = chanPostEntity.isSage,
        // Do not serialize/deserialize "endless" flag
        endless = false
      )
    } else {
      ChanPost(
        chanPostId = chanPostEntity.chanPostId,
        postDescriptor = postDescriptor,
        _postImages = postImages.toMutableList(),
        postIcons = postIcons,
        repliesTo = repliesTo,
        timestamp = chanPostEntity.timestamp,
        name = chanPostEntity.name,
        postComment = mapPostComment(chanTextSpanEntityList),
        subject = mapSubject(chanTextSpanEntityList),
        tripcode = mapTripcode(chanTextSpanEntityList),
        posterId = chanPostEntity.posterId,
        posterIdColor = chanPostEntity.posterIdColor,
        moderatorCapcode = chanPostEntity.moderatorCapcode,
        isSavedReply = chanPostEntity.isSavedReply,
        deleted = chanPostEntity.deleted,
        isSage = chanPostEntity.isSage
      )
    }
  }

  fun mapTripcode(
    chanTextSpanEntityList: List<ChanTextSpanEntity>?
  ): CharSequence {
    val tripcode = TextSpanMapper.fromEntity(
      chanTextSpanEntityList,
      ChanTextSpanEntity.TextType.Tripcode
    ) ?: ParcelableSpannableString()

    return ParcelableSpannableStringMapper.fromParcelableSpannableString(tripcode)
  }

  fun mapSubject(
    chanTextSpanEntityList: List<ChanTextSpanEntity>?
  ): CharSequence {
    val subject = TextSpanMapper.fromEntity(
      chanTextSpanEntityList,
      ChanTextSpanEntity.TextType.Subject
    ) ?: ParcelableSpannableString()

    return ParcelableSpannableStringMapper.fromParcelableSpannableString(subject)
  }

  fun mapPostComment(
    chanTextSpanEntityList: List<ChanTextSpanEntity>?
  ): PostComment {
    val commentParcelableSpannableString = TextSpanMapper.fromEntity(
      chanTextSpanEntityList,
      ChanTextSpanEntity.TextType.PostComment
    ) ?: ParcelableSpannableString()

    val comment = ParcelableSpannableStringMapper.fromParcelableSpannableString(commentParcelableSpannableString)

    val postLinkables = comment.toSpanned().getSpans(
      0,
      comment.length,
      PostLinkable::class.java
    ).toList()

    val unparsedPostComment = chanTextSpanEntityList
      ?.filter { textSpanEntity -> textSpanEntity.textType == ChanTextSpanEntity.TextType.PostComment }
      ?.firstOrNull()
      ?.unparsedText

    return PostComment(
      originalComment = SpannableString(comment),
      originalUnparsedComment = unparsedPostComment,
      linkables = postLinkables
    )
  }

}