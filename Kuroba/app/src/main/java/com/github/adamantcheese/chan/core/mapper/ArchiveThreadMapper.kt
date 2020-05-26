package com.github.adamantcheese.chan.core.mapper

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.model.data.archive.ArchivePost
import com.github.adamantcheese.model.data.archive.ArchivePostMedia
import com.github.adamantcheese.model.data.archive.ArchiveThread
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import okhttp3.HttpUrl.Companion.toHttpUrl

object ArchiveThreadMapper {
  private const val TAG = "ArchiveThreadMapper"

  fun fromThread(
    board: Board,
    archiveThread: ArchiveThread,
    archiveDescriptor: ArchiveDescriptor
  ): List<Post.Builder> {
    val repliesCount = archiveThread.posts.filter { post -> !post.isOP }.count()
    val imagesCount = archiveThread.posts.sumBy { post -> post.archivePostMediaList.size }

    return archiveThread.posts.mapNotNull { post ->
      return@mapNotNull Try {
        return@Try fromPost(board, repliesCount, imagesCount, post, archiveDescriptor)
      }.safeUnwrap { error ->
        Logger.e(TAG, "Error mapping archive post ${post.postNo}", error)
        return@mapNotNull null
      }
    }
  }

  private fun fromPost(
    board: Board,
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
      .board(board)
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
      // Always archived == true because this post is from third-party archive
      .deleted(true)

    postBuilder.postCommentBuilder.setComment(archivePost.comment)
    postBuilder.setArchiveDescriptor(archiveDescriptor)

    return postBuilder
  }

  private fun fromPostMedia(
    archivePostMedia: ArchivePostMedia,
    archiveDescriptor: ArchiveDescriptor
  ): PostImage? {
    val imageUrl = archivePostMedia.imageUrl?.toHttpUrl()

    return PostImage.Builder()
      .serverFilename(archivePostMedia.serverFilename)
      .thumbnailUrl(archivePostMedia.thumbnailUrl!!.toHttpUrl())
      .filename(archivePostMedia.filename)
      .extension(archivePostMedia.extension)
      .imageWidth(archivePostMedia.imageWidth)
      .imageHeight(archivePostMedia.imageHeight)
      .archiveId(archiveDescriptor.getArchiveId())
      .size(archivePostMedia.size)
      .fileHash(archivePostMedia.fileHashBase64, true)
      .imageUrl(imageUrl)
      .spoiler(false)
      .build()
  }

}