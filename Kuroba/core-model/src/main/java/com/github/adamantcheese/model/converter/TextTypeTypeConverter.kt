package com.github.adamantcheese.model.converter

import androidx.room.TypeConverter
import com.github.adamantcheese.model.entity.chan.ChanTextSpanEntity

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