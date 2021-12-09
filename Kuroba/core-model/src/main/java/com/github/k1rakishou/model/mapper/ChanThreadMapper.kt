package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.entity.chan.post.ChanPostFull
import com.github.k1rakishou.model.entity.chan.post.ChanTextSpanEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity
import com.github.k1rakishou.model.source.local.ChanPostLocalSource

object ChanThreadMapper {

  fun toEntity(threadNo: Long, ownerBoardId: Long, chanPost: ChanOriginalPost): ChanThreadEntity {
    if (chanPost.isOP()) {
      check(chanPost.lastModified >= 0L) { "Bad chanOriginalPost.lastModified" }
    }

    return ChanThreadEntity(
      threadId = 0L,
      threadNo = threadNo,
      ownerBoardId = ownerBoardId,
      lastModified = chanPost.lastModified,
      catalogRepliesCount = chanPost.catalogRepliesCount,
      catalogImagesCount = chanPost.catalogImagesCount,
      uniqueIps = chanPost.uniqueIps,
      sticky = chanPost.sticky,
      closed = chanPost.closed,
      archived = chanPost.archived
    )
  }

  fun fromEntity(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    chanThreadEntity: ChanThreadEntity,
    chanPostFull: ChanPostFull,
    chanTextSpanEntityList: List<ChanTextSpanEntity>?,
    postAdditionalData: ChanPostLocalSource.PostAdditionalData
  ): ChanOriginalPost {
    val postDescriptor = PostDescriptor.create(
      threadDescriptor.siteName(),
      threadDescriptor.boardCode(),
      threadDescriptor.threadNo,
      chanPostFull.chanPostIdEntity.postNo
    )

    val postImages = postAdditionalData.postImageByPostIdMap[chanPostFull.chanPostIdEntity.postId]
      ?.map { chanPostImageEntity -> ChanPostImageMapper.fromEntity(chanPostImageEntity, postDescriptor) }
      ?: emptyList()

    val postIcons = postAdditionalData.postIconsByPostIdMap[chanPostFull.chanPostIdEntity.postId]
      ?.map { chanPostHttpIconEntity -> ChanPostHttpIconMapper.fromEntity(chanPostHttpIconEntity) }
      ?: emptyList()

    val repliesTo = postAdditionalData.postReplyToByPostIdMap[chanPostFull.chanPostIdEntity.postId]
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

    check(chanPostFull.chanPostEntity.isOp) { "Post is not OP! (chanPostFull=${chanPostFull})" }

    return ChanOriginalPost(
      chanPostId = chanPostFull.chanPostIdEntity.postId,
      postDescriptor = postDescriptor,
      postImages = postImages,
      postIcons = postIcons,
      repliesTo = repliesTo,
      catalogRepliesCount = chanThreadEntity.catalogRepliesCount,
      catalogImagesCount = chanThreadEntity.catalogImagesCount,
      uniqueIps = chanThreadEntity.uniqueIps,
      lastModified = chanThreadEntity.lastModified,
      sticky = chanThreadEntity.sticky,
      closed = chanThreadEntity.closed,
      archived = chanThreadEntity.archived,
      deleted = chanPostFull.chanPostEntity.deleted,
      timestamp = chanPostFull.chanPostEntity.timestamp,
      name = chanPostFull.chanPostEntity.name,
      postComment = ChanPostEntityMapper.mapPostComment(chanTextSpanEntityList),
      subject = ChanPostEntityMapper.mapSubject(chanTextSpanEntityList),
      tripcode = ChanPostEntityMapper.mapTripcode(chanTextSpanEntityList),
      posterId = chanPostFull.chanPostEntity.posterId,
      posterIdColor = chanPostFull.chanPostEntity.posterIdColor,
      moderatorCapcode = chanPostFull.chanPostEntity.moderatorCapcode,
      isSavedReply = chanPostFull.chanPostEntity.isSavedReply,
      isSage = chanPostFull.chanPostEntity.isSage,
      // Do not serialize/deserialize "endless" flag
      endless = false
    )
  }

}