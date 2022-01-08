package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.archive.ArchivePost
import com.github.k1rakishou.model.data.archive.ArchivePostMedia
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageBuilder
import okhttp3.HttpUrl.Companion.toHttpUrl

object ArchiveThreadMapper {
  private const val TAG = "ArchiveThreadMapper"

  fun fromPost(
    boardDescriptor: BoardDescriptor,
    archivePost: ArchivePost
  ): ChanPostBuilder {
    val images = archivePost.archivePostMediaList.mapNotNull { archivePostMedia ->
      return@mapNotNull Try {
        return@Try fromPostMedia(archivePostMedia)
      }.safeUnwrap { error ->
        Logger.e(TAG, "Error mapping archive post media ${archivePostMedia.imageUrl}", error)
        return@mapNotNull null
      }
    }

    val now = System.currentTimeMillis()

    val postBuilder = ChanPostBuilder()
      .boardDescriptor(boardDescriptor)
      .id(archivePost.postNo)
      .opId(archivePost.threadNo)
      .op(archivePost.isOP)
      .sticky(archivePost.sticky)
      .closed(archivePost.closed)
      .archived(archivePost.archived)
      .lastModified(now)
      .name(archivePost.name)
      .subject(archivePost.subject)
      .tripcode(archivePost.tripcode)
      .posterId(archivePost.posterId)
      .setUnixTimestampSeconds(archivePost.unixTimestampSeconds)
      .postImages(images, archivePost.postDescriptor)
      .moderatorCapcode(archivePost.moderatorCapcode)
      .isSavedReply(false)
      .deleted(false)

    postBuilder.postCommentBuilder.setUnparsedComment(archivePost.comment)

    return postBuilder
  }

  private fun fromPostMedia(
    archivePostMedia: ArchivePostMedia
  ): ChanPostImage? {
    val imageUrl = archivePostMedia.imageUrl?.toHttpUrl()
    val thumbnailUrl = archivePostMedia.thumbnailUrl?.toHttpUrl()

    val spoilerThumbnailUrl = if (archivePostMedia.spoiler) {
      thumbnailUrl
    } else {
      null
    }

    return ChanPostImageBuilder()
      .serverFilename(archivePostMedia.serverFilename)
      .thumbnailUrl(thumbnailUrl)
      .spoilerThumbnailUrl(spoilerThumbnailUrl)
      .filename(archivePostMedia.filename)
      .extension(archivePostMedia.extension)
      .imageWidth(archivePostMedia.imageWidth)
      .imageHeight(archivePostMedia.imageHeight)
      .imageSize(archivePostMedia.size)
      .fileHash(archivePostMedia.fileHashBase64, true)
      .imageUrl(imageUrl)
      .spoiler(archivePostMedia.spoiler)
      .build()
  }

}