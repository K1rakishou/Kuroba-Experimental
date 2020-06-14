package com.github.adamantcheese.model.converter

import androidx.room.TypeConverter
import com.github.adamantcheese.common.loadable.LoadableType

class LoadableTypeConverter {

  @TypeConverter
  fun toLoadableType(value: Int) = LoadableType.fromTypeValue(value)

  @TypeConverter
  fun fromLoadableType(loadableType: LoadableType) = loadableType.typeValue

}