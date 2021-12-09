package com.github.k1rakishou.model.entity.chan.post

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = ChanTextSpanEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ChanPostEntity::class,
      parentColumns = [ChanPostEntity.CHAN_POST_ID_COLUMN_NAME],
      childColumns = [ChanTextSpanEntity.OWNER_POST_ID_COLUMN_NAME],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.CASCADE
    )
  ],
  indices = [
    Index(
      value = [ChanTextSpanEntity.OWNER_POST_ID_COLUMN_NAME]
    ),
    Index(
      value = [
        ChanTextSpanEntity.OWNER_POST_ID_COLUMN_NAME,
        ChanTextSpanEntity.TEXT_TYPE_COLUMN_NAME
      ],
      unique = true
    )
  ]
)
data class ChanTextSpanEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = TEXT_SPAN_ID_COLUMN_NAME)
  var textSpanId: Long = 0L,
  @ColumnInfo(name = OWNER_POST_ID_COLUMN_NAME)
  val ownerPostId: Long,
  @ColumnInfo(name = PARSED_TEXT_COLUMN_NAME)
  val parsedText: String,
  @ColumnInfo(name = UNPARSED_TEXT_COLUMN_NAME, defaultValue = "NULL")
  val unparsedText: String?,
  @ColumnInfo(name = SPAN_INFO_BYTES_COLUMN_NAME, typeAffinity = ColumnInfo.BLOB)
  val spanInfoBytes: ByteArray,
  @ColumnInfo(name = TEXT_TYPE_COLUMN_NAME)
  val textType: TextType
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ChanTextSpanEntity

    if (textSpanId != other.textSpanId) return false
    if (ownerPostId != other.ownerPostId) return false
    if (parsedText != other.parsedText) return false
    if (unparsedText != other.unparsedText) return false
    if (!spanInfoBytes.contentEquals(other.spanInfoBytes)) return false
    if (textType != other.textType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = textSpanId.hashCode()
    result = 31 * result + ownerPostId.hashCode()
    result = 31 * result + parsedText.hashCode()
    result = 31 * result + (unparsedText?.hashCode() ?: 0)
    result = 31 * result + spanInfoBytes.contentHashCode()
    result = 31 * result + textType.hashCode()
    return result
  }

  enum class TextType(val value: Int) {
    PostComment(0),
    Subject(1),
    Tripcode(2);

    companion object {
      fun fromValue(value: Int): TextType {
        return values()
          .firstOrNull { it.value == value }
          ?: throw IllegalArgumentException("Value ($value) not found")
      }
    }
  }

  companion object {
    const val TABLE_NAME = "chan_text_span"

    const val TEXT_SPAN_ID_COLUMN_NAME = "text_span_id"
    const val OWNER_POST_ID_COLUMN_NAME = "owner_post_id"
    const val PARSED_TEXT_COLUMN_NAME = "parsed_text"
    const val UNPARSED_TEXT_COLUMN_NAME = "unparsed_text"
    const val SPAN_INFO_BYTES_COLUMN_NAME = "span_info_bytes"
    const val TEXT_TYPE_COLUMN_NAME = "text_type"
  }

}