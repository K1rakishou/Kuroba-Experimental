package com.github.adamantcheese.chan.utils

import android.annotation.SuppressLint
import android.text.TextUtils
import com.github.adamantcheese.chan.core.model.ChanThread
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.model.data.archive.ArchivePost
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.data.post.ChanPostImage
import java.util.*
import kotlin.math.abs

object PostUtils {

  @JvmStatic
  @SuppressLint("DefaultLocale")
  fun getReadableFileSize(bytes: Long): String {
    // Nice stack overflow copy-paste, but it's been updated to be more correct
    // https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
    val s = if (bytes < 0) {
      "-"
    } else {
      ""
    }

    var b = if (bytes == Long.MIN_VALUE) {
      Long.MAX_VALUE
    } else {
      abs(bytes)
    }

    return when {
      b < 1000L -> "$bytes B"
      b < 999950L -> String.format("%s%.1f kB", s, b / 1e3)
      1000.let { b /= it; b } < 999950L -> String.format("%s%.1f MB", s, b / 1e3)
      1000.let { b /= it; b } < 999950L -> String.format("%s%.1f GB", s, b / 1e3)
      1000.let { b /= it; b } < 999950L -> String.format("%s%.1f TB", s, b / 1e3)
      1000.let { b /= it; b } < 999950L -> String.format("%s%.1f PB", s, b / 1e3)
      else -> String.format("%s%.1f EB", s, b / 1e6)
    }
  }

  @JvmStatic
  fun findPostById(id: Long, thread: ChanThread?): Post? {
    if (thread != null) {
      for (post in thread.posts) {
        if (post.no == id) {
          return post
        }
      }
    }

    return null
  }

  @JvmStatic
  fun findPostWithReplies(id: Long, posts: List<Post>): HashSet<Post> {
    val postsSet = HashSet<Post>()
    findPostWithRepliesRecursive(id, posts, postsSet)
    return postsSet
  }

  /**
   * Finds a post by it's id and then finds all posts that has replied to this post recursively
   */
  private fun findPostWithRepliesRecursive(id: Long, posts: List<Post>, postsSet: MutableSet<Post>) {
    for (post in posts) {
      if (post.no == id && !postsSet.contains(post)) {
        postsSet.add(post)

        for (replyId in post.repliesFrom) {
          findPostWithRepliesRecursive(replyId, posts, postsSet)
        }
      }
    }
  }

  fun postsDiffer(postBuilder: Post.Builder, chanPost: ChanPost): Boolean {
    if (postBuilder.boardDescriptor!! != chanPost.postDescriptor.descriptor.boardDescriptor()) {
      return true
    }
    if (postBuilder.op != chanPost.isOp) {
      return true
    }

    if (postBuilder.op) {
      if (postBuilder.lastModified != chanPost.lastModified) {
        return true
      }
      if (postBuilder.sticky != chanPost.sticky) {
        return true
      }
      if (postBuilder.uniqueIps != chanPost.uniqueIps) {
        return true
      }
      if (postBuilder.threadImagesCount != chanPost.threadImagesCount) {
        return true
      }
      if (postBuilder.closed != chanPost.closed) {
        return true
      }
      if (postBuilder.archived != chanPost.archived) {
        return true
      }
      if (postBuilder.deleted != chanPost.deleted) {
        return true
      }
      if (postBuilder.totalRepliesCount != chanPost.replies) {
        return true
      }
    }

    if (postRepliesDiffer(postBuilder, chanPost)) {
      return true
    }
    if (postImagesDiffer2(postBuilder.postImages, chanPost.postImages)) {
      return true
    }
    if (postBuilder.postCommentBuilder.getComment().toString() != chanPost.postComment.text) {
      return true
    }
    if (postBuilder.subject?.toString() != chanPost.subject.toString()) {
      return true
    }
    if (postBuilder.name != chanPost.name) {
      return true
    }
    if (postBuilder.tripcode?.toString() != chanPost.tripcode.text) {
      return true
    }
    if (postBuilder.posterId != chanPost.posterId) {
      return true
    }
    if (postBuilder.moderatorCapcode != chanPost.moderatorCapcode) {
      return true
    }

    return false
  }

  private fun postRepliesDiffer(postBuilder: Post.Builder, chanPost: ChanPost): Boolean {
    if (postBuilder.getRepliesToIds().size != chanPost.repliesTo.size) {
      return true
    }

    if (postBuilder.getRepliesToIds() != chanPost.repliesTo) {
      return true
    }

    return false
  }

  private fun postImagesDiffer2(postImages: List<PostImage>, chanPostImages: List<ChanPostImage>): Boolean {
    if (postImages.size != chanPostImages.size) {
      return true
    }

    for (i in postImages.indices) {
      val postImage = postImages[i]
      val chanPostImage = chanPostImages[i]

      if (postImage.archiveId != chanPostImage.archiveId) {
        return true
      }
      if (postImage.type != chanPostImage.type) {
        return true
      }
      if (postImage.imageUrl != chanPostImage.imageUrl) {
        return true
      }
      if (postImage.thumbnailUrl != chanPostImage.thumbnailUrl) {
        return true
      }
    }

    return false
  }

