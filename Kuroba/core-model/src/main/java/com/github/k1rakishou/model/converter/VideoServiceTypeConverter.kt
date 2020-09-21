package com.github.k1rakishou.model.converter

import androidx.room.TypeConverter
import com.github.k1rakishou.model.data.video_service.MediaServiceType

class VideoServiceTypeConverter {

  @TypeConverter
  fun toVideoServiceType(value: Int): MediaServiceType {
    return MediaServiceType.fromTypeValue(value)
  }

  @TypeConverter
  fun fromVideoServiceType(mediaServiceType: MediaServiceType): Int {
    return mediaServiceType.typeValue
  }

}