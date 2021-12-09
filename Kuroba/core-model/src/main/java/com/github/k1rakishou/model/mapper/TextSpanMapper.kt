package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.marshall
import com.github.k1rakishou.common.unmarshall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.ParcelableSpannableString
import com.github.k1rakishou.core_spannable.ParcelableSpans
import com.github.k1rakishou.model.entity.chan.post.ChanTextSpanEntity

object TextSpanMapper {
  private const val TAG = "TextSpanMapper"

  fun toEntity(
    ownerPostId: Long,
    parcelableSpannableString: ParcelableSpannableString,
    originalUnparsedComment: String?,
    chanTextType: ChanTextSpanEntity.TextType
  ): ChanTextSpanEntity? {
    if (parcelableSpannableString.isEmpty()) {
      return null
    }

    return ChanTextSpanEntity(
      textSpanId = 0L,
      ownerPostId = ownerPostId,
      parsedText = parcelableSpannableString.text,
      unparsedText = originalUnparsedComment,
      spanInfoBytes = parcelableSpannableString.parcelableSpans.marshall(),
      textType = chanTextType
    )
  }

  fun fromEntity(
    chanTextSpanEntityList: List<ChanTextSpanEntity>?,
    chanTextType: ChanTextSpanEntity.TextType
  ): ParcelableSpannableString? {
    if (chanTextSpanEntityList == null || chanTextSpanEntityList.isEmpty()) {
      return null
    }

    val filteredTextSpanEntityList = chanTextSpanEntityList.filter { textSpanEntity ->
      textSpanEntity.textType == chanTextType
    }

    if (filteredTextSpanEntityList.isEmpty()) {
      return null
    }

    if (filteredTextSpanEntityList.size > 1) {
      throw IllegalStateException("Expected one (or zero) TextSpanEntity with type (${chanTextType.name}). " +
          "Got ${filteredTextSpanEntityList.size}.")
    }

    val textSpanEntity = filteredTextSpanEntityList.first()

    val parcelableSpans = textSpanEntity.spanInfoBytes.unmarshall(ParcelableSpans.CREATOR)
      .peekError { error -> Logger.e(TAG, "fromEntity() error: ${error.errorMessageOrClassName()}") }
      .valueOrNull()
      ?: ParcelableSpans()

    return ParcelableSpannableString(
      parcelableSpans,
      textSpanEntity.parsedText
    )
  }

}