package com.github.adamantcheese.model.converter

import androidx.room.TypeConverter
import com.github.adamantcheese.model.DatabaseModuleInjector
import com.github.adamantcheese.model.data.misc.KeyValueSettings
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class KeyValueSettingsTypeConverter {
  private val gson by lazy { DatabaseModuleInjector.modelMainComponent.getGson() }

  @TypeConverter
  fun toKeyValueSettings(jsonSettings: String?): KeyValueSettings? {
    return fromJson(jsonSettings)
  }

  @TypeConverter
  fun fromKeyValueSettings(keyValueSettings: KeyValueSettings?): String? {
    return toJson(keyValueSettings)
  }

  private fun fromJson(json: String?): KeyValueSettings? {
    if (json == null) {
      return null
    }

    val type: Type = object : TypeToken<Map<String?, String?>?>() {}.type
    val map = gson.fromJson<Map<String, String>>(json, type)

    return KeyValueSettings(map)
  }

  private fun toJson(keyValueSettings: KeyValueSettings?): String? {
    if (keyValueSettings == null) {
      return null
    }

    return gson.toJson(keyValueSettings.map)
  }

}