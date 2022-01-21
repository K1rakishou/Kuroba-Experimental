/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.core_spannable

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.common.CommentParserConstants
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.data.ArchiveType
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine

/**
 * A Clickable span that handles post clicks. These are created in PostParser for post quotes,
 * spoilers etc. PostCell has a [PostViewMovementMethod], that searches spans at the
 * location the TextView was tapped, and handled if it was a PostLinkable.
 */
@DoNotStrip
open class PostLinkable(
  val key: CharSequence,
  val linkableValue: Value,
  val type: Type
) : ClickableSpan() {
  protected var themeEngineOverride: ThemeEngine? = null

  private val themeEngine by lazy {
    // A hack for ThemeSettingsController
    return@lazy themeEngineOverride
      ?: SpannableModuleInjector.themeEngine
  }

  var isSpoilerVisible: Boolean = ChanSettings.revealTextSpoilers.get()
    private set

  private var markedNo: Long = -1

  override fun onClick(widget: View) {
    isSpoilerVisible = !isSpoilerVisible
  }

  fun setMarkedNo(markedNo: Long) {
    this.markedNo = markedNo
  }

  override fun updateDrawState(ds: TextPaint) {
    val theme = getTheme()
    var needResetTypeFace = true

    when (type) {
      Type.QUOTE,
      Type.QUOTE_TO_HIDDEN_OR_REMOVED_POST,
      Type.LINK,
      Type.THREAD,
      Type.BOARD,
      Type.SEARCH,
      Type.DEAD,
      Type.ARCHIVE -> {
        if (type == Type.QUOTE || type == Type.QUOTE_TO_HIDDEN_OR_REMOVED_POST) {
          val value = when (linkableValue) {
            is Value.LongValue -> linkableValue.value
            else -> throw IllegalArgumentException("Unsupported value type: ${linkableValue::class.java.simpleName}")
          }

          if (value == markedNo) {
            ds.color = theme.postHighlightQuoteColor
            ds.typeface = theme.defaultBoldTypeface

            needResetTypeFace = false
          } else {
            ds.color = theme.postQuoteColor
          }
        } else if (type == Type.LINK) {
          ds.color = theme.postLinkColor
        } else {
          ds.color = theme.postQuoteColor
        }

        if (type == Type.DEAD) {
          ds.isStrikeThruText = true
        } else {
          ds.isUnderlineText = true
        }
      }
      Type.SPOILER -> {
        ds.bgColor = theme.postSpoilerColor
        ds.isUnderlineText = false

        if (!isSpoilerVisible) {
          ds.color = theme.postSpoilerColor
        } else {
          ds.color = theme.postSpoilerRevealTextColor
        }
      }
    }

    if (needResetTypeFace && ds.typeface != null) {
      ds.typeface = null
    }
  }

  fun isMarkedNo(): Boolean {
    if (type != Type.QUOTE) {
      return false
    }

    val value = when (linkableValue) {
      is Value.LongValue -> linkableValue.value
      else -> throw IllegalArgumentException(
        "Unsupported value type: ${linkableValue::class.java.simpleName}"
      )
    }

    return value == markedNo
  }

  open fun getTheme(): ChanTheme = themeEngine.chanTheme

  override fun hashCode(): Int {
    var result = key.toString().toCharArray().sumOf(Char::code)

    result *= 31
    result = 31 * result + linkableValue.hashCode()
    result = 31 * result + type.ordinal
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (other !is PostLinkable) return false

    // We need to ignore the spans here when comparing
    return other.key.toString() == key.toString()
      && other.linkableValue == linkableValue
      && other.type == type
  }

  override fun toString(): String {
    return "PostLinkable(key=${key.take(32)}, linkableValue=$linkableValue, type=$type, " +
      "isSpoilerVisible=$isSpoilerVisible, markedNo=$markedNo)"
  }

  data class Link(
    val type: Type,
    var key: CharSequence,
    val linkValue: Value
  )

  enum class Type {
    QUOTE,
    QUOTE_TO_HIDDEN_OR_REMOVED_POST,
    LINK,
    SPOILER,
    THREAD,
    BOARD,
    SEARCH,
    DEAD,
    ARCHIVE
  }

  sealed class Value {

    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int

    fun extractValueOrNull(): Long? {
      return when (this) {
        is LongValue -> value
        is LongPairValue -> value
        is StringValue,
        is ThreadOrPostLink,
        is ArchiveThreadLink,
        is SearchLink,
        NoValue -> null
      }
    }

    fun extractSubValueOrNull(): Long? {
      return when (this) {
        is LongValue -> null
        is LongPairValue -> subValue
        is ThreadOrPostLink -> postSubId
        is ArchiveThreadLink -> postSubId
        is StringValue,
        is SearchLink,
        NoValue -> null
      }
    }

    object NoValue : Value() {
      override fun equals(other: Any?): Boolean {
        return other is NoValue
      }

      override fun hashCode(): Int {
        return 0
      }
    }

    data class LongValue(val value: Long) : Value()
    data class LongPairValue(val value: Long, val subValue: Long) : Value()
    data class SearchLink(val board: String, val query: String) : Value()

    data class StringValue(val value: CharSequence) : Value() {

      override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is StringValue) return false

        // We need to ignore the spans here when comparing
        return other.value.toString() == value.toString()
      }

      override fun hashCode(): Int {
        return value.toString().toCharArray().sumOf(Char::code)
      }
    }

    data class ThreadOrPostLink(
      val board: String,
      val threadId: Long,
      val postId: Long,
      val postSubId: Long = 0
    ) : Value() {
      fun isThreadLink(): Boolean = threadId == postId

      fun isValid(): Boolean = threadId > 0 && postId > 0
    }

    data class ArchiveThreadLink(
      val archiveType: ArchiveType,
      val board: String,
      val threadId: Long,
      val postId: Long?,
      val postSubId: Long? = null,
    ) : Value() {

      fun isValid(): Boolean = threadId > 0 && (postId == null || postId > 0)

      fun postIdOrThreadId(): Long {
        return postId ?: threadId
      }

      fun urlText(): String {
        return buildString {
          append(">>>")
          append(archiveType.domain)
          append("/")
          append(board)
          append("/")
          append(threadId)

          if (postId != null) {
            append("#")
            append(postId)
          }

          append(CommentParserConstants.EXTERNAL_THREAD_LINK_SUFFIX)
        }
      }
    }

  }

}