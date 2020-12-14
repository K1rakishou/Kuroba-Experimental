package com.github.k1rakishou.model.util

import android.annotation.SuppressLint
import android.text.TextUtils
import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.data.post.ChanPostImage
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.min

object ChanPostUtils {
  private val dateFormat = SimpleDateFormat.getDateTimeInstance(
    DateFormat.SHORT,
    DateFormat.MEDIUM,
    Locale.ENGLISH
  )
  private val tmpDate = Date()

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
  fun getTitle(post: ChanPost?, chanDescriptor: ChanDescriptor?): String {
    if (post != null) {
      if (!TextUtils.isEmpty(post.subject)) {
        return "/" + post.boardDescriptor.boardCode + "/ - " + post.subject.toString()
      }

      val comment = post.postComment.comment

      if (!TextUtils.isEmpty(comment)) {
        val length = min(comment.length, 200)
        return "/" + post.boardDescriptor.boardCode + "/ - " + comment.subSequence(0, length)
      }

      return "/" + post.boardDescriptor.boardCode + "/" + post.postNo()
    }

    if (chanDescriptor != null) {
      if (chanDescriptor is CatalogDescriptor) {
        return "/" + chanDescriptor.boardCode() + "/"
      } else {
        val threadDescriptor = chanDescriptor as ThreadDescriptor
        return "/" + chanDescriptor.boardCode() + "/" + threadDescriptor.threadNo
      }
    }

    return ""
  }

  fun getLocalDate(post: ChanPost): String {
    tmpDate.time = post.timestamp * 1000L
    return dateFormat.format(tmpDate)
  }

  fun postsDiffer(chanPostBuilder: ChanPostBuilder, chanPost: ChanPost): Boolean {
    if (chanPostBuilder.boardDescriptor!! != chanPost.postDescriptor.descriptor.boardDescriptor()) {
      return true
    }
    if (chanPostBuilder.op != (chanPost is ChanOriginalPost)) {
      return true
    }

    if (chanPostBuilder.op) {
      chanPost as ChanOriginalPost

      if (chanPostBuilder.lastModified != chanPost.lastModified) {
        return true
      }
      if (chanPostBuilder.sticky != chanPost.sticky) {
        return true
      }
      if (chanPostBuilder.uniqueIps != chanPost.uniqueIps) {
        return true
      }
      if (chanPostBuilder.threadImagesCount != chanPost.catalogImagesCount) {
        return true
      }
      if (chanPostBuilder.closed != chanPost.closed) {
        return true
      }
      if (chanPostBuilder.archived != chanPost.archived) {
        return true
      }
      if (chanPostBuilder.deleted != chanPost.deleted) {
        return true
      }
      if (chanPostBuilder.totalRepliesCount != chanPost.catalogRepliesCount) {
        return true
      }
    }

    // We do not compare comments, subject, name, tripcode, posterId, capcode and repliesTo here
    // because they will always differ because we add and remove some stuff from the raw values.
    // (like the server usually sends HTML code inside of post comment and we need to extract and
    // remove it to not show it to the user).
    // Instead, we calculate a hash of a raw post comment (+ subject, name, tripcode, posterId and
    // moderatorCapcode) and then compare them instead.
    if (postImagesDiffer2(chanPostBuilder.postImages, chanPost.postImages)) {
      return true
    }

    return false
  }

  private fun postImagesDiffer2(
    postImages: List<ChanPostImage>,
    chanPostImages: List<ChanPostImage>
  ): Boolean {
    if (postImages.size != chanPostImages.size) {
      return true
    }

    for (i in postImages.indices) {
      val postImage = postImages[i]
      val chanPostImage = chanPostImages[i]

      if (postImage.type != chanPostImage.type) {
        return true
      }
      if (postImage.imageUrl != chanPostImage.imageUrl) {
        return true
      }
      if (postImage.actualThumbnailUrl != chanPostImage.actualThumbnailUrl) {
        return true
      }
    }

    return false
  }

  @JvmStatic
  fun getPostHash(chanPostBuilder: ChanPostBuilder): MurmurHashUtils.Murmur3Hash {
    val inputString = buildString {
      append(chanPostBuilder.postCommentBuilder.getComment().toString())

      chanPostBuilder.subject?.let { subject -> append(subject) }
      chanPostBuilder.name?.let { name -> append(name) }
      chanPostBuilder.tripcode?.let { tripcode -> append(tripcode) }
      chanPostBuilder.posterId?.let { posterId -> append(posterId) }
      chanPostBuilder.moderatorCapcode?.let { moderatorCapcode -> append(moderatorCapcode) }
    }

    return MurmurHashUtils.murmurhash3_x64_128(inputString)
  }

  @JvmStatic
  fun findPostWithReplies(postNo: Long, posts: List<ChanPost>): HashSet<ChanPost> {
    val postsSet = HashSet<ChanPost>()
    findPostWithRepliesRecursive(postNo, posts, postsSet)
    return postsSet
  }

  /**
   * Finds a post by it's id and then finds all posts that has replied to this post recursively
   */
  private fun findPostWithRepliesRecursive(
    postNo: Long,
    posts: List<ChanPost>,
    postsSet: MutableSet<ChanPost>
  ) {
    for (post in posts) {
      if (post.postNo() == postNo && !postsSet.contains(post)) {
        postsSet.add(post)

        post.iterateRepliesFrom { replyId ->
          findPostWithRepliesRecursive(replyId, posts, postsSet)
        }
      }
    }
  }

}