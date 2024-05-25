package com.github.k1rakishou.chan.core.parser

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import okio.Buffer

@Immutable
sealed class TextPartSpan {
  val isPartialSpan: Boolean
    get() = this is PartialSpan

  fun priority(): Int {
    return when (this) {
      is BgColor,
      is BgColorId -> PriorityBackground
      is Linkable.Board,
      is Linkable.Quote,
      is Linkable.Search,
      is Linkable.Url,
      is PartialSpan,
      is Underline,
      is Linethrough,
      is Bold,
      is Italic,
      is Superscript,
      is Subscript,
      is Heading,
      is FontSize,
      is Monospace -> PriorityText
      is FgColor,
      is FgColorId -> PriorityForeground
      Spoiler -> PrioritySpoiler
    }
  }

  data class PartialSpan(
    val start: Int,
    val end: Int,
    val linkSpan: TextPartSpan
  ) : TextPartSpan()

  data class BgColor(val color: Int) : TextPartSpan()
  data class FgColor(val color: Int) : TextPartSpan()
  data class BgColorId(val colorId: ChanThemeColorId) : TextPartSpan()
  data class FgColorId(val colorId: ChanThemeColorId) : TextPartSpan()

  data class Heading(private val headingValue: Int) : TextPartSpan() {
    fun calculateNewFontSize(fontSize: Int): Int {
      return fontSize + (MAX_FONT_SIZE_INCREMENT - (headingValue.coerceAtMost(MAX_FONT_SIZE_INCREMENT).toFloat() * 2f).toInt())
    }

    companion object {
      private const val MAX_FONT_SIZE_INCREMENT = 10
    }
  }

  data class FontSize(private val fontSizeValue: Int) : TextPartSpan() {
    fun calculateNewFontSize(fontSize: Int): Int {
      return fontSize + fontSizeValue
    }

    companion object {
      const val MIN_FONT_SIZE_INCREMENT = 1
      const val MAX_FONT_SIZE_INCREMENT = 7
    }
  }

  data object Spoiler : TextPartSpan()
  data object Underline : TextPartSpan()
  data object Monospace : TextPartSpan()
  data object Linethrough : TextPartSpan()
  data object Bold : TextPartSpan()
  data object Italic : TextPartSpan()
  data object Superscript : TextPartSpan()
  data object Subscript : TextPartSpan()

  sealed class Linkable : TextPartSpan() {

    fun serialize(): Buffer {
      val buffer = Buffer()

      buffer.writeInt(id().value)
      serializeLinkable(buffer)

      return buffer
    }

    private fun id(): Id {
      return when (this) {
        is Board -> Id.Board
        is Quote -> Id.Quote
        is Search -> Id.Search
        is Url -> Id.Url
      }
    }

    protected abstract fun serializeLinkable(buffer: Buffer)

    data class Quote(
      val crossThread: Boolean,
      val dead: Boolean,
      val postDescriptor: PostDescriptor
    ) : Linkable() {
      override fun serializeLinkable(buffer: Buffer) {
        buffer.writeByte(if (crossThread) 1 else 0)
        buffer.writeByte(if (dead) 1 else 0)
        // TODO: compose post cells.
//        postDescriptor.serialize(buffer)
      }

      companion object {
        fun deserializeLinkable(buffer: Buffer): Quote {
//          val crossThread = buffer.readByte() == 1.toByte()
//          val dead = buffer.readByte() == 1.toByte()
//          val postDescriptor = PostDescriptor.deserialize(buffer)
//
//          return Quote(crossThread, dead, postDescriptor)
          // TODO: compose post cells.
          TODO()
        }
      }
    }

    data class Search(
      val boardCode: String,
      val searchQuery: String
    ) : Linkable() {
      override fun serializeLinkable(buffer: Buffer) {
        // TODO: compose post cells.
//        buffer.writeUtfString(boardCode)
//        buffer.writeUtfString(searchQuery)
      }

      companion object {
        fun deserializeLinkable(buffer: Buffer): Search {
//          val boardCode = buffer.readUtfString()
//          val searchQuery = buffer.readUtfString()
//
//          return Search(boardCode, searchQuery)
          // TODO: compose post cells.
          TODO()
        }
      }
    }

    data class Board(
      val boardCode: String
    ) : Linkable() {
      override fun serializeLinkable(buffer: Buffer) {
        // TODO: compose post cells.
//        buffer.writeUtfString(boardCode)
      }

      companion object {
        fun deserializeLinkable(buffer: Buffer): Board {
//          val boardCode = buffer.readUtfString()
//          return Board(boardCode)
          // TODO: compose post cells.
          TODO()
        }
      }
    }

    data class Url(
      val url: String
    ) : Linkable() {
      override fun serializeLinkable(buffer: Buffer) {
        // TODO: compose post cells.
//        buffer.writeUtfString(url)
      }

      companion object {
        fun deserializeLinkable(buffer: Buffer): Url {
//          val url = buffer.readUtfString()
//          return Url(url)
          // TODO: compose post cells.
          TODO()
        }
      }
    }

    enum class Id(val value: Int) {
      Quote(0),
      Search(1),
      Board(2),
      Url(3);

      companion object {
        fun fromValue(value: Int): Id? {
          return when (value) {
            0 -> Quote
            1 -> Search
            2 -> Board
            3 -> Url
            else -> null
          }
        }
      }
    }

    companion object {
      private const val TAG = "Linkable"

      fun deserialize(buffer: Buffer): Linkable? {
        val idValue = buffer.readInt()
        val id = Id.fromValue(idValue)
        if (id == null) {
          Logger.error(tag = TAG) { "Unknown id: ${idValue}" }
          return null
        }

        return when (id) {
          Id.Quote -> Quote.deserializeLinkable(buffer)
          Id.Search -> Search.deserializeLinkable(buffer)
          Id.Board -> Board.deserializeLinkable(buffer)
          Id.Url -> Url.deserializeLinkable(buffer)
        }
      }
    }
  }

  companion object {
    private const val PriorityBackground = 0
    private const val PriorityText = 1
    private const val PriorityForeground = 2
    private const val PrioritySpoiler = 1000
  }

}