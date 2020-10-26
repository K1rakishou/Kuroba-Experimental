package com.github.k1rakishou.chan.core.mapper

import android.text.SpannableString
import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.model.PostImage
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.model.data.archive.ArchivePost
import com.github.k1rakishou.model.data.archive.ArchivePostMedia
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import okhttp3.HttpUrl.Companion.toHttpUrl

object ArchiveThreadMapper {
  private const val TAG = "ArchiveThreadMapper"

  fun fromPost(
    boardDescriptor: BoardDescriptor,
    archivePost: ArchivePost
  ): Post.Builder {
    val images = archivePost.archivePostMediaList.mapNotNull { archivePostMedia ->
      return@mapNotNull Try {
        return@Try fromPostMedia(archivePost.postDescriptor, archivePostMedia)
      }.safeUnwrap { error ->
        Logger.e(TAG, "Error mapping archive post media ${archivePostMedia.imageUrl}", error)
        return@mapNotNull null
      }
    }

    val postBuilder = Post.Builder()
      .boardDescriptor(boardDescriptor)
      .id(archivePost.postNo)
      .opId(archivePost.threadNo)
      .op(archivePost.isOP)
      .sticky(archivePost.sticky)
      .closed(archivePost.closed)
      .archived(archivePost.archived)
      .lastModified(-1)
      .name(archivePost.name)
      .subject(archivePost.subject)
      .tripcode(archivePost.tripcode)
      .setUnixTimestampSeconds(archivePost.unixTimestampSeconds)
      .postImages(images)
      .moderatorCapcode(archivePost.moderatorCapcode)
      .isSavedReply(false)
      .deleted(false)

    postBuilder.postCommentBuilder.setComment(SpannableString(archivePost.comment))

    return postBuilder
  }

  private fun fromPostMedia(
    postDescriptor: PostDescriptor,
    archivePostMedia: ArchivePostMedia
  ): PostImage? {
    val imageUrl = archivePostMedia.imageUrl?.toHttpUrl()

    return PostImage.Builder()
      .serverFilename(archivePostMedia.serverFilename)
      .thumbnailUrl(archivePostMedia.thumbnailUrl!!.toHttpUrl())
      .filename(archivePostMedia.filename)
      .extension(archivePostMedia.extension)
      .imageWidth(archivePostMedia.imageWidth)
      .imageHeight(archivePostMedia.imageHeight)
      .archiveId(0)
      .size(archivePostMedia.size)
      .fileHash(archivePostMedia.fileHashBase64, true)
      .imageUrl(imageUrl)
      .spoiler(false)
      .postDescriptor(postDescriptor)
      .build()
  }

}