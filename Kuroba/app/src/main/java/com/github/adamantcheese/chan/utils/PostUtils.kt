package com.github.adamantcheese.chan.utils

import android.annotation.SuppressLint
import android.text.TextUtils
import com.github.adamantcheese.chan.core.model.ChanThread
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.common.MurmurHashUtils
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

    // We do not compare comments, subject, name, tripcode, posterId, capcode and repliesTo here
    // because they may differ from the ones of a parsed post.

    if (postImagesDiffer2(postBuilder.postImages, chanPost.postImages)) {
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

  @JvmStatic
  fun getPostHash(postBuilder: Post.Builder): MurmurHashUtils.Murmur3Hash {
    val inputString = postBuilder.postCommentBuilder.getComment().toString() +
      (postBuilder.subject ?: "") +
      (postBuilder.name ?: "") +
      (postBuilder.tripcode ?: "") +
      (postBuilder.posterId ?: "") +
      (postBuilder.moderatorCapcode ?: "")

    return MurmurHashUtils.murmurhash3_x64_128(inputString)
  }

}