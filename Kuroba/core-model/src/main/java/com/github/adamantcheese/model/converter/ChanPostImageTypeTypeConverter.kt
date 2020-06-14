package com.github.adamantcheese.model.converter

import androidx.room.TypeConverter
import com.github.adamantcheese.model.data.post.ChanPostImageType

class ChanPostImageTypeTypeConverter {

  @TypeConverter
  fun toChanPostImageType(value: Int): ChanPostImageType {
    return ChanPostImageType.fromValue(value)
  }

  @TypeConverter
  fun fromChanPostImageType(chanPostImageType: ChanPostImageType): Int {
    return chanPostImageType.value
  }

}