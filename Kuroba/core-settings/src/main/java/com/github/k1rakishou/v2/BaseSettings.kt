package com.github.k1rakishou.v2

import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import com.github.k1rakishou.v2.settings.AbstractKurobaSetting
import com.github.k1rakishou.v2.settings.KurobaBooleanSetting
import com.github.k1rakishou.v2.settings.KurobaEnumSetting
import com.github.k1rakishou.v2.settings.KurobaIntSetting
import com.github.k1rakishou.v2.settings.KurobaLongSetting
import com.github.k1rakishou.v2.settings.KurobaMoshiSetting
import com.github.k1rakishou.v2.settings.KurobaRangeSetting
import com.github.k1rakishou.v2.settings.KurobaStringSetting
import com.squareup.moshi.Moshi
import java.util.concurrent.ConcurrentHashMap

abstract class BaseSettings(
  private val database: KurobaSettingsDatabase
) : KurobaSettingInfo {
  private val moshi by lazy {
    Moshi.Builder()
      .build()
  }

  private val _allSettings = ConcurrentHashMap<KurobaSettingKey, AbstractKurobaSetting<*, *>>(128)
  val allSettings: Map<KurobaSettingKey, AbstractKurobaSetting<*, *>>
    get() = _allSettings

  protected fun createBooleanSetting(
    key: KurobaSettingKey,
    default: Boolean
  ): KurobaBooleanSetting {
    checkSettingAlreadyExists(key)

    val setting = KurobaBooleanSetting(database, this, key, default)
    _allSettings[key] = setting
    return setting
  }

  protected fun createLongSetting(
    key: KurobaSettingKey,
    default: Long
  ): KurobaLongSetting {
    checkSettingAlreadyExists(key)

    val setting = KurobaLongSetting(database, this, key, default)
    _allSettings[key] = setting
    return setting
  }

  protected fun createIntSetting(
    key: KurobaSettingKey,
    default: Int
  ): KurobaIntSetting {
    checkSettingAlreadyExists(key)

    val setting = KurobaIntSetting(database, this, key, default)
    _allSettings[key] = setting
    return setting
  }

  protected fun createStringSetting(
    key: KurobaSettingKey,
    default: String
  ): KurobaStringSetting {
    checkSettingAlreadyExists(key)

    val setting = KurobaStringSetting(database, this, key, default)
    _allSettings[key] = setting
    return setting
  }

  protected fun createRangeSetting(
    key: KurobaSettingKey,
    default: Int,
    min: Int,
    max: Int,
  ): KurobaRangeSetting {
    checkSettingAlreadyExists(key)

    val setting = KurobaRangeSetting(database, this, key, default, min, max)
    _allSettings[key] = setting
    return setting
  }

  protected fun <T : Enum<T>> createEnumSetting(
    clazz: Class<T>,
    key: KurobaSettingKey,
    default: T
  ): KurobaEnumSetting<T> {
    checkSettingAlreadyExists(key)

    val setting = KurobaEnumSetting(database, this, clazz, key, default)
    _allSettings[key] = setting
    return setting
  }

  protected fun <T> createMoshiSetting(
    key: KurobaSettingKey,
    clazz: Class<T>,
    default: T
  ): KurobaMoshiSetting<T> {
    checkSettingAlreadyExists(key)

    val setting = KurobaMoshiSetting<T>(database, this, moshi, clazz, key, default)
    _allSettings[key] = setting
    return setting
  }


  private fun checkSettingAlreadyExists(key: KurobaSettingKey) {
    if (_allSettings.keys.any { existingKey -> existingKey.raw == key.raw }) {
      error("Setting with key ${key.raw} already registered!")
    }
  }
}