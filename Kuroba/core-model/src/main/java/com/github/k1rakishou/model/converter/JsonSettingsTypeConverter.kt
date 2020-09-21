package com.github.k1rakishou.model.converter

import androidx.room.TypeConverter
import com.github.k1rakishou.json.JsonSettings
import com.github.k1rakishou.model.DatabaseModuleInjector
import com.github.k1rakishou.model.KurobaDatabase

class JsonSettingsTypeConverter {
  private val gson by lazy { DatabaseModuleInjector.modelMainComponent.getGson() }

  @TypeConverter
  fun toJsonSettings(jsonSettings: String?): JsonSettings? {
    return fromJson(jsonSettings)
  }

  @TypeConverter
  fun fromJsonSettings(jsonSettings: JsonSettings?): String? {
    return toJson(jsonSettings)
  }

  private fun fromJson(json: String?): JsonSettings {
    if (json == null) {
      return JsonSettings(mutableMapOf())
    }

    return gson.fromJson(json, JsonSettings::class.java)
  }

  private fun toJson(jsonSettings: JsonSettings?): String {
    if (jsonSettings == null) {
      return KurobaDatabase.EMPTY_JSON
    }

    return gson.toJson(jsonSettings)
  }

}