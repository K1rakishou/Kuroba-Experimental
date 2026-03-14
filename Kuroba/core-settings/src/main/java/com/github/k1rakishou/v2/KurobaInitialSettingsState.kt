package com.github.k1rakishou.v2

import com.github.k1rakishou.common.toHashSetBy
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTimedValue

data class KurobaInitialSettingsState(
  // All settings loaded from the database upon app start (not modified anymore during app lifetime).
  // This is used for KurobaSettings initial states' initialization
  val allSettings: Map<String, ByteArray?>,
  // Seen settings loaded from the database upon app start (not modified anymore during app lifetime).
  // This is used for the logic which either display or not the "New setting" badge.
  val seenSettings: Set<String>
)

@Suppress("JavaCollectionWithNullableTypeArgument")
object CreateKurobaInitialSettingsState {
  private const val Tag = "CreateKurobaInitialSettingsState"

  fun create(database: KurobaSettingsDatabase): KurobaInitialSettingsState {
    val (allSettings, seenSettings) = runBlocking(Dispatchers.IO) {
      val allSettingsDeferred = async(Dispatchers.IO) { loadAllSettings(database) }
      val seenSettingsDeferred = async(Dispatchers.IO) { loadSeenSettings(database) }

      val (allSettings, allSettingsDuration) = measureTimedValue { allSettingsDeferred.await() }
      val (seenSettings, seenSettingsDuration) = measureTimedValue { seenSettingsDeferred.await() }

      Logger.debug(Tag) { "allSettings load took ${allSettingsDuration}, loaded ${allSettings.size}" }
      Logger.debug(Tag) { "seenSettings load took ${seenSettingsDuration}, loaded ${seenSettings.size}" }

      return@runBlocking allSettings to seenSettings
    }

    return KurobaInitialSettingsState(
      allSettings = allSettings,
      seenSettings = seenSettings
    )
  }

  private suspend fun loadSeenSettings(
    database: KurobaSettingsDatabase
  ): Set<String> {
    return database.seenSettingDao.selectAll()
      .toHashSetBy { kurobaSeenSettingEntity -> kurobaSeenSettingEntity.ownerKey }
  }

  private suspend fun loadAllSettings(
    database: KurobaSettingsDatabase
  ): ConcurrentHashMap<String, ByteArray?> {
    val allSettingsFromDatabase = database.settingDao.selectAll()
      .associateBy { kurobaSettingEntity -> kurobaSettingEntity.key }

    val allSettings = ConcurrentHashMap<String, ByteArray?>(allSettingsFromDatabase.size)

    for ((_, settingEntity) in allSettingsFromDatabase) {
      allSettings[settingEntity.key] = settingEntity.value
    }

    return allSettings
  }
}