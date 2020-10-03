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
package com.github.k1rakishou.chan.ui.text.span

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.common.DoNotStrip
import javax.inject.Inject

/**
 * A Clickable span that handles post clicks. These are created in PostParser for post quotes,
 * spoilers etc. PostCell has a [PostCell.PostViewMovementMethod], that searches spans at the
 * location the TextView was tapped, and handled if it was a PostLinkable.
 */
@DoNotStrip
class PostLinkable(
  val key: CharSequence,
  val linkableValue: Value,
  val type: Type
) : ClickableSpan() {

  @Inject
  lateinit var themeEngine: ThemeEngine

  init {
    Chan.inject(this)
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
    val theme = themeEngine.chanTheme

    when (type) {
      Type.QUOTE,
      Type.LINK,
      Type.THREAD,
      Type.BOARD,
      Type.SEARCH,
      Type.DEAD -> {
        if (type == Type.QUOTE) {
          val value = when (linkableValue) {
            is Value.IntegerValue -> linkableValue.value.toLong()
            is Value.LongValue -> linkableValue.value
            else -> throw IllegalArgumentException(
              "Unsupported value type: ${linkableValue::class.java.simpleName}"
            )
          }

          if (value == markedNo) {
            ds.color = theme.postHighlightQuoteColor
            ds.typeface = theme.defaultBoldTypeface
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
  }

  override fun hashCode(): Int {
    var result = 0

    for (c in key.toString().toCharArray()) {
      result += c.toInt()
    }

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
    return "PostLinkable(key=$key, linkableValue=$linkableValue, type=$type, " +
      "isSpoilerVisible=$isSpoilerVisible, markedNo=$markedNo)"
  }

  data class Link(
    val type: Type,
    var key: CharSequence,
    val linkValue: Value
  )

  enum class Type {
    QUOTE,
    LINK,
    SPOILER,
    THREAD,
    BOARD,
    SEARCH,
    DEAD
  }

  sealed class Value {

    fun extractLongOrNull(): Long? {
      return when (this) {
        is IntegerValue -> value.toLong()
        is LongValue -> value
        is StringValue,
        is ThreadLink,
        is SearchLink -> null
        NoValue -> null
      }
    }

    object NoValue : Value()
    data class IntegerValue(val value: Int) : Value()
    data class LongValue(val value: Long) : Value()
    data class StringValue(val value: CharSequence) : Value()
    data class ThreadLink(var board: String, var threadId: Long, var postId: Long) : Value()
    data class SearchLink(var board: String, var search: String) : Value()
  }

}