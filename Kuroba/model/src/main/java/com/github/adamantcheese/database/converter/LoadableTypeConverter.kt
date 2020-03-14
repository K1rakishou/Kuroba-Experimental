package com.github.adamantcheese.database.converter

import androidx.room.TypeConverter
import com.github.adamantcheese.database.data.loadable.LoadableType

class LoadableTypeConverter {

    @TypeConverter
    fun toLoadableType(value: Int) = LoadableType.fromTypeValue(value)

    @TypeConverter
    fun fromLoadableType(loadableType: LoadableType) = loadableType.typeValue

}