package com.github.k1rakishou.model.converter

import androidx.room.TypeConverter
import com.github.k1rakishou.model.entity.chan.post.ChanTextSpanEntity

class TextTypeTypeConverter {

  @TypeConverter
  fun toTextType(value: Int): ChanTextSpanEntity.TextType {
    return ChanTextSpanEntity.TextType.fromValue(value)
  }

  @TypeConverter
  fun fromTextType(textType: ChanTextSpanEntity.TextType): Int {
    return textType.value
  }

}