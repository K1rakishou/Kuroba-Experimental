package com.github.k1rakishou.model.util

import android.annotation.SuppressLint
import android.text.TextUtils
import android.widget.TextView
import androidx.core.text.PrecomputedTextCompat
import androidx.core.widget.TextViewCompat
import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
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
  private const val TAG = "ChanPostUtils"

  private val dateFormat = SimpleDateFormat.getDateTimeInstance(
    DateFormat.SHORT,
    DateFormat.MEDIUM,
    Locale.ENGLISH
  )

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
  fun getSafeToUseTitle(post: ChanPost): String? {
    fun getSafeToUseTitleInternal(post: ChanPost): String? {
      if (!TextUtils.isEmpty(post.subject)) {
        return post.subject.toString()
      }

      val comment = post.postComment.originalComment()

      if (!TextUtils.isEmpty(comment)) {
        val length = min(comment.length, 64)
        return comment.subSequence(0, length).toString()
      }

      return null
    }

    val title = getSafeToUseTitleInternal(post)
    if (title.isNullOrBlank()) {
      return null
    }

    return StringUtils.dirNameRemoveBadCharacters(title)
  }

  @JvmOverloads
  @JvmStatic
  fun getTitle(
    post: ChanPost?,
    chanDescriptor: ChanDescriptor?,
    maxCommentLength: Int = 200
  ): String {
    if (post != null) {
      if (!TextUtils.isEmpty(post.subject)) {
        return "/" + post.boardDescriptor.boardCode + "/ - " + post.subject.toString()
      }

      val comment = post.postComment.originalComment()

      if (!TextUtils.isEmpty(comment)) {
        val length = min(comment.length, maxCommentLength)
        return "/" + post.boardDescriptor.boardCode + "/ - " + comment.subSequence(0, length)
      }

      return "/" + post.boardDescriptor.boardCode + "/" + post.postNo()
    }

    if (chanDescriptor != null) {
      when (chanDescriptor) {
        is CatalogDescriptor -> {
          return "/" + chanDescriptor.boardCode() + "/"
        }
        is ThreadDescriptor -> {
          return "/" + chanDescriptor.boardCode() + "/" + chanDescriptor.threadNo
        }
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          return chanDescriptor.userReadableString()
        }
      }
    }

    return ""
  }

  @JvmOverloads
  @JvmStatic
  fun getTitle(
    subject: CharSequence?,
    comment: CharSequence?,
    threadDescriptor: ThreadDescriptor,
    maxCommentLength: Int = 200
  ): String {
    val boardDescriptor = threadDescriptor.boardDescriptor()

    if (!TextUtils.isEmpty(subject)) {
      return "/" + boardDescriptor.boardCode + "/ - " + subject.toString()
    }

    if (!TextUtils.isEmpty(comment)) {
      val length = min(comment!!.length, maxCommentLength)
      return "/" + boardDescriptor.boardCode + "/ - " + comment.subSequence(0, length)
    }

    return "/" + boardDescriptor.boardCode + "/" + threadDescriptor.threadNo
  }

  fun getLocalDate(post: ChanPost): String {
    val tmpDate = Date()
    tmpDate.time = post.timestamp * 1000L

    return try {
      dateFormat.format(tmpDate)
    } catch (error: Throwable) {
      Logger.e(TAG, "Invalid time: ${tmpDate.time}")
      return "Unk"
    }
  }

  fun postsDiffer(chanPostBuilder: ChanPostBuilder, chanPostFromCache: ChanPost, isThreadMode: Boolean): Boolean {
    if (chanPostBuilder.boardDescriptor!! != chanPostFromCache.postDescriptor.descriptor.boardDescriptor()) {
      return true
    }
    if (chanPostBuilder.op != (chanPostFromCache is ChanOriginalPost)) {
      return true
    }
    if (chanPostBuilder.sage != chanPostFromCache.isSage) {
      return true
    }

    if (chanPostBuilder.op) {
      chanPostFromCache as ChanOriginalPost

      if (!isThreadMode) {
        // When in a thread lastModified will most likely not be sent from the server
        // (4chan.org/2ch.hk) it is only sent for catalog threads. So we don't want to compare
        // them here because otherwise this method will be always returning true for OPs.
        if (chanPostBuilder.lastModified != chanPostFromCache.lastModified) {
          return true
        }
      }

      if (chanPostBuilder.sticky != chanPostFromCache.sticky) {
        return true
      }
      if (chanPostBuilder.uniqueIps != chanPostFromCache.uniqueIps) {
        return true
      }
      if (chanPostBuilder.threadImagesCount != chanPostFromCache.catalogImagesCount) {
        return true
      }
      if (chanPostBuilder.closed != chanPostFromCache.closed) {
        return true
      }
      if (chanPostBuilder.archived != chanPostFromCache.archived) {
        return true
      }
      if (chanPostBuilder.deleted != chanPostFromCache.isDeleted) {
        return true
      }
      if (chanPostBuilder.endless != chanPostFromCache.endless) {
        return true
      }
      if (chanPostBuilder.totalRepliesCount != chanPostFromCache.catalogRepliesCount) {
        return true
      }
    }

    // We do not compare comments, subject, name, tripcode, posterId, capcode and repliesTo here
    // because they will always differ because we add and remove some stuff from the raw values.
    // (like the server usually sends HTML code inside of post comment and we need to extract and
    // remove it to not show it to the user).
    // Instead, we calculate a hash of a raw post comment (+ subject, name, tripcode, posterId and
    // moderatorCapcode) and then compare them instead.
    if (postImagesDiffer(chanPostBuilder.postImages, chanPostFromCache.postImages)) {
      return true
    }

    return false
  }

  fun postImagesDiffer(
    postImages1: List<ChanPostImage>,
    postImages2: List<ChanPostImage>
  ): Boolean {
    if (postImages1.size != postImages2.size) {
      return true
    }

    for (i in postImages1.indices) {
      val postImage1 = postImages1[i]
      val postImage2 = postImages2[i]

      if (postImage1.type != postImage2.type) {
        return true
      }
      if (postImage1.imageUrl != postImage2.imageUrl) {
        return true
      }
      if (postImage1.actualThumbnailUrl != postImage2.actualThumbnailUrl) {
        return true
      }
      if (postImage1.isPrefetched != postImage2.isPrefetched) {
        return true
      }
      if (postImage1.loadedFileSize != postImage2.loadedFileSize) {
        return true
      }
    }

    return false
  }

  @JvmStatic
  fun getPostHash(chanPostBuilder: ChanPostBuilder): MurmurHashUtils.Murmur3Hash {
    val inputString = buildString {
      append(chanPostBuilder.postCommentBuilder.getUnparsedComment())

      chanPostBuilder.subject?.let { subject -> append(subject) }
      chanPostBuilder.name?.let { name -> append(name) }
      chanPostBuilder.tripcode?.let { tripcode -> append(tripcode) }
      chanPostBuilder.posterId?.let { posterId -> append(posterId) }
      chanPostBuilder.moderatorCapcode?.let { moderatorCapcode -> append(moderatorCapcode) }
    }

    return MurmurHashUtils.murmurhash3_x64_128(inputString)
  }

  @JvmStatic
  fun findPostWithReplies(postDescriptor: PostDescriptor, posts: Collection<ChanPost>): HashSet<ChanPost> {
    val postsSet = HashSet<ChanPost>()
    findPostWithRepliesRecursive(postDescriptor, posts, postsSet)
    return postsSet
  }

  /**
   * Finds a post by it's id and then finds all posts that has replied to this post recursively
   */
  private fun findPostWithRepliesRecursive(
    postDescriptor: PostDescriptor,
    posts: Collection<ChanPost>,
    postsSet: MutableSet<ChanPost>
  ) {
    for (post in posts) {
      if (post.postDescriptor == postDescriptor && !postsSet.contains(post)) {
        postsSet.add(post)
        val repliesFromCopy = post.repliesFromCopy

        repliesFromCopy.forEach { replyId ->
          findPostWithRepliesRecursive(replyId, posts, postsSet)
        }
      }
    }
  }

  fun wrapTextIntoPrecomputedText(text: CharSequence?, textView: TextView) {
    if (text.isNullOrEmpty()) {
      textView.setText(text, TextView.BufferType.SPANNABLE)
      return
    }

    val precomputedTextCompat = PrecomputedTextCompat.create(
      text,
      TextViewCompat.getTextMetricsParams(textView)
    )

    try {
      TextViewCompat.setPrecomputedText(textView, precomputedTextCompat)
    } catch (ignored: Throwable) {
      textView.setText(text, TextView.BufferType.SPANNABLE)
    }
  }

}