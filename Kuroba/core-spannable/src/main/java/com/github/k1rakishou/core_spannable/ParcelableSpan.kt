package com.github.k1rakishou.core_spannable

import android.os.Parcel
import android.os.Parcelable
import com.github.k1rakishou.core_spannable.parcelable_spannable_string.ParcelableSpannableStringMapper
import com.github.k1rakishou.core_themes.ChanThemeColorId
import kotlinx.parcelize.Parcelize

class ParcelableSpannableString(
  val parcelableSpans: ParcelableSpans = ParcelableSpans(),
  val text: String = ""
) {

  fun isEmpty(): Boolean = text.isEmpty()
  fun hasNoSpans(): Boolean = parcelableSpans.spanInfoList.isEmpty()
  fun isValid(): Boolean = parcelableSpans.version >= 1
  fun version(): Int = parcelableSpans.version

}

class ParcelableSpans : Parcelable {
  val version: Int
  val spanInfoList: List<ParcelableSpanInfo>

  constructor() {
    version = ParcelableSpannableStringMapper.CURRENT_MAPPER_VERSION
    spanInfoList = emptyList()
  }

  constructor(parcel: Parcel) {
    version = parcel.readInt()
    spanInfoList = parcel.createTypedArrayList(ParcelableSpanInfo.CREATOR) ?: emptyList()
  }

  constructor(
    version: Int,
    spanInfoList: List<ParcelableSpanInfo>
  ) {
    this.version = version
    this.spanInfoList = spanInfoList
  }

  override fun writeToParcel(parcel: Parcel, flags: Int) {
    parcel.writeInt(version)
    parcel.writeTypedList(spanInfoList)
  }

  override fun describeContents(): Int {
    return 0
  }

  companion object CREATOR : Parcelable.Creator<ParcelableSpans> {
    override fun createFromParcel(parcel: Parcel): ParcelableSpans {
      return ParcelableSpans(parcel)
    }

    override fun newArray(size: Int): Array<ParcelableSpans?> {
      return arrayOfNulls(size)
    }
  }

}

class ParcelableSpanInfo : Parcelable {
  val spanStart: Int
  val spanEnd: Int
  val flags: Int
  val parcelableTypeRaw: Int
  val parcelableSpan: ParcelableSpan?

  constructor(parcel: Parcel) {
    spanStart = parcel.readInt()
    spanEnd = parcel.readInt()
    flags = parcel.readInt()
    parcelableTypeRaw = parcel.readInt()
    parcelableSpan = parcel.readParcelable(ParcelableSpan::class.java.classLoader)
  }

  constructor(
    spanStart: Int,
    spanEnd: Int,
    flags: Int,
    parcelableTypeRaw: Int,
    parcelableSpan: ParcelableSpan?,
  ) {
    this.spanStart = spanStart
    this.spanEnd = spanEnd
    this.flags = flags
    this.parcelableTypeRaw = parcelableTypeRaw
    this.parcelableSpan = parcelableSpan
  }

  override fun writeToParcel(parcel: Parcel, flags: Int) {
    parcel.writeInt(spanStart)
    parcel.writeInt(spanEnd)
    parcel.writeInt(flags)
    parcel.writeInt(parcelableTypeRaw)
    parcel.writeParcelable(parcelableSpan, flags)
  }

  override fun describeContents(): Int {
    return 0
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ParcelableSpanInfo

    if (spanStart != other.spanStart) return false
    if (spanEnd != other.spanEnd) return false
    if (flags != other.flags) return false
    if (parcelableTypeRaw != other.parcelableTypeRaw) return false
    if (parcelableSpan != other.parcelableSpan) return false

    return true
  }

  override fun hashCode(): Int {
    var result = spanStart
    result = 31 * result + spanEnd
    result = 31 * result + flags
    result = 31 * result + parcelableTypeRaw
    result = 31 * result + (parcelableSpan?.hashCode() ?: 0)
    return result
  }

