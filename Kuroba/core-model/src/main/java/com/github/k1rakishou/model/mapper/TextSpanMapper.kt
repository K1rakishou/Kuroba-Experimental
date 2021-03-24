package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.core_spannable.serializable.SerializableSpanInfoList
import com.github.k1rakishou.core_spannable.serializable.SerializableSpannableString
import com.github.k1rakishou.model.entity.chan.post.ChanTextSpanEntity
import com.google.gson.Gson

object TextSpanMapper {

  fun toEntity(
    gson: Gson,
    ownerPostId: Long,
    serializableSpannableString: SerializableSpannableString,
    originalUnparsedComment: String?,
    chanTextType: ChanTextSpanEntity.TextType
  ): ChanTextSpanEntity? {
    if (serializableSpannableString.isEmpty) {
      return null
    }

    val spanInfoJson = gson.toJson(
      SerializableSpanInfoList(serializableSpannableString.spanInfoList)
    )

    return ChanTextSpanEntity(
      textSpanId = 0L,
      ownerPostId = ownerPostId,
      parsedText = serializableSpannableString.text,
      unparsedText = originalUnparsedComment,
      spanInfoJson = spanInfoJson,
      textType = chanTextType
    )
  }

  fun fromEntity(
    gson: Gson,
    chanTextSpanEntityList: List<ChanTextSpanEntity>?,
    chanTextType: ChanTextSpanEntity.TextType
  ): SerializableSpannableString? {
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

    val serializableSpanInfoList = gson.fromJson(
      textSpanEntity.spanInfoJson,
      SerializableSpanInfoList::class.java
    )

    return SerializableSpannableString(
      serializableSpanInfoList.spanInfoList,
      textSpanEntity.parsedText
    )
  }

}