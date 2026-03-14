package com.github.k1rakishou.v2.settings

import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettingInfo
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import com.squareup.moshi.Moshi

class KurobaCookieSetting(
  database: KurobaSettingsDatabase,
  kurobaSettingInfo: KurobaSettingInfo,
  private val moshi: Moshi,
  key: KurobaSettingKey
) : KurobaJsonSetting<KurobaCookie?>(
  database = database,
  kurobaSettingInfo = kurobaSettingInfo,
  key = key,
  default = null
) {
  override suspend fun fromJson(json: String?): KurobaCookie? {
    return stringToCookie(json)
  }

  override suspend fun toJson(value: KurobaCookie?): String? {
    return cookieToString(value)
  }

  private fun cookieToString(kurobaCookie: KurobaCookie?): String? {
    if (kurobaCookie == null) {
      return null
    }

    return moshi
      .adapter<KurobaCookie>(KurobaCookie::class.java)
      .toJson(kurobaCookie)
  }

  private fun stringToCookie(json: String?): KurobaCookie? {
    if (json.isNullOrBlank()) {
      return null
    }

    return try {
      moshi.adapter<KurobaCookie>(KurobaCookie::class.java).fromJson(json)
    } catch (error: Throwable) {
      Logger.error(TAG, error) { "stringToCookie('${json}')" }
      return null
    }
  }

  companion object {
    private const val TAG = "KurobaCookieSetting"
  }
}