  companion object CREATOR : Parcelable.Creator<ParcelableSpanInfo> {
    override fun createFromParcel(parcel: Parcel): ParcelableSpanInfo {
      return ParcelableSpanInfo(parcel)
    }

    override fun newArray(size: Int): Array<ParcelableSpanInfo?> {
      return arrayOfNulls(size)
    }
  }

}

sealed interface ParcelableSpan : Parcelable {

  @Parcelize
  data class AbsoluteSize(val size: Int) : ParcelableSpan

  @Parcelize
  data class BackgroundColor(val color: Int) : ParcelableSpan

  @Parcelize
  data class ForegroundColor(val color: Int) : ParcelableSpan

  @Parcelize
  data class BackgroundColorId(val colorId: ChanThemeColorId) : ParcelableSpan

  @Parcelize
  data class ForegroundColorId(val colorId: ChanThemeColorId) : ParcelableSpan

  @Parcelize
  data class PostLinkable(
    val key: String,
    val postLinkableTypeRaw: Int,
    val postLinkableValue: PostLinkableValue
  ) : ParcelableSpan

  @Parcelize
  data class Style(val style: Int) : ParcelableSpan

  @Parcelize
  data class Typeface(val family: String) : ParcelableSpan

  @Parcelize
  object Strikethrough : ParcelableSpan

}

sealed interface PostLinkableValue : Parcelable {

  open fun isValid(): Boolean = true

  @Parcelize
  data class Archive(
    val archiveDomain: String,
    val boardCode: String,
    val threadNo: Long = 0,
    val postNo: Long = 0,
    val postSubNo: Long = 0
  ) : PostLinkableValue

  @Parcelize
  data class Board(
    val boardCode: String
  ) : PostLinkableValue

  @Parcelize
  data class Link(
    val link: String
  ) : PostLinkableValue

  @Parcelize
  data class Quote(
    val postNo: Long,
    val postSubNo: Long = 0
  ) : PostLinkableValue

  @Parcelize
  data class Dead(
    val postNo: Long,
    val postSubNo: Long = 0
  ) : PostLinkableValue

  @Parcelize
  data class Search(
    val boardCode: String,
    val searchQuery: String
  ) : PostLinkableValue

  @Parcelize
  object Spoiler : PostLinkableValue

  @Parcelize
  data class ThreadOrPost(
    val boardCode: String,
    val threadNo: Long = 0,
    val postNo: Long = 0,
    val postSubNo: Long = 0,
  ) : PostLinkableValue

}

enum class ParcelableSpanType(val value: Int) {
  Unknown(-1),
  ForegroundColorSpanType(0),
  BackgroundColorSpanType(1),
  StrikethroughSpanType(2),
  StyleSpanType(3),
  TypefaceSpanType(4),
  AbsoluteSizeSpanHashed(5),
  PostLinkable(6),
  BackgroundColorIdSpan(7),
  ForegroundColorIdSpan(8);

  companion object {
    fun from(value: Int): ParcelableSpanType {
      return when (value) {
        0 -> ForegroundColorSpanType
        1 -> BackgroundColorSpanType
        2 -> StrikethroughSpanType
        3 -> StyleSpanType
        4 -> TypefaceSpanType
        5 -> AbsoluteSizeSpanHashed
        6 -> PostLinkable
        7 -> BackgroundColorIdSpan
        8 -> ForegroundColorIdSpan
        else -> Unknown
      }
    }
  }
}


enum class PostLinkableType(val value: Int) {
  Quote(0),
  Link(1),
  Spoiler(2),
  Thread(3),
  Board(4),
  Search(5),
  Dead(6),
  Archive(7);

  companion object {
    @JvmStatic
    fun from(value: Int): PostLinkableType? {
      return when (value) {
        0 -> Quote
        1 -> Link
        2 -> Spoiler
        3 -> Thread
        4 -> Board
        5 -> Search
        6 -> Dead
        7 -> Archive
        else -> null
      }
    }
  }
}