  /**
   * This version of postsDiffer function is to compare a post from archives with already cached
   * post from the DB. We only care about the images count difference. It changes when original
   * image gets deleted by mods. We would also like to check the comments difference, but we can't
   * because at this point [archivePost]'s comment is still unparsed so it contains stuff like HTMl
   * tags etc.
   * */
  fun shouldRetainPostFromArchive(archivePost: ArchivePost, cachedArchivePost: ChanPost): Boolean {
    if (archivePost.archivePostMediaList.size > cachedArchivePost.postImages.size) {
      // Archived post has more images than the cached post
      return true
    }

    repeat(archivePost.archivePostMediaList.size) { index ->
      val archiveImage = archivePost.archivePostMediaList[index]
      val cachedArchiveImage = cachedArchivePost.postImages[index]

      // If archived post has an original image and cached post has no original image - retain
      // the archived post.
      if (archiveImage.imageUrl != null && cachedArchiveImage.imageUrl == null) {
        return true
      }

      // If archived post has a thumbnail image and cached post has no thumbnail image - retain
      // the archived post.
      if (archiveImage.thumbnailUrl != null && cachedArchiveImage.thumbnailUrl == null) {
        return true
      }
    }

    return false
  }

  /**
   * Same as above but for Post.Builder
   * */
  fun shouldRetainPostFromArchive(archivePost: ArchivePost, freshPost: Post.Builder): Boolean {
    if (archivePost.archivePostMediaList.size > freshPost.postImages.size) {
      // Archived post has more images than the fresh post
      return true
    }

    repeat(archivePost.archivePostMediaList.size) { index ->
      val archiveImage = archivePost.archivePostMediaList[index]
      val freshImage = freshPost.postImages[index]

      // If archived post has an original image and cached post has no original image - retain
      // the archived post.
      if (archiveImage.imageUrl != null && freshImage.imageUrl == null) {
        return true
      }

      // If archived post has a thumbnail image and cached post has no thumbnail image - retain
      // the archived post.
      if (archiveImage.thumbnailUrl != null && freshImage.thumbnailUrl == null) {
        return true
      }
    }

    return false
  }

  @JvmStatic
  fun postsDiffer(displayedPost: Post, newPost: Post): Boolean {
    if (displayedPost.no != newPost.no) {
      return true
    }
    if (displayedPost.isOP && newPost.isOP) {
      if (displayedPost.lastModified != newPost.lastModified) {
        return true
      }
      if (displayedPost.isSticky != newPost.isSticky) {
        return true
      }
      if (displayedPost.uniqueIps != newPost.uniqueIps) {
        return true
      }
      if (displayedPost.threadImagesCount != newPost.threadImagesCount) {
        return true
      }
      if (displayedPost.isClosed != newPost.isClosed) {
        return true
      }
      if (displayedPost.isArchived != newPost.isArchived) {
        return true
      }
      if (displayedPost.deleted.get() != newPost.deleted.get()) {
        return true
      }
      if (displayedPost.totalRepliesCount != newPost.totalRepliesCount) {
        return true
      }
    }
    if (postImagesDiffer(displayedPost.postImages, newPost.postImages)) {
      return true
    }
    if (postRepliesDiffer(displayedPost.repliesTo, newPost.repliesTo)) {
      return true
    }
    if (!TextUtils.equals(displayedPost.comment, newPost.comment)) {
      return true
    }
    if (!TextUtils.equals(displayedPost.subject, newPost.subject)) {
      return true
    }
    if (!TextUtils.equals(displayedPost.name, newPost.name)) {
      return true
    }
    if (!TextUtils.equals(displayedPost.tripcode, newPost.tripcode)) {
      return true
    }
    if (!TextUtils.equals(displayedPost.posterId, newPost.posterId)) {
      return true
    }
    if (!TextUtils.equals(displayedPost.capcode, newPost.capcode)) {
      return true
    }

    return false
  }

  private fun postRepliesDiffer(
    displayedPostRepliesTo: Set<Long>,
    newPostRepliesTo: Set<Long>
  ): Boolean {
    if (displayedPostRepliesTo.size != newPostRepliesTo.size) {
      return true
    }

    if (displayedPostRepliesTo != newPostRepliesTo) {
      return true
    }

    return false
  }

  private fun postImagesDiffer(
    displayedPostImages: List<PostImage>,
    newPostImages: List<PostImage>
  ): Boolean {
    if (displayedPostImages.size != newPostImages.size) {
      return true
    }

    val size = displayedPostImages.size

    for (i in 0 until size) {
      val displayedPostImage = displayedPostImages[i]
      val newPostImage = newPostImages[i]

      if (displayedPostImage.archiveId != newPostImage.archiveId) {
        return true
      }
      if (displayedPostImage.type != newPostImage.type) {
        return true
      }
      if (displayedPostImage.imageUrl != newPostImage.imageUrl) {
        return true
      }
      if (displayedPostImage.thumbnailUrl != newPostImage.thumbnailUrl) {
        return true
      }
    }

    return false
  }
}