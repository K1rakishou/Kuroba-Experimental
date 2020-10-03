package com.github.k1rakishou.chan.core.mapper

import android.text.SpannableString
import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.model.PostImage
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.model.data.archive.ArchivePost
import com.github.k1rakishou.model.data.archive.ArchivePostMedia
import com.github.k1rakishou.model.data.archive.ArchiveThread
import com.github.k1rakishou.model.data.descriptor.ArchiveDescriptor
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import okhttp3.HttpUrl.Companion.toHttpUrl

object ArchiveThreadMapper {
  private const val TAG = "ArchiveThreadMapper"

  fun fromThread(
    boardDescriptor: BoardDescriptor,
    archiveThread: ArchiveThread,
    archiveDescriptor: ArchiveDescriptor
  ): List<Post.Builder> {
    val repliesCount = archiveThread.posts.filter { post -> !post.isOP }.size
    val imagesCount = archiveThread.posts.sumBy { post -> post.archivePostMediaList.size }

    return archiveThread.posts.mapNotNull { post ->
      return@mapNotNull Try {
        return@Try fromPost(boardDescriptor, repliesCount, imagesCount, post, archiveDescriptor)
      }.safeUnwrap { error ->
        Logger.e(TAG, "Error mapping archive post ${post.postNo}", error)
        return@mapNotNull null
      }
    }
  }

  fun fromPost(
    boardDescriptor: BoardDescriptor,
    archivePost: ArchivePost
  ): Post.Builder {
    val images = archivePost.archivePostMediaList.mapNotNull { archivePostMedia ->
      return@mapNotNull Try {
        return@Try fromPostMedia(archivePostMedia, null)
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

  fun fromPost(
    boardDescriptor: BoardDescriptor,
    repliesCount: Int,
    imagesCount: Int,
    archivePost: ArchivePost,
    archiveDescriptor: ArchiveDescriptor
  ): Post.Builder {
    val images = archivePost.archivePostMediaList.mapNotNull { archivePostMedia ->
      return@mapNotNull Try {
        return@Try fromPostMedia(archivePostMedia, archiveDescriptor)
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
      .replies(repliesCount)
      .threadImagesCount(imagesCount)
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
    postBuilder.setArchiveDescriptor(archiveDescriptor)

    return postBuilder
  }

  private fun fromPostMedia(
    archivePostMedia: ArchivePostMedia,
    archiveDescriptor: ArchiveDescriptor?
  ): PostImage? {
    val imageUrl = archivePostMedia.imageUrl?.toHttpUrl()

    return PostImage.Builder()
      .serverFilename(archivePostMedia.serverFilename)
      .thumbnailUrl(archivePostMedia.thumbnailUrl!!.toHttpUrl())
      .filename(archivePostMedia.filename)
      .extension(archivePostMedia.extension)
      .imageWidth(archivePostMedia.imageWidth)
      .imageHeight(archivePostMedia.imageHeight)
      .archiveId(archiveDescriptor?.archiveId ?: 0)
      .size(archivePostMedia.size)
      .fileHash(archivePostMedia.fileHashBase64, true)
      .imageUrl(imageUrl)
      .spoiler(false)
      .build()
  }

}