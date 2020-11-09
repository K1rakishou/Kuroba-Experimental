package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.entity.chan.post.ChanPostFull
import com.github.k1rakishou.model.entity.chan.post.ChanTextSpanEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity
import com.github.k1rakishou.model.source.local.ChanPostLocalSource
import com.google.gson.Gson

object ChanThreadMapper {

  fun toEntity(threadNo: Long, ownerBoardId: Long, chanPost: ChanOriginalPost): ChanThreadEntity {
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
    gson: Gson,
    chanDescriptor: ChanDescriptor,
    chanThreadEntity: ChanThreadEntity,
    chanPostFull: ChanPostFull,
    chanTextSpanEntityList: List<ChanTextSpanEntity>?,
    postAdditionalData: ChanPostLocalSource.PostAdditionalData
  ): ChanOriginalPost {
    val postDescriptor = when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> PostDescriptor.create(
        chanDescriptor.siteName(),
        chanDescriptor.boardCode(),
        chanDescriptor.threadNo,
        chanPostFull.chanPostIdEntity.postNo
      )
      is ChanDescriptor.CatalogDescriptor -> PostDescriptor.create(
        chanDescriptor.siteName(),
        chanDescriptor.boardCode(),
        chanPostFull.chanPostIdEntity.postNo
      )
    }

    val postImages = postAdditionalData.postImageByPostIdMap[chanPostFull.chanPostIdEntity.postId]
      ?.map { chanPostImageEntity -> ChanPostImageMapper.fromEntity(chanPostImageEntity, postDescriptor) }
      ?: emptyList()

    val postIcons = postAdditionalData.postIconsByPostIdMap[chanPostFull.chanPostIdEntity.postId]
      ?.map { chanPostHttpIconEntity -> ChanPostHttpIconMapper.fromEntity(chanPostHttpIconEntity) }
      ?: emptyList()

    val repliesTo = postAdditionalData.postReplyToByPostIdMap[chanPostFull.chanPostIdEntity.postId]
      ?.map { chanPostReplyEntity -> chanPostReplyEntity.replyNo }
      ?.toSet()
      ?: emptySet()

    check(chanPostFull.chanPostEntity.isOp) { "Post is not OP! (chanPostFull=${chanPostFull})" }

    val chanOriginalPost = ChanOriginalPost(
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
      timestamp = chanPostFull.chanPostEntity.timestamp,
      name = chanPostFull.chanPostEntity.name,
      postComment = ChanPostEntityMapper.mapPostComment(gson, chanTextSpanEntityList),
      subject = ChanPostEntityMapper.mapSubject(gson, chanTextSpanEntityList),
      tripcode = ChanPostEntityMapper.mapTripcode(gson, chanTextSpanEntityList),
      posterId = chanPostFull.chanPostEntity.posterId,
      moderatorCapcode = chanPostFull.chanPostEntity.moderatorCapcode,
      isSavedReply = chanPostFull.chanPostEntity.isSavedReply
    )

    if (chanPostFull.chanPostEntity.deleted) {
      chanOriginalPost.setPostDeleted()
    }

    return chanOriginalPost
  }

}