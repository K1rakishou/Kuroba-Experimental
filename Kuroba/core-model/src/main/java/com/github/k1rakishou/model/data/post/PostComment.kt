package com.github.k1rakishou.model.data.post

import android.text.Spannable
import android.text.SpannableString
import androidx.core.text.getSpans
import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_spannable.ThemeJsonSpannable

// Thread safe
class PostComment(
  @get:Synchronized
  @set:Synchronized
  // The original comment without any custom link spannables.
  private var originalComment: CharSequence,
  @get:Synchronized
  val originalUnparsedComment: String?,
  @get:Synchronized
  val linkables: List<PostLinkable>
) {

  @get:Synchronized
  @set:Synchronized
  private var _originalCommentHash = MurmurHashUtils.murmurhash3_x64_128(originalComment)

  @get:Synchronized
  @set:Synchronized
  // A comment version that may contain manually added spans (like link spans with link
  // video title/video duration etc)
  private var _updatedComment: CharSequence? = null

  @get:Synchronized
  @set:Synchronized
  private var _updatedCommentHash: MurmurHashUtils.Murmur3Hash? = null

  @get:Synchronized
  val originalCommentHash: MurmurHashUtils.Murmur3Hash
    get() = _originalCommentHash

  @get:Synchronized
  val updatedCommentHash: MurmurHashUtils.Murmur3Hash?
    get() = _updatedCommentHash

  @Synchronized
  fun copy(): PostComment {
    return PostComment(
      SpannableString(originalComment),
      originalUnparsedComment,
      linkables.toList()
    ).also { newPostComment ->
      newPostComment._updatedComment = this._updatedComment
      newPostComment._originalCommentHash = this._originalCommentHash
      newPostComment._updatedCommentHash = this._updatedCommentHash
    }
  }

  @Synchronized
  fun updateComment(newComment: CharSequence) {
    this._updatedComment = newComment
    this._updatedCommentHash = MurmurHashUtils.murmurhash3_x64_128(newComment)
  }

  @Synchronized
  fun comment(): CharSequence {
    if (_updatedComment == null) {
      return originalComment
    }

    return _updatedComment!!
  }

  @Synchronized
  fun originalComment(): CharSequence {
    return originalComment
  }

  @Synchronized
  fun getAllLinkables(): List<PostLinkable> {
    return linkables.toList()
  }

  @Synchronized
  fun hasComment() = originalComment.isNotEmpty()

  @Synchronized
  fun containsPostLinkable(postLinkable: PostLinkable): Boolean {
    return linkables.contains(postLinkable)
  }

  fun getThemeJsonSpannables(): Array<out ThemeJsonSpannable> {
    val spannableComment = comment() as? Spannable
      ?: return emptyArray()

    return spannableComment.getSpans()
  }

  fun getThemeJsonByThemeName(themeName: String): String? {
    val spannableComment = comment() as? Spannable
      ?: return null

    val themeJsonSpannable = spannableComment.getSpans<ThemeJsonSpannable>()
      .firstOrNull { themeJsonSpannable -> themeJsonSpannable.themeName == themeName }

    if (themeJsonSpannable == null) {
      return null
    }

    val start = spannableComment.getSpanStart(themeJsonSpannable)
    val end = spannableComment.getSpanEnd(themeJsonSpannable)

    return try {
      spannableComment.substring(start, end)
    } catch (error: Throwable) {
      return null
    }
  }

  override fun toString(): String {
    return "PostComment(originalComment='${originalComment.take(64)}\', " +
      "comment='${_updatedComment?.take(64)}', linkablesCount=${linkables.size})"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PostComment
    if (originalComment != other.originalComment) return false

    return true
  }

  override fun hashCode(): Int {
    return originalComment.hashCode()
  }